package com.blyfast.routing;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Manages routes and matches incoming requests to the appropriate handler.
 */
public class Router {
    private final List<Route> routes;

    /**
     * Creates a new Router instance.
     */
    public Router() {
        this.routes = new ArrayList<>();
    }

    /**
     * Adds a route to the router.
     *
     * @param method the HTTP method
     * @param path the route path
     * @param handler the handler function
     * @return the created route for further customization
     */
    public Route addRoute(String method, String path, Blyfast.Handler handler) {
        Route route = new Route(method, path, handler);
        routes.add(route);
        return route;
    }

    /**
     * Adds a route for the GET method.
     *
     * @param path the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route get(String path, Blyfast.Handler handler) {
        return addRoute("GET", path, handler);
    }

    /**
     * Adds a route for the POST method.
     *
     * @param path the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route post(String path, Blyfast.Handler handler) {
        return addRoute("POST", path, handler);
    }

    /**
     * Adds a route for the PUT method.
     *
     * @param path the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route put(String path, Blyfast.Handler handler) {
        return addRoute("PUT", path, handler);
    }

    /**
     * Adds a route for the DELETE method.
     *
     * @param path the route path
     * @param handler the handler function
     * @return the created route
     */
    public Route delete(String path, Blyfast.Handler handler) {
        return addRoute("DELETE", path, handler);
    }

    /**
     * Finds a route that matches the given method and path.
     *
     * @param method the HTTP method
     * @param path the request path
     * @return the matching route or null if none matches
     */
    public Route findRoute(String method, String path) {
        for (Route route : routes) {
            if (route.matches(method, path)) {
                return route;
            }
        }
        return null;
    }

    /**
     * Resolves the path parameters for a request based on the matching route.
     *
     * @param request the request
     * @param route the matching route
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
                request.setPathParam(paramNames.get(i), value);
            }
        }
    }

    /**
     * Gets all routes.
     *
     * @return the list of routes
     */
    public List<Route> getRoutes() {
        return new ArrayList<>(routes);
    }
} 