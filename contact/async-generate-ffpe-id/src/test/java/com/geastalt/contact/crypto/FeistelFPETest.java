/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeistelFPETest {

    private FeistelFPE fpe;
    private static final byte[] TEST_KEY = "test-encryption-key-32-bytes!!!!".getBytes();

    @BeforeEach
    void setUp() {
        fpe = new FeistelFPE(TEST_KEY);
    }

    @Test
    void encrypt_shouldProduceValueInDomain() {
        long encrypted = fpe.encrypt(12345L);

        assertTrue(encrypted >= 0, "Encrypted value should be non-negative");
        assertTrue(encrypted <= 999_999_999_999_999L, "Encrypted value should be within 15-digit domain");
    }

    @Test
    void encryptDecrypt_shouldRoundTrip() {
        long[] testValues = {0L, 1L, 100L, 12345L, 999999L, 1_000_000_000L, 999_999_999_999_999L};

        for (long original : testValues) {
            long encrypted = fpe.encrypt(original);
            long decrypted = fpe.decrypt(encrypted);

            assertEquals(original, decrypted,
                "Round-trip failed for value: " + original + " -> " + encrypted + " -> " + decrypted);
        }
    }

    @Test
    void encrypt_shouldProduceNonSequentialValues() {
        long encrypted1 = fpe.encrypt(1L);
        long encrypted2 = fpe.encrypt(2L);
        long encrypted3 = fpe.encrypt(3L);

        // Sequential inputs should not produce sequential outputs
        assertNotEquals(encrypted2 - encrypted1, encrypted3 - encrypted2,
            "Sequential IDs should not produce sequential encrypted values");

        // Encrypted values should be different
        assertNotEquals(encrypted1, encrypted2);
        assertNotEquals(encrypted2, encrypted3);
    }

    @Test
    void encrypt_shouldBeUnique() {
        Set<Long> encryptedValues = new HashSet<>();
        int count = 10000;

        for (long i = 0; i < count; i++) {
            long encrypted = fpe.encrypt(i);
            assertTrue(encryptedValues.add(encrypted),
                "Collision detected at i=" + i + ", encrypted=" + encrypted);
        }

        assertEquals(count, encryptedValues.size());
    }

    @Test
    void encrypt_withDifferentKeys_shouldProduceDifferentResults() {
        byte[] key1 = "key-one-for-testing-purposes!!!!".getBytes();
        byte[] key2 = "key-two-for-testing-purposes!!!!".getBytes();

        FeistelFPE fpe1 = new FeistelFPE(key1);
        FeistelFPE fpe2 = new FeistelFPE(key2);

        long value = 12345L;
        long encrypted1 = fpe1.encrypt(value);
        long encrypted2 = fpe2.encrypt(value);

        assertNotEquals(encrypted1, encrypted2,
            "Different keys should produce different encrypted values");
    }

    @Test
    void constructor_shouldRejectNullKey() {
        assertThrows(IllegalArgumentException.class, () -> new FeistelFPE(null));
    }

    @Test
    void constructor_shouldRejectShortKey() {
        byte[] shortKey = "short".getBytes();
        assertThrows(IllegalArgumentException.class, () -> new FeistelFPE(shortKey));
    }

    @Test
    void encrypt_shouldRejectNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> fpe.encrypt(-1L));
    }

    @Test
    void encrypt_shouldRejectValuesOutOfDomain() {
        assertThrows(IllegalArgumentException.class, () -> fpe.encrypt(1_000_000_000_000_000L));
    }

    @Test
    void decrypt_shouldRejectNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> fpe.decrypt(-1L));
    }

    @Test
    void decrypt_shouldRejectValuesOutOfDomain() {
        assertThrows(IllegalArgumentException.class, () -> fpe.decrypt(1_000_000_000_000_000L));
    }
}
