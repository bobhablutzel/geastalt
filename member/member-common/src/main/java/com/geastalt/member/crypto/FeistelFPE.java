package com.geastalt.member.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Format-Preserving Encryption using a 4-round Feistel network.
 * Encrypts/decrypts values in the 15-digit numeric domain (0 to 999,999,999,999,999).
 *
 * The Feistel structure guarantees bijective mapping (one-to-one correspondence),
 * meaning each input maps to exactly one unique output and vice versa.
 */
public class FeistelFPE {

    private static final int NUM_ROUNDS = 4;
    private static final long MAX_VALUE = 999_999_999_999_999L; // 15 digits max
    // Split into two equal-sized halves for balanced Feistel
    // We use sqrt of domain size for balanced splitting
    // 10^15 = 10^7.5 * 10^7.5, so we use 10^8 as the modulus (slightly larger to cover domain)
    private static final long HALF_MODULUS = 100_000_000L;  // 10^8
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public FeistelFPE(byte[] key) {
        if (key == null || key.length < 16) {
            throw new IllegalArgumentException("Key must be at least 16 bytes");
        }
        this.key = key.clone();
    }

    /**
     * Encrypts an internal ID to a pseudo-random 15-digit number.
     *
     * @param plaintext the internal database ID (must be >= 0 and <= MAX_VALUE)
     * @return encrypted value in the 15-digit domain
     */
    public long encrypt(long plaintext) {
        validateInput(plaintext);
        return feistelWithCycleWalking(plaintext, true);
    }

    /**
     * Decrypts an encrypted value back to the original internal ID.
     *
     * @param ciphertext the encrypted external ID value
     * @return the original internal database ID
     */
    public long decrypt(long ciphertext) {
        validateInput(ciphertext);
        return feistelWithCycleWalking(ciphertext, false);
    }

    private void validateInput(long value) {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                "Value must be between 0 and " + MAX_VALUE + ", got: " + value);
        }
    }

    private long feistelWithCycleWalking(long value, boolean encrypt) {
        long result = value;
        do {
            result = feistelCore(result, encrypt);
        } while (result > MAX_VALUE);
        return result;
    }

    private long feistelCore(long value, boolean encrypt) {
        // Split: value = left * HALF_MODULUS + right
        // Both halves are in [0, HALF_MODULUS)
        long left = value / HALF_MODULUS;
        long right = value % HALF_MODULUS;

        if (encrypt) {
            // Forward rounds
            for (int round = 0; round < NUM_ROUNDS; round++) {
                long f = roundFunction(right, round);
                long newLeft = right;
                long newRight = Math.floorMod(left + f, HALF_MODULUS);
                left = newLeft;
                right = newRight;
            }
        } else {
            // Reverse rounds
            for (int round = NUM_ROUNDS - 1; round >= 0; round--) {
                long f = roundFunction(left, round);
                long newRight = left;
                long newLeft = Math.floorMod(right - f, HALF_MODULUS);
                left = newLeft;
                right = newRight;
            }
        }

        // Recombine
        return left * HALF_MODULUS + right;
    }

    /**
     * Feistel round function using HMAC-SHA256.
     * Takes the input half and round number, produces a pseudo-random value.
     */
    private long roundFunction(long input, int round) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(keySpec);

            // Create input: round number || input value
            ByteBuffer buffer = ByteBuffer.allocate(12);
            buffer.putInt(round);
            buffer.putLong(input);

            byte[] hash = mac.doFinal(buffer.array());

            // Extract 8 bytes from hash and reduce to HALF_MODULUS domain
            long hashValue = ByteBuffer.wrap(hash, 0, 8).getLong();
            return Math.floorMod(hashValue, HALF_MODULUS);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute round function", e);
        }
    }
}
