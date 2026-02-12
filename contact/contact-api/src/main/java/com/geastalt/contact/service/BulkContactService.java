/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.config.ContactSqlProperties;
import com.geastalt.contact.entity.AddressKind;
import com.geastalt.contact.entity.PendingActionType;
import com.geastalt.contact.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkContactService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<Long, String> kafkaTemplate;
    private final PartitionAssignmentService partitionAssignmentService;
    private final ContactSqlProperties sqlProperties;

    public record ContactCreationResult(Long contactId, int partitionNumber) {}

    @Transactional
    public BulkCreateContactsResponse bulkCreateContacts(
            BulkCreateContactsRequest request,
            String generateIdsTopic) {

        List<BulkCreateResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < request.getContactsCount(); i++) {
            ContactInput input = request.getContacts(i);
            try {
                ContactCreationResult creationResult = createContactWithDetails(
                        input,
                        !request.getSkipGenerateExternalIdentifiers(),
                        generateIdsTopic
                );

                results.add(BulkCreateResult.newBuilder()
                        .setIndex(i)
                        .setSuccess(true)
                        .setContactId(creationResult.contactId())
                        .setPartitionNumber(creationResult.partitionNumber())
                        .build());
                successCount++;

            } catch (Exception e) {
                log.error("Error creating contact at index {}: {}", i, e.getMessage());
                results.add(BulkCreateResult.newBuilder()
                        .setIndex(i)
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .build());
                failureCount++;
            }
        }

        return BulkCreateContactsResponse.newBuilder()
                .addAllResults(results)
                .setTotalCount(request.getContactsCount())
                .setSuccessCount(successCount)
                .setFailureCount(failureCount)
                .build();
    }

    private ContactCreationResult createContactWithDetails(
            ContactInput input,
            boolean addGenerateIdsAction,
            String generateIdsTopic) {

        // Validate required fields
        if (input.getLastName() == null || input.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }

        // Determine partition before insert
        ContactPartitionContext partitionContext = new ContactPartitionContext(
                input.getFirstName(),
                input.getLastName(),
                input.getCompanyName());
        int partitionNumber = partitionAssignmentService.assignPartition(partitionContext);

        // Insert contact and get generated ID
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sqlProperties.getBulk().getInsertContact(), new String[]{"id"});
            ps.setString(1, input.getFirstName());
            ps.setString(2, input.getLastName());
            return ps;
        }, keyHolder);

        Long contactId = keyHolder.getKey().longValue();
        log.debug("Created contact with ID: {}", contactId);

        // Insert partition lookup
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sqlProperties.getBulk().getInsertContactLookup(), contactId, partitionNumber, now);

        // Batch insert phones
        if (input.getPhonesCount() > 0) {
            insertPhones(contactId, input.getPhonesList());
        }

        // Batch insert emails
        if (input.getEmailsCount() > 0) {
            insertEmails(contactId, input.getEmailsList());
        }

        // Insert addresses (with standardized address creation)
        if (input.getAddressesCount() > 0) {
            insertAddresses(contactId, input.getAddressesList());
        }

        // Insert pending actions and publish to Kafka
        if (addGenerateIdsAction) {
            jdbcTemplate.update(sqlProperties.getBulk().getInsertPendingAction(), contactId,
                    PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS.name(), now);
            kafkaTemplate.send(generateIdsTopic, contactId, String.valueOf(contactId));
        }

        return new ContactCreationResult(contactId, partitionNumber);
    }

    private void insertPhones(Long contactId, List<PhoneInput> phones) {
        List<Object[]> batchArgs = new ArrayList<>();
        for (PhoneInput phone : phones) {
            AddressKind phoneType = mapPhoneType(phone.getPhoneType());
            batchArgs.add(new Object[]{contactId, phone.getPhoneNumber(), phoneType.name()});
        }
        jdbcTemplate.batchUpdate(sqlProperties.getBulk().getInsertPhone(), batchArgs);
        log.debug("Inserted {} phones for contact {}", phones.size(), contactId);
    }

    private void insertEmails(Long contactId, List<EmailInput> emails) {
        List<Object[]> batchArgs = new ArrayList<>();
        boolean firstEmail = true;
        for (EmailInput email : emails) {
            AddressKind emailType = mapEmailType(email.getEmailType());
            batchArgs.add(new Object[]{contactId, email.getEmail(), emailType.name(), firstEmail});
            firstEmail = false;
        }
        jdbcTemplate.batchUpdate(sqlProperties.getBulk().getInsertEmail(), batchArgs);
        log.debug("Inserted {} emails for contact {}", emails.size(), contactId);
    }

    private void insertAddresses(Long contactId, List<AddressInput> addresses) {
        boolean firstAddress = true;
        for (AddressInput address : addresses) {
            // Insert address (without lines) and get generated ID
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        sqlProperties.getBulk().getInsertAddress(), new String[]{"id"});
                ps.setString(1, address.getLocality());
                ps.setString(2, address.getAdministrativeArea());
                ps.setString(3, address.getPostalCode().isEmpty() ? null : address.getPostalCode());
                ps.setString(4, address.getCountryCode().isEmpty() ? "US" : address.getCountryCode());
                return ps;
            }, keyHolder);

            Long addressId = keyHolder.getKey().longValue();

            // Insert address lines
            List<String> lines = address.getAddressLinesList();
            for (int i = 0; i < lines.size(); i++) {
                final int lineOrder = i + 1;
                final String lineValue = lines.get(i);
                jdbcTemplate.update(sqlProperties.getBulk().getInsertAddressLine(),
                        addressId, lineOrder, lineValue);
            }

            // Insert contact address link
            AddressKind addressType = mapAddressType(address.getAddressType());
            jdbcTemplate.update(sqlProperties.getBulk().getInsertContactAddress(),
                    contactId, addressId, addressType.name(), firstAddress);
            firstAddress = false;
        }
        log.debug("Inserted {} addresses for contact {}", addresses.size(), contactId);
    }

    private AddressKind mapPhoneType(PhoneType grpcType) {
        return switch (grpcType) {
            case PHONE_HOME -> AddressKind.HOME;
            case PHONE_BUSINESS -> AddressKind.BUSINESS;
            case PHONE_MAILING -> AddressKind.MAILING;
            default -> AddressKind.HOME;
        };
    }

    private AddressKind mapEmailType(EmailType grpcType) {
        return switch (grpcType) {
            case EMAIL_HOME -> AddressKind.HOME;
            case EMAIL_BUSINESS -> AddressKind.BUSINESS;
            case EMAIL_MAILING -> AddressKind.MAILING;
            default -> AddressKind.HOME;
        };
    }

    private AddressKind mapAddressType(com.geastalt.contact.grpc.AddressType grpcType) {
        return switch (grpcType) {
            case HOME -> AddressKind.HOME;
            case BUSINESS -> AddressKind.BUSINESS;
            case MAILING -> AddressKind.MAILING;
            default -> AddressKind.HOME;
        };
    }

}
