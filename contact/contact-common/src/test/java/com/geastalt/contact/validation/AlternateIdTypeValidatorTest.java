package com.geastalt.contact.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AlternateIdTypeValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"NEW_NATIONS", "OLD_NATIONS", "PAN_HASH", "CONTACT_TUPLE"})
    void existingTypesAreValid(String type) {
        assertDoesNotThrow(() -> AlternateIdTypeValidator.validate(type));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOM_TYPE", "MyType", "x", "A123"})
    void customTypesAreValid(String type) {
        assertDoesNotThrow(() -> AlternateIdTypeValidator.validate(type));
    }

    @Test
    void nullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.validate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankThrows(String type) {
        assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.validate(type));
    }

    @ParameterizedTest
    @ValueSource(strings = {"_INTERNAL", "$SYSTEM"})
    void reservedPrefixThrows(String type) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.validate(type));
        assertTrue(ex.getMessage().contains("reserved"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123_BAD", "1ABC"})
    void digitStartThrows(String type) {
        assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.validate(type));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID-TYPE", "has space", "dot.name"})
    void invalidCharactersThrow(String type) {
        assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.validate(type));
    }

    @Test
    void resolveAndValidateReturnsDefaultForNull() {
        assertEquals(AlternateIdTypeValidator.DEFAULT_TYPE,
                AlternateIdTypeValidator.resolveAndValidate(null));
    }

    @Test
    void resolveAndValidateReturnsDefaultForBlank() {
        assertEquals(AlternateIdTypeValidator.DEFAULT_TYPE,
                AlternateIdTypeValidator.resolveAndValidate(""));
    }

    @Test
    void resolveAndValidateReturnsProvidedType() {
        assertEquals("CUSTOM_TYPE",
                AlternateIdTypeValidator.resolveAndValidate("CUSTOM_TYPE"));
    }

    @Test
    void resolveAndValidateRejectsInvalidType() {
        assertThrows(IllegalArgumentException.class,
                () -> AlternateIdTypeValidator.resolveAndValidate("_RESERVED"));
    }
}
