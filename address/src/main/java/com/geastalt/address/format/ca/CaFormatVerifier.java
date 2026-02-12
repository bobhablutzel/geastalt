/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format.ca;

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
public class CaFormatVerifier implements FormatVerifier {

    private static final Pattern POSTAL_CODE = Pattern.compile("^[A-Z]\\d[A-Z] \\d[A-Z]\\d$");
    private static final Pattern POSTAL_CODE_NO_SPACE = Pattern.compile("^[A-Z]\\d[A-Z]\\d[A-Z]\\d$");

    private static final Set<String> VALID_PROVINCE_CODES = Set.of(
            "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT"
    );

    @Override
    public String getCountryCode() {
        return "CA";
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
                    .message("Province/territory is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        } else {
            String upper = administrativeArea.toUpperCase().trim();
            if (!VALID_PROVINCE_CODES.contains(upper)) {
                issues.add(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.ERROR)
                        .message("Invalid Canadian province/territory code")
                        .originalValue(administrativeArea)
                        .correctedValue("")
                        .build());
                hasErrors = true;
            } else if (!upper.equals(administrativeArea)) {
                correctedAdminArea = upper;
                issues.add(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.INFO)
                        .message("Province code normalized to uppercase")
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
                    .message("Postal code is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        } else {
            String upper = postalCode.toUpperCase().trim();
            if (POSTAL_CODE_NO_SPACE.matcher(upper).matches()) {
                correctedPostalCode = upper.substring(0, 3) + " " + upper.substring(3);
                issues.add(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.INFO)
                        .message("Postal code formatted with space")
                        .originalValue(postalCode)
                        .correctedValue(correctedPostalCode)
                        .build());
                hasCorrected = true;
            } else if (POSTAL_CODE.matcher(upper).matches()) {
                if (!upper.equals(postalCode)) {
                    correctedPostalCode = upper;
                    issues.add(FormatIssueItem.builder()
                            .field("postal_code")
                            .severity(Severity.INFO)
                            .message("Postal code normalized to uppercase")
                            .originalValue(postalCode)
                            .correctedValue(upper)
                            .build());
                    hasCorrected = true;
                }
            } else {
                issues.add(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.ERROR)
                        .message("Invalid Canadian postal code format (expected A9A 9A9)")
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
                .countryCode("CA")
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
