package com.geastalt.member;

import com.geastalt.member.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive gRPC Load Test for Member Service.
 * Tests all gRPC endpoints with burn-in phase followed by sustained load test.
 *
 * Usage: java GrpcLoadTest [concurrency] [burnInSeconds] [testDurationSeconds]
 * Defaults: 50 concurrent, 30s burn-in, 300s (5 min) test
 */
public class GrpcLoadTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9001;
    private static final int MAX_MEMBER_ID = 6_647_795;

    // Test data loaded from database
    private static List<Long> sampleMemberIds = new ArrayList<>();
    private static List<String> sampleAlternateIds = new ArrayList<>();
    private static List<String> samplePhoneNumbers = new ArrayList<>();
    private static List<Long> samplePlanIds = new ArrayList<>();
    private static final String[] LAST_NAMES = {"smith", "johnson", "williams", "brown", "jones", "davis", "miller", "wilson", "moore", "taylor"};
    private static final String[] FIRST_NAME_PREFIXES = {"j*", "m*", "a*", "s*", "d*", "r*", "c*", "b*", "t*", "l*"};

    public static void main(String[] args) throws Exception {
        int concurrency = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int burnInSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int testDurationSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 300;

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         MEMBER SERVICE gRPC COMPREHENSIVE LOAD TEST          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.printf("  Concurrency:      %d threads%n", concurrency);
        System.out.printf("  Burn-in:          %d seconds%n", burnInSeconds);
        System.out.printf("  Test Duration:    %d seconds (%d minutes)%n", testDurationSeconds, testDurationSeconds / 60);
        System.out.println();

        // Load test data from database
        loadTestData();

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

        // Define all test operations
        List<TestOperation> operations = List.of(
                new TestOperation("GetAddresses", stubs, GrpcLoadTest::testGetAddresses),
                new TestOperation("GetPhones", stubs, GrpcLoadTest::testGetPhones),
                new TestOperation("GetEmails", stubs, GrpcLoadTest::testGetEmails),
                new TestOperation("GetMemberByAlternateId", stubs, GrpcLoadTest::testGetMemberByAlternateId),
                new TestOperation("SearchMembersByPhone", stubs, GrpcLoadTest::testSearchMembersByPhone),
                new TestOperation("SearchMembers", stubs, GrpcLoadTest::testSearchMembers),
                new TestOperation("GetPlans", stubs, GrpcLoadTest::testGetPlans),
                new TestOperation("GetPlan", stubs, GrpcLoadTest::testGetPlan),
                new TestOperation("GetMemberPlans", stubs, GrpcLoadTest::testGetMemberPlans),
                new TestOperation("GetCurrentMemberPlan", stubs, GrpcLoadTest::testGetCurrentMemberPlan)
        );

        // Run tests for each operation
        for (TestOperation op : operations) {
            System.out.println();
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.printf("  Testing: %s%n", op.name);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // Burn-in phase
            System.out.printf("%n[Burn-in Phase] Running for %d seconds...%n", burnInSeconds);
            TestResults burnInResults = runTimedTest(op, concurrency, Duration.ofSeconds(burnInSeconds));
            printResults("Burn-in", burnInResults);

            // Main test phase
            System.out.printf("%n[Main Test Phase] Running for %d seconds...%n", testDurationSeconds);
            TestResults mainResults = runTimedTest(op, concurrency, Duration.ofSeconds(testDurationSeconds));
            printResults("Main Test", mainResults);
        }

        // Summary
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    ALL TESTS COMPLETE                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Cleanup
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
    }

    private static void loadTestData() {
        System.out.println("Loading test data from database...");

        String dbUrl = "jdbc:postgresql://192.168.1.17:5432/member";
        String dbUser = "bob";
        String dbPass = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "Goat-Pen78";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            // Load sample member IDs
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM members ORDER BY RANDOM() LIMIT 10000")) {
                while (rs.next()) {
                    sampleMemberIds.add(rs.getLong("id"));
                }
            }

            // Load sample alternate IDs
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT alternate_id FROM member_alternate_ids WHERE id_type = 'NEW_NATIONS' ORDER BY RANDOM() LIMIT 10000")) {
                while (rs.next()) {
                    sampleAlternateIds.add(rs.getString("alternate_id"));
                }
            }

            // Load sample phone numbers
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT phone_number FROM member_phones ORDER BY RANDOM() LIMIT 10000")) {
                while (rs.next()) {
                    samplePhoneNumbers.add(rs.getString("phone_number"));
                }
            }

            // Load sample plan IDs
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM plans")) {
                while (rs.next()) {
                    samplePlanIds.add(rs.getLong("id"));
                }
            }

            System.out.printf("  Loaded %d member IDs, %d alternate IDs, %d phone numbers, %d plan IDs%n",
                    sampleMemberIds.size(), sampleAlternateIds.size(), samplePhoneNumbers.size(), samplePlanIds.size());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load test data from database: " + e.getMessage(), e);
        }

        // Validate that we have test data
        if (sampleMemberIds.isEmpty()) {
            throw new RuntimeException("No member IDs loaded from database");
        }
        if (sampleAlternateIds.isEmpty()) {
            throw new RuntimeException("No alternate IDs loaded from database");
        }
        if (samplePhoneNumbers.isEmpty()) {
            throw new RuntimeException("No phone numbers loaded from database");
        }
        if (samplePlanIds.isEmpty()) {
            throw new RuntimeException("No plan IDs loaded from database");
        }
    }

    private static TestResults runTimedTest(TestOperation op, int concurrency, Duration duration) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        LongAdder completedRequests = new LongAdder();
        LongAdder errors = new LongAdder();
        LongAdder totalLatencyNanos = new LongAdder();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(duration);

        // Submit workers
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < concurrency; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                Random localRandom = new Random();
                while (Instant.now().isBefore(endTime)) {
                    long reqStart = System.nanoTime();
                    try {
                        op.executor.execute(op.stubs.get(threadId), localRandom);
                        long latency = System.nanoTime() - reqStart;
                        totalLatencyNanos.add(latency);
                        completedRequests.increment();

                        // Sample latencies for percentile calculation (1 in 100)
                        if (localRandom.nextInt(100) == 0) {
                            latencies.add(latency);
                        }
                    } catch (Exception e) {
                        errors.increment();
                    }
                }
            }));
        }

        // Wait for completion
        for (Future<?> f : futures) {
            f.get();
        }

        executor.shutdown();

        // Calculate results
        long actualDurationMs = Duration.between(startTime, Instant.now()).toMillis();
        return new TestResults(
                completedRequests.sum(),
                errors.sum(),
                actualDurationMs,
                totalLatencyNanos.sum(),
                new ArrayList<>(latencies)
        );
    }

    private static void printResults(String phase, TestResults results) {
        double durationSec = results.durationMs / 1000.0;
        double tps = results.completedRequests / durationSec;
        double avgLatencyMs = results.completedRequests > 0
                ? (results.totalLatencyNanos / results.completedRequests) / 1_000_000.0
                : 0;

        // Calculate percentiles
        List<Long> sortedLatencies = new ArrayList<>(results.latencySamples);
        Collections.sort(sortedLatencies);

        double p50 = percentile(sortedLatencies, 50);
        double p90 = percentile(sortedLatencies, 90);
        double p99 = percentile(sortedLatencies, 99);

        System.out.printf("%n  %s Results:%n", phase);
        System.out.printf("    Requests:     %,d%n", results.completedRequests);
        System.out.printf("    Errors:       %,d (%.2f%%)%n", results.errors,
                results.completedRequests > 0 ? (results.errors * 100.0 / (results.completedRequests + results.errors)) : 0);
        System.out.printf("    Duration:     %.2f seconds%n", durationSec);
        System.out.printf("    Throughput:   %.2f TPS%n", tps);
        System.out.printf("    Latency:%n");
        System.out.printf("      Average:    %.2f ms%n", avgLatencyMs);
        System.out.printf("      p50:        %.2f ms%n", p50);
        System.out.printf("      p90:        %.2f ms%n", p90);
        System.out.printf("      p99:        %.2f ms%n", p99);
    }

    private static double percentile(List<Long> sortedLatencies, int percentile) {
        if (sortedLatencies.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.size()) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index) / 1_000_000.0;
    }

    // Test operation implementations

    private static void testGetAddresses(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long memberId = sampleMemberIds.get(random.nextInt(sampleMemberIds.size()));
        GetAddressesRequest request = GetAddressesRequest.newBuilder()
                .setMemberId(memberId)
                .build();
        stub.getAddresses(request);
    }

    private static void testGetPhones(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long memberId = sampleMemberIds.get(random.nextInt(sampleMemberIds.size()));
        GetPhonesRequest request = GetPhonesRequest.newBuilder()
                .setMemberId(memberId)
                .build();
        stub.getPhones(request);
    }

    private static void testGetEmails(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long memberId = sampleMemberIds.get(random.nextInt(sampleMemberIds.size()));
        GetEmailsRequest request = GetEmailsRequest.newBuilder()
                .setMemberId(memberId)
                .build();
        stub.getEmails(request);
    }

    private static void testGetMemberByAlternateId(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        String alternateId = sampleAlternateIds.get(random.nextInt(sampleAlternateIds.size()));
        GetMemberByAlternateIdRequest request = GetMemberByAlternateIdRequest.newBuilder()
                .setAlternateId(alternateId)
                .build();
        stub.getMemberByAlternateId(request);
    }

    private static void testSearchMembersByPhone(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        String phone = samplePhoneNumbers.get(random.nextInt(samplePhoneNumbers.size()));
        SearchMembersByPhoneRequest request = SearchMembersByPhoneRequest.newBuilder()
                .setPhoneNumber(phone)
                .setMaxResults(25)
                .build();
        stub.searchMembersByPhone(request);
    }

    private static void testSearchMembers(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        boolean useWildcard = random.nextBoolean();
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];

        SearchMembersRequest.Builder builder = SearchMembersRequest.newBuilder()
                .setLastName(useWildcard ? lastName + "*" : lastName)
                .setMaxResults(25)
                .setIncludeTotalCount(false);

        if (useWildcard && random.nextBoolean()) {
            builder.setFirstName(FIRST_NAME_PREFIXES[random.nextInt(FIRST_NAME_PREFIXES.length)]);
        }

        stub.searchMembers(builder.build());
    }

    private static void testGetPlans(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        GetPlansRequest request = GetPlansRequest.newBuilder().build();
        stub.getPlans(request);
    }

    private static void testGetPlan(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long planId = samplePlanIds.get(random.nextInt(samplePlanIds.size()));
        GetPlanRequest request = GetPlanRequest.newBuilder()
                .setPlanId(planId)
                .build();
        stub.getPlan(request);
    }

    private static void testGetMemberPlans(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long memberId = sampleMemberIds.get(random.nextInt(sampleMemberIds.size()));
        GetMemberPlansRequest request = GetMemberPlansRequest.newBuilder()
                .setMemberId(memberId)
                .build();
        stub.getMemberPlans(request);
    }

    private static void testGetCurrentMemberPlan(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random) {
        long memberId = sampleMemberIds.get(random.nextInt(sampleMemberIds.size()));
        GetCurrentMemberPlanRequest request = GetCurrentMemberPlanRequest.newBuilder()
                .setMemberId(memberId)
                .build();
        stub.getCurrentMemberPlan(request);
    }

    // Helper classes

    @FunctionalInterface
    interface TestExecutor {
        void execute(MemberServiceGrpc.MemberServiceBlockingStub stub, Random random);
    }

    record TestOperation(
            String name,
            List<MemberServiceGrpc.MemberServiceBlockingStub> stubs,
            TestExecutor executor
    ) {}

    record TestResults(
            long completedRequests,
            long errors,
            long durationMs,
            long totalLatencyNanos,
            List<Long> latencySamples
    ) {}
}
