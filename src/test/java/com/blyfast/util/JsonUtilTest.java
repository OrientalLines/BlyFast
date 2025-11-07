package com.blyfast.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the JsonUtil class.
 * 
 * <p>Tests JSON serialization, deserialization, and utility methods.</p>
 */
@DisplayName("JsonUtil Tests")
public class JsonUtilTest {

    // Test data classes
    static class TestUser {
        private String name;
        private int age;
        private String email;

        public TestUser() {
        }

        public TestUser(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @Test
    @DisplayName("Should convert object to JSON string")
    void testToJson() throws JsonProcessingException {
        // Given: a simple object
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("age", 30);
        
        // When: converting to JSON
        String json = JsonUtil.toJson(data);
        
        // Then: should produce valid JSON
        assertNotNull(json);
        assertTrue(json.contains("name"));
        assertTrue(json.contains("John"));
        assertTrue(json.contains("age"));
        assertTrue(json.contains("30"));
    }

    @Test
    @DisplayName("Should convert object to pretty-printed JSON")
    void testToPrettyJson() throws JsonProcessingException {
        // Given: a simple object
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("age", 30);
        
        // When: converting to pretty JSON
        String json = JsonUtil.toPrettyJson(data);
        
        // Then: should produce formatted JSON
        assertNotNull(json);
        assertTrue(json.contains("\n")); // Pretty print includes newlines
        assertTrue(json.contains("name"));
        assertTrue(json.contains("John"));
    }

    @Test
    @DisplayName("Should parse JSON string to object")
    void testFromJson() throws IOException {
        // Given: JSON string
        String json = "{\"name\":\"John\",\"age\":30,\"email\":\"john@example.com\"}";
        
        // When: parsing to object
        TestUser user = JsonUtil.fromJson(json, TestUser.class);
        
        // Then: should parse correctly
        assertNotNull(user);
        assertEquals("John", user.getName());
        assertEquals(30, user.getAge());
        assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("Should parse JSON string to list")
    void testFromJsonList() throws IOException {
        // Given: JSON array string
        String json = "[{\"name\":\"John\",\"age\":30},{\"name\":\"Jane\",\"age\":25}]";
        
        // When: parsing to list
        List<TestUser> users = JsonUtil.fromJsonList(json, TestUser.class);
        
        // Then: should parse correctly
        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("John", users.get(0).getName());
        assertEquals(30, users.get(0).getAge());
        assertEquals("Jane", users.get(1).getName());
        assertEquals(25, users.get(1).getAge());
    }

    @Test
    @DisplayName("Should parse JSON string to map")
    void testFromJsonMap() throws IOException {
        // Given: JSON object string
        String json = "{\"name\":\"John\",\"age\":30,\"active\":true}";
        
        // When: parsing to map
        Map<String, Object> map = JsonUtil.fromJsonMap(json);
        
        // Then: should parse correctly
        assertNotNull(map);
        assertEquals("John", map.get("name"));
        assertEquals(30, map.get("age"));
        assertEquals(true, map.get("active"));
    }

    @Test
    @DisplayName("Should parse JSON string to JsonNode")
    void testParseJson() throws IOException {
        // Given: JSON string
        String json = "{\"name\":\"John\",\"age\":30}";
        
        // When: parsing to JsonNode
        JsonNode node = JsonUtil.parseJson(json);
        
        // Then: should parse correctly
        assertNotNull(node);
        assertEquals("John", node.get("name").asText());
        assertEquals(30, node.get("age").asInt());
    }

    @Test
    @DisplayName("Should create empty ObjectNode")
    void testCreateObjectNode() {
        // When: creating ObjectNode
        ObjectNode node = JsonUtil.createObjectNode();
        
        // Then: should create empty node
        assertNotNull(node);
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Should get ObjectMapper instance")
    void testGetMapper() {
        // When: getting mapper
        ObjectMapper mapper = JsonUtil.getMapper();
        
        // Then: should return non-null mapper
        assertNotNull(mapper);
    }

    @Test
    @DisplayName("Should handle null values in JSON")
    void testNullValues() throws JsonProcessingException, IOException {
        // Given: object with null value
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("email", null);
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: null should be preserved
        assertNotNull(parsed);
        assertEquals("John", parsed.get("name"));
        assertNull(parsed.get("email"));
    }

    @Test
    @DisplayName("Should handle nested objects")
    void testNestedObjects() throws JsonProcessingException, IOException {
        // Given: nested object structure
        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Main St");
        address.put("city", "New York");
        
        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("address", address);
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(user);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: nested structure should be preserved
        assertNotNull(parsed);
        assertEquals("John", parsed.get("name"));
        assertNotNull(parsed.get("address"));
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedAddress = (Map<String, Object>) parsed.get("address");
        assertEquals("123 Main St", parsedAddress.get("street"));
        assertEquals("New York", parsedAddress.get("city"));
    }

    @Test
    @DisplayName("Should handle arrays")
    void testArrays() throws JsonProcessingException, IOException {
        // Given: object with array
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("tags", new String[]{"java", "testing", "json"});
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: array should be preserved
        assertNotNull(parsed);
        assertEquals("John", parsed.get("name"));
        assertNotNull(parsed.get("tags"));
    }

    @Test
    @DisplayName("Should handle empty JSON object")
    void testEmptyObject() throws JsonProcessingException, IOException {
        // Given: empty map
        Map<String, Object> data = new HashMap<>();
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: should handle empty object
        assertNotNull(parsed);
        assertTrue(parsed.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty JSON array")
    void testEmptyArray() throws IOException {
        // Given: empty JSON array
        String json = "[]";
        
        // When: parsing to list
        List<TestUser> users = JsonUtil.fromJsonList(json, TestUser.class);
        
        // Then: should return empty list
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void testInvalidJson() {
        // Given: invalid JSON string
        String invalidJson = "{name: John}"; // Missing quotes
        
        // When/Then: should throw exception
        assertThrows(IOException.class, () -> {
            JsonUtil.fromJson(invalidJson, TestUser.class);
        });
    }

    @Test
    @DisplayName("Should handle special characters in JSON")
    void testSpecialCharacters() throws JsonProcessingException, IOException {
        // Given: object with special characters
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello \"World\"\nNew Line\tTab");
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: special characters should be escaped/unescaped correctly
        assertNotNull(parsed);
        assertNotNull(parsed.get("message"));
    }

    @Test
    @DisplayName("Should handle numeric types correctly")
    void testNumericTypes() throws JsonProcessingException, IOException {
        // Given: object with various numeric types
        Map<String, Object> data = new HashMap<>();
        data.put("intValue", 42);
        data.put("longValue", 123456789L);
        data.put("doubleValue", 3.14);
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: numeric types should be preserved
        assertNotNull(parsed);
        assertEquals(42, parsed.get("intValue"));
        // Note: JSON numbers are parsed as Integer for values that fit, Long for larger values
        Object longValue = parsed.get("longValue");
        assertTrue(longValue instanceof Number);
        assertEquals(123456789L, ((Number) longValue).longValue());
        assertEquals(3.14, parsed.get("doubleValue"));
    }

    @Test
    @DisplayName("Should handle boolean values")
    void testBooleanValues() throws JsonProcessingException, IOException {
        // Given: object with boolean values
        Map<String, Object> data = new HashMap<>();
        data.put("active", true);
        data.put("verified", false);
        
        // When: converting to JSON and back
        String json = JsonUtil.toJson(data);
        Map<String, Object> parsed = JsonUtil.fromJsonMap(json);
        
        // Then: boolean values should be preserved
        assertNotNull(parsed);
        assertEquals(true, parsed.get("active"));
        assertEquals(false, parsed.get("verified"));
    }
}

