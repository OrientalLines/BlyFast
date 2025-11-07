package com.blyfast.routing;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.Middleware;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Represents a route in the application. */
public class Route {
  private final String method;
  private final String path;
  private final String normalizedPath;
  private final Blyfast.Handler handler;
  private final List<Middleware> middleware;
  private final Pattern pattern;
  private final List<String> paramNames;

  /**
   * Creates a new route.
   *
   * @param method the HTTP method
   * @param path the route path
   * @param handler the handler function
   */
  public Route(String method, String path, Blyfast.Handler handler) {
    this.method = method.toUpperCase(); // Always store methods in uppercase for consistency
    this.path = path;
    this.handler = handler;
    this.middleware = new ArrayList<>();

    // Process the path and create a regex pattern for matching
    PathProcessor pathProcessor = PathProcessor.process(path);
    this.normalizedPath = pathProcessor.getNormalizedPath();
    this.pattern = pathProcessor.getPattern();
    this.paramNames = pathProcessor.getParamNames();
  }

  /**
   * Gets the HTTP method of the route.
   *
   * @return the method
   */
  public String getMethod() {
    return method;
  }

  /**
   * Gets the path of the route.
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Gets the handler function of the route.
   *
   * @return the handler
   */
  public Blyfast.Handler getHandler() {
    return handler;
  }

  /**
   * Gets the middleware list for this route.
   *
   * @return the middleware list
   */
  public List<Middleware> getMiddleware() {
    return middleware;
  }

  /**
   * Adds middleware to this route.
   *
   * @param middleware the middleware to add
   * @return this route for method chaining
   */
  public Route use(Middleware middleware) {
    this.middleware.add(middleware);
    return this;
  }

  /**
   * Checks if this route matches the given method and path.
   *
   * @param method the HTTP method
   * @param path the request path
   * @return true if the route matches
   */
  public boolean matches(String method, String path) {
    // Always do case-insensitive method comparison
    if (!this.method.equalsIgnoreCase(method.toUpperCase())) {
      return false;
    }

    // Direct path match (fast path)
    if (this.normalizedPath.equals(path)) {
      return true;
    }

    // Regex match for paths with parameters
    return pattern.matcher(path).matches();
  }

  /**
   * Gets the parameter names for this route.
   *
   * @return the list of parameter names
   */
  public List<String> getParamNames() {
    return paramNames;
  }

  /**
   * Gets the regex pattern for this route.
   *
   * @return the pattern
   */
  public Pattern getPattern() {
    return pattern;
  }
}
