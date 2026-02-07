package com.geastalt.contact.dto.usps;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AddressResponse {

    private Address address;
    private String deliveryPoint;
    private String carrierRoute;
    private String DPVConfirmation;
    private String DPVCMRA;
    private String business;
    private String centralDeliveryPoint;
    private String vacant;

    @Data
    public static class Address {
        @JsonProperty("streetAddress")
        private String streetAddress;

        @JsonProperty("streetAddressAbbreviation")
        private String streetAddressAbbreviation;

        @JsonProperty("secondaryAddress")
        private String secondaryAddress;

        @JsonProperty("city")
        private String city;

        @JsonProperty("cityAbbreviation")
        private String cityAbbreviation;

        @JsonProperty("state")
        private String state;

        @JsonProperty("ZIPCode")
        private String zipCode;

        @JsonProperty("ZIPPlus4")
        private String zipPlus4;
    }
}
