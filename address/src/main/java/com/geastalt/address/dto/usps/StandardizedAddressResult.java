package com.geastalt.address.dto.usps;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StandardizedAddressResult {
    private Long id;
    private AddressResponse uspsResponse;
}
