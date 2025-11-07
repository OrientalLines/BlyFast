package com.blyfast.plugin.cors;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Plugin for handling Cross-Origin Resource Sharing (CORS). */
public class CorsPlugin extends AbstractPlugin {
  private final CorsConfig config;

  /** Creates a new CORS plugin with default configuration. */
  public CorsPlugin() {
    this(new CorsConfig());
  }

  /**
   * Creates a new CORS plugin with the specified configuration.
   *
   * @param config the CORS configuration
   */
  public CorsPlugin(CorsConfig config) {
    super("cors", "1.0.0");
    this.config = config;
  }

  @Override
  public void register(Blyfast app) {
    logger.info("Registering CORS plugin");
    app.set("cors", this);

    // Add the CORS middleware globally if configured to do so
    if (config.isEnableGlobal()) {
      app.use(createMiddleware());
    }
  }

  /**
   * Creates a CORS middleware with the current configuration.
   *
   * @return the middleware
   */
  public Middleware createMiddleware() {
    return ctx -> {
      String origin = ctx.header("Origin");

      // Skip if no Origin header or if it's a same-origin request
      if (origin == null) {
        return true;
      }

      // Check if the origin is allowed
      if (!isOriginAllowed(origin)) {
        return true; // Continue without CORS headers
      }

      // Set CORS headers
      ctx.header("Access-Control-Allow-Origin", getAllowedOriginValue(origin));

      if (config.isAllowCredentials()) {
        ctx.header("Access-Control-Allow-Credentials", "true");
      }

      if (!config.getExposeHeaders().isEmpty()) {
        ctx.header("Access-Control-Expose-Headers", String.join(", ", config.getExposeHeaders()));
      }

      // Handle preflight requests
      if (ctx.request().getMethod().equalsIgnoreCase("OPTIONS")) {
        // Set preflight headers
        ctx.header("Access-Control-Allow-Methods", String.join(", ", config.getAllowMethods()));

        if (!config.getAllowHeaders().isEmpty()) {
          ctx.header("Access-Control-Allow-Headers", String.join(", ", config.getAllowHeaders()));
        }

        if (config.getMaxAge() > 0) {
          ctx.header("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));
        }

        // End preflight request with 204 No Content
        ctx.status(204).send("");
        return false; // Stop middleware chain
      }

      return true; // Continue processing
    };
  }

  /**
   * Checks if the origin is allowed based on the configuration.
   *
   * @param origin the origin to check
   * @return true if the origin is allowed
   */
  private boolean isOriginAllowed(String origin) {
    // If allowAllOrigins is true, all origins are allowed
    if (config.isAllowAllOrigins()) {
      return true;
    }

    // Check if the origin is in the allowed origins list
    return config.getAllowOrigins().contains(origin) || config.getAllowOrigins().contains("*");
  }

  /**
   * Gets the value for the Access-Control-Allow-Origin header.
   *
   * @param origin the request origin
   * @return the header value
   */
  private String getAllowedOriginValue(String origin) {
    // If allowAllOrigins is true, return "*" (unless credentials are allowed)
    if (config.isAllowAllOrigins() && !config.isAllowCredentials()) {
      return "*";
    }

    // Otherwise, return the specific origin if it's allowed
    return isOriginAllowed(origin) ? origin : "";
  }

  /**
   * Gets the CORS configuration.
   *
   * @return the configuration
   */
  public CorsConfig getConfig() {
    return config;
  }

  /** Configuration for the CORS plugin. */
  public static class CorsConfig {
    private boolean enableGlobal = true;
    private boolean allowAllOrigins = true;
    private Set<String> allowOrigins = new HashSet<>();
    private Set<String> allowMethods =
        new HashSet<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    private Set<String> allowHeaders =
        new HashSet<>(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization"));
    private Set<String> exposeHeaders = new HashSet<>();
    private boolean allowCredentials = false;
    private long maxAge = 86400; // 24 hours

    public boolean isEnableGlobal() {
      return enableGlobal;
    }

    public CorsConfig setEnableGlobal(boolean enableGlobal) {
      this.enableGlobal = enableGlobal;
      return this;
    }

    public boolean isAllowAllOrigins() {
      return allowAllOrigins;
    }

    public CorsConfig setAllowAllOrigins(boolean allowAllOrigins) {
      this.allowAllOrigins = allowAllOrigins;
      return this;
    }

    public Set<String> getAllowOrigins() {
      return allowOrigins;
    }

    public CorsConfig setAllowOrigins(Set<String> allowOrigins) {
      this.allowOrigins = allowOrigins;
      return this;
    }

    public CorsConfig addAllowOrigin(String origin) {
      this.allowOrigins.add(origin);
      return this;
    }

    public Set<String> getAllowMethods() {
      return allowMethods;
    }

    public CorsConfig setAllowMethods(Set<String> allowMethods) {
      this.allowMethods = allowMethods;
      return this;
    }

    public CorsConfig addAllowMethod(String method) {
      this.allowMethods.add(method);
      return this;
    }

    public Set<String> getAllowHeaders() {
      return allowHeaders;
    }

    public CorsConfig setAllowHeaders(Set<String> allowHeaders) {
      this.allowHeaders = allowHeaders;
      return this;
    }

    public CorsConfig addAllowHeader(String header) {
      this.allowHeaders.add(header);
      return this;
    }

    public Set<String> getExposeHeaders() {
      return exposeHeaders;
    }

    public CorsConfig setExposeHeaders(Set<String> exposeHeaders) {
      this.exposeHeaders = exposeHeaders;
      return this;
    }

    public CorsConfig addExposeHeader(String header) {
      this.exposeHeaders.add(header);
      return this;
    }

    public boolean isAllowCredentials() {
      return allowCredentials;
    }

    public CorsConfig setAllowCredentials(boolean allowCredentials) {
      this.allowCredentials = allowCredentials;
      return this;
    }

    public long getMaxAge() {
      return maxAge;
    }

    public CorsConfig setMaxAge(long maxAge) {
      this.maxAge = maxAge;
      return this;
    }
  }
}
