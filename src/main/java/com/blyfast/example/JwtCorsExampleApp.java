package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.cors.CorsPlugin;
import com.blyfast.plugin.jwt.JwtPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone example demonstrating JWT authentication and CORS functionality. This example shows:
 * 1. How to configure and use JWT for authentication 2. How to secure routes with JWT middleware 3.
 * How to configure and use CORS for cross-origin requests 4. How to combine both plugins in a
 * single application
 */
public class JwtCorsExampleApp {
  // Simple in-memory user database for demo purposes
  private static final Map<String, User> users = new ConcurrentHashMap<>();

  static {
    // Add some sample users
    users.put("john", new User("john", "password123", "user"));
    users.put("admin", new User("admin", "admin123", "admin"));
  }

  public static void main(String[] args) {
    // Create the application
    Blyfast app = new Blyfast();

    // Configure and register JWT plugin
    // Create JWT plugin with token expiration of 30 minutes
    JwtPlugin jwtPlugin = new JwtPlugin("this-is-a-very-secure-secret-key-for-jwt-signing-32b");
    // Set expiration to 30 minutes (1800000 ms)
    jwtPlugin.getConfig().setExpirationMs(1800000);

    app.register(jwtPlugin);
    System.out.println("JWT Plugin registered");

    // Configure and register CORS plugin
    CorsPlugin corsPlugin = new CorsPlugin();
    // Configure CORS settings
    corsPlugin
        .getConfig()
        .setAllowAllOrigins(false)
        .addAllowOrigin("http://localhost:3000") // Frontend origin
        .addAllowOrigin("https://yourdomain.com")
        .setAllowCredentials(true)
        .addAllowHeader("Content-Type")
        .addAllowHeader("Authorization")
        .addAllowMethod("GET")
        .addAllowMethod("POST")
        .addAllowMethod("PUT")
        .addAllowMethod("DELETE")
        .setMaxAge(86400); // 24 hours

    app.register(corsPlugin);
    System.out.println("CORS Plugin registered");

    // Create middlewares
    Middleware jwtProtect = jwtPlugin.protect(); // Basic JWT protection
    Middleware adminOnly = jwtPlugin.protectWithRoles("admin"); // Role-based protection

    // Public routes

    // Home page - public
    app.get(
        "/",
        ctx -> {
          ctx.json(
              Map.of(
                  "message", "Welcome to the JWT and CORS example API",
                  "version", "1.0.0"));
        });

    // Login endpoint - public
    app.post(
        "/auth/login",
        ctx -> {
          try {
            // Parse login request
            LoginRequest loginRequest = ctx.parseBody(LoginRequest.class);

            // Validate credentials
            User user = users.get(loginRequest.getUsername());
            if (user == null || !user.getPassword().equals(loginRequest.getPassword())) {
              ctx.status(401)
                  .json(Map.of("error", true, "message", "Invalid username or password"));
              return;
            }

            // Create claims with user information
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());

            // Generate JWT token
            String token = jwtPlugin.generateToken(user.getUsername(), claims);

            // Return token and user info
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

    // User profile - requires authentication
    app.get(
        "/api/profile",
        ctx -> {
          // Apply JWT middleware
          if (!jwtProtect.handle(ctx)) {
            return; // Authentication failed
          }

          // Get username from token
          String token = extractToken(ctx);
          String username = jwtPlugin.extractSubject(token);
          User user = users.get(username);

          if (user == null) {
            ctx.status(404).json(Map.of("error", true, "message", "User not found"));
            return;
          }

          ctx.json(
              Map.of(
                  "username", user.getUsername(),
                  "role", user.getRole(),
                  "profile", Map.of("lastLogin", System.currentTimeMillis(), "status", "active")));
        });

    // Admin dashboard - requires admin role
    app.get(
        "/api/admin",
        ctx -> {
          // Apply admin middleware
          if (!adminOnly.handle(ctx)) {
            return; // Authentication or authorization failed
          }

          ctx.json(
              Map.of(
                  "message",
                  "Admin dashboard data",
                  "stats",
                  Map.of("users", users.size(), "activeUsers", 2, "systemLoad", 0.25)));
        });

    // Start the server
    int port = 8080;
    app.port(port).listen();

    System.out.println("Server started on http://localhost:" + port);
    System.out.println("\nAvailable endpoints:");
    System.out.println("  GET  / - Public homepage");
    System.out.println("  POST /auth/login - Authentication endpoint");
    System.out.println("  GET  /api/profile - Protected user profile (requires authentication)");
    System.out.println("  GET  /api/admin - Protected admin dashboard (requires admin role)");

    System.out.println("\nTest commands:");
    System.out.println("1. Login as regular user:");
    System.out.println(
        "   curl -X POST http://localhost:"
            + port
            + "/auth/login -H \"Content-Type: application/json\" -d '{\"username\":\"john\",\"password\":\"password123\"}'");

    System.out.println("\n2. Login as admin:");
    System.out.println(
        "   curl -X POST http://localhost:"
            + port
            + "/auth/login -H \"Content-Type: application/json\" -d '{\"username\":\"admin\",\"password\":\"admin123\"}'");

    System.out.println(
        "\n3. Access protected profile (replace TOKEN with the token from login response):");
    System.out.println(
        "   curl http://localhost:" + port + "/api/profile -H \"Authorization: Bearer TOKEN\"");

    System.out.println("\n4. Access admin dashboard (replace TOKEN with admin token):");
    System.out.println(
        "   curl http://localhost:" + port + "/api/admin -H \"Authorization: Bearer TOKEN\"");
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

  /** Simple user class for example purposes. */
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

  /** Simple login request class. */
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
