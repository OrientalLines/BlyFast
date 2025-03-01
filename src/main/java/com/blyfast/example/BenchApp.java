package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.core.ThreadPool;
import com.blyfast.core.ThreadPool.ThreadPoolConfig;
import com.blyfast.nativeopt.NativeOptimizer;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.postgresql.ds.PGPoolingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance benchmark application for database operations
 * Optimized with connection pooling, prepared statement caching, and native acceleration
 */
public class BenchApp {
    private static final Logger logger = LoggerFactory.getLogger(BenchApp.class);
    
    // Performance metrics
    private static final AtomicLong requestCount = new AtomicLong(0);
    private static final AtomicLong successCount = new AtomicLong(0);
    private static final AtomicLong errorCount = new AtomicLong(0);
    private static final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    // Connection pool
    private static PGPoolingDataSource connectionPool;
    
    // Prepared statement cache using thread-local pattern for thread safety
    private static final ThreadLocal<PreparedStatement> insertStatementCache = new ThreadLocal<>();
    
    // Fast response cache for common responses
    private static final ConcurrentHashMap<String, ByteBuffer> responseCache = new ConcurrentHashMap<>(16);
    
    // Request class for insert operations
    public static class InsertRequest {
        private String textField1;
        private String textField2;
        private String textField3;
        private String textField4;
        private String textField5;
        private int intField1;
        private int intField2;
        private int intField3;
        private int intField4;
        private int intField5;
        private int intField6;
        private boolean boolField1;
        private boolean boolField2;

        // Getters and setters
        public String getTextField1() { return textField1; }
        public void setTextField1(String textField1) { this.textField1 = textField1; }
        public String getTextField2() { return textField2; }
        public void setTextField2(String textField2) { this.textField2 = textField2; }
        public String getTextField3() { return textField3; }
        public void setTextField3(String textField3) { this.textField3 = textField3; }
        public String getTextField4() { return textField4; }
        public void setTextField4(String textField4) { this.textField4 = textField4; }
        public String getTextField5() { return textField5; }
        public void setTextField5(String textField5) { this.textField5 = textField5; }
        public int getIntField1() { return intField1; }
        public void setIntField1(int intField1) { this.intField1 = intField1; }
        public int getIntField2() { return intField2; }
        public void setIntField2(int intField2) { this.intField2 = intField2; }
        public int getIntField3() { return intField3; }
        public void setIntField3(int intField3) { this.intField3 = intField3; }
        public int getIntField4() { return intField4; }
        public void setIntField4(int intField4) { this.intField4 = intField4; }
        public int getIntField5() { return intField5; }
        public void setIntField5(int intField5) { this.intField5 = intField5; }
        public int getIntField6() { return intField6; }
        public void setIntField6(int intField6) { this.intField6 = intField6; }
        public boolean isBoolField1() { return boolField1; }
        public void setBoolField1(boolean boolField1) { this.boolField1 = boolField1; }
        public boolean isBoolField2() { return boolField2; }
        public void setBoolField2(boolean boolField2) { this.boolField2 = boolField2; }
    }

    /**
     * Initialize the database connection pool
     */
    private static void initConnectionPool() {
        try {
            // Use PGPoolingDataSource for high-performance connection pooling
            connectionPool = new PGPoolingDataSource();
            connectionPool.setServerName("localhost");
            connectionPool.setDatabaseName("postgres");
            connectionPool.setUser("postgres");
            connectionPool.setPassword("postgres");
            connectionPool.setPortNumber(5432);
            
            // Optimize connection pool settings
            connectionPool.setInitialConnections(10);
            connectionPool.setMaxConnections(50);
            
            // PostgreSQL connection properties cannot be set directly on PGPoolingDataSource
            // We'll use them when getting a connection
            
            logger.info("Database connection pool initialized with max connections: {}", 50);
            
            // Initialize database schema with one connection
            try (Connection conn = connectionPool.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS benchmark_data (" +
                    "id SERIAL PRIMARY KEY, " +
                    "text_field1 TEXT NOT NULL, " +
                    "text_field2 TEXT NOT NULL, " +
                    "text_field3 TEXT NOT NULL, " +
                    "text_field4 TEXT NOT NULL, " +
                    "text_field5 TEXT NOT NULL, " +
                    "int_field1 INTEGER NOT NULL, " +
                    "int_field2 INTEGER NOT NULL, " +
                    "int_field3 INTEGER NOT NULL, " +
                    "int_field4 INTEGER NOT NULL, " +
                    "int_field5 INTEGER NOT NULL, " +
                    "int_field6 INTEGER NOT NULL, " +
                    "bool_field1 BOOLEAN NOT NULL, " +
                    "bool_field2 BOOLEAN NOT NULL, " +
                    "timestamp_field1 TIMESTAMP WITH TIME ZONE NOT NULL, " +
                    "timestamp_field2 TIMESTAMP WITH TIME ZONE NOT NULL" +
                    ")"
                );
                
                // Create indexes for better query performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_int_field1 ON benchmark_data(int_field1)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_int_field2 ON benchmark_data(int_field2)");
                
                logger.info("Database schema initialized");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Get a prepared statement for inserts, creating it if needed
     */
    private static PreparedStatement getInsertStatement(Connection conn) throws SQLException {
        PreparedStatement stmt = insertStatementCache.get();
        if (stmt == null || stmt.isClosed()) {
            String sql = 
                "INSERT INTO benchmark_data (" +
                "text_field1, text_field2, text_field3, text_field4, text_field5, " +
                "int_field1, int_field2, int_field3, int_field4, int_field5, int_field6, " +
                "bool_field1, bool_field2, timestamp_field1, timestamp_field2" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "RETURNING id";
            
            stmt = conn.prepareStatement(sql);
            insertStatementCache.set(stmt);
        }
        return stmt;
    }
    
    /**
     * Start a metrics reporting thread
     */
    private static void startMetricsReporter() {
        Thread metricsThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long total = requestCount.get();
                    long success = successCount.get();
                    long errors = errorCount.get();
                    long avgTime = total > 0 ? totalProcessingTime.get() / total : 0;
                    
                    logger.info("Performance: {} total requests, {} successful, {} errors, {}ms avg time", 
                            total, success, errors, avgTime);
                    
                    Thread.sleep(5000); // Report every 5 seconds
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "metrics-reporter");
        metricsThread.setDaemon(true);
        metricsThread.start();
    }
    
    /**
     * Pre-cache common response strings for better performance
     */
    private static void initResponseCache() {
        // Cache common responses for better performance
        if (NativeOptimizer.isNativeOptimizationAvailable()) {
            cacheResponse("{\"success\":true}");
            cacheResponse("{\"success\":false}");
            cacheResponse("{\"message\":\"Benchmark API is running\"}");
            cacheResponse("{\"error\":\"Cannot parse request\"}");
        }
    }
    
    /**
     * Cache a response using native optimizations if available
     */
    private static void cacheResponse(String response) {
        if (NativeOptimizer.isNativeOptimizationAvailable()) {
            ByteBuffer buffer = NativeOptimizer.stringToDirectBytes(response);
            responseCache.put(response, buffer.duplicate());
        }
    }

    public static void main(String[] args) {
        try {
            // Initialize connection pool
            initConnectionPool();
            
            // Initialize response cache
            initResponseCache();
            
            // Create server with optimized configuration
            Blyfast server = new Blyfast();
            
            // Create an optimized thread pool that will be used by Blyfast internally
            // Note: We don't explicitly set it on the server as it's created internally
            // The ThreadPoolConfig values match what we've optimized in our native tests
            ThreadPool threadPool = new ThreadPool(new ThreadPoolConfig()
                .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 8)
                .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 16)
                .setQueueCapacity(500000)
                .setKeepAliveTime(java.time.Duration.ofSeconds(20))
                .setUseSynchronousQueue(false)
                .setEnableDynamicScaling(true)
                .setTargetUtilization(0.90)
                .setScalingCheckIntervalMs(1000));
                
            logger.info("Optimized thread pool created with {} core threads, {} max threads",
                threadPool.getConfig().getCorePoolSize(),
                threadPool.getConfig().getMaxPoolSize());
            
            // Start metrics reporting
            startMetricsReporter();
            
            // Root endpoint - just a simple healthcheck
            server.get("/", ctx -> {
                // Use cached response if possible
                ByteBuffer cachedResponse = responseCache.get("{\"message\":\"Benchmark API is running\"}");
                if (cachedResponse != null && NativeOptimizer.isNativeOptimizationAvailable()) {
                    ctx.type("application/json");
                    ctx.exchange().getResponseSender().send(cachedResponse.duplicate());
                } else {
                    ctx.json(Map.of("message", "Benchmark API is running"));
                }
            });
            
            // Ultra-fast ping endpoint for load balancers
            server.get("/ping", ctx -> {
                ctx.send("pong");
            });
            
            // Metrics endpoint
            server.get("/metrics", ctx -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalRequests", requestCount.get());
                metrics.put("successfulRequests", successCount.get());
                metrics.put("failedRequests", errorCount.get());
                metrics.put("avgProcessingTimeMs", 
                        requestCount.get() > 0 ? totalProcessingTime.get() / requestCount.get() : 0);
                
                ctx.json(metrics);
            });
            
            // Insert endpoint - optimized for performance
            server.post("/insert", ctx -> {
                long startTime = System.currentTimeMillis();
                requestCount.incrementAndGet();
                
                try {
                    // Parse request body - native accelerated if available
                    InsertRequest req = ctx.parseBody(InsertRequest.class);
                    
                    // Current time for timestamp fields - use single timestamp for both
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    
                    // Get connection from pool
                    try (Connection conn = connectionPool.getConnection()) {
                        // Get prepared statement from cache
                        PreparedStatement pstmt = getInsertStatement(conn);
                        
                        // Bind parameters - no need to clear parameters as we set all values
                        pstmt.setString(1, req.getTextField1());
                        pstmt.setString(2, req.getTextField2());
                        pstmt.setString(3, req.getTextField3());
                        pstmt.setString(4, req.getTextField4());
                        pstmt.setString(5, req.getTextField5());
                        pstmt.setInt(6, req.getIntField1());
                        pstmt.setInt(7, req.getIntField2());
                        pstmt.setInt(8, req.getIntField3());
                        pstmt.setInt(9, req.getIntField4());
                        pstmt.setInt(10, req.getIntField5());
                        pstmt.setInt(11, req.getIntField6());
                        pstmt.setBoolean(12, req.isBoolField1());
                        pstmt.setBoolean(13, req.isBoolField2());
                        pstmt.setTimestamp(14, now);
                        pstmt.setTimestamp(15, now);
                        
                        // Execute statement
                        try (ResultSet rs = pstmt.executeQuery()) {
                            int id = -1;
                            if (rs.next()) {
                                id = rs.getInt(1);
                            }
                            
                            // Use cached response if available
                            ByteBuffer cachedResponse = responseCache.get("{\"success\":true}");
                            if (id > 0 && cachedResponse != null && NativeOptimizer.isNativeOptimizationAvailable()) {
                                // Fast direct response path
                                ctx.status(200).type("application/json");
                                ctx.exchange().getResponseSender().send(cachedResponse.duplicate());
                            } else {
                                // Return standard response
                                Map<String, Object> response = new HashMap<>(2);
                                response.put("id", id);
                                response.put("success", true);
                                ctx.json(response);
                            }
                            
                            // Update metrics
                            successCount.incrementAndGet();
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Database error: {}", e.getMessage());
                    errorCount.incrementAndGet();
                    
                    ctx.status(500).json(Map.of(
                        "error", e.getMessage(),
                        "success", false
                    ));
                } catch (Exception e) {
                    logger.error("Request processing error: {}", e.getMessage());
                    errorCount.incrementAndGet();
                    
                    ctx.status(400).json(Map.of(
                        "error", "Cannot parse request: " + e.getMessage(),
                        "success", false
                    ));
                } finally {
                    // Record processing time
                    long processingTime = System.currentTimeMillis() - startTime;
                    totalProcessingTime.addAndGet(processingTime);
                }
            });
            
            // Bulk insert endpoint for batch operations
            server.post("/bulk-insert", ctx -> {
                // Implementation omitted for brevity
                ctx.status(501).json(Map.of("message", "Not implemented"));
            });
            
            // Start the server with optimized settings
            server.port(3005).listen();
            logger.info("Benchmark server started on http://localhost:3005");
            
        } catch (Exception e) {
            logger.error("Server startup error", e);
            e.printStackTrace();
        }
    }
}
