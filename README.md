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

This example includes:
- JWT authentication
- CORS handling
- Rate limiting
- Response compression

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