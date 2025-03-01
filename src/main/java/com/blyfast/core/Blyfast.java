package com.blyfast.core;

import com.blyfast.http.Context;
import com.blyfast.http.Request;
import com.blyfast.http.Response;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.Plugin;
import com.blyfast.routing.Route;
import com.blyfast.routing.Router;
import com.blyfast.util.Banner;
import com.blyfast.util.ConsoleColors;
import com.blyfast.util.LogUtil;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.UndertowOptions;
import io.undertow.util.Headers;
import org.xnio.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * The main application class for the Blyfast framework.
 * Provides a fluent API for creating a web server and defining routes.
 */
public class Blyfast {
    private static final Logger logger = LoggerFactory.getLogger(Blyfast.class);
    
    // Object pool configuration
    private static final int DEFAULT_POOL_SIZE = 1000;
    private static final int MAX_POOL_SIZE = 10000;
    private int currentPoolSize = DEFAULT_POOL_SIZE;
    private final ArrayBlockingQueue<Request> requestPool;
    private final ArrayBlockingQueue<Response> responsePool;
    private final ArrayBlockingQueue<Context> contextPool;
    
    // Pool usage metrics
    private final LongAdder requestPoolMisses = new LongAdder();
    private final LongAdder responsePoolMisses = new LongAdder();
    private final LongAdder contextPoolMisses = new LongAdder();
    private long lastPoolResizeTime = System.currentTimeMillis();
    private boolean adaptivePoolSizing = true;

    private final Router router;
    private final List<Middleware> globalMiddleware;
    private final List<Plugin> plugins;
    private final Map<String, Object> locals;
    private Undertow server;
    private String host = "0.0.0.0";
    private int port = 8080;
    private ThreadPool threadPool;
    
    private final boolean useObjectPooling;

    // Add a new field for async middleware execution
    private boolean enableAsyncMiddleware = false;

    // Circuit breaker configuration
    private boolean enableCircuitBreaker = false;
    private int circuitBreakerThreshold = 50; // Number of errors before tripping
    private long circuitBreakerResetTimeoutMs = 30000; // 30 seconds
    
    // Circuit breaker state
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private long circuitOpenTime = 0;

    // Flag to track if pool monitor is running
    private boolean isPoolMonitorRunning = false;

    /**
     * Creates a new Blyfast application instance.
     */
    public Blyfast() {
        this(true); // Enable object pooling by default
    }
    
    /**
     * Creates a new Blyfast application instance with option to enable/disable object pooling.
     * 
     * @param useObjectPooling whether to use object pooling for better performance
     */
    public Blyfast(boolean useObjectPooling) {
        this.router = new Router();
        this.globalMiddleware = new ArrayList<>();
        this.plugins = new ArrayList<>();
        this.locals = new HashMap<>();
        this.useObjectPooling = useObjectPooling;

        // Initialize object pools if enabled
        if (useObjectPooling) {
            this.requestPool = new ArrayBlockingQueue<>(currentPoolSize);
            this.responsePool = new ArrayBlockingQueue<>(currentPoolSize);
            this.contextPool = new ArrayBlockingQueue<>(currentPoolSize);
            
            // Start pool monitoring thread if adaptive sizing is enabled
            if (adaptivePoolSizing) {
                startPoolMonitoringThread();
            }
        } else {
            this.requestPool = null;
            this.responsePool = null;
            this.contextPool = null;
        }

        // Initialize the thread pool with default configuration
        this.threadPool = new ThreadPool();
    }

    /**
     * Creates a new Blyfast application instance with a custom thread pool
     * configuration.
     * 
     * @param threadPoolConfig the thread pool configuration
     */
    public Blyfast(ThreadPool.ThreadPoolConfig threadPoolConfig) {
        this(threadPoolConfig, true);
    }
    
    /**
     * Creates a new Blyfast application instance with a custom thread pool
     * configuration and option to enable/disable object pooling.
     * 
     * @param threadPoolConfig the thread pool configuration
     * @param useObjectPooling whether to use object pooling for better performance
     */
    public Blyfast(ThreadPool.ThreadPoolConfig threadPoolConfig, boolean useObjectPooling) {
        this.router = new Router();
        this.globalMiddleware = new ArrayList<>();
        this.plugins = new ArrayList<>();
        this.locals = new HashMap<>();
        this.useObjectPooling = useObjectPooling;

        // Initialize object pools if enabled
        if (useObjectPooling) {
            this.requestPool = new ArrayBlockingQueue<>(currentPoolSize);
            this.responsePool = new ArrayBlockingQueue<>(currentPoolSize);
            this.contextPool = new ArrayBlockingQueue<>(currentPoolSize);
            
            // Start pool monitoring thread if adaptive sizing is enabled
            if (adaptivePoolSizing) {
                startPoolMonitoringThread();
            }
        } else {
            this.requestPool = null;
            this.responsePool = null;
            this.contextPool = null;
        }

        // Initialize the thread pool with custom configuration
        this.threadPool = new ThreadPool(threadPoolConfig);
    }

    /**
     * Sets the host for the server.
     * 
     * @param host the host to bind to
     * @return this instance for method chaining
     */
    public Blyfast host(String host) {
        this.host = host;
        return this;
    }

    /**
     * Sets the port for the server.
     * 
     * @param port the port to listen on
     * @return this instance for method chaining
     */
    public Blyfast port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Adds a global middleware to the application.
     * 
     * @param middleware the middleware to add
     * @return this instance for method chaining
     */
    public Blyfast use(Middleware middleware) {
        this.globalMiddleware.add(middleware);
        return this;
    }

    /**
     * Registers a plugin with the application.
     * 
     * @param plugin the plugin to register
     * @return this instance for method chaining
     */
    public Blyfast register(Plugin plugin) {
        logger.info(LogUtil.info("Registering plugin: " + ConsoleColors.CYAN_BOLD + plugin.getName() + ConsoleColors.RESET));
        plugins.add(plugin);
        plugin.register(this);
        return this;
    }

    /**
     * Gets a plugin by name.
     * 
     * @param name the name of the plugin
     * @return the plugin or null if not found
     */
    public Plugin getPlugin(String name) {
        for (Plugin plugin : plugins) {
            if (plugin.getName().equals(name)) {
                return plugin;
            }
        }
        return null;
    }

    /**
     * Checks if a plugin is registered.
     * 
     * @param name the name of the plugin
     * @return true if the plugin is registered
     */
    public boolean hasPlugin(String name) {
        return getPlugin(name) != null;
    }

    /**
     * Sets a value in the application locals.
     * 
     * @param key   the key
     * @param value the value
     * @return this instance for method chaining
     */
    public Blyfast set(String key, Object value) {
        locals.put(key, value);
        return this;
    }

    /**
     * Gets a value from the application locals.
     * 
     * @param key the key
     * @param <T> the type of the value
     * @return the value or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) locals.get(key);
    }

    /**
     * Defines a GET route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast get(String path, Handler handler) {
        router.addRoute("GET", path, handler);
        return this;
    }

    /**
     * Defines a POST route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast post(String path, Handler handler) {
        router.addRoute("POST", path, handler);
        return this;
    }

    /**
     * Defines a PUT route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast put(String path, Handler handler) {
        router.addRoute("PUT", path, handler);
        return this;
    }

    /**
     * Defines a DELETE route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast delete(String path, Handler handler) {
        router.addRoute("DELETE", path, handler);
        return this;
    }

    /**
     * Defines a PATCH route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast patch(String path, Handler handler) {
        router.addRoute("PATCH", path, handler);
        return this;
    }

    /**
     * Defines a HEAD route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast head(String path, Handler handler) {
        router.addRoute("HEAD", path, handler);
        return this;
    }

    /**
     * Defines an OPTIONS route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast options(String path, Handler handler) {
        router.addRoute("OPTIONS", path, handler);
        return this;
    }

    /**
     * Defines a TRACE route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast trace(String path, Handler handler) {
        router.addRoute("TRACE", path, handler);
        return this;
    }

    /**
     * Defines a CONNECT route.
     * 
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast connect(String path, Handler handler) {
        router.addRoute("CONNECT", path, handler);
        return this;
    }

    /**
     * Defines a route with a custom HTTP method.
     * 
     * @param method  the HTTP method
     * @param path    the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast route(String method, String path, Handler handler) {
        router.addRoute(method, path, handler);
        return this;
    }

    /**
     * Gets the router instance.
     * 
     * @return the router
     */
    public Router getRouter() {
        return router;
    }

    /**
     * Gets the thread pool used by this application.
     * 
     * @return the thread pool
     */
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    /**
     * Sets the thread pool for this application.
     * 
     * @param threadPool the thread pool to use
     * @return this instance for method chaining
     */
    public Blyfast threadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
        return this;
    }

    /**
     * Enables or disables asynchronous middleware execution.
     * When enabled, middleware can be processed concurrently, improving throughput.
     * 
     * @param enable true to enable async middleware, false to disable
     * @return this instance for method chaining
     */
    public Blyfast asyncMiddleware(boolean enable) {
        this.enableAsyncMiddleware = enable;
        return this;
    }

    /**
     * Enables or disables the circuit breaker.
     * When enabled, the circuit will trip after a certain number of consecutive errors,
     * preventing further requests until a timeout period elapses.
     * 
     * @param enable true to enable circuit breaker, false to disable
     * @return this instance for method chaining
     */
    public Blyfast circuitBreaker(boolean enable) {
        this.enableCircuitBreaker = enable;
        return this;
    }
    
    /**
     * Sets the threshold for the circuit breaker.
     * 
     * @param threshold the number of errors before tripping the circuit
     * @return this instance for method chaining
     */
    public Blyfast circuitBreakerThreshold(int threshold) {
        this.circuitBreakerThreshold = threshold;
        return this;
    }
    
    /**
     * Sets the reset timeout for the circuit breaker.
     * 
     * @param timeoutMs the time in milliseconds before attempting to reset the circuit
     * @return this instance for method chaining
     */
    public Blyfast circuitBreakerResetTimeout(long timeoutMs) {
        this.circuitBreakerResetTimeoutMs = timeoutMs;
        return this;
    }
    
    /**
     * Resets the circuit breaker manually.
     */
    public void resetCircuitBreaker() {
        circuitOpen.set(false);
        errorCount.set(0);
        logger.info("Circuit breaker manually reset");
    }
    
    /**
     * Checks the circuit breaker state and determines whether to allow the request.
     * 
     * @return true if the request should be allowed, false if it should be rejected
     */
    private boolean checkCircuitBreaker() {
        if (!enableCircuitBreaker) {
            return true; // Circuit breaker disabled, allow all requests
        }
        
        // If circuit is open (tripped)
        if (circuitOpen.get()) {
            // Check if reset timeout has elapsed
            long now = System.currentTimeMillis();
            if (now - circuitOpenTime > circuitBreakerResetTimeoutMs) {
                // Try to reset circuit (half-open state)
                if (circuitOpen.compareAndSet(true, false)) {
                    errorCount.set(0);
                    logger.info("Circuit reset after timeout period, entering half-open state");
                }
                return true; // Allow request to test if system has recovered
            }
            return false; // Circuit is open, reject request
        }
        
        return true; // Circuit is closed, allow request
    }
    
    /**
     * Records a successful request for circuit breaker purposes.
     */
    private void recordSuccess() {
        if (enableCircuitBreaker) {
            errorCount.set(0); // Reset error count on success
        }
    }
    
    /**
     * Records a failed request for circuit breaker purposes.
     */
    private void recordFailure() {
        if (enableCircuitBreaker && !circuitOpen.get()) {
            int currentErrors = errorCount.incrementAndGet();
            if (currentErrors >= circuitBreakerThreshold) {
                // Trip the circuit
                if (circuitOpen.compareAndSet(false, true)) {
                    circuitOpenTime = System.currentTimeMillis();
                    logger.warn("Circuit breaker tripped after {} consecutive errors", currentErrors);
                }
            }
        }
    }

    /**
     * Starts the server and begins listening for requests.
     */
    public void listen() {
        listen(() -> {
            // Display a stylish banner with server information
            Banner.display(host, port);
        });
    }

    /**
     * Starts the server with a callback function.
     * 
     * @param callback the function to call when the server has started
     */
    public void listen(Runnable callback) {
        // Notify plugins that the server is starting
        for (Plugin plugin : plugins) {
            logger.info(LogUtil.info("Starting plugin: " + ConsoleColors.CYAN_BOLD + plugin.getName() + ConsoleColors.RESET));
            plugin.onStart(this);
        }

        HttpHandler handler = new BlyFastHttpHandler();
        
        // Calculate optimal thread counts for IO and worker threads - extreme optimization
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int ioThreads = Math.max(4, availableProcessors * 4);  // Quadrupled for extreme throughput
        int workerThreads = threadPool.getConfig().getMaxPoolSize();
        
        // Build an extremely optimized Undertow server
        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(handler)
                // Extreme IO thread optimization for network operations
                .setIoThreads(ioThreads)
                // Worker threads from thread pool configuration
                .setWorkerThreads(workerThreads)
                // Maximize buffer pool for highest possible throughput
                .setBufferSize(64 * 1024)  // Doubled buffer size for extreme throughput
                .setDirectBuffers(true)    // Use direct buffers for best performance
                // Enable HTTP/2 support
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                // Extreme socket optimizations
                .setSocketOption(Options.TCP_NODELAY, true)
                .setSocketOption(Options.BACKLOG, 50000)  // Dramatically increased connection backlog
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setSocketOption(Options.CORK, true)
                .setSocketOption(Options.KEEP_ALIVE, true) // Keep TCP connections alive
                // Connection pooling settings
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, 4 * 1024 * 1024L)  // 4MB max request size
                // Advanced connection handling optimizations
                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                .setServerOption(UndertowOptions.ALWAYS_SET_DATE, true)
                .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false)
                .setServerOption(UndertowOptions.MAX_CONCURRENT_REQUESTS_PER_CONNECTION, 200) // Doubled
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 30 * 1000) // 30 seconds timeout (reduced)
                .setServerOption(UndertowOptions.IDLE_TIMEOUT, 60 * 1000) // 60 second idle timeout
                // Performance-focused settings
                .setServerOption(UndertowOptions.MAX_CACHED_HEADER_SIZE, 1024)
                .setServerOption(UndertowOptions.MAX_HEADERS, 200)
                .setServerOption(UndertowOptions.MAX_PARAMETERS, 2000)
                .setServerOption(UndertowOptions.MAX_COOKIES, 200)
                .setServerOption(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false) // Disable statistics for performance
                .build();

        // Set system properties for extreme performance
        System.setProperty("org.xnio.nio.WRITE_TIMEOUT", "30000"); // 30 second write timeout
        System.setProperty("io.undertow.disable-body-buffer-reuse", "false"); // Enable buffer reuse
        System.setProperty("io.undertow.server.max-entity-workers", String.valueOf(availableProcessors * 8)); // More entity workers

        server.start();
        logger.info(LogUtil.info(ConsoleColors.GREEN_BOLD + "BlyFast server started in EXTREME PERFORMANCE MODE" + ConsoleColors.RESET));
        logger.info(LogUtil.info("IO threads: " + ioThreads + ", worker threads: " + workerThreads + ", buffer size: 64KB"));
        
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        if (server != null) {
            // Notify plugins that the server is stopping
            for (Plugin plugin : plugins) {
                logger.info(LogUtil.info("Stopping plugin: " + ConsoleColors.CYAN_BOLD + plugin.getName() + ConsoleColors.RESET));
                plugin.onStop(this);
            }

            server.stop();

            // Shutdown the thread pool
            threadPool.shutdown();
            try {
                // Wait for tasks to complete
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't complete in time
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info(LogUtil.info(ConsoleColors.YELLOW_BOLD + "Blyfast server stopped" + ConsoleColors.RESET));
        }
    }

    /**
     * Gets a Request object from the pool or creates a new one if the pool is empty.
     * 
     * @param exchange the HTTP exchange
     * @return a Request object
     */
    private Request getRequest(HttpServerExchange exchange) {
        if (useObjectPooling) {
            Request request = requestPool.poll();
            if (request == null) {
                // Pool miss, create a new instance
                requestPoolMisses.increment();
                request = new Request(exchange);
            } else {
                request.reset(exchange);
            }
            return request;
        } else {
            return new Request(exchange);
        }
    }
    
    /**
     * Gets a Response object from the pool or creates a new one if the pool is empty.
     * 
     * @param exchange the HTTP exchange
     * @return a Response object
     */
    private Response getResponse(HttpServerExchange exchange) {
        if (useObjectPooling) {
            Response response = responsePool.poll();
            if (response == null) {
                // Pool miss, create a new instance
                responsePoolMisses.increment();
                response = new Response(exchange);
            } else {
                response.reset(exchange);
            }
            return response;
        } else {
            return new Response(exchange);
        }
    }
    
    /**
     * Gets a Context object from the pool or creates a new one if the pool is empty.
     * 
     * @param request the request object
     * @param response the response object
     * @return a Context object
     */
    private Context getContext(Request request, Response response) {
        if (useObjectPooling) {
            Context context = contextPool.poll();
            if (context == null) {
                // Pool miss, create a new instance
                contextPoolMisses.increment();
                context = new Context(request, response, locals);
            } else {
                context.reset(request, response, locals);
            }
            return context;
        } else {
            return new Context(request, response, locals);
        }
    }
    
    /**
     * Returns objects to their respective pools after use.
     * 
     * @param context the context to recycle
     * @param request the request to recycle
     * @param response the response to recycle
     */
    private void recycleObjects(Context context, Request request, Response response) {
        if (useObjectPooling) {
            // Only return to pool if not at capacity
            requestPool.offer(request);
            responsePool.offer(response);
            contextPool.offer(context);
        }
    }

    /**
     * Internal HTTP handler that processes all incoming requests.
     */
    private class BlyFastHttpHandler implements HttpHandler {
        // Pre-compile frequently used constants for less overhead at runtime
        private static final String GET_METHOD = "GET";
        private static final String HEAD_METHOD = "HEAD";
        
        // Track frequently accessed routes for optimization
        private final Map<String, Route> fastRouteCache = new ConcurrentHashMap<>();
        private final AtomicInteger routeHits = new AtomicInteger(0);
        private final AtomicInteger routeMisses = new AtomicInteger(0);
        
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String path = exchange.getRequestPath();
            String method = exchange.getRequestMethod().toString();
            
            // Ultra-fast path for GET/HEAD requests to common endpoints
            if ((GET_METHOD.equals(method) || HEAD_METHOD.equals(method)) && 
                    exchange.isInIoThread() && !hasBodyConsumingMiddleware()) {
                
                // Common health checks - absolute fastest path
                if ("/health".equals(path) || "/ping".equals(path) || "/status".equals(path)) {
                    exchange.setStatusCode(200);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    exchange.getResponseSender().send("{\"status\":\"ok\"}");
                    return;
                }

                // Fast cached route lookup using path+method as key
                String cacheKey = method + "|" + path;
                Route route = fastRouteCache.get(cacheKey);
                
                if (route != null) {
                    // Hit the fast route cache
                    routeHits.incrementAndGet();
                    
                    // Process directly in IO thread for maximum performance
                    try {
                        ultraFastPathProcessing(exchange, route, path);
                        return;
                    } catch (Exception e) {
                        // Fall back to normal processing if fast path fails
                        logger.debug("Fast path failed, falling back to normal processing", e);
                    }
                } else {
                    routeMisses.incrementAndGet();
                    
                    // Try to find the route
                    route = router.findRoute(method, path);
                    if (route != null && route.getMiddleware().isEmpty()) {
                        // Cache this route for future requests if it doesn't have middleware
                        fastRouteCache.put(cacheKey, route);
                        
                        // Try the fast path
                        try {
                            ultraFastPathProcessing(exchange, route, path);
                            return;
                        } catch (Exception e) {
                            // Fall back to normal processing
                            logger.debug("Fast path failed, falling back to normal processing", e);
                        }
                    }
                }
                
                // Traditional fast path for GET requests without middleware
                try {
                    processSimpleRequest(exchange);
                    return;
                } catch (Exception e) {
                    recordFailure();
                    handleError(exchange, e);
                    return;
                }
            }
            
            // Standard path for non-GET or requests that need blocking I/O
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }

            // Circuit breaker check
            if (!checkCircuitBreaker()) {
                exchange.setStatusCode(503);
                exchange.getResponseSender().send("{\"error\": \"Service temporarily unavailable\", \"message\": \"Circuit breaker open\"}");
                exchange.endExchange();
                return;
            }
            
            // We're now in a worker thread
            try {
                // Make sure the exchange is blocking for body reading
                if (!exchange.isBlocking()) {
                    exchange.startBlocking();
                }

                // Process the request directly
                processRequest(exchange);

                // Record successful request
                recordSuccess();
                
                // Ensure the exchange is completed
                if (!exchange.isComplete()) {
                    exchange.endExchange();
                }
            } catch (Exception e) {
                recordFailure();
                handleError(exchange, e);
            }
        }
        
        /**
         * Ultra-fast path processing that skips almost all checks and overhead.
         * This is the absolute fastest path for simple GET requests to known routes.
         * 
         * @param exchange the HTTP exchange
         * @param route the pre-resolved route
         * @param path the request path
         */
        private void ultraFastPathProcessing(HttpServerExchange exchange, Route route, String path) throws Exception {
            // Direct handler execution for absolute maximum performance
            // This avoids almost all allocations and processing overhead
            String routePath = route.getPath();
            if (!routePath.contains(":") && !routePath.contains("{") && !routePath.contains("*")) {
                // No path parameters, so we can execute directly
                Request request = getRequest(exchange);
                Response response = getResponse(exchange);
                Context context = getContext(request, response);
                
                try {
                    // Execute handler directly
                    route.getHandler().handle(context);
                } finally {
                    // Always recycle objects to avoid leaks
                    recycleObjects(context, request, response);
                }
                return;
            }
            
            // If we have path parameters, use the regular fast path
            // which handles parameter extraction
            throw new IllegalStateException("Route has path parameters, cannot use ultra-fast path");
        }
        
        /**
         * Process simple requests that don't need blocking operations.
         * This method is optimized for speed and runs directly in IO threads.
         * 
         * @param exchange the HTTP exchange
         */
        private void processSimpleRequest(HttpServerExchange exchange) throws Exception {
            // Create request and response objects (without blocking operations)
            Request request = getRequest(exchange);
            Response response = getResponse(exchange);
            Context context = getContext(request, response);
            
            // Find route (avoid full middleware processing for the fast path)
            String method = request.getMethod();
            String path = request.getPath();
            Route route = router.findRoute(method, path);
            
            if (route != null) {
                // Extract path parameters
                router.resolveParams(request, route);
                
                // Execute handler directly
                try {
                    route.getHandler().handle(context);
                    recordSuccess();
                } catch (Exception e) {
                    recordFailure();
                    throw e;
                } finally {
                    recycleObjects(context, request, response);
                }
            } else {
                // No route found - return 404
                response.status(404).json("{\"error\": \"Not Found\"}");
                recycleObjects(context, request, response);
            }
        }
        
        /**
         * Handles error responses consistently
         */
        private void handleError(HttpServerExchange exchange, Exception e) {
            logger.error(LogUtil.error("Error processing request: " + e.getMessage()), e);
            try {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send("{\"error\": \"Internal Server Error\"}");
                exchange.endExchange();
            } catch (Exception ex) {
                logger.error(LogUtil.error("Error sending error response: " + ex.getMessage()), ex);
            }
        }
        
        /**
         * Determines if any middleware is registered that might consume the request body
         */
        private boolean hasBodyConsumingMiddleware() {
            // This is a simple implementation - we could enhance this with more 
            // sophisticated detection of body-consuming middleware
            return !globalMiddleware.isEmpty();
        }

        /**
         * Processes a request using the middleware and router.
         * 
         * @param exchange the HTTP exchange
         */
        private void processRequest(HttpServerExchange exchange) {
            try {
                // Create request and response objects
                Request request = getRequest(exchange);
                Response response = getResponse(exchange);
                Context context = getContext(request, response);

                if (enableAsyncMiddleware && !globalMiddleware.isEmpty()) {
                    // Process global middleware asynchronously
                    processMiddlewareAsync(context, request, response, globalMiddleware, () -> {
                        // Continue with route processing after middleware
                        if (!response.isSent()) {
                            processRoute(context, request, response);
                        } else {
                            recycleObjects(context, request, response);
                        }
                    });
                } else {
                    // Process global middleware synchronously (original behavior)
                    for (Middleware middleware : globalMiddleware) {
                        boolean continueProcessing;
                        try {
                            continueProcessing = middleware.handle(context);
                        } catch (Exception e) {
                            logger.error(LogUtil.error("Error in middleware: " + e.getMessage()), e);
                            response.status(500).json("{\"error\": \"Internal Server Error\"}");
                            recycleObjects(context, request, response);
                            return; // Exit early on error
                        }

                        if (!continueProcessing || response.isSent()) {
                            recycleObjects(context, request, response);
                            return; // Middleware chain was interrupted or response was sent
                        }
                    }

                    // Process the route
                    processRoute(context, request, response);
                }
            } catch (Exception e) {
                logger.error(LogUtil.error("Error processing request: " + e.getMessage()), e);
                try {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("{\"error\": \"Internal Server Error\"}");
                } catch (Exception ex) {
                    logger.error(LogUtil.error("Error sending error response: " + ex.getMessage()), ex);
                }
            }
        }
        
        /**
         * Processes the route after middleware execution.
         * 
         * @param context the request context
         * @param request the request
         * @param response the response
         */
        private void processRoute(Context context, Request request, Response response) {
            String method = request.getMethod();
            String path = request.getPath();

            Route route = router.findRoute(method, path);
            if (route != null) {
                // Extract path parameters - debug logging
                logger.debug(LogUtil.debug("Found route: " + ConsoleColors.BLUE_BOLD + method + " " + path + ConsoleColors.RESET + 
                                  ", pattern: " + ConsoleColors.CYAN + route.getPattern() + ConsoleColors.RESET));
                router.resolveParams(request, route);

                // Debug log the extracted parameters
                logger.debug(LogUtil.debug("Path parameters: " + ConsoleColors.CYAN + request.getPathParams() + ConsoleColors.RESET));

                if (enableAsyncMiddleware && !route.getMiddleware().isEmpty()) {
                    // Process route-specific middleware asynchronously
                    processMiddlewareAsync(context, request, response, route.getMiddleware(), () -> {
                        // Execute the route handler if response hasn't been sent
                        if (!response.isSent()) {
                            executeRouteHandler(context, response, route);
                        }
                        recycleObjects(context, request, response);
                    });
                } else {
                    // Process route-specific middleware synchronously (original behavior)
                    for (Middleware middleware : route.getMiddleware()) {
                        boolean continueProcessing;
                        try {
                            continueProcessing = middleware.handle(context);
                        } catch (Exception e) {
                            logger.error(LogUtil.error("Error in route middleware: " + e.getMessage()), e);
                            response.status(500).json("{\"error\": \"Internal Server Error\"}");
                            recycleObjects(context, request, response);
                            return; // Exit early on error
                        }

                        if (!continueProcessing || response.isSent()) {
                            recycleObjects(context, request, response);
                            return; // Middleware chain was interrupted or response was sent
                        }
                    }

                    // Execute the route handler
                    executeRouteHandler(context, response, route);
                    recycleObjects(context, request, response);
                }
            } else {
                // No route found - return 404
                response.status(404).json("{\"error\": \"Not Found\"}");
                recycleObjects(context, request, response);
            }
        }
        
        /**
         * Executes the route handler and handles any exceptions.
         * 
         * @param context the request context
         * @param response the response
         * @param route the route to execute
         */
        private void executeRouteHandler(Context context, Response response, Route route) {
            try {
                route.getHandler().handle(context);
                // Record success for circuit breaker
                recordSuccess();
            } catch (Exception e) {
                // Record failure for circuit breaker
                recordFailure();
                
                logger.error(LogUtil.error("Error in route handler: " + e.getMessage()), e);
                if (!response.isSent()) {
                    response.status(500).json("{\"error\": \"Internal Server Error\"}");
                }
            }
        }
        
        /**
         * Processes middleware asynchronously.
         * 
         * @param context the request context
         * @param request the request
         * @param response the response
         * @param middlewareList the list of middleware to process
         * @param completionCallback the callback to execute after all middleware is processed
         */
        private void processMiddlewareAsync(Context context, Request request, Response response, 
                                          List<Middleware> middlewareList, Runnable completionCallback) {
            // If no middleware, just call the completion callback
            if (middlewareList.isEmpty()) {
                completionCallback.run();
                return;
            }
            
            // Use the thread pool for async processing
            threadPool.execute(() -> {
                // Process each middleware in sequence
                for (Middleware middleware : middlewareList) {
                    if (response.isSent()) {
                        break; // Stop if response already sent
                    }
                    
                    boolean continueProcessing;
                    try {
                        continueProcessing = middleware.handle(context);
                    } catch (Exception e) {
                        logger.error(LogUtil.error("Error in async middleware: " + e.getMessage()), e);
                        response.status(500).json("{\"error\": \"Internal Server Error\"}");
                        completionCallback.run();
                        return; // Exit early on error
                    }
                    
                    if (!continueProcessing) {
                        break; // Stop middleware chain if requested
                    }
                }
                
                // Call the completion callback
                completionCallback.run();
            });
        }
    }

    /**
     * Functional interface for route handlers.
     */
    public interface Handler {
        void handle(Context ctx) throws Exception;
    }

    /**
     * Enables or disables adaptive pool sizing.
     * 
     * @param enable true to enable adaptive pool sizing, false to disable
     * @return this instance for method chaining
     */
    public Blyfast adaptivePoolSizing(boolean enable) {
        this.adaptivePoolSizing = enable;
        if (enable && useObjectPooling && !isPoolMonitorRunning) {
            startPoolMonitoringThread();
        }
        return this;
    }
    
    /**
     * Sets the object pool size.
     * 
     * @param size the pool size
     * @return this instance for method chaining
     */
    public Blyfast poolSize(int size) {
        if (size > 0) {
            this.currentPoolSize = Math.min(size, MAX_POOL_SIZE);
        }
        return this;
    }
    
    /**
     * Starts a background thread to monitor and adjust object pool sizes.
     */
    private void startPoolMonitoringThread() {
        if (isPoolMonitorRunning || !useObjectPooling) {
            return;
        }
        
        Thread monitorThread = new Thread(() -> {
            isPoolMonitorRunning = true;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(30000); // Check every 30 seconds
                        
                        // Check if pools need to be resized
                        checkPoolResizing();
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error in pool monitoring thread", e);
                    }
                }
            } finally {
                isPoolMonitorRunning = false;
            }
        }, "object-pool-monitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
        logger.info("Started object pool monitoring thread");
    }
    
    /**
     * Checks if the object pools need to be resized based on miss rates.
     */
    private void checkPoolResizing() {
        if (!adaptivePoolSizing || !useObjectPooling) {
            return;
        }
        
        // We can't modify ArrayBlockingQueue size directly, so the new size will
        // be applied over time as new objects are created
        
        long now = System.currentTimeMillis();
        long totalMisses = requestPoolMisses.sum() + responsePoolMisses.sum() + contextPoolMisses.sum();
        
        // If we have a significant number of misses, increase the pool size
        if (totalMisses > currentPoolSize * 0.1) { // More than 10% misses
            int newSize = Math.min((int)(currentPoolSize * 1.5), MAX_POOL_SIZE);
            if (newSize > currentPoolSize) {
                logger.info("Increasing object pool size from {} to {} due to {} pool misses", 
                        currentPoolSize, newSize, totalMisses);
                currentPoolSize = newSize;
                lastPoolResizeTime = now;
                
                // Reset counters
                requestPoolMisses.reset();
                responsePoolMisses.reset();
                contextPoolMisses.reset();
            }
        }
        // If we haven't had pool misses for a while and it's been at least 10 minutes since 
        // the last resize, consider reducing pool size
        else if (totalMisses == 0 && now - lastPoolResizeTime > 600000 && currentPoolSize > DEFAULT_POOL_SIZE) {
            int newSize = Math.max((int)(currentPoolSize * 0.8), DEFAULT_POOL_SIZE);
            if (newSize < currentPoolSize) {
                logger.info("Decreasing object pool size from {} to {} due to low utilization", 
                        currentPoolSize, newSize);
                currentPoolSize = newSize;
                lastPoolResizeTime = now;
            }
        }
    }
}