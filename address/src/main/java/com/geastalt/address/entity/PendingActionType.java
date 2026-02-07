package com.geastalt.address.entity;

/**
 * Types of pending actions that can be associated with a contact.
 */
public enum PendingActionType {
    /** Generate external identifiers for the contact */
    GENERATE_EXTERNAL_IDENTIFIERS,
    /** Validate the contact's address */
    VALIDATE_ADDRESS
}
