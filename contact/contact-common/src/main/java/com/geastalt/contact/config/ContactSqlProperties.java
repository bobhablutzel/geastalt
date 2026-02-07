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
    private Controller controller = new Controller();

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
        private String insertContactAddress;
        private String insertPendingAction;
        private String insertContactLookup;
    }

    @Data
    public static class Controller {
        private String findContactsWithoutAlternateId;
        private String insertAlternateId;
    }
}
