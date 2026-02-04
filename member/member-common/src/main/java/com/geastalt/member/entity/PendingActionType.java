package com.geastalt.member.entity;

/**
 * Types of pending actions that can be associated with a member.
 */
public enum PendingActionType {
    /** Generate external identifiers for the member */
    GENERATE_EXTERNAL_IDENTIFIERS,
    /** Validate the member's address */
    VALIDATE_ADDRESS
}
