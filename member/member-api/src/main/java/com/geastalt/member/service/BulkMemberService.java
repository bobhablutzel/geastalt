package com.geastalt.member.service;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.PendingActionType;
import com.geastalt.member.grpc.*;
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
public class BulkMemberService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<Long, String> kafkaTemplate;
    private final PartitionAssignmentService partitionAssignmentService;

    private static final String INSERT_MEMBER_SQL =
            "INSERT INTO members (first_name, last_name) VALUES (?, ?)";

    private static final String INSERT_PHONE_SQL =
            "INSERT INTO member_phones (member_id, phone_number, phone_type) VALUES (?, ?, ?)";

    private static final String INSERT_EMAIL_SQL =
            "INSERT INTO member_emails (member_id, email, email_type, preferred) VALUES (?, ?, ?, ?)";

    private static final String INSERT_ADDRESS_SQL =
            "INSERT INTO standardized_addresses (street_address, secondary_address, city, state, zip_code, zip_plus4) " +
            "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";

    private static final String INSERT_MEMBER_ADDRESS_SQL =
            "INSERT INTO member_addresses (member_id, address_id, address_type, preferred) VALUES (?, ?, ?, ?)";

    private static final String INSERT_PENDING_ACTION_SQL =
            "INSERT INTO member_pending_actions (member_id, action_type, created_at) VALUES (?, ?, ?)";

    private static final String INSERT_MEMBER_LOOKUP_SQL =
            "INSERT INTO member_lookup (member_id, partition_number, created_at) VALUES (?, ?, ?)";

    public record MemberCreationResult(Long memberId, int partitionNumber) {}

    @Transactional
    public BulkCreateMembersResponse bulkCreateMembers(
            BulkCreateMembersRequest request,
            String generateIdsTopic,
            String validateAddressTopic) {

        List<BulkCreateResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < request.getMembersCount(); i++) {
            MemberInput input = request.getMembers(i);
            try {
                MemberCreationResult creationResult = createMemberWithDetails(
                        input,
                        !request.getSkipGenerateExternalIdentifiers(),
                        !request.getSkipValidateAddress(),
                        generateIdsTopic,
                        validateAddressTopic
                );

                results.add(BulkCreateResult.newBuilder()
                        .setIndex(i)
                        .setSuccess(true)
                        .setMemberId(creationResult.memberId())
                        .setPartitionNumber(creationResult.partitionNumber())
                        .build());
                successCount++;

            } catch (Exception e) {
                log.error("Error creating member at index {}: {}", i, e.getMessage());
                results.add(BulkCreateResult.newBuilder()
                        .setIndex(i)
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .build());
                failureCount++;
            }
        }

        return BulkCreateMembersResponse.newBuilder()
                .addAllResults(results)
                .setTotalCount(request.getMembersCount())
                .setSuccessCount(successCount)
                .setFailureCount(failureCount)
                .build();
    }

    private MemberCreationResult createMemberWithDetails(
            MemberInput input,
            boolean addGenerateIdsAction,
            boolean addValidateAddressAction,
            String generateIdsTopic,
            String validateAddressTopic) {

        // Validate required fields
        if (input.getLastName() == null || input.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }

        // Determine partition before insert
        MemberPartitionContext partitionContext = new MemberPartitionContext(
                input.getFirstName(),
                input.getLastName(),
                input.getCarrierName());
        int partitionNumber = partitionAssignmentService.assignPartition(partitionContext);

        // Insert member and get generated ID
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_MEMBER_SQL, new String[]{"id"});
            ps.setString(1, input.getFirstName());
            ps.setString(2, input.getLastName());
            return ps;
        }, keyHolder);

        Long memberId = keyHolder.getKey().longValue();
        log.debug("Created member with ID: {}", memberId);

        // Insert partition lookup
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(INSERT_MEMBER_LOOKUP_SQL, memberId, partitionNumber, now);

        // Batch insert phones
        if (input.getPhonesCount() > 0) {
            insertPhones(memberId, input.getPhonesList());
        }

        // Batch insert emails
        if (input.getEmailsCount() > 0) {
            insertEmails(memberId, input.getEmailsList());
        }

        // Insert addresses (with standardized address creation)
        if (input.getAddressesCount() > 0) {
            insertAddresses(memberId, input.getAddressesList());
        }

        // Insert pending actions and publish to Kafka
        if (addGenerateIdsAction) {
            jdbcTemplate.update(INSERT_PENDING_ACTION_SQL, memberId,
                    PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS.name(), now);
            kafkaTemplate.send(generateIdsTopic, memberId, String.valueOf(memberId));
        }

        if (addValidateAddressAction) {
            jdbcTemplate.update(INSERT_PENDING_ACTION_SQL, memberId,
                    PendingActionType.VALIDATE_ADDRESS.name(), now);
            kafkaTemplate.send(validateAddressTopic, memberId, String.valueOf(memberId));
        }

        return new MemberCreationResult(memberId, partitionNumber);
    }

    private void insertPhones(Long memberId, List<PhoneInput> phones) {
        List<Object[]> batchArgs = new ArrayList<>();
        for (PhoneInput phone : phones) {
            AddressType phoneType = mapPhoneType(phone.getPhoneType());
            batchArgs.add(new Object[]{memberId, phone.getPhoneNumber(), phoneType.name()});
        }
        jdbcTemplate.batchUpdate(INSERT_PHONE_SQL, batchArgs);
        log.debug("Inserted {} phones for member {}", phones.size(), memberId);
    }

    private void insertEmails(Long memberId, List<EmailInput> emails) {
        List<Object[]> batchArgs = new ArrayList<>();
        boolean firstEmail = true;
        for (EmailInput email : emails) {
            AddressType emailType = mapEmailType(email.getEmailType());
            batchArgs.add(new Object[]{memberId, email.getEmail(), emailType.name(), firstEmail});
            firstEmail = false;
        }
        jdbcTemplate.batchUpdate(INSERT_EMAIL_SQL, batchArgs);
        log.debug("Inserted {} emails for member {}", emails.size(), memberId);
    }

    private void insertAddresses(Long memberId, List<AddressInput> addresses) {
        boolean firstAddress = true;
        for (AddressInput address : addresses) {
            // Insert or find standardized address
            Long addressId = jdbcTemplate.queryForObject(INSERT_ADDRESS_SQL, Long.class,
                    address.getStreetAddress(),
                    address.getSecondaryAddress().isEmpty() ? null : address.getSecondaryAddress(),
                    address.getCity(),
                    address.getState(),
                    address.getZipCode(),
                    address.getZipPlus4().isEmpty() ? null : address.getZipPlus4()
            );

            // Insert member address link
            AddressType addressType = mapAddressType(address.getAddressType());
            jdbcTemplate.update(INSERT_MEMBER_ADDRESS_SQL, memberId, addressId, addressType.name(), firstAddress);
            firstAddress = false;
        }
        log.debug("Inserted {} addresses for member {}", addresses.size(), memberId);
    }

    private AddressType mapPhoneType(PhoneType grpcType) {
        return switch (grpcType) {
            case PHONE_HOME -> AddressType.HOME;
            case PHONE_BUSINESS -> AddressType.BUSINESS;
            case PHONE_MAILING -> AddressType.MAILING;
            default -> AddressType.HOME;
        };
    }

    private AddressType mapEmailType(EmailType grpcType) {
        return switch (grpcType) {
            case EMAIL_HOME -> AddressType.HOME;
            case EMAIL_BUSINESS -> AddressType.BUSINESS;
            case EMAIL_MAILING -> AddressType.MAILING;
            default -> AddressType.HOME;
        };
    }

    private AddressType mapAddressType(com.geastalt.member.grpc.AddressType grpcType) {
        return switch (grpcType) {
            case HOME -> AddressType.HOME;
            case BUSINESS -> AddressType.BUSINESS;
            case MAILING -> AddressType.MAILING;
            default -> AddressType.HOME;
        };
    }
}
