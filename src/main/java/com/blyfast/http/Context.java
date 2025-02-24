package com.blyfast.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Context for an HTTP request/response cycle.
 * Provides convenient access to both the request and response objects.
 */
public class Context {
    private final Request request;
    private final Response response;
    private final Map<String, Object> locals = new HashMap<>();

    /**
     * Creates a new context with the given request and response.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public Context(Request request, Response response) {
        this.request = request;
        this.response = response;
    }

    /**
     * Gets the request object.
     *
     * @return the request
     */
    public Request request() {
        return request;
    }

    /**
     * Gets the response object.
     *
     * @return the response
     */
    public Response response() {
        return response;
    }

    /**
     * Gets the underlying exchange object.
     *
     * @return the exchange
     */
    public HttpServerExchange exchange() {
        return request.getExchange();
    }

    /**
     * Gets a path parameter by name.
     *
     * @param name the parameter name
     * @return the parameter value
     */
    public String param(String name) {
        return request.getPathParam(name);
    }

    /**
     * Gets a query parameter by name.
     *
     * @param name the parameter name
     * @return the parameter value
     */
    public String query(String name) {
        return request.getQueryParam(name);
    }

    /**
     * Gets a header by name.
     *
     * @param name the header name
     * @return the header value
     */
    public String header(String name) {
        return request.getHeader(name);
    }

    /**
     * Gets the request body as a string.
     *
     * @return the body string
     * @throws IOException if an I/O error occurs
     */
    public String body() throws IOException {
        return request.getBody();
    }

    /**
     * Gets the request body as JSON.
     *
     * @return the JSON body
     * @throws IOException if an I/O error occurs
     */
    public JsonNode json() throws IOException {
        return request.getJsonBody();
    }

    /**
     * Parses the request body into an object of the specified type.
     *
     * @param clazz the class to parse into
     * @param <T>   the type of the object
     * @return the parsed object
     * @throws IOException if an I/O error occurs
     */
    public <T> T parseBody(Class<T> clazz) throws IOException {
        return request.parseBody(clazz);
    }

    /**
     * Sets the response status code.
     *
     * @param status the status code
     * @return this context for method chaining
     */
    public Context status(int status) {
        response.status(status);
        return this;
    }

    /**
     * Sets a response header.
     *
     * @param name  the header name
     * @param value the header value
     * @return this context for method chaining
     */
    public Context header(String name, String value) {
        response.header(name, value);
        return this;
    }

    /**
     * Sets the Content-Type header.
     *
     * @param contentType the content type
     * @return this context for method chaining
     */
    public Context type(String contentType) {
        response.type(contentType);
        return this;
    }

    /**
     * Sends a text response.
     *
     * @param text the text to send
     * @return this context for method chaining
     */
    public Context send(String text) {
        response.send(text);
        return this;
    }

    /**
     * Sends a JSON response.
     *
     * @param json the JSON string to send
     * @return this context for method chaining
     */
    public Context json(String json) {
        response.json(json);
        return this;
    }

    /**
     * Serializes an object to JSON and sends it as a response.
     *
     * @param obj the object to serialize
     * @return this context for method chaining
     */
    public Context json(Object obj) {
        response.json(obj);
        return this;
    }

    /**
     * Stores a value in the context locals for the current request/response cycle.
     *
     * @param key   the key
     * @param value the value
     * @return this context for method chaining
     */
    public Context set(String key, Object value) {
        locals.put(key, value);
        return this;
    }

    /**
     * Gets a value from the context locals.
     *
     * @param key the key
     * @param <T> the type of the value
     * @return the value or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) locals.get(key);
    }

    /**
     * Sends a standardized error response.
     *
     * @param status  the HTTP status code
     * @param message the error message
     * @return this context for method chaining
     */
    public Context error(int status, String message) {
        response.error(status, message);
        return this;
    }

    /**
     * Redirects to the specified URL.
     *
     * @param url the URL to redirect to
     * @return this context for method chaining
     */
    public Context redirect(String url) {
        response.redirect(url);
        return this;
    }
}