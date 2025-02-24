package com.blyfast.core;

import com.blyfast.middleware.Middleware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the BlyFast framework core functionality.
 */
public class BlyFastTest {

    private static final int TEST_PORT = 8888;
    private Blyfast app;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Create a new BlyFast application for each test
        ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                .setCorePoolSize(4)
                .setMaxPoolSize(8)
                .setQueueCapacity(100);

        app = new Blyfast(config);

        // Create an HTTP client for testing
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    public void tearDown() {
        // Stop the server after each test
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void testBasicRouting() throws Exception {
        // Define a simple route
        app.get("/test", ctx -> ctx.json("{ \"message\": \"Hello, World!\" }"));

        // Start the server
        app.port(TEST_PORT).listen();

        // Send a request to the server
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify the response
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Hello, World!"));
    }

    @Test
    public void testPathParameters() throws Exception {
        // Define a route with path parameters
        app.get("/users/:id", ctx -> {
            String id = ctx.param("id");
            ctx.json("{ \"id\": \"" + id + "\", \"name\": \"User " + id + "\" }");
        });

        // Start the server
        app.port(TEST_PORT).listen();

        // Send a request to the server
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users/123"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify the response
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":\"123\""));
        assertTrue(response.body().contains("\"name\":\"User 123\""));
    }

    @Test
    public void testMiddleware() throws Exception {
        // Create a middleware that adds a header
        AtomicBoolean middlewareExecuted = new AtomicBoolean(false);

        Middleware testMiddleware = ctx -> {
            middlewareExecuted.set(true);
            ctx.header("X-Test", "middleware-executed");
            return true;
        };

        // Add the middleware and define a route
        app.use(testMiddleware);
        app.get("/middleware-test", ctx -> ctx.json("{ \"message\": \"Middleware Test\" }"));

        // Start the server
        app.port(TEST_PORT).listen();

        // Send a request to the server
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/middleware-test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify the response
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Middleware Test"));
        assertEquals("middleware-executed", response.headers().firstValue("X-Test").orElse(null));
        assertTrue(middlewareExecuted.get());
    }

    @Test
    public void testMiddlewareChainInterruption() throws Exception {
        // Create a middleware that interrupts the chain
        Middleware blockingMiddleware = ctx -> {
            ctx.status(403).json("{ \"error\": \"Access Denied\" }");
            return false; // Interrupt the chain
        };

        // Add the middleware and define a route
        app.use(blockingMiddleware);
        app.get("/protected", ctx -> ctx.json("{ \"message\": \"This should not be reached\" }"));

        // Start the server
        app.port(TEST_PORT).listen();

        // Send a request to the server
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/protected"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify the response
        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("Access Denied"));
        assertFalse(response.body().contains("This should not be reached"));
    }

    @Test
    public void testNotFound() throws Exception {
        // Define a route
        app.get("/exists", ctx -> ctx.json("{ \"message\": \"This route exists\" }"));

        // Start the server
        app.port(TEST_PORT).listen();

        // Send a request to a non-existent route
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/does-not-exist"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify the response
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Not Found"));
    }

    @Test
    public void testDifferentHttpMethods() throws Exception {
        // Define routes for different HTTP methods
        app.get("/methods", ctx -> ctx.json("{ \"method\": \"GET\" }"));
        app.post("/methods", ctx -> ctx.json("{ \"method\": \"POST\" }"));
        app.put("/methods", ctx -> ctx.json("{ \"method\": \"PUT\" }"));
        app.delete("/methods", ctx -> ctx.json("{ \"method\": \"DELETE\" }"));

        // Start the server
        app.port(TEST_PORT).listen();

        // Test GET
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/methods"))
                .GET()
                .build();

        HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        assertTrue(getResponse.body().contains("\"method\":\"GET\""));

        // Test POST
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/methods"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postResponse.statusCode());
        assertTrue(postResponse.body().contains("\"method\":\"POST\""));

        // Test PUT
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/methods"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode());
        assertTrue(putResponse.body().contains("\"method\":\"PUT\""));

        // Test DELETE
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/methods"))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResponse.statusCode());
        assertTrue(deleteResponse.body().contains("\"method\":\"DELETE\""));
    }

    @Test
    public void testConcurrentRequests() throws Exception {
        // Define a route that simulates some processing time
        app.get("/concurrent", ctx -> {
            try {
                // Simulate some processing time
                Thread.sleep(50);
                ctx.json("{ \"message\": \"Processed\" }");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.status(500).json("{ \"error\": \"Processing interrupted\" }");
            }
        });

        // Start the server
        app.port(TEST_PORT).listen();

        // Send multiple concurrent requests
        int numRequests = 50;
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numRequests; i++) {
            new Thread(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + TEST_PORT + "/concurrent"))
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200 && response.body().contains("Processed")) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException | InterruptedException e) {
                    // Count as failure
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all requests to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Verify that all requests were successful
        assertEquals(numRequests, successCount.get());
    }

    @Test
    public void testThreadPoolPerformance() throws Exception {
        // Define a CPU-intensive route
        app.get("/cpu-intensive", ctx -> {
            // Perform a CPU-intensive operation
            long result = 0;
            for (int i = 0; i < 1000000; i++) {
                result += i;
            }
            ctx.json("{ \"result\": " + result + " }");
        });

        // Start the server
        app.port(TEST_PORT).listen();

        // Send multiple concurrent requests
        int numRequests = 20;
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < numRequests; i++) {
            new Thread(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + TEST_PORT + "/cpu-intensive"))
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200 && response.body().contains("result")) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException | InterruptedException e) {
                    // Count as failure
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all requests to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS));

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

        // Verify that all requests were successful
        assertEquals(numRequests, successCount.get());

        // Print performance metrics
        System.out.println("Thread pool performance test:");
        System.out.println("  Completed " + numRequests + " CPU-intensive requests in " + elapsedSeconds + " seconds");
        System.out.println("  Throughput: " + (numRequests / elapsedSeconds) + " requests/second");
        System.out.println("  Thread pool stats:");
        System.out.println("    Tasks submitted: " + app.getThreadPool().getTasksSubmitted());
        System.out.println("    Tasks completed: " + app.getThreadPool().getTasksCompleted());
        System.out.println("    Average execution time: " +
                (app.getThreadPool().getAverageExecutionTime() / 1_000_000.0) + " ms");
    }
}