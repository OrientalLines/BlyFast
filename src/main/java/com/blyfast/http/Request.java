package com.blyfast.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.blyfast.nativeopt.NativeOptimizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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
    private ByteBuffer rawBodyBuffer;
    private int bodyLength;
    private int bodyType = -1; // -1=unknown, >= 0 means analyzed
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
            // First load the raw body if we haven't already
            if (rawBodyBuffer == null) {
                loadRawBody();
            }
            
            // Convert to string using native methods if available
            if (NativeOptimizer.isNativeOptimizationAvailable()) {
                byte[] bytes = new byte[bodyLength];
                rawBodyBuffer.position(0);
                rawBodyBuffer.get(bytes, 0, bodyLength);
                body = new String(bytes, StandardCharsets.UTF_8);
            } else {
                // Fallback to Java implementation
                rawBodyBuffer.position(0);
                byte[] bytes = new byte[bodyLength];
                rawBodyBuffer.get(bytes, 0, bodyLength);
                body = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return body;
    }
    
    /**
     * Loads the raw request body into a direct ByteBuffer.
     * This is used internally by body parsing methods.
     *
     * @throws IOException if an I/O error occurs
     */
    private void loadRawBody() throws IOException {
        if (!exchange.isBlocking()) {
            exchange.startBlocking();
        }
        
        ByteBuffer buffer = bufferPool.get();
        buffer.clear();
        
        // First read, to see if we can fit it all in the initial buffer
        try (ReadableByteChannel channel = Channels.newChannel(exchange.getInputStream())) {
            int bytesRead = channel.read(buffer);
            if (bytesRead < 0) bytesRead = 0;
            
            // If it all fits in the initial buffer
            if (bytesRead >= 0 && buffer.position() < buffer.capacity() && bytesRead < buffer.capacity()) {
                bodyLength = bytesRead;
                buffer.flip();
                rawBodyBuffer = ByteBuffer.allocateDirect(bodyLength);
                rawBodyBuffer.put(buffer);
                rawBodyBuffer.flip();
                return;
            }
            
            // If it doesn't fit, we need to resize
            ByteBuffer resizedBuffer = ByteBuffer.allocateDirect(Math.max(bytesRead * 2, 16384)); // At least double
            buffer.flip();
            resizedBuffer.put(buffer);
            
            // Continue reading
            while (true) {
                buffer.clear();
                int read = channel.read(buffer);
                if (read <= 0) break;
                
                buffer.flip();
                
                // If resizedBuffer is too small, resize it again
                if (resizedBuffer.position() + buffer.remaining() > resizedBuffer.capacity()) {
                    ByteBuffer newBuffer = ByteBuffer.allocateDirect(resizedBuffer.capacity() * 2);
                    resizedBuffer.flip();
                    newBuffer.put(resizedBuffer);
                    resizedBuffer = newBuffer;
                }
                
                resizedBuffer.put(buffer);
            }
            
            bodyLength = resizedBuffer.position();
            resizedBuffer.flip();
            rawBodyBuffer = resizedBuffer;
        }
    }

    /**
     * Gets the request body parsed as JSON.
     * Uses native parsing for better performance if available.
     *
     * @return the JSON body
     * @throws IOException if an I/O error occurs
     */
    public JsonNode getJsonBody() throws IOException {
        if (jsonBody == null) {
            // First, try native optimization if available
            if (NativeOptimizer.isNativeOptimizationAvailable()) {
                // Load raw body if not already loaded
                if (rawBodyBuffer == null) {
                    loadRawBody();
                }
                
                // Detect content type if not already done
                detectBodyType();
                
                // If it's JSON, use native JSON parsing
                if (bodyType == 1) { // JSON
                    try {
                        // Use the body string (either cached or generated)
                        jsonBody = MAPPER.readTree(getBody());
                        return jsonBody;
                    } catch (Exception e) {
                        // Fallback to standard parsing if native fails
                    }
                }
            }
            
            // Fallback to standard Jackson parsing
            jsonBody = MAPPER.readTree(getBody());
        }
        return jsonBody;
    }
    
    /**
     * Detects the body type using native optimization if available.
     * This method analyzes the body content and caches the result.
     */
    private void detectBodyType() {
        if (bodyType != -1) {
            return; // Already detected
        }
        
        if (NativeOptimizer.isNativeOptimizationAvailable() && rawBodyBuffer != null) {
            // Try to get Content-Type header first
            String contentType = getHeader("Content-Type");
            if (contentType != null) {
                bodyType = NativeOptimizer.nativeAnalyzeHttpBody(rawBodyBuffer, bodyLength, contentType);
            }
            
            // If couldn't determine from Content-Type or no Content-Type header
            if (bodyType <= 0) {
                // Try to detect from content
                bodyType = NativeOptimizer.nativeFastDetectContentType(rawBodyBuffer, bodyLength);
            }
        } else {
            // Default to unknown
            bodyType = 0;
        }
    }

    /**
     * Deserializes the request body into an object of the specified type.
     * Uses native optimization for common types if available.
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
        
        // Try native optimization for form data
        if (NativeOptimizer.isNativeOptimizationAvailable() && clazz == Map.class) {
            if (rawBodyBuffer == null) {
                loadRawBody();
            }
            
            detectBodyType();
            
            // Form data (application/x-www-form-urlencoded)
            if (bodyType == 2) {
                try {
                    ByteBuffer parsedBuffer = NativeOptimizer.nativeFastParseBody(rawBodyBuffer, bodyLength, bodyType);
                    if (parsedBuffer != null) {
                        Map<String, String> result = parseFormDataBuffer(parsedBuffer);
                        parsedObjects.put(clazz.getName(), result);
                        return (T) result;
                    }
                } catch (Exception e) {
                    // Fallback to standard parsing
                }
            }
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
     * Parses a pre-processed form data buffer (from native code) into a Map.
     * 
     * @param buffer the buffer containing pre-processed form data
     * @return a Map of form field names to values
     */
    private Map<String, String> parseFormDataBuffer(ByteBuffer buffer) {
        Map<String, String> result = new HashMap<>();
        buffer.position(0);
        
        while (buffer.hasRemaining()) {
            // Read key length and value length
            int keyLength = buffer.getInt();
            if (!buffer.hasRemaining()) break;
            
            // Read key
            byte[] keyBytes = new byte[keyLength];
            buffer.get(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            
            if (!buffer.hasRemaining()) break;
            
            // Read value length
            int valueLength = buffer.getInt();
            if (!buffer.hasRemaining() && valueLength > 0) break;
            
            // Read value
            byte[] valueBytes = new byte[valueLength];
            buffer.get(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);
            
            result.put(key, value);
        }
        
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
     * Parse multipart form data from the request body.
     * Uses native optimizations for better performance when available.
     *
     * @return a map containing form fields and file data
     * @throws IOException if an I/O error occurs
     */
    public MultipartData parseMultipartData() throws IOException {
        // Check if we've already parsed multipart data
        Object cached = parsedObjects.get(MultipartData.class.getName());
        if (cached != null && cached instanceof MultipartData) {
            return (MultipartData) cached;
        }
        
        // Ensure raw body is loaded
        if (rawBodyBuffer == null) {
            loadRawBody();
        }
        
        // Detect body type if not already done
        detectBodyType();
        
        MultipartData result = new MultipartData();
        
        // If this is indeed multipart data and native optimization is available
        if (bodyType == 3 && NativeOptimizer.isNativeOptimizationAvailable()) {
            try {
                ByteBuffer parsedBuffer = NativeOptimizer.nativeFastParseBody(rawBodyBuffer, bodyLength, bodyType);
                if (parsedBuffer != null) {
                    // Parse the pre-processed buffer from native code
                    parsedBuffer.position(0);
                    
                    // Read part count
                    int partCount = parsedBuffer.getInt();
                    
                    // Parse each part
                    for (int i = 0; i < partCount; i++) {
                        // Read header lengths and is_file flag
                        int nameLen = parsedBuffer.getInt();
                        int filenameLen = parsedBuffer.getInt();
                        int contentTypeLen = parsedBuffer.getInt();
                        int dataLen = parsedBuffer.getInt();
                        boolean isFile = parsedBuffer.get() != 0;
                        
                        // Read name
                        String name = "";
                        if (nameLen > 0) {
                            byte[] nameBytes = new byte[nameLen];
                            parsedBuffer.get(nameBytes);
                            name = new String(nameBytes, StandardCharsets.UTF_8);
                        }
                        
                        // Read filename if present
                        String filename = null;
                        if (filenameLen > 0) {
                            byte[] filenameBytes = new byte[filenameLen];
                            parsedBuffer.get(filenameBytes);
                            filename = new String(filenameBytes, StandardCharsets.UTF_8);
                        }
                        
                        // Read content type if present
                        String contentType = null;
                        if (contentTypeLen > 0) {
                            byte[] contentTypeBytes = new byte[contentTypeLen];
                            parsedBuffer.get(contentTypeBytes);
                            contentType = new String(contentTypeBytes, StandardCharsets.UTF_8);
                        }
                        
                        // Read data
                        byte[] data = new byte[dataLen];
                        parsedBuffer.get(data);
                        
                        if (isFile) {
                            // Handle file part
                            MultipartFile file = new MultipartFile(name, filename, contentType, data);
                            result.addFile(name, file);
                        } else {
                            // Handle field part
                            String value = new String(data, StandardCharsets.UTF_8);
                            result.addField(name, value);
                        }
                        
                        // Skip any alignment padding
                        while (parsedBuffer.position() % 4 != 0 && parsedBuffer.hasRemaining()) {
                            parsedBuffer.get();
                        }
                    }
                    
                    // Cache result
                    parsedObjects.put(MultipartData.class.getName(), result);
                    return result;
                }
            } catch (Exception e) {
                // Fallback to Java implementation
            }
        }
        
        // Fallback to Java implementation for parsing multipart data
        // This is a simplified implementation - in a production environment
        // you would want a more robust multipart parser
        
        // Extract boundary from Content-Type header
        String contentType = getHeader("Content-Type");
        String boundary = null;
        if (contentType != null && contentType.contains("boundary=")) {
            boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            // Remove quotes if present
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            // In case there are additional parameters after the boundary
            if (boundary.contains(";")) {
                boundary = boundary.substring(0, boundary.indexOf(";"));
            }
        }
        
        if (boundary == null) {
            // Can't parse without a boundary
            return result;
        }
        
        // Parse multipart data using Java
        try {
            // Get the raw body as bytes
            byte[] bodyBytes = new byte[bodyLength];
            rawBodyBuffer.position(0);
            rawBodyBuffer.get(bodyBytes);
            
            // Prepare boundary markers
            String startBoundary = "--" + boundary;
            String endBoundary = "--" + boundary + "--";
            
            // Convert body to string for easier parsing
            String bodyStr = new String(bodyBytes, StandardCharsets.ISO_8859_1);
            
            // Find all part boundaries
            int pos = 0;
            while (pos < bodyStr.length()) {
                // Find next boundary
                int boundaryPos = bodyStr.indexOf(startBoundary, pos);
                if (boundaryPos == -1) {
                    break; // No more boundaries
                }
                
                // Check if this is the end boundary
                if (bodyStr.startsWith(endBoundary, boundaryPos)) {
                    break; // End of multipart data
                }
                
                // Move past boundary to headers
                int headerStart = boundaryPos + startBoundary.length();
                if (bodyStr.charAt(headerStart) == '\r' && bodyStr.charAt(headerStart + 1) == '\n') {
                    headerStart += 2; // Skip CRLF
                }
                
                // Find end of headers (double CRLF)
                int headerEnd = bodyStr.indexOf("\r\n\r\n", headerStart);
                if (headerEnd == -1) {
                    break; // Malformed data
                }
                
                // Extract and parse headers
                String headersStr = bodyStr.substring(headerStart, headerEnd);
                String[] headerLines = headersStr.split("\r\n");
                
                String name = null;
                String filename = null;
                String partContentType = null;
                
                // Parse headers
                for (String headerLine : headerLines) {
                    // Parse Content-Disposition header
                    if (headerLine.startsWith("Content-Disposition:")) {
                        String[] parts = headerLine.split(";");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.startsWith("name=")) {
                                name = extractQuotedValue(part.substring(5));
                            } else if (part.startsWith("filename=")) {
                                filename = extractQuotedValue(part.substring(9));
                            }
                        }
                    }
                    // Parse Content-Type header
                    else if (headerLine.startsWith("Content-Type:")) {
                        partContentType = headerLine.substring(13).trim();
                    }
                }
                
                // Content start is after headers
                int contentStart = headerEnd + 4; // Skip "\r\n\r\n"
                
                // Find next boundary
                int nextBoundaryPos = bodyStr.indexOf(startBoundary, contentStart);
                if (nextBoundaryPos == -1) {
                    nextBoundaryPos = bodyStr.length(); // Use end of data
                }
                
                // Extract content, adjusting for CRLF before boundary
                int contentEnd = nextBoundaryPos;
                if (contentEnd >= 2 && bodyStr.charAt(contentEnd - 2) == '\r' && bodyStr.charAt(contentEnd - 1) == '\n') {
                    contentEnd -= 2; // Exclude CRLF before boundary
                }
                
                // Extract content
                String content = bodyStr.substring(contentStart, contentEnd);
                byte[] contentBytes = content.getBytes(StandardCharsets.ISO_8859_1);
                
                // Add to result
                if (name != null) {
                    if (filename != null) {
                        // This is a file
                        MultipartFile file = new MultipartFile(name, filename, partContentType, contentBytes);
                        result.addFile(name, file);
                    } else {
                        // This is a field
                        String value = new String(contentBytes, StandardCharsets.UTF_8);
                        result.addField(name, value);
                    }
                }
                
                // Move to next part
                pos = nextBoundaryPos;
            }
        } catch (Exception e) {
            // Ignore parsing errors, return what we have
        }
        
        // Cache result
        parsedObjects.put(MultipartData.class.getName(), result);
        return result;
    }

    /**
     * Helper method to extract a quoted value from a string
     * 
     * @param value the input string (e.g. "value" or value)
     * @return the extracted value without quotes
     */
    private String extractQuotedValue(String value) {
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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

    /**
     * MultipartData class for handling multipart/form-data contents
     */
    public static class MultipartData {
        private final Map<String, String> fields = new HashMap<>();
        private final Map<String, MultipartFile> files = new HashMap<>();
        private final List<MultipartFile> allFiles = new ArrayList<>();
        
        /**
         * Add a field to the multipart data
         * 
         * @param name field name
         * @param value field value
         */
        public void addField(String name, String value) {
            fields.put(name, value);
        }
        
        /**
         * Add a file to the multipart data
         * 
         * @param name field name
         * @param file the uploaded file
         */
        public void addFile(String name, MultipartFile file) {
            files.put(name, file);
            allFiles.add(file);
        }
        
        /**
         * Get field value by name
         * 
         * @param name field name
         * @return field value or null if not found
         */
        public String getField(String name) {
            return fields.get(name);
        }
        
        /**
         * Get all field values
         * 
         * @return map of field names to values
         */
        public Map<String, String> getFields() {
            return Collections.unmodifiableMap(fields);
        }
        
        /**
         * Get file by field name
         * 
         * @param name field name
         * @return file or null if not found
         */
        public MultipartFile getFile(String name) {
            return files.get(name);
        }
        
        /**
         * Get all files
         * 
         * @return list of all uploaded files
         */
        public List<MultipartFile> getFiles() {
            return Collections.unmodifiableList(allFiles);
        }
        
        /**
         * Check if a field exists
         * 
         * @param name field name
         * @return true if the field exists
         */
        public boolean hasField(String name) {
            return fields.containsKey(name);
        }
        
        /**
         * Check if a file exists
         * 
         * @param name field name
         * @return true if the file exists
         */
        public boolean hasFile(String name) {
            return files.containsKey(name);
        }
    }
    
    /**
     * Represents a file uploaded via multipart/form-data
     */
    public static class MultipartFile {
        private final String fieldName;
        private final String filename;
        private final String contentType;
        private final byte[] data;
        
        /**
         * Create a new MultipartFile
         * 
         * @param fieldName the form field name
         * @param filename the original filename
         * @param contentType the content type
         * @param data the file data
         */
        public MultipartFile(String fieldName, String filename, String contentType, byte[] data) {
            this.fieldName = fieldName;
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }
        
        /**
         * Get the form field name
         * 
         * @return field name
         */
        public String getFieldName() {
            return fieldName;
        }
        
        /**
         * Get the original filename
         * 
         * @return filename
         */
        public String getFilename() {
            return filename;
        }
        
        /**
         * Get the content type
         * 
         * @return content type
         */
        public String getContentType() {
            return contentType;
        }
        
        /**
         * Get the file data
         * 
         * @return file data
         */
        public byte[] getData() {
            return data;
        }
        
        /**
         * Get the file size
         * 
         * @return file size in bytes
         */
        public int getSize() {
            return data != null ? data.length : 0;
        }
        
        /**
         * Save the file to a specified path
         * 
         * @param path the destination path
         * @throws IOException if an I/O error occurs
         */
        public void saveTo(Path path) throws IOException {
            Files.write(path, data);
        }
        
        /**
         * Get the file content as an input stream
         * 
         * @return input stream containing the file data
         */
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }
    }
}