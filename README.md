# BlyFast

A blazingly fast Java framework for building REST APIs, inspired by GoFiber and Express.js.

## Features

- Minimalist design with a fluent API
- Fast request processing with minimal overhead
- Middleware support for cross-cutting concerns
- Convenient route handling with path parameters
- JSON processing built-in
- Simple deployment

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven

### Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.blyfast</groupId>
    <artifactId>blyfast</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Quick Start

```java
import com.blyfast.core.Blyfast;

public class HelloWorld {
    public static void main(String[] args) {
        Blyfast app = new Blyfast();
        
        app.get("/", ctx -> ctx.json("{\"message\": \"Hello, World!\"}"));
        
        app.listen();
        System.out.println("Server started on http://localhost:8080");
    }
}
```

## Examples

### Route Parameters

```java
app.get("/users/:id", ctx -> {
    String id = ctx.param("id");
    ctx.json("{\"id\": \"" + id + "\", \"name\": \"John Doe\"}");
});
```

### Query Parameters

```java
app.get("/search", ctx -> {
    String query = ctx.query("q");
    ctx.json("{\"query\": \"" + query + "\", \"results\": []}");
});
```

### JSON Handling

```java
app.post("/users", ctx -> {
    User user = ctx.parseBody(User.class);
    // Process the user...
    ctx.status(201).json(user);
});
```

### Middleware

```java
// Global middleware
app.use(ctx -> {
    System.out.println("Request: " + ctx.request().getMethod() + " " + ctx.request().getPath());
    return true; // Continue processing
});

// Built-in middleware
app.use(CommonMiddleware.logger());
app.use(CommonMiddleware.cors());
```

### Error Handling

```java
app.get("/error", ctx -> {
    try {
        // Some operation that might fail
        throw new RuntimeException("Something went wrong");
    } catch (Exception e) {
        ctx.status(500).json("{\"error\": \"" + e.getMessage() + "\"}");
    }
});
```

## API Reference

### Blyfast

- `Blyfast()` - Creates a new application instance
- `get(path, handler)` - Adds a GET route
- `post(path, handler)` - Adds a POST route
- `put(path, handler)` - Adds a PUT route
- `delete(path, handler)` - Adds a DELETE route
- `route(method, path, handler)` - Adds a route with a custom method
- `use(middleware)` - Adds global middleware
- `host(host)` - Sets the host to bind to
- `port(port)` - Sets the port to listen on
- `listen()` - Starts the server
- `stop()` - Stops the server

### Context

- `request()` - Gets the request object
- `response()` - Gets the response object
- `param(name)` - Gets a path parameter
- `query(name)` - Gets a query parameter
- `header(name)` - Gets a request header
- `body()` - Gets the request body as a string
- `json()` - Gets the request body as JSON
- `parseBody(class)` - Parses the request body into an object
- `status(code)` - Sets the response status code
- `header(name, value)` - Sets a response header
- `type(contentType)` - Sets the Content-Type header
- `send(text)` - Sends a text response
- `json(obj)` - Sends a JSON response
- `error(status, message)` - Sends an error response
- `redirect(url)` - Redirects to a URL

## License

This project is licensed under the MIT License - see the LICENSE file for details. 