package com.geastalt.member.dto.usps;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OAuthTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("issued_at")
    private String issuedAt;

    @JsonProperty("expires_in")
    private String expiresIn;

    private String status;

    private String scope;
}
