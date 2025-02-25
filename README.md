# BlyFast Framework

A blazingly fast Java framework for REST APIs, inspired by GoFiber and Express.js.

## Features

- Extremely fast HTTP request handling with optimized thread pool
- Simple and intuitive API for defining routes and middleware
- Plugin system for extending functionality
- JSON processing with Jackson
- Path parameter support
- Middleware support
- Built on top of Undertow for maximum performance

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven

### Building the Project

```bash
mvn clean package
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
    return true; // Continue to next middleware
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
    return true; // Continue to next middleware
}, ctx -> {
    ctx.json(Map.of("message", "Admin dashboard"));
    return true;
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
    return true;
});

app.get("/profile", auth, ctx -> {
    ctx.json(Map.of("message", "User profile"));
    return true;
});
```

### Common Middleware Examples

- **Logging middleware**: Logs request details
- **Authentication middleware**: Verifies user credentials
- **Error handling middleware**: Catches exceptions and returns appropriate responses
- **Request parsing middleware**: Parses request bodies
- **Response transformation middleware**: Transforms response data

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
3. **Rate Limiting** - Limits request rates
4. **Compression** - Compresses HTTP responses

### Using Plugins

Plugins are registered with the application and often create middlewares that are added to the middleware chain:

```java
// Create and configure the CORS plugin
CorsPlugin corsPlugin = new CorsPlugin(new CorsConfig()
    .setAllowOrigin("https://example.com")
    .setAllowMethods("GET, POST, PUT, DELETE")
    .setAllowHeaders("Content-Type, Authorization")
    .setMaxAge(86400));

// Register the plugin
app.register(corsPlugin);

// JWT Authentication plugin
JwtPlugin jwtPlugin = new JwtPlugin(new JwtConfig()
    .setSecret("your-jwt-secret")
    .setIssuer("blyfast-api"));

app.register(jwtPlugin);

// Protect routes with JWT middleware
app.get("/protected", jwtPlugin.createMiddleware(), ctx -> {
    // This route is protected by JWT authentication
    ctx.json(Map.of("message", "Protected route"));
    return true;
});
```

### Compression Plugin Example

The Compression plugin provides response compression capabilities:

```java
// Create with default configuration
CompressionPlugin compressionPlugin = new CompressionPlugin();

// Or with custom configuration
CompressionPlugin compressionPlugin = new CompressionPlugin(new CompressionConfig()
    .setEnableGzip(true)
    .setEnableDeflate(false)
    .setMinLength(512)
    .addExcludedPath(".*/images/.*")
    .addIncludedContentType("application/json"));

// Register the plugin
app.register(compressionPlugin);

// Apply compression to specific routes if global is disabled
app.get("/api/data", compressionPlugin.createMiddleware(), ctx -> {
    // Response will be compressed
    ctx.json(largeDataObject);
    return true;
});
```

### Creating Custom Plugins

To create a custom plugin, extend the `AbstractPlugin` class:

```java
public class MyCustomPlugin extends AbstractPlugin {
    private final MyCustomConfig config;

    public MyCustomPlugin(MyCustomConfig config) {
        super("my-custom-plugin", "1.0.0");
        this.config = config;
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

    // Additional methods and inner classes as needed
}
```

### Plugin vs Middleware: Key Differences

| Aspect | Middleware | Plugin |
|--------|------------|--------|
| Complexity | Simple function | Complex component |
| State | Typically stateless | Can maintain state |
| Configuration | Limited/none | Often configurable |
| Registration | `app.use()` | `app.register()` |
| Scope | Either global or route-specific | Can provide both |
| Implementation | Functional interface | Class implementation |
| Reusability | Simple reuse | Creates reusable middleware |
| Purpose | Request/response processing | Framework extension |

## Running Tests

BlyFast includes a comprehensive test suite to ensure everything works correctly. To run the tests:

```bash
mvn test
```

This will run all the JUnit tests, including:
- Core functionality tests
- Thread pool tests
- Plugin tests

## Performance Benchmarks

### Running the Thread Pool Benchmark

The `ThreadPoolBenchmark` class demonstrates the performance of different thread pool configurations:

```bash
mvn exec:java -Dexec.mainClass="com.blyfast.example.ThreadPoolBenchmark"
```

This benchmark compares:
1. Default thread pool configuration
2. Optimized thread pool configuration
3. Work-stealing thread pool configuration

### Comparing with Spring Boot

To compare BlyFast performance with Spring Boot:

1. Run the BlyFast comparison server:

```bash
mvn exec:java -Dexec.mainClass="com.blyfast.example.SpringComparisonBenchmark"
```

2. Create a Spring Boot application with equivalent endpoints (example provided in the console output)

3. Run the Spring Boot application on port 8081

4. The benchmark will automatically run when both servers are detected

## Example Applications

### Plugin Example

The `PluginExampleApp` demonstrates how to use various plugins:

```bash
mvn exec:java -Dexec.mainClass="com.blyfast.example.PluginExampleApp"
```

### JWT and CORS Example

The `JwtCorsExampleApp` provides a focused example of JWT authentication and CORS configuration:

```bash
mvn exec:java -Dexec.mainClass="com.blyfast.example.JwtCorsExampleApp"
```

This example demonstrates:
- Setting up JWT authentication with custom configuration
- Implementing login functionality with JWT token generation
- Creating protected routes with role-based access control
- Configuring CORS for secure cross-origin requests
- Testing endpoints with curl commands

## Thread Pool Configuration

BlyFast includes a highly optimized thread pool for handling HTTP requests. You can configure it to match your specific workload:

```java
ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
    .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2)
    .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4)
    .setQueueCapacity(10000)
    .setPrestartCoreThreads(true)
    .setUseSynchronousQueue(false);

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
- **Caller Runs When Rejected**: Whether to execute tasks in the caller's thread when the queue is full

## Performance Tuning Tips

For maximum performance:

1. **Adjust Thread Pool Size**: Set core threads to CPU cores × 2 and max threads to CPU cores × 4
2. **Prestart Core Threads**: Eliminate thread creation overhead on first requests
3. **Use Work Stealing for CPU-bound Tasks**: Better load balancing for CPU-intensive operations
4. **Use Bounded Queue for I/O-bound Tasks**: Better throughput for I/O-bound operations
5. **Tune Queue Capacity**: Match your expected concurrent request volume
6. **Disable Metrics Collection in Production**: Reduce overhead if metrics aren't needed

## License

This project is licensed under the MIT License - see the LICENSE file for details. 