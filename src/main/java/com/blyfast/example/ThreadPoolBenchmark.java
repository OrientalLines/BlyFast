package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.core.ThreadPool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark application to demonstrate the performance of the ThreadPool implementation.
 */
public class ThreadPoolBenchmark {
    
    // Number of concurrent requests to simulate
    private static final int CONCURRENT_REQUESTS = 10000;
    
    // Number of iterations for each request
    private static final int ITERATIONS_PER_REQUEST = 1000;
    
    public static void main(String[] args) throws Exception {
        // Create different thread pool configurations for comparison
        ThreadPool.ThreadPoolConfig defaultConfig = new ThreadPool.ThreadPoolConfig();
        
        ThreadPool.ThreadPoolConfig optimizedConfig = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4)
                .setQueueCapacity(CONCURRENT_REQUESTS)
                .setUseSynchronousQueue(false)
                .setUseWorkStealing(false)
                .setPrestartCoreThreads(true)
                .setCollectMetrics(true);
        
        ThreadPool.ThreadPoolConfig workStealingConfig = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .setUseWorkStealing(true)
                .setCollectMetrics(true);
        
        // Run benchmarks
        System.out.println("Running thread pool benchmarks...");
        System.out.println("Concurrent requests: " + CONCURRENT_REQUESTS);
        System.out.println("Iterations per request: " + ITERATIONS_PER_REQUEST);
        System.out.println();
        
        runBenchmark("Default Configuration", defaultConfig);
        runBenchmark("Optimized Configuration", optimizedConfig);
        runBenchmark("Work-Stealing Configuration", workStealingConfig);
        
        // Create a BlyFast application with the optimized thread pool
        System.out.println("\nStarting BlyFast server with optimized thread pool...");
        Blyfast app = new Blyfast(optimizedConfig);
        
        // Add a simple route for benchmarking
        app.get("/benchmark", ctx -> {
            // Simulate some CPU-bound work
            long result = 0;
            for (int i = 0; i < 1000; i++) {
                result += i;
            }
            ctx.json("{ \"result\": " + result + " }");
        });
        
        // Start the server
        app.port(8080).listen();
        
        System.out.println("Server started on http://localhost:8080");
        System.out.println("Run a load test against /benchmark endpoint to see the thread pool in action");
        System.out.println("Press Ctrl+C to stop the server");
        
        // Keep the server running
        Thread.currentThread().join();
    }
    
    /**
     * Runs a benchmark with the specified thread pool configuration.
     * 
     * @param name the name of the benchmark
     * @param config the thread pool configuration
     * @throws Exception if an error occurs
     */
    private static void runBenchmark(String name, ThreadPool.ThreadPoolConfig config) throws Exception {
        System.out.println("=== " + name + " ===");
        
        // Create the thread pool
        ThreadPool threadPool = new ThreadPool(config);
        
        // Create a latch to wait for all tasks to complete
        CountDownLatch latch = new CountDownLatch(CONCURRENT_REQUESTS);
        
        // Track successful and failed tasks
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Start timing
        long startTime = System.nanoTime();
        
        // Submit tasks
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final int taskId = i;
            threadPool.execute(() -> {
                try {
                    // Simulate a request with some CPU-bound work
                    for (int j = 0; j < ITERATIONS_PER_REQUEST; j++) {
                        // Perform some calculation to simulate CPU work
                        @SuppressWarnings("unused")
                        long calculation = (taskId * j) % 100;
                    }

                    // Task completed successfully
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Task failed
                    failureCount.incrementAndGet();
                } finally {
                    // Signal that the task is complete
                    latch.countDown();
                }
            });
        }
        
        // Wait for all tasks to complete
        latch.await(30, TimeUnit.SECONDS);
        
        // Calculate elapsed time
        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        
        // Print results
        System.out.println("Completed in: " + elapsedSeconds + " seconds");
        System.out.println("Throughput: " + (CONCURRENT_REQUESTS / elapsedSeconds) + " requests/second");
        System.out.println("Successful tasks: " + successCount.get());
        System.out.println("Failed tasks: " + failureCount.get());
        
        if (config.isCollectMetrics()) {
            System.out.println("Average task execution time: " + 
                    (threadPool.getAverageExecutionTime() / 1_000_000.0) + " ms");
        }
        
        System.out.println("Thread pool stats:");
        System.out.println("  Pool size: " + threadPool.getPoolSize());
        System.out.println("  Active threads: " + threadPool.getActiveCount());
        System.out.println("  Queue size: " + threadPool.getQueueSize());
        System.out.println("  Tasks submitted: " + threadPool.getTasksSubmitted());
        System.out.println("  Tasks completed: " + threadPool.getTasksCompleted());
        System.out.println("  Tasks rejected: " + threadPool.getTasksRejected());
        
        // Shutdown the thread pool
        threadPool.shutdown();
        threadPool.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println();
    }
} 