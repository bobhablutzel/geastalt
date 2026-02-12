/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.service;

import com.geastalt.address.format.CountryFormatRegistry;
import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressFormatService {

    private final CountryFormatRegistry formatRegistry;

    public Optional<FormatVerifier> findVerifier(String countryCode) {
        return formatRegistry.getVerifier(countryCode);
    }

    public FormatVerificationResult verify(FormatVerifier verifier,
                                           String countryCode,
                                           List<String> addressLines,
                                           String locality,
                                           String administrativeArea,
                                           String postalCode,
                                           String subLocality,
                                           String sortingCode) {
        log.info("Verifying address format for country '{}'", countryCode);
        return verifier.verify(countryCode, addressLines, locality,
                administrativeArea, postalCode, subLocality, sortingCode);
    }

    public boolean isSupported(String countryCode) {
        return formatRegistry.isSupported(countryCode);
    }
}
