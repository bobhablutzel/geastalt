package com.geastalt.contact.controller;

import com.geastalt.contact.config.ContactSqlProperties;
import com.geastalt.contact.dto.ContactSearchResult;
import com.geastalt.contact.entity.Contact;
import com.geastalt.contact.validation.AlternateIdTypeValidator;
import com.geastalt.contact.repository.ContactRepository;
import com.geastalt.contact.repository.ContactSearchJdbcRepository;
import com.geastalt.contact.service.ExternalIdService;
import com.geastalt.contact.service.ContactSearchService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactRepository contactRepository;
    private final ContactSearchService contactSearchService;
    private final ContactSearchJdbcRepository contactSearchJdbcRepository;
    private final ExternalIdService externalIdService;
    private final JdbcTemplate jdbcTemplate;
    private final ContactSqlProperties sqlProperties;

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        long count = contactRepository.count();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ContactSearchResult>> search(
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "maxResults", defaultValue = "25") int maxResults) {

        ContactSearchService.SearchResult result = contactSearchService.searchContacts(
                lastName, firstName, maxResults, false);

        return ResponseEntity.ok(result.contacts());
    }

    @GetMapping("/by-alternate-id/{alternateId}")
    public ResponseEntity<ContactSearchResult> findByAlternateId(
            @org.springframework.web.bind.annotation.PathVariable String alternateId,
            @RequestParam(value = "type", defaultValue = "NEW_NATIONS") String type) {
        String idType;
        try {
            idType = AlternateIdTypeValidator.resolveAndValidate(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        ContactSearchResult result = contactSearchJdbcRepository.findByAlternateId(alternateId, idType);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search-by-phone")
    public ResponseEntity<List<ContactSearchResult>> searchByPhone(
            @RequestParam("phone") String phone,
            @RequestParam(value = "maxResults", defaultValue = "25") int maxResults) {
        List<ContactSearchResult> results = contactSearchJdbcRepository.searchByPhone(phone, maxResults);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/generate-alternate-ids")
    public ResponseEntity<Map<String, Object>> generateAlternateIds(
            @RequestParam(value = "batchSize", defaultValue = "10000") int batchSize) {
        log.info("Generating NEW_NATIONS alternate IDs for contacts without one (batch size: {})", batchSize);

        final int BATCH_SIZE = Math.min(batchSize, 50000); // Cap at 50k
        int totalUpdated = 0;
        int batchCount = 0;

        while (true) {
            // Fetch a batch of contact IDs that don't have a NEW_NATIONS alternate ID
            List<Long> contactIds = jdbcTemplate.queryForList(
                    sqlProperties.getController().getFindContactsWithoutAlternateId(),
                    Long.class,
                    BATCH_SIZE
            );

            if (contactIds.isEmpty()) {
                break;
            }

            // Use batch insert for efficiency
            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.batchUpdate(
                    sqlProperties.getController().getInsertAlternateId(),
                    contactIds,
                    BATCH_SIZE,
                    (PreparedStatement ps, Long contactId) -> {
                        String alternateId = externalIdService.generateExternalId(contactId);
                        ps.setLong(1, contactId);
                        ps.setString(2, alternateId);
                        ps.setTimestamp(3, now);
                    }
            );

            totalUpdated += contactIds.size();
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
