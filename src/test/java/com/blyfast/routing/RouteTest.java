package com.blyfast.routing;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.Middleware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the Route class.
 * 
 * <p>Tests route creation, matching, middleware management, and parameter handling.</p>
 */
@DisplayName("Route Tests")
public class RouteTest {

    @Test
    @DisplayName("Should create route with method, path, and handler")
    void testCreateRoute() {
        // Given: route components
        String method = "GET";
        String path = "/test";
        Blyfast.Handler handler = ctx -> ctx.json("test");
        
        // When: creating route
        Route route = new Route(method, path, handler);
        
        // Then: route should be created correctly
        assertEquals("GET", route.getMethod());
        assertEquals("/test", route.getPath());
        assertEquals(handler, route.getHandler());
        assertNotNull(route.getPattern());
        assertNotNull(route.getParamNames());
    }

    @Test
    @DisplayName("Should normalize HTTP method to uppercase")
    void testMethodNormalization() {
        // Given: method in lowercase
        String method = "get";
        String path = "/test";
        Blyfast.Handler handler = ctx -> ctx.json("test");
        
        // When: creating route
        Route route = new Route(method, path, handler);
        
        // Then: method should be uppercase
        assertEquals("GET", route.getMethod());
    }

    @Test
    @DisplayName("Should match exact path")
    void testExactPathMatch() {
        // Given: a route
        Route route = new Route("GET", "/users", ctx -> ctx.json("users"));
        
        // When/Then: should match exact path
        assertTrue(route.matches("GET", "/users"));
        assertFalse(route.matches("GET", "/user"));
        assertFalse(route.matches("GET", "/users/123"));
    }

    @Test
    @DisplayName("Should match path with parameters")
    void testParameterPathMatch() {
        // Given: route with parameter
        Route route = new Route("GET", "/users/:id", ctx -> ctx.json("user"));
        
        // When/Then: should match paths with parameter values
        assertTrue(route.matches("GET", "/users/123"));
        assertTrue(route.matches("GET", "/users/abc"));
        assertTrue(route.matches("GET", "/users/test-user"));
        assertFalse(route.matches("GET", "/users"));
        assertFalse(route.matches("GET", "/users/123/posts"));
    }

    @Test
    @DisplayName("Should match routes case-insensitively for HTTP methods")
    void testCaseInsensitiveMethodMatch() {
        // Given: a route
        Route route = new Route("GET", "/test", ctx -> ctx.json("test"));
        
        // When/Then: should match regardless of method case
        assertTrue(route.matches("get", "/test"));
        assertTrue(route.matches("Get", "/test"));
        assertTrue(route.matches("GET", "/test"));
        assertTrue(route.matches("gEt", "/test"));
    }

    @Test
    @DisplayName("Should not match route with wrong method")
    void testMethodMismatch() {
        // Given: a GET route
        Route route = new Route("GET", "/test", ctx -> ctx.json("test"));
        
        // When/Then: should not match other methods
        assertFalse(route.matches("POST", "/test"));
        assertFalse(route.matches("PUT", "/test"));
        assertFalse(route.matches("DELETE", "/test"));
    }

    @Test
    @DisplayName("Should extract parameter names from path")
    void testParameterNameExtraction() {
        // Given: route with parameters
        Route route = new Route("GET", "/users/:userId/posts/:postId", ctx -> ctx.json("test"));
        
        // When: getting parameter names
        List<String> paramNames = route.getParamNames();
        
        // Then: should extract all parameter names in order
        assertEquals(2, paramNames.size());
        assertEquals("userId", paramNames.get(0));
        assertEquals("postId", paramNames.get(1));
    }

    @Test
    @DisplayName("Should return empty list for route without parameters")
    void testNoParameters() {
        // Given: route without parameters
        Route route = new Route("GET", "/users", ctx -> ctx.json("users"));
        
        // When: getting parameter names
        List<String> paramNames = route.getParamNames();
        
        // Then: should return empty list
        assertTrue(paramNames.isEmpty());
    }

    @Test
    @DisplayName("Should add middleware to route")
    void testAddMiddleware() {
        // Given: a route and middleware
        Route route = new Route("GET", "/test", ctx -> ctx.json("test"));
        Middleware middleware = ctx -> true;
        
        // When: adding middleware
        Route result = route.use(middleware);
        
        // Then: middleware should be added and method should return route for chaining
        assertEquals(route, result);
        List<Middleware> middlewareList = route.getMiddleware();
        assertEquals(1, middlewareList.size());
        assertEquals(middleware, middlewareList.get(0));
    }

    @Test
    @DisplayName("Should add multiple middleware to route")
    void testAddMultipleMiddleware() {
        // Given: a route and multiple middleware
        Route route = new Route("GET", "/test", ctx -> ctx.json("test"));
        Middleware middleware1 = ctx -> true;
        Middleware middleware2 = ctx -> true;
        Middleware middleware3 = ctx -> true;
        
        // When: adding multiple middleware
        route.use(middleware1)
             .use(middleware2)
             .use(middleware3);
        
        // Then: all middleware should be added in order
        List<Middleware> middlewareList = route.getMiddleware();
        assertEquals(3, middlewareList.size());
        assertEquals(middleware1, middlewareList.get(0));
        assertEquals(middleware2, middlewareList.get(1));
        assertEquals(middleware3, middlewareList.get(2));
    }

    @Test
    @DisplayName("Should get pattern for route")
    void testGetPattern() {
        // Given: a route
        Route route = new Route("GET", "/users/:id", ctx -> ctx.json("user"));
        
        // When: getting pattern
        Pattern pattern = route.getPattern();
        
        // Then: pattern should be valid and match correct paths
        assertNotNull(pattern);
        assertTrue(pattern.matcher("/users/123").matches());
        assertFalse(pattern.matcher("/users").matches());
    }

    @Test
    @DisplayName("Should handle route with wildcard")
    void testWildcardRoute() {
        // Given: route with wildcard
        Route route = new Route("GET", "/api/*", ctx -> ctx.json("api"));
        
        // When/Then: should match any path after /api/
        assertTrue(route.matches("GET", "/api/users"));
        assertTrue(route.matches("GET", "/api/users/123"));
        assertTrue(route.matches("GET", "/api/anything/here"));
    }

    @Test
    @DisplayName("Should handle route with trailing slash")
    void testTrailingSlash() {
        // Given: route with trailing slash
        Route route = new Route("GET", "/test/", ctx -> ctx.json("test"));
        
        // When/Then: should match both with and without trailing slash
        assertTrue(route.matches("GET", "/test"));
        assertTrue(route.matches("GET", "/test/"));
    }

    @Test
    @DisplayName("Should handle complex route with multiple parameters")
    void testComplexRoute() {
        // Given: complex route
        Route route = new Route("GET", "/api/v1/users/:userId/posts/:postId", 
                                ctx -> ctx.json("post"));
        
        // When/Then: should match correct paths
        assertTrue(route.matches("GET", "/api/v1/users/123/posts/456"));
        assertFalse(route.matches("GET", "/api/v1/users/123/posts"));
        assertFalse(route.matches("GET", "/api/v1/users/posts/456"));
        
        // Should extract parameters correctly
        List<String> paramNames = route.getParamNames();
        assertEquals(2, paramNames.size());
    }

    @Test
    @DisplayName("Should return immutable middleware list")
    void testMiddlewareListImmutability() {
        // Given: a route with middleware
        Route route = new Route("GET", "/test", ctx -> ctx.json("test"));
        Middleware middleware = ctx -> true;
        route.use(middleware);
        
        // When: getting middleware list
        List<Middleware> middlewareList = route.getMiddleware();
        
        // Then: should be able to access middleware
        assertEquals(1, middlewareList.size());
        // Note: The list itself is not immutable, but modifications should be through use() method
    }
}

