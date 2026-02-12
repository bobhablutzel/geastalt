/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.lock.model;

/**
 * Represents the status of a lock operation.
 */
public enum LockStatus {
    /**
     * Operation completed successfully.
     */
    OK,

    /**
     * Lock is already held by another client.
     */
    ALREADY_LOCKED,

    /**
     * Lock not found (never acquired).
     */
    NOT_FOUND,

    /**
     * Fencing token mismatch.
     */
    INVALID_TOKEN,

    /**
     * Lock has expired.
     */
    EXPIRED,

    /**
     * Quorum could not be reached across regions.
     */
    QUORUM_FAILED,

    /**
     * Internal error occurred.
     */
    ERROR,

    /**
     * Operation timed out.
     */
    TIMEOUT,

    /**
     * This node is not the Raft leader.
     */
    NOT_LEADER;

    /**
     * Checks if this status represents a successful operation.
     */
    public boolean isSuccess() {
        return this == OK;
    }

    /**
     * Checks if this status represents a retryable error.
     */
    public boolean isRetryable() {
        return this == QUORUM_FAILED || this == TIMEOUT || this == NOT_LEADER;
    }
}
