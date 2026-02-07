package com.geastalt.contact.controller;

import com.geastalt.contact.dto.usps.AddressRequest;
import com.geastalt.contact.dto.usps.StandardizedAddressResult;
import com.geastalt.contact.service.AddressStandardizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressStandardizationService addressService;

    @GetMapping("/standardize")
    public ResponseEntity<StandardizedAddressResult> standardizeAddress(
            @RequestParam String streetAddress,
            @RequestParam(required = false) String secondaryAddress,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String zipPlus4) {

        AddressRequest request = AddressRequest.builder()
                .streetAddress(streetAddress)
                .secondaryAddress(secondaryAddress)
                .city(city)
                .state(state)
                .zipCode(zipCode)
                .zipPlus4(zipPlus4)
                .build();

        StandardizedAddressResult result = addressService.standardizeAndSaveAddress(request);
        return ResponseEntity.ok(result);
    }
}
