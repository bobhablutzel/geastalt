/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.service;

import com.geastalt.address.provider.ProviderRegistry;
import com.geastalt.address.provider.ValidationProvider;
import com.geastalt.address.provider.ValidationRequest;
import com.geastalt.address.provider.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressValidationService {

    private final ProviderRegistry providerRegistry;

    public record Result(ValidationResult validationResult, String providerId) {}

    public Optional<ValidationProvider> findProvider(String countryCode, String providerOverride) {
        return providerRegistry.getProvider(countryCode, providerOverride);
    }

    public Result validate(ValidationRequest request, ValidationProvider provider) {
        log.info("Validating address with provider '{}' for country '{}'",
                provider.getProviderId(), request.getCountryCode());
        ValidationResult result = provider.validate(request);
        return new Result(result, provider.getProviderId());
    }
}
