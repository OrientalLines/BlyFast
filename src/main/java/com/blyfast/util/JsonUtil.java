package com.blyfast.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility class for working with JSON.
 */
public class JsonUtil {
    private static final ObjectMapper mapper;
    
    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }
    
    /**
     * Converts an object to a JSON string.
     *
     * @param obj the object to convert
     * @return the JSON string
     * @throws JsonProcessingException if the conversion fails
     */
    public static String toJson(Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }
    
    /**
     * Converts an object to a pretty-printed JSON string.
     *
     * @param obj the object to convert
     * @return the pretty-printed JSON string
     * @throws JsonProcessingException if the conversion fails
     */
    public static String toPrettyJson(Object obj) throws JsonProcessingException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }
    
    /**
     * Parses a JSON string into an object of the specified type.
     *
     * @param json the JSON string
     * @param clazz the class of the object
     * @param <T> the type of the object
     * @return the parsed object
     * @throws IOException if the parsing fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        return mapper.readValue(json, clazz);
    }
    
    /**
     * Parses a JSON string into a list of objects of the specified type.
     *
     * @param json the JSON string
     * @param clazz the class of the objects in the list
     * @param <T> the type of the objects in the list
     * @return the parsed list
     * @throws IOException if the parsing fails
     */
    public static <T> List<T> fromJsonList(String json, Class<T> clazz) throws IOException {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }
    
    /**
     * Parses a JSON string into a map.
     *
     * @param json the JSON string
     * @return the parsed map
     * @throws IOException if the parsing fails
     */
    public static Map<String, Object> fromJsonMap(String json) throws IOException {
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
    
    /**
     * Parses a JSON string into a JsonNode.
     *
     * @param json the JSON string
     * @return the parsed JsonNode
     * @throws IOException if the parsing fails
     */
    public static JsonNode parseJson(String json) throws IOException {
        return mapper.readTree(json);
    }
    
    /**
     * Creates a new empty ObjectNode.
     *
     * @return the created ObjectNode
     */
    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }
    
    /**
     * Gets the ObjectMapper instance.
     *
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getMapper() {
        return mapper;
    }
} 