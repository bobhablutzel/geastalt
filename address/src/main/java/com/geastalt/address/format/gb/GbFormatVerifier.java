/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format.gb;

import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.geastalt.address.format.FormatVerificationResult.Severity;
import com.geastalt.address.format.FormatVerifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GbFormatVerifier implements FormatVerifier {

    // UK postcode: outward code (2-4 chars) + space + inward code (3 chars)
    // Patterns: A9 9AA, A99 9AA, A9A 9AA, AA9 9AA, AA99 9AA, AA9A 9AA
    private static final Pattern UK_POSTCODE = Pattern.compile(
            "^[A-Z]{1,2}\\d[A-Z\\d]? ?\\d[A-Z]{2}$"
    );

    // To normalize spacing: capture outward + inward parts
    private static final Pattern UK_POSTCODE_PARTS = Pattern.compile(
            "^([A-Z]{1,2}\\d[A-Z\\d]?) ?(\\d[A-Z]{2})$"
    );

    @Override
    public String getCountryCode() {
        return "GB";
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

        // Required: at least one address line
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

        // Required: locality (post town)
        if (locality == null || locality.isBlank()) {
            issues.add(FormatIssueItem.builder()
                    .field("locality")
                    .severity(Severity.ERROR)
                    .message("Post town is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        }

        // UK doesn't require administrative_area for postal addresses

        // Postal code validation
        if (postalCode == null || postalCode.isBlank()) {
            issues.add(FormatIssueItem.builder()
                    .field("postal_code")
                    .severity(Severity.ERROR)
                    .message("Postcode is required")
                    .originalValue("")
                    .correctedValue("")
                    .build());
            hasErrors = true;
        } else {
            String upper = postalCode.toUpperCase().trim();
            if (!UK_POSTCODE.matcher(upper).matches()) {
                issues.add(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.ERROR)
                        .message("Invalid UK postcode format")
                        .originalValue(postalCode)
                        .correctedValue("")
                        .build());
                hasErrors = true;
            } else {
                // Normalize spacing
                Matcher m = UK_POSTCODE_PARTS.matcher(upper);
                if (m.matches()) {
                    String normalized = m.group(1) + " " + m.group(2);
                    if (!normalized.equals(postalCode)) {
                        correctedPostalCode = normalized;
                        issues.add(FormatIssueItem.builder()
                                .field("postal_code")
                                .severity(Severity.INFO)
                                .message("Postcode normalized (spacing/case)")
                                .originalValue(postalCode)
                                .correctedValue(normalized)
                                .build());
                        hasCorrected = true;
                    }
                }
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
                .countryCode("GB")
                .addressLines(addressLines)
                .locality(locality)
                .administrativeArea(administrativeArea)
                .postalCode(correctedPostalCode)
                .subLocality(subLocality)
                .sortingCode(sortingCode)
                .issues(issues)
                .build();
    }
}
