package com.blyfast.plugin.jwt;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;

/** Plugin for JWT authentication. */
public class JwtPlugin extends AbstractPlugin {
  private static final String DEFAULT_AUTH_SCHEME = "Bearer";
  private static final String DEFAULT_TOKEN_LOOKUP = "header:Authorization";
  private static final String DEFAULT_CONTEXT_KEY = "user";

  private final SecretKey secretKey;
  private final JwtConfig config;

  /**
   * Creates a new JWT plugin with the specified secret key.
   *
   * @param secretKey the secret key for signing tokens
   */
  public JwtPlugin(String secretKey) {
    this(secretKey, new JwtConfig());
  }

  /**
   * Creates a new JWT plugin with the specified secret key and configuration.
   *
   * @param secretKey the secret key for signing tokens
   * @param config the JWT configuration
   */
  public JwtPlugin(String secretKey, JwtConfig config) {
    super("jwt", "1.0.0");
    this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    this.config = config;
  }

  @Override
  public void register(Blyfast app) {
    logger.info("Registering JWT plugin");
    app.set("jwt", this);
  }

  /**
   * Creates a middleware that protects routes with JWT authentication.
   *
   * @return the middleware
   */
  public Middleware protect() {
    return ctx -> {
      String token = extractToken(ctx);
      if (token == null) {
        return handleAuthError(ctx, "Missing authentication token");
      }

      try {
        Claims claims = validateToken(token);
        ctx.set(config.getContextKey(), claims);
        return true;
      } catch (ExpiredJwtException e) {
        return handleAuthError(ctx, "Token expired");
      } catch (SignatureException e) {
        return handleAuthError(ctx, "Invalid token signature");
      } catch (MalformedJwtException e) {
        return handleAuthError(ctx, "Malformed token");
      } catch (JwtException e) {
        return handleAuthError(ctx, "Invalid token: " + e.getMessage());
      }
    };
  }

  /**
   * Creates a middleware that protects routes with JWT authentication and role-based authorization.
   *
   * @param roles the allowed roles
   * @return the middleware
   */
  public Middleware protectWithRoles(String... roles) {
    return ctx -> {
      // First, authenticate the user
      String token = extractToken(ctx);
      if (token == null) {
        return handleAuthError(ctx, "Missing authentication token");
      }

      try {
        Claims claims = validateToken(token);
        ctx.set(config.getContextKey(), claims);

        // Check roles
        String userRole = claims.get("role", String.class);
        if (userRole == null) {
          return handleAuthError(ctx, "No role specified in token");
        }

        for (String role : roles) {
          if (role.equals(userRole)) {
            return true; // Role matches
          }
        }

        // No matching role
        ctx.status(403).json(Map.of("error", true, "message", "Insufficient permissions"));
        return false;
      } catch (JwtException e) {
        return handleAuthError(ctx, "Invalid token: " + e.getMessage());
      }
    };
  }

  /**
   * Generates a JWT token for the specified subject.
   *
   * @param subject the subject (usually a user ID)
   * @return the generated token
   */
  public String generateToken(String subject) {
    return generateToken(subject, new HashMap<>());
  }

  /**
   * Generates a JWT token for the specified subject with custom claims.
   *
   * @param subject the subject (usually a user ID)
   * @param claims the custom claims to include
   * @return the generated token
   */
  public String generateToken(String subject, Map<String, Object> claims) {
    long now = System.currentTimeMillis();

    JwtBuilder builder = Jwts.builder().subject(subject).issuedAt(new Date(now));

    // Add custom claims
    for (Map.Entry<String, Object> entry : claims.entrySet()) {
      builder.claim(entry.getKey(), entry.getValue());
    }

    // Set expiration if configured
    if (config.getExpirationMs() > 0) {
      builder.expiration(new Date(now + config.getExpirationMs()));
    }

    return builder.signWith(secretKey).compact();
  }

  /**
   * Validates a JWT token and returns the claims.
   *
   * @param token the token to validate
   * @return the claims
   * @throws JwtException if the token is invalid
   */
  public Claims validateToken(String token) throws JwtException {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  /**
   * Extracts a claim from a token.
   *
   * @param token the token
   * @param claimsResolver the function to extract the claim
   * @param <T> the type of the claim
   * @return the claim value
   */
  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    Claims claims = validateToken(token);
    return claimsResolver.apply(claims);
  }

  /**
   * Extracts the subject from a token.
   *
   * @param token the token
   * @return the subject
   */
  public String extractSubject(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  /**
   * Extracts the token from the request.
   *
   * @param ctx the context
   * @return the token or null if not found
   */
  private String extractToken(Context ctx) {
    String[] parts = config.getTokenLookup().split(":");
    if (parts.length != 2) {
      return null;
    }

    String type = parts[0].toLowerCase();
    String key = parts[1];

    switch (type) {
      case "header":
        String authHeader = ctx.header(key);
        if (authHeader != null && authHeader.startsWith(config.getAuthScheme() + " ")) {
          return authHeader.substring(config.getAuthScheme().length() + 1);
        }
        break;
      case "query":
        return ctx.query(key);
      case "cookie":
        // Cookie extraction would be implemented here
        break;
    }

    return null;
  }

  /**
   * Handles an authentication error.
   *
   * @param ctx the context
   * @param message the error message
   * @return false to stop the middleware chain
   */
  private boolean handleAuthError(Context ctx, String message) {
    ctx.status(401).json(Map.of("error", true, "message", message));
    return false;
  }

  /**
   * Gets the JWT configuration.
   *
   * @return the configuration
   */
  public JwtConfig getConfig() {
    return config;
  }

  /** Configuration for the JWT plugin. */
  public static class JwtConfig {
    private String authScheme = DEFAULT_AUTH_SCHEME;
    private String tokenLookup = DEFAULT_TOKEN_LOOKUP;
    private String contextKey = DEFAULT_CONTEXT_KEY;
    private long expirationMs = 3600000; // 1 hour by default

    public String getAuthScheme() {
      return authScheme;
    }

    public JwtConfig setAuthScheme(String authScheme) {
      this.authScheme = authScheme;
      return this;
    }

    public String getTokenLookup() {
      return tokenLookup;
    }

    public JwtConfig setTokenLookup(String tokenLookup) {
      this.tokenLookup = tokenLookup;
      return this;
    }

    public String getContextKey() {
      return contextKey;
    }

    public JwtConfig setContextKey(String contextKey) {
      this.contextKey = contextKey;
      return this;
    }

    public long getExpirationMs() {
      return expirationMs;
    }

    public JwtConfig setExpirationMs(long expirationMs) {
      this.expirationMs = expirationMs;
      return this;
    }
  }
}
