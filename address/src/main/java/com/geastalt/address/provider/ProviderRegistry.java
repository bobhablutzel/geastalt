/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProviderRegistry {

    private final Map<String, ValidationProvider> providersById;
    private final Map<String, String> countryDefaults;

    public ProviderRegistry(List<ValidationProvider> providers,
                            @Value("#{${address.providers.defaults:{}}}") Map<String, String> countryDefaults) {
        this.providersById = providers.stream()
                .collect(Collectors.toMap(ValidationProvider::getProviderId, Function.identity()));
        this.countryDefaults = countryDefaults;
        log.info("Registered {} validation providers: {}", providers.size(),
                providers.stream().map(ValidationProvider::getProviderId).toList());
    }

    public Optional<ValidationProvider> getProvider(String countryCode, String providerOverride) {
        if (providerOverride != null && !providerOverride.isEmpty()) {
            ValidationProvider provider = providersById.get(providerOverride);
            if (provider != null && provider.isEnabled()
                    && provider.getSupportedCountries().contains(countryCode)) {
                return Optional.of(provider);
            }
            return Optional.empty();
        }

        String defaultProviderId = countryDefaults.get(countryCode);
        if (defaultProviderId != null) {
            ValidationProvider provider = providersById.get(defaultProviderId);
            if (provider != null && provider.isEnabled()) {
                return Optional.of(provider);
            }
        }

        return Optional.empty();
    }

    public List<ValidationProvider> getAllProviders() {
        return List.copyOf(providersById.values());
    }
}
