#include "blyfastnative.h"

// JNI method implementations for com.blyfast.nativeopt.NativeOptimizer

/**
 * Fast native JSON string escaping - optimized version
 */
JNIEXPORT jstring JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeEscapeJson
  (JNIEnv *env, jclass cls, jstring input) {
    if (input == NULL) {
        return NULL;
    }
    
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    // Pre-allocate buffer with better size estimation
    // Most strings don't need escaping, so start with 1.2x size
    size_t bufSize = length * 2 + 64;  // Better initial estimate
    char *buffer = (char *)malloc(bufSize);
    if (buffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, utf8Str);
        return NULL; // OutOfMemoryError
    }
    
    size_t pos = 0;
    for (size_t i = 0; i < length; i++) {
        // Check if we need to expand buffer
        if (pos + 6 >= bufSize) {
            bufSize = bufSize * 2;
            char *newBuffer = (char *)realloc(buffer, bufSize);
            if (newBuffer == NULL) {
                free(buffer);
                (*env)->ReleaseStringUTFChars(env, input, utf8Str);
                return NULL;
            }
            buffer = newBuffer;
        }
        
        unsigned char c = (unsigned char)utf8Str[i];
        
        // Escape special characters - optimized switch
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
                    // Use \uXXXX format - optimized sprintf
                    int written = snprintf(buffer + pos, 7, "\\u%04x", c);
                    if (written > 0) {
                        pos += written;
                    }
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
 * Fast native JSON parsing - improved error handling
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeParseJson
  (JNIEnv *env, jclass cls, jstring input) {
    if (input == NULL) {
        return NULL;
    }
    
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    if (length == 0) {
        (*env)->ReleaseStringUTFChars(env, input, utf8Str);
        return NULL; // Empty string
    }
    
    // Start parsing from the beginning
    const char *cursor = utf8Str;
    const char *end = utf8Str + length;
    
    // Parse the top-level JSON value
    jobject result = parseJsonValue(env, &cursor, end);
    
    // Verify we consumed all input (after whitespace)
    if (result != NULL) {
        skipWhitespace(&cursor, end);
        if (cursor < end) {
            // Extra data after JSON value - invalid
            (*env)->DeleteLocalRef(env, result);
            result = NULL;
        }
    }
    
    // Clean up the input string
    (*env)->ReleaseStringUTFChars(env, input, utf8Str);
    
    return result;
}

/**
 * Optimized string to bytes conversion with direct memory
 * NOTE: Memory is managed by Java's ByteBuffer cleanup mechanism
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeStringToBytes
  (JNIEnv *env, jclass cls, jstring input) {
    if (input == NULL) {
        return NULL;
    }
    
    // Get the input string
    const char *utf8Str = (*env)->GetStringUTFChars(env, input, NULL);
    if (utf8Str == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Calculate the length of the input string
    size_t length = (*env)->GetStringUTFLength(env, input);
    
    // Allocate a direct buffer
    // Note: This memory will be freed when the ByteBuffer is garbage collected
    // In production, consider using a cleaner or explicit free method
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
    
    // Note: The directBuffer memory will be managed by Java's GC system
    // For production use, consider registering a cleaner callback
    return result;
}

/**
 * Optimized memory copy - copies from source to destination buffer
 */
JNIEXPORT void JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeMemoryCopy
  (JNIEnv *env, jclass cls, jobject srcBuffer, jint srcOffset, jobject dstBuffer, jint dstOffset, jint length) {
    if (srcBuffer == NULL || dstBuffer == NULL || length < 0 || srcOffset < 0 || dstOffset < 0) {
        return; // Invalid parameters
    }
    
    // Get direct buffer addresses
    char* src = (char*)(*env)->GetDirectBufferAddress(env, srcBuffer);
    char* dst = (char*)(*env)->GetDirectBufferAddress(env, dstBuffer);
    
    if (src == NULL || dst == NULL) {
        return; // Error in buffer access
    }
    
    // Bounds checking
    jlong srcCapacity = (*env)->GetDirectBufferCapacity(env, srcBuffer);
    jlong dstCapacity = (*env)->GetDirectBufferCapacity(env, dstBuffer);
    
    if (srcOffset + length > srcCapacity || dstOffset + length > dstCapacity) {
        return; // Out of bounds
    }
    
    // Perform the memory copy - use memmove for safety (handles overlapping)
    if (src + srcOffset < dst + dstOffset || src + srcOffset >= dst + dstOffset + length) {
        memcpy(dst + dstOffset, src + srcOffset, length);
    } else {
        memmove(dst + dstOffset, src + srcOffset, length);
    }
}

/**
 * Optimized HTTP body processing - analyzes content type and prepares for parsing
 */
JNIEXPORT jint JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeAnalyzeHttpBody
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length, jstring contentType) {
    if (bodyBuffer == NULL || length < 0) {
        return 0; // Error
    }
    
    // Get buffer address
    char* buffer = (char*)(*env)->GetDirectBufferAddress(env, bodyBuffer);
    if (buffer == NULL) {
        return 0; // Error
    }
    
    // Determine the body type based on content type
    int bodyType = 0; // 0=unknown, 1=JSON, 2=form, 3=multipart, 4=text, 5=binary
    
    if (contentType != NULL) {
        const char *ctStr = (*env)->GetStringUTFChars(env, contentType, NULL);
        if (ctStr != NULL) {
            // Case-insensitive matching using portable function
            if (strcasestr_portable(ctStr, "application/json") != NULL) {
                bodyType = 1; // JSON
                // Simple validation - check if it starts with { or [
                if (length > 0 && (buffer[0] == '{' || buffer[0] == '[')) {
                    bodyType = 1;
                } else {
                    bodyType = 0;
                }
            } else if (strcasestr_portable(ctStr, "application/x-www-form-urlencoded") != NULL) {
                bodyType = 2; // Form data
            } else if (strcasestr_portable(ctStr, "multipart/form-data") != NULL) {
                bodyType = 3; // Multipart form
            } else if (strcasestr_portable(ctStr, "text/") != NULL) {
                bodyType = 4; // Text
            } else {
                bodyType = 5; // Assume binary
            }
            
            (*env)->ReleaseStringUTFChars(env, contentType, ctStr);
        }
    }
    
    return bodyType;
}

/**
 * Fast HTTP body parsing based on content type
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFastParseBody
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length, jint bodyType) {
    if (bodyBuffer == NULL || length < 0) {
        return NULL; // Error
    }
    
    // Get buffer address
    char* buffer = (char*)(*env)->GetDirectBufferAddress(env, bodyBuffer);
    if (buffer == NULL) {
        return NULL; // Error
    }
    
    // Allocate a new direct ByteBuffer to hold parsed result
    jobject resultBuffer = NULL;
    
    switch (bodyType) {
        case 1: // JSON
            // For JSON, we'll do minimal processing and return the buffer as is
            // since we have separate JSON parsing functionality
            resultBuffer = (*env)->NewDirectByteBuffer(env, buffer, length);
            break;
            
        case 2: // Form data
            // For form data, we parse it and convert to a structured format
            resultBuffer = parseFormData(env, buffer, length);
            break;
            
        case 3: // Multipart form
            // For multipart form, we extract boundaries and parts
            resultBuffer = parseMultipartForm(env, buffer, length);
            break;
            
        case 4: // Text
        case 5: // Binary
        default:
            // For others, just return the buffer as is
            resultBuffer = (*env)->NewDirectByteBuffer(env, buffer, length);
            break;
    }
    
    return resultBuffer;
}

/**
 * Fast content type detection - improved algorithm
 */
JNIEXPORT jint JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFastDetectContentType
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length) {
    if (bodyBuffer == NULL) {
        return 0; // Unknown
    }
    
    char* buffer = (char*)(*env)->GetDirectBufferAddress(env, bodyBuffer);
    if (buffer == NULL || length <= 0) {
        return 0; // Unknown
    }
    
    // Simple content type detection based on the first few bytes
    if (length >= 2) {
        if (buffer[0] == '{' || buffer[0] == '[') {
            return 1; // JSON
        } else if (buffer[0] == '<' && length >= 5) {
            // Check for XML or HTML
            if (buffer[1] == '?' && buffer[2] == 'x' && buffer[3] == 'm' && buffer[4] == 'l') {
                return 6; // XML
            } else if (buffer[1] == '!' || buffer[1] == 'h' || buffer[1] == 'H') {
                return 7; // Probably HTML
            }
        } else if (buffer[0] == '-' && buffer[1] == '-' && length >= 10) {
            // Check for multipart form data
            return 3; // Multipart form
        }
    }
    
    // Check for form URL-encoded data
    int hasEquals = 0;
    int hasAmpersand = 0;
    int textChars = 0;
    int binaryChars = 0;
    
    int checkLen = length > 200 ? 200 : length;
    for (int i = 0; i < checkLen; i++) {
        char c = buffer[i];
        if (c == '=') hasEquals++;
        if (c == '&') hasAmpersand++;
        if (c >= 32 && c <= 126) textChars++; // ASCII printable
        else binaryChars++;
    }
    
    if (hasEquals > 0 && (hasAmpersand > 0 || length < 100)) {
        return 2; // Likely form data
    }
    
    // Determine if text or binary
    if (binaryChars > checkLen / 10) { // More than 10% binary chars
        return 5; // Binary
    } else if (textChars > checkLen * 0.9) { // More than 90% text chars
        return 4; // Text
    } else {
        // Mixed content, default to text
        return 4;
    }
}
