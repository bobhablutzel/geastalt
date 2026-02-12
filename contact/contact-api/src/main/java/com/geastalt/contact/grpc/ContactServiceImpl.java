/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.grpc;

import com.geastalt.contact.dto.ContactSearchResult;
import com.geastalt.contact.entity.Contact;
import com.geastalt.contact.entity.ContactAddress;
import com.geastalt.contact.entity.ContactEmail;
import com.geastalt.contact.entity.ContactLookup;
import com.geastalt.contact.entity.ContactPendingAction;
import com.geastalt.contact.entity.ContactPhone;
import com.geastalt.contact.entity.StreetAddress;
import com.geastalt.contact.repository.ContactLookupRepository;
import com.geastalt.contact.repository.ContactPendingActionRepository;
import com.geastalt.contact.repository.ContactRepository;
import com.geastalt.contact.repository.ContactSearchJdbcRepository;
import com.geastalt.contact.entity.ContactContract;
import com.geastalt.contact.entity.Contract;
import com.geastalt.contact.service.ContactAddressService;
import com.geastalt.contact.service.ContactEmailService;
import com.geastalt.contact.service.ContactPartitionContext;
import com.geastalt.contact.service.ContactPhoneService;
import com.geastalt.contact.service.ContactContractService;
import com.geastalt.contact.service.BulkContactService;
import com.geastalt.contact.service.ContactSearchService;
import com.geastalt.contact.service.PartitionAssignmentService;
import com.geastalt.contact.service.PendingActionEventPublisher;
import com.geastalt.contact.service.ContractService;
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
import java.util.UUID;

@Slf4j
@GrpcService
public class ContactServiceImpl extends ContactServiceGrpc.ContactServiceImplBase {

    private final ContactAddressService contactAddressService;
    private final ContactEmailService contactEmailService;
    private final ContactPhoneService contactPhoneService;
    private final ContactSearchService contactSearchService;
    private final ContactRepository contactRepository;
    private final ContactSearchJdbcRepository contactSearchJdbcRepository;
    private final ContactPendingActionRepository contactPendingActionRepository;
    private final PendingActionEventPublisher pendingActionEventPublisher;
    private final BulkContactService bulkContactService;
    private final ContractService contractService;
    private final ContactContractService contactContractService;
    private final PartitionAssignmentService partitionAssignmentService;
    private final ContactLookupRepository contactLookupRepository;
    private final Tracer tracer;

    private final String generateIdsTopic;

    public ContactServiceImpl(
            ContactAddressService contactAddressService,
            ContactEmailService contactEmailService,
            ContactPhoneService contactPhoneService,
            ContactSearchService contactSearchService,
            ContactRepository contactRepository,
            ContactSearchJdbcRepository contactSearchJdbcRepository,
            ContactPendingActionRepository contactPendingActionRepository,
            PendingActionEventPublisher pendingActionEventPublisher,
            BulkContactService bulkContactService,
            ContractService contractService,
            ContactContractService contactContractService,
            PartitionAssignmentService partitionAssignmentService,
            ContactLookupRepository contactLookupRepository,
            Tracer tracer,
            @Value("${contact.pending-actions.topics.generate-external-identifiers}") String generateIdsTopic) {
        this.contactAddressService = contactAddressService;
        this.contactEmailService = contactEmailService;
        this.contactPhoneService = contactPhoneService;
        this.contactSearchService = contactSearchService;
        this.contactRepository = contactRepository;
        this.contactSearchJdbcRepository = contactSearchJdbcRepository;
        this.contactPendingActionRepository = contactPendingActionRepository;
        this.pendingActionEventPublisher = pendingActionEventPublisher;
        this.bulkContactService = bulkContactService;
        this.contractService = contractService;
        this.contactContractService = contactContractService;
        this.partitionAssignmentService = partitionAssignmentService;
        this.contactLookupRepository = contactLookupRepository;
        this.tracer = tracer;
        this.generateIdsTopic = generateIdsTopic;
    }

    @Override
    public void addAddress(AddAddressRequest request,
                           StreamObserver<AddAddressResponse> responseObserver) {
        log.info("gRPC AddAddress called for contact: {}, address: {}, type: {}",
                request.getContactId(),
                request.getAddressId(),
                request.getAddressType());

        try {
            com.geastalt.contact.entity.AddressKind addressType = mapAddressType(request.getAddressType());
            ContactAddress contactAddress = contactAddressService.addAddressToContact(
                    request.getContactId(),
                    request.getAddressId(),
                    addressType
            );

            AddAddressResponse response = AddAddressResponse.newBuilder()
                    .setId(contactAddress.getId())
                    .setContactId(request.getContactId())
                    .setAddressId(contactAddress.getAddress().getId())
                    .setAddressType(request.getAddressType())
                    .setAddressDetails(buildAddressDetails(contactAddress.getAddress()))
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
        log.info("gRPC UpdateAddress called for contact: {}, address: {}, type: {}",
                request.getContactId(),
                request.getAddressId(),
                request.getAddressType());

        try {
            com.geastalt.contact.entity.AddressKind addressType = mapAddressType(request.getAddressType());
            ContactAddress contactAddress = contactAddressService.updateContactAddress(
                    request.getContactId(),
                    request.getAddressId(),
                    addressType
            );

            UpdateAddressResponse response = UpdateAddressResponse.newBuilder()
                    .setId(contactAddress.getId())
                    .setContactId(request.getContactId())
                    .setAddressId(contactAddress.getAddress().getId())
                    .setAddressType(request.getAddressType())
                    .setAddressDetails(buildAddressDetails(contactAddress.getAddress()))
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
        log.debug("gRPC GetAddresses called for contact: {}", request.getContactId());

        try {
            List<ContactAddress> addresses = contactAddressService.getContactAddresses(request.getContactId());

            GetAddressesResponse.Builder responseBuilder = GetAddressesResponse.newBuilder();

            for (ContactAddress contactAddress : addresses) {
                ContactAddressEntry entry = ContactAddressEntry.newBuilder()
                        .setId(contactAddress.getId())
                        .setAddressType(mapToGrpcAddressType(contactAddress.getAddressType()))
                        .setAddressId(contactAddress.getAddress().getId())
                        .setAddressDetails(buildAddressDetails(contactAddress.getAddress()))
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
        log.info("gRPC RemoveAddress called for contact: {}, type: {}",
                request.getContactId(),
                request.getAddressType());

        try {
            com.geastalt.contact.entity.AddressKind addressType = mapAddressType(request.getAddressType());
            contactAddressService.removeContactAddress(request.getContactId(), addressType);

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

    private com.geastalt.contact.entity.AddressKind mapAddressType(AddressType grpcType) {
        return switch (grpcType) {
            case HOME -> com.geastalt.contact.entity.AddressKind.HOME;
            case BUSINESS -> com.geastalt.contact.entity.AddressKind.BUSINESS;
            case MAILING -> com.geastalt.contact.entity.AddressKind.MAILING;
            default -> throw new IllegalArgumentException("Invalid address type: " + grpcType);
        };
    }

    private AddressType mapToGrpcAddressType(com.geastalt.contact.entity.AddressKind entityType) {
        return switch (entityType) {
            case HOME -> AddressType.HOME;
            case BUSINESS -> AddressType.BUSINESS;
            case MAILING -> AddressType.MAILING;
        };
    }

    private ContactAddressDetails buildAddressDetails(StreetAddress address) {
        ContactAddressDetails.Builder builder = ContactAddressDetails.newBuilder();

        if (address.getAddressLines() != null) {
            for (com.geastalt.contact.entity.AddressLine line : address.getAddressLines()) {
                builder.addAddressLines(line.getLineValue());
            }
        }
        if (address.getLocality() != null) {
            builder.setLocality(address.getLocality());
        }
        if (address.getAdministrativeArea() != null) {
            builder.setAdministrativeArea(address.getAdministrativeArea());
        }
        if (address.getPostalCode() != null) {
            builder.setPostalCode(address.getPostalCode());
        }
        if (address.getCountryCode() != null) {
            builder.setCountryCode(address.getCountryCode());
        }
        if (address.getSubLocality() != null) {
            builder.setSubLocality(address.getSubLocality());
        }
        if (address.getSortingCode() != null) {
            builder.setSortingCode(address.getSortingCode());
        }
        if (address.getValidated() != null) {
            builder.setValidated(address.getValidated());
        }

        return builder.build();
    }

    // Email operations

    @Override
    public void addEmail(AddEmailRequest request,
                         StreamObserver<AddEmailResponse> responseObserver) {
        log.info("gRPC AddEmail called for contact: {}, email: {}, type: {}",
                request.getContactId(),
                request.getEmail(),
                request.getEmailType());

        try {
            com.geastalt.contact.entity.AddressKind emailType = mapEmailType(request.getEmailType());
            ContactEmail contactEmail = contactEmailService.addEmailToContact(
                    request.getContactId(),
                    request.getEmail(),
                    emailType
            );

            AddEmailResponse response = AddEmailResponse.newBuilder()
                    .setId(contactEmail.getId())
                    .setContactId(request.getContactId())
                    .setEmail(contactEmail.getEmail())
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
        log.info("gRPC UpdateEmail called for contact: {}, email: {}, type: {}",
                request.getContactId(),
                request.getEmail(),
                request.getEmailType());

        try {
            com.geastalt.contact.entity.AddressKind emailType = mapEmailType(request.getEmailType());
            ContactEmail contactEmail = contactEmailService.updateContactEmail(
                    request.getContactId(),
                    request.getEmail(),
                    emailType
            );

            UpdateEmailResponse response = UpdateEmailResponse.newBuilder()
                    .setId(contactEmail.getId())
                    .setContactId(request.getContactId())
                    .setEmail(contactEmail.getEmail())
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
        log.debug("gRPC GetEmails called for contact: {}", request.getContactId());

        try {
            List<ContactEmail> emails = contactEmailService.getContactEmails(request.getContactId());

            GetEmailsResponse.Builder responseBuilder = GetEmailsResponse.newBuilder();

            for (ContactEmail contactEmail : emails) {
                ContactEmailEntry entry = ContactEmailEntry.newBuilder()
                        .setId(contactEmail.getId())
                        .setEmailType(mapToGrpcEmailType(contactEmail.getEmailType()))
                        .setEmail(contactEmail.getEmail())
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
        log.info("gRPC RemoveEmail called for contact: {}, type: {}",
                request.getContactId(),
                request.getEmailType());

        try {
            com.geastalt.contact.entity.AddressKind emailType = mapEmailType(request.getEmailType());
            contactEmailService.removeContactEmail(request.getContactId(), emailType);

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
        log.info("gRPC AddPhone called for contact: {}, phone: {}, type: {}",
                request.getContactId(),
                request.getPhoneNumber(),
                request.getPhoneType());

        try {
            com.geastalt.contact.entity.AddressKind phoneType = mapPhoneType(request.getPhoneType());
            ContactPhone contactPhone = contactPhoneService.addPhoneToContact(
                    request.getContactId(),
                    request.getPhoneNumber(),
                    phoneType
            );

            AddPhoneResponse response = AddPhoneResponse.newBuilder()
                    .setId(contactPhone.getId())
                    .setContactId(request.getContactId())
                    .setPhoneNumber(contactPhone.getPhoneNumber())
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
        log.info("gRPC UpdatePhone called for contact: {}, phone: {}, type: {}",
                request.getContactId(),
                request.getPhoneNumber(),
                request.getPhoneType());

        try {
            com.geastalt.contact.entity.AddressKind phoneType = mapPhoneType(request.getPhoneType());
            ContactPhone contactPhone = contactPhoneService.updateContactPhone(
                    request.getContactId(),
                    request.getPhoneNumber(),
                    phoneType
            );

            UpdatePhoneResponse response = UpdatePhoneResponse.newBuilder()
                    .setId(contactPhone.getId())
                    .setContactId(request.getContactId())
                    .setPhoneNumber(contactPhone.getPhoneNumber())
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
        log.debug("gRPC GetPhones called for contact: {}", request.getContactId());

        try {
            List<ContactPhone> phones = contactPhoneService.getContactPhones(request.getContactId());

            GetPhonesResponse.Builder responseBuilder = GetPhonesResponse.newBuilder();

            for (ContactPhone contactPhone : phones) {
                ContactPhoneEntry entry = ContactPhoneEntry.newBuilder()
                        .setId(contactPhone.getId())
                        .setPhoneType(mapToGrpcPhoneType(contactPhone.getPhoneType()))
                        .setPhoneNumber(contactPhone.getPhoneNumber())
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
        log.info("gRPC RemovePhone called for contact: {}, type: {}",
                request.getContactId(),
                request.getPhoneType());

        try {
            com.geastalt.contact.entity.AddressKind phoneType = mapPhoneType(request.getPhoneType());
            contactPhoneService.removeContactPhone(request.getContactId(), phoneType);

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

    private com.geastalt.contact.entity.AddressKind mapEmailType(EmailType grpcType) {
        return switch (grpcType) {
            case EMAIL_HOME -> com.geastalt.contact.entity.AddressKind.HOME;
            case EMAIL_BUSINESS -> com.geastalt.contact.entity.AddressKind.BUSINESS;
            case EMAIL_MAILING -> com.geastalt.contact.entity.AddressKind.MAILING;
            default -> throw new IllegalArgumentException("Invalid email type: " + grpcType);
        };
    }

    private EmailType mapToGrpcEmailType(com.geastalt.contact.entity.AddressKind entityType) {
        return switch (entityType) {
            case HOME -> EmailType.EMAIL_HOME;
            case BUSINESS -> EmailType.EMAIL_BUSINESS;
            case MAILING -> EmailType.EMAIL_MAILING;
        };
    }

    // Phone type mapping helpers

    private com.geastalt.contact.entity.AddressKind mapPhoneType(PhoneType grpcType) {
        return switch (grpcType) {
            case PHONE_HOME -> com.geastalt.contact.entity.AddressKind.HOME;
            case PHONE_BUSINESS -> com.geastalt.contact.entity.AddressKind.BUSINESS;
            case PHONE_MAILING -> com.geastalt.contact.entity.AddressKind.MAILING;
            default -> throw new IllegalArgumentException("Invalid phone type: " + grpcType);
        };
    }

    private PhoneType mapToGrpcPhoneType(com.geastalt.contact.entity.AddressKind entityType) {
        return switch (entityType) {
            case HOME -> PhoneType.PHONE_HOME;
            case BUSINESS -> PhoneType.PHONE_BUSINESS;
            case MAILING -> PhoneType.PHONE_MAILING;
        };
    }

    // Search operations

    @Override
    public void searchContacts(SearchContactsRequest request,
                              StreamObserver<SearchContactsResponse> responseObserver) {
        Span grpcSpan = tracer.spanBuilder("grpc.SearchContacts")
                .setAttribute("rpc.service", "ContactService")
                .setAttribute("rpc.method", "SearchContacts")
                .startSpan();

        try (Scope scope = grpcSpan.makeCurrent()) {
            log.debug("gRPC SearchContacts called: lastName={}, firstName={}, maxResults={}, includeTotalCount={}",
                    request.getLastName(),
                    request.getFirstName(),
                    request.getMaxResults(),
                    request.getIncludeTotalCount());

            // Call service layer
            ContactSearchService.SearchResult result = contactSearchService.searchContacts(
                    request.getLastName(),
                    request.getFirstName().isEmpty() ? null : request.getFirstName(),
                    request.getMaxResults(),
                    request.getIncludeTotalCount()
            );

            // Build response
            SearchContactsResponse.Builder responseBuilder = SearchContactsResponse.newBuilder()
                    .setTotalCount((int) result.totalCount());

            for (ContactSearchResult contact : result.contacts()) {
                ContactEntry.Builder entryBuilder = ContactEntry.newBuilder()
                        .setId(contact.getId())
                        .setFirstName(contact.getFirstName() != null ? contact.getFirstName() : "")
                        .setLastName(contact.getLastName() != null ? contact.getLastName() : "");

                if (contact.getPreferredEmail() != null) {
                    entryBuilder.setPreferredEmail(contact.getPreferredEmail());
                }

                if (contact.getPreferredAddress() != null) {
                    ContactSearchResult.PreferredAddress addr = contact.getPreferredAddress();
                    ContactAddressDetails.Builder addrBuilder = ContactAddressDetails.newBuilder();
                    if (addr.getAddressLines() != null) {
                        addrBuilder.addAllAddressLines(addr.getAddressLines());
                    }
                    addrBuilder
                            .setLocality(addr.getLocality() != null ? addr.getLocality() : "")
                            .setAdministrativeArea(addr.getAdministrativeArea() != null ? addr.getAdministrativeArea() : "")
                            .setPostalCode(addr.getPostalCode() != null ? addr.getPostalCode() : "")
                            .setCountryCode(addr.getCountryCode() != null ? addr.getCountryCode() : "");
                    entryBuilder.setPreferredAddress(addrBuilder.build());
                }

                responseBuilder.addContacts(entryBuilder.build());
            }
            SearchContactsResponse response = responseBuilder.build();

            grpcSpan.setAttribute("response.contactsCount", response.getContactsCount());
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
            log.error("Error searching contacts", e);
            grpcSpan.setStatus(StatusCode.ERROR, e.getMessage());
            grpcSpan.recordException(e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to search contacts: " + e.getMessage())
                    .asRuntimeException());
        } finally {
            grpcSpan.end();
        }
    }

    @Override
    public void getContactByAlternateId(GetContactByAlternateIdRequest request,
                                       StreamObserver<GetContactByAlternateIdResponse> responseObserver) {
        String idType = com.geastalt.contact.validation.AlternateIdTypeValidator.resolveAndValidate(request.getType());
        log.debug("gRPC GetContactByAlternateId called: alternateId={}, type={}", request.getAlternateId(), idType);

        try {
            ContactSearchResult contact = contactSearchJdbcRepository.findByAlternateId(request.getAlternateId(), idType);

            if (contact == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Contact not found with alternate ID: " + request.getAlternateId())
                        .asRuntimeException());
                return;
            }

            ContactEntry entry = buildContactEntry(contact);
            GetContactByAlternateIdResponse response = GetContactByAlternateIdResponse.newBuilder()
                    .setContact(entry)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting contact by alternate ID", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contact: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getContactById(GetContactByIdRequest request,
                              StreamObserver<GetContactByIdResponse> responseObserver) {
        log.debug("gRPC GetContactById called: contactId={}", request.getContactId());

        try {
            ContactSearchResult contact = contactSearchJdbcRepository.findById(request.getContactId());

            if (contact == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Contact not found with ID: " + request.getContactId())
                        .asRuntimeException());
                return;
            }

            ContactEntry entry = buildContactEntry(contact);
            GetContactByIdResponse response = GetContactByIdResponse.newBuilder()
                    .setContact(entry)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting contact by ID", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contact: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void searchContactsByPhone(SearchContactsByPhoneRequest request,
                                      StreamObserver<SearchContactsByPhoneResponse> responseObserver) {
        log.debug("gRPC SearchContactsByPhone called: phone={}, maxResults={}",
                request.getPhoneNumber(), request.getMaxResults());

        try {
            int maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : 25;
            List<ContactSearchResult> contacts = contactSearchJdbcRepository.searchByPhone(
                    request.getPhoneNumber(), maxResults);

            SearchContactsByPhoneResponse.Builder responseBuilder = SearchContactsByPhoneResponse.newBuilder();
            for (ContactSearchResult contact : contacts) {
                responseBuilder.addContacts(buildContactEntry(contact));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error searching contacts by phone", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to search contacts: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void hasPendingAction(HasPendingActionRequest request,
                                  StreamObserver<HasPendingActionResponse> responseObserver) {
        log.debug("gRPC HasPendingAction called: contactId={}, actionType={}",
                request.getContactId(), request.getActionType());

        try {
            if (request.getActionType() == PendingActionType.PENDING_ACTION_TYPE_UNSPECIFIED) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Action type must be specified")
                        .asRuntimeException());
                return;
            }

            com.geastalt.contact.entity.PendingActionType actionType = mapPendingActionType(request.getActionType());
            boolean hasPending = contactPendingActionRepository.existsByContactIdAndActionType(
                    request.getContactId(), actionType);

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

    private com.geastalt.contact.entity.PendingActionType mapPendingActionType(PendingActionType grpcType) {
        return switch (grpcType) {
            case GENERATE_EXTERNAL_IDENTIFIERS -> com.geastalt.contact.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS;
            default -> throw new IllegalArgumentException("Invalid pending action type: " + grpcType);
        };
    }

    @Override
    public void createContact(CreateContactRequest request,
                              StreamObserver<CreateContactResponse> responseObserver) {
        log.info("gRPC CreateContact called: firstName={}, lastName={}, companyName={}, skipGenerateExternalIdentifiers={}",
                request.getFirstName(), request.getLastName(), request.getCompanyName(),
                request.getSkipGenerateExternalIdentifiers());

        try {
            // Validate required fields
            if (request.getLastName() == null || request.getLastName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Last name is required")
                        .asRuntimeException());
                return;
            }

            // Determine partition before insert
            ContactPartitionContext partitionContext = new ContactPartitionContext(
                    request.getFirstName(),
                    request.getLastName(),
                    request.getCompanyName());
            int partitionNumber = partitionAssignmentService.assignPartition(partitionContext);

            // Create and save the contact
            Contact contact = new Contact();
            contact.setFirstName(request.getFirstName());
            contact.setLastName(request.getLastName());
            contact = contactRepository.save(contact);

            // Save partition lookup
            ContactLookup lookup = ContactLookup.builder()
                    .contactId(contact.getId())
                    .partitionNumber(partitionNumber)
                    .build();
            contactLookupRepository.save(lookup);

            // Add pending actions based on flags and publish to Kafka
            if (!request.getSkipGenerateExternalIdentifiers()) {
                ContactPendingAction action = ContactPendingAction.builder()
                        .contact(contact)
                        .actionType(com.geastalt.contact.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS)
                        .build();
                contactPendingActionRepository.save(action);
                pendingActionEventPublisher.publishPendingAction(
                        contact.getId(),
                        com.geastalt.contact.entity.PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS);
            }

            // Fetch the complete contact data for response
            ContactSearchResult contactResult = contactSearchJdbcRepository.findById(contact.getId());
            ContactEntry entry = buildContactEntry(contactResult);

            CreateContactResponse response = CreateContactResponse.newBuilder()
                    .setContact(entry)
                    .setPartitionNumber(partitionNumber)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error creating contact", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to create contact: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private ContactEntry buildContactEntry(ContactSearchResult contact) {
        ContactEntry.Builder entryBuilder = ContactEntry.newBuilder()
                .setId(contact.getId())
                .setFirstName(contact.getFirstName() != null ? contact.getFirstName() : "")
                .setLastName(contact.getLastName() != null ? contact.getLastName() : "");

        // Add all alternate IDs
        if (contact.getAlternateIds() != null) {
            for (var entry : contact.getAlternateIds().entrySet()) {
                entryBuilder.addAlternateIds(AlternateIdEntry.newBuilder()
                        .setType(entry.getKey())
                        .setAlternateId(entry.getValue())
                        .build());
            }
        }

        if (contact.getPreferredEmail() != null) {
            entryBuilder.setPreferredEmail(contact.getPreferredEmail());
        }

        if (contact.getPreferredAddress() != null) {
            ContactSearchResult.PreferredAddress addr = contact.getPreferredAddress();
            ContactAddressDetails.Builder addrBuilder = ContactAddressDetails.newBuilder();
            if (addr.getAddressLines() != null) {
                addrBuilder.addAllAddressLines(addr.getAddressLines());
            }
            addrBuilder
                    .setLocality(addr.getLocality() != null ? addr.getLocality() : "")
                    .setAdministrativeArea(addr.getAdministrativeArea() != null ? addr.getAdministrativeArea() : "")
                    .setPostalCode(addr.getPostalCode() != null ? addr.getPostalCode() : "")
                    .setCountryCode(addr.getCountryCode() != null ? addr.getCountryCode() : "");
            entryBuilder.setPreferredAddress(addrBuilder.build());
        }

        return entryBuilder.build();
    }

    @Override
    public void bulkCreateContacts(BulkCreateContactsRequest request,
                                   StreamObserver<BulkCreateContactsResponse> responseObserver) {
        log.info("gRPC BulkCreateContacts called: contactCount={}, skipGenerateExternalIdentifiers={}",
                request.getContactsCount(),
                request.getSkipGenerateExternalIdentifiers());

        try {
            if (request.getContactsCount() == 0) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("At least one contact must be provided")
                        .asRuntimeException());
                return;
            }

            BulkCreateContactsResponse response = bulkContactService.bulkCreateContacts(
                    request,
                    generateIdsTopic
            );

            log.info("BulkCreateContacts completed: total={}, success={}, failure={}",
                    response.getTotalCount(),
                    response.getSuccessCount(),
                    response.getFailureCount());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in bulk create contacts", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to bulk create contacts: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Partition operations

    @Override
    public void getContactPartition(GetContactPartitionRequest request,
                                    StreamObserver<GetContactPartitionResponse> responseObserver) {
        log.debug("gRPC GetContactPartition called: contactId={}", request.getContactId());

        try {
            Optional<ContactLookup> lookup = contactLookupRepository.findById(request.getContactId());

            if (lookup.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("No partition assignment found for contact ID: " + request.getContactId())
                        .asRuntimeException());
                return;
            }

            GetContactPartitionResponse response = GetContactPartitionResponse.newBuilder()
                    .setContactId(request.getContactId())
                    .setPartitionNumber(lookup.get().getPartitionNumber())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting contact partition", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contact partition: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // Contract operations

    @Override
    public void createContract(CreateContractRequest request,
                               StreamObserver<CreateContractResponse> responseObserver) {
        log.info("gRPC CreateContract called: contractName={}, companyId={}, companyName={}",
                request.getContractName(), request.getCompanyId(), request.getCompanyName());

        try {
            if (request.getContractName() == null || request.getContractName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Contract name is required")
                        .asRuntimeException());
                return;
            }
            if (request.getCompanyName() == null || request.getCompanyName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Company name is required")
                        .asRuntimeException());
                return;
            }

            UUID companyId = parseUuid(request.getCompanyId(), "company_id");

            Contract contract = contractService.createContract(
                    request.getContractName(),
                    companyId,
                    request.getCompanyName());

            CreateContractResponse response = CreateContractResponse.newBuilder()
                    .setContract(buildContractEntry(contract))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error creating contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to create contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateContract(UpdateContractRequest request,
                               StreamObserver<UpdateContractResponse> responseObserver) {
        log.info("gRPC UpdateContract called: contractId={}", request.getContractId());

        try {
            if (request.getContractName() == null || request.getContractName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Contract name is required")
                        .asRuntimeException());
                return;
            }
            if (request.getCompanyName() == null || request.getCompanyName().isBlank()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Company name is required")
                        .asRuntimeException());
                return;
            }

            UUID contractId = parseUuid(request.getContractId(), "contract_id");
            UUID companyId = parseUuid(request.getCompanyId(), "company_id");

            Contract contract = contractService.updateContract(
                    contractId,
                    request.getContractName(),
                    companyId,
                    request.getCompanyName());

            UpdateContractResponse response = UpdateContractResponse.newBuilder()
                    .setContract(buildContractEntry(contract))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error updating contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getContract(GetContractRequest request,
                            StreamObserver<GetContractResponse> responseObserver) {
        log.debug("gRPC GetContract called: contractId={}", request.getContractId());

        try {
            UUID contractId = parseUuid(request.getContractId(), "contract_id");
            Contract contract = contractService.getContract(contractId);

            GetContractResponse response = GetContractResponse.newBuilder()
                    .setContract(buildContractEntry(contract))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getContracts(GetContractsRequest request,
                             StreamObserver<GetContractsResponse> responseObserver) {
        log.debug("gRPC GetContracts called");

        try {
            List<Contract> contracts = contractService.getAllContracts();

            GetContractsResponse.Builder responseBuilder = GetContractsResponse.newBuilder();
            for (Contract contract : contracts) {
                responseBuilder.addContracts(buildContractEntry(contract));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting contracts", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contracts: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void deleteContract(DeleteContractRequest request,
                               StreamObserver<DeleteContractResponse> responseObserver) {
        log.info("gRPC DeleteContract called: contractId={}", request.getContractId());

        try {
            UUID contractId = parseUuid(request.getContractId(), "contract_id");
            contractService.deleteContract(contractId);

            DeleteContractResponse response = DeleteContractResponse.newBuilder()
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
            log.error("Error deleting contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to delete contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private ContractEntry buildContractEntry(Contract contract) {
        return ContractEntry.newBuilder()
                .setContractId(contract.getId().toString())
                .setContractName(contract.getContractName())
                .setCompanyId(contract.getCompanyId().toString())
                .setCompanyName(contract.getCompanyName())
                .build();
    }

    // Contact contract operations

    @Override
    public void addContactContract(AddContactContractRequest request,
                                   StreamObserver<AddContactContractResponse> responseObserver) {
        log.info("gRPC AddContactContract called: contactId={}, contractId={}, effectiveDate={}, expirationDate={}",
                request.getContactId(), request.getContractId(),
                request.getEffectiveDate(), request.getExpirationDate());

        try {
            OffsetDateTime effectiveDate = parseDateTime(request.getEffectiveDate(), "effective_date");
            OffsetDateTime expirationDate = parseDateTime(request.getExpirationDate(), "expiration_date");
            UUID contractId = parseUuid(request.getContractId(), "contract_id");

            ContactContract contactContract = contactContractService.addContactContract(
                    request.getContactId(),
                    contractId,
                    effectiveDate,
                    expirationDate);

            AddContactContractResponse response = AddContactContractResponse.newBuilder()
                    .setContactContract(buildContactContractEntry(contactContract))
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
            log.error("Error adding contact contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to add contact contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateContactContract(UpdateContactContractRequest request,
                                      StreamObserver<UpdateContactContractResponse> responseObserver) {
        log.info("gRPC UpdateContactContract called: id={}, contractId={}, effectiveDate={}, expirationDate={}",
                request.getId(), request.getContractId(),
                request.getEffectiveDate(), request.getExpirationDate());

        try {
            OffsetDateTime effectiveDate = parseDateTime(request.getEffectiveDate(), "effective_date");
            OffsetDateTime expirationDate = parseDateTime(request.getExpirationDate(), "expiration_date");
            UUID contractId = parseUuid(request.getContractId(), "contract_id");

            ContactContract contactContract = contactContractService.updateContactContract(
                    request.getId(),
                    contractId,
                    effectiveDate,
                    expirationDate);

            UpdateContactContractResponse response = UpdateContactContractResponse.newBuilder()
                    .setContactContract(buildContactContractEntry(contactContract))
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
            log.error("Error updating contact contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to update contact contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getContactContracts(GetContactContractsRequest request,
                                    StreamObserver<GetContactContractsResponse> responseObserver) {
        log.debug("gRPC GetContactContracts called: contactId={}", request.getContactId());

        try {
            List<ContactContract> contactContracts = contactContractService.getContactContracts(request.getContactId());

            GetContactContractsResponse.Builder responseBuilder = GetContactContractsResponse.newBuilder();
            for (ContactContract contactContract : contactContracts) {
                responseBuilder.addContactContracts(buildContactContractEntry(contactContract));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting contact contracts", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get contact contracts: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getCurrentContactContract(GetCurrentContactContractRequest request,
                                          StreamObserver<GetCurrentContactContractResponse> responseObserver) {
        log.debug("gRPC GetCurrentContactContract called: contactId={}", request.getContactId());

        try {
            Optional<ContactContract> currentContract = contactContractService.getCurrentContactContract(request.getContactId());

            GetCurrentContactContractResponse.Builder responseBuilder = GetCurrentContactContractResponse.newBuilder();
            currentContract.ifPresent(contactContract -> responseBuilder.setContactContract(buildContactContractEntry(contactContract)));

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting current contact contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get current contact contract: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void removeContactContract(RemoveContactContractRequest request,
                                      StreamObserver<RemoveContactContractResponse> responseObserver) {
        log.info("gRPC RemoveContactContract called: contactId={}, id={}",
                request.getContactId(), request.getId());

        try {
            contactContractService.removeContactContract(request.getContactId(), request.getId());

            RemoveContactContractResponse response = RemoveContactContractResponse.newBuilder()
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
            log.error("Error removing contact contract", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to remove contact contract: " + e.getMessage())
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

    private UUID parseUuid(String uuidStr, String fieldName) {
        if (uuidStr == null || uuidStr.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }

    private ContactContractEntry buildContactContractEntry(ContactContract contactContract) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean isCurrent = !contactContract.getEffectiveDate().isAfter(now)
                && contactContract.getExpirationDate().isAfter(now);

        return ContactContractEntry.newBuilder()
                .setId(contactContract.getId())
                .setContactId(contactContract.getContact().getId())
                .setContract(buildContractEntry(contactContract.getContract()))
                .setEffectiveDate(contactContract.getEffectiveDate().atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .setExpirationDate(contactContract.getExpirationDate().atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .setIsCurrent(isCurrent)
                .build();
    }
}
