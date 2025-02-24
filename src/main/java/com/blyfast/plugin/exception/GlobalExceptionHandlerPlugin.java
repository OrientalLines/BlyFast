package com.blyfast.plugin.exception;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Plugin that adds global exception handling to the application.
 * This captures uncaught exceptions and provides a consistent error response.
 */
public class GlobalExceptionHandlerPlugin extends AbstractPlugin {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandlerPlugin.class);
    
    public GlobalExceptionHandlerPlugin() {
        super("global-exception-handler", "1.0.0");
    }
    
    @Override
    public void register(Blyfast app) {
        logger.info("Registering Global Exception Handler Plugin");
        app.use(exceptionHandler());
    }
    
    /**
     * Creates a middleware that handles uncaught exceptions.
     *
     * @return the middleware
     */
    public static Middleware exceptionHandler() {
        return ctx -> {
            try {
                // Continue processing and catch any exceptions
                return true;
            } catch (Exception e) {
                // Log the exception
                logger.error("Uncaught exception: ", e);
                
                // Send a consistent error response
                sendErrorResponse(ctx, e);
                
                // Stop the middleware chain
                return false;
            }
        };
    }
    
    /**
     * Sends a consistent error response for an exception.
     *
     * @param ctx the context
     * @param e   the exception
     */
    private static void sendErrorResponse(Context ctx, Exception e) {
        int status = 500;
        String message = "Internal Server Error";
        
        // Customize status and message based on exception type
        if (e instanceof IllegalArgumentException) {
            status = 400;
            message = "Bad Request";
        } else if (e instanceof SecurityException) {
            status = 403;
            message = "Forbidden";
        } else if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("not found")) {
            status = 404;
            message = "Not Found";
        }
        
        // Create error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("status", status);
        errorResponse.put("message", message);
        
        // Include exception details in development mode
        if (Boolean.getBoolean("blyfast.dev")) {
            errorResponse.put("exception", e.getClass().getName());
            errorResponse.put("exceptionMessage", e.getMessage());
        }
        
        // Send the response
        ctx.status(status).json(errorResponse);
    }
} 