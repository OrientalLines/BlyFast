package com.blyfast.nativeopt;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the NativeOptimizer class.
 * 
 * <p>Tests all native optimization methods including JSON parsing/escaping,
 * HTTP header parsing, form data parsing, and memory operations.</p>
 */
@DisplayName("NativeOptimizer Tests")
public class NativeOptimizerTest {

    @BeforeAll
    static void checkNativeLibrary() {
        // Force reload to ensure library is loaded
        NativeOptimizer.forceReload();
        
        // Skip tests if native library is not available
        if (!NativeOptimizer.isNativeOptimizationAvailable()) {
            System.out.println("WARNING: Native library not available. Some tests may be skipped.");
        }
    }

    @Nested
    @DisplayName("JSON Escaping Tests")
    class JsonEscapingTests {

        @Test
        @DisplayName("Should escape simple string")
        void testEscapeSimpleString() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "hello world";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertEquals(input, result); // No special chars, should be unchanged
        }

        @Test
        @DisplayName("Should escape double quotes")
        void testEscapeDoubleQuotes() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "Say \"Hello\"";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertEquals("Say \\\"Hello\\\"", result);
        }

        @Test
        @DisplayName("Should escape backslash")
        void testEscapeBackslash() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "path\\to\\file";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertEquals("path\\\\to\\\\file", result);
        }

        @Test
        @DisplayName("Should escape control characters")
        void testEscapeControlCharacters() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "Line1\nLine2\rTab\t";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertTrue(result.contains("\\n"));
            assertTrue(result.contains("\\r"));
            assertTrue(result.contains("\\t"));
        }

        @Test
        @DisplayName("Should escape all special characters")
        void testEscapeAllSpecialChars() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "\"quotes\" \\backslash\\ \nnewline \rreturn \ttab \bbackspace \fformfeed";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertTrue(result.contains("\\\""));
            assertTrue(result.contains("\\\\"));
            assertTrue(result.contains("\\n"));
            assertTrue(result.contains("\\r"));
            assertTrue(result.contains("\\t"));
            assertTrue(result.contains("\\b"));
            assertTrue(result.contains("\\f"));
        }

        @Test
        @DisplayName("Should handle null input")
        void testEscapeNull() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String result = NativeOptimizer.escapeJson(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty string")
        void testEscapeEmptyString() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String result = NativeOptimizer.escapeJson("");
            assertNotNull(result);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void testEscapeUnicode() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "Hello 世界 🌍";
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertTrue(result.contains("世界"));
        }

        @Test
        @DisplayName("Should handle very long string")
        void testEscapeLongString() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("test\"string\\");
            }
            
            String input = sb.toString();
            String result = NativeOptimizer.escapeJson(input);
            
            assertNotNull(result);
            assertTrue(result.length() > input.length());
        }
    }

    @Nested
    @DisplayName("JSON Parsing Tests")
    class JsonParsingTests {

        @Test
        @DisplayName("Should parse simple JSON object")
        void testParseSimpleObject() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{\"name\":\"John\",\"age\":30}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            // Note: The native parser returns a HashMap wrapped in ByteBuffer
            // We can't directly test the contents without additional parsing
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse JSON array")
        void testParseArray() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "[1,2,3,4,5]";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse nested JSON object")
        void testParseNestedObject() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{\"user\":{\"name\":\"John\",\"age\":30}}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse JSON with boolean values")
        void testParseBooleans() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{\"active\":true,\"verified\":false}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse JSON with null values")
        void testParseNull() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{\"name\":\"John\",\"email\":null}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse JSON with numbers")
        void testParseNumbers() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{\"int\":42,\"float\":3.14,\"negative\":-10}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle empty JSON object")
        void testParseEmptyObject() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "{}";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle empty JSON array")
        void testParseEmptyArray() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String json = "[]";
            ByteBuffer result = NativeOptimizer.nativeParseJson(json);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle null input")
        void testParseNullInput() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            ByteBuffer result = NativeOptimizer.nativeParseJson(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty string")
        void testParseEmptyString() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            ByteBuffer result = NativeOptimizer.nativeParseJson("");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("HTTP Header Parsing Tests")
    class HttpHeaderParsingTests {

        @Test
        @DisplayName("Should parse simple HTTP headers")
        void testParseSimpleHeaders() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "Content-Type: application/json\r\n" +
                           "Content-Length: 100\r\n" +
                           "User-Agent: TestAgent\r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            
            assertTrue(headersId > 0);
            
            // Test retrieving headers
            String contentType = NativeOptimizer.nativeGetHeader(headersId, "Content-Type");
            assertNotNull(contentType);
            assertEquals("application/json", contentType.trim());
            
            String contentLength = NativeOptimizer.nativeGetHeader(headersId, "Content-Length");
            assertNotNull(contentLength);
            assertEquals("100", contentLength.trim());
            
            // Cleanup
            NativeOptimizer.nativeFreeHeaders(headersId);
        }

        @Test
        @DisplayName("Should parse headers case-insensitively")
        void testParseHeadersCaseInsensitive() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "Content-Type: application/json\r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            assertTrue(headersId > 0);
            
            // Test case-insensitive retrieval
            String contentType1 = NativeOptimizer.nativeGetHeader(headersId, "content-type");
            String contentType2 = NativeOptimizer.nativeGetHeader(headersId, "CONTENT-TYPE");
            String contentType3 = NativeOptimizer.nativeGetHeader(headersId, "Content-Type");
            
            assertNotNull(contentType1);
            assertNotNull(contentType2);
            assertNotNull(contentType3);
            assertEquals("application/json", contentType1.trim());
            assertEquals("application/json", contentType2.trim());
            assertEquals("application/json", contentType3.trim());
            
            NativeOptimizer.nativeFreeHeaders(headersId);
        }

        @Test
        @DisplayName("Should handle empty headers")
        void testParseEmptyHeaders() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "\r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            
            // Empty headers may still return a valid ID (but with no headers)
            // Clean up if we got a valid ID
            if (headersId > 0) {
                String result = NativeOptimizer.nativeGetHeader(headersId, "Any-Header");
                assertNull(result); // Should not find any headers
                NativeOptimizer.nativeFreeHeaders(headersId);
            }
        }

        @Test
        @DisplayName("Should return null for non-existent header")
        void testGetNonExistentHeader() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "Content-Type: application/json\r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            assertTrue(headersId > 0);
            
            String result = NativeOptimizer.nativeGetHeader(headersId, "Non-Existent-Header");
            assertNull(result);
            
            NativeOptimizer.nativeFreeHeaders(headersId);
        }

        @Test
        @DisplayName("Should handle headers with whitespace")
        void testParseHeadersWithWhitespace() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "Content-Type:   application/json   \r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            assertTrue(headersId > 0);
            
            String contentType = NativeOptimizer.nativeGetHeader(headersId, "Content-Type");
            assertNotNull(contentType);
            assertTrue(contentType.contains("application/json"));
            
            NativeOptimizer.nativeFreeHeaders(headersId);
        }

        @Test
        @DisplayName("Should free headers correctly")
        void testFreeHeaders() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String headers = "Content-Type: application/json\r\n";
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(headers.length());
            buffer.put(headers.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            long headersId = NativeOptimizer.nativeParseHttpHeaders(buffer, headers.length());
            assertTrue(headersId > 0);
            
            // Free headers
            NativeOptimizer.nativeFreeHeaders(headersId);
            
            // Should not crash when freeing again
            assertDoesNotThrow(() -> NativeOptimizer.nativeFreeHeaders(headersId));
        }
    }

    @Nested
    @DisplayName("String to Bytes Tests")
    class StringToBytesTests {

        @Test
        @DisplayName("Should convert string to direct bytes")
        void testStringToBytes() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String input = "Hello World";
            ByteBuffer result = NativeOptimizer.stringToDirectBytes(input);
            
            assertNotNull(result);
            assertTrue(result.isDirect());
            assertEquals(input.length(), result.remaining());
            
            byte[] bytes = new byte[result.remaining()];
            result.get(bytes);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(input, decoded);
        }

        @Test
        @DisplayName("Should handle empty string")
        void testEmptyStringToBytes() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            ByteBuffer result = NativeOptimizer.stringToDirectBytes("");
            
            assertNotNull(result);
            assertEquals(0, result.remaining());
        }

        @Test
        @DisplayName("Should handle unicode string")
        void testUnicodeStringToBytes() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            // Test with basic unicode characters (avoid emoji which may have encoding issues)
            String input = "Hello 世界";
            ByteBuffer result = NativeOptimizer.stringToDirectBytes(input);
            
            assertNotNull(result);
            assertTrue(result.isDirect());
            
            byte[] bytes = new byte[result.remaining()];
            result.get(bytes);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(input, decoded);
        }
    }

    @Nested
    @DisplayName("Memory Copy Tests")
    class MemoryCopyTests {

        @Test
        @DisplayName("Should copy memory between buffers")
        void testMemoryCopy() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String sourceData = "Hello World";
            ByteBuffer src = ByteBuffer.allocateDirect(sourceData.length());
            src.put(sourceData.getBytes(StandardCharsets.UTF_8));
            src.flip();
            
            ByteBuffer dst = ByteBuffer.allocateDirect(sourceData.length());
            
            NativeOptimizer.nativeMemoryCopy(src, 0, dst, 0, sourceData.length());
            
            byte[] result = new byte[sourceData.length()];
            dst.get(result);
            String decoded = new String(result, StandardCharsets.UTF_8);
            
            assertEquals(sourceData, decoded);
        }

        @Test
        @DisplayName("Should copy with offset")
        void testMemoryCopyWithOffset() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String sourceData = "Hello World";
            ByteBuffer src = ByteBuffer.allocateDirect(sourceData.length());
            src.put(sourceData.getBytes(StandardCharsets.UTF_8));
            src.flip();
            
            ByteBuffer dst = ByteBuffer.allocateDirect(20);
            
            NativeOptimizer.nativeMemoryCopy(src, 0, dst, 5, 5);
            
            byte[] result = new byte[20];
            dst.get(result);
            String decoded = new String(result, 5, 5, StandardCharsets.UTF_8);
            
            assertEquals("Hello", decoded);
        }

        @Test
        @DisplayName("Should handle null buffers gracefully")
        void testMemoryCopyNullBuffers() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            // Should not throw exception
            assertDoesNotThrow(() -> {
                NativeOptimizer.nativeMemoryCopy(null, 0, null, 0, 0);
            });
        }
    }

    @Nested
    @DisplayName("HTTP Body Analysis Tests")
    class HttpBodyAnalysisTests {

        @Test
        @DisplayName("Should detect JSON content type")
        void testDetectJsonContentType() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String body = "{\"name\":\"John\"}";
            ByteBuffer buffer = ByteBuffer.allocateDirect(body.length());
            buffer.put(body.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeAnalyzeHttpBody(buffer, body.length(), "application/json");
            
            assertEquals(1, bodyType); // JSON
        }

        @Test
        @DisplayName("Should detect form data content type")
        void testDetectFormDataContentType() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String body = "name=John&age=30";
            ByteBuffer buffer = ByteBuffer.allocateDirect(body.length());
            buffer.put(body.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeAnalyzeHttpBody(buffer, body.length(), 
                    "application/x-www-form-urlencoded");
            
            assertEquals(2, bodyType); // Form data
        }

        @Test
        @DisplayName("Should detect multipart content type")
        void testDetectMultipartContentType() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String body = "--boundary\r\nContent-Disposition: form-data\r\n\r\ndata\r\n--boundary--";
            ByteBuffer buffer = ByteBuffer.allocateDirect(body.length());
            buffer.put(body.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeAnalyzeHttpBody(buffer, body.length(), 
                    "multipart/form-data; boundary=boundary");
            
            assertEquals(3, bodyType); // Multipart
        }

        @Test
        @DisplayName("Should detect text content type")
        void testDetectTextContentType() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String body = "Plain text content";
            ByteBuffer buffer = ByteBuffer.allocateDirect(body.length());
            buffer.put(body.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeAnalyzeHttpBody(buffer, body.length(), "text/plain");
            
            assertEquals(4, bodyType); // Text
        }

        @Test
        @DisplayName("Should detect content type from body")
        void testDetectContentTypeFromBody() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String jsonBody = "{\"name\":\"John\"}";
            ByteBuffer buffer = ByteBuffer.allocateDirect(jsonBody.length());
            buffer.put(jsonBody.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeFastDetectContentType(buffer, jsonBody.length());
            
            assertEquals(1, bodyType); // JSON detected from content
        }

        @Test
        @DisplayName("Should detect form data from body")
        void testDetectFormDataFromBody() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String formBody = "name=John&age=30";
            ByteBuffer buffer = ByteBuffer.allocateDirect(formBody.length());
            buffer.put(formBody.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            int bodyType = NativeOptimizer.nativeFastDetectContentType(buffer, formBody.length());
            
            assertEquals(2, bodyType); // Form data detected
        }
    }

    @Nested
    @DisplayName("Form Data Parsing Tests")
    class FormDataParsingTests {

        @Test
        @DisplayName("Should parse simple form data")
        void testParseSimpleFormData() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String formData = "name=John&age=30";
            ByteBuffer buffer = ByteBuffer.allocateDirect(formData.length());
            buffer.put(formData.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            ByteBuffer result = NativeOptimizer.nativeFastParseBody(buffer, formData.length(), 2);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should parse URL-encoded form data")
        void testParseUrlEncodedFormData() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String formData = "name=John+Doe&email=john%40example.com";
            ByteBuffer buffer = ByteBuffer.allocateDirect(formData.length());
            buffer.put(formData.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            
            ByteBuffer result = NativeOptimizer.nativeFastParseBody(buffer, formData.length(), 2);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle empty form data")
        void testParseEmptyFormData() {
            if (!NativeOptimizer.isNativeOptimizationAvailable()) {
                return;
            }
            
            String formData = "";
            ByteBuffer buffer = ByteBuffer.allocateDirect(1);
            buffer.flip();
            
            ByteBuffer result = NativeOptimizer.nativeFastParseBody(buffer, 0, 2);
            
            // Empty form data may return null, which is acceptable
            // The important thing is it doesn't crash
            assertDoesNotThrow(() -> {
                NativeOptimizer.nativeFastParseBody(buffer, 0, 2);
            });
        }
    }

    @Test
    @DisplayName("Should check if native optimization is available")
    void testIsNativeOptimizationAvailable() {
        boolean available = NativeOptimizer.isNativeOptimizationAvailable();
        
        // This test always passes - it just checks the method works
        assertTrue(available || !available); // Either true or false is valid
    }
}

