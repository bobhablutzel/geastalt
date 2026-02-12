/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact;

import com.geastalt.contact.grpc.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latency diagnostic tool for contact search.
 *
 * Compares latency across four layers to isolate where time is spent:
 *   Layer 1: Raw JDBC        – dedicated connections, raw PreparedStatement (baseline)
 *   Layer 2: HikariCP JDBC   – pooled connections, raw PreparedStatement (pool overhead)
 *   Layer 3: HTTP/REST       – Spring MVC + JdbcTemplate via port-forward
 *   Layer 4: gRPC            – gRPC + Netty + protobuf via port-forward
 *
 * Usage:
 *   mvn compile test-compile exec:java \
 *       -Dexec.mainClass="com.geastalt.contact.LatencyDiagnosticTest" \
 *       -Dexec.classpathScope=test \
 *       -Ddb.pass=YOUR_PASSWORD
 */
public class LatencyDiagnosticTest {

    private static final String GRPC_HOST = System.getProperty("grpc.host", "localhost");
    private static final int    GRPC_PORT = Integer.getInteger("grpc.port", 9001);
    private static final String HTTP_HOST = System.getProperty("http.host", "localhost");
    private static final int    HTTP_PORT = Integer.getInteger("http.port", 9002);
    private static final String DB_HOST   = System.getProperty("db.host", "192.168.1.17");
    private static final int    DB_PORT   = Integer.getInteger("db.port", 5432);
    private static final String DB_NAME   = System.getProperty("db.name", "contact");
    private static final String DB_USER   = System.getProperty("db.user", "bob");
    private static final String DB_PASS   = System.getProperty("db.pass", "");

    private static final int NAME_SAMPLE_SIZE  = 5000;
    private static final int CONCURRENCY       = 50;
    private static final long PHASE_DURATION_MS = 60_000;

    private static final String DB_URL = String.format("jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, DB_NAME);

    // Use the same SQL the service uses (branching variant for firstName)
    private static final String SQL_EXACT_BOTH = """
            SELECT id, first_name, last_name, email FROM contacts
            WHERE last_name_lower = ? AND first_name_lower = ?
            LIMIT 25
            """;
    private static final String SQL_EXACT_LAST = """
            SELECT id, first_name, last_name, email FROM contacts
            WHERE last_name_lower = ?
            LIMIT 25
            """;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Contact Search Latency Diagnostic ===");
        System.out.printf("  gRPC target : %s:%d%n", GRPC_HOST, GRPC_PORT);
        System.out.printf("  HTTP target : %s:%d%n", HTTP_HOST, HTTP_PORT);
        System.out.printf("  DB target   : %s:%d/%s%n", DB_HOST, DB_PORT, DB_NAME);
        System.out.printf("  Concurrency : %d threads%n", CONCURRENCY);
        System.out.printf("  Phase length: %d s%n", PHASE_DURATION_MS / 1000);
        System.out.println();

        List<String[]> names = fetchRandomNames();
        System.out.printf("Loaded %,d random name pairs from database%n%n", names.size());

        // =============================================================
        // Layer 1: Raw JDBC (dedicated connections)
        // =============================================================
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Layer 1: Raw JDBC (dedicated connections)      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        Connection[] jdbcConns = new Connection[CONCURRENCY];
        for (int i = 0; i < CONCURRENCY; i++) {
            jdbcConns[i] = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }

        System.out.println("--- Warmup (1 minute) ---");
        runJdbcDedicatedPhase(jdbcConns, names, PHASE_DURATION_MS);

        System.out.println("--- Measurement (1 minute) ---");
        PhaseResult jdbcMeasure = runJdbcDedicatedPhase(jdbcConns, names, PHASE_DURATION_MS);
        printSummary(jdbcMeasure);
        printHistogram(jdbcMeasure);

        for (Connection c : jdbcConns) c.close();

        // =============================================================
        // Layer 2: HikariCP JDBC (pooled connections)
        // =============================================================
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Layer 2: HikariCP JDBC (pooled connections)    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(DB_URL);
        hikariConfig.setUsername(DB_USER);
        hikariConfig.setPassword(DB_PASS);
        hikariConfig.setMaximumPoolSize(CONCURRENCY);
        hikariConfig.setMinimumIdle(CONCURRENCY);
        hikariConfig.setConnectionTimeout(5000);
        hikariConfig.setAutoCommit(true);
        HikariDataSource hikariDs = new HikariDataSource(hikariConfig);

        System.out.println("--- Warmup (1 minute) ---");
        runJdbcPooledPhase(hikariDs, names, PHASE_DURATION_MS);

        System.out.println("--- Measurement (1 minute) ---");
        PhaseResult hikariMeasure = runJdbcPooledPhase(hikariDs, names, PHASE_DURATION_MS);
        printSummary(hikariMeasure);
        printHistogram(hikariMeasure);

        hikariDs.close();

        // =============================================================
        // Layer 3: HTTP/REST (Spring MVC via port-forward)
        // =============================================================
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Layer 3: HTTP/REST (Spring MVC)                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        System.out.println("--- Warmup (1 minute) ---");
        runHttpPhase(names, PHASE_DURATION_MS);

        System.out.println("--- Measurement (1 minute) ---");
        PhaseResult httpMeasure = runHttpPhase(names, PHASE_DURATION_MS);
        printSummary(httpMeasure);
        printHistogram(httpMeasure);

        // =============================================================
        // Layer 4: gRPC (full service stack via port-forward)
        // =============================================================
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Layer 4: gRPC (full service stack)             ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        List<ManagedChannel> channels = new ArrayList<>();
        List<ContactServiceGrpc.ContactServiceBlockingStub> stubs = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            ManagedChannel ch = ManagedChannelBuilder.forAddress(GRPC_HOST, GRPC_PORT)
                    .usePlaintext()
                    .build();
            channels.add(ch);
            stubs.add(ContactServiceGrpc.newBlockingStub(ch));
        }

        System.out.println("--- Warmup (1 minute) ---");
        runGrpcPhase(stubs, names, PHASE_DURATION_MS);

        System.out.println("--- Measurement (1 minute) ---");
        PhaseResult grpcMeasure = runGrpcPhase(stubs, names, PHASE_DURATION_MS);
        printSummary(grpcMeasure);
        printHistogram(grpcMeasure);

        for (ManagedChannel ch : channels) ch.shutdown();

        // =============================================================
        // Comparison
        // =============================================================
        printComparison(jdbcMeasure, hikariMeasure, httpMeasure, grpcMeasure);

        System.out.println("Done.");
    }

    // ---------------------------------------------------------------
    // Fetch random names
    // ---------------------------------------------------------------

    private static List<String[]> fetchRandomNames() throws Exception {
        System.out.printf("Connecting to %s as %s ...%n", DB_URL, DB_USER);
        List<String[]> names = new ArrayList<>(NAME_SAMPLE_SIZE);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = """
                    SELECT DISTINCT last_name, first_name
                    FROM contacts TABLESAMPLE BERNOULLI(1)
                    WHERE last_name IS NOT NULL
                    LIMIT ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, NAME_SAMPLE_SIZE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
            }
            if (names.size() < NAME_SAMPLE_SIZE) {
                System.out.printf("  TABLESAMPLE returned %d rows, falling back to ORDER BY random()%n", names.size());
                names.clear();
                String fallback = """
                        SELECT DISTINCT last_name, first_name
                        FROM contacts WHERE last_name IS NOT NULL
                        ORDER BY random() LIMIT ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(fallback)) {
                    ps.setInt(1, NAME_SAMPLE_SIZE);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            names.add(new String[]{rs.getString(1), rs.getString(2)});
                        }
                    }
                }
            }
        }
        return names;
    }

    // ---------------------------------------------------------------
    // Layer 1: Raw JDBC with dedicated connections
    // ---------------------------------------------------------------

    private static PhaseResult runJdbcDedicatedPhase(
            Connection[] connections, List<String[]> names, long durationMs) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong totalNs = new AtomicLong();
        long deadline = System.currentTimeMillis() + durationMs;

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < CONCURRENCY; t++) {
            final int idx = t;
            futures.add(executor.submit(() -> {
                Random rng = ThreadLocalRandom.current();
                try {
                    // Pre-prepare both variants
                    PreparedStatement psBoth = connections[idx].prepareStatement(SQL_EXACT_BOTH);
                    PreparedStatement psLast = connections[idx].prepareStatement(SQL_EXACT_LAST);

                    while (System.currentTimeMillis() < deadline) {
                        String[] name = names.get(rng.nextInt(names.size()));
                        String lastName = name[0].toLowerCase();
                        String firstName = (name[1] != null && !name[1].isEmpty())
                                ? name[1].toLowerCase() : null;
                        try {
                            long start = System.nanoTime();
                            PreparedStatement ps;
                            if (firstName != null) {
                                psBoth.setString(1, lastName);
                                psBoth.setString(2, firstName);
                                ps = psBoth;
                            } else {
                                psLast.setString(1, lastName);
                                ps = psLast;
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    rs.getLong("id");
                                    rs.getString("first_name");
                                    rs.getString("last_name");
                                    rs.getString("email");
                                }
                            }
                            long elapsed = System.nanoTime() - start;
                            latencies.add(elapsed);
                            totalNs.addAndGet(elapsed);
                            completed.incrementAndGet();
                        } catch (Exception e) { errors.incrementAndGet(); }
                    }
                    psBoth.close();
                    psLast.close();
                } catch (Exception e) { System.err.println("JDBC dedicated error: " + e.getMessage()); }
            }));
        }
        for (Future<?> f : futures) { try { f.get(); } catch (ExecutionException ignored) {} }
        executor.shutdown();
        return new PhaseResult(latencies, completed.get(), errors.get(), totalNs.get());
    }

    // ---------------------------------------------------------------
    // Layer 2: HikariCP pooled JDBC
    // ---------------------------------------------------------------

    private static PhaseResult runJdbcPooledPhase(
            HikariDataSource ds, List<String[]> names, long durationMs) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong totalNs = new AtomicLong();
        long deadline = System.currentTimeMillis() + durationMs;

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < CONCURRENCY; t++) {
            futures.add(executor.submit(() -> {
                Random rng = ThreadLocalRandom.current();
                while (System.currentTimeMillis() < deadline) {
                    String[] name = names.get(rng.nextInt(names.size()));
                    String lastName = name[0].toLowerCase();
                    String firstName = (name[1] != null && !name[1].isEmpty())
                            ? name[1].toLowerCase() : null;
                    try {
                        long start = System.nanoTime();

                        try (Connection conn = ds.getConnection()) {
                            String sql = firstName != null ? SQL_EXACT_BOTH : SQL_EXACT_LAST;
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                ps.setString(1, lastName);
                                if (firstName != null) ps.setString(2, firstName);
                                try (ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) {
                                        rs.getLong("id");
                                        rs.getString("first_name");
                                        rs.getString("last_name");
                                        rs.getString("email");
                                    }
                                }
                            }
                        }

                        long elapsed = System.nanoTime() - start;
                        latencies.add(elapsed);
                        totalNs.addAndGet(elapsed);
                        completed.incrementAndGet();
                    } catch (Exception e) { errors.incrementAndGet(); }
                }
            }));
        }
        for (Future<?> f : futures) { try { f.get(); } catch (ExecutionException ignored) {} }
        executor.shutdown();
        return new PhaseResult(latencies, completed.get(), errors.get(), totalNs.get());
    }

    // ---------------------------------------------------------------
    // Layer 3: HTTP/REST
    // ---------------------------------------------------------------

    private static PhaseResult runHttpPhase(List<String[]> names, long durationMs) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong totalNs = new AtomicLong();
        long deadline = System.currentTimeMillis() + durationMs;
        String baseUrl = String.format("http://%s:%d/api/contacts/search", HTTP_HOST, HTTP_PORT);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < CONCURRENCY; t++) {
            futures.add(executor.submit(() -> {
                Random rng = ThreadLocalRandom.current();
                while (System.currentTimeMillis() < deadline) {
                    String[] name = names.get(rng.nextInt(names.size()));
                    try {
                        long start = System.nanoTime();
                        StringBuilder urlStr = new StringBuilder(baseUrl)
                                .append("?lastName=").append(URLEncoder.encode(name[0], StandardCharsets.UTF_8))
                                .append("&maxResults=25");
                        if (name[1] != null && !name[1].isEmpty()) {
                            urlStr.append("&firstName=").append(URLEncoder.encode(name[1], StandardCharsets.UTF_8));
                        }
                        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr.toString()).openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        int status = conn.getResponseCode();
                        if (status == 200) {
                            try (InputStream is = conn.getInputStream()) { is.readAllBytes(); }
                            long elapsed = System.nanoTime() - start;
                            latencies.add(elapsed);
                            totalNs.addAndGet(elapsed);
                            completed.incrementAndGet();
                        } else { errors.incrementAndGet(); }
                        conn.disconnect();
                    } catch (Exception e) { errors.incrementAndGet(); }
                }
            }));
        }
        for (Future<?> f : futures) { try { f.get(); } catch (ExecutionException ignored) {} }
        executor.shutdown();
        return new PhaseResult(latencies, completed.get(), errors.get(), totalNs.get());
    }

    // ---------------------------------------------------------------
    // Layer 4: gRPC
    // ---------------------------------------------------------------

    private static PhaseResult runGrpcPhase(
            List<ContactServiceGrpc.ContactServiceBlockingStub> stubs,
            List<String[]> names, long durationMs) throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong totalNs = new AtomicLong();
        long deadline = System.currentTimeMillis() + durationMs;

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < CONCURRENCY; t++) {
            final int idx = t;
            futures.add(executor.submit(() -> {
                Random rng = ThreadLocalRandom.current();
                ContactServiceGrpc.ContactServiceBlockingStub stub = stubs.get(idx);
                while (System.currentTimeMillis() < deadline) {
                    String[] name = names.get(rng.nextInt(names.size()));
                    try {
                        long start = System.nanoTime();
                        SearchContactsRequest.Builder req = SearchContactsRequest.newBuilder()
                                .setLastName(name[0]).setMaxResults(25).setIncludeTotalCount(false);
                        if (name[1] != null && !name[1].isEmpty()) req.setFirstName(name[1]);
                        stub.searchContacts(req.build());
                        long elapsed = System.nanoTime() - start;
                        latencies.add(elapsed);
                        totalNs.addAndGet(elapsed);
                        completed.incrementAndGet();
                    } catch (Exception e) { errors.incrementAndGet(); }
                }
            }));
        }
        for (Future<?> f : futures) { try { f.get(); } catch (ExecutionException ignored) {} }
        executor.shutdown();
        return new PhaseResult(latencies, completed.get(), errors.get(), totalNs.get());
    }

    // ---------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------

    private static void printSummary(PhaseResult result) {
        if (result.completed == 0) { System.out.println("  No requests completed."); return; }
        long[] sorted = result.sortedLatenciesNs();
        double avgMs = (result.totalLatencyNs / (double) result.completed) / 1_000_000.0;
        double tps = result.completed / (PHASE_DURATION_MS / 1000.0);
        System.out.printf("  Completed : %,d requests%n", result.completed);
        System.out.printf("  Errors    : %,d%n", result.errors);
        System.out.printf("  Throughput: %,.1f req/s%n", tps);
        System.out.println();
        System.out.printf("  Min       : %,.3f ms%n", sorted[0] / 1_000_000.0);
        System.out.printf("  p50       : %,.3f ms%n", percentile(sorted, 50) / 1_000_000.0);
        System.out.printf("  p90       : %,.3f ms%n", percentile(sorted, 90) / 1_000_000.0);
        System.out.printf("  p95       : %,.3f ms%n", percentile(sorted, 95) / 1_000_000.0);
        System.out.printf("  p99       : %,.3f ms%n", percentile(sorted, 99) / 1_000_000.0);
        System.out.printf("  Max       : %,.3f ms%n", sorted[sorted.length - 1] / 1_000_000.0);
        System.out.printf("  Avg       : %,.3f ms%n", avgMs);
        System.out.println();
    }

    private static void printHistogram(PhaseResult result) {
        if (result.completed == 0) return;
        long[] sorted = result.sortedLatenciesNs();
        double[] buckets = {0.5, 1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000};
        int[] counts = new int[buckets.length + 1];
        for (long ns : sorted) {
            double ms = ns / 1_000_000.0;
            boolean placed = false;
            for (int b = 0; b < buckets.length; b++) {
                if (ms <= buckets[b]) { counts[b]++; placed = true; break; }
            }
            if (!placed) counts[buckets.length]++;
        }
        System.out.println("  Latency histogram:");
        int total = result.completed;
        String prevLabel = "0";
        for (int b = 0; b < buckets.length; b++) {
            if (counts[b] > 0) {
                double pct = counts[b] * 100.0 / total;
                String bar = "█".repeat(Math.max(1, (int)(pct / 2)));
                System.out.printf("  %6s - %6s ms : %,8d (%5.1f%%) %s%n",
                        prevLabel, formatBucket(buckets[b]), counts[b], pct, bar);
            }
            prevLabel = formatBucket(buckets[b]);
        }
        if (counts[buckets.length] > 0) {
            double pct = counts[buckets.length] * 100.0 / total;
            String bar = "█".repeat(Math.max(1, (int)(pct / 2)));
            System.out.printf("  %6s+          ms : %,8d (%5.1f%%) %s%n",
                    prevLabel, counts[buckets.length], pct, bar);
        }
        System.out.println();
    }

    private static void printComparison(PhaseResult jdbc, PhaseResult hikari, PhaseResult http, PhaseResult grpc) {
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  Layer Comparison (measurement phases)                                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");

        if (jdbc.completed == 0 || hikari.completed == 0 || http.completed == 0 || grpc.completed == 0) {
            System.out.println("║  Insufficient data                                                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");
            return;
        }

        long[] js = jdbc.sortedLatenciesNs(), hs = hikari.sortedLatenciesNs();
        long[] ts = http.sortedLatenciesNs(), gs = grpc.sortedLatenciesNs();

        double jp50 = p(js,50), hp50 = p(hs,50), tp50 = p(ts,50), gp50 = p(gs,50);
        double jp95 = p(js,95), hp95 = p(hs,95), tp95 = p(ts,95), gp95 = p(gs,95);
        double jp99 = p(js,99), hp99 = p(hs,99), tp99 = p(ts,99), gp99 = p(gs,99);
        double ja = avg(jdbc), ha = avg(hikari), ta = avg(http), ga = avg(grpc);
        double jt = tps(jdbc), ht = tps(hikari), tt = tps(http), gt = tps(grpc);

        System.out.printf("║  %-12s %11s %11s %11s %11s              ║%n",
                "", "Raw JDBC", "HikariCP", "HTTP/REST", "gRPC");
        System.out.printf("║  %-12s %9.3f ms %9.3f ms %9.3f ms %9.3f ms              ║%n", "Avg", ja, ha, ta, ga);
        System.out.printf("║  %-12s %9.3f ms %9.3f ms %9.3f ms %9.3f ms              ║%n", "p50", jp50, hp50, tp50, gp50);
        System.out.printf("║  %-12s %9.3f ms %9.3f ms %9.3f ms %9.3f ms              ║%n", "p95", jp95, hp95, tp95, gp95);
        System.out.printf("║  %-12s %9.3f ms %9.3f ms %9.3f ms %9.3f ms              ║%n", "p99", jp99, hp99, tp99, gp99);
        System.out.printf("║  %-12s %8.1f/s  %8.1f/s  %8.1f/s  %8.1f/s               ║%n", "Throughput", jt, ht, tt, gt);
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Overhead breakdown at p50:                                                   ║");
        System.out.printf("║    HikariCP pool checkout/return  (Hikari - JDBC):       %8.3f ms           ║%n", hp50 - jp50);
        System.out.printf("║    Spring + port-fwd + JSON serde (HTTP - Hikari):       %8.3f ms           ║%n", tp50 - hp50);
        System.out.printf("║    gRPC vs HTTP                   (gRPC - HTTP):         %8.3f ms           ║%n", gp50 - tp50);
        System.out.printf("║    Total service overhead         (gRPC - JDBC):         %8.3f ms           ║%n", gp50 - jp50);
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static double p(long[] sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, idx)] / 1_000_000.0;
    }
    private static double avg(PhaseResult r) {
        return (r.totalLatencyNs / (double) r.completed) / 1_000_000.0;
    }
    private static double tps(PhaseResult r) {
        return r.completed / (PHASE_DURATION_MS / 1000.0);
    }
    private static long percentile(long[] sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, idx)];
    }
    private static String formatBucket(double ms) {
        if (ms >= 1 && ms == (int) ms) return String.valueOf((int) ms);
        return String.format("%.1f", ms);
    }

    private record PhaseResult(
            ConcurrentLinkedQueue<Long> rawLatencies,
            int completed, int errors, long totalLatencyNs) {
        long[] sortedLatenciesNs() {
            long[] arr = rawLatencies.stream().mapToLong(Long::longValue).toArray();
            Arrays.sort(arr);
            return arr;
        }
    }
}
