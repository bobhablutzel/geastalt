package com.geastalt.member.repository;

import com.geastalt.member.dto.MemberSearchResult;
import com.geastalt.member.entity.AlternateIdType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MemberSearchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_FIELDS = """
            m.id, m.first_name, m.last_name,
            me.email as preferred_email,
            sa.street_address, sa.secondary_address, sa.city, sa.state, sa.zip_code, sa.zip_plus4
            """;

    private static final String FROM_CLAUSE = """
            FROM members m
            LEFT JOIN member_emails me ON me.member_id = m.id AND me.preferred = true
            LEFT JOIN member_addresses ma ON ma.member_id = m.id AND ma.preferred = true
            LEFT JOIN standardized_addresses sa ON sa.id = ma.address_id
            """;

    public List<MemberSearchResult> searchByNameExact(String lastName, String firstName, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastName", lastName)
                .addValue("limit", limit);

        String sql;
        if (firstName != null) {
            sql = "SELECT " + SELECT_FIELDS + FROM_CLAUSE +
                    "WHERE m.last_name_lower = :lastName AND m.first_name_lower = :firstName LIMIT :limit";
            params.addValue("firstName", firstName);
        } else {
            sql = "SELECT " + SELECT_FIELDS + FROM_CLAUSE +
                    "WHERE m.last_name_lower = :lastName LIMIT :limit";
        }

        List<MemberSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    public List<MemberSearchResult> searchByNamePrefix(String lastNamePattern, String firstNamePattern, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastNamePattern", lastNamePattern)
                .addValue("limit", limit);

        String sql;
        if (firstNamePattern != null) {
            sql = "SELECT " + SELECT_FIELDS + FROM_CLAUSE +
                    "WHERE m.last_name_lower LIKE :lastNamePattern AND m.first_name_lower LIKE :firstNamePattern LIMIT :limit";
            params.addValue("firstNamePattern", firstNamePattern);
        } else {
            sql = "SELECT " + SELECT_FIELDS + FROM_CLAUSE +
                    "WHERE m.last_name_lower LIKE :lastNamePattern LIMIT :limit";
        }

        List<MemberSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    public long countByNameExact(String lastName, String firstName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastName", lastName);

        String sql;
        if (firstName != null) {
            sql = "SELECT COUNT(*) FROM members WHERE last_name_lower = :lastName AND first_name_lower = :firstName";
            params.addValue("firstName", firstName);
        } else {
            sql = "SELECT COUNT(*) FROM members WHERE last_name_lower = :lastName";
        }

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0;
    }

    public long countByNamePrefix(String lastNamePattern, String firstNamePattern) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastNamePattern", lastNamePattern);

        String sql;
        if (firstNamePattern != null) {
            sql = "SELECT COUNT(*) FROM members WHERE last_name_lower LIKE :lastNamePattern AND first_name_lower LIKE :firstNamePattern";
            params.addValue("firstNamePattern", firstNamePattern);
        } else {
            sql = "SELECT COUNT(*) FROM members WHERE last_name_lower LIKE :lastNamePattern";
        }

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Find a member by alternate ID with a specific type.
     * Two-step lookup: alternate ID → member_lookup (member_id, partition_number) → member.
     */
    public MemberSearchResult findByAlternateId(String alternateId, AlternateIdType idType) {
        // Step 1: Look up member_id and partition_number via member_alternate_ids → member_lookup
        String lookupSql = """
                SELECT maid.member_id, ml.partition_number
                FROM member_alternate_ids maid
                LEFT JOIN member_lookup ml ON ml.member_id = maid.member_id
                WHERE maid.id_type = :idType AND maid.alternate_id = :alternateId
                """;
        MapSqlParameterSource lookupParams = new MapSqlParameterSource()
                .addValue("alternateId", alternateId)
                .addValue("idType", idType.name());

        List<Map<String, Object>> lookupResults = jdbc.queryForList(lookupSql, lookupParams);
        if (lookupResults.isEmpty()) {
            return null;
        }

        Long memberId = ((Number) lookupResults.get(0).get("member_id")).longValue();
        Number partNum = (Number) lookupResults.get(0).get("partition_number");
        Integer partitionNumber = partNum != null ? partNum.intValue() : null;

        // Step 2: Fetch member by ID
        MemberSearchResult result = findById(memberId);
        if (result != null) {
            result.setPartitionNumber(partitionNumber);
        }
        return result;
    }

    /**
     * Find a member by alternate ID. Defaults to NEW_NATIONS type for backward compatibility.
     */
    public MemberSearchResult findByAlternateId(String alternateId) {
        return findByAlternateId(alternateId, AlternateIdType.NEW_NATIONS);
    }

    /**
     * Find a member by their primary ID.
     */
    public MemberSearchResult findById(Long memberId) {
        String sql = "SELECT " + SELECT_FIELDS + FROM_CLAUSE + "WHERE m.id = :memberId";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("memberId", memberId);

        List<MemberSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        if (results.isEmpty()) {
            return null;
        }

        // Fetch all alternate IDs for this member
        MemberSearchResult result = results.get(0);
        Map<String, String> alternateIds = fetchAlternateIds(result.getId());
        return MemberSearchResult.builder()
                .id(result.getId())
                .alternateIds(alternateIds)
                .firstName(result.getFirstName())
                .lastName(result.getLastName())
                .preferredEmail(result.getPreferredEmail())
                .preferredAddress(result.getPreferredAddress())
                .build();
    }

    /**
     * Fetch all alternate IDs for a member.
     */
    private Map<String, String> fetchAlternateIds(Long memberId) {
        String sql = "SELECT id_type, alternate_id FROM member_alternate_ids WHERE member_id = :memberId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memberId", memberId);

        Map<String, String> alternateIds = new HashMap<>();
        jdbc.query(sql, params, (rs) -> {
            alternateIds.put(rs.getString("id_type"), rs.getString("alternate_id"));
        });
        return alternateIds;
    }

    /**
     * Batch fetch alternate IDs for multiple members in a single query.
     * This avoids the N+1 query problem.
     */
    private List<MemberSearchResult> enrichWithAlternateIds(List<MemberSearchResult> results) {
        if (results.isEmpty()) {
            return results;
        }

        // Collect all member IDs
        List<Long> memberIds = results.stream()
                .map(MemberSearchResult::getId)
                .toList();

        // Fetch all alternate IDs in a single query
        String sql = "SELECT member_id, id_type, alternate_id FROM member_alternate_ids WHERE member_id IN (:memberIds)";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memberIds", memberIds);

        // Build a map of member_id -> alternateIds
        Map<Long, Map<String, String>> alternateIdsByMember = new HashMap<>();
        jdbc.query(sql, params, (rs) -> {
            Long memberId = rs.getLong("member_id");
            alternateIdsByMember
                    .computeIfAbsent(memberId, k -> new HashMap<>())
                    .put(rs.getString("id_type"), rs.getString("alternate_id"));
        });

        // Enrich results with alternate IDs
        return results.stream()
                .map(r -> MemberSearchResult.builder()
                        .id(r.getId())
                        .alternateIds(alternateIdsByMember.getOrDefault(r.getId(), Map.of()))
                        .firstName(r.getFirstName())
                        .lastName(r.getLastName())
                        .preferredEmail(r.getPreferredEmail())
                        .preferredAddress(r.getPreferredAddress())
                        .build())
                .toList();
    }

    public List<MemberSearchResult> searchByPhone(String phoneNumber, int limit) {
        // Normalize phone number - remove non-digits
        String normalizedPhone = phoneNumber.replaceAll("[^0-9]", "");

        String sql = "SELECT " + SELECT_FIELDS + """
                FROM members m
                INNER JOIN member_phones mp ON mp.member_id = m.id
                LEFT JOIN member_emails me ON me.member_id = m.id AND me.preferred = true
                LEFT JOIN member_addresses ma ON ma.member_id = m.id AND ma.preferred = true
                LEFT JOIN standardized_addresses sa ON sa.id = ma.address_id
                WHERE mp.phone_number = :phoneNumber
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("phoneNumber", normalizedPhone)
                .addValue("limit", limit);

        List<MemberSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    private MemberSearchResult mapRowWithoutAlternateIds(ResultSet rs, int rowNum) throws SQLException {
        MemberSearchResult.MemberSearchResultBuilder builder = MemberSearchResult.builder()
                .id(rs.getLong("id"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .preferredEmail(rs.getString("preferred_email"));

        // Build preferred address if present
        String streetAddress = rs.getString("street_address");
        if (streetAddress != null) {
            builder.preferredAddress(MemberSearchResult.PreferredAddress.builder()
                    .streetAddress(streetAddress)
                    .secondaryAddress(rs.getString("secondary_address"))
                    .city(rs.getString("city"))
                    .state(rs.getString("state"))
                    .zipCode(rs.getString("zip_code"))
                    .zipPlus4(rs.getString("zip_plus4"))
                    .build());
        }

        return builder.build();
    }

}
