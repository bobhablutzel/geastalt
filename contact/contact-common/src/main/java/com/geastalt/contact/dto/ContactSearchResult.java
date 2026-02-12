/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ContactSearchResult {
    private Long id;
    @Singular
    private Map<String, String> alternateIds;
    private String firstName;
    private String lastName;
    private String preferredEmail;
    private PreferredAddress preferredAddress;
    private Integer partitionNumber;

    @Data
    @Builder
    public static class PreferredAddress {
        private List<String> addressLines;
        private String locality;
        private String administrativeArea;
        private String postalCode;
        private String countryCode;
    }
}
