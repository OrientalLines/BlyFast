# BlyFast Modular Handlers

This package provides a modular approach to organizing handlers in a BlyFast application.

## Overview

The modular handler system provides several benefits:
1. Separation of concerns - route handlers are organized by functionality
2. Improved code organization - related routes are grouped together
3. Better testability - handlers can be tested in isolation
4. Reusability - handlers can be reused across different applications

## Handler Types

There are two ways to define handlers:

### 1. Class-based static handlers

Define a class with static methods for handlers and a static `registerRoutes` method:

```java
public class MyHandler {
    public static void registerRoutes(Blyfast app) {
        app.get("/my-route", MyHandler::handleRoute);
    }
    
    public static void handleRoute(Context ctx) {
        ctx.json("Hello from MyHandler!");
    }
}
```

### 2. Instance-based handlers (implementing RouteHandler)

Implement the `RouteHandler` interface to create an instance-based handler:

```java
public class MyHandler implements RouteHandler {
    @Override
    public void registerRoutes(Blyfast app) {
        app.get("/my-route", this::handleRoute);
    }
    
    public void handleRoute(Context ctx) {
        ctx.json("Hello from MyHandler!");
    }
}
```

## Using the HandlerRegistry

The `HandlerRegistry` provides a centralized way to register and manage handlers:

```java
// Register class-based handlers
HandlerRegistry.register(MyHandler.class);

// Register instance-based handlers
HandlerRegistry.register(new MyOtherHandler());

// Register all handlers with the application
HandlerRegistry.registerAllHandlers(app);
```

## Automatic Handler Discovery

You can use Java's ServiceLoader mechanism to automatically discover and register handlers:

1. Create a file named `META-INF/services/com.blyfast.example.handlers.RouteHandler`
2. Add the fully qualified name of your handler implementations to this file
3. Call `HandlerRegistry.discoverHandlers()` to discover and register these handlers

## Best Practices

1. Group related routes in the same handler
2. Use meaningful handler names that describe their functionality
3. Keep handlers focused on a specific domain or feature
4. Use instance-based handlers when you need to share state between route handlers
5. Use class-based handlers for stateless route handling 