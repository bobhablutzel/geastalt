package com.geastalt.contact.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "usps")
public class UspsConfig {

    private String baseUrl = "https://apis.usps.com";
    private String clientId;
    private String clientSecret;
    private OAuth oauth = new OAuth();

    @Data
    public static class OAuth {
        private String tokenPath = "/oauth2/v3/token";
        private String grantType = "client_credentials";
    }
}
