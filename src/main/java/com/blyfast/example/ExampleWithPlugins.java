package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.CommonMiddleware;
import com.blyfast.plugin.exception.GlobalExceptionHandlerPlugin;
import com.blyfast.plugin.monitor.MonitorPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Example application demonstrating the BlyFast framework with plugins.
 */
public class ExampleWithPlugins {
    public static void main(String[] args) {
        // Create the application
        Blyfast app = new Blyfast();
        
        // Create plugins
        GlobalExceptionHandlerPlugin exceptionPlugin = new GlobalExceptionHandlerPlugin();
        MonitorPlugin monitorPlugin = new MonitorPlugin();

        // Add pre-route middleware
        app.use(CommonMiddleware.logger());
        app.use(CommonMiddleware.responseTime());
        app.use(CommonMiddleware.cors());
        app.use(CommonMiddleware.securityHeaders());
        app.use(CommonMiddleware.monitor());
        
        // Register other plugin components
        exceptionPlugin.register(app);
        monitorPlugin.register(app);

        // Define routes
        // GET /
        app.get("/", ctx -> {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Welcome to the BlyFast API with Plugins");
            response.put("version", "1.0.0");
            ctx.json(response);
        });

        // Example of a route that might throw an exception
        app.get("/error", ctx -> {
            throw new RuntimeException("This is a test exception");
        });

        // Example of a route with a slow operation (for monitoring)
        app.get("/slow", ctx -> {
            try {
                Thread.sleep(2000); // Simulate slow processing
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Slow operation completed");
                ctx.json(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Operation interrupted");
                ctx.status(500).json(error);
            }
        });

        // Example of a route that should be cached (but isn't since we removed caching)
        app.get("/data", ctx -> {
            // This would normally be an expensive operation
            try {
                Thread.sleep(500); // Simulate some processing time
                Map<String, Object> response = new HashMap<>();
                response.put("data", "This response would have been cached");
                response.put("timestamp", System.currentTimeMillis());
                ctx.json(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Add post-route middleware - the order is important
        app.use(CommonMiddleware.recover());         // Handle any recoverable errors  
        app.use(CommonMiddleware.exceptionHandler()); // Finally handle any exceptions

        // Start the server
        app.port(8080).listen();
        System.out.println("Server started on http://localhost:8080");
        System.out.println("- View monitor stats at: http://localhost:8080/monitor/stats");
        System.out.println("- Try the data endpoint at: http://localhost:8080/data");
        System.out.println("- Test error handling at: http://localhost:8080/error");
        System.out.println("- Test monitoring with: http://localhost:8080/slow");
    }
} 