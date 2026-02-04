package com.geastalt.member;

import com.geastalt.member.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SearchComparisonTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9001;

    public static void main(String[] args) throws Exception {
        int totalRequests = 5000;
        int concurrency = 50;

        System.out.println("=== Search Performance Comparison ===");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Concurrency: " + concurrency);
        System.out.println();

        // Create channel pool
        List<ManagedChannel> channels = new ArrayList<>();
        List<MemberServiceGrpc.MemberServiceBlockingStub> stubs = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(HOST, PORT)
                    .usePlaintext()
                    .build();
            channels.add(channel);
            stubs.add(MemberServiceGrpc.newBlockingStub(channel));
        }

        // Test exact search
        System.out.println("--- Exact Search (SMITH) ---");
        runTest(stubs, totalRequests, concurrency, false);

        // Test wildcard search
        System.out.println("\n--- Wildcard Search (SMITH*) ---");
        runTest(stubs, totalRequests, concurrency, true);

        // Cleanup
        for (ManagedChannel channel : channels) {
            channel.shutdown();
        }
    }

    private static void runTest(List<MemberServiceGrpc.MemberServiceBlockingStub> stubs,
                                int totalRequests, int concurrency, boolean useWildcard) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicInteger completedRequests = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        String[] lastNames = useWildcard ?
            new String[]{"Smith*", "Johnson*", "Williams*", "Brown*", "Jones*"} :
            new String[]{"Smith", "Johnson", "Williams", "Brown", "Jones"};

        long startTime = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int threadIndex = i % concurrency;
            executor.submit(() -> {
                try {
                    Random localRandom = ThreadLocalRandom.current();
                    long reqStart = System.nanoTime();

                    SearchMembersRequest request = SearchMembersRequest.newBuilder()
                            .setLastName(lastNames[localRandom.nextInt(lastNames.length)])
                            .setMaxResults(25)
                            .setIncludeTotalCount(false)
                            .build();
                    stubs.get(threadIndex).searchMembers(request);

                    totalLatency.addAndGet(System.nanoTime() - reqStart);
                    completedRequests.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double tps = completedRequests.get() / durationSeconds;
        double avgLatencyMs = (totalLatency.get() / completedRequests.get()) / 1_000_000.0;

        System.out.printf("Completed: %d requests%n", completedRequests.get());
        System.out.printf("Errors: %d%n", errors.get());
        System.out.printf("Duration: %.2f seconds%n", durationSeconds);
        System.out.printf("Throughput: %.2f TPS%n", tps);
        System.out.printf("Avg Latency: %.2f ms%n", avgLatencyMs);

        executor.shutdown();
    }
}
