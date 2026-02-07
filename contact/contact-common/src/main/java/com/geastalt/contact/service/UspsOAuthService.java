package com.geastalt.contact.service;

import com.geastalt.contact.config.UspsConfig;
import com.geastalt.contact.dto.usps.OAuthTokenRequest;
import com.geastalt.contact.dto.usps.OAuthTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class UspsOAuthService {

    private final UspsConfig uspsConfig;
    private final RestClient.Builder restClientBuilder;

    private String cachedToken;
    private Instant tokenExpiry;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    public String getAccessToken() {
        tokenLock.lock();
        try {
            if (isTokenValid()) {
                log.debug("Using cached USPS OAuth token");
                return cachedToken;
            }

            log.info("Requesting new USPS OAuth token");
            OAuthTokenResponse response = requestNewToken();

            cachedToken = response.getAccessToken();
            int expiresIn = Integer.parseInt(response.getExpiresIn());
            tokenExpiry = Instant.now().plusSeconds(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);

            log.info("USPS OAuth token obtained, expires at: {}", tokenExpiry);
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenValid() {
        return cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry);
    }

    private OAuthTokenResponse requestNewToken() {
        RestClient restClient = restClientBuilder
                .baseUrl(uspsConfig.getBaseUrl())
                .build();

        OAuthTokenRequest request = OAuthTokenRequest.builder()
                .clientId(uspsConfig.getClientId())
                .clientSecret(uspsConfig.getClientSecret())
                .grantType(uspsConfig.getOauth().getGrantType())
                .build();

        OAuthTokenResponse response = restClient.post()
                .uri(uspsConfig.getOauth().getTokenPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OAuthTokenResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("Failed to obtain USPS OAuth token");
        }

        return response;
    }

    public void invalidateToken() {
        tokenLock.lock();
        try {
            cachedToken = null;
            tokenExpiry = null;
            log.info("USPS OAuth token invalidated");
        } finally {
            tokenLock.unlock();
        }
    }
}
