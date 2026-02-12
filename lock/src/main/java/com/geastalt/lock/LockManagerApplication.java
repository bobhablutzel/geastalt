/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application class for the Distributed Lock Manager.
 *
 * <p>This application provides a highly available distributed lock service
 * designed to run across multiple cloud regions. It uses:
 * <ul>
 *   <li>Raft consensus within each regional cluster for high availability</li>
 *   <li>Quorum-based voting across regional leaders for cross-region coordination</li>
 *   <li>gRPC for fast, type-safe communication</li>
 *   <li>Fencing tokens for safe lock ownership</li>
 * </ul>
 *
 * <p>The application supports three main operations:
 * <ul>
 *   <li>AcquireLock - Obtain a distributed lock with a timeout</li>
 *   <li>ReleaseLock - Release a previously acquired lock</li>
 *   <li>CheckLock - Query the status of a lock</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties
public class LockManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LockManagerApplication.class, args);
    }
}
