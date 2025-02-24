package com.blyfast.plugin.compression;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Plugin for compressing HTTP responses.
 */
public class CompressionPlugin extends AbstractPlugin {
    private final CompressionConfig config;

    /**
     * Creates a new compression plugin with default configuration.
     */
    public CompressionPlugin() {
        this(new CompressionConfig());
    }

    /**
     * Creates a new compression plugin with the specified configuration.
     * 
     * @param config the compression configuration
     */
    public CompressionPlugin(CompressionConfig config) {
        super("compression", "1.0.0");
        this.config = config;
    }

    @Override
    public void register(Blyfast app) {
        logger.info("Registering Compression plugin");
        app.set("compression", this);

        // Add the compression middleware globally if configured to do so
        if (config.isEnableGlobal()) {
            app.use(createMiddleware());
        }
    }

    /**
     * Creates a compression middleware with the current configuration.
     * 
     * @return the middleware
     */
    public Middleware createMiddleware() {
        return ctx -> {
            // Check if compression should be applied
            if (!shouldCompress(ctx)) {
                return true; // Skip compression
            }

            // Get the client's accepted encodings
            String acceptEncoding = ctx.header("Accept-Encoding");
            if (acceptEncoding == null) {
                return true; // Client doesn't support compression
            }

            // Apply compression based on accepted encodings
            if (acceptEncoding.contains("gzip") && config.isEnableGzip()) {
                applyGzipCompression(ctx);
            } else if (acceptEncoding.contains("deflate") && config.isEnableDeflate()) {
                applyDeflateCompression(ctx);
            }

            return true; // Continue processing
        };
    }

    /**
     * Checks if compression should be applied based on the configuration and
     * request.
     * 
     * @param ctx the context
     * @return true if compression should be applied
     */
    private boolean shouldCompress(Context ctx) {
        // Check if the request path matches any excluded patterns
        String path = ctx.request().getPath();
        for (Pattern pattern : config.getExcludedPaths()) {
            if (pattern.matcher(path).matches()) {
                return false;
            }
        }

        // Check if the content type matches any included types
        String contentType = ctx.response().getExchange().getResponseHeaders().getFirst("Content-Type");
        if (contentType != null) {
            for (String type : config.getIncludedContentTypes()) {
                if (contentType.startsWith(type)) {
                    return true;
                }
            }
        }

        // Default to compressing if no content type is set yet
        return contentType == null;
    }

    /**
     * Applies gzip compression to the response.
     * 
     * @param ctx the context
     */
    private void applyGzipCompression(Context ctx) {
        // Set the Content-Encoding header
        ctx.header("Content-Encoding", "gzip");

        // Enable gzip encoding at the Undertow level
        HttpServerExchange exchange = ctx.exchange();
        exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, "gzip");
    }

    /**
     * Applies deflate compression to the response.
     * 
     * @param ctx the context
     */
    private void applyDeflateCompression(Context ctx) {
        // Set the Content-Encoding header
        ctx.header("Content-Encoding", "deflate");

        // Enable deflate encoding at the Undertow level
        HttpServerExchange exchange = ctx.exchange();
        exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, "deflate");
    }

    /**
     * Gets the compression configuration.
     * 
     * @return the configuration
     */
    public CompressionConfig getConfig() {
        return config;
    }

    /**
     * Configuration for the compression plugin.
     */
    public static class CompressionConfig {
        private boolean enableGlobal = true;
        private boolean enableGzip = true;
        private boolean enableDeflate = true;
        private int priority = 10;
        private int minLength = 1024; // Minimum content length to compress (in bytes)
        private Set<String> includedContentTypes = new HashSet<>();
        private Set<Pattern> excludedPaths = new HashSet<>();

        /**
         * Creates a new compression configuration with default settings.
         */
        public CompressionConfig() {
            // Default content types to compress
            includedContentTypes.add("text/");
            includedContentTypes.add("application/json");
            includedContentTypes.add("application/xml");
            includedContentTypes.add("application/javascript");
            includedContentTypes.add("application/xhtml+xml");

            // Default paths to exclude
            excludedPaths.add(Pattern.compile(".*\\.(jpg|jpeg|gif|png|zip|gz|mp4|mp3|avi|mov)$"));
        }

        public boolean isEnableGlobal() {
            return enableGlobal;
        }

        public CompressionConfig setEnableGlobal(boolean enableGlobal) {
            this.enableGlobal = enableGlobal;
            return this;
        }

        public boolean isEnableGzip() {
            return enableGzip;
        }

        public CompressionConfig setEnableGzip(boolean enableGzip) {
            this.enableGzip = enableGzip;
            return this;
        }

        public boolean isEnableDeflate() {
            return enableDeflate;
        }

        public CompressionConfig setEnableDeflate(boolean enableDeflate) {
            this.enableDeflate = enableDeflate;
            return this;
        }

        public int getPriority() {
            return priority;
        }

        public CompressionConfig setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public int getMinLength() {
            return minLength;
        }

        public CompressionConfig setMinLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Set<String> getIncludedContentTypes() {
            return includedContentTypes;
        }

        public CompressionConfig setIncludedContentTypes(Set<String> includedContentTypes) {
            this.includedContentTypes = includedContentTypes;
            return this;
        }

        public CompressionConfig addIncludedContentType(String contentType) {
            this.includedContentTypes.add(contentType);
            return this;
        }

        public Set<Pattern> getExcludedPaths() {
            return excludedPaths;
        }

        public CompressionConfig setExcludedPaths(Set<Pattern> excludedPaths) {
            this.excludedPaths = excludedPaths;
            return this;
        }

        public CompressionConfig addExcludedPath(String pathPattern) {
            this.excludedPaths.add(Pattern.compile(pathPattern));
            return this;
        }
    }
}