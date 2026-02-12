/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.lock.raft;

/**
 * Types of Raft log entries.
 */
public enum LogEntryType {
    /**
     * No operation - used for leader commitment.
     */
    NOOP,

    /**
     * Acquire a lock.
     */
    ACQUIRE_LOCK,

    /**
     * Release a lock.
     */
    RELEASE_LOCK,

    /**
     * Extend a lock timeout.
     */
    EXTEND_LOCK
}
