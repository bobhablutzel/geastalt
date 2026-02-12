/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact;

import com.geastalt.contact.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.datafaker.Faker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bulk create load test using synthetic data from DataFaker.
 *
 * Usage: java BulkCreateLoadTest [totalContacts] [batchSize] [host] [port]
 *
 * Defaults:
 *   totalContacts = 10000
 *   batchSize = 100
 *   host = localhost
 *   port = 9001
 *
 * Example:
 *   java BulkCreateLoadTest 100000 500 localhost 9001
 */
public class BulkCreateLoadTest {

    private static final Faker faker = new Faker(Locale.US);

    // Phone types to randomly choose from
    private static final PhoneType[] PHONE_TYPES = {
            PhoneType.PHONE_HOME, PhoneType.PHONE_BUSINESS, PhoneType.PHONE_MAILING
    };

    // Email types to randomly choose from
    private static final EmailType[] EMAIL_TYPES = {
            EmailType.EMAIL_HOME, EmailType.EMAIL_BUSINESS, EmailType.EMAIL_MAILING
    };

    // Address types to randomly choose from
    private static final AddressType[] ADDRESS_TYPES = {
            AddressType.HOME, AddressType.BUSINESS, AddressType.MAILING
    };

    public static void main(String[] args) throws Exception {
        int totalContacts = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int batchSize = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        String host = args.length > 2 ? args[2] : "localhost";
        int port = args.length > 3 ? Integer.parseInt(args[3]) : 9001;

        System.out.println("=".repeat(70));
        System.out.println("Bulk Create Load Test");
        System.out.println("=".repeat(70));
        System.out.printf("Target: %s:%d%n", host, port);
        System.out.printf("Total Contacts: %,d%n", totalContacts);
        System.out.printf("Batch Size: %d%n", batchSize);
        System.out.printf("Number of Batches: %d%n", (totalContacts + batchSize - 1) / batchSize);
        System.out.println("=".repeat(70));

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(50 * 1024 * 1024) // 50MB for large responses
                .build();

        try {
            ContactServiceGrpc.ContactServiceBlockingStub stub = ContactServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.MINUTES);

            runBulkCreateTest(stub, totalContacts, batchSize);

        } finally {
            channel.shutdown();
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void runBulkCreateTest(ContactServiceGrpc.ContactServiceBlockingStub stub,
                                           int totalContacts, int batchSize) {
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFailure = new AtomicInteger(0);
        AtomicLong totalTimeMs = new AtomicLong(0);

        int batches = (totalContacts + batchSize - 1) / batchSize;
        int remaining = totalContacts;

        System.out.println("\nStarting bulk create...\n");
        long overallStart = System.currentTimeMillis();

        for (int batch = 0; batch < batches; batch++) {
            int currentBatchSize = Math.min(batchSize, remaining);
            remaining -= currentBatchSize;

            // Generate fake contacts for this batch
            List<ContactInput> contacts = generateFakeContacts(currentBatchSize);

            // Enable pending actions - Kafka should be available
            BulkCreateContactsRequest request = BulkCreateContactsRequest.newBuilder()
                    .addAllContacts(contacts)
                    .setSkipGenerateExternalIdentifiers(false)
                    .setSkipValidateAddress(false)
                    .build();

            long startTime = System.currentTimeMillis();
            try {
                BulkCreateContactsResponse response = stub.bulkCreateContacts(request);
                long elapsed = System.currentTimeMillis() - startTime;
                totalTimeMs.addAndGet(elapsed);

                totalSuccess.addAndGet(response.getSuccessCount());
                totalFailure.addAndGet(response.getFailureCount());

                double rate = currentBatchSize / (elapsed / 1000.0);
                System.out.printf("Batch %d/%d: %d contacts in %dms (%.1f/sec) - Success: %d, Failed: %d%n",
                        batch + 1, batches, currentBatchSize, elapsed, rate,
                        response.getSuccessCount(), response.getFailureCount());

                // Print first error if any failures
                if (response.getFailureCount() > 0) {
                    for (BulkCreateResult result : response.getResultsList()) {
                        if (!result.getSuccess()) {
                            System.out.printf("  First error at index %d: %s%n",
                                    result.getIndex(), result.getErrorMessage());
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                totalTimeMs.addAndGet(elapsed);
                totalFailure.addAndGet(currentBatchSize);
                System.out.printf("Batch %d/%d: FAILED in %dms - %s%n",
                        batch + 1, batches, elapsed, e.getMessage());
            }
        }

        long overallElapsed = System.currentTimeMillis() - overallStart;

        // Print summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Total Contacts Requested: %,d%n", totalContacts);
        System.out.printf("Successful Creates: %,d%n", totalSuccess.get());
        System.out.printf("Failed Creates: %,d%n", totalFailure.get());
        System.out.printf("Total Time: %.2f seconds%n", overallElapsed / 1000.0);
        System.out.printf("Overall Rate: %.1f contacts/second%n",
                totalContacts / (overallElapsed / 1000.0));
        System.out.printf("Average Batch Time: %.0f ms%n",
                totalTimeMs.get() / (double) batches);
        System.out.println("=".repeat(70));
    }

    private static List<ContactInput> generateFakeContacts(int count) {
        List<ContactInput> contacts = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ContactInput.Builder contactBuilder = ContactInput.newBuilder()
                    .setFirstName(faker.name().firstName())
                    .setLastName(faker.name().lastName());

            // Add 1-2 phone numbers (70% chance)
            if (faker.random().nextDouble() < 0.7) {
                int phoneCount = faker.random().nextInt(1, 3);
                for (int p = 0; p < phoneCount; p++) {
                    contactBuilder.addPhones(PhoneInput.newBuilder()
                            .setPhoneNumber(faker.phoneNumber().phoneNumber())
                            .setPhoneType(PHONE_TYPES[p % PHONE_TYPES.length])
                            .build());
                }
            }

            // Add 1-2 emails (80% chance)
            if (faker.random().nextDouble() < 0.8) {
                int emailCount = faker.random().nextInt(1, 3);
                for (int e = 0; e < emailCount; e++) {
                    contactBuilder.addEmails(EmailInput.newBuilder()
                            .setEmail(faker.internet().emailAddress())
                            .setEmailType(EMAIL_TYPES[e % EMAIL_TYPES.length])
                            .build());
                }
            }

            // Add 1-2 addresses (60% chance)
            if (faker.random().nextDouble() < 0.6) {
                int addressCount = faker.random().nextInt(1, 3);
                for (int a = 0; a < addressCount; a++) {
                    String zip = faker.address().zipCode();
                    // 50% chance of zip+4
                    if (faker.random().nextDouble() < 0.5) {
                        zip = zip + "-" + String.format("%04d", faker.random().nextInt(0, 10000));
                    }

                    AddressInput.Builder addressBuilder = AddressInput.newBuilder()
                            .setAddressType(ADDRESS_TYPES[a % ADDRESS_TYPES.length])
                            .addAddressLines(faker.address().streetAddress())
                            .setLocality(faker.address().city())
                            .setAdministrativeArea(faker.address().stateAbbr())
                            .setPostalCode(zip)
                            .setCountryCode("US");

                    // 30% chance of secondary address (apt, suite, etc)
                    if (faker.random().nextDouble() < 0.3) {
                        addressBuilder.addAddressLines(faker.address().secondaryAddress());
                    }

                    contactBuilder.addAddresses(addressBuilder.build());
                }
            }

            contacts.add(contactBuilder.build());
        }

        return contacts;
    }
}
