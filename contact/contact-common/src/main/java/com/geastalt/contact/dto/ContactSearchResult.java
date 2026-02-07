package com.geastalt.contact.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

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
        private String streetAddress;
        private String secondaryAddress;
        private String city;
        private String state;
        private String zipCode;
        private String zipPlus4;
    }
}
