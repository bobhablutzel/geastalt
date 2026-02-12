/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.validation;

/**
 * Validates alternate ID type strings. Types must be valid Java identifiers
 * and must not start with {@code _} or {@code $} (reserved for internal use).
 */
public final class AlternateIdTypeValidator {

    public static final String DEFAULT_TYPE = "NEW_NATIONS";

    private AlternateIdTypeValidator() {}

    /**
     * Validates that the given type is a legal alternate ID type.
     *
     * @throws IllegalArgumentException if the type is null, blank, starts with
     *         {@code _} or {@code $}, or is not a valid Java identifier
     */
    public static void validate(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Alternate ID type must not be null or blank");
        }
        char first = type.charAt(0);
        if (first == '_' || first == '$') {
            throw new IllegalArgumentException(
                    "Alternate ID type must not start with '_' or '$' (reserved for internal use): " + type);
        }
        if (!Character.isJavaIdentifierStart(first)) {
            throw new IllegalArgumentException(
                    "Alternate ID type must start with a letter: " + type);
        }
        for (int i = 1; i < type.length(); i++) {
            if (!Character.isJavaIdentifierPart(type.charAt(i))) {
                throw new IllegalArgumentException(
                        "Alternate ID type contains invalid character at position " + i + ": " + type);
            }
        }
    }

    /**
     * Returns {@link #DEFAULT_TYPE} when the input is null or blank,
     * otherwise validates and returns the input.
     */
    public static String resolveAndValidate(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_TYPE;
        }
        validate(type);
        return type;
    }
}
