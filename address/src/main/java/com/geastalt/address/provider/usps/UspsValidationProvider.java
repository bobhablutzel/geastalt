/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.usps;

import com.geastalt.address.provider.ValidationProvider;
import com.geastalt.address.provider.ValidationRequest;
import com.geastalt.address.provider.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UspsValidationProvider implements ValidationProvider {

    private static final String ADDRESS_PATH = "/addresses/v3/address";

    private final UspsConfig uspsConfig;
    private final UspsOAuthService oAuthService;
    private final RestClient.Builder restClientBuilder;

    @Override
    public String getProviderId() {
        return "usps";
    }

    @Override
    public String getDisplayName() {
        return "USPS Address Validation API";
    }

    @Override
    public List<String> getSupportedCountries() {
        return List.of("US");
    }

    @Override
    public boolean isEnabled() {
        return uspsConfig.getClientId() != null && !uspsConfig.getClientId().isEmpty();
    }

    @Override
    public ValidationResult validate(ValidationRequest request) {
        UspsAddressRequest uspsRequest = mapToUspsRequest(request);

        try {
            UspsAddressResponse uspsResponse = callUspsApi(uspsRequest);
            return mapFromUspsResponse(request, uspsResponse);
        } catch (Exception e) {
            log.error("USPS validation failed: {}", e.getMessage(), e);
            return ValidationResult.builder()
                    .status(ValidationResult.Status.PROVIDER_ERROR)
                    .message("USPS API error: " + e.getMessage())
                    .metadata(Map.of())
                    .build();
        }
    }

    private UspsAddressRequest mapToUspsRequest(ValidationRequest request) {
        List<String> lines = request.getAddressLines() != null ? request.getAddressLines() : List.of();
        return UspsAddressRequest.builder()
                .streetAddress(lines.size() > 0 ? lines.get(0) : null)
                .secondaryAddress(lines.size() > 1 ? lines.get(1) : null)
                .city(request.getLocality())
                .state(request.getAdministrativeArea())
                .zipCode(request.getPostalCode())
                .build();
    }

    private ValidationResult mapFromUspsResponse(ValidationRequest originalRequest,
                                                  UspsAddressResponse uspsResponse) {
        if (uspsResponse == null || uspsResponse.getAddress() == null) {
            return ValidationResult.builder()
                    .status(ValidationResult.Status.INVALID)
                    .message("USPS returned no address data")
                    .metadata(Map.of())
                    .build();
        }

        UspsAddressResponse.Address addr = uspsResponse.getAddress();

        List<String> standardizedLines = new ArrayList<>();
        if (addr.getStreetAddress() != null && !addr.getStreetAddress().isEmpty()) {
            standardizedLines.add(addr.getStreetAddress());
        }
        if (addr.getSecondaryAddress() != null && !addr.getSecondaryAddress().isEmpty()) {
            standardizedLines.add(addr.getSecondaryAddress());
        }

        String postalCode = joinPostalCode(addr.getZipCode(), addr.getZipPlus4());

        boolean hasCorrections = !addressLinesMatch(originalRequest.getAddressLines(), standardizedLines)
                || !equalsIgnoreCase(originalRequest.getLocality(), addr.getCity())
                || !equalsIgnoreCase(originalRequest.getAdministrativeArea(), addr.getState())
                || !equalsIgnoreCase(originalRequest.getPostalCode(), postalCode);

        Map<String, String> metadata = new HashMap<>();
        if (uspsResponse.getDeliveryPoint() != null) {
            metadata.put("deliveryPoint", uspsResponse.getDeliveryPoint());
        }
        if (uspsResponse.getCarrierRoute() != null) {
            metadata.put("carrierRoute", uspsResponse.getCarrierRoute());
        }
        if (uspsResponse.getDPVConfirmation() != null) {
            metadata.put("dpvConfirmation", uspsResponse.getDPVConfirmation());
        }
        if (uspsResponse.getDPVCMRA() != null) {
            metadata.put("dpvCmra", uspsResponse.getDPVCMRA());
        }
        if (uspsResponse.getBusiness() != null) {
            metadata.put("business", uspsResponse.getBusiness());
        }
        if (uspsResponse.getVacant() != null) {
            metadata.put("vacant", uspsResponse.getVacant());
        }

        return ValidationResult.builder()
                .status(hasCorrections ? ValidationResult.Status.VALIDATED_WITH_CORRECTIONS
                        : ValidationResult.Status.VALIDATED)
                .countryCode("US")
                .addressLines(standardizedLines)
                .locality(addr.getCity())
                .administrativeArea(addr.getState())
                .postalCode(postalCode)
                .metadata(metadata)
                .build();
    }

    private UspsAddressResponse callUspsApi(UspsAddressRequest request) {
        String token = oAuthService.getAccessToken();

        RestClient restClient = restClientBuilder
                .baseUrl(uspsConfig.getBaseUrl())
                .build();

        String uri = buildAddressUri(request);

        try {
            return restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 401) {
                            log.warn("USPS OAuth token expired, invalidating cache");
                            oAuthService.invalidateToken();
                        }
                        throw new RuntimeException("USPS API error: " + res.getStatusCode());
                    })
                    .body(UspsAddressResponse.class);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("Retrying with new token");
                token = oAuthService.getAccessToken();
                return restClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(UspsAddressResponse.class);
            }
            throw e;
        }
    }

    private String buildAddressUri(UspsAddressRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(ADDRESS_PATH);

        if (request.getStreetAddress() != null) {
            builder.queryParam("streetAddress", request.getStreetAddress());
        }
        if (request.getSecondaryAddress() != null) {
            builder.queryParam("secondaryAddress", request.getSecondaryAddress());
        }
        if (request.getCity() != null) {
            builder.queryParam("city", request.getCity());
        }
        if (request.getState() != null) {
            builder.queryParam("state", request.getState());
        }
        if (request.getZipCode() != null) {
            builder.queryParam("ZIPCode", request.getZipCode());
        }
        if (request.getZipPlus4() != null) {
            builder.queryParam("ZIPPlus4", request.getZipPlus4());
        }

        return builder.build().toUriString();
    }

    private String joinPostalCode(String zipCode, String zipPlus4) {
        if (zipCode == null) return null;
        if (zipPlus4 != null && !zipPlus4.isEmpty()) {
            return zipCode + "-" + zipPlus4;
        }
        return zipCode;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private boolean addressLinesMatch(List<String> original, List<String> standardized) {
        if (original == null && standardized == null) return true;
        if (original == null || standardized == null) return false;
        if (original.size() != standardized.size()) return false;
        for (int i = 0; i < original.size(); i++) {
            if (!equalsIgnoreCase(original.get(i), standardized.get(i))) return false;
        }
        return true;
    }
}
