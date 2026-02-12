/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format;

public interface FormatVerifier {

    String getCountryCode();

    FormatVerificationResult verify(String countryCode,
                                    java.util.List<String> addressLines,
                                    String locality,
                                    String administrativeArea,
                                    String postalCode,
                                    String subLocality,
                                    String sortingCode);
}
