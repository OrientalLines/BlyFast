package com.blyfast.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A highly optimized thread pool for handling HTTP requests in the BlyFast
 * framework.
 * This implementation focuses on maximum throughput and minimal latency.
 */
public class ThreadPool {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPool.class);

    // Core thread pool for handling requests
    private final ExecutorService executor;

    // Statistics for monitoring
    private final AtomicLong tasksSubmitted = new AtomicLong(0);
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksRejected = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    // Configuration
    private final ThreadPoolConfig config;

    /**
     * Creates a new thread pool with default configuration.
     */
    public ThreadPool() {
        this(new ThreadPoolConfig());
    }

    /**
     * Creates a new thread pool with the specified configuration.
     * 
     * @param config the thread pool configuration
     */
    public ThreadPool(ThreadPoolConfig config) {
        this.config = config;

        // Create the thread pool with optimized settings
        BlockingQueue<Runnable> workQueue;

        // Choose the most appropriate queue based on configuration
        if (config.isUseWorkStealing()) {
            // Work-stealing pool doesn't use a queue in the same way
            this.executor = Executors.newWorkStealingPool(config.getCorePoolSize());
            logger.info("Created work-stealing thread pool with parallelism level: {}", config.getCorePoolSize());
            return;
        } else if (config.isUseSynchronousQueue()) {
            // Synchronous handoff - no queueing, immediate handoff to a thread or rejection
            workQueue = new SynchronousQueue<>();
        } else {
            // Bounded queue with the specified capacity
            workQueue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        }

        // Create a custom thread factory
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "blyfast-worker-" + threadNumber.getAndIncrement());

                // Set thread priority
                if (config.getThreadPriority() > 0) {
                    thread.setPriority(config.getThreadPriority());
                }

                // Set as daemon thread if configured
                thread.setDaemon(config.isDaemonThreads());

                return thread;
            }
        };

        // Create a custom rejection handler
        RejectedExecutionHandler rejectionHandler = (r, executor) -> {
            tasksRejected.incrementAndGet();
            if (config.isCallerRunsWhenRejected()) {
                // Execute the task in the caller's thread
                logger.debug("Thread pool saturated, executing task in caller thread");
                r.run();
            } else {
                logger.warn("Thread pool saturated, rejecting task");
                throw new RejectedExecutionException("Thread pool saturated");
            }
        };

        // Create the thread pool
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                rejectionHandler);

        // Allow core threads to time out if configured
        threadPoolExecutor.allowCoreThreadTimeOut(config.isAllowCoreThreadTimeout());

        // Prestart core threads if configured
        if (config.isPrestartCoreThreads()) {
            threadPoolExecutor.prestartAllCoreThreads();
        }

        this.executor = threadPoolExecutor;

        logger.info("Created thread pool with core size: {}, max size: {}, queue capacity: {}",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
    }

    /**
     * Submits a task to the thread pool.
     * 
     * @param task the task to execute
     */
    public void execute(Runnable task) {
        tasksSubmitted.incrementAndGet();

        if (config.isCollectMetrics()) {
            // Wrap the task to collect metrics
            executor.execute(() -> {
                long startTime = System.nanoTime();
                try {
                    task.run();
                } finally {
                    long executionTime = System.nanoTime() - startTime;
                    totalExecutionTime.addAndGet(executionTime);
                    tasksCompleted.incrementAndGet();
                }
            });
        } else {
            // Execute the task directly without metrics collection
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    tasksCompleted.incrementAndGet();
                }
            });
        }
    }

    /**
     * Submits a task to the thread pool and returns a Future representing the
     * result.
     * 
     * @param task the task to execute
     * @param <T>  the type of the task's result
     * @return a Future representing the result of the task
     */
    public <T> Future<T> submit(Callable<T> task) {
        tasksSubmitted.incrementAndGet();

        if (config.isCollectMetrics()) {
            // Wrap the task to collect metrics
            return executor.submit(() -> {
                long startTime = System.nanoTime();
                try {
                    return task.call();
                } finally {
                    long executionTime = System.nanoTime() - startTime;
                    totalExecutionTime.addAndGet(executionTime);
                    tasksCompleted.incrementAndGet();
                }
            });
        } else {
            // Submit the task directly without metrics collection
            return executor.submit(() -> {
                try {
                    return task.call();
                } finally {
                    tasksCompleted.incrementAndGet();
                }
            });
        }
    }

    /**
     * Shuts down the thread pool, allowing previously submitted tasks to complete.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Shuts down the thread pool immediately, attempting to stop all actively
     * executing tasks.
     * 
     * @return a list of tasks that were awaiting execution
     */
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    /**
     * Waits for all tasks to complete or until the timeout occurs.
     * 
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return true if the executor terminated, false if the timeout elapsed before
     *         termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    /**
     * Gets the number of tasks that have been submitted to the thread pool.
     * 
     * @return the number of tasks submitted
     */
    public long getTasksSubmitted() {
        return tasksSubmitted.get();
    }

    /**
     * Gets the number of tasks that have been completed by the thread pool.
     * 
     * @return the number of tasks completed
     */
    public long getTasksCompleted() {
        return tasksCompleted.get();
    }

    /**
     * Gets the number of tasks that have been rejected by the thread pool.
     * 
     * @return the number of tasks rejected
     */
    public long getTasksRejected() {
        return tasksRejected.get();
    }

    /**
     * Gets the total execution time of all completed tasks in nanoseconds.
     * 
     * @return the total execution time in nanoseconds
     */
    public long getTotalExecutionTime() {
        return totalExecutionTime.get();
    }

    /**
     * Gets the average execution time of completed tasks in nanoseconds.
     * 
     * @return the average execution time in nanoseconds, or 0 if no tasks have been
     *         completed
     */
    public double getAverageExecutionTime() {
        long completed = tasksCompleted.get();
        return completed > 0 ? (double) totalExecutionTime.get() / completed : 0;
    }

    /**
     * Gets the thread pool configuration.
     * 
     * @return the configuration
     */
    public ThreadPoolConfig getConfig() {
        return config;
    }

    /**
     * Gets the underlying executor service.
     * 
     * @return the executor service
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Gets the current number of threads in the pool.
     * 
     * @return the current pool size, or -1 if not available
     */
    public int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getPoolSize();
        }
        return -1;
    }

    /**
     * Gets the current number of active threads in the pool.
     * 
     * @return the number of active threads, or -1 if not available
     */
    public int getActiveCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        }
        return -1;
    }

    /**
     * Gets the current size of the task queue.
     * 
     * @return the queue size, or -1 if not available
     */
    public int getQueueSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getQueue().size();
        }
        return -1;
    }

    /**
     * Configuration for the thread pool.
     */
    public static class ThreadPoolConfig {
        // Thread pool sizing
        private int corePoolSize = Runtime.getRuntime().availableProcessors();
        private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        private int queueCapacity = 10000;
        private Duration keepAliveTime = Duration.ofSeconds(60);

        // Thread configuration
        private int threadPriority = Thread.NORM_PRIORITY;
        private boolean daemonThreads = true;

        // Pool behavior
        private boolean allowCoreThreadTimeout = false;
        private boolean prestartCoreThreads = true;
        private boolean useSynchronousQueue = false;
        private boolean useWorkStealing = false;
        private boolean callerRunsWhenRejected = true;

        // Metrics
        private boolean collectMetrics = true;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public ThreadPoolConfig setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public ThreadPoolConfig setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public ThreadPoolConfig setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public Duration getKeepAliveTime() {
            return keepAliveTime;
        }

        public ThreadPoolConfig setKeepAliveTime(Duration keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        public int getThreadPriority() {
            return threadPriority;
        }

        public ThreadPoolConfig setThreadPriority(int threadPriority) {
            this.threadPriority = threadPriority;
            return this;
        }

        public boolean isDaemonThreads() {
            return daemonThreads;
        }

        public ThreadPoolConfig setDaemonThreads(boolean daemonThreads) {
            this.daemonThreads = daemonThreads;
            return this;
        }

        public boolean isAllowCoreThreadTimeout() {
            return allowCoreThreadTimeout;
        }

        public ThreadPoolConfig setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
            this.allowCoreThreadTimeout = allowCoreThreadTimeout;
            return this;
        }

        public boolean isPrestartCoreThreads() {
            return prestartCoreThreads;
        }

        public ThreadPoolConfig setPrestartCoreThreads(boolean prestartCoreThreads) {
            this.prestartCoreThreads = prestartCoreThreads;
            return this;
        }

        public boolean isUseSynchronousQueue() {
            return useSynchronousQueue;
        }

        public ThreadPoolConfig setUseSynchronousQueue(boolean useSynchronousQueue) {
            this.useSynchronousQueue = useSynchronousQueue;
            return this;
        }

        public boolean isUseWorkStealing() {
            return useWorkStealing;
        }

        public ThreadPoolConfig setUseWorkStealing(boolean useWorkStealing) {
            this.useWorkStealing = useWorkStealing;
            return this;
        }

        public boolean isCallerRunsWhenRejected() {
            return callerRunsWhenRejected;
        }

        public ThreadPoolConfig setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
            this.callerRunsWhenRejected = callerRunsWhenRejected;
            return this;
        }

        public boolean isCollectMetrics() {
            return collectMetrics;
        }

        public ThreadPoolConfig setCollectMetrics(boolean collectMetrics) {
            this.collectMetrics = collectMetrics;
            return this;
        }
    }
}