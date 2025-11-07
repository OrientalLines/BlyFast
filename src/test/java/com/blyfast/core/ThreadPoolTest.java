package com.blyfast.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the ThreadPool implementation.
 * 
 * <p>These tests verify thread pool configuration, task execution,
 * concurrency handling, shutdown behavior, and work-stealing capabilities.</p>
 */
@DisplayName("ThreadPool Implementation Tests")
public class ThreadPoolTest {
    
    // Test configuration constants
    private static final int CUSTOM_CORE_POOL_SIZE = 4;
    private static final int CUSTOM_MAX_POOL_SIZE = 8;
    private static final int CUSTOM_QUEUE_CAPACITY = 100;
    private static final Duration CUSTOM_KEEP_ALIVE_TIME = Duration.ofSeconds(30);
    private static final int TASK_COUNT_SMALL = 10;
    private static final int TASK_COUNT_MEDIUM = 100;
    private static final int TASK_EXECUTION_DELAY_MS = 100;
    private static final int SHORT_TIMEOUT_SECONDS = 5;
    private static final int MEDIUM_TIMEOUT_SECONDS = 10;
    
    private ThreadPool threadPool;
    
    @BeforeEach
    void setUp() {
        // Each test gets a fresh thread pool instance
        threadPool = null;
    }
    
    @AfterEach
    void tearDown() {
        // Clean up thread pool after each test
        if (threadPool != null) {
            try {
                threadPool.shutdown();
                threadPool.awaitTermination(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                threadPool.shutdownNow();
            }
        }
    }
    
    /**
     * Helper method to create a default thread pool.
     */
    private ThreadPool createDefaultThreadPool() {
        threadPool = new ThreadPool();
        return threadPool;
    }
    
    /**
     * Helper method to create a thread pool with custom configuration.
     */
    private ThreadPool createCustomThreadPool(ThreadPool.ThreadPoolConfig config) {
        threadPool = new ThreadPool(config);
        return threadPool;
    }
    
    /**
     * Helper method to wait for a latch with timeout.
     */
    private void awaitLatch(CountDownLatch latch, int timeoutSeconds) throws InterruptedException {
        assertTrue(latch.await(timeoutSeconds, TimeUnit.SECONDS),
                "Latch should complete within timeout");
    }
    
    @Test
    @DisplayName("Should create thread pool with custom configuration")
    void testCustomConfiguration() {
        // Given: custom thread pool configuration
        ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(CUSTOM_CORE_POOL_SIZE)
                .setMaxPoolSize(CUSTOM_MAX_POOL_SIZE)
                .setQueueCapacity(CUSTOM_QUEUE_CAPACITY)
                .setKeepAliveTime(CUSTOM_KEEP_ALIVE_TIME)
                .setCollectMetrics(false);
        
        // When: thread pool is created with custom config
        ThreadPool pool = createCustomThreadPool(config);
        
        // Then: configuration should be applied correctly
        assertEquals(CUSTOM_CORE_POOL_SIZE, pool.getConfig().getCorePoolSize());
        assertEquals(CUSTOM_MAX_POOL_SIZE, pool.getConfig().getMaxPoolSize());
        assertEquals(CUSTOM_QUEUE_CAPACITY, pool.getConfig().getQueueCapacity());
        assertEquals(CUSTOM_KEEP_ALIVE_TIME, pool.getConfig().getKeepAliveTime());
        assertFalse(pool.getConfig().isCollectMetrics());
    }
    
    @Test
    @DisplayName("Should execute Runnable tasks successfully")
    void testTaskExecution() throws Exception {
        // Given: a thread pool and a task
        ThreadPool pool = createDefaultThreadPool();
        AtomicInteger result = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        int expectedValue = 42;
        
        // When: task is executed
        pool.execute(() -> {
            result.set(expectedValue);
            latch.countDown();
        });
        
        // Then: task should complete successfully
        awaitLatch(latch, SHORT_TIMEOUT_SECONDS);
        assertEquals(expectedValue, result.get());
        
        // Verify metrics
        assertEquals(1, pool.getTasksSubmitted());
        assertEquals(1, pool.getTasksCompleted());
        assertEquals(0, pool.getTasksRejected());
    }
    
    @Test
    @DisplayName("Should submit Callable tasks and return Future results")
    void testSubmitCallable() throws Exception {
        // Given: a thread pool and a callable task
        ThreadPool pool = createDefaultThreadPool();
        int expectedResult = 42;
        
        // When: callable is submitted
        Future<Integer> future = pool.submit(() -> {
            Thread.sleep(TASK_EXECUTION_DELAY_MS);
            return expectedResult;
        });
        
        // Then: future should return correct result
        assertEquals(expectedResult, future.get(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        
        // Verify metrics
        assertEquals(1, pool.getTasksSubmitted());
        assertEquals(1, pool.getTasksCompleted());
    }
    
    @Test
    @DisplayName("Should handle multiple concurrent tasks")
    void testMultipleTasks() throws Exception {
        // Given: a thread pool and multiple tasks
        ThreadPool pool = createDefaultThreadPool();
        int numTasks = TASK_COUNT_MEDIUM;
        CountDownLatch latch = new CountDownLatch(numTasks);
        AtomicInteger counter = new AtomicInteger(0);
        
        // When: multiple tasks are submitted
        for (int i = 0; i < numTasks; i++) {
            pool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        
        // Then: all tasks should complete successfully
        awaitLatch(latch, MEDIUM_TIMEOUT_SECONDS);
        assertEquals(numTasks, counter.get());
        
        // Verify metrics
        assertEquals(numTasks, pool.getTasksSubmitted());
        assertEquals(numTasks, pool.getTasksCompleted());
    }
    
    @Test
    @DisplayName("Should shutdown gracefully and complete pending tasks")
    void testShutdown() throws Exception {
        // Given: a thread pool with running tasks
        ThreadPool pool = createDefaultThreadPool();
        int numTasks = TASK_COUNT_SMALL;
        CountDownLatch latch = new CountDownLatch(numTasks);
        
        // When: tasks are submitted and pool is shut down
        for (int i = 0; i < numTasks; i++) {
            pool.execute(() -> {
                try {
                    Thread.sleep(TASK_EXECUTION_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        pool.shutdown();
        
        // Then: all tasks should complete and pool should terminate
        awaitLatch(latch, SHORT_TIMEOUT_SECONDS);
        assertTrue(pool.awaitTermination(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Thread pool should terminate gracefully");
        
        // Verify metrics
        assertEquals(numTasks, pool.getTasksSubmitted());
        assertEquals(numTasks, pool.getTasksCompleted());
    }
    
    @Test
    @DisplayName("Should shutdown immediately and interrupt running tasks")
    void testShutdownNow() throws Exception {
        // Given: a thread pool with long-running tasks
        ThreadPool pool = createDefaultThreadPool();
        int numTasks = TASK_COUNT_SMALL;
        CountDownLatch startedLatch = new CountDownLatch(numTasks);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        // When: long-running tasks are submitted
        for (int i = 0; i < numTasks; i++) {
            pool.execute(() -> {
                startedLatch.countDown();
                try {
                    // Wait indefinitely or until interrupted
                    blockLatch.await();
                } catch (InterruptedException e) {
                    // Expected when shutdownNow is called
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Wait for tasks to start
        awaitLatch(startedLatch, SHORT_TIMEOUT_SECONDS);
        
        // Then: shutdownNow should interrupt tasks
        pool.shutdownNow();
        
        // Verify that the executor is shutting down
        assertTrue(pool.getExecutor().isShutdown(),
                "Executor should be shutting down");
        
        // Unblock any tasks that weren't interrupted
        blockLatch.countDown();
        
        // Wait for termination
        assertTrue(pool.awaitTermination(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Thread pool should terminate");
    }
    
    @Test
    @DisplayName("Should use work-stealing for efficient task distribution")
    void testWorkStealing() throws Exception {
        // Given: a work-stealing thread pool
        ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                .setUseWorkStealing(true)
                .setCorePoolSize(Runtime.getRuntime().availableProcessors());
        
        ThreadPool pool = createCustomThreadPool(config);
        int numTasks = TASK_COUNT_MEDIUM;
        CountDownLatch latch = new CountDownLatch(numTasks);
        AtomicInteger counter = new AtomicInteger(0);
        
        // When: tasks with varying execution times are submitted
        for (int i = 0; i < numTasks; i++) {
            final int taskId = i;
            pool.execute(() -> {
                try {
                    // Simulate varying workloads to test work stealing
                    if (taskId % 10 == 0) {
                        Thread.sleep(50); // Longer task
                    } else {
                        Thread.sleep(5); // Shorter task
                    }
                    counter.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then: all tasks should complete efficiently
        awaitLatch(latch, MEDIUM_TIMEOUT_SECONDS);
        assertEquals(numTasks, counter.get());
        
        // Verify metrics
        assertEquals(numTasks, pool.getTasksSubmitted());
        assertEquals(numTasks, pool.getTasksCompleted());
    }
} 