/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.usps;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UspsAddressRequest {

    private String streetAddress;
    private String secondaryAddress;
    private String city;
    private String state;
    private String zipCode;
    private String zipPlus4;
}
