#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// Header value struct for HTTP header parsing
typedef struct {
    char* name;
    char* value;
    struct HeaderValue* next;
} HeaderValue;

// Headers collection for the parsed HTTP headers
typedef struct {
    HeaderValue* first;
    int count;
    jlong id;
} ParsedHeaders;

// Global storage for parsed headers (simple implementation for demo)
#define MAX_HEADERS 100
static ParsedHeaders* headers_storage[MAX_HEADERS];
static int next_header_id = 1;

// JNI method implementations for com.blyfast.nativeopt.NativeOptimizer

/**
 * Fast native JSON string escaping
 */
JNIEXPORT jstring JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeEscapeJson
  (JNIEnv *env, jclass cls, jstring input) {
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    // Allocate buffer for the escaped string (worst case: all chars need escaping)
    char *buffer = (char *)malloc(length * 6 + 1); // 6x for unicode escape sequences
    if (buffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, utf8Str);
        return NULL; // OutOfMemoryError
    }
    
    size_t pos = 0;
    for (size_t i = 0; i < length; i++) {
        char c = utf8Str[i];
        
        // Escape special characters
        switch (c) {
            case '"':
                buffer[pos++] = '\\';
                buffer[pos++] = '"';
                break;
            case '\\':
                buffer[pos++] = '\\';
                buffer[pos++] = '\\';
                break;
            case '\b':
                buffer[pos++] = '\\';
                buffer[pos++] = 'b';
                break;
            case '\f':
                buffer[pos++] = '\\';
                buffer[pos++] = 'f';
                break;
            case '\n':
                buffer[pos++] = '\\';
                buffer[pos++] = 'n';
                break;
            case '\r':
                buffer[pos++] = '\\';
                buffer[pos++] = 'r';
                break;
            case '\t':
                buffer[pos++] = '\\';
                buffer[pos++] = 't';
                break;
            default:
                // Check for control characters
                if (c < 32) {
                    // Use \uXXXX format
                    sprintf(buffer + pos, "\\u%04x", (unsigned char)c);
                    pos += 6;
                } else {
                    // Regular character
                    buffer[pos++] = c;
                }
                break;
        }
    }
    
    // Null terminate the buffer
    buffer[pos] = '\0';
    
    // Create a new Java string
    jstring result = (*env)->NewStringUTF(env, buffer);
    
    // Clean up
    free(buffer);
    (*env)->ReleaseStringUTFChars(env, input, utf8Str);
    
    return result;
}

/**
 * Fast native JSON parsing
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeParseJson
  (JNIEnv *env, jclass cls, jstring input) {
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    // For this implementation, we'll just return a ByteBuffer with the UTF-8 bytes
    // A full JSON parser would be much more complex and beyond the scope here
    
    // Allocate a direct ByteBuffer
    jobject buffer = (*env)->NewDirectByteBuffer(env, (void *)utf8Str, length);
    
    // Note: Normally we would release utf8Str, but the ByteBuffer now owns the pointer
    // So we don't call (*env)->ReleaseStringUTFChars here
    
    return buffer;
}

/**
 * Fast native HTTP header parsing
 * Parses HTTP headers from a ByteBuffer and returns an ID to access the parsed headers
 */
JNIEXPORT jlong JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeParseHttpHeaders
  (JNIEnv *env, jclass cls, jobject headerBytes, jint length) {
    // Get the buffer from the ByteBuffer
    char *buffer = (*env)->GetDirectBufferAddress(env, headerBytes);
    if (buffer == NULL) {
        return 0; // Error
    }
    
    // Allocate a new headers structure
    ParsedHeaders* headers = (ParsedHeaders*)malloc(sizeof(ParsedHeaders));
    if (!headers) {
        return 0; // Memory allocation failed
    }
    
    // Initialize the headers structure
    headers->first = NULL;
    headers->count = 0;
    headers->id = next_header_id++;
    
    // Parse headers (simple implementation)
    char* pos = buffer;
    char* end = buffer + length;
    char* line_start = pos;
    char* line_end;
    char* colon;
    
    while (pos < end) {
        // Find end of line
        line_end = pos;
        while (line_end < end && *line_end != '\r' && *line_end != '\n') {
            line_end++;
        }
        
        // Skip empty lines
        if (line_start == line_end) {
            // Skip CRLF
            if (line_end < end && *line_end == '\r') line_end++;
            if (line_end < end && *line_end == '\n') line_end++;
            line_start = line_end;
            pos = line_end;
            continue;
        }
        
        // Find colon separator
        colon = line_start;
        while (colon < line_end && *colon != ':') {
            colon++;
        }
        
        if (colon < line_end) {
            // We found a header
            
            // Allocate new header value
            HeaderValue* header = (HeaderValue*)malloc(sizeof(HeaderValue));
            if (!header) {
                // Memory allocation failed, cleanup and return
                // TODO: implement proper cleanup
                return 0;
            }
            
            // Extract name (trim trailing whitespace)
            size_t name_len = colon - line_start;
            header->name = (char*)malloc(name_len + 1);
            if (!header->name) {
                free(header);
                return 0;
            }
            memcpy(header->name, line_start, name_len);
            header->name[name_len] = '\0';
            
            // Extract value (skip leading whitespace)
            char* value_start = colon + 1;
            while (value_start < line_end && (*value_start == ' ' || *value_start == '\t')) {
                value_start++;
            }
            
            size_t value_len = line_end - value_start;
            header->value = (char*)malloc(value_len + 1);
            if (!header->value) {
                free(header->name);
                free(header);
                return 0;
            }
            memcpy(header->value, value_start, value_len);
            header->value[value_len] = '\0';
            
            // Add to list
            header->next = headers->first;
            headers->first = header;
            headers->count++;
        }
        
        // Skip CRLF
        if (line_end < end && *line_end == '\r') line_end++;
        if (line_end < end && *line_end == '\n') line_end++;
        line_start = line_end;
        pos = line_end;
    }
    
    // Store headers in global storage
    if (headers->id < MAX_HEADERS) {
        headers_storage[headers->id] = headers;
    } else {
        // Too many headers, cleanup and return error
        // TODO: implement proper cleanup
        free(headers);
        return 0;
    }
    
    return headers->id;
}

/**
 * Optimized string to bytes conversion with direct memory
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeStringToBytes
  (JNIEnv *env, jclass cls, jstring input) {
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    // Allocate a direct buffer
    char *directBuffer = (char *)malloc(length);
    if (directBuffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, utf8Str);
        return NULL; // OutOfMemoryError
    }
    
    // Copy the string data to the direct buffer
    memcpy(directBuffer, utf8Str, length);
    
    // Create a new ByteBuffer that owns the direct buffer
    jobject result = (*env)->NewDirectByteBuffer(env, directBuffer, length);
    
    // Release the string
    (*env)->ReleaseStringUTFChars(env, input, utf8Str);
    
    return result;
}

/**
 * Retrieves a header value by name from previously parsed headers
 */
JNIEXPORT jstring JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeGetHeader
  (JNIEnv *env, jclass cls, jlong headersId, jstring headerName) {
    // Check if headers ID is valid
    if (headersId <= 0 || headersId >= MAX_HEADERS || headers_storage[headersId] == NULL) {
        return NULL;
    }
    
    // Get the header name
    const char *nameStr = (*env)->GetStringUTFChars(env, headerName, NULL);
    if (nameStr == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Find the header
    ParsedHeaders* headers = headers_storage[headersId];
    HeaderValue* current = headers->first;
    
    while (current != NULL) {
        if (strcasecmp(current->name, nameStr) == 0) {
            // Found the header, return its value
            jstring result = (*env)->NewStringUTF(env, current->value);
            (*env)->ReleaseStringUTFChars(env, headerName, nameStr);
            return result;
        }
        current = current->next;
    }
    
    // Header not found
    (*env)->ReleaseStringUTFChars(env, headerName, nameStr);
    return NULL;
}

/**
 * Releases resources associated with previously parsed headers
 */
JNIEXPORT void JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFreeHeaders
  (JNIEnv *env, jclass cls, jlong headersId) {
    // Check if headers ID is valid
    if (headersId <= 0 || headersId >= MAX_HEADERS || headers_storage[headersId] == NULL) {
        return;
    }
    
    // Free header values
    ParsedHeaders* headers = headers_storage[headersId];
    HeaderValue* current = headers->first;
    
    while (current != NULL) {
        HeaderValue* next = current->next;
        free(current->name);
        free(current->value);
        free(current);
        current = next;
    }
    
    // Free headers structure
    free(headers);
    headers_storage[headersId] = NULL;
}

/**
 * Optimized memory copy - copies from source to destination buffer
 */
JNIEXPORT void JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeMemoryCopy
  (JNIEnv *env, jclass cls, jobject srcBuffer, jint srcOffset, jobject dstBuffer, jint dstOffset, jint length) {
    // Get direct buffer addresses
    char* src = (char*)(*env)->GetDirectBufferAddress(env, srcBuffer);
    char* dst = (char*)(*env)->GetDirectBufferAddress(env, dstBuffer);
    
    if (src == NULL || dst == NULL) {
        return; // Error in buffer access
    }
    
    // Perform the memory copy
    memcpy(dst + dstOffset, src + srcOffset, length);
}

/**
 * Optimized HTTP body processing - analyzes content type and prepares for parsing
 */
JNIEXPORT jint JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeAnalyzeHttpBody
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length, jstring contentType) {
    // Get the content type string
    const char *ctStr = (*env)->GetStringUTFChars(env, contentType, NULL);
    if (ctStr == NULL) {
        return 0; // Error
    }
    
    // Get buffer address
    char* buffer = (char*)(*env)->GetDirectBufferAddress(env, bodyBuffer);
    if (buffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, contentType, ctStr);
        return 0; // Error
    }
    
    // Determine the body type based on content type
    int bodyType = 0; // 0=unknown, 1=JSON, 2=form, 3=multipart, 4=text, 5=binary
    
    if (strstr(ctStr, "application/json") != NULL) {
        bodyType = 1; // JSON
        
        // Simple validation - check if it starts with { or [
        if (length > 0 && (buffer[0] == '{' || buffer[0] == '[')) {
            // Looks like valid JSON
            bodyType = 1;
        } else {
            // Doesn't look like JSON
            bodyType = 0;
        }
    } else if (strstr(ctStr, "application/x-www-form-urlencoded") != NULL) {
        bodyType = 2; // Form data
    } else if (strstr(ctStr, "multipart/form-data") != NULL) {
        bodyType = 3; // Multipart form
    } else if (strstr(ctStr, "text/") != NULL) {
        bodyType = 4; // Text
    } else {
        bodyType = 5; // Assume binary
    }
    
    // Release the content type string
    (*env)->ReleaseStringUTFChars(env, contentType, ctStr);
    
    return bodyType;
} 