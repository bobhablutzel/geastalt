/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format.us;

import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.geastalt.address.format.FormatVerificationResult.Severity;
import com.geastalt.address.format.FormatVerifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UsFormatVerifier implements FormatVerifier {

    private static final Pattern ZIP5 = Pattern.compile("^\\d{5}$");
    private static final Pattern ZIP5_PLUS4 = Pattern.compile("^\\d{5}-\\d{4}$");
    private static final Pattern ZIP9_NO_DASH = Pattern.compile("^\\d{9}$");

    private static final Set<String> VALID_STATE_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "PR", "GU", "VI", "AS", "MP"
    );

    @Override
    public String getCountryCode() {
        return "US";
    }

    @Override
    public FormatVerificationResult verify(String countryCode,
                                           List<String> addressLines,
                                           String locality,
                                           String administrativeArea,
                                           String postalCode,
                                           String subLocality,
                                           String sortingCode) {
        List<FormatIssueItem> issues = new ArrayList<>();
        boolean hasErrors = false;
        boolean hasCorrected = false;

        String correctedPostalCode = postalCode;
        String correctedAdminArea = administrativeArea;

        // Required fields
        if (addressLines == null || addressLines.isEmpty()
                || addressLines.stream().allMatch(l -> l == null || l.isBlank())) {
            issues.add(FormatIssueItem.builder()
                    .field("address_lines")
                    .severity(Severity.ERROR)
                    .message("At least one address line is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        }

        if (locality == null || locality.isBlank()) {
            issues.add(FormatIssueItem.builder()
                    .field("locality")
                    .severity(Severity.ERROR)
                    .message("City/locality is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        }

        if (administrativeArea == null || administrativeArea.isBlank()) {
            issues.add(FormatIssueItem.builder()
                    .field("administrative_area")
                    .severity(Severity.ERROR)
                    .message("State is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        } else {
            String upper = administrativeArea.toUpperCase().trim();
            if (!VALID_STATE_CODES.contains(upper)) {
                issues.add(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.ERROR)
                        .message("Invalid US state/territory code")
                        .originalValue(administrativeArea)
                        .correctedValue("")
                        .build());
                hasErrors = true;
            } else if (!upper.equals(administrativeArea)) {
                correctedAdminArea = upper;
                issues.add(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.INFO)
                        .message("State code normalized to uppercase")
                        .originalValue(administrativeArea)
                        .correctedValue(upper)
                        .build());
                hasCorrected = true;
            }
        }

        if (postalCode == null || postalCode.isBlank()) {
            issues.add(FormatIssueItem.builder()
                    .field("postal_code")
                    .severity(Severity.ERROR)
                    .message("ZIP code is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        } else {
            String trimmed = postalCode.trim();
            if (ZIP9_NO_DASH.matcher(trimmed).matches()) {
                correctedPostalCode = trimmed.substring(0, 5) + "-" + trimmed.substring(5);
                issues.add(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.INFO)
                        .message("ZIP+4 formatted with dash")
                        .originalValue(postalCode)
                        .correctedValue(correctedPostalCode)
                        .build());
                hasCorrected = true;
            } else if (!ZIP5.matcher(trimmed).matches() && !ZIP5_PLUS4.matcher(trimmed).matches()) {
                issues.add(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.ERROR)
                        .message("Invalid ZIP code format (expected 12345 or 12345-6789)")
                        .originalValue(postalCode)
                        .correctedValue("")
                        .build());
                hasErrors = true;
            }
        }

        FormatVerificationResult.Status status;
        if (hasErrors) {
            status = FormatVerificationResult.Status.INVALID;
        } else if (hasCorrected) {
            status = FormatVerificationResult.Status.CORRECTED;
        } else {
            status = FormatVerificationResult.Status.VALID;
        }

        return FormatVerificationResult.builder()
                .status(status)
                .countryCode("US")
                .addressLines(addressLines)
                .locality(locality)
                .administrativeArea(correctedAdminArea)
                .postalCode(correctedPostalCode)
                .subLocality(subLocality)
                .sortingCode(sortingCode)
                .issues(issues)
                .build();
    }
}
