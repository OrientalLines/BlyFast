package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.compression.CompressionPlugin;
import com.blyfast.plugin.cors.CorsPlugin;
import com.blyfast.plugin.jwt.JwtPlugin;
import com.blyfast.plugin.limiter.RateLimiterPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Example application demonstrating the use of plugins in the BlyFast framework. */
public class PluginExampleApp {
  // Simple in-memory user store for the example
  private static final Map<String, User> users = new ConcurrentHashMap<>();

  static {
    // Add some sample users
    users.put("user1", new User("user1", "password1", "user"));
    users.put("admin1", new User("admin1", "password1", "admin"));
  }

  public static void main(String[] args) {
    // Create the application
    Blyfast app = new Blyfast();

    // Register plugins

    // 1. JWT Authentication Plugin
    JwtPlugin jwtPlugin = new JwtPlugin("your-secret-key-should-be-at-least-32-bytes-long");
    app.register(jwtPlugin);

    // 2. CORS Plugin
    CorsPlugin corsPlugin = new CorsPlugin();
    app.register(corsPlugin);

    // 3. Rate Limiter Plugin
    RateLimiterPlugin rateLimiterPlugin = new RateLimiterPlugin();
    app.register(rateLimiterPlugin);

    // 4. Compression Plugin
    CompressionPlugin compressionPlugin = new CompressionPlugin();
    app.register(compressionPlugin);

    // Define routes

    // Public routes
    app.get(
        "/",
        ctx -> {
          Map<String, Object> response = new HashMap<>();
          response.put("message", "Welcome to the BlyFast Plugin Example API");
          response.put("version", "1.0.0");

          ctx.json(response);
        });

    // Authentication route
    app.post(
        "/auth/login",
        ctx -> {
          try {
            LoginRequest loginRequest = ctx.parseBody(LoginRequest.class);

            // Validate credentials
            User user = users.get(loginRequest.getUsername());
            if (user == null || !user.getPassword().equals(loginRequest.getPassword())) {
              ctx.status(401)
                  .json(Map.of("error", true, "message", "Invalid username or password"));
              return;
            }

            // Generate JWT token
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());

            String token = jwtPlugin.generateToken(user.getUsername(), claims);

            // Return the token
            ctx.json(
                Map.of(
                    "token",
                    token,
                    "user",
                    Map.of(
                        "username", user.getUsername(),
                        "role", user.getRole())));
          } catch (Exception e) {
            ctx.status(400)
                .json(Map.of("error", true, "message", "Invalid request: " + e.getMessage()));
          }
        });

    // Protected routes

    // User route - accessible by any authenticated user
    // First, add the middleware to the route
    Middleware jwtProtect = jwtPlugin.protect();
    app.get(
        "/api/user/profile",
        ctx -> {
          try {
            // Apply the middleware first
            if (!jwtProtect.handle(ctx)) {
              return; // Middleware chain was interrupted
            }

            // The JWT middleware has already verified the token
            // and added the claims to the context
            String username = jwtPlugin.extractSubject(extractToken(ctx));
            User user = users.get(username);

            if (user == null) {
              ctx.status(404).json(Map.of("error", true, "message", "User not found"));
              return;
            }

            ctx.json(
                Map.of(
                    "username", user.getUsername(),
                    "role", user.getRole()));
          } catch (Exception e) {
            ctx.status(500)
                .json(Map.of("error", true, "message", "Server error: " + e.getMessage()));
          }
        });

    // Admin route - accessible only by users with the "admin" role
    Middleware jwtProtectAdmin = jwtPlugin.protectWithRoles("admin");
    app.get(
        "/api/admin/dashboard",
        ctx -> {
          try {
            // Apply the middleware first
            if (!jwtProtectAdmin.handle(ctx)) {
              return; // Middleware chain was interrupted
            }

            ctx.json(
                Map.of(
                    "message",
                    "Welcome to the admin dashboard",
                    "stats",
                    Map.of("users", users.size(), "activeUsers", 2)));
          } catch (Exception e) {
            ctx.status(500)
                .json(Map.of("error", true, "message", "Server error: " + e.getMessage()));
          }
        });

    // Rate-limited route
    Middleware rateLimiter = rateLimiterPlugin.createMiddleware();
    app.get(
        "/api/limited",
        ctx -> {
          try {
            // Apply the middleware first
            if (!rateLimiter.handle(ctx)) {
              return; // Middleware chain was interrupted
            }

            ctx.json(
                Map.of(
                    "message",
                    "This route is rate-limited",
                    "timestamp",
                    System.currentTimeMillis()));
          } catch (Exception e) {
            ctx.status(500)
                .json(Map.of("error", true, "message", "Server error: " + e.getMessage()));
          }
        });

    // Start the server
    app.port(8080).listen();
    System.out.println("Server started on http://localhost:8080");
    System.out.println("Available endpoints:");
    System.out.println("  GET  / - Public welcome page");
    System.out.println("  POST /auth/login - Authentication endpoint");
    System.out.println(
        "  GET  /api/user/profile - Protected user profile (requires authentication)");
    System.out.println(
        "  GET  /api/admin/dashboard - Protected admin dashboard (requires admin role)");
    System.out.println("  GET  /api/limited - Rate-limited endpoint");
  }

  /**
   * Extracts the JWT token from the request.
   *
   * @param ctx the context
   * @return the token or null if not found
   */
  private static String extractToken(Context ctx) {
    String authHeader = ctx.header("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  /** Example user class. */
  public static class User {
    private String username;
    private String password;
    private String role;

    public User() {}

    public User(String username, String password, String role) {
      this.username = username;
      this.password = password;
      this.role = role;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }
  }

  /** Login request class. */
  public static class LoginRequest {
    private String username;
    private String password;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
