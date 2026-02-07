package com.geastalt.contact;

import com.geastalt.contact.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.datafaker.Faker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * CSV-based gRPC Load Test for Contact Service.
 * Seeds contacts from a CSV file via BulkCreateContacts, then runs a load test battery.
 *
 * Usage: java GrpcLoadTest [concurrency] [burnInSeconds] [testDurationSeconds]
 *                          [seedingChannels] [seedBatchSize] [host] [port] [csvPath]
 * Defaults: 50 concurrent, 30s burn-in, 300s test, 8 seeding channels, 200 batch size,
 *           localhost, 9001, test-data/contacts_with_addresses.csv
 */
public class GrpcLoadTest {

    private static final int SAMPLE_CONTACT_IDS = 10_000;
    private static final int SAMPLE_PHONES = 10_000;
    private static final int SAMPLE_LAST_NAMES = 500;

    // Collected during seeding
    private static final List<Long> sampleContactIds = new CopyOnWriteArrayList<>();
    private static final List<String> samplePhoneNumbers = new CopyOnWriteArrayList<>();
    private static final Set<String> sampleLastNames = ConcurrentHashMap.newKeySet();
    private static final List<Long> samplePlanIds = new CopyOnWriteArrayList<>();

    private static final AtomicLong seededCount = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        int concurrency = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int burnInSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int testDurationSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 300;
        int seedingChannels = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        int seedBatchSize = args.length > 4 ? Integer.parseInt(args[4]) : 200;
        String host = args.length > 5 ? args[5] : "localhost";
        int port = args.length > 6 ? Integer.parseInt(args[6]) : 9001;
        String csvPath = args.length > 7 ? args[7] : "test-data/contacts_with_addresses.csv";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       CONTACT SERVICE gRPC CSV-BASED LOAD TEST              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.printf("  Host:             %s:%d%n", host, port);
        System.out.printf("  Concurrency:      %d threads%n", concurrency);
        System.out.printf("  Burn-in:          %d seconds%n", burnInSeconds);
        System.out.printf("  Test Duration:    %d seconds (%d minutes)%n", testDurationSeconds, testDurationSeconds / 60);
        System.out.printf("  Seeding Channels: %d%n", seedingChannels);
        System.out.printf("  Seed Batch Size:  %d%n", seedBatchSize);
        System.out.printf("  CSV Path:         %s%n", csvPath);
        System.out.println();

        // Phase 1: Create test plans
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Phase 1: Creating test plans");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        createTestPlans(host, port);

        // Phase 2: Seed contacts from CSV
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Phase 2: Seeding contacts from CSV");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        seedContactsFromCsv(host, port, csvPath, seedingChannels, seedBatchSize);

        System.out.printf("%n  Collected samples: %d contact IDs, %d phone numbers, %d last names, %d plan IDs%n",
                sampleContactIds.size(), samplePhoneNumbers.size(), sampleLastNames.size(), samplePlanIds.size());

        if (sampleContactIds.isEmpty()) {
            throw new RuntimeException("No contact IDs collected during seeding");
        }

        // Phase 3: Assign plans to sample contacts
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Phase 3: Assigning plans to contacts");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        assignPlansToContacts(host, port);

        // Phase 4: Run load test battery
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Phase 4: Running load test battery");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Create channel pool for load test
        List<ManagedChannel> channels = new ArrayList<>();
        List<ContactServiceGrpc.ContactServiceBlockingStub> stubs = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            channels.add(channel);
            stubs.add(ContactServiceGrpc.newBlockingStub(channel));
        }

        // Convert last names set to list for random access
        List<String> lastNamesList = new ArrayList<>(sampleLastNames);

        List<TestOperation> operations = List.of(
                new TestOperation("GetAddresses", stubs, GrpcLoadTest::testGetAddresses),
                new TestOperation("GetPhones", stubs, GrpcLoadTest::testGetPhones),
                new TestOperation("GetEmails", stubs, GrpcLoadTest::testGetEmails),
                new TestOperation("SearchContactsByPhone", stubs, GrpcLoadTest::testSearchContactsByPhone),
                new TestOperation("SearchContacts", stubs, (stub, random) -> testSearchContacts(stub, random, lastNamesList)),
                new TestOperation("GetContactById", stubs, GrpcLoadTest::testGetContactById),
                new TestOperation("GetPlans", stubs, GrpcLoadTest::testGetPlans),
                new TestOperation("GetPlan", stubs, GrpcLoadTest::testGetPlan),
                new TestOperation("GetContactPlans", stubs, GrpcLoadTest::testGetContactPlans),
                new TestOperation("GetCurrentContactPlan", stubs, GrpcLoadTest::testGetCurrentContactPlan)
        );

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

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    ALL TESTS COMPLETE                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
    }

    // ─── Phase 1: Create test plans ─────────────────────────────────────────────

    private static void createTestPlans(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        try {
            ContactServiceGrpc.ContactServiceBlockingStub stub = ContactServiceGrpc.newBlockingStub(channel);

            String[][] plans = {
                    {"Gold Health Plan", "1001", "Aetna"},
                    {"Silver Health Plan", "1002", "Aetna"},
                    {"Bronze Health Plan", "1003", "Humana"},
                    {"Platinum Health Plan", "1004", "UnitedHealth"},
                    {"Basic Health Plan", "1005", "Cigna"}
            };

            for (String[] plan : plans) {
                CreatePlanResponse response = stub.createPlan(CreatePlanRequest.newBuilder()
                        .setPlanName(plan[0])
                        .setCarrierId(Integer.parseInt(plan[1]))
                        .setCarrierName(plan[2])
                        .build());
                samplePlanIds.add(response.getPlan().getPlanId());
                System.out.printf("  Created plan: %s (ID: %d)%n", plan[0], response.getPlan().getPlanId());
            }
        } finally {
            channel.shutdownNow();
        }
    }

    // ─── Phase 2: Seed contacts from CSV ────────────────────────────────────────

    private static void seedContactsFromCsv(String host, int port, String csvPath,
                                            int senderCount, int batchSize) throws Exception {
        BlockingQueue<List<ContactInput>> batchQueue = new LinkedBlockingQueue<>(senderCount * 2);
        Faker faker = new Faker();

        Instant startTime = Instant.now();

        // Producer: read CSV and enqueue batches
        Thread producer = Thread.ofVirtual().name("csv-reader").start(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath), 1 << 16)) {
                reader.readLine(); // skip header

                List<ContactInput> batch = new ArrayList<>(batchSize);
                String line;
                while ((line = reader.readLine()) != null) {
                    ContactInput contact = parseCsvLine(line, faker);
                    if (contact != null) {
                        batch.add(contact);
                        if (batch.size() >= batchSize) {
                            batchQueue.put(batch);
                            batch = new ArrayList<>(batchSize);
                        }
                    }
                }
                // Send remaining
                if (!batch.isEmpty()) {
                    batchQueue.put(batch);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("CSV reading failed: " + e.getMessage(), e);
            }
            // Send poison pills
            for (int i = 0; i < senderCount; i++) {
                try {
                    batchQueue.put(List.of());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Sender threads: consume batches and call BulkCreateContacts
        ExecutorService senders = Executors.newFixedThreadPool(senderCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < senderCount; i++) {
            futures.add(senders.submit(() -> {
                ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .maxInboundMessageSize(64 * 1024 * 1024)
                        .build();
                try {
                    while (true) {
                        List<ContactInput> batch = batchQueue.take();
                        if (batch.isEmpty()) break; // poison pill

                        BulkCreateContactsRequest request = BulkCreateContactsRequest.newBuilder()
                                .addAllContacts(batch)
                                .setSkipGenerateExternalIdentifiers(true)
                                .setSkipValidateAddress(true)
                                .build();

                        try {
                            // Re-apply deadline per call
                            BulkCreateContactsResponse response =
                                    ContactServiceGrpc.newBlockingStub(channel)
                                            .withDeadlineAfter(120, TimeUnit.SECONDS)
                                            .bulkCreateContacts(request);

                            collectSamples(response, batch);

                            long count = seededCount.addAndGet(response.getSuccessCount());
                            if (count % 50_000 < batch.size()) {
                                double elapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
                                System.out.printf("  Progress: %,d contacts seeded (%.0f/sec)%n",
                                        count, count / elapsed);
                            }
                        } catch (Exception e) {
                            System.err.printf("  Batch send failed: %s%n", e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    channel.shutdownNow();
                }
            }));
        }

        producer.join();
        for (Future<?> f : futures) {
            f.get();
        }
        senders.shutdown();

        double totalElapsed = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
        System.out.printf("%n  Seeding complete: %,d contacts in %.1f seconds (%.0f/sec)%n",
                seededCount.get(), totalElapsed, seededCount.get() / totalElapsed);
    }

    private static ContactInput parseCsvLine(String line, Faker faker) {
        // CSV: first_name,last_name,street_number,street_name,street_type,city,state,zip_code,longitude,latitude
        String[] fields = line.split(",", -1);
        if (fields.length < 8) return null;

        String firstName = fields[0].trim();
        String lastName = fields[1].trim();
        if (firstName.isEmpty() || lastName.isEmpty()) return null;

        String streetNumber = fields[2].trim();
        String streetName = fields[3].trim();
        String streetType = fields[4].trim();
        String city = fields[5].trim();
        String state = fields[6].trim();
        String zipCode = fields[7].trim();

        String streetAddress = streetNumber + " " + streetName +
                (streetType.isEmpty() ? "" : " " + streetType);

        // Generate synthetic phone and email
        String phone = faker.phoneNumber().subscriberNumber(10);
        String email = firstName.toLowerCase() + "." + lastName.toLowerCase() +
                ThreadLocalRandom.current().nextInt(100000) + "@example.com";

        return ContactInput.newBuilder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .addPhones(PhoneInput.newBuilder()
                        .setPhoneNumber(phone)
                        .setPhoneType(PhoneType.PHONE_HOME)
                        .build())
                .addEmails(EmailInput.newBuilder()
                        .setEmail(email)
                        .setEmailType(EmailType.EMAIL_HOME)
                        .build())
                .addAddresses(AddressInput.newBuilder()
                        .setAddressType(com.geastalt.contact.grpc.AddressType.HOME)
                        .setStreetAddress(streetAddress)
                        .setCity(city)
                        .setState(state)
                        .setZipCode(zipCode)
                        .build())
                .build();
    }

    private static void collectSamples(BulkCreateContactsResponse response, List<ContactInput> batch) {
        // Reservoir sampling for contact IDs and phone numbers
        for (BulkCreateResult result : response.getResultsList()) {
            if (!result.getSuccess()) continue;

            long count = seededCount.get(); // approximate, fine for sampling
            long contactId = result.getContactId();

            // Reservoir sample contact IDs
            if (sampleContactIds.size() < SAMPLE_CONTACT_IDS) {
                sampleContactIds.add(contactId);
            } else {
                int r = ThreadLocalRandom.current().nextInt((int) Math.min(count, Integer.MAX_VALUE));
                if (r < SAMPLE_CONTACT_IDS) {
                    sampleContactIds.set(r, contactId);
                }
            }

            // Collect phone numbers from the corresponding input
            int index = result.getIndex();
            if (index < batch.size()) {
                ContactInput input = batch.get(index);

                // Reservoir sample phones
                if (input.getPhonesCount() > 0) {
                    String phone = input.getPhones(0).getPhoneNumber();
                    if (samplePhoneNumbers.size() < SAMPLE_PHONES) {
                        samplePhoneNumbers.add(phone);
                    } else {
                        int r = ThreadLocalRandom.current().nextInt((int) Math.min(count, Integer.MAX_VALUE));
                        if (r < SAMPLE_PHONES) {
                            samplePhoneNumbers.set(r, phone);
                        }
                    }
                }

                // Collect last names (up to limit)
                if (sampleLastNames.size() < SAMPLE_LAST_NAMES) {
                    sampleLastNames.add(input.getLastName().toLowerCase());
                }
            }
        }
    }

    // ─── Phase 3: Assign plans to contacts ──────────────────────────────────────

    private static void assignPlansToContacts(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        try {
            ContactServiceGrpc.ContactServiceBlockingStub stub = ContactServiceGrpc.newBlockingStub(channel);

            int assignCount = Math.min(1000, sampleContactIds.size());
            String effectiveDate = Instant.now().atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT);
            String expirationDate = Instant.now().plusSeconds(365L * 24 * 3600).atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT);

            int assigned = 0;
            for (int i = 0; i < assignCount; i++) {
                long contactId = sampleContactIds.get(i);
                long planId = samplePlanIds.get(i % samplePlanIds.size());

                try {
                    stub.addContactPlan(AddContactPlanRequest.newBuilder()
                            .setContactId(contactId)
                            .setPlanId(planId)
                            .setEffectiveDate(effectiveDate)
                            .setExpirationDate(expirationDate)
                            .build());
                    assigned++;
                } catch (Exception e) {
                    // Some may fail if contact doesn't exist, that's fine
                }
            }
            System.out.printf("  Assigned plans to %d contacts%n", assigned);
        } finally {
            channel.shutdownNow();
        }
    }

    // ─── Phase 4: Load test execution ───────────────────────────────────────────

    private static TestResults runTimedTest(TestOperation op, int concurrency, Duration duration) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        LongAdder completedRequests = new LongAdder();
        LongAdder errors = new LongAdder();
        LongAdder totalLatencyNanos = new LongAdder();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(duration);

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

                        if (localRandom.nextInt(100) == 0) {
                            latencies.add(latency);
                        }
                    } catch (Exception e) {
                        errors.increment();
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

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

    // ─── Test operation implementations ─────────────────────────────────────────

    private static void testGetAddresses(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getAddresses(GetAddressesRequest.newBuilder().setContactId(contactId).build());
    }

    private static void testGetPhones(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getPhones(GetPhonesRequest.newBuilder().setContactId(contactId).build());
    }

    private static void testGetEmails(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getEmails(GetEmailsRequest.newBuilder().setContactId(contactId).build());
    }

    private static void testSearchContactsByPhone(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        String phone = samplePhoneNumbers.get(random.nextInt(samplePhoneNumbers.size()));
        stub.searchContactsByPhone(SearchContactsByPhoneRequest.newBuilder()
                .setPhoneNumber(phone)
                .setMaxResults(25)
                .build());
    }

    private static void testSearchContacts(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random,
                                           List<String> lastNames) {
        String lastName = lastNames.get(random.nextInt(lastNames.size()));
        boolean useWildcard = random.nextBoolean();

        SearchContactsRequest.Builder builder = SearchContactsRequest.newBuilder()
                .setLastName(useWildcard ? lastName + "*" : lastName)
                .setMaxResults(25)
                .setIncludeTotalCount(false);

        stub.searchContacts(builder.build());
    }

    private static void testGetContactById(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getContactById(GetContactByIdRequest.newBuilder().setContactId(contactId).build());
    }

    private static void testGetPlans(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        stub.getPlans(GetPlansRequest.newBuilder().build());
    }

    private static void testGetPlan(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long planId = samplePlanIds.get(random.nextInt(samplePlanIds.size()));
        stub.getPlan(GetPlanRequest.newBuilder().setPlanId(planId).build());
    }

    private static void testGetContactPlans(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getContactPlans(GetContactPlansRequest.newBuilder().setContactId(contactId).build());
    }

    private static void testGetCurrentContactPlan(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random) {
        long contactId = sampleContactIds.get(random.nextInt(sampleContactIds.size()));
        stub.getCurrentContactPlan(GetCurrentContactPlanRequest.newBuilder().setContactId(contactId).build());
    }

    // ─── Helper classes ─────────────────────────────────────────────────────────

    @FunctionalInterface
    interface TestExecutor {
        void execute(ContactServiceGrpc.ContactServiceBlockingStub stub, Random random);
    }

    record TestOperation(
            String name,
            List<ContactServiceGrpc.ContactServiceBlockingStub> stubs,
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
