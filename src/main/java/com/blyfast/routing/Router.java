package com.blyfast.routing;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Manages routes and matches incoming requests to the appropriate handler.
 * Optimized for high-performance route matching.
 */
public class Router {
    // Store routes by HTTP method for faster lookups (case-insensitive)
    private final Map<String, List<Route>> routesByMethod;
    
    // Static routes by exact path match
    private final Map<String, Map<String, Route>> staticRoutes;
    
    // All routes for introspection
    private final List<Route> allRoutes;

    /**
     * Creates a new Router instance.
     */
    public Router() {
        this.routesByMethod = new HashMap<>();
        this.staticRoutes = new HashMap<>();
        this.allRoutes = new ArrayList<>();
    }

    /**
     * Adds a route to the router.
     *
     * @param method  the HTTP method
     * @param path    the route path
     * @param handler the handler function
     * @return the created route for further customization
     */
    public Route addRoute(String method, String path, Blyfast.Handler handler) {
        // Keep original method case for the Route object
        Route route = new Route(method, path, handler);
        
        // Add to the all-routes list
        allRoutes.add(route);
        
        // Add to the method-specific list (case-insensitive key)
        String methodKey = method.toUpperCase();
        routesByMethod.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(route);
        
        // If it's a static route (no parameters), add to static routes map for O(1) lookup
        if (!path.contains(":") && !path.contains("*")) {
            staticRoutes.computeIfAbsent(methodKey, k -> new HashMap<>()).put(path, route);
        }
        
        return route;
    }

    /**
     * Adds a route for the GET method.
     *
     * @param path    the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route get(String path, Blyfast.Handler handler) {
        return addRoute("GET", path, handler);
    }

    /**
     * Adds a route for the POST method.
     *
     * @param path    the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route post(String path, Blyfast.Handler handler) {
        return addRoute("POST", path, handler);
    }

    /**
     * Adds a route for the PUT method.
     *
     * @param path    the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route put(String path, Blyfast.Handler handler) {
        return addRoute("PUT", path, handler);
    }

    /**
     * Adds a route for the DELETE method.
     *
     * @param path    the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route delete(String path, Blyfast.Handler handler) {
        return addRoute("DELETE", path, handler);
    }

    /**
     * Finds a route that matches the given method and path.
     * Optimized for performance with O(1) lookups for static routes.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @return the matching route or null if none matches
     */
    public Route findRoute(String method, String path) {
        String methodKey = method.toUpperCase();
        
        // First, try exact match for static routes (O(1) lookup)
        Map<String, Route> methodStaticRoutes = staticRoutes.get(methodKey);
        if (methodStaticRoutes != null) {
            Route staticRoute = methodStaticRoutes.get(path);
            if (staticRoute != null) {
                return staticRoute;
            }
        }
        
        // If no static route match, check dynamic routes
        List<Route> methodRoutes = routesByMethod.get(methodKey);
        if (methodRoutes != null) {
            for (Route route : methodRoutes) {
                // Use Route's matches method which handles case-insensitive method comparison
                if (route.matches(method, path)) {
                    return route;
                }
            }
        }
        
        return null;
    }

    /**
     * Resolves the path parameters for a request based on the matching route.
     * Validates path parameters to prevent path traversal attacks.
     *
     * @param request the request
     * @param route   the matching route
     */
    public void resolveParams(Request request, Route route) {
        String path = request.getPath();
        Matcher matcher = route.getPattern().matcher(path);

        if (matcher.matches()) {
            List<String> paramNames = route.getParamNames();

            // Extract parameter values from the matcher groups
            for (int i = 0; i < paramNames.size(); i++) {
                // Groups are 1-indexed, with group 0 being the entire match
                String value = matcher.group(i + 1);
                if (value != null) {
                    // Validate path parameter to prevent path traversal attacks
                    String sanitizedValue = sanitizePathParameter(value);
                    if (sanitizedValue == null) {
                        // Invalid path parameter detected, log and skip
                        continue;
                    }
                    String paramName = paramNames.get(i);
                    request.setPathParam(paramName, sanitizedValue);
                }
            }
        }
    }
    
    /**
     * Sanitizes a path parameter value to prevent path traversal attacks.
     * 
     * @param value the path parameter value to sanitize
     * @return the sanitized value, or null if the value is invalid
     */
    private String sanitizePathParameter(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Check for path traversal patterns
        if (value.contains("..") || value.contains("//") || value.contains("\\")) {
            return null; // Reject potentially dangerous values
        }
        
        // Check for absolute paths
        if (value.startsWith("/") || value.startsWith("\\")) {
            return null; // Reject absolute paths
        }
        
        // Check for control characters and other dangerous characters
        for (char c : value.toCharArray()) {
            if (Character.isISOControl(c) || c == '\0') {
                return null; // Reject control characters
            }
        }
        
        return value;
    }

    /**
     * Gets all routes.
     *
     * @return the list of routes
     */
    public List<Route> getRoutes() {
        return new ArrayList<>(allRoutes);
    }
}