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
            
            // Start dynamic scaling monitor if enabled
            if (config.isEnableDynamicScaling()) {
                startDynamicScalingMonitor();
            }
            
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
                
        // Start dynamic scaling and adaptive queue monitors if enabled
        if (config.isEnableDynamicScaling() || config.isUseAdaptiveQueue()) {
            startMonitors();
        }
    }

    /**
     * Starts the dynamic scaling and adaptive queue monitors if they are enabled in the configuration.
     */
    private void startMonitors() {
        if (executor instanceof ThreadPoolExecutor) {
            final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
            
            // Use a single daemon thread for monitoring
            Thread monitorThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted() && !threadPoolExecutor.isShutdown()) {
                        try {
                            // Dynamic scaling
                            if (config.isEnableDynamicScaling()) {
                                adjustThreadPoolSize(threadPoolExecutor);
                            }
                            
                            // Adaptive queue
                            if (config.isUseAdaptiveQueue()) {
                                adjustQueueCapacity(threadPoolExecutor);
                            }
                            
                            // Sleep for the minimum interval between checks
                            Thread.sleep(Math.min(
                                config.getScalingCheckIntervalMs(), 
                                config.getAdaptiveQueueCheckIntervalMs()));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            logger.error("Error in thread pool monitor", e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread pool monitor terminated with exception", e);
                }
            }, "thread-pool-monitor");
            
            monitorThread.setDaemon(true);
            monitorThread.start();
            
            logger.info("Started thread pool monitoring with dynamic scaling={}, adaptive queue={}", 
                    config.isEnableDynamicScaling(), config.isUseAdaptiveQueue());
        }
    }
    
    /**
     * Adjusts the thread pool size based on current utilization.
     * 
     * @param threadPoolExecutor the thread pool executor
     */
    private void adjustThreadPoolSize(ThreadPoolExecutor threadPoolExecutor) {
        int activeCount = threadPoolExecutor.getActiveCount();
        int currentPoolSize = threadPoolExecutor.getPoolSize();
        int corePoolSize = threadPoolExecutor.getCorePoolSize();
        int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
        
        // Calculate utilization (active threads / current pool size)
        double utilization = currentPoolSize > 0 ? (double) activeCount / currentPoolSize : 0;
        
        // Log current state
        if (logger.isDebugEnabled()) {
            logger.debug("Thread pool utilization: {}%, active: {}, size: {}, core: {}, max: {}",
                    String.format("%.2f", utilization * 100),
                    activeCount, currentPoolSize, corePoolSize, maxPoolSize);
        }
        
        // If utilization is too high, increase core pool size
        if (utilization > config.getTargetUtilization() && corePoolSize < maxPoolSize) {
            int newCoreSize = Math.min(corePoolSize + 2, maxPoolSize);
            threadPoolExecutor.setCorePoolSize(newCoreSize);
            logger.info("Increased core pool size to {} due to high utilization: {}%", 
                    newCoreSize, String.format("%.2f", utilization * 100));
        }
        // If utilization is very low, decrease core pool size
        else if (utilization < config.getTargetUtilization() / 2 && corePoolSize > config.getCorePoolSize()) {
            int newCoreSize = Math.max(corePoolSize - 1, config.getCorePoolSize());
            threadPoolExecutor.setCorePoolSize(newCoreSize);
            logger.info("Decreased core pool size to {} due to low utilization: {}%", 
                    newCoreSize, String.format("%.2f", utilization * 100));
        }
    }
    
    /**
     * Adjusts the queue capacity based on current load.
     * 
     * @param threadPoolExecutor the thread pool executor
     */
    private void adjustQueueCapacity(ThreadPoolExecutor threadPoolExecutor) {
        BlockingQueue<Runnable> queue = threadPoolExecutor.getQueue();
        
        // We can only resize if it's a LinkedBlockingQueue
        if (queue instanceof LinkedBlockingQueue) {
            int queueSize = queue.size();
            int queueCapacity = config.getQueueCapacity();
            
            // Calculate utilization (queue size / queue capacity)
            double queueUtilization = (double) queueSize / queueCapacity;
            
            // Log current state
            if (logger.isDebugEnabled()) {
                logger.debug("Queue utilization: {}%, size: {}, capacity: {}",
                        String.format("%.2f", queueUtilization * 100),
                        queueSize, queueCapacity);
            }
            
            // If queue is very full (>80%), increase capacity for next time
            if (queueUtilization > 0.8) {
                int newCapacity = (int) (queueCapacity * 1.5);
                config.setQueueCapacity(newCapacity);
                logger.info("Queue is heavily utilized ({}%). Will use increased capacity of {} for next startup.", 
                        String.format("%.2f", queueUtilization * 100), newCapacity);
            }
        }
    }

    /**
     * Starts the dynamic scaling monitor for work-stealing pools.
     */
    private void startDynamicScalingMonitor() {
        // For work-stealing pools, we can't directly adjust the thread count,
        // but we can monitor for overload and log warnings
        Thread monitorThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Using completed/submitted tasks to estimate load
                        long completed = tasksCompleted.get();
                        long submitted = tasksSubmitted.get();
                        
                        if (submitted - completed > 1000) {
                            logger.warn("Work-stealing pool appears to be overloaded: {} pending tasks", 
                                    submitted - completed);
                        }
                        
                        // Sleep before next check
                        Thread.sleep(config.getScalingCheckIntervalMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error in work-stealing pool monitor", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Work-stealing pool monitor terminated with exception", e);
            }
        }, "work-stealing-pool-monitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        logger.info("Started work-stealing pool monitoring");
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
        // Thread pool sizing - updated defaults based on performance testing
        private int corePoolSize = Runtime.getRuntime().availableProcessors() * 8; // Doubled from 4x
        private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 16; // Doubled from 8x
        private int queueCapacity = 200000; // Doubled from 100000
        private Duration keepAliveTime = Duration.ofSeconds(30); // Reduced from 60 seconds

        // Thread configuration
        private int threadPriority = Thread.NORM_PRIORITY;
        private boolean daemonThreads = true;

        // Pool behavior
        private boolean allowCoreThreadTimeout = false; // Changed to keep core threads alive
        private boolean prestartCoreThreads = true;
        private boolean useSynchronousQueue = false;
        private boolean useWorkStealing = false; // Disabled by default as it can be less predictable
        private boolean callerRunsWhenRejected = true;
        
        // Dynamic scaling
        private boolean enableDynamicScaling = true;
        private double targetUtilization = 0.85; // Increased from 0.75
        private int scalingCheckIntervalMs = 2000; // Reduced from 5000
        
        // Adaptive queue behavior
        private boolean useAdaptiveQueue = true;
        private int adaptiveQueueCheckIntervalMs = 500; // Reduced from 1000

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

        public boolean isEnableDynamicScaling() {
            return enableDynamicScaling;
        }
        
        public ThreadPoolConfig setEnableDynamicScaling(boolean enableDynamicScaling) {
            this.enableDynamicScaling = enableDynamicScaling;
            return this;
        }
        
        public double getTargetUtilization() {
            return targetUtilization;
        }
        
        public ThreadPoolConfig setTargetUtilization(double targetUtilization) {
            this.targetUtilization = targetUtilization;
            return this;
        }
        
        public int getScalingCheckIntervalMs() {
            return scalingCheckIntervalMs;
        }
        
        public ThreadPoolConfig setScalingCheckIntervalMs(int scalingCheckIntervalMs) {
            this.scalingCheckIntervalMs = scalingCheckIntervalMs;
            return this;
        }
        
        public boolean isUseAdaptiveQueue() {
            return useAdaptiveQueue;
        }
        
        public ThreadPoolConfig setUseAdaptiveQueue(boolean useAdaptiveQueue) {
            this.useAdaptiveQueue = useAdaptiveQueue;
            return this;
        }
        
        public int getAdaptiveQueueCheckIntervalMs() {
            return adaptiveQueueCheckIntervalMs;
        }
        
        public ThreadPoolConfig setAdaptiveQueueCheckIntervalMs(int adaptiveQueueCheckIntervalMs) {
            this.adaptiveQueueCheckIntervalMs = adaptiveQueueCheckIntervalMs;
            return this;
        }
    }
}