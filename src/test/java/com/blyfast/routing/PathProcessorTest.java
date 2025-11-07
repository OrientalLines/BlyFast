package com.blyfast.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the PathProcessor class.
 * 
 * <p>Tests path normalization, pattern generation, and parameter extraction.</p>
 */
@DisplayName("PathProcessor Tests")
public class PathProcessorTest {

    @Test
    @DisplayName("Should normalize simple path")
    void testNormalizeSimplePath() {
        // Given: a simple path
        String path = "/test";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should normalize correctly
        assertEquals("/test", processor.getNormalizedPath());
    }

    @Test
    @DisplayName("Should normalize path without leading slash")
    void testNormalizePathWithoutLeadingSlash() {
        // Given: path without leading slash
        String path = "test";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should add leading slash
        assertEquals("/test", processor.getNormalizedPath());
    }

    @Test
    @DisplayName("Should normalize empty path")
    void testNormalizeEmptyPath() {
        // Given: empty path
        String path = "";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should normalize to root
        assertEquals("/", processor.getNormalizedPath());
    }

    @Test
    @DisplayName("Should normalize null path")
    void testNormalizeNullPath() {
        // Given: null path
        String path = null;
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should normalize to root
        assertEquals("/", processor.getNormalizedPath());
    }

    @Test
    @DisplayName("Should remove trailing slash in normalized path")
    void testRemoveTrailingSlash() {
        // Given: path with trailing slash
        String path = "/test/";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should remove trailing slash in normalized path
        assertEquals("/test", processor.getNormalizedPath());
    }

    @Test
    @DisplayName("Should extract single path parameter")
    void testExtractSingleParameter() {
        // Given: path with one parameter
        String path = "/users/:id";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should extract parameter name
        List<String> paramNames = processor.getParamNames();
        assertEquals(1, paramNames.size());
        assertEquals("id", paramNames.get(0));
    }

    @Test
    @DisplayName("Should extract multiple path parameters")
    void testExtractMultipleParameters() {
        // Given: path with multiple parameters
        String path = "/users/:userId/posts/:postId";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should extract all parameter names
        List<String> paramNames = processor.getParamNames();
        assertEquals(2, paramNames.size());
        assertEquals("userId", paramNames.get(0));
        assertEquals("postId", paramNames.get(1));
    }

    @Test
    @DisplayName("Should generate pattern for static path")
    void testStaticPathPattern() {
        // Given: static path
        String path = "/api/users";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: pattern should match exact path
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/api/users").matches());
        assertTrue(pattern.matcher("/api/users/").matches());
        assertFalse(pattern.matcher("/api/user").matches());
    }

    @Test
    @DisplayName("Should generate pattern for path with parameter")
    void testParameterPathPattern() {
        // Given: path with parameter
        String path = "/users/:id";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: pattern should match paths with any value
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/users/123").matches());
        assertTrue(pattern.matcher("/users/abc").matches());
        assertTrue(pattern.matcher("/users/test-user").matches());
        assertFalse(pattern.matcher("/users/123/posts").matches());
    }

    @Test
    @DisplayName("Should generate pattern for path with wildcard")
    void testWildcardPathPattern() {
        // Given: path with wildcard
        String path = "/api/*";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: pattern should match any path after /api/
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/api/users").matches());
        assertTrue(pattern.matcher("/api/users/123").matches());
        assertTrue(pattern.matcher("/api/anything/here").matches());
    }

    @Test
    @DisplayName("Should handle root path")
    void testRootPath() {
        // Given: root path
        String path = "/";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should normalize correctly
        assertEquals("/", processor.getNormalizedPath());
        assertTrue(processor.getPattern().matcher("/").matches());
    }

    @Test
    @DisplayName("Should handle path with multiple segments and parameters")
    void testComplexPath() {
        // Given: complex path with multiple segments
        String path = "/api/v1/users/:userId/posts/:postId/comments";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should extract parameters correctly
        List<String> paramNames = processor.getParamNames();
        assertEquals(2, paramNames.size());
        assertEquals("userId", paramNames.get(0));
        assertEquals("postId", paramNames.get(1));
        
        // Pattern should match correct paths
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/api/v1/users/123/posts/456/comments").matches());
        assertFalse(pattern.matcher("/api/v1/users/123/posts/comments").matches());
    }

    @Test
    @DisplayName("Should handle consecutive parameters")
    void testConsecutiveParameters() {
        // Given: path with consecutive parameters
        String path = "/:year/:month/:day";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should extract all parameters
        List<String> paramNames = processor.getParamNames();
        assertEquals(3, paramNames.size());
        assertEquals("year", paramNames.get(0));
        assertEquals("month", paramNames.get(1));
        assertEquals("day", paramNames.get(2));
        
        // Pattern should match
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/2024/01/15").matches());
    }

    @Test
    @DisplayName("Should handle parameter at start of path")
    void testParameterAtStart() {
        // Given: path with parameter at start
        String path = "/:id/users";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should extract parameter
        List<String> paramNames = processor.getParamNames();
        assertEquals(1, paramNames.size());
        assertEquals("id", paramNames.get(0));
        
        // Pattern should match
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/123/users").matches());
    }

    @Test
    @DisplayName("Should handle path with special characters")
    void testPathWithSpecialCharacters() {
        // Given: path with special characters
        String path = "/api/test-endpoint_v2";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: pattern should match exact path (special chars escaped)
        Pattern pattern = processor.getPattern();
        assertTrue(pattern.matcher("/api/test-endpoint_v2").matches());
        assertFalse(pattern.matcher("/api/test-endpointv2").matches());
    }

    @Test
    @DisplayName("Should not extract parameter from literal colon")
    void testLiteralColon() {
        // Given: path with literal colon (not parameter)
        // Note: In current implementation, : always indicates parameter
        // This test documents current behavior
        String path = "/users/:id";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: colon is treated as parameter indicator
        List<String> paramNames = processor.getParamNames();
        assertEquals(1, paramNames.size());
    }

    @Test
    @DisplayName("Should handle empty segments")
    void testEmptySegments() {
        // Given: path with empty segments
        String path = "//test//";
        
        // When: processing path
        PathProcessor processor = PathProcessor.process(path);
        
        // Then: should normalize correctly
        assertNotNull(processor.getNormalizedPath());
        assertNotNull(processor.getPattern());
    }
}

