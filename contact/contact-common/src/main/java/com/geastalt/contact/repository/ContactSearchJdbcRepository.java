package com.geastalt.contact.repository;

import com.geastalt.contact.config.ContactSqlProperties;
import com.geastalt.contact.dto.ContactSearchResult;
import com.geastalt.contact.validation.AlternateIdTypeValidator;
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
public class ContactSearchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContactSqlProperties sqlProperties;

    public List<ContactSearchResult> searchByNameExact(String lastName, String firstName, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastName", lastName)
                .addValue("limit", limit);

        String sql;
        if (firstName != null) {
            sql = sqlProperties.getSearch().getByNameExactWithFirstName();
            params.addValue("firstName", firstName);
        } else {
            sql = sqlProperties.getSearch().getByNameExact();
        }

        List<ContactSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    public List<ContactSearchResult> searchByNamePrefix(String lastNamePattern, String firstNamePattern, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastNamePattern", lastNamePattern)
                .addValue("limit", limit);

        String sql;
        if (firstNamePattern != null) {
            sql = sqlProperties.getSearch().getByNamePrefixWithFirstName();
            params.addValue("firstNamePattern", firstNamePattern);
        } else {
            sql = sqlProperties.getSearch().getByNamePrefix();
        }

        List<ContactSearchResult> results = jdbc.query(sql, params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    public long countByNameExact(String lastName, String firstName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastName", lastName);

        String sql;
        if (firstName != null) {
            sql = sqlProperties.getSearch().getCountByNameExactWithFirstName();
            params.addValue("firstName", firstName);
        } else {
            sql = sqlProperties.getSearch().getCountByNameExact();
        }

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0;
    }

    public long countByNamePrefix(String lastNamePattern, String firstNamePattern) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastNamePattern", lastNamePattern);

        String sql;
        if (firstNamePattern != null) {
            sql = sqlProperties.getSearch().getCountByNamePrefixWithFirstName();
            params.addValue("firstNamePattern", firstNamePattern);
        } else {
            sql = sqlProperties.getSearch().getCountByNamePrefix();
        }

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Find a contact by alternate ID with a specific type.
     * Two-step lookup: alternate ID -> contact_lookup (contact_id, partition_number) -> contact.
     */
    public ContactSearchResult findByAlternateId(String alternateId, String idType) {
        // Step 1: Look up contact_id and partition_number via contact_alternate_ids -> contact_lookup
        MapSqlParameterSource lookupParams = new MapSqlParameterSource()
                .addValue("alternateId", alternateId)
                .addValue("idType", idType);

        List<Map<String, Object>> lookupResults = jdbc.queryForList(
                sqlProperties.getSearch().getFindByAlternateIdLookup(), lookupParams);
        if (lookupResults.isEmpty()) {
            return null;
        }

        Long contactId = ((Number) lookupResults.get(0).get("contact_id")).longValue();
        Number partNum = (Number) lookupResults.get(0).get("partition_number");
        Integer partitionNumber = partNum != null ? partNum.intValue() : null;

        // Step 2: Fetch contact by ID
        ContactSearchResult result = findById(contactId);
        if (result != null) {
            result.setPartitionNumber(partitionNumber);
        }
        return result;
    }

    /**
     * Find a contact by alternate ID. Defaults to NEW_NATIONS type for backward compatibility.
     */
    public ContactSearchResult findByAlternateId(String alternateId) {
        return findByAlternateId(alternateId, AlternateIdTypeValidator.DEFAULT_TYPE);
    }

    /**
     * Find a contact by their primary ID.
     */
    public ContactSearchResult findById(Long contactId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("contactId", contactId);

        List<ContactSearchResult> results = jdbc.query(
                sqlProperties.getSearch().getFindById(), params, this::mapRowWithoutAlternateIds);
        if (results.isEmpty()) {
            return null;
        }

        // Fetch all alternate IDs for this contact
        ContactSearchResult result = results.get(0);
        Map<String, String> alternateIds = fetchAlternateIds(result.getId());
        return ContactSearchResult.builder()
                .id(result.getId())
                .alternateIds(alternateIds)
                .firstName(result.getFirstName())
                .lastName(result.getLastName())
                .preferredEmail(result.getPreferredEmail())
                .preferredAddress(result.getPreferredAddress())
                .build();
    }

    /**
     * Fetch all alternate IDs for a contact.
     */
    private Map<String, String> fetchAlternateIds(Long contactId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("contactId", contactId);

        Map<String, String> alternateIds = new HashMap<>();
        jdbc.query(sqlProperties.getSearch().getFetchAlternateIds(), params, (rs) -> {
            alternateIds.put(rs.getString("id_type"), rs.getString("alternate_id"));
        });
        return alternateIds;
    }

    /**
     * Batch fetch alternate IDs for multiple contacts in a single query.
     * This avoids the N+1 query problem.
     */
    private List<ContactSearchResult> enrichWithAlternateIds(List<ContactSearchResult> results) {
        if (results.isEmpty()) {
            return results;
        }

        // Collect all contact IDs
        List<Long> contactIds = results.stream()
                .map(ContactSearchResult::getId)
                .toList();

        // Fetch all alternate IDs in a single query
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("contactIds", contactIds);

        // Build a map of contact_id -> alternateIds
        Map<Long, Map<String, String>> alternateIdsByContact = new HashMap<>();
        jdbc.query(sqlProperties.getSearch().getBatchFetchAlternateIds(), params, (rs) -> {
            Long contactId = rs.getLong("contact_id");
            alternateIdsByContact
                    .computeIfAbsent(contactId, k -> new HashMap<>())
                    .put(rs.getString("id_type"), rs.getString("alternate_id"));
        });

        // Enrich results with alternate IDs
        return results.stream()
                .map(r -> ContactSearchResult.builder()
                        .id(r.getId())
                        .alternateIds(alternateIdsByContact.getOrDefault(r.getId(), Map.of()))
                        .firstName(r.getFirstName())
                        .lastName(r.getLastName())
                        .preferredEmail(r.getPreferredEmail())
                        .preferredAddress(r.getPreferredAddress())
                        .build())
                .toList();
    }

    public List<ContactSearchResult> searchByPhone(String phoneNumber, int limit) {
        // Normalize phone number - remove non-digits
        String normalizedPhone = phoneNumber.replaceAll("[^0-9]", "");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("phoneNumber", normalizedPhone)
                .addValue("limit", limit);

        List<ContactSearchResult> results = jdbc.query(
                sqlProperties.getSearch().getSearchByPhone(), params, this::mapRowWithoutAlternateIds);
        return enrichWithAlternateIds(results);
    }

    private ContactSearchResult mapRowWithoutAlternateIds(ResultSet rs, int rowNum) throws SQLException {
        ContactSearchResult.ContactSearchResultBuilder builder = ContactSearchResult.builder()
                .id(rs.getLong("id"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .preferredEmail(rs.getString("preferred_email"));

        // Build preferred address if present
        String streetAddress = rs.getString("street_address");
        if (streetAddress != null) {
            builder.preferredAddress(ContactSearchResult.PreferredAddress.builder()
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
