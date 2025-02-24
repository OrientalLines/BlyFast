package com.blyfast.core;

import com.blyfast.http.Context;
import com.blyfast.http.Request;
import com.blyfast.http.Response;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.Plugin;
import com.blyfast.routing.Route;
import com.blyfast.routing.Router;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main application class for the Blyfast framework.
 * Provides a fluent API for creating a web server and defining routes.
 */
public class Blyfast {
    private static final Logger logger = LoggerFactory.getLogger(Blyfast.class);
    
    private final Router router;
    private final List<Middleware> globalMiddleware;
    private final List<Plugin> plugins;
    private final Map<String, Object> locals;
    private Undertow server;
    private String host = "0.0.0.0";
    private int port = 8080;
    
    /**
     * Creates a new Blyfast application instance.
     */
    public Blyfast() {
        this.router = new Router();
        this.globalMiddleware = new ArrayList<>();
        this.plugins = new ArrayList<>();
        this.locals = new HashMap<>();
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
        logger.info("Registering plugin: {}", plugin.getName());
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
     * @param key the key
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
     * @param path the route path
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
     * @param path the route path
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
     * @param path the route path
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
     * @param path the route path
     * @param handler the handler function
     * @return this instance for method chaining
     */
    public Blyfast delete(String path, Handler handler) {
        router.addRoute("DELETE", path, handler);
        return this;
    }
    
    /**
     * Defines a route with a custom HTTP method.
     * 
     * @param method the HTTP method
     * @param path the route path
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
     * Starts the server and begins listening for requests.
     */
    public void listen() {
        listen(() -> logger.info("Blyfast server started on {}:{}", host, port));
    }
    
    /**
     * Starts the server with a callback function.
     * 
     * @param callback the function to call when the server has started
     */
    public void listen(Runnable callback) {
        // Notify plugins that the server is starting
        for (Plugin plugin : plugins) {
            plugin.onStart(this);
        }
        
        HttpHandler handler = new BlyFastHttpHandler();
        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(handler)
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
                plugin.onStop(this);
            }
            
            server.stop();
            logger.info("Blyfast server stopped");
        }
    }
    
    /**
     * Internal HTTP handler that processes all incoming requests.
     */
    private class BlyFastHttpHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }
            
            Request request = new Request(exchange);
            Response response = new Response(exchange);
            Context context = new Context(request, response);
            
            // Process middleware first
            for (Middleware middleware : globalMiddleware) {
                boolean continueProcessing = middleware.handle(context);
                if (!continueProcessing) {
                    return; // Middleware chain was interrupted
                }
            }
            
            // Process the route
            Route route = router.findRoute(request.getMethod(), request.getPath());
            if (route != null) {
                // Extract path parameters
                router.resolveParams(request, route);
                
                // Process route-specific middleware
                for (Middleware middleware : route.getMiddleware()) {
                    boolean continueProcessing = middleware.handle(context);
                    if (!continueProcessing) {
                        return; // Middleware chain was interrupted
                    }
                }
                
                // Execute the route handler
                route.getHandler().handle(context);
            } else {
                // No route found - send 404
                response.status(404).json("{\"error\": \"Not Found\"}");
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