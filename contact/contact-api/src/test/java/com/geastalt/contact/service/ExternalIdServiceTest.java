package com.geastalt.contact.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ExternalIdServiceTest {

    private ExternalIdService service;

    @BeforeEach
    void setUp() {
        service = new ExternalIdService();
        // Inject test encryption key
        ReflectionTestUtils.setField(service, "encryptionKeyHex", "test-encryption-key-32-bytes!!!!");
        service.init();
    }

    @Test
    void generateExternalId_shouldHaveCorrectFormat() {
        String externalId = service.generateExternalId(12345L);

        assertNotNull(externalId);
        assertTrue(externalId.startsWith("NH"), "External ID should start with NH");
        assertEquals(17, externalId.length(), "External ID should be 17 characters (NH + 15 digits)");
        assertTrue(externalId.substring(2).matches("\\d{15}"),
            "External ID should have 15 digits after prefix");
    }

    @Test
    void generateAndDecode_shouldRoundTrip() {
        long[] testIds = {1L, 100L, 12345L, 999999L, 1_000_000_000L};

        for (long originalId : testIds) {
            String externalId = service.generateExternalId(originalId);
            Long decodedId = service.decodeExternalId(externalId);

            assertEquals(originalId, decodedId,
                "Round-trip failed for ID: " + originalId);
        }
    }

    @Test
    void generateExternalId_shouldProduceNonSequentialIds() {
        String id1 = service.generateExternalId(1L);
        String id2 = service.generateExternalId(2L);
        String id3 = service.generateExternalId(3L);

        // Extract numeric parts
        long num1 = Long.parseLong(id1.substring(2));
        long num2 = Long.parseLong(id2.substring(2));
        long num3 = Long.parseLong(id3.substring(2));

        // They should not be sequential
        assertNotEquals(num2 - num1, num3 - num2,
            "Sequential internal IDs should not produce sequential external IDs");
    }

    @Test
    void generateExternalId_shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> service.generateExternalId(null));
    }

    @Test
    void generateExternalId_shouldRejectNegative() {
        assertThrows(IllegalArgumentException.class, () -> service.generateExternalId(-1L));
    }

    @Test
    void isValidFormat_shouldAcceptValidFormat() {
        assertTrue(service.isValidFormat("NH847293615028374"));
        assertTrue(service.isValidFormat("NH000000000000001"));
        assertTrue(service.isValidFormat("NH999999999999999"));
    }

    @Test
    void isValidFormat_shouldRejectInvalidFormats() {
        assertFalse(service.isValidFormat(null));
        assertFalse(service.isValidFormat(""));
        assertFalse(service.isValidFormat("NH"));
        assertFalse(service.isValidFormat("NH12345")); // too short
        assertFalse(service.isValidFormat("NH1234567890123456")); // too long
        assertFalse(service.isValidFormat("AB847293615028374")); // wrong prefix
        assertFalse(service.isValidFormat("nh847293615028374")); // lowercase prefix
        assertFalse(service.isValidFormat("NH84729361502837A")); // contains letter
    }

    @Test
    void decodeExternalId_shouldRejectInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> service.decodeExternalId("invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.decodeExternalId("NH12345"));
        assertThrows(IllegalArgumentException.class, () -> service.decodeExternalId(null));
    }

    @Test
    void generatedIds_shouldAllHaveCorrectFormat() {
        for (long i = 0; i < 1000; i++) {
            String externalId = service.generateExternalId(i);
            assertTrue(service.isValidFormat(externalId),
                "Generated ID should pass format validation: " + externalId);
        }
    }
}
