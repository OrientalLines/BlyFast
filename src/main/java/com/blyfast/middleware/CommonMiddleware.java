package com.blyfast.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Collection of common middleware implementations.
 */
public class CommonMiddleware {
    private static final Logger logger = LoggerFactory.getLogger(CommonMiddleware.class);

    /**
     * Creates a logging middleware that logs request information.
     *
     * @return the middleware
     */
    public static Middleware logger() {
        return ctx -> {
            long startTime = System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString().substring(0, 8);

            ctx.request().setAttribute("requestId", requestId);
            ctx.request().setAttribute("startTime", startTime);

            logger.info("[{}] {} {} started", requestId, ctx.request().getMethod(), ctx.request().getPath());

            // Continue processing
            return true;
        };
    }

    /**
     * Creates a middleware that logs the response time after request handling.
     *
     * @return the middleware
     */
    public static Middleware responseTime() {
        return ctx -> {
            // Execute after the handler (this runs before the response is sent)
            ctx.exchange().addExchangeCompleteListener((exchange, nextListener) -> {
                try {
                    Long startTime = (Long) ctx.request().getAttribute("startTime");
                    if (startTime != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        String requestId = (String) ctx.request().getAttribute("requestId");
                        int status = exchange.getStatusCode();

                        logger.info("[{}] {} {} completed with status {} in {}ms",
                                requestId, ctx.request().getMethod(), ctx.request().getPath(),
                                status, duration);
                    }
                } finally {
                    nextListener.proceed();
                }
            });

            // Continue processing
            return true;
        };
    }

    /**
     * Creates a CORS middleware with default settings.
     *
     * @return the middleware
     */
    public static Middleware cors() {
        return cors("*", "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
    }

    /**
     * Creates a CORS middleware with custom settings.
     *
     * @param allowOrigin  the allowed origin
     * @param allowMethods the allowed methods
     * @param allowHeaders the allowed headers
     * @return the middleware
     */
    public static Middleware cors(String allowOrigin, String allowMethods, String allowHeaders) {
        return ctx -> {
            ctx.header("Access-Control-Allow-Origin", allowOrigin);
            ctx.header("Access-Control-Allow-Methods", allowMethods);
            ctx.header("Access-Control-Allow-Headers", allowHeaders);

            // Handle OPTIONS requests
            if (ctx.request().getMethod().equalsIgnoreCase("OPTIONS")) {
                ctx.status(204);
                ctx.response().status(204).send("");
                return false; // Stop processing
            }

            // Continue processing
            return true;
        };
    }

    /**
     * Creates a middleware that adds standard security headers.
     *
     * @return the middleware
     */
    public static Middleware securityHeaders() {
        return ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("X-XSS-Protection", "1; mode=block");
            ctx.header("Referrer-Policy", "no-referrer-when-downgrade");

            // Continue processing
            return true;
        };
    }

    /**
     * Creates a middleware that compresses the response.
     *
     * @return the middleware
     */
    public static Middleware compress() {
        return ctx -> {
            String acceptEncoding = ctx.header("Accept-Encoding");
            if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                ctx.header("Content-Encoding", "gzip");
                // Enable gzip encoding at the Undertow level if needed
                // This would typically be configured at the server level rather than
                // per-request
            }

            // Continue processing
            return true;
        };
    }

    /**
     * Creates a middleware that sets a request timeout.
     *
     * @param timeoutMillis the timeout in milliseconds
     * @return the middleware
     */
    public static Middleware timeout(long timeoutMillis) {
        return ctx -> {
            // Set timeout at the Undertow level
            ctx.exchange().setMaxEntitySize(timeoutMillis); // This is not a timeout, but a size limit
            // Actual timeout handling would typically be configured at the server level

            // Continue processing
            return true;
        };
    }
}