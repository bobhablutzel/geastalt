/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ValidationResult {

    public enum Status {
        VALIDATED,
        VALIDATED_WITH_CORRECTIONS,
        INVALID,
        PROVIDER_ERROR
    }

    private Status status;
    private String countryCode;
    private List<String> addressLines;
    private String locality;
    private String administrativeArea;
    private String postalCode;
    private String subLocality;
    private String sortingCode;
    private String message;
    private Map<String, String> metadata;
}
