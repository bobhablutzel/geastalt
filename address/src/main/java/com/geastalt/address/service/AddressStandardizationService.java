package com.geastalt.address.service;

import com.geastalt.address.config.UspsConfig;
import com.geastalt.address.dto.usps.AddressRequest;
import com.geastalt.address.dto.usps.AddressResponse;
import com.geastalt.address.dto.usps.StandardizedAddressResult;
import com.geastalt.address.entity.StandardizedAddress;
import com.geastalt.address.repository.StandardizedAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressStandardizationService {

    private static final String ADDRESS_PATH = "/addresses/v3/address";

    private final UspsConfig uspsConfig;
    private final UspsOAuthService oAuthService;
    private final RestClient.Builder restClientBuilder;
    private final StandardizedAddressRepository addressRepository;

    @Transactional
    public StandardizedAddressResult standardizeAndSaveAddress(AddressRequest request) {
        AddressResponse uspsResponse = standardizeAddress(request);

        if (uspsResponse == null || uspsResponse.getAddress() == null) {
            throw new RuntimeException("Failed to standardize address");
        }

        AddressResponse.Address addr = uspsResponse.getAddress();

        // Check for existing address
        Optional<StandardizedAddress> existing = addressRepository
                .findByStreetAddressAndSecondaryAddressAndCityAndStateAndZipCodeAndZipPlus4(
                        addr.getStreetAddress(),
                        addr.getSecondaryAddress(),
                        addr.getCity(),
                        addr.getState(),
                        addr.getZipCode(),
                        addr.getZipPlus4()
                );

        if (existing.isPresent()) {
            log.info("Address already exists with id: {}", existing.get().getId());
            return StandardizedAddressResult.builder()
                    .id(existing.get().getId())
                    .uspsResponse(uspsResponse)
                    .build();
        }

        // Save new address
        StandardizedAddress newAddress = StandardizedAddress.builder()
                .streetAddress(addr.getStreetAddress())
                .secondaryAddress(addr.getSecondaryAddress())
                .city(addr.getCity())
                .state(addr.getState())
                .zipCode(addr.getZipCode())
                .zipPlus4(addr.getZipPlus4())
                .build();

        StandardizedAddress saved = addressRepository.save(newAddress);
        log.info("Saved new standardized address with id: {}", saved.getId());

        return StandardizedAddressResult.builder()
                .id(saved.getId())
                .uspsResponse(uspsResponse)
                .build();
    }

    public AddressResponse standardizeAddress(AddressRequest request) {
        log.info("Standardizing address: {}, {}, {} {}",
                request.getStreetAddress(),
                request.getCity(),
                request.getState(),
                request.getZipCode());

        String token = oAuthService.getAccessToken();

        RestClient restClient = restClientBuilder
                .baseUrl(uspsConfig.getBaseUrl())
                .build();

        String uri = buildAddressUri(request);

        try {
            AddressResponse response = restClient.get()
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
                    .body(AddressResponse.class);

            log.info("Address standardized successfully");
            return response;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("Retrying with new token");
                token = oAuthService.getAccessToken();
                return restClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(AddressResponse.class);
            }
            throw e;
        }
    }

    private String buildAddressUri(AddressRequest request) {
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
}
