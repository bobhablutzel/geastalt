/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.lock.integration;

import com.geastalt.lock.config.LockConfig;
import com.geastalt.lock.config.RaftConfig;
import com.geastalt.lock.config.RegionConfig;
import com.geastalt.lock.model.LockStatus;
import com.geastalt.lock.quorum.QuorumManager;
import com.geastalt.lock.raft.LogEntry;
import com.geastalt.lock.raft.RaftNode;
import com.geastalt.lock.raft.RaftStateMachine;
import com.geastalt.lock.service.FencingTokenGenerator;
import com.geastalt.lock.service.LockService;
import com.geastalt.lock.service.LockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the distributed lock manager.
 * Tests the full lock lifecycle including Raft and Quorum coordination.
 */
class DistributedLockTest {

    private LockService lockService;
    private LockStore lockStore;
    private FencingTokenGenerator tokenGenerator;

    @BeforeEach
    void setUp() {
        tokenGenerator = new FencingTokenGenerator();
        lockStore = new LockStore(tokenGenerator);

        var raftConfig = new RaftConfig();
        raftConfig.setNodeId("test-node-1");
        raftConfig.setElectionTimeoutMs(100);
        raftConfig.setHeartbeatIntervalMs(50);

        var regionConfig = new RegionConfig();
        regionConfig.setRegionId("test-region");
        regionConfig.setQuorumTimeoutMs(1000);
        regionConfig.setPeersString("");

        var lockConfig = new LockConfig();

        var stateMachine = new RaftStateMachine(lockStore);
        var raftNode = new TestRaftNode(raftConfig, stateMachine, tokenGenerator);
        var quorumManager = new QuorumManager(regionConfig, lockStore, raftNode);
        quorumManager.init();

        lockService = new LockService(raftNode, quorumManager, lockStore,
                tokenGenerator, lockConfig, regionConfig);
    }

    @Test
    @DisplayName("Should complete full lock lifecycle")
    @Timeout(10)
    void shouldCompleteFullLockLifecycle() throws Exception {
        var lockId = UUID.randomUUID().toString();
        var clientId = "client-1";

        // Acquire lock
        var acquireResult = lockService.acquireLock(lockId, clientId, 30000)
                .get(5, TimeUnit.SECONDS);

        assertTrue(acquireResult.isSuccess(), "Lock acquisition should succeed");
        var lock = acquireResult.getValue();
        assertTrue(lock.fencingToken() > 0);

        // Check lock status
        var checkResult = lockService.checkLock(lockId);
        assertTrue(checkResult.isSuccess());
        var info = checkResult.getValue();
        assertTrue(info.isLocked());
        assertEquals(clientId, info.holderId());
        assertEquals(lock.fencingToken(), info.fencingToken());

        // Release lock
        var releaseResult = lockService.releaseLock(lockId, clientId, lock.fencingToken())
                .get(5, TimeUnit.SECONDS);

        assertTrue(releaseResult.isSuccess(), "Lock release should succeed");

        // Verify lock is released - should return NOT_FOUND since lock no longer exists
        var finalCheck = lockService.checkLock(lockId);
        assertFalse(finalCheck.isSuccess());
        assertEquals(LockStatus.NOT_FOUND, finalCheck.getError().status());
    }

    @Test
    @DisplayName("Should prevent double acquisition")
    @Timeout(10)
    void shouldPreventDoubleAcquisition() throws Exception {
        var lockId = UUID.randomUUID().toString();

        // First acquisition
        var result1 = lockService.acquireLock(lockId, "client-1", 30000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(result1.isSuccess());

        // Second acquisition should fail
        var result2 = lockService.acquireLock(lockId, "client-2", 30000)
                .get(5, TimeUnit.SECONDS);
        assertFalse(result2.isSuccess());
        assertEquals(LockStatus.ALREADY_LOCKED, result2.getError().status());
    }

    @Test
    @DisplayName("Should reject release with wrong token")
    @Timeout(10)
    void shouldRejectReleaseWithWrongToken() throws Exception {
        var lockId = UUID.randomUUID().toString();

        // Acquire
        var acquireResult = lockService.acquireLock(lockId, "client-1", 30000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(acquireResult.isSuccess());

        // Release with wrong token
        var releaseResult = lockService.releaseLock(lockId, "client-1", 999999L)
                .get(5, TimeUnit.SECONDS);

        assertFalse(releaseResult.isSuccess());
        assertEquals(LockStatus.INVALID_TOKEN, releaseResult.getError().status());

        // Lock should still be held
        assertTrue(lockStore.isLocked(lockId));
    }

    @Test
    @DisplayName("Should handle concurrent acquisition attempts")
    @Timeout(30)
    void shouldHandleConcurrentAcquisitionAttempts() throws Exception {
        var lockId = UUID.randomUUID().toString();
        int numClients = 10;

        var successCount = new AtomicInteger(0);
        var winningClient = new AtomicReference<String>();
        var latch = new CountDownLatch(numClients);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numClients; i++) {
                final String clientId = "client-" + i;
                executor.submit(() -> {
                    try {
                        var result = lockService.acquireLock(lockId, clientId, 30000)
                                .get(10, TimeUnit.SECONDS);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                            winningClient.set(clientId);
                        }
                    } catch (Exception e) {
                        // Expected for losers
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS));
        }

        // Exactly one client should have won
        assertEquals(1, successCount.get());
        assertNotNull(winningClient.get());

        // Verify the winning client holds the lock
        var lock = lockStore.get(lockId);
        assertTrue(lock.isPresent());
        assertEquals(winningClient.get(), lock.get().holderId());
    }

    @Test
    @DisplayName("Should allow re-acquisition after release")
    @Timeout(10)
    void shouldAllowReacquisitionAfterRelease() throws Exception {
        var lockId = UUID.randomUUID().toString();

        // First cycle
        var result1 = lockService.acquireLock(lockId, "client-1", 30000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(result1.isSuccess());
        var lock1 = result1.getValue();

        lockService.releaseLock(lockId, "client-1", lock1.fencingToken())
                .get(5, TimeUnit.SECONDS);

        // Second cycle - different client
        var result2 = lockService.acquireLock(lockId, "client-2", 30000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(result2.isSuccess());
        var lock2 = result2.getValue();

        // Fencing token should be higher
        assertTrue(lock2.fencingToken() > lock1.fencingToken());
        assertEquals("client-2", lock2.holderId());
    }

    @Test
    @DisplayName("Should allow acquisition after expiration")
    @Timeout(10)
    void shouldAllowAcquisitionAfterExpiration() throws Exception {
        var lockId = UUID.randomUUID().toString();

        // Acquire with short timeout (minTimeoutMs is 1000ms per LockConfig)
        var result1 = lockService.acquireLock(lockId, "client-1", 1000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(result1.isSuccess());
        var lock1 = result1.getValue();

        // Wait for expiration (must be > 1000ms since that's the min timeout)
        Thread.sleep(1100);

        // Another client should be able to acquire
        var result2 = lockService.acquireLock(lockId, "client-2", 30000)
                .get(5, TimeUnit.SECONDS);
        assertTrue(result2.isSuccess());
        var lock2 = result2.getValue();

        assertTrue(lock2.fencingToken() > lock1.fencingToken());
        assertEquals("client-2", lock2.holderId());
    }

    @Test
    @DisplayName("Should validate lock ID format")
    @Timeout(5)
    void shouldValidateLockIdFormat() throws Exception {
        var result = lockService.acquireLock("invalid-lock-id", "client-1", 30000)
                .get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(LockStatus.ERROR, result.getError().status());
    }

    /**
     * Test RaftNode that simulates being a leader for testing purposes.
     */
    private static class TestRaftNode extends RaftNode {
        private final RaftStateMachine testStateMachine;
        private final java.util.concurrent.atomic.AtomicLong nextIndex = new java.util.concurrent.atomic.AtomicLong(0);

        TestRaftNode(RaftConfig config, RaftStateMachine stateMachine,
                     FencingTokenGenerator tokenGenerator) {
            super(config, null, stateMachine, tokenGenerator);
            this.testStateMachine = stateMachine;
        }

        @Override
        public boolean isLeader() {
            return true; // Always leader for testing
        }

        @Override
        public java.util.Optional<String> getLeaderId() {
            return java.util.Optional.of("test-node-1");
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.geastalt.lock.model.LockResult<?>> submit(
                com.geastalt.lock.raft.LogEntryType type,
                com.geastalt.lock.raft.LockCommand command) {
            // Bypass Raft for testing - directly apply to state machine
            var future = new java.util.concurrent.CompletableFuture<com.geastalt.lock.model.LockResult<?>>();

            long index = nextIndex.incrementAndGet();
            var entry = new LogEntry(index, 1, type, command.serialize());

            testStateMachine.apply(entry, future::complete);

            return future;
        }

        @Override
        public void init() {
            // No-op for testing
        }

        @Override
        public void startElectionProcess() {
            // No-op for testing
        }

        @Override
        public void stop() {
            // No-op for testing
        }
    }
}
