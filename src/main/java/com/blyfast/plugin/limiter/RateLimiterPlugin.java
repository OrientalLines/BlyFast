package com.blyfast.plugin.limiter;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Plugin for rate limiting requests to protect against abuse. */
public class RateLimiterPlugin extends AbstractPlugin {
  private final RateLimiterConfig config;
  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  /** Creates a new rate limiter plugin with default configuration. */
  public RateLimiterPlugin() {
    this(new RateLimiterConfig());
  }

  /**
   * Creates a new rate limiter plugin with the specified configuration.
   *
   * @param config the rate limiter configuration
   */
  public RateLimiterPlugin(RateLimiterConfig config) {
    super("rate-limiter", "1.0.0");
    this.config = config;
  }

  @Override
  public void register(Blyfast app) {
    logger.info("Registering Rate Limiter plugin");
    app.set("rateLimiter", this);

    // Schedule cleanup of expired buckets
    scheduler.scheduleAtFixedRate(
        this::cleanupExpiredBuckets,
        config.getCleanupInterval().toMillis(),
        config.getCleanupInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void onStop(Blyfast app) {
    super.onStop(app);
    scheduler.shutdown();
  }

  /**
   * Creates a rate limiting middleware with the current configuration.
   *
   * @return the middleware
   */
  public Middleware createMiddleware() {
    return createMiddleware(config.getKeyExtractor());
  }

  /**
   * Creates a rate limiting middleware with a custom key extractor.
   *
   * @param keyExtractor the function to extract the key from the context
   * @return the middleware
   */
  public Middleware createMiddleware(Function<com.blyfast.http.Context, String> keyExtractor) {
    return ctx -> {
      String key = keyExtractor.apply(ctx);

      // Get or create a token bucket for this key
      TokenBucket bucket =
          buckets.computeIfAbsent(
              key, k -> new TokenBucket(config.getMaxTokens(), config.getRefillRate()));

      // Try to consume a token
      if (!bucket.tryConsume()) {
        // Rate limit exceeded
        ctx.status(429).header("Retry-After", String.valueOf(config.getRetryAfter().getSeconds()));
        ctx.json(Map.of("error", true, "message", "Rate limit exceeded. Try again later."));
        return false; // Stop middleware chain
      }

      return true; // Continue processing
    };
  }

  /** Cleans up expired token buckets to prevent memory leaks. */
  private void cleanupExpiredBuckets() {
    long now = System.currentTimeMillis();
    long expirationTime = now - config.getExpirationTime().toMillis();

    buckets.entrySet().removeIf(entry -> entry.getValue().getLastAccessTime() < expirationTime);
  }

  /**
   * Gets the rate limiter configuration.
   *
   * @return the configuration
   */
  public RateLimiterConfig getConfig() {
    return config;
  }

  /** Token bucket implementation for rate limiting. */
  private static class TokenBucket {
    private final double maxTokens;
    private final double refillRate; // tokens per millisecond
    private double tokens;
    private long lastRefillTime;
    private long lastAccessTime;

    /**
     * Creates a new token bucket.
     *
     * @param maxTokens the maximum number of tokens
     * @param refillRate the refill rate in tokens per second
     */
    public TokenBucket(double maxTokens, double refillRate) {
      this.maxTokens = maxTokens;
      this.refillRate = refillRate / 1000.0; // Convert to tokens per millisecond
      this.tokens = maxTokens;
      this.lastRefillTime = System.currentTimeMillis();
      this.lastAccessTime = lastRefillTime;
    }

    /**
     * Tries to consume a token from the bucket.
     *
     * @return true if a token was consumed, false if no tokens are available
     */
    public synchronized boolean tryConsume() {
      refill();

      if (tokens < 1.0) {
        return false;
      }

      tokens -= 1.0;
      lastAccessTime = System.currentTimeMillis();
      return true;
    }

    /** Refills the token bucket based on the elapsed time. */
    private void refill() {
      long now = System.currentTimeMillis();
      long elapsed = now - lastRefillTime;

      if (elapsed > 0) {
        double tokensToAdd = elapsed * refillRate;
        tokens = Math.min(maxTokens, tokens + tokensToAdd);
        lastRefillTime = now;
      }
    }

    /**
     * Gets the last access time of the bucket.
     *
     * @return the last access time in milliseconds
     */
    public long getLastAccessTime() {
      return lastAccessTime;
    }
  }

  /** Configuration for the rate limiter plugin. */
  public static class RateLimiterConfig {
    private double maxTokens = 60; // Maximum number of tokens (requests)
    private double refillRate = 1; // Tokens per second
    private Duration retryAfter = Duration.ofSeconds(60); // Retry-After header value
    private Duration expirationTime =
        Duration.ofHours(1); // Time after which unused buckets are removed
    private Duration cleanupInterval =
        Duration.ofMinutes(5); // Interval for cleaning up expired buckets
    private Function<com.blyfast.http.Context, String> keyExtractor =
        ctx ->
            ctx.request().getHeader("X-Forwarded-For") != null
                ? ctx.request().getHeader("X-Forwarded-For")
                : ctx.request()
                    .getExchange()
                    .getSourceAddress()
                    .getHostString(); // Default key extractor uses

    // IP address

    public double getMaxTokens() {
      return maxTokens;
    }

    public RateLimiterConfig setMaxTokens(double maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public double getRefillRate() {
      return refillRate;
    }

    public RateLimiterConfig setRefillRate(double refillRate) {
      this.refillRate = refillRate;
      return this;
    }

    public Duration getRetryAfter() {
      return retryAfter;
    }

    public RateLimiterConfig setRetryAfter(Duration retryAfter) {
      this.retryAfter = retryAfter;
      return this;
    }

    public Duration getExpirationTime() {
      return expirationTime;
    }

    public RateLimiterConfig setExpirationTime(Duration expirationTime) {
      this.expirationTime = expirationTime;
      return this;
    }

    public Duration getCleanupInterval() {
      return cleanupInterval;
    }

    public RateLimiterConfig setCleanupInterval(Duration cleanupInterval) {
      this.cleanupInterval = cleanupInterval;
      return this;
    }

    public Function<com.blyfast.http.Context, String> getKeyExtractor() {
      return keyExtractor;
    }

    public RateLimiterConfig setKeyExtractor(
        Function<com.blyfast.http.Context, String> keyExtractor) {
      this.keyExtractor = keyExtractor;
      return this;
    }
  }
}
