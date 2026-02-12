/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.lock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for lock behavior.
 */
@Configuration
@ConfigurationProperties(prefix = "lockmgr.lock")
@Getter
@Setter
public class LockConfig {

    private long defaultTimeoutMs = 30000;
    private long maxTimeoutMs = 300000;
    private long minTimeoutMs = 1000;

    /**
     * Validates and normalizes a timeout value.
     */
    public long normalizeTimeout(long requestedTimeoutMs) {
        if (requestedTimeoutMs <= 0) {
            return defaultTimeoutMs;
        }
        return Math.min(Math.max(requestedTimeoutMs, minTimeoutMs), maxTimeoutMs);
    }
}
