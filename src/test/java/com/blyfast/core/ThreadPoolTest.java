package com.blyfast.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ThreadPool implementation.
 */
public class ThreadPoolTest {
    
    @Test
    public void testDefaultConfiguration() {
        ThreadPool threadPool = new ThreadPool();
        
        assertNotNull(threadPool.getConfig());
        assertEquals(Runtime.getRuntime().availableProcessors(), threadPool.getConfig().getCorePoolSize());
        assertEquals(Runtime.getRuntime().availableProcessors() * 2, threadPool.getConfig().getMaxPoolSize());
        assertEquals(10000, threadPool.getConfig().getQueueCapacity());
        assertEquals(Duration.ofSeconds(60), threadPool.getConfig().getKeepAliveTime());
        assertTrue(threadPool.getConfig().isCollectMetrics());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testCustomConfiguration() {
        ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(4)
                .setMaxPoolSize(8)
                .setQueueCapacity(100)
                .setKeepAliveTime(Duration.ofSeconds(30))
                .setCollectMetrics(false);
        
        ThreadPool threadPool = new ThreadPool(config);
        
        assertEquals(4, threadPool.getConfig().getCorePoolSize());
        assertEquals(8, threadPool.getConfig().getMaxPoolSize());
        assertEquals(100, threadPool.getConfig().getQueueCapacity());
        assertEquals(Duration.ofSeconds(30), threadPool.getConfig().getKeepAliveTime());
        assertFalse(threadPool.getConfig().isCollectMetrics());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testTaskExecution() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        // Execute a simple task
        AtomicInteger result = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        threadPool.execute(() -> {
            result.set(42);
            latch.countDown();
        });
        
        // Wait for the task to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(42, result.get());
        
        // Verify metrics
        assertEquals(1, threadPool.getTasksSubmitted());
        assertEquals(1, threadPool.getTasksCompleted());
        assertEquals(0, threadPool.getTasksRejected());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testSubmitCallable() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        // Submit a callable task
        Future<Integer> future = threadPool.submit(() -> {
            Thread.sleep(100); // Simulate some work
            return 42;
        });
        
        // Get the result
        assertEquals(42, future.get(5, TimeUnit.SECONDS));
        
        // Verify metrics
        assertEquals(1, threadPool.getTasksSubmitted());
        assertEquals(1, threadPool.getTasksCompleted());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testMultipleTasks() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        int numTasks = 100;
        CountDownLatch latch = new CountDownLatch(numTasks);
        AtomicInteger counter = new AtomicInteger(0);
        
        // Submit multiple tasks
        for (int i = 0; i < numTasks; i++) {
            threadPool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        
        // Wait for all tasks to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(numTasks, counter.get());
        
        // Verify metrics
        assertEquals(numTasks, threadPool.getTasksSubmitted());
        assertEquals(numTasks, threadPool.getTasksCompleted());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testShutdown() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        // Submit some tasks
        int numTasks = 10;
        CountDownLatch latch = new CountDownLatch(numTasks);
        
        for (int i = 0; i < numTasks; i++) {
            threadPool.execute(() -> {
                try {
                    Thread.sleep(100); // Simulate some work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Shutdown the thread pool
        threadPool.shutdown();
        
        // Wait for tasks to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(threadPool.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify metrics
        assertEquals(numTasks, threadPool.getTasksSubmitted());
        assertEquals(numTasks, threadPool.getTasksCompleted());
    }
    
    @Test
    public void testShutdownNow() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        // Submit some long-running tasks
        int numTasks = 10;
        CountDownLatch startedLatch = new CountDownLatch(numTasks);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        for (int i = 0; i < numTasks; i++) {
            threadPool.execute(() -> {
                startedLatch.countDown();
                try {
                    // Wait indefinitely or until interrupted
                    blockLatch.await();
                } catch (InterruptedException e) {
                    // Expected
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Wait for tasks to start
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS));
        
        // Shutdown the thread pool immediately
        List<Runnable> unexecutedTasks = threadPool.shutdownNow();
        
        // Verify that the thread pool is shutting down
        assertTrue(threadPool.getExecutor().isShutdown());
        
        // Unblock any tasks that weren't interrupted
        blockLatch.countDown();
        
        // Wait for termination
        assertTrue(threadPool.awaitTermination(5, TimeUnit.SECONDS));
    }
    
    @Test
    public void testWorkStealing() throws Exception {
        // Create a work-stealing thread pool
        ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                .setUseWorkStealing(true)
                .setCorePoolSize(Runtime.getRuntime().availableProcessors());
        
        ThreadPool threadPool = new ThreadPool(config);
        
        int numTasks = 100;
        CountDownLatch latch = new CountDownLatch(numTasks);
        AtomicInteger counter = new AtomicInteger(0);
        
        // Submit tasks with varying execution times to test work stealing
        for (int i = 0; i < numTasks; i++) {
            final int taskId = i;
            threadPool.execute(() -> {
                try {
                    // Simulate varying workloads
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
        
        // Wait for all tasks to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(numTasks, counter.get());
        
        // Verify metrics
        assertEquals(numTasks, threadPool.getTasksSubmitted());
        assertEquals(numTasks, threadPool.getTasksCompleted());
        
        threadPool.shutdown();
    }
    
    @Test
    public void testPerformanceMetrics() throws Exception {
        ThreadPool threadPool = new ThreadPool();
        
        int numTasks = 1000;
        CountDownLatch latch = new CountDownLatch(numTasks);
        
        // Submit tasks that perform some work
        for (int i = 0; i < numTasks; i++) {
            final int taskId = i;
            threadPool.execute(() -> {
                // Perform some work
                long result = 0;
                for (int j = 0; j < 10000; j++) {
                    result += (taskId * j) % 100;
                }
                latch.countDown();
            });
        }
        
        // Wait for all tasks to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        // Verify metrics
        assertEquals(numTasks, threadPool.getTasksSubmitted());
        assertEquals(numTasks, threadPool.getTasksCompleted());
        assertTrue(threadPool.getTotalExecutionTime() > 0);
        assertTrue(threadPool.getAverageExecutionTime() > 0);
        
        // Print performance metrics
        System.out.println("Thread pool performance metrics:");
        System.out.println("  Tasks submitted: " + threadPool.getTasksSubmitted());
        System.out.println("  Tasks completed: " + threadPool.getTasksCompleted());
        System.out.println("  Tasks rejected: " + threadPool.getTasksRejected());
        System.out.println("  Total execution time: " + threadPool.getTotalExecutionTime() + " ns");
        System.out.println("  Average execution time: " + 
                (threadPool.getAverageExecutionTime() / 1_000_000.0) + " ms");
        
        threadPool.shutdown();
    }
} 