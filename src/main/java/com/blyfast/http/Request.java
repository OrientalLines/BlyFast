package com.blyfast.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wrapper for HTTP request data that provides a convenient API.
 */
public class Request {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final HttpServerExchange exchange;
    private String body;
    private JsonNode jsonBody;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> pathParams = new HashMap<>();

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
     *
     * @return the request body
     * @throws IOException if an I/O error occurs
     */
    public String getBody() throws IOException {
        if (body == null) {
            if (exchange.isBlocking()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8))) {
                    body = reader.lines().collect(Collectors.joining());
                }
            } else {
                exchange.startBlocking();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8))) {
                    body = reader.lines().collect(Collectors.joining());
                }
            }
        }
        return body;
    }

    /**
     * Gets the request body parsed as JSON.
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
     *
     * @param clazz the class of the object to deserialize into
     * @param <T> the type of the object
     * @return the deserialized object
     * @throws IOException if an I/O error occurs
     */
    public <T> T parseBody(Class<T> clazz) throws IOException {
        return MAPPER.readValue(getBody(), clazz);
    }

    /**
     * Sets a path parameter.
     *
     * @param name the parameter name
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
     * @param name the attribute name
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
} 