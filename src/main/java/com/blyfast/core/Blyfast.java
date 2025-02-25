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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The main application class for the Blyfast framework.
 * Provides a fluent API for creating a web server and defining routes.
 */
public class Blyfast {
    private static final Logger logger = LoggerFactory.getLogger(Blyfast.class);
    
    // Object pool configuration
    private static final int DEFAULT_POOL_SIZE = 1000;
    private final ArrayBlockingQueue<Request> requestPool;
    private final ArrayBlockingQueue<Response> responsePool;
    private final ArrayBlockingQueue<Context> contextPool;

    private final Router router;
    private final List<Middleware> globalMiddleware;
    private final List<Plugin> plugins;
    private final Map<String, Object> locals;
    private Undertow server;
    private String host = "0.0.0.0";
    private int port = 8080;
    private ThreadPool threadPool;
    
    private final boolean useObjectPooling;

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
            this.requestPool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
            this.responsePool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
            this.contextPool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
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
            this.requestPool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
            this.responsePool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
            this.contextPool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
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
        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(handler)
                .setWorkerThreads(threadPool.getConfig().getMaxPoolSize()) // Set Undertow worker threads to match our
                                                                           // thread pool
                .build();

        server.start();
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
        if (!useObjectPooling) {
            return new Request(exchange);
        }
        
        Request request = requestPool.poll();
        if (request == null) {
            return new Request(exchange);
        }
        request.reset(exchange);
        return request;
    }
    
    /**
     * Gets a Response object from the pool or creates a new one if the pool is empty.
     * 
     * @param exchange the HTTP exchange
     * @return a Response object
     */
    private Response getResponse(HttpServerExchange exchange) {
        if (!useObjectPooling) {
            return new Response(exchange);
        }
        
        Response response = responsePool.poll();
        if (response == null) {
            return new Response(exchange);
        }
        response.reset(exchange);
        return response;
    }
    
    /**
     * Gets a Context object from the pool or creates a new one if the pool is empty.
     * 
     * @param request the request object
     * @param response the response object
     * @return a Context object
     */
    private Context getContext(Request request, Response response) {
        if (!useObjectPooling) {
            return new Context(request, response);
        }
        
        Context context = contextPool.poll();
        if (context == null) {
            return new Context(request, response);
        }
        context.reset(request, response);
        return context;
    }
    
    /**
     * Returns objects to their respective pools after use.
     * 
     * @param context the context to recycle
     * @param request the request to recycle
     * @param response the response to recycle
     */
    private void recycleObjects(Context context, Request request, Response response) {
        if (!useObjectPooling) {
            return;
        }
        
        // Only recycle if pools aren't full
        contextPool.offer(context);
        requestPool.offer(request);
        responsePool.offer(response);
    }

    /**
     * Internal HTTP handler that processes all incoming requests.
     */
    private class BlyFastHttpHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                // Dispatch to worker thread
                exchange.dispatch(this);
                return;
            }

            // We're now in a worker thread
            try {
                // Make sure the exchange is blocking for body reading
                if (!exchange.isBlocking()) {
                    exchange.startBlocking();
                }

                // Process the request directly, not in a separate thread
                processRequest(exchange);

                // Ensure the exchange is completed
                if (!exchange.isComplete()) {
                    exchange.endExchange();
                }
            } catch (Exception e) {
                logger.error(LogUtil.error("Error processing request: " + e.getMessage()), e);
                try {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("{\"error\": \"Internal Server Error\"}");
                    exchange.endExchange();
                } catch (Exception ex) {
                    logger.error(LogUtil.error("Error sending error response: " + ex.getMessage()), ex);
                }
            }
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

                // Process global middleware
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

                    // Process route-specific middleware
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
                    try {
                        route.getHandler().handle(context);
                    } catch (Exception e) {
                        logger.error(LogUtil.error("Error in route handler: " + e.getMessage()), e);
                        if (!response.isSent()) {
                            response.status(500).json("{\"error\": \"Internal Server Error\"}");
                        }
                    }
                } else {
                    // No route found - return 404
                    response.status(404).json("{\"error\": \"Not Found\"}");
                }
                
                // Return objects to the pool after processing
                recycleObjects(context, request, response);
            } catch (Exception e) {
                logger.error(LogUtil.error("Unhandled error in request processing: " + e.getMessage()), e);
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("{\"error\": \"Internal Server Error\"}");
                }
            }
        }
    }

    /**
     * Functional interface for route handlers.
     */
    public interface Handler {
        void handle(Context ctx) throws Exception;
    }
}