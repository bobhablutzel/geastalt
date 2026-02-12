/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.usps;

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
