package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.core.ThreadPool;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.postgresql.ds.PGPoolingDataSource;

/**
 * High-performance benchmark application for database operations
 * Optimized with connection pooling and native acceleration
 */
public class BenchApp {
    // Connection pool
    private static PGPoolingDataSource connectionPool;

    // Request class for insert operations
    public static class InsertRequest {
        @JsonProperty("text_field1")
        private String textField1;
        @JsonProperty("text_field2")
        private String textField2;
        @JsonProperty("text_field3")
        private String textField3;
        @JsonProperty("text_field4")
        private String textField4;
        @JsonProperty("text_field5")
        private String textField5;
        @JsonProperty("int_field1")
        private int intField1;
        @JsonProperty("int_field2")
        private int intField2;
        @JsonProperty("int_field3")
        private int intField3;
        @JsonProperty("int_field4")
        private int intField4;
        @JsonProperty("int_field5")
        private int intField5;
        @JsonProperty("int_field6")
        private int intField6;
        @JsonProperty("bool_field1")
        private boolean boolField1;
        @JsonProperty("bool_field2")
        private boolean boolField2;

        // Getters and setters
        public String getTextField1() {
            return textField1;
        }

        public void setTextField1(String textField1) {
            this.textField1 = textField1;
        }

        public String getTextField2() {
            return textField2;
        }

        public void setTextField2(String textField2) {
            this.textField2 = textField2;
        }

        public String getTextField3() {
            return textField3;
        }

        public void setTextField3(String textField3) {
            this.textField3 = textField3;
        }

        public String getTextField4() {
            return textField4;
        }

        public void setTextField4(String textField4) {
            this.textField4 = textField4;
        }

        public String getTextField5() {
            return textField5;
        }

        public void setTextField5(String textField5) {
            this.textField5 = textField5;
        }

        public int getIntField1() {
            return intField1;
        }

        public void setIntField1(int intField1) {
            this.intField1 = intField1;
        }

        public int getIntField2() {
            return intField2;
        }

        public void setIntField2(int intField2) {
            this.intField2 = intField2;
        }

        public int getIntField3() {
            return intField3;
        }

        public void setIntField3(int intField3) {
            this.intField3 = intField3;
        }

        public int getIntField4() {
            return intField4;
        }

        public void setIntField4(int intField4) {
            this.intField4 = intField4;
        }

        public int getIntField5() {
            return intField5;
        }

        public void setIntField5(int intField5) {
            this.intField5 = intField5;
        }

        public int getIntField6() {
            return intField6;
        }

        public void setIntField6(int intField6) {
            this.intField6 = intField6;
        }

        public boolean isBoolField1() {
            return boolField1;
        }

        public void setBoolField1(boolean boolField1) {
            this.boolField1 = boolField1;
        }

        public boolean isBoolField2() {
            return boolField2;
        }

        public void setBoolField2(boolean boolField2) {
            this.boolField2 = boolField2;
        }
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
                                ")");

                // Create indexes for better query performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_int_field1 ON benchmark_data(int_field1)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_int_field2 ON benchmark_data(int_field2)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Get a prepared statement for inserts
     */
    private static PreparedStatement createInsertStatement(Connection conn) throws SQLException {
        String sql = "INSERT INTO benchmark_data (" +
                "text_field1, text_field2, text_field3, text_field4, text_field5, " +
                "int_field1, int_field2, int_field3, int_field4, int_field5, int_field6, " +
                "bool_field1, bool_field2, timestamp_field1, timestamp_field2" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "RETURNING id";

        return conn.prepareStatement(sql);
    }

    public static void main(String[] args) {
        try {
            // Initialize connection pool
            initConnectionPool();

            ThreadPool.ThreadPoolConfig config = new ThreadPool.ThreadPoolConfig()
                    .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 10)
                    .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 20)
                    .setQueueCapacity(500000)
                    .setPrestartCoreThreads(true)
                    .setUseSynchronousQueue(false)
                    .setUseWorkStealing(true)
                    .setEnableDynamicScaling(true);

            // Create server with optimized configuration
            Blyfast server = new Blyfast(config);

            // Insert endpoint
            server.post("/insert", ctx -> {
                try {
                    // Parse request body
                    InsertRequest req = ctx.parseBody(InsertRequest.class);

                    // Current time for timestamp fields - use single timestamp for both
                    Timestamp now = new Timestamp(System.currentTimeMillis());

                    // Get connection from pool
                    try (Connection conn = connectionPool.getConnection();
                            PreparedStatement pstmt = createInsertStatement(conn)) {

                        // Bind parameters
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

                            // Return response
                            Map<String, Object> response = new HashMap<>(2);
                            response.put("id", id);
                            response.put("success", true);
                            ctx.json(response);
                        }
                    }
                } catch (SQLException e) {
                    ctx.status(500).json(Map.of(
                            "error", e.getMessage(),
                            "success", false));
                } catch (Exception e) {
                    ctx.status(400).json(Map.of(
                            "error", "Cannot parse request: " + e.getMessage(),
                            "success", false));
                }
            });

            // Start the server with optimized settings
            server.port(3005).listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
