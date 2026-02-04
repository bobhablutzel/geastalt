package com.geastalt.member.entity;

/**
 * Types of alternate identifiers that can be associated with a member.
 */
public enum AlternateIdType {
    /** Current FPE-generated identifier in format NH + 15 digits */
    NEW_NATIONS,
    /** Legacy Nations identifier */
    OLD_NATIONS,
    /** PAN hash identifier */
    PAN_HASH,
    /** Member tuple identifier */
    MEMBER_TUPLE
}
