package com.blyfast.plugin.monitor;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.Middleware;
import com.blyfast.plugin.AbstractPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;

/**
 * Plugin that provides application monitoring capabilities.
 * Tracks metrics like request counts, response times, error rates, etc.
 */
public class MonitorPlugin extends AbstractPlugin {
    private static final Logger logger = LoggerFactory.getLogger(MonitorPlugin.class);
    
    // Global counters
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    // Path-specific metrics
    private final Map<String, PathMetrics> pathMetrics = new ConcurrentHashMap<>();
    
    // JVM metrics
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    
    private final long startTime = System.currentTimeMillis();
    
    private static final String DASHBOARD_HTML_PATH = "/monitor/dashboard.html";
    private static final String DASHBOARD_CSS_PATH = "/monitor/dashboard.css";
    private static final String DASHBOARD_JS_PATH = "/monitor/dashboard.js";
    
    /**
     * Creates a new monitor plugin.
     */
    public MonitorPlugin() {
        super("monitor", "1.0.0");
    }
    
    @Override
    public void register(Blyfast app) {
        logger.info("Registering Monitor Plugin");
        
        // Register the monitoring middleware
        app.use(monitoringMiddleware());
        
        // Add a monitoring endpoint
        app.get("/monitor/stats", ctx -> {
            ctx.json(getMonitoringData());
        });
        
        // Add a monitoring dashboard with HTML visualization
        app.get("/monitor/dashboard", ctx -> {
            ctx.type("text/html");
            ctx.send(generateDashboardHtml());
        });
        
        // Add static resource routes for individual assets
        app.get("/monitor/dashboard.css", ctx -> {
            ctx.type("text/css");
            ctx.send(loadResourceFromClasspath(DASHBOARD_CSS_PATH));
        });
        
        app.get("/monitor/dashboard.js", ctx -> {
            ctx.type("application/javascript");
            ctx.send(loadResourceFromClasspath(DASHBOARD_JS_PATH));
        });
    }
    
    /**
     * Creates a middleware that monitors requests.
     *
     * @return the middleware
     */
    public Middleware monitoringMiddleware() {
        return ctx -> {
            String path = normalizePath(ctx.request().getPath());
            
            // Skip monitoring for monitoring-related endpoints
            if (path.startsWith("/monitor/")) {
                return true; // Skip metrics tracking but continue processing
            }
            
            long requestStartTime = System.currentTimeMillis();
            
            // Increment counters
            totalRequests.incrementAndGet();
            activeRequests.incrementAndGet();
            
            // Get or create path metrics
            PathMetrics metrics = pathMetrics.computeIfAbsent(path, 
                    p -> new PathMetrics(p));
            metrics.incrementRequests();
            
            // Execute after the handler (this runs before the response is sent)
            ctx.exchange().addExchangeCompleteListener((exchange, nextListener) -> {
                try {
                    long duration = System.currentTimeMillis() - requestStartTime;
                    int statusCode = exchange.getStatusCode();
                    
                    // Update global metrics
                    activeRequests.decrementAndGet();
                    totalResponseTime.addAndGet(duration);
                    
                    if (statusCode >= 400) {
                        errorCount.incrementAndGet();
                        metrics.incrementErrors();
                    }
                    
                    // Update path-specific metrics
                    metrics.recordResponseTime(duration);
                    
                    // Log for high response times
                    if (duration > 1000) {
                        logger.warn("Slow request: {} {} completed in {}ms", 
                                ctx.request().getMethod(), path, duration);
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
     * Loads a resource from the classpath.
     *
     * @param path the path to the resource
     * @return the resource content as a string
     */
    private String loadResourceFromClasspath(String path) {
        try (var inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                logger.error("Resource not found: {}", path);
                return "Resource not found: " + path;
            }
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load resource: {}", path, e);
            return "Error loading resource: " + e.getMessage();
        }
    }
    
    /**
     * Generates the HTML for the monitoring dashboard.
     *
     * @return the HTML content
     */
    private String generateDashboardHtml() {
        String html = loadResourceFromClasspath(DASHBOARD_HTML_PATH);
        String css = loadResourceFromClasspath(DASHBOARD_CSS_PATH);
        String js = loadResourceFromClasspath(DASHBOARD_JS_PATH);
        
        // Replace placeholders in the HTML template
        html = html.replace("<!-- DASHBOARD_CSS_PLACEHOLDER -->", 
                "<style>\n" + css + "\n</style>");
        
        html = html.replace("<!-- DASHBOARD_JS_PLACEHOLDER -->", 
                "<script>\n" + js + "\n</script>");
        
        return html;
    }
    
    /**
     * Gets the monitoring data.
     *
     * @return the monitoring data
     */
    public Map<String, Object> getMonitoringData() {
        Map<String, Object> data = new ConcurrentHashMap<>();
        
        // General stats
        data.put("uptime", System.currentTimeMillis() - startTime);
        data.put("startTime", startTime);
        
        // Request stats
        Map<String, Object> requestStats = new ConcurrentHashMap<>();
        requestStats.put("total", totalRequests.get());
        requestStats.put("active", activeRequests.get());
        requestStats.put("errors", errorCount.get());
        
        double avgResponseTime = totalRequests.get() > 0 ? 
            (double) totalResponseTime.get() / totalRequests.get() : 0;
        requestStats.put("avgResponseTime", avgResponseTime);
        
        data.put("requests", requestStats);
        
        // JVM stats
        Map<String, Object> jvmStats = new ConcurrentHashMap<>();
        jvmStats.put("heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
        jvmStats.put("heapMax", memoryBean.getHeapMemoryUsage().getMax());
        jvmStats.put("nonHeapUsed", memoryBean.getNonHeapMemoryUsage().getUsed());
        jvmStats.put("threadCount", Thread.activeCount());
        jvmStats.put("cpuLoad", osBean.getSystemLoadAverage());
        
        // Add runtime information using runtimeBean
        jvmStats.put("jvmName", runtimeBean.getVmName());
        jvmStats.put("jvmVersion", runtimeBean.getVmVersion());
        jvmStats.put("jvmVendor", runtimeBean.getVmVendor());
        jvmStats.put("jvmUptime", runtimeBean.getUptime());
        jvmStats.put("jvmStartTime", runtimeBean.getStartTime());
        
        data.put("jvm", jvmStats);
        
        // Path metrics
        Map<String, Object> pathData = new ConcurrentHashMap<>();
        for (PathMetrics metric : pathMetrics.values()) {
            pathData.put(metric.getPath(), metric.toMap());
        }
        
        data.put("paths", pathData);
        
        return data;
    }
    
    /**
     * Normalizes a path by removing query parameters and specific IDs.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    private String normalizePath(String path) {
        // Remove query parameters
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        
        // Replace numeric IDs with placeholders
        // This is a simple approach - in a real app, you might want to use
        // the actual route pattern instead of trying to guess
        String[] segments = path.split("/");
        StringBuilder normalized = new StringBuilder();
        
        for (String segment : segments) {
            if (segment.isEmpty()) {
                normalized.append("/");
                continue;
            }
            
            // Check if segment is numeric (likely an ID)
            if (segment.matches("\\d+")) {
                normalized.append(":id");
            } else {
                normalized.append(segment);
            }
            
            normalized.append("/");
        }
        
        // Remove trailing slash if present
        String result = normalized.toString();
        if (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        
        return result;
    }
    
    /**
     * Class representing metrics for a specific path.
     */
    private static class PathMetrics {
        private final String path;
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        
        public PathMetrics(String path) {
            this.path = path;
        }
        
        public void incrementRequests() {
            requestCount.incrementAndGet();
        }
        
        public void incrementErrors() {
            errorCount.incrementAndGet();
        }
        
        public void recordResponseTime(long time) {
            totalResponseTime.addAndGet(time);
            
            // Update min response time
            long currentMin = minResponseTime.get();
            while (time < currentMin) {
                if (minResponseTime.compareAndSet(currentMin, time)) {
                    break;
                }
                currentMin = minResponseTime.get();
            }
            
            // Update max response time
            long currentMax = maxResponseTime.get();
            while (time > currentMax) {
                if (maxResponseTime.compareAndSet(currentMax, time)) {
                    break;
                }
                currentMax = maxResponseTime.get();
            }
        }
        
        public String getPath() {
            return path;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("requests", requestCount.get());
            data.put("errors", errorCount.get());
            
            double avgResponseTime = requestCount.get() > 0 ? 
                (double) totalResponseTime.get() / requestCount.get() : 0;
            
            data.put("avgResponseTime", avgResponseTime);
            data.put("minResponseTime", minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get());
            data.put("maxResponseTime", maxResponseTime.get());
            
            return data;
        }
    }
} 