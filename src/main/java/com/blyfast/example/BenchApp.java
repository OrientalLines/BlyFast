package com.blyfast.example;

import com.blyfast.core.Blyfast;
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

/**
 * Benchmark application for database operations
 */
public class BenchApp {
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

    public static void main(String[] args) {
        // Database connection
        final Connection conn;
        try {
            // Connect to PostgreSQL
            conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/postgres", 
                "postgres", 
                "postgres"
            );
            
            // Create table if not exists
            try (Statement stmt = conn.createStatement()) {
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
            }
            
            // Create the BlyFast server
            Blyfast server = new Blyfast();
            
            // Root endpoint
            server.get("/", ctx -> {
                ctx.json(Map.of("message", "Benchmark API is running"));
            });
            
            // Insert endpoint
            server.post("/insert", ctx -> {
                try {
                    // Parse request body
                    InsertRequest req = ctx.parseBody(InsertRequest.class);
                    
                    // Current time for timestamp fields
                    Timestamp now = Timestamp.from(Instant.now());
                    
                    // Insert data
                    String sql = 
                        "INSERT INTO benchmark_data (" +
                        "text_field1, text_field2, text_field3, text_field4, text_field5, " +
                        "int_field1, int_field2, int_field3, int_field4, int_field5, int_field6, " +
                        "bool_field1, bool_field2, timestamp_field1, timestamp_field2" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "RETURNING id";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                        
                        ResultSet rs = pstmt.executeQuery();
                        int id = -1;
                        if (rs.next()) {
                            id = rs.getInt(1);
                        }
                        
                        // Return response
                        Map<String, Object> response = new HashMap<>();
                        response.put("id", id);
                        response.put("success", true);
                        ctx.json(response);
                    }
                } catch (SQLException e) {
                    ctx.status(500).json(Map.of(
                        "error", e.getMessage(),
                        "success", false
                    ));
                } catch (Exception e) {
                    ctx.status(400).json(Map.of(
                        "error", "Cannot parse request: " + e.getMessage(),
                        "success", false
                    ));
                }
            });
            
            // Start the server
            server.port(3005).listen();
            System.out.println("Benchmark server started on http://localhost:3005");
            
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
