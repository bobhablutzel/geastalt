/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "contact.sql")
public class ContactSqlProperties {

    private Search search = new Search();
    private Bulk bulk = new Bulk();

    @Data
    public static class Search {
        private String byNameExact;
        private String byNameExactWithFirstName;
        private String byNamePrefix;
        private String byNamePrefixWithFirstName;
        private String countByNameExact;
        private String countByNameExactWithFirstName;
        private String countByNamePrefix;
        private String countByNamePrefixWithFirstName;
        private String findByAlternateIdLookup;
        private String findById;
        private String fetchAlternateIds;
        private String batchFetchAlternateIds;
        private String searchByPhone;
    }

    @Data
    public static class Bulk {
        private String insertContact;
        private String insertPhone;
        private String insertEmail;
        private String insertAddress;
        private String insertAddressLine;
        private String insertContactAddress;
        private String insertPendingAction;
        private String insertContactLookup;
    }
}
