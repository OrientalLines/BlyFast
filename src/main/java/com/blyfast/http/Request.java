package com.blyfast.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper for HTTP request data that provides a convenient API.
 */
public class Request {
    // Shared ObjectMapper instance configured for performance
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // Cache for deserialization of frequently used types
    private static final ConcurrentHashMap<Class<?>, Boolean> deserializationConfigured = new ConcurrentHashMap<>();

    // Buffer size for reading request bodies
    private static final int BUFFER_SIZE = 8192;
    
    // Reusable ByteBuffer for reading request bodies
    private static final ThreadLocal<ByteBuffer> bufferPool = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(BUFFER_SIZE));

    private HttpServerExchange exchange;
    private String body;
    private JsonNode jsonBody;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> pathParams = new HashMap<>();
    private final Map<String, Object> parsedObjects = new HashMap<>();

    /**
     * Creates a new Request instance wrapped around an HttpServerExchange.
     *
     * @param exchange the underlying exchange
     */
    public Request(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Gets the HTTP method of the request.
     *
     * @return the HTTP method (e.g., GET, POST)
     */
    public String getMethod() {
        return exchange.getRequestMethod().toString();
    }

    /**
     * Gets the path of the request.
     *
     * @return the request path
     */
    public String getPath() {
        return exchange.getRequestPath();
    }

    /**
     * Gets a query parameter by name.
     *
     * @param name the parameter name
     * @return the parameter value or null if not present
     */
    public String getQueryParam(String name) {
        Deque<String> values = exchange.getQueryParameters().get(name);
        return values != null && !values.isEmpty() ? values.getFirst() : null;
    }

    /**
     * Gets all values for a query parameter by name.
     *
     * @param name the parameter name
     * @return the list of parameter values or null if not present
     */
    public Deque<String> getQueryParamValues(String name) {
        return exchange.getQueryParameters().get(name);
    }

    /**
     * Gets a query parameter as an Integer.
     *
     * @param name the parameter name
     * @return the parameter value as an Integer or null if not present or not a valid integer
     */
    public Integer getQueryParamAsInt(String name) {
        String value = getQueryParam(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a query parameter as a Long.
     *
     * @param name the parameter name
     * @return the parameter value as a Long or null if not present or not a valid long
     */
    public Long getQueryParamAsLong(String name) {
        String value = getQueryParam(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a query parameter as a Double.
     *
     * @param name the parameter name
     * @return the parameter value as a Double or null if not present or not a valid double
     */
    public Double getQueryParamAsDouble(String name) {
        String value = getQueryParam(name);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a query parameter as a Boolean.
     * Returns true for "true", "yes", "1", "on" (case insensitive),
     * false for "false", "no", "0", "off" (case insensitive),
     * and null for other values or if the parameter is not present.
     *
     * @param name the parameter name
     * @return the parameter value as a Boolean or null if not present or not a valid boolean
     */
    public Boolean getQueryParamAsBoolean(String name) {
        String value = getQueryParam(name);
        if (value == null) {
            return null;
        }
        value = value.toLowerCase();
        if (value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("on")) {
            return true;
        } else if (value.equals("false") || value.equals("no") || value.equals("0") || value.equals("off")) {
            return false;
        }
        return null;
    }

    /**
     * Gets all query parameters.
     *
     * @return a map of parameter names to values
     */
    public Map<String, String> getQueryParams() {
        Map<String, String> result = new HashMap<>();
        exchange.getQueryParameters().forEach((key, values) -> {
            if (!values.isEmpty()) {
                result.put(key, values.getFirst());
            }
        });
        return result;
    }

    /**
     * Gets a header by name.
     *
     * @param name the header name
     * @return the header value or null if not present
     */
    public String getHeader(String name) {
        HeaderValues values = exchange.getRequestHeaders().get(name);
        return values != null ? values.getFirst() : null;
    }

    /**
     * Gets all headers.
     *
     * @return the header map
     */
    public HeaderMap getHeaders() {
        return exchange.getRequestHeaders();
    }

    /**
     * Gets the raw request body as a string.
     * Uses lazy loading, caching, and optimized I/O for better performance.
     *
     * @return the request body
     * @throws IOException if an I/O error occurs
     */
    public String getBody() throws IOException {
        if (body == null) {
            if (!exchange.isBlocking()) {
                exchange.startBlocking();
            }

            ByteBuffer buffer = bufferPool.get();
            buffer.clear();
            
            try (ReadableByteChannel channel = Channels.newChannel(exchange.getInputStream())) {
                StringBuilder bodyBuilder = new StringBuilder();
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    bodyBuilder.append(new String(bytes, StandardCharsets.UTF_8));
                    buffer.clear();
                }
                body = bodyBuilder.toString();
            }
        }
        return body;
    }

    /**
     * Gets the request body parsed as JSON.
     * Uses caching for better performance.
     *
     * @return the JSON body
     * @throws IOException if an I/O error occurs
     */
    public JsonNode getJsonBody() throws IOException {
        if (jsonBody == null) {
            jsonBody = MAPPER.readTree(getBody());
        }
        return jsonBody;
    }

    /**
     * Deserializes the request body into an object of the specified type.
     * Uses caching for frequently parsed classes.
     *
     * @param clazz the class of the object to deserialize into
     * @param <T>   the type of the object
     * @return the deserialized object
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("unchecked")
    public <T> T parseBody(Class<T> clazz) throws IOException {
        // Check if we've already parsed this body into this class
        Object cached = parsedObjects.get(clazz.getName());
        if (cached != null && clazz.isInstance(cached)) {
            return (T) cached;
        }
        
        // Configure deserialization for this class only once
        deserializationConfigured.computeIfAbsent(clazz, k -> {
            // This block runs only once per class during the application's lifecycle
            MAPPER.findAndRegisterModules(); // Optional: find modules in the classpath 
            return true;
        });
        
        T result = MAPPER.readValue(getBody(), clazz);
        parsedObjects.put(clazz.getName(), result);
        return result;
    }

    /**
     * Sets a path parameter.
     *
     * @param name  the parameter name
     * @param value the parameter value
     */
    public void setPathParam(String name, String value) {
        pathParams.put(name, value);
    }

    /**
     * Gets a path parameter by name.
     *
     * @param name the parameter name
     * @return the parameter value or null if not present
     */
    public String getPathParam(String name) {
        return pathParams.get(name);
    }

    /**
     * Gets all path parameters.
     *
     * @return a map of parameter names to values
     */
    public Map<String, String> getPathParams() {
        return new HashMap<>(pathParams);
    }

    /**
     * Sets a custom attribute on the request.
     *
     * @param name  the attribute name
     * @param value the attribute value
     */
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * Gets a custom attribute by name.
     *
     * @param name the attribute name
     * @return the attribute value or null if not present
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Gets the underlying exchange object.
     *
     * @return the exchange
     */
    public HttpServerExchange getExchange() {
        return exchange;
    }

    /**
     * Resets this request instance for reuse with a new exchange.
     * Used for object pooling to minimize garbage collection.
     *
     * @param exchange the new exchange to use
     * @return this instance for method chaining
     */
    public Request reset(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.body = null;
        this.jsonBody = null;
        this.attributes.clear();
        this.pathParams.clear();
        this.parsedObjects.clear();
        return this;
    }

    /**
     * Gets the shared ObjectMapper instance.
     * This allows other classes to use the same configured instance.
     * 
     * @return the shared ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return MAPPER;
    }
}