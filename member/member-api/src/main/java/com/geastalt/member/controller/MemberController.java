package com.geastalt.member.controller;

import com.geastalt.member.dto.MemberSearchResult;
import com.geastalt.member.entity.AlternateIdType;
import com.geastalt.member.entity.Member;
import com.geastalt.member.repository.MemberRepository;
import com.geastalt.member.repository.MemberSearchJdbcRepository;
import com.geastalt.member.service.ExternalIdService;
import com.geastalt.member.service.MemberSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;
    private final MemberSearchService memberSearchService;
    private final MemberSearchJdbcRepository memberSearchJdbcRepository;
    private final ExternalIdService externalIdService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        long count = memberRepository.count();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemberSearchResult>> search(
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "maxResults", defaultValue = "25") int maxResults) {

        MemberSearchService.SearchResult result = memberSearchService.searchMembers(
                lastName, firstName, maxResults, false);

        return ResponseEntity.ok(result.members());
    }

    @GetMapping("/by-alternate-id/{alternateId}")
    public ResponseEntity<MemberSearchResult> findByAlternateId(
            @org.springframework.web.bind.annotation.PathVariable String alternateId,
            @RequestParam(value = "type", defaultValue = "NEW_NATIONS") String type) {
        AlternateIdType idType;
        try {
            idType = AlternateIdType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        MemberSearchResult result = memberSearchJdbcRepository.findByAlternateId(alternateId, idType);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search-by-phone")
    public ResponseEntity<List<MemberSearchResult>> searchByPhone(
            @RequestParam("phone") String phone,
            @RequestParam(value = "maxResults", defaultValue = "25") int maxResults) {
        List<MemberSearchResult> results = memberSearchJdbcRepository.searchByPhone(phone, maxResults);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/generate-alternate-ids")
    public ResponseEntity<Map<String, Object>> generateAlternateIds(
            @RequestParam(value = "batchSize", defaultValue = "10000") int batchSize) {
        log.info("Generating NEW_NATIONS alternate IDs for members without one (batch size: {})", batchSize);

        final int BATCH_SIZE = Math.min(batchSize, 50000); // Cap at 50k
        int totalUpdated = 0;
        int batchCount = 0;

        while (true) {
            // Fetch a batch of member IDs that don't have a NEW_NATIONS alternate ID
            List<Long> memberIds = jdbcTemplate.queryForList(
                    """
                    SELECT m.id FROM members m
                    WHERE NOT EXISTS (
                        SELECT 1 FROM member_alternate_ids mai
                        WHERE mai.member_id = m.id AND mai.id_type = 'NEW_NATIONS'
                    )
                    ORDER BY m.id LIMIT ?
                    """,
                    Long.class,
                    BATCH_SIZE
            );

            if (memberIds.isEmpty()) {
                break;
            }

            // Use batch insert for efficiency
            jdbcTemplate.batchUpdate(
                    "INSERT INTO member_alternate_ids (member_id, id_type, alternate_id, created_at) VALUES (?, 'NEW_NATIONS', ?, NOW())",
                    memberIds,
                    BATCH_SIZE,
                    (PreparedStatement ps, Long memberId) -> {
                        String alternateId = externalIdService.generateExternalId(memberId);
                        ps.setLong(1, memberId);
                        ps.setString(2, alternateId);
                    }
            );

            totalUpdated += memberIds.size();
            batchCount++;

            if (batchCount % 10 == 0) {
                log.info("Generated {} alternate IDs ({} batches)...", totalUpdated, batchCount);
            }
        }

        log.info("Completed: generated {} NEW_NATIONS alternate IDs in {} batches", totalUpdated, batchCount);
        return ResponseEntity.ok(Map.of(
                "generated", totalUpdated,
                "batches", batchCount,
                "message", "NEW_NATIONS alternate IDs generated successfully"
        ));
    }
}
