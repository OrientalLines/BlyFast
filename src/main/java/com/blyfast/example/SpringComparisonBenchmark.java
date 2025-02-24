package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.core.ThreadPool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark application to compare BlyFast performance with Spring Boot.
 * 
 * To use this benchmark:
 * 1. Start the BlyFast server using the main method in this class
 * 2. Start a Spring Boot application (see instructions in comments below)
 * 3. Run the benchmark methods to compare performance
 */
public class SpringComparisonBenchmark {

    // Configuration
    private static final int WARMUP_REQUESTS = 1000;
    private static final int BENCHMARK_REQUESTS = 10000;
    private static final int CONCURRENT_USERS = 100;
    private static final int ITERATIONS = 5;

    // BlyFast server port
    private static final int BLYFAST_PORT = 8080;

    // Spring server port (default Spring Boot port)
    private static final int SPRING_PORT = 8081;

    // HTTP client for making requests
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) throws Exception {
        // Start BlyFast server
        System.out.println("Starting BlyFast server for benchmark comparison...");

        // Create optimized thread pool configuration
        ThreadPool.ThreadPoolConfig threadPoolConfig = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4)
                .setQueueCapacity(10000)
                .setPrestartCoreThreads(true)
                .setUseSynchronousQueue(false);

        // Create and configure BlyFast application
        Blyfast app = new Blyfast(threadPoolConfig);

        // Add benchmark endpoints

        // Simple JSON response endpoint
        app.get("/api/hello", ctx -> {
            ctx.json("{ \"message\": \"Hello, World!\" }");
        });

        // Computation endpoint
        app.get("/api/compute", ctx -> {
            // Simulate some CPU-bound work
            long result = 0;
            for (int i = 0; i < 1000; i++) {
                result += i;
            }
            ctx.json("{ \"result\": " + result + " }");
        });

        // Path parameter endpoint
        app.get("/api/users/:id", ctx -> {
            String id = ctx.param("id");
            ctx.json("{ \"id\": \"" + id + "\", \"name\": \"User " + id + "\" }");
        });

        // Start the server
        app.port(BLYFAST_PORT).listen();

        System.out.println("BlyFast server started on http://localhost:" + BLYFAST_PORT);
        System.out.println("\nTo compare with Spring Boot:");
        System.out.println("1. Create a Spring Boot application with equivalent endpoints");
        System.out.println("2. Run it on port " + SPRING_PORT);
        System.out.println("3. Run the benchmark methods in this class");
        System.out.println("\nExample Spring Boot controller:");
        System.out.println("---------------------------");
        System.out.println("@RestController");
        System.out.println("public class BenchmarkController {");
        System.out.println("    @GetMapping(\"/api/hello\")");
        System.out.println("    public Map<String, String> hello() {");
        System.out.println("        return Map.of(\"message\", \"Hello, World!\");");
        System.out.println("    }");
        System.out.println("    ");
        System.out.println("    @GetMapping(\"/api/compute\")");
        System.out.println("    public Map<String, Long> compute() {");
        System.out.println("        long result = 0;");
        System.out.println("        for (int i = 0; i < 1000; i++) {");
        System.out.println("            result += i;");
        System.out.println("        }");
        System.out.println("        return Map.of(\"result\", result);");
        System.out.println("    }");
        System.out.println("    ");
        System.out.println("    @GetMapping(\"/api/users/{id}\")");
        System.out.println("    public Map<String, String> getUser(@PathVariable String id) {");
        System.out.println("        return Map.of(\"id\", id, \"name\", \"User \" + id);");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("---------------------------");

        // Run the benchmarks if both servers are running
        if (isServerRunning("http://localhost:" + BLYFAST_PORT + "/api/hello") &&
                isServerRunning("http://localhost:" + SPRING_PORT + "/api/hello")) {

            System.out.println("\nBoth servers are running. Starting benchmarks...\n");

            // Run benchmarks for each endpoint
            runComparison("/api/hello", "Simple JSON Response");
            runComparison("/api/compute", "Computation");
            runComparison("/api/users/123", "Path Parameter");

            System.out.println("\nBenchmarks completed.");
        } else {
            System.out.println("\nWaiting for both servers to be running...");
            System.out.println("Press Ctrl+C to stop the BlyFast server");

            // Keep the server running
            Thread.currentThread().join();
        }
    }

    /**
     * Runs a comparison benchmark between BlyFast and Spring Boot for a specific
     * endpoint.
     * 
     * @param endpoint    the endpoint to benchmark
     * @param description a description of the benchmark
     * @throws Exception if an error occurs
     */
    private static void runComparison(String endpoint, String description) throws Exception {
        System.out.println("=== Benchmark: " + description + " ===");

        // Warm up both servers
        System.out.println("Warming up servers...");
        warmup("http://localhost:" + BLYFAST_PORT + endpoint);
        warmup("http://localhost:" + SPRING_PORT + endpoint);

        // Run the benchmarks
        System.out.println("Running benchmarks...");

        List<BenchmarkResult> blyfastResults = new ArrayList<>();
        List<BenchmarkResult> springResults = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            System.out.println("Iteration " + (i + 1) + "/" + ITERATIONS);

            // Benchmark BlyFast
            BenchmarkResult blyfastResult = benchmark("http://localhost:" + BLYFAST_PORT + endpoint);
            blyfastResults.add(blyfastResult);

            // Benchmark Spring
            BenchmarkResult springResult = benchmark("http://localhost:" + SPRING_PORT + endpoint);
            springResults.add(springResult);

            // Short pause between iterations
            Thread.sleep(1000);
        }

        // Calculate and print results
        BenchmarkResult blyfastAvg = calculateAverage(blyfastResults);
        BenchmarkResult springAvg = calculateAverage(springResults);

        System.out.println("\nResults for " + description + ":");
        System.out.println("BlyFast:");
        System.out.println("  Requests/sec: " + String.format("%.2f", blyfastAvg.requestsPerSecond));
        System.out.println("  Avg response time: " + String.format("%.2f", blyfastAvg.avgResponseTime) + " ms");
        System.out.println("  Success rate: " + String.format("%.2f", blyfastAvg.successRate * 100) + "%");

        System.out.println("Spring Boot:");
        System.out.println("  Requests/sec: " + String.format("%.2f", springAvg.requestsPerSecond));
        System.out.println("  Avg response time: " + String.format("%.2f", springAvg.avgResponseTime) + " ms");
        System.out.println("  Success rate: " + String.format("%.2f", springAvg.successRate * 100) + "%");

        // Calculate performance difference
        double throughputDiff = (blyfastAvg.requestsPerSecond / springAvg.requestsPerSecond - 1) * 100;
        double latencyDiff = (1 - blyfastAvg.avgResponseTime / springAvg.avgResponseTime) * 100;

        System.out.println("\nPerformance comparison:");
        System.out.println("  BlyFast is " + String.format("%.2f", throughputDiff) + "% faster in throughput");
        System.out.println("  BlyFast is " + String.format("%.2f", latencyDiff) + "% faster in response time");

        System.out.println();
    }

    /**
     * Warms up a server by sending a number of requests.
     * 
     * @param url the URL to send requests to
     * @throws Exception if an error occurs
     */
    private static void warmup(String url) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(WARMUP_REQUESTS);

        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            executor.execute(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    // Ignore exceptions during warmup
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    /**
     * Benchmarks a server by sending a number of concurrent requests.
     * 
     * @param url the URL to send requests to
     * @return the benchmark result
     * @throws Exception if an error occurs
     */
    private static BenchmarkResult benchmark(String url) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(BENCHMARK_REQUESTS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_REQUESTS; i++) {
            executor.execute(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

                    long requestStart = System.nanoTime();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    long requestEnd = System.nanoTime();

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                        totalResponseTime.addAndGet(requestEnd - requestStart);
                    }
                } catch (Exception e) {
                    // Count as failure
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

        // Calculate results
        double requestsPerSecond = BENCHMARK_REQUESTS / elapsedSeconds;
        double successRate = (double) successCount.get() / BENCHMARK_REQUESTS;
        double avgResponseTime = successCount.get() > 0
                ? (totalResponseTime.get() / successCount.get()) / 1_000_000.0
                : 0;

        return new BenchmarkResult(requestsPerSecond, avgResponseTime, successRate);
    }

    /**
     * Calculates the average of multiple benchmark results.
     * 
     * @param results the results to average
     * @return the average result
     */
    private static BenchmarkResult calculateAverage(List<BenchmarkResult> results) {
        double totalRequestsPerSecond = 0;
        double totalAvgResponseTime = 0;
        double totalSuccessRate = 0;

        for (BenchmarkResult result : results) {
            totalRequestsPerSecond += result.requestsPerSecond;
            totalAvgResponseTime += result.avgResponseTime;
            totalSuccessRate += result.successRate;
        }

        int count = results.size();
        return new BenchmarkResult(
                totalRequestsPerSecond / count,
                totalAvgResponseTime / count,
                totalSuccessRate / count);
    }

    /**
     * Checks if a server is running by sending a request to it.
     * 
     * @param url the URL to check
     * @return true if the server is running
     */
    private static boolean isServerRunning(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Class to hold benchmark results.
     */
    private static class BenchmarkResult {
        final double requestsPerSecond;
        final double avgResponseTime;
        final double successRate;

        BenchmarkResult(double requestsPerSecond, double avgResponseTime, double successRate) {
            this.requestsPerSecond = requestsPerSecond;
            this.avgResponseTime = avgResponseTime;
            this.successRate = successRate;
        }
    }
}