# BlyFast Framework

A blazingly fast Java framework for REST APIs, inspired by GoFiber and Express.js.

## Features

- Extremely fast HTTP request handling with advanced thread pool optimization
- Adaptive resource management for high-performance applications
- Simple and intuitive API for defining routes and middleware
- Robust plugin system for extending functionality
- JSON processing with Jackson
- Path parameter support with efficient routing
- Comprehensive middleware support with async options
- Circuit breaker pattern for enhanced reliability
- Built on top of Undertow for maximum performance
- Object pooling for reduced GC pressure

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven

### Building the Project

```bash
mvn clean package
```

### Basic Usage

Create a simple API server with just a few lines of code:

```java
public class ExampleApp {
    public static void main(String[] args) {
        // Create the application
        Blyfast app = new Blyfast();

        // Add global middleware for all routes
        app.use(CommonMiddleware.logger());
        app.use(CommonMiddleware.responseTime());
        
        // Define routes
        app.get("/hello", ctx -> {
            ctx.json(Map.of("message", "Hello, World!"));
        });
        
        // Start the server
        app.port(8080).listen();
        System.out.println("Server started on http://localhost:8080");
    }
}
```

## Middleware

Middleware are functions that have access to the request and response objects and can process HTTP requests in the application's request-response cycle. Middleware can execute code, make changes to the request and response objects, end the request-response cycle, and call the next middleware in the stack.

### Understanding Middleware

In BlyFast, middleware is defined by the `Middleware` functional interface:

```java
@FunctionalInterface
public interface Middleware {
    /**
     * Process the HTTP request.
     *
     * @param ctx the context
     * @return true if the next middleware should be executed, false to end the chain
     */
    boolean handle(Context ctx);
}
```

Each middleware receives a context object containing both request and response, and returns a boolean indicating whether the next middleware should be executed.

### Using Middleware

You can add middleware to your application in several ways:

1. **Global middleware** - Applied to all routes:

```java
// Log all requests
app.use(ctx -> {
    System.out.println("Request received: " + ctx.request().getMethod() + " " + ctx.request().getPath());
    return true;
});
```

2. **Route-specific middleware** - Applied only to specific routes:

```java
// Authentication middleware for a specific route
app.get("/admin", ctx -> {
    String token = ctx.header("Authorization");
    if (token == null || !isValidToken(token)) {
        ctx.status(401).json(Map.of("error", "Unauthorized"));
        return false; // Stop middleware chain
    }
    return true;
}, ctx -> {
    ctx.json(Map.of("message", "Admin dashboard"));
});
```

3. **Named middleware** - Defined once and reused:

```java
// Define authentication middleware
Middleware auth = ctx -> {
    String token = ctx.header("Authorization");
    if (token == null || !isValidToken(token)) {
        ctx.status(401).json(Map.of("error", "Unauthorized"));
        return false; // Stop middleware chain
    }
    return true; // Continue to next middleware
};

// Use in multiple routes
app.get("/admin", auth, ctx -> {
    ctx.json(Map.of("message", "Admin dashboard"));
});

app.get("/profile", auth, ctx -> {
    ctx.json(Map.of("message", "User profile"));
});
```

### Common Middleware Examples

BlyFast includes several built-in middleware functions in the `CommonMiddleware` class:

- **logger()**: Logs request details
- **responseTime()**: Adds response time metrics
- **cors()**: Handles Cross-Origin Resource Sharing
- **securityHeaders()**: Adds security-related headers
- **bodyParser()**: Parses request bodies for various content types
- **errorHandler()**: Catches exceptions and returns appropriate responses

## Plugins

Plugins are more complex components that extend the framework's functionality. They encapsulate related features and can include configuration options, multiple middlewares, and additional functionality.

### Understanding Plugins

In BlyFast, plugins implement the `Plugin` interface:

```java
public interface Plugin {
    /**
     * Gets the name of the plugin.
     * 
     * @return the plugin name
     */
    String getName();

    /**
     * Gets the version of the plugin.
     * 
     * @return the plugin version
     */
    String getVersion();

    /**
     * Registers the plugin with the Blyfast application.
     * 
     * @param app the Blyfast application
     */
    void register(Blyfast app);
}
```

The framework provides an `AbstractPlugin` base class that simplifies plugin creation.

### Built-in Plugins

BlyFast includes several built-in plugins:

1. **JWT Authentication** - Handles JSON Web Token authentication
2. **CORS** - Manages Cross-Origin Resource Sharing
3. **Rate Limiting** - Limits request rates to protect your API
4. **Compression** - Compresses HTTP responses for better performance
5. **Exception Handler** - Advanced error handling and reporting
6. **Monitor** - Performance monitoring and metrics collection

### Using Plugins

Plugins are registered with the application:

```java
// Create and configure the CORS plugin
CorsPlugin corsPlugin = new CorsPlugin();
corsPlugin.getConfig()
    .addAllowOrigin("https://example.com")
    .addAllowMethod("GET, POST, PUT, DELETE")
    .addAllowHeader("Content-Type, Authorization")
    .setMaxAge(86400);

// Register the plugin
app.register(corsPlugin);

// JWT Authentication plugin
JwtPlugin jwtPlugin = new JwtPlugin("your-secure-jwt-secret-key");
jwtPlugin.getConfig()
    .setIssuer("blyfast-api");

app.register(jwtPlugin);

// Protect routes with JWT middleware
Middleware jwtProtect = jwtPlugin.protect();
app.get("/protected", jwtProtect, ctx -> {
    // This route is protected by JWT authentication
    ctx.json(Map.of("message", "Protected route"));
});
```

### Creating Custom Plugins

To create a custom plugin, extend the `AbstractPlugin` class:

```java
public class MyCustomPlugin extends AbstractPlugin {
    private MyCustomConfig config;

    public MyCustomPlugin() {
        super("my-custom-plugin", "1.0.0");
        this.config = new MyCustomConfig();
    }

    @Override
    public void register(Blyfast app) {
        logger.info("Registering MyCustomPlugin");
        app.set("my-custom-plugin", this);

        // Add global middleware if configured
        if (config.isEnableGlobal()) {
            app.use(createMiddleware());
        }
    }

    public Middleware createMiddleware() {
        return ctx -> {
            // Implement your middleware logic here
            return true; // Continue to next middleware
        };
    }
    
    public MyCustomConfig getConfig() {
        return config;
    }

    // Additional methods and inner classes as needed
}
```

## Advanced Features

### Thread Pool Configuration

BlyFast includes a highly optimized thread pool for handling HTTP requests. You can configure it to match your specific workload:

```java
ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
    .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 16)
    .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 32)
    .setQueueCapacity(500000)
    .setPrestartCoreThreads(true)
    .setUseSynchronousQueue(false)
    .setEnableDynamicScaling(true);

Blyfast app = new Blyfast(config);
```

### Configuration Options

- **Core Pool Size**: Number of threads to keep in the pool, even if idle
- **Max Pool Size**: Maximum number of threads allowed in the pool
- **Queue Capacity**: Size of the work queue
- **Keep Alive Time**: How long idle threads should be kept alive
- **Use Work Stealing**: Whether to use a work-stealing pool for better load balancing
- **Use Synchronous Queue**: Whether to use a synchronous handoff queue instead of a bounded queue
- **Prestart Core Threads**: Whether to prestart all core threads to eliminate warmup time
- **Enable Dynamic Scaling**: Automatically adjust thread counts based on workload
- **Adaptive Queue**: Dynamically adjust queue size based on demand

### Circuit Breaker Pattern

BlyFast supports the circuit breaker pattern to enhance system reliability:

```java
app.circuitBreaker(true)
   .circuitBreakerThreshold(50)
   .circuitBreakerResetTimeout(30000);
```

When the error threshold is exceeded, the circuit opens and fast-fails requests until the reset timeout expires, protecting downstream systems.

### Object Pooling

BlyFast uses object pooling to reduce garbage collection pressure:

```java
// Enable or disable object pooling (enabled by default)
Blyfast app = new Blyfast(true);

// Configure adaptive pool sizing
app.adaptivePoolSizing(true);
app.poolSize(2000);
```

Object pooling reuses request, response, and context objects to minimize object creation and reduce GC pauses.

### Async Middleware

For non-blocking operations, BlyFast supports asynchronous middleware execution:

```java
app.asyncMiddleware(true);

// Use async middleware
app.use(ctx -> {
    CompletableFuture.runAsync(() -> {
        // Perform async operation
    });
    return true;
});
```

## Performance Benchmarks

BlyFast is designed for maximum performance. The `ThreadPoolBenchmark` class demonstrates the performance of different thread pool configurations:

```bash
mvn exec:java -Dexec.mainClass="com.blyfast.example.ThreadPoolBenchmark"
```

This benchmark compares:
1. Default thread pool configuration
2. Optimized thread pool configuration 
3. Work-stealing thread pool configuration

## Example Applications

The framework includes several example applications to help you get started:

### Basic Example

```java
// Simple API with basic routes
mvn exec:java -Dexec.mainClass="com.blyfast.example.ExampleApp"
```

### Plugin Example

```java
// Demonstrates how to use various plugins
mvn exec:java -Dexec.mainClass="com.blyfast.example.PluginExampleApp"
```

### JWT and CORS Example

```java
// Demonstrates JWT authentication and CORS configuration
mvn exec:java -Dexec.mainClass="com.blyfast.example.JwtCorsExampleApp"
```

### File System Example

```java
// Demonstrates serving static files
mvn exec:java -Dexec.mainClass="com.blyfast.example.FileSystemExample"
```

### Benchmark Application

```java
// Performance benchmarking application
mvn exec:java -Dexec.mainClass="com.blyfast.example.BenchApp"
```

## Performance Tuning Tips

For maximum performance:

1. **Adjust Thread Pool Size**: Set core threads to CPU cores × 16 and max threads to CPU cores × 32
2. **Prestart Core Threads**: Eliminate thread creation overhead on first requests
3. **Use Work Stealing for CPU-bound Tasks**: Better load balancing for CPU-intensive operations
4. **Use Bounded Queue for I/O-bound Tasks**: Better throughput for I/O-bound operations
5. **Tune Queue Capacity**: Higher values (500000+) for high-throughput applications
6. **Enable Dynamic Scaling**: Let the framework optimize thread counts automatically
7. **Disable Metrics Collection in Production**: Reduce overhead if metrics aren't needed

## License

This project is licensed under the MIT License - see the LICENSE file for details. 