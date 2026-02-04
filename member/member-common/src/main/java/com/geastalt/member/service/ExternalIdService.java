package com.geastalt.member.service;

import com.geastalt.member.crypto.FeistelFPE;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Service for generating and decoding external member identifiers.
 * External IDs have the format "NH" + 15 digits (e.g., "NH847293615028374").
 */
@Service
public class ExternalIdService {

    private static final String PREFIX = "NH";
    private static final Pattern EXTERNAL_ID_PATTERN = Pattern.compile("^NH\\d{15}$");

    @Value("${member.external-id.encryption-key}")
    private String encryptionKeyHex;

    private FeistelFPE feistelFPE;

    // Static instance for JPA listener access
    private static ExternalIdService instance;

    @PostConstruct
    public void init() {
        byte[] key = parseKey(encryptionKeyHex);
        this.feistelFPE = new FeistelFPE(key);
        instance = this;
    }

    /**
     * Get the singleton instance for use in JPA entity listeners.
     */
    public static ExternalIdService getInstance() {
        return instance;
    }

    /**
     * Generates an external ID from an internal database ID.
     *
     * @param internalId the internal database ID
     * @return external ID in format "NH" + 15 digits
     */
    public String generateExternalId(Long internalId) {
        if (internalId == null || internalId < 0) {
            throw new IllegalArgumentException("Internal ID must be a non-negative number");
        }

        long encrypted = feistelFPE.encrypt(internalId);
        return PREFIX + String.format("%015d", encrypted);
    }

    /**
     * Decodes an external ID back to the internal database ID.
     *
     * @param externalId the external ID (e.g., "NH847293615028374")
     * @return the original internal database ID
     * @throws IllegalArgumentException if the external ID format is invalid
     */
    public Long decodeExternalId(String externalId) {
        if (!isValidFormat(externalId)) {
            throw new IllegalArgumentException("Invalid external ID format: " + externalId);
        }

        String numericPart = externalId.substring(PREFIX.length());
        long encrypted = Long.parseLong(numericPart);
        return feistelFPE.decrypt(encrypted);
    }

    /**
     * Validates whether a string matches the external ID format.
     *
     * @param externalId the string to validate
     * @return true if the format is valid (NH + 15 digits)
     */
    public boolean isValidFormat(String externalId) {
        if (externalId == null) {
            return false;
        }
        return EXTERNAL_ID_PATTERN.matcher(externalId).matches();
    }

    private byte[] parseKey(String keyHex) {
        if (keyHex == null || keyHex.isBlank()) {
            throw new IllegalStateException("Encryption key is not configured");
        }

        // Support both hex format and plain text (for dev convenience)
        if (keyHex.matches("^[0-9a-fA-F]+$") && keyHex.length() >= 32) {
            return HexFormat.of().parseHex(keyHex);
        } else {
            // Use the string bytes directly (dev mode)
            byte[] bytes = keyHex.getBytes();
            if (bytes.length < 16) {
                throw new IllegalStateException("Encryption key must be at least 16 bytes");
            }
            return bytes;
        }
    }
}
