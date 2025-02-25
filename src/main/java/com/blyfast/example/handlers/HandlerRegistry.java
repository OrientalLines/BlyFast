package com.blyfast.example.handlers;

import com.blyfast.core.Blyfast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Registry for registering and loading handlers.
 * This provides a centralized way to manage and discover handlers.
 */
public class HandlerRegistry {
    
    // List of handler classes to register
    private static final List<Class<?>> HANDLER_CLASSES = new ArrayList<>();
    
    // List of handler instances to register
    private static final List<RouteHandler> HANDLER_INSTANCES = new ArrayList<>();
    
    static {
        // Register built-in handlers
        register(HomeHandler.class);
        register(UserHandler.class);
    }
    
    /**
     * Registers all handlers with the application.
     * 
     * @param app the Blyfast application
     */
    public static void registerAllHandlers(Blyfast app) {
        // Register class-based handlers
        for (Class<?> handlerClass : HANDLER_CLASSES) {
            try {
                // Check if it implements RouteHandler
                if (RouteHandler.class.isAssignableFrom(handlerClass)) {
                    RouteHandler handler = (RouteHandler) handlerClass.getDeclaredConstructor().newInstance();
                    handler.registerRoutes(app);
                } else {
                    // Look for a static registerRoutes method
                    Method registerMethod = handlerClass.getDeclaredMethod("registerRoutes", Blyfast.class);
                    registerMethod.invoke(null, app);
                }
            } catch (Exception e) {
                System.err.println("Failed to register handler: " + handlerClass.getName());
                e.printStackTrace();
            }
        }
        
        // Register instance-based handlers
        for (RouteHandler handler : HANDLER_INSTANCES) {
            handler.registerRoutes(app);
        }
    }
    
    /**
     * Registers a handler class with the registry.
     * 
     * @param handlerClass the handler class to register
     */
    public static void register(Class<?> handlerClass) {
        if (!HANDLER_CLASSES.contains(handlerClass)) {
            HANDLER_CLASSES.add(handlerClass);
        }
    }
    
    /**
     * Registers a handler instance with the registry.
     * 
     * @param handler the handler to register
     */
    public static void register(RouteHandler handler) {
        if (!HANDLER_INSTANCES.contains(handler)) {
            HANDLER_INSTANCES.add(handler);
        }
    }
    
    /**
     * Discovers and registers handlers using Java's ServiceLoader mechanism.
     * This allows for automatic discovery of handlers on the classpath.
     */
    public static void discoverHandlers() {
        ServiceLoader<RouteHandler> serviceLoader = ServiceLoader.load(RouteHandler.class);
        for (RouteHandler handler : serviceLoader) {
            register(handler);
        }
    }
} 