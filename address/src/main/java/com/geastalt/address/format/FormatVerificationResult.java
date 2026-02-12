/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormatVerificationResult {

    public enum Status {
        VALID,
        CORRECTED,
        INVALID
    }

    private Status status;
    private String countryCode;
    private List<String> addressLines;
    private String locality;
    private String administrativeArea;
    private String postalCode;
    private String subLocality;
    private String sortingCode;
    private List<FormatIssueItem> issues;

    @Data
    @Builder
    public static class FormatIssueItem {
        private String field;
        private Severity severity;
        private String message;
        private String originalValue;
        private String correctedValue;
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}
