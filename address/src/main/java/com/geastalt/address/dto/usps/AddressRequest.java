package com.geastalt.address.dto.usps;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressRequest {

    private String streetAddress;
    private String secondaryAddress;
    private String city;
    private String state;
    private String zipCode;
    private String zipPlus4;
}
