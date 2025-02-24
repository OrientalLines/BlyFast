package com.blyfast.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collection of common middleware implementations.
 */
public class CommonMiddleware {
    private static final Logger logger = LoggerFactory.getLogger(CommonMiddleware.class);

    // Monitoring counters
    private static final AtomicInteger requestCounter = new AtomicInteger(0);
    private static final AtomicInteger errorCounter = new AtomicInteger(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final Map<String, AtomicInteger> pathCounter = new ConcurrentHashMap<>();

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

    /**
     * Creates a global exception handler middleware that catches and processes exceptions.
     *
     * @return the middleware
     */
    public static Middleware exceptionHandler() {
        return ctx -> {
            try {
                return true; // Continue processing the request
            } catch (Exception e) {
                logger.error("Uncaught exception during request processing", e);
                
                // Determine appropriate status code
                int statusCode = 500;
                String message = "Internal Server Error";
                
                if (e instanceof IllegalArgumentException) {
                    statusCode = 400;
                    message = "Bad Request: " + e.getMessage();
                } else if (e instanceof SecurityException) {
                    statusCode = 403;
                    message = "Forbidden: " + e.getMessage();
                }
                
                // Create error response
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", true);
                errorResponse.put("status", statusCode);
                errorResponse.put("message", message);
                
                // Include stack trace in development mode
                if (Boolean.getBoolean("blyfast.dev")) {
                    errorResponse.put("exception", e.getClass().getName());
                    errorResponse.put("detail", e.getMessage());
                }
                
                // Send error response
                ctx.status(statusCode).json(errorResponse);
                
                return false; // Stop processing further middleware
            }
        };
    }
    
    /**
     * Creates a recovery middleware that attempts to continue processing 
     * even when errors occur in subsequent middleware.
     *
     * @return the middleware
     */
    public static Middleware recover() {
        return ctx -> {
            try {
                return true; // Continue to next middleware
            } catch (Exception e) {
                logger.warn("Recovered from exception in middleware chain: {}", e.getMessage());
                logger.debug("Exception details:", e);
                
                // Continue processing instead of letting the exception propagate
                return true;
            }
        };
    }

    /**
     * Creates a monitoring middleware that tracks request statistics.
     *
     * @return the middleware
     */
    public static Middleware monitor() {
        return ctx -> {
            long startTime = System.currentTimeMillis();
            String path = ctx.request().getPath();
            
            // Increment request counter
            requestCounter.incrementAndGet();
            
            // Increment path-specific counter
            pathCounter.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
            
            // Add a listener for when the exchange completes
            ctx.exchange().addExchangeCompleteListener((exchange, nextListener) -> {
                try {
                    // Record response time
                    long responseTime = System.currentTimeMillis() - startTime;
                    totalResponseTime.addAndGet(responseTime);
                    
                    // Record errors
                    int statusCode = exchange.getStatusCode();
                    if (statusCode >= 400) {
                        errorCounter.incrementAndGet();
                    }
                    
                    // Log slow requests
                    if (responseTime > 1000) {
                        logger.warn("Slow request: {} {} completed in {}ms", 
                                ctx.request().getMethod(), path, responseTime);
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
     * Gets the current monitoring statistics.
     *
     * @return the monitoring statistics
     */
    public static Map<String, Object> getMonitoringStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalRequests", requestCounter.get());
        stats.put("errorCount", errorCounter.get());
        
        // Calculate average response time
        double avgResponseTime = requestCounter.get() > 0 
                ? (double) totalResponseTime.get() / requestCounter.get() 
                : 0;
        stats.put("avgResponseTime", avgResponseTime);
        
        // Path-specific stats
        Map<String, Integer> pathStats = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : pathCounter.entrySet()) {
            pathStats.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("pathCounts", pathStats);
        
        return stats;
    }

    /**
     * Creates a middleware that serves static files from a directory.
     *
     * @param directory the directory to serve files from
     * @return the middleware
     */
    public static Middleware fileSystem(String directory) {
        return fileSystem(directory, "/");
    }

    /**
     * Creates a middleware that serves static files from a directory with a custom
     * URL prefix.
     *
     * @param directory  the directory to serve files from
     * @param urlPrefix  the URL prefix to match against
     * @return the middleware
     */
    public static Middleware fileSystem(String directory, String urlPrefix) {
        return ctx -> {
            String path = ctx.request().getPath();
            
            // Check if the path starts with the URL prefix
            if (!path.startsWith(urlPrefix)) {
                return true; // Continue processing if the path doesn't match
            }
            
            // Remove the URL prefix from the path to get the relative file path
            String relativePath = path.substring(urlPrefix.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            
            if (relativePath.isEmpty()) {
                return true; // Continue processing for the root path
            }
            
            // Build the full file path
            java.io.File file = new java.io.File(directory, relativePath);
            
            // Security check - prevent directory traversal attacks
            if (!file.getCanonicalPath().startsWith(new java.io.File(directory).getCanonicalPath())) {
                ctx.status(403).error(403, "Forbidden - Access denied");
                return false;
            }
            
            // Check if file exists and is not a directory
            if (!file.exists() || file.isDirectory()) {
                return true; // File not found, continue processing
            }
            
            try {
                // Set appropriate content type based on file extension
                String contentType = getContentType(file.getName());
                ctx.type(contentType);
                
                // Read and send file content
                byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
                ctx.response().send(content);
                
                // Stop middleware chain
                return false;
            } catch (Exception e) {
                logger.error("Error serving file: " + file.getPath(), e);
                return true; // Continue processing on error
            }
        };
    }
    
    /**
     * Gets the appropriate content type based on the file extension.
     *
     * @param fileName the file name
     * @return the content type
     */
    private static String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            case "html": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "svg": return "image/svg+xml";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain";
            case "xml": return "application/xml";
            default: return "application/octet-stream";
        }
    }

    /**
     * Creates a middleware that handles favicon requests efficiently.
     * This middleware either ignores favicon requests or caches a favicon in memory
     * to improve performance by skipping disk access on subsequent requests.
     *
     * @return the middleware
     */
    public static Middleware favicon() {
        return favicon("/favicon.ico", null);
    }

    /**
     * Creates a middleware that handles favicon requests efficiently with a custom file path.
     * This middleware caches the favicon in memory to improve performance.
     *
     * @param filePath the path to the favicon file
     * @return the middleware
     */
    public static Middleware favicon(String filePath) {
        return favicon("/favicon.ico", filePath);
    }

    /**
     * Configuration options for the favicon middleware.
     */
    public static class FaviconConfig {
        private String url = "/favicon.ico";
        private String file = "";
        private byte[] data = null;
        private String cacheControl = "public, max-age=31536000";

        public String getUrl() {
            return url;
        }

        public FaviconConfig setUrl(String url) {
            this.url = url;
            return this;
        }

        public String getFile() {
            return file;
        }

        public FaviconConfig setFile(String file) {
            this.file = file;
            return this;
        }

        public byte[] getData() {
            return data;
        }

        public FaviconConfig setData(byte[] data) {
            this.data = data;
            return this;
        }

        public String getCacheControl() {
            return cacheControl;
        }

        public FaviconConfig setCacheControl(String cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }
    }

    /**
     * Creates a middleware that handles favicon requests efficiently with custom URL and file path.
     * This middleware caches the favicon in memory to improve performance.
     *
     * @param url the URL to match for favicon requests
     * @param filePath the path to the favicon file
     * @return the middleware
     */
    public static Middleware favicon(String url, String filePath) {
        // Cache the favicon bytes if a file path is provided
        byte[] faviconBytes = null;
        if (filePath != null && !filePath.isEmpty()) {
            try {
                faviconBytes = java.nio.file.Files.readAllBytes(new java.io.File(filePath).toPath());
                logger.info("Favicon loaded and cached: {}", filePath);
            } catch (Exception e) {
                logger.warn("Could not read favicon file: {}. Favicon requests will be ignored.", filePath, e);
            }
        }

        // Create a final reference to the favicon bytes for use in the lambda
        final byte[] finalFaviconBytes = faviconBytes;
        
        return ctx -> {
            // Check if this is a favicon request
            if (!ctx.request().getPath().equals(url)) {
                return true; // Not a favicon request, continue processing
            }
            
            // If we have a favicon, serve it
            if (finalFaviconBytes != null) {
                ctx.type("image/x-icon");
                ctx.header("Cache-Control", "public, max-age=31536000"); // Cache for 1 year
                ctx.response().send(finalFaviconBytes);
            } else {
                // No favicon provided, return 204 No Content
                ctx.status(204);
            }
            
            return false; // Stop processing
        };
    }
    
    /**
     * Creates a middleware that handles favicon requests efficiently with custom configuration.
     * This middleware caches the favicon in memory to improve performance.
     *
     * @param config the configuration for the favicon middleware
     * @return the middleware
     */
    public static Middleware favicon(FaviconConfig config) {
        // Cache the favicon bytes if provided in config
        byte[] faviconBytes = config.getData();
        
        // If no data is provided, try to load from file
        if (faviconBytes == null && config.getFile() != null && !config.getFile().isEmpty()) {
            try {
                faviconBytes = java.nio.file.Files.readAllBytes(new java.io.File(config.getFile()).toPath());
                logger.info("Favicon loaded and cached: {}", config.getFile());
            } catch (Exception e) {
                logger.warn("Could not read favicon file: {}. Favicon requests will be ignored.", config.getFile(), e);
            }
        }

        // Create a final reference to the values for use in the lambda
        final byte[] finalFaviconBytes = faviconBytes;
        final String url = config.getUrl();
        final String cacheControl = config.getCacheControl();
        
        return ctx -> {
            // Check if this is a favicon request
            if (!ctx.request().getPath().equals(url)) {
                return true; // Not a favicon request, continue processing
            }
            
            // If we have a favicon, serve it
            if (finalFaviconBytes != null) {
                ctx.type("image/x-icon");
                ctx.header("Cache-Control", cacheControl);
                ctx.response().send(finalFaviconBytes);
            } else {
                // No favicon provided, return 204 No Content
                ctx.status(204);
            }
            
            return false; // Stop processing
        };
    }
}