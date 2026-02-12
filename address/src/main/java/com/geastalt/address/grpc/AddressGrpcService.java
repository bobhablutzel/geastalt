/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.grpc;

import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerifier;
import com.geastalt.address.grpc.generated.*;
import com.geastalt.address.provider.ProviderRegistry;
import com.geastalt.address.provider.ValidationProvider;
import com.geastalt.address.provider.ValidationRequest;
import com.geastalt.address.provider.ValidationResult;
import com.geastalt.address.service.AddressFormatService;
import com.geastalt.address.service.AddressValidationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AddressGrpcService extends AddressServiceGrpc.AddressServiceImplBase {

    private final AddressValidationService validationService;
    private final AddressFormatService formatService;
    private final ProviderRegistry providerRegistry;

    @Override
    public void validateAddress(ValidateAddressRequest request,
                                StreamObserver<ValidateAddressResponse> responseObserver) {
        PostalAddress addr = request.getAddress();
        String countryCode = addr.getCountryCode();
        String providerOverride = request.hasProviderId() ? request.getProviderId() : null;

        Optional<ValidationProvider> provider = validationService.findProvider(countryCode, providerOverride);

        if (provider.isEmpty()) {
            responseObserver.onNext(ValidateAddressResponse.newBuilder()
                    .setStatus(ValidationStatus.PROVIDER_UNAVAILABLE)
                    .setMessage("No validation provider available for country: " + countryCode)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        ValidationRequest validationRequest = ValidationRequest.builder()
                .countryCode(countryCode)
                .addressLines(addr.getAddressLinesList())
                .locality(addr.getLocality())
                .administrativeArea(addr.getAdministrativeArea())
                .postalCode(addr.getPostalCode())
                .subLocality(addr.hasSubLocality() ? addr.getSubLocality() : null)
                .sortingCode(addr.hasSortingCode() ? addr.getSortingCode() : null)
                .organization(addr.hasOrganization() ? addr.getOrganization() : null)
                .recipient(addr.hasRecipient() ? addr.getRecipient() : null)
                .build();

        AddressValidationService.Result result = validationService.validate(validationRequest, provider.get());
        ValidationResult vr = result.validationResult();

        ValidateAddressResponse.Builder responseBuilder = ValidateAddressResponse.newBuilder()
                .setStatus(mapValidationStatus(vr.getStatus()))
                .setProviderId(result.providerId());

        if (vr.getMessage() != null) {
            responseBuilder.setMessage(vr.getMessage());
        }

        if (vr.getMetadata() != null) {
            responseBuilder.putAllMetadata(vr.getMetadata());
        }

        if (vr.getAddressLines() != null) {
            PostalAddress.Builder standardized = PostalAddress.newBuilder()
                    .setCountryCode(vr.getCountryCode() != null ? vr.getCountryCode() : countryCode)
                    .addAllAddressLines(vr.getAddressLines());
            if (vr.getLocality() != null) standardized.setLocality(vr.getLocality());
            if (vr.getAdministrativeArea() != null) standardized.setAdministrativeArea(vr.getAdministrativeArea());
            if (vr.getPostalCode() != null) standardized.setPostalCode(vr.getPostalCode());
            if (vr.getSubLocality() != null) standardized.setSubLocality(vr.getSubLocality());
            if (vr.getSortingCode() != null) standardized.setSortingCode(vr.getSortingCode());
            responseBuilder.setStandardizedAddress(standardized.build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void verifyAddressFormat(VerifyAddressFormatRequest request,
                                    StreamObserver<VerifyAddressFormatResponse> responseObserver) {
        PostalAddress addr = request.getAddress();
        String countryCode = addr.getCountryCode();

        Optional<FormatVerifier> verifier = formatService.findVerifier(countryCode);

        if (verifier.isEmpty()) {
            responseObserver.onNext(VerifyAddressFormatResponse.newBuilder()
                    .setStatus(FormatStatus.FORMAT_UNSUPPORTED_COUNTRY)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        FormatVerificationResult result = formatService.verify(
                verifier.get(),
                countryCode,
                addr.getAddressLinesList(),
                addr.getLocality(),
                addr.getAdministrativeArea(),
                addr.getPostalCode(),
                addr.hasSubLocality() ? addr.getSubLocality() : null,
                addr.hasSortingCode() ? addr.getSortingCode() : null
        );

        VerifyAddressFormatResponse.Builder responseBuilder = VerifyAddressFormatResponse.newBuilder()
                .setStatus(mapFormatStatus(result.getStatus()));

        PostalAddress.Builder corrected = PostalAddress.newBuilder()
                .setCountryCode(countryCode);
        if (result.getAddressLines() != null) corrected.addAllAddressLines(result.getAddressLines());
        if (result.getLocality() != null) corrected.setLocality(result.getLocality());
        if (result.getAdministrativeArea() != null) corrected.setAdministrativeArea(result.getAdministrativeArea());
        if (result.getPostalCode() != null) corrected.setPostalCode(result.getPostalCode());
        if (result.getSubLocality() != null) corrected.setSubLocality(result.getSubLocality());
        if (result.getSortingCode() != null) corrected.setSortingCode(result.getSortingCode());
        responseBuilder.setCorrectedAddress(corrected.build());

        for (FormatVerificationResult.FormatIssueItem issue : result.getIssues()) {
            responseBuilder.addIssues(FormatIssue.newBuilder()
                    .setField(issue.getField())
                    .setSeverity(mapSeverity(issue.getSeverity()))
                    .setMessage(issue.getMessage())
                    .setOriginalValue(issue.getOriginalValue())
                    .setCorrectedValue(issue.getCorrectedValue())
                    .build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getProviders(GetProvidersRequest request,
                             StreamObserver<GetProvidersResponse> responseObserver) {
        GetProvidersResponse.Builder response = GetProvidersResponse.newBuilder();

        for (ValidationProvider provider : providerRegistry.getAllProviders()) {
            response.addProviders(ProviderInfo.newBuilder()
                    .setProviderId(provider.getProviderId())
                    .setDisplayName(provider.getDisplayName())
                    .addAllSupportedCountries(provider.getSupportedCountries())
                    .setEnabled(provider.isEnabled())
                    .build());
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private ValidationStatus mapValidationStatus(ValidationResult.Status status) {
        return switch (status) {
            case VALIDATED -> ValidationStatus.VALIDATED;
            case VALIDATED_WITH_CORRECTIONS -> ValidationStatus.VALIDATED_WITH_CORRECTIONS;
            case INVALID -> ValidationStatus.INVALID;
            case PROVIDER_ERROR -> ValidationStatus.PROVIDER_ERROR;
        };
    }

    private FormatStatus mapFormatStatus(FormatVerificationResult.Status status) {
        return switch (status) {
            case VALID -> FormatStatus.FORMAT_VALID;
            case CORRECTED -> FormatStatus.FORMAT_CORRECTED;
            case INVALID -> FormatStatus.FORMAT_INVALID;
        };
    }

    private FormatIssueSeverity mapSeverity(FormatVerificationResult.Severity severity) {
        return switch (severity) {
            case ERROR -> FormatIssueSeverity.ERROR;
            case WARNING -> FormatIssueSeverity.WARNING;
            case INFO -> FormatIssueSeverity.INFO;
        };
    }
}
