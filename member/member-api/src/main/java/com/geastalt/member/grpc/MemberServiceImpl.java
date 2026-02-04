package com.geastalt.member.grpc;

import com.geastalt.member.dto.MemberSearchResult;
import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberAddress;
import com.geastalt.member.entity.MemberEmail;
import com.geastalt.member.entity.MemberLookup;
import com.geastalt.member.entity.MemberPendingAction;
import com.geastalt.member.entity.MemberPhone;
import com.geastalt.member.entity.StandardizedAddress;
import com.geastalt.member.repository.MemberLookupRepository;
import com.geastalt.member.repository.MemberPendingActionRepository;
import com.geastalt.member.repository.MemberRepository;
import com.geastalt.member.repository.MemberSearchJdbcRepository;
import com.geastalt.member.entity.MemberPlan;
import com.geastalt.member.entity.Plan;
import com.geastalt.member.service.MemberAddressService;
import com.geastalt.member.service.MemberEmailService;
import com.geastalt.member.service.MemberPartitionContext;
import com.geastalt.member.service.MemberPhoneService;
import com.geastalt.member.service.MemberPlanService;
import com.geastalt.member.service.BulkMemberService;
import com.geastalt.member.service.MemberSearchService;
import com.geastalt.member.service.PartitionAssignmentService;
import com.geastalt.member.service.PendingActionEventPublisher;
import com.geastalt.member.service.PlanService;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@GrpcService
public class MemberServiceImpl extends MemberServiceGrpc.MemberServiceImplBase {

    private final MemberAddressService memberAddressService;
    private final MemberEmailService memberEmailService;
    private final MemberPhoneService memberPhoneService;
    private final MemberSearchService memberSearchService;
    private final MemberRepository memberRepository;
    private final MemberSearchJdbcRepository memberSearchJdbcRepository;
    private final MemberPendingActionRepository memberPendingActionRepository;
    private final PendingActionEventPublisher pendingActionEventPublisher;
    private final BulkMemberService bulkMemberService;
    private final PlanService planService;
    private final MemberPlanService memberPlanService;
    private final PartitionAssignmentService partitionAssignmentService;
    private final MemberLookupRepository memberLookupRepository;
    private final Tracer tracer;

    private final String generateIdsTopic;
    private final String validateAddressTopic;

    public MemberServiceImpl(
            MemberAddressService memberAddressService,
            MemberEmailService memberEmailService,
            MemberPhoneService memberPhoneService,
            MemberSearchService memberSearchService,
            MemberRepository memberRepository,
            MemberSearchJdbcRepository memberSearchJdbcRepository,
            MemberPendingActionRepository memberPendingActionRepository,
            PendingActionEventPublisher pendingActionEventPublisher,
            BulkMemberService bulkMemberService,
            PlanService planService,
            MemberPlanService memberPlanService,
            PartitionAssignmentService partitionAssignmentService,
            MemberLookupRepository memberLookupRepository,
            Tracer tracer,
            @Value("${member.pending-actions.topics.generate-external-identifiers}") String generateIdsTopic,
            @Value("${member.pending-actions.topics.validate-address}") String validateAddressTopic) {
        this.memberAddressService = memberAddressService;
        this.memberEmailService = memberEmailService;
        this.memberPhoneService = memberPhoneService;
        this.memberSearchService = memberSearchService;
        this.memberRepository = memberRepository;
        this.memberSearchJdbcRepository = memberSearchJdbcRepository;
        this.memberPendingActionRepository = memberPendingActionRepository;
        this.pendingActionEventPublisher = pendingActionEventPublisher;
        this.bulkMemberService = bulkMemberService;
        this.planService = planService;
        this.memberPlanService = memberPlanService;
        this.partitionAssignmentService = partitionAssignmentService;
        this.memberLookupRepository = memberLookupRepository;
        this.tracer = tracer;
        this.generateIdsTopic = generateIdsTopic;
        this.validateAddressTopic = validateAddressTopic;
    }

    @Override
    public void addAddress(AddAddressRequest request,
                           StreamObserver<AddAddressResponse> responseObserver) {
        log.info("gRPC AddAddress called for member: {}, address: {}, type: {}",
                request.getMemberId(),
                request.getAddressId(),
                request.getAddressType());

        try {
            com.geastalt.member.entity.AddressType addressType = mapAddressType(request.getAddressType());
            MemberAddress memberAddress = memberAddressService.addAddressToMember(
                    request.getMemberId(),
                    request.getAddressId(),
                    addressType
            );

            AddAddressResponse response = AddAddressResponse.newBuilder()
                    .setId(memberAddress.getId())
                    .setMemberId(request.getMemberId())
                    .setAddressId(memberAddress.getAddress().getId())
                    .setAddressType(request.getAddressType())
                    .setAddressDetails(buildAddressDetails(memberAddress.getAddress()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error adding address", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to add address: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateAddress(UpdateAddressRequest request,
                              StreamObserver<UpdateAddressResponse> responseObserver) {
        log.info("gRPC UpdateAddress called for member: {}, address: {}, type: {}",
                request.getMemberId(),
                request.getAddressId(),
                request.getAddressType());

        try {
            com.geastalt.member.entity.AddressType addressType = mapAddressType(request.getAddressType());
            MemberAddress memberAddress = memberAddressService.updateMemberAddress(
                    request.getMemberId(),
                    request.getAddressId(),
                    addressType
            );

            UpdateAddressResponse response = UpdateAddressResponse.newBuilder()
                    .setId(memberAddress.getId())
                    .setMemberId(request.getMemberId())
                    .setAddressId(memberAddress.getAddress().getId())
                    .setAddressType(request.getAddressType())
                    .setAddressDetails(buildAddressDetails(memberAddress.getAddress()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating address", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update address: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getAddresses(GetAddressesRequest request,
                             StreamObserver<GetAddressesResponse> responseObserver) {
        log.debug("gRPC GetAddresses called for member: {}", request.getMemberId());

        try {
            List<MemberAddress> addresses = memberAddressService.getMemberAddresses(request.getMemberId());

            GetAddressesResponse.Builder responseBuilder = GetAddressesResponse.newBuilder();

            for (MemberAddress memberAddress : addresses) {
                MemberAddressEntry entry = MemberAddressEntry.newBuilder()
                        .setId(memberAddress.getId())
                        .setAddressType(mapToGrpcAddressType(memberAddress.getAddressType()))
                        .setAddressId(memberAddress.getAddress().getId())
                        .setAddressDetails(buildAddressDetails(memberAddress.getAddress()))
                        .build();
                responseBuilder.addAddresses(entry);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting addresses", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get addresses: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void removeAddress(RemoveAddressRequest request,
                              StreamObserver<RemoveAddressResponse> responseObserver) {
        log.info("gRPC RemoveAddress called for member: {}, type: {}",
                request.getMemberId(),
                request.getAddressType());

        try {
            com.geastalt.member.entity.AddressType addressType = mapAddressType(request.getAddressType());
            memberAddressService.removeMemberAddress(request.getMemberId(), addressType);

            RemoveAddressResponse response = RemoveAddressResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error removing address", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to remove address: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private com.geastalt.member.entity.AddressType mapAddressType(AddressType grpcType) {
        return switch (grpcType) {
            case HOME -> com.geastalt.member.entity.AddressType.HOME;
            case BUSINESS -> com.geastalt.member.entity.AddressType.BUSINESS;
            case MAILING -> com.geastalt.member.entity.AddressType.MAILING;
            default -> throw new IllegalArgumentException("Invalid address type: " + grpcType);
        };
    }

    private AddressType mapToGrpcAddressType(com.geastalt.member.entity.AddressType entityType) {
        return switch (entityType) {
            case HOME -> AddressType.HOME;
            case BUSINESS -> AddressType.BUSINESS;
            case MAILING -> AddressType.MAILING;
        };
    }

    private MemberAddressDetails buildAddressDetails(StandardizedAddress address) {
        MemberAddressDetails.Builder builder = MemberAddressDetails.newBuilder();

        if (address.getStreetAddress() != null) {
            builder.setStreetAddress(address.getStreetAddress());
        }
        if (address.getSecondaryAddress() != null) {
            builder.setSecondaryAddress(address.getSecondaryAddress());
        }
        if (address.getCity() != null) {
            builder.setCity(address.getCity());
        }
        if (address.getState() != null) {
            builder.setState(address.getState());
        }
        if (address.getZipCode() != null) {
            builder.setZipCode(address.getZipCode());
        }
        if (address.getZipPlus4() != null) {
            builder.setZipPlus4(address.getZipPlus4());
        }

        return builder.build();
    }

    // Email operations

    @Override
    public void addEmail(AddEmailRequest request,
                         StreamObserver<AddEmailResponse> responseObserver) {
        log.info("gRPC AddEmail called for member: {}, email: {}, type: {}",
                request.getMemberId(),
                request.getEmail(),
                request.getEmailType());

        try {
            com.geastalt.member.entity.AddressType emailType = mapEmailType(request.getEmailType());
            MemberEmail memberEmail = memberEmailService.addEmailToMember(
                    request.getMemberId(),
                    request.getEmail(),
                    emailType
            );

            AddEmailResponse response = AddEmailResponse.newBuilder()
                    .setId(memberEmail.getId())
                    .setMemberId(request.getMemberId())
                    .setEmail(memberEmail.getEmail())
                    .setEmailType(request.getEmailType())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error adding email", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to add email: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateEmail(UpdateEmailRequest request,
                            StreamObserver<UpdateEmailResponse> responseObserver) {
        log.info("gRPC UpdateEmail called for member: {}, email: {}, type: {}",
                request.getMemberId(),
                request.getEmail(),
                request.getEmailType());

        try {
            com.geastalt.member.entity.AddressType emailType = mapEmailType(request.getEmailType());
            MemberEmail memberEmail = memberEmailService.updateMemberEmail(
                    request.getMemberId(),
                    request.getEmail(),
                    emailType
            );

            UpdateEmailResponse response = UpdateEmailResponse.newBuilder()
                    .setId(memberEmail.getId())
                    .setMemberId(request.getMemberId())
                    .setEmail(memberEmail.getEmail())
                    .setEmailType(request.getEmailType())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating email", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update email: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getEmails(GetEmailsRequest request,
                          StreamObserver<GetEmailsResponse> responseObserver) {
        log.debug("gRPC GetEmails called for member: {}", request.getMemberId());

        try {
            List<MemberEmail> emails = memberEmailService.getMemberEmails(request.getMemberId());

            GetEmailsResponse.Builder responseBuilder = GetEmailsResponse.newBuilder();

            for (MemberEmail memberEmail : emails) {
                MemberEmailEntry entry = MemberEmailEntry.newBuilder()
                        .setId(memberEmail.getId())
                        .setEmailType(mapToGrpcEmailType(memberEmail.getEmailType()))
                        .setEmail(memberEmail.getEmail())
                        .build();
                responseBuilder.addEmails(entry);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting emails", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get emails: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void removeEmail(RemoveEmailRequest request,
                            StreamObserver<RemoveEmailResponse> responseObserver) {
        log.info("gRPC RemoveEmail called for member: {}, type: {}",
                request.getMemberId(),
                request.getEmailType());

        try {
            com.geastalt.member.entity.AddressType emailType = mapEmailType(request.getEmailType());
            memberEmailService.removeMemberEmail(request.getMemberId(), emailType);

            RemoveEmailResponse response = RemoveEmailResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error removing email", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to remove email: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Phone operations

    @Override
    public void addPhone(AddPhoneRequest request,
                         StreamObserver<AddPhoneResponse> responseObserver) {
        log.info("gRPC AddPhone called for member: {}, phone: {}, type: {}",
                request.getMemberId(),
                request.getPhoneNumber(),
                request.getPhoneType());

        try {
            com.geastalt.member.entity.AddressType phoneType = mapPhoneType(request.getPhoneType());
            MemberPhone memberPhone = memberPhoneService.addPhoneToMember(
                    request.getMemberId(),
                    request.getPhoneNumber(),
                    phoneType
            );

            AddPhoneResponse response = AddPhoneResponse.newBuilder()
                    .setId(memberPhone.getId())
                    .setMemberId(request.getMemberId())
                    .setPhoneNumber(memberPhone.getPhoneNumber())
                    .setPhoneType(request.getPhoneType())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error adding phone", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to add phone: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updatePhone(UpdatePhoneRequest request,
                            StreamObserver<UpdatePhoneResponse> responseObserver) {
        log.info("gRPC UpdatePhone called for member: {}, phone: {}, type: {}",
                request.getMemberId(),
                request.getPhoneNumber(),
                request.getPhoneType());

        try {
            com.geastalt.member.entity.AddressType phoneType = mapPhoneType(request.getPhoneType());
            MemberPhone memberPhone = memberPhoneService.updateMemberPhone(
                    request.getMemberId(),
                    request.getPhoneNumber(),
                    phoneType
            );

            UpdatePhoneResponse response = UpdatePhoneResponse.newBuilder()
                    .setId(memberPhone.getId())
                    .setMemberId(request.getMemberId())
                    .setPhoneNumber(memberPhone.getPhoneNumber())
                    .setPhoneType(request.getPhoneType())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating phone", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update phone: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPhones(GetPhonesRequest request,
                          StreamObserver<GetPhonesResponse> responseObserver) {
        log.debug("gRPC GetPhones called for member: {}", request.getMemberId());

        try {
            List<MemberPhone> phones = memberPhoneService.getMemberPhones(request.getMemberId());

            GetPhonesResponse.Builder responseBuilder = GetPhonesResponse.newBuilder();

            for (MemberPhone memberPhone : phones) {
                MemberPhoneEntry entry = MemberPhoneEntry.newBuilder()
                        .setId(memberPhone.getId())
                        .setPhoneType(mapToGrpcPhoneType(memberPhone.getPhoneType()))
                        .setPhoneNumber(memberPhone.getPhoneNumber())
                        .build();
                responseBuilder.addPhones(entry);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting phones", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get phones: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void removePhone(RemovePhoneRequest request,
                            StreamObserver<RemovePhoneResponse> responseObserver) {
        log.info("gRPC RemovePhone called for member: {}, type: {}",
                request.getMemberId(),
                request.getPhoneType());

        try {
            com.geastalt.member.entity.AddressType phoneType = mapPhoneType(request.getPhoneType());
            memberPhoneService.removeMemberPhone(request.getMemberId(), phoneType);

            RemovePhoneResponse response = RemovePhoneResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error removing phone", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to remove phone: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Email type mapping helpers

    private com.geastalt.member.entity.AddressType mapEmailType(EmailType grpcType) {
        return switch (grpcType) {
            case EMAIL_HOME -> com.geastalt.member.entity.AddressType.HOME;
            case EMAIL_BUSINESS -> com.geastalt.member.entity.AddressType.BUSINESS;
            case EMAIL_MAILING -> com.geastalt.member.entity.AddressType.MAILING;
            default -> throw new IllegalArgumentException("Invalid email type: " + grpcType);
        };
    }

    private EmailType mapToGrpcEmailType(com.geastalt.member.entity.AddressType entityType) {
        return switch (entityType) {
            case HOME -> EmailType.EMAIL_HOME;
            case BUSINESS -> EmailType.EMAIL_BUSINESS;
            case MAILING -> EmailType.EMAIL_MAILING;
        };
    }

    // Phone type mapping helpers

    private com.geastalt.member.entity.AddressType mapPhoneType(PhoneType grpcType) {
        return switch (grpcType) {
            case PHONE_HOME -> com.geastalt.member.entity.AddressType.HOME;
            case PHONE_BUSINESS -> com.geastalt.member.entity.AddressType.BUSINESS;
            case PHONE_MAILING -> com.geastalt.member.entity.AddressType.MAILING;
            default -> throw new IllegalArgumentException("Invalid phone type: " + grpcType);
        };
    }

    private PhoneType mapToGrpcPhoneType(com.geastalt.member.entity.AddressType entityType) {
        return switch (entityType) {
            case HOME -> PhoneType.PHONE_HOME;
            case BUSINESS -> PhoneType.PHONE_BUSINESS;
            case MAILING -> PhoneType.PHONE_MAILING;
        };
    }

    // Search operations

    @Override
    public void searchMembers(SearchMembersRequest request,
                              StreamObserver<SearchMembersResponse> responseObserver) {
        Span grpcSpan = tracer.spanBuilder("grpc.SearchMembers")
                .setAttribute("rpc.service", "MemberService")
                .setAttribute("rpc.method", "SearchMembers")
                .startSpan();

        try (Scope scope = grpcSpan.makeCurrent()) {
            log.debug("gRPC SearchMembers called: lastName={}, firstName={}, maxResults={}, includeTotalCount={}",
                    request.getLastName(),
                    request.getFirstName(),
                    request.getMaxResults(),
                    request.getIncludeTotalCount());

            // Call service layer
            MemberSearchService.SearchResult result = memberSearchService.searchMembers(
                    request.getLastName(),
                    request.getFirstName().isEmpty() ? null : request.getFirstName(),
                    request.getMaxResults(),
                    request.getIncludeTotalCount()
            );

            // Build response
            SearchMembersResponse.Builder responseBuilder = SearchMembersResponse.newBuilder()
                    .setTotalCount((int) result.totalCount());

            for (MemberSearchResult member : result.members()) {
                MemberEntry.Builder entryBuilder = MemberEntry.newBuilder()
                        .setId(member.getId())
                        .setFirstName(member.getFirstName() != null ? member.getFirstName() : "")
                        .setLastName(member.getLastName() != null ? member.getLastName() : "");

                if (member.getPreferredEmail() != null) {
                    entryBuilder.setPreferredEmail(member.getPreferredEmail());
                }

                if (member.getPreferredAddress() != null) {
                    MemberSearchResult.PreferredAddress addr = member.getPreferredAddress();
                    entryBuilder.setPreferredAddress(MemberAddressDetails.newBuilder()
                            .setStreetAddress(addr.getStreetAddress() != null ? addr.getStreetAddress() : "")
                            .setSecondaryAddress(addr.getSecondaryAddress() != null ? addr.getSecondaryAddress() : "")
                            .setCity(addr.getCity() != null ? addr.getCity() : "")
                            .setState(addr.getState() != null ? addr.getState() : "")
                            .setZipCode(addr.getZipCode() != null ? addr.getZipCode() : "")
                            .setZipPlus4(addr.getZipPlus4() != null ? addr.getZipPlus4() : "")
                            .build());
                }

                responseBuilder.addMembers(entryBuilder.build());
            }
            SearchMembersResponse response = responseBuilder.build();

            grpcSpan.setAttribute("response.membersCount", response.getMembersCount());
            grpcSpan.setStatus(StatusCode.OK);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            grpcSpan.setStatus(StatusCode.ERROR, e.getMessage());
            grpcSpan.recordException(e);
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error searching members", e);
            grpcSpan.setStatus(StatusCode.ERROR, e.getMessage());
            grpcSpan.recordException(e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to search members: " + e.getMessage())
                    .asRuntimeException());
        } finally {
            grpcSpan.end();
        }
    }

    @Override
    public void getMemberByAlternateId(GetMemberByAlternateIdRequest request,
                                       StreamObserver<GetMemberByAlternateIdResponse> responseObserver) {
        // Default to NEW_NATIONS if type not specified
        com.geastalt.member.entity.AlternateIdType idType = mapAlternateIdType(request.getType());
        log.debug("gRPC GetMemberByAlternateId called: alternateId={}, type={}", request.getAlternateId(), idType);

        try {
            MemberSearchResult member = memberSearchJdbcRepository.findByAlternateId(request.getAlternateId(), idType);

            if (member == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Member not found with alternate ID: " + request.getAlternateId())
                        .asRuntimeException());
                return;
            }

            MemberEntry entry = buildMemberEntry(member);
            GetMemberByAlternateIdResponse response = GetMemberByAlternateIdResponse.newBuilder()
                    .setMember(entry)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting member by alternate ID", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get member: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getMemberById(GetMemberByIdRequest request,
                              StreamObserver<GetMemberByIdResponse> responseObserver) {
        log.debug("gRPC GetMemberById called: memberId={}", request.getMemberId());

        try {
            MemberSearchResult member = memberSearchJdbcRepository.findById(request.getMemberId());

            if (member == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Member not found with ID: " + request.getMemberId())
                        .asRuntimeException());
                return;
            }

            MemberEntry entry = buildMemberEntry(member);
            GetMemberByIdResponse response = GetMemberByIdResponse.newBuilder()
                    .setMember(entry)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting member by ID", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get member: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private com.geastalt.member.entity.AlternateIdType mapAlternateIdType(AlternateIdType grpcType) {
        return switch (grpcType) {
            case NEW_NATIONS -> com.geastalt.member.entity.AlternateIdType.NEW_NATIONS;
            case OLD_NATIONS -> com.geastalt.member.entity.AlternateIdType.OLD_NATIONS;
            case PAN_HASH -> com.geastalt.member.entity.AlternateIdType.PAN_HASH;
            case MEMBER_TUPLE -> com.geastalt.member.entity.AlternateIdType.MEMBER_TUPLE;
            default -> com.geastalt.member.entity.AlternateIdType.NEW_NATIONS; // Default for unspecified
        };
    }

    private AlternateIdType mapToGrpcAlternateIdType(com.geastalt.member.entity.AlternateIdType entityType) {
        return switch (entityType) {
            case NEW_NATIONS -> AlternateIdType.NEW_NATIONS;
            case OLD_NATIONS -> AlternateIdType.OLD_NATIONS;
            case PAN_HASH -> AlternateIdType.PAN_HASH;
            case MEMBER_TUPLE -> AlternateIdType.MEMBER_TUPLE;
        };
    }

    @Override
    public void searchMembersByPhone(SearchMembersByPhoneRequest request,
                                      StreamObserver<SearchMembersByPhoneResponse> responseObserver) {
        log.debug("gRPC SearchMembersByPhone called: phone={}, maxResults={}",
                request.getPhoneNumber(), request.getMaxResults());

        try {
            int maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : 25;
            List<MemberSearchResult> members = memberSearchJdbcRepository.searchByPhone(
                    request.getPhoneNumber(), maxResults);

            SearchMembersByPhoneResponse.Builder responseBuilder = SearchMembersByPhoneResponse.newBuilder();
            for (MemberSearchResult member : members) {
                responseBuilder.addMembers(buildMemberEntry(member));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error searching members by phone", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to search members: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void hasPendingAction(HasPendingActionRequest request,
                                  StreamObserver<HasPendingActionResponse> responseObserver) {
        log.debug("gRPC HasPendingAction called: memberId={}, actionType={}",
                request.getMemberId(), request.getActionType());

        try {
            if (request.getActionType() == PendingActionType.PENDING_ACTION_TYPE_UNSPECIFIED) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Action type must be specified")
                        .asRuntimeException());
                return;
            }

            com.geastalt.member.entity.PendingActionType actionType = mapPendingActionType(request.getActionType());
            boolean hasPending = memberPendingActionRepository.existsByMemberIdAndActionType(
                    request.getMemberId(), actionType);

            HasPendingActionResponse response = HasPendingActionResponse.newBuilder()
                    .setHasPendingAction(hasPending)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error checking pending action", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to check pending action: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private com.geastalt.member.entity.PendingActionType mapPendingActionType(PendingActionType grpcType) {
        return switch (grpcType) {
            case GENERATE_EXTERNAL_IDENTIFIERS -> com.geastalt.member.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS;
            case VALIDATE_ADDRESS -> com.geastalt.member.entity.PendingActionType.VALIDATE_ADDRESS;
            default -> throw new IllegalArgumentException("Invalid pending action type: " + grpcType);
        };
    }

    @Override
    public void createMember(CreateMemberRequest request,
                              StreamObserver<CreateMemberResponse> responseObserver) {
        log.info("gRPC CreateMember called: firstName={}, lastName={}, carrierName={}, skipGenerateExternalIdentifiers={}, skipValidateAddress={}",
                request.getFirstName(), request.getLastName(), request.getCarrierName(),
                request.getSkipGenerateExternalIdentifiers(), request.getSkipValidateAddress());

        try {
            // Validate required fields
            if (request.getLastName() == null || request.getLastName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Last name is required")
                        .asRuntimeException());
                return;
            }

            // Determine partition before insert
            MemberPartitionContext partitionContext = new MemberPartitionContext(
                    request.getFirstName(),
                    request.getLastName(),
                    request.getCarrierName());
            int partitionNumber = partitionAssignmentService.assignPartition(partitionContext);

            // Create and save the member
            Member member = new Member();
            member.setFirstName(request.getFirstName());
            member.setLastName(request.getLastName());
            member = memberRepository.save(member);

            // Save partition lookup
            MemberLookup lookup = MemberLookup.builder()
                    .memberId(member.getId())
                    .partitionNumber(partitionNumber)
                    .build();
            memberLookupRepository.save(lookup);

            // Add pending actions based on flags and publish to Kafka
            if (!request.getSkipGenerateExternalIdentifiers()) {
                MemberPendingAction action = MemberPendingAction.builder()
                        .member(member)
                        .actionType(com.geastalt.member.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS)
                        .build();
                memberPendingActionRepository.save(action);
                pendingActionEventPublisher.publishPendingAction(
                        member.getId(),
                        com.geastalt.member.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS);
            }

            if (!request.getSkipValidateAddress()) {
                MemberPendingAction action = MemberPendingAction.builder()
                        .member(member)
                        .actionType(com.geastalt.member.entity.PendingActionType.VALIDATE_ADDRESS)
                        .build();
                memberPendingActionRepository.save(action);
                pendingActionEventPublisher.publishPendingAction(
                        member.getId(),
                        com.geastalt.member.entity.PendingActionType.VALIDATE_ADDRESS);
            }

            // Fetch the complete member data for response
            MemberSearchResult memberResult = memberSearchJdbcRepository.findById(member.getId());
            MemberEntry entry = buildMemberEntry(memberResult);

            CreateMemberResponse response = CreateMemberResponse.newBuilder()
                    .setMember(entry)
                    .setPartitionNumber(partitionNumber)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error creating member", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to create member: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private MemberEntry buildMemberEntry(MemberSearchResult member) {
        MemberEntry.Builder entryBuilder = MemberEntry.newBuilder()
                .setId(member.getId())
                .setFirstName(member.getFirstName() != null ? member.getFirstName() : "")
                .setLastName(member.getLastName() != null ? member.getLastName() : "");

        // Add all alternate IDs
        if (member.getAlternateIds() != null) {
            for (var entry : member.getAlternateIds().entrySet()) {
                try {
                    com.geastalt.member.entity.AlternateIdType entityType =
                            com.geastalt.member.entity.AlternateIdType.valueOf(entry.getKey());
                    entryBuilder.addAlternateIds(AlternateIdEntry.newBuilder()
                            .setType(mapToGrpcAlternateIdType(entityType))
                            .setAlternateId(entry.getValue())
                            .build());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown alternate ID type: {}", entry.getKey());
                }
            }
        }

        if (member.getPreferredEmail() != null) {
            entryBuilder.setPreferredEmail(member.getPreferredEmail());
        }

        if (member.getPreferredAddress() != null) {
            MemberSearchResult.PreferredAddress addr = member.getPreferredAddress();
            entryBuilder.setPreferredAddress(MemberAddressDetails.newBuilder()
                    .setStreetAddress(addr.getStreetAddress() != null ? addr.getStreetAddress() : "")
                    .setSecondaryAddress(addr.getSecondaryAddress() != null ? addr.getSecondaryAddress() : "")
                    .setCity(addr.getCity() != null ? addr.getCity() : "")
                    .setState(addr.getState() != null ? addr.getState() : "")
                    .setZipCode(addr.getZipCode() != null ? addr.getZipCode() : "")
                    .setZipPlus4(addr.getZipPlus4() != null ? addr.getZipPlus4() : "")
                    .build());
        }

        return entryBuilder.build();
    }

    @Override
    public void bulkCreateMembers(BulkCreateMembersRequest request,
                                   StreamObserver<BulkCreateMembersResponse> responseObserver) {
        log.info("gRPC BulkCreateMembers called: memberCount={}, skipGenerateExternalIdentifiers={}, skipValidateAddress={}",
                request.getMembersCount(),
                request.getSkipGenerateExternalIdentifiers(),
                request.getSkipValidateAddress());

        try {
            if (request.getMembersCount() == 0) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("At least one member must be provided")
                        .asRuntimeException());
                return;
            }

            BulkCreateMembersResponse response = bulkMemberService.bulkCreateMembers(
                    request,
                    generateIdsTopic,
                    validateAddressTopic
            );

            log.info("BulkCreateMembers completed: total={}, success={}, failure={}",
                    response.getTotalCount(),
                    response.getSuccessCount(),
                    response.getFailureCount());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in bulk create members", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to bulk create members: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Partition operations

    @Override
    public void getMemberPartition(GetMemberPartitionRequest request,
                                    StreamObserver<GetMemberPartitionResponse> responseObserver) {
        log.debug("gRPC GetMemberPartition called: memberId={}", request.getMemberId());

        try {
            Optional<MemberLookup> lookup = memberLookupRepository.findById(request.getMemberId());

            if (lookup.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("No partition assignment found for member ID: " + request.getMemberId())
                        .asRuntimeException());
                return;
            }

            GetMemberPartitionResponse response = GetMemberPartitionResponse.newBuilder()
                    .setMemberId(request.getMemberId())
                    .setPartitionNumber(lookup.get().getPartitionNumber())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting member partition", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get member partition: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Plan operations

    @Override
    public void createPlan(CreatePlanRequest request,
                           StreamObserver<CreatePlanResponse> responseObserver) {
        log.info("gRPC CreatePlan called: planName={}, carrierId={}, carrierName={}",
                request.getPlanName(), request.getCarrierId(), request.getCarrierName());

        try {
            if (request.getPlanName() == null || request.getPlanName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Plan name is required")
                        .asRuntimeException());
                return;
            }
            if (request.getCarrierName() == null || request.getCarrierName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Carrier name is required")
                        .asRuntimeException());
                return;
            }

            Plan plan = planService.createPlan(
                    request.getPlanName(),
                    request.getCarrierId(),
                    request.getCarrierName());

            CreatePlanResponse response = CreatePlanResponse.newBuilder()
                    .setPlan(buildPlanEntry(plan))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error creating plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to create plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updatePlan(UpdatePlanRequest request,
                           StreamObserver<UpdatePlanResponse> responseObserver) {
        log.info("gRPC UpdatePlan called: planId={}", request.getPlanId());

        try {
            if (request.getPlanName() == null || request.getPlanName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Plan name is required")
                        .asRuntimeException());
                return;
            }
            if (request.getCarrierName() == null || request.getCarrierName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Carrier name is required")
                        .asRuntimeException());
                return;
            }

            Plan plan = planService.updatePlan(
                    request.getPlanId(),
                    request.getPlanName(),
                    request.getCarrierId(),
                    request.getCarrierName());

            UpdatePlanResponse response = UpdatePlanResponse.newBuilder()
                    .setPlan(buildPlanEntry(plan))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPlan(GetPlanRequest request,
                        StreamObserver<GetPlanResponse> responseObserver) {
        log.debug("gRPC GetPlan called: planId={}", request.getPlanId());

        try {
            Plan plan = planService.getPlan(request.getPlanId());

            GetPlanResponse response = GetPlanResponse.newBuilder()
                    .setPlan(buildPlanEntry(plan))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPlans(GetPlansRequest request,
                         StreamObserver<GetPlansResponse> responseObserver) {
        log.debug("gRPC GetPlans called");

        try {
            List<Plan> plans = planService.getAllPlans();

            GetPlansResponse.Builder responseBuilder = GetPlansResponse.newBuilder();
            for (Plan plan : plans) {
                responseBuilder.addPlans(buildPlanEntry(plan));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting plans", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get plans: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void deletePlan(DeletePlanRequest request,
                           StreamObserver<DeletePlanResponse> responseObserver) {
        log.info("gRPC DeletePlan called: planId={}", request.getPlanId());

        try {
            planService.deletePlan(request.getPlanId());

            DeletePlanResponse response = DeletePlanResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error deleting plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to delete plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private PlanEntry buildPlanEntry(Plan plan) {
        return PlanEntry.newBuilder()
                .setPlanId(plan.getId())
                .setPlanName(plan.getPlanName())
                .setCarrierId(plan.getCarrierId())
                .setCarrierName(plan.getCarrierName())
                .build();
    }

    // Member plan operations

    @Override
    public void addMemberPlan(AddMemberPlanRequest request,
                              StreamObserver<AddMemberPlanResponse> responseObserver) {
        log.info("gRPC AddMemberPlan called: memberId={}, planId={}, effectiveDate={}, expirationDate={}",
                request.getMemberId(), request.getPlanId(),
                request.getEffectiveDate(), request.getExpirationDate());

        try {
            OffsetDateTime effectiveDate = parseDateTime(request.getEffectiveDate(), "effective_date");
            OffsetDateTime expirationDate = parseDateTime(request.getExpirationDate(), "expiration_date");

            MemberPlan memberPlan = memberPlanService.addMemberPlan(
                    request.getMemberId(),
                    request.getPlanId(),
                    effectiveDate,
                    expirationDate);

            AddMemberPlanResponse response = AddMemberPlanResponse.newBuilder()
                    .setMemberPlan(buildMemberPlanEntry(memberPlan))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error adding member plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to add member plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateMemberPlan(UpdateMemberPlanRequest request,
                                 StreamObserver<UpdateMemberPlanResponse> responseObserver) {
        log.info("gRPC UpdateMemberPlan called: id={}, planId={}, effectiveDate={}, expirationDate={}",
                request.getId(), request.getPlanId(),
                request.getEffectiveDate(), request.getExpirationDate());

        try {
            OffsetDateTime effectiveDate = parseDateTime(request.getEffectiveDate(), "effective_date");
            OffsetDateTime expirationDate = parseDateTime(request.getExpirationDate(), "expiration_date");

            MemberPlan memberPlan = memberPlanService.updateMemberPlan(
                    request.getId(),
                    request.getPlanId(),
                    effectiveDate,
                    expirationDate);

            UpdateMemberPlanResponse response = UpdateMemberPlanResponse.newBuilder()
                    .setMemberPlan(buildMemberPlanEntry(memberPlan))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating member plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update member plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getMemberPlans(GetMemberPlansRequest request,
                               StreamObserver<GetMemberPlansResponse> responseObserver) {
        log.debug("gRPC GetMemberPlans called: memberId={}", request.getMemberId());

        try {
            List<MemberPlan> memberPlans = memberPlanService.getMemberPlans(request.getMemberId());

            GetMemberPlansResponse.Builder responseBuilder = GetMemberPlansResponse.newBuilder();
            for (MemberPlan memberPlan : memberPlans) {
                responseBuilder.addMemberPlans(buildMemberPlanEntry(memberPlan));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting member plans", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get member plans: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getCurrentMemberPlan(GetCurrentMemberPlanRequest request,
                                     StreamObserver<GetCurrentMemberPlanResponse> responseObserver) {
        log.debug("gRPC GetCurrentMemberPlan called: memberId={}", request.getMemberId());

        try {
            Optional<MemberPlan> currentPlan = memberPlanService.getCurrentMemberPlan(request.getMemberId());

            GetCurrentMemberPlanResponse.Builder responseBuilder = GetCurrentMemberPlanResponse.newBuilder();
            currentPlan.ifPresent(memberPlan -> responseBuilder.setMemberPlan(buildMemberPlanEntry(memberPlan)));

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting current member plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get current member plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void removeMemberPlan(RemoveMemberPlanRequest request,
                                 StreamObserver<RemoveMemberPlanResponse> responseObserver) {
        log.info("gRPC RemoveMemberPlan called: memberId={}, id={}",
                request.getMemberId(), request.getId());

        try {
            memberPlanService.removeMemberPlan(request.getMemberId(), request.getId());

            RemoveMemberPlanResponse response = RemoveMemberPlanResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error removing member plan", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to remove member plan: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private OffsetDateTime parseDateTime(String dateTimeStr, String fieldName) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return OffsetDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be in ISO 8601 UTC format (e.g., 2024-01-01T00:00:00Z)");
        }
    }

    private MemberPlanEntry buildMemberPlanEntry(MemberPlan memberPlan) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean isCurrent = !memberPlan.getEffectiveDate().isAfter(now)
                && memberPlan.getExpirationDate().isAfter(now);

        return MemberPlanEntry.newBuilder()
                .setId(memberPlan.getId())
                .setMemberId(memberPlan.getMember().getId())
                .setPlan(buildPlanEntry(memberPlan.getPlan()))
                .setEffectiveDate(memberPlan.getEffectiveDate().atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .setExpirationDate(memberPlan.getExpirationDate().atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .setIsCurrent(isCurrent)
                .build();
    }
}
