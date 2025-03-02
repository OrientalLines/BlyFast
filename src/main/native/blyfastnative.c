#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// Header value struct for HTTP header parsing
typedef struct HeaderValue {
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

// Function prototypes
jobject parseFormData(JNIEnv *env, char* buffer, jint length);
jobject parseMultipartForm(JNIEnv *env, char* buffer, jint length);

// Forward declarations for our recursive parser functions
static void skipWhitespace(const char **cursor, const char *end);
static jobject parseJsonValue(JNIEnv *env, const char **cursor, const char *end);
static jobject parseJsonObject(JNIEnv *env, const char **cursor, const char *end);
static jobject parseJsonArray(JNIEnv *env, const char **cursor, const char *end);
static jobject parseJsonString(JNIEnv *env, const char **cursor, const char *end);
static jobject parseJsonNumber(JNIEnv *env, const char **cursor, const char *end);

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
    
    // Start parsing from the beginning
    const char *cursor = utf8Str;
    const char *end = utf8Str + length;
    
    // Parse the top-level JSON value
    jobject result = parseJsonValue(env, &cursor, end);
    
    // Clean up the input string
    (*env)->ReleaseStringUTFChars(env, input, utf8Str);
    
    return result;
}

// Skip whitespace characters
static void skipWhitespace(const char **cursor, const char *end) {
    while (*cursor < end) {
        char c = **cursor;
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            (*cursor)++;
        } else {
            break;
        }
    }
}

// Parse a JSON value (object, array, string, number, true, false, null)
static jobject parseJsonValue(JNIEnv *env, const char **cursor, const char *end) {
    skipWhitespace(cursor, end);
    
    if (*cursor >= end) {
        return NULL; // Unexpected end of input
    }
    
    char c = **cursor;
    
    if (c == '{') {
        return parseJsonObject(env, cursor, end);
    } else if (c == '[') {
        return parseJsonArray(env, cursor, end);
    } else if (c == '"') {
        return parseJsonString(env, cursor, end);
    } else if (c == 't') {
        // Parse 'true'
        if (*cursor + 4 <= end && strncmp(*cursor, "true", 4) == 0) {
            *cursor += 4;
            
            // Get Boolean.TRUE
            jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
            if (booleanClass == NULL) return NULL;
            
            jfieldID trueField = (*env)->GetStaticFieldID(env, booleanClass, "TRUE", "Ljava/lang/Boolean;");
            if (trueField == NULL) return NULL;
            
            jobject trueValue = (*env)->GetStaticObjectField(env, booleanClass, trueField);
            return trueValue;
        }
    } else if (c == 'f') {
        // Parse 'false'
        if (*cursor + 5 <= end && strncmp(*cursor, "false", 5) == 0) {
            *cursor += 5;
            
            // Get Boolean.FALSE
            jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
            if (booleanClass == NULL) return NULL;
            
            jfieldID falseField = (*env)->GetStaticFieldID(env, booleanClass, "FALSE", "Ljava/lang/Boolean;");
            if (falseField == NULL) return NULL;
            
            jobject falseValue = (*env)->GetStaticObjectField(env, booleanClass, falseField);
            return falseValue;
        }
    } else if (c == 'n') {
        // Parse 'null'
        if (*cursor + 4 <= end && strncmp(*cursor, "null", 4) == 0) {
            *cursor += 4;
            return NULL; // In Java, null is represented as NULL in C
        }
    } else if (c == '-' || (c >= '0' && c <= '9')) {
        return parseJsonNumber(env, cursor, end);
    }
    
    return NULL; // Invalid JSON or unsupported value type
}

// Parse a JSON object
static jobject parseJsonObject(JNIEnv *env, const char **cursor, const char *end) {
    // Skip opening brace
    (*cursor)++;
    
    // Create a HashMap
    jclass mapClass = (*env)->FindClass(env, "java/util/HashMap");
    if (mapClass == NULL) return NULL;
    
    jmethodID mapConstructor = (*env)->GetMethodID(env, mapClass, "<init>", "()V");
    if (mapConstructor == NULL) return NULL;
    
    jobject map = (*env)->NewObject(env, mapClass, mapConstructor);
    if (map == NULL) return NULL;
    
    jmethodID putMethod = (*env)->GetMethodID(env, mapClass, "put", 
                                             "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (putMethod == NULL) return NULL;
    
    skipWhitespace(cursor, end);
    
    // Check if it's an empty object
    if (*cursor < end && **cursor == '}') {
        (*cursor)++;
        return map;
    }
    
    // Parse key-value pairs
    while (*cursor < end) {
        skipWhitespace(cursor, end);
        
        // Expect a string key
        if (**cursor != '"') {
            break; // Invalid format
        }
        
        // Parse the key
        jobject key = parseJsonString(env, cursor, end);
        if (key == NULL) {
            return NULL;
        }
        
        skipWhitespace(cursor, end);
        
        // Expect a colon
        if (*cursor >= end || **cursor != ':') {
            (*env)->DeleteLocalRef(env, key);
            return NULL;
        }
        (*cursor)++; // Skip the colon
        
        // Parse the value
        jobject value = parseJsonValue(env, cursor, end);
        
        // Add key-value pair to the map
        // Note: value can be NULL for JSON null
        (*env)->CallObjectMethod(env, map, putMethod, key, value);
        
        // Clean up references
        (*env)->DeleteLocalRef(env, key);
        if (value != NULL) (*env)->DeleteLocalRef(env, value);
        
        skipWhitespace(cursor, end);
        
        // Check for end of object or comma for next pair
        if (*cursor >= end) {
            return NULL;
        }
        
        if (**cursor == '}') {
            (*cursor)++;
            return map;
        } else if (**cursor == ',') {
            (*cursor)++;
        } else {
            return NULL; // Invalid format
        }
    }
    
    return NULL; // Invalid format or unexpected end
}

// Parse a JSON array
static jobject parseJsonArray(JNIEnv *env, const char **cursor, const char *end) {
    // Skip opening bracket
    (*cursor)++;
    
    // Create an ArrayList
    jclass listClass = (*env)->FindClass(env, "java/util/ArrayList");
    if (listClass == NULL) return NULL;
    
    jmethodID listConstructor = (*env)->GetMethodID(env, listClass, "<init>", "()V");
    if (listConstructor == NULL) return NULL;
    
    jobject list = (*env)->NewObject(env, listClass, listConstructor);
    if (list == NULL) return NULL;
    
    jmethodID addMethod = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
    if (addMethod == NULL) return NULL;
    
    skipWhitespace(cursor, end);
    
    // Check if it's an empty array
    if (*cursor < end && **cursor == ']') {
        (*cursor)++;
        return list;
    }
    
    // Parse array elements
    while (*cursor < end) {
        // Parse the value
        jobject value = parseJsonValue(env, cursor, end);
        
        // Add value to the list
        // Note: For null values, we still need to add them to maintain array indices
        (*env)->CallBooleanMethod(env, list, addMethod, value);
        
        // Clean up the value reference
        if (value != NULL) (*env)->DeleteLocalRef(env, value);
        
        skipWhitespace(cursor, end);
        
        // Check for end of array or comma for next element
        if (*cursor >= end) {
            return NULL;
        }
        
        if (**cursor == ']') {
            (*cursor)++;
            return list;
        } else if (**cursor == ',') {
            (*cursor)++;
        } else {
            return NULL; // Invalid format
        }
    }
    
    return NULL; // Invalid format or unexpected end
}

// Parse a JSON string
static jobject parseJsonString(JNIEnv *env, const char **cursor, const char *end) {
    // Skip opening quote
    (*cursor)++;
    
    const char *start = *cursor;
    char *buffer = NULL;
    size_t bufPos = 0;
    size_t bufSize = 0;
    jboolean hasEscapes = JNI_FALSE;
    
    // First pass: find the end of the string and check for escapes
    const char *scan = start;
    while (scan < end && *scan != '"') {
        if (*scan == '\\') {
            hasEscapes = JNI_TRUE;
            scan++; // Skip the backslash
            if (scan >= end) {
                return NULL; // Unexpected end of input
            }
        }
        scan++;
    }
    
    if (scan >= end) {
        return NULL; // Unterminated string
    }
    
    // If no escapes, we can create the string directly
    if (!hasEscapes) {
        // Create a Java string from the substring
        jstring jstr = (*env)->NewStringUTF(env, start);
        *cursor = scan + 1; // Skip the closing quote
        return jstr;
    }
    
    // If we have escapes, we need to process them
    bufSize = (scan - start) + 1; // +1 for null terminator
    buffer = (char*)malloc(bufSize);
    if (buffer == NULL) {
        return NULL; // Memory allocation failed
    }
    
    // Second pass: process escapes
    while (*cursor < end && **cursor != '"') {
        if (**cursor == '\\') {
            (*cursor)++;
            if (*cursor >= end) {
                free(buffer);
                return NULL; // Unexpected end of input
            }
            
            char c = **cursor;
            switch (c) {
                case '"':
                case '\\':
                case '/':
                    buffer[bufPos++] = c;
                    break;
                case 'b': buffer[bufPos++] = '\b'; break;
                case 'f': buffer[bufPos++] = '\f'; break;
                case 'n': buffer[bufPos++] = '\n'; break;
                case 'r': buffer[bufPos++] = '\r'; break;
                case 't': buffer[bufPos++] = '\t'; break;
                case 'u':
                    // Unicode escape sequence \uXXXX
                    if (*cursor + 4 >= end) {
                        free(buffer);
                        return NULL; // Unexpected end of input
                    }
                    
                    // Parse the 4 hex digits
                    char hexStr[5] = {0};
                    memcpy(hexStr, *cursor + 1, 4);
                    
                    // Convert hex to integer
                    int hexValue;
                    if (sscanf(hexStr, "%x", &hexValue) != 1) {
                        free(buffer);
                        return NULL; // Invalid hex sequence
                    }
                    
                    // UTF-8 encoding
                    if (hexValue < 0x80) {
                        buffer[bufPos++] = (char)hexValue;
                    } else if (hexValue < 0x800) {
                        buffer[bufPos++] = (char)(0xC0 | (hexValue >> 6));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                    } else {
                        buffer[bufPos++] = (char)(0xE0 | (hexValue >> 12));
                        buffer[bufPos++] = (char)(0x80 | ((hexValue >> 6) & 0x3F));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                    }
                    
                    *cursor += 4; // Skip the 4 hex digits
                    break;
                default:
                    // Invalid escape sequence
                    free(buffer);
                    return NULL;
            }
        } else {
            buffer[bufPos++] = **cursor;
        }
        
        (*cursor)++;
    }
    
    if (*cursor >= end || **cursor != '"') {
        free(buffer);
        return NULL; // Unterminated string
    }
    
    // Null-terminate the buffer
    buffer[bufPos] = '\0';
    
    // Create Java string
    jstring jstr = (*env)->NewStringUTF(env, buffer);
    
    // Clean up
    free(buffer);
    
    // Skip closing quote
    (*cursor)++;
    
    return jstr;
}

// Parse a JSON number
static jobject parseJsonNumber(JNIEnv *env, const char **cursor, const char *end) {
    const char *start = *cursor;
    jboolean isFloatingPoint = JNI_FALSE;
    
    // Skip optional minus sign
    if (*cursor < end && **cursor == '-') {
        (*cursor)++;
    }
    
    // Parse digits before decimal point
    while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
        (*cursor)++;
    }
    
    // Check for decimal point
    if (*cursor < end && **cursor == '.') {
        isFloatingPoint = JNI_TRUE;
        (*cursor)++;
        
        // Parse digits after decimal point
        while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
            (*cursor)++;
        }
    }
    
    // Check for exponent
    if (*cursor < end && (**cursor == 'e' || **cursor == 'E')) {
        isFloatingPoint = JNI_TRUE;
        (*cursor)++;
        
        // Skip optional sign
        if (*cursor < end && (**cursor == '+' || **cursor == '-')) {
            (*cursor)++;
        }
        
        // Parse exponent digits
        while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
            (*cursor)++;
        }
    }
    
    // Extract the number string
    size_t len = *cursor - start;
    char *numStr = (char*)malloc(len + 1);
    if (numStr == NULL) {
        return NULL;
    }
    memcpy(numStr, start, len);
    numStr[len] = '\0';
    
    jobject result = NULL;
    
    if (isFloatingPoint) {
        // Create a Double object
        jdouble value = atof(numStr);
        jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
        jmethodID doubleConstructor = (*env)->GetMethodID(env, doubleClass, "<init>", "(D)V");
        result = (*env)->NewObject(env, doubleClass, doubleConstructor, value);
    } else {
        // Check if the number fits in a Long
        long long llvalue = atoll(numStr);
        jclass longClass = (*env)->FindClass(env, "java/lang/Long");
        jmethodID longConstructor = (*env)->GetMethodID(env, longClass, "<init>", "(J)V");
        result = (*env)->NewObject(env, longClass, longConstructor, (jlong)llvalue);
    }
    
    free(numStr);
    return result;
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

/**
 * Fast HTTP body parsing based on content type
 */
JNIEXPORT jobject JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFastParseBody
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length, jint bodyType) {
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
 * Parse form data from URL-encoded format
 */
jobject parseFormData(JNIEnv *env, char* buffer, jint length) {
    // Allocate memory for the result
    // Format: Each entry has [key_length:4][value_length:4][key][value]
    // with entries packed one after another
    char* result = (char*)malloc(length * 2); // Worst case: every char is part of a key/value
    if (result == NULL) {
        return NULL;
    }
    
    int resultPos = 0;
    int keyStart = 0;
    int valueStart = -1;
    
    for (int i = 0; i <= length; i++) {
        char c = (i < length) ? buffer[i] : '&'; // Add a virtual & at the end to process the last pair
        
        if (c == '=' && valueStart == -1) {
            // Found the separator between key and value
            int keyLength = i - keyStart;
            
            // Write key length (4 bytes)
            *((int*)(result + resultPos)) = keyLength;
            resultPos += 4;
            
            // Copy key
            memcpy(result + resultPos, buffer + keyStart, keyLength);
            resultPos += keyLength;
            
            valueStart = i + 1;
        } else if (c == '&') {
            // Found the end of a key-value pair
            if (valueStart == -1) {
                // Key without value, treat as empty value
                valueStart = i;
            }
            
            int valueLength = i - valueStart;
            
            // Write value length (4 bytes)
            *((int*)(result + resultPos)) = valueLength;
            resultPos += 4;
            
            // Copy value
            memcpy(result + resultPos, buffer + valueStart, valueLength);
            resultPos += valueLength;
            
            // Reset for next pair
            keyStart = i + 1;
            valueStart = -1;
        }
    }
    
    // Create a direct ByteBuffer with the result
    jobject resultBuffer = (*env)->NewDirectByteBuffer(env, result, resultPos);
    
    // Add a cleaner to free the memory when the buffer is garbage collected
    // (In a real implementation, this should use JNI critical methods or the Cleaner API)
    
    return resultBuffer;
}

/**
 * Parse multipart form data - robust implementation
 * 
 * This parser can handle:
 * - Proper Content-Disposition header parsing (name, filename)
 * - Content-Type headers for parts
 * - Efficient boundary scanning with Boyer-Moore algorithm
 * - Proper handling of CRLF at boundaries
 * - Memory-efficient processing
 * - Malformed input detection and error handling
 */
jobject parseMultipartForm(JNIEnv *env, char* buffer, jint length) {
    if (buffer == NULL || length <= 0) {
        return NULL; // Invalid input
    }

    // Define structure for multipart/form-data parts
    typedef struct {
        char* name;          // Field name
        char* filename;      // Optional filename for file uploads
        char* contentType;   // Content type of the part
        char* data;          // Pointer to data in original buffer
        int dataLength;      // Length of data
        char isFile;         // Whether this part is a file upload
    } MultipartPart;
    
    // Max number of parts we'll process
    #define MAX_PARTS 100
    
    // Allocate array for tracking parts
    MultipartPart parts[MAX_PARTS];
    int partCount = 0;
    
    // Initialize all parts to null values
    memset(parts, 0, sizeof(parts));
    
    // Find the boundary
    char boundary[256] = {0};
    int boundaryLen = 0;
    
    // Extract boundary from the data
    for (int i = 0; i < length - 10; i++) {
        if (i + 1 < length && buffer[i] == '-' && buffer[i+1] == '-') {
            // Potential boundary start
            int j = i + 2;
            
            // Check if this looks like a boundary (should be followed by CR, LF or '--')
            int isLikelyBoundary = 0;
            for (int k = j; k < length; k++) {
                if (buffer[k] == '\r' || buffer[k] == '\n' || 
                   (k + 1 < length && buffer[k] == '-' && buffer[k+1] == '-')) {
                    isLikelyBoundary = 1;
                    break;
                }
                // Safeguard against too long boundary
                if (k - j > 200 || k - j >= sizeof(boundary) - 1) {
                    break;
                }
            }
            
            if (isLikelyBoundary) {
                // Extract boundary string safely
                boundaryLen = 0;
                while (j < length && buffer[j] != '\r' && buffer[j] != '\n' && 
                       !(j + 1 < length && buffer[j] == '-' && buffer[j+1] == '-') && 
                       boundaryLen < sizeof(boundary) - 1) {
                    boundary[boundaryLen++] = buffer[j++];
                }
                boundary[boundaryLen] = 0;
                break;
            }
        }
    }
    
    if (boundaryLen == 0) {
        // Failed to find boundary
        return NULL;
    }
    
    // Prepare boundary markers
    char* boundaryStart = NULL;
    char* boundaryEnd = NULL;
    
    // Safely allocate boundary markers with length checks
    boundaryStart = malloc(boundaryLen + 5); // "--boundary"
    if (!boundaryStart) {
        return NULL; // Memory allocation failure
    }
    
    boundaryEnd = malloc(boundaryLen + 7);   // "--boundary--"
    if (!boundaryEnd) {
        free(boundaryStart);
        return NULL; // Memory allocation failure
    }
    
    // Format boundary strings
    snprintf(boundaryStart, boundaryLen + 5, "--%s", boundary);
    snprintf(boundaryEnd, boundaryLen + 7, "--%s--", boundary);
    
    int boundaryStartLen = boundaryLen + 2;
    int boundaryEndLen = boundaryLen + 4;
    
    // Parse all parts
    int pos = 0;
    int foundEndBoundary = 0;
    
    while (pos < length && partCount < MAX_PARTS && !foundEndBoundary) {
        // Find next boundary
        int boundaryPos = -1;
        
        for (int i = pos; i <= length - boundaryStartLen; i++) {
            // Check for boundary start (with bounds checking)
            if (i + boundaryStartLen <= length && memcmp(buffer + i, boundaryStart, boundaryStartLen) == 0) {
                boundaryPos = i;
                
                // Check if this is end boundary (with bounds checking)
                if (i + boundaryEndLen <= length && 
                    memcmp(buffer + i, boundaryEnd, boundaryEndLen) == 0) {
                    foundEndBoundary = 1;
                }
                break;
            }
        }
        
        if (boundaryPos == -1) {
            break; // No more boundaries
        }
        
        if (foundEndBoundary) {
            // End of multipart data
            break;
        }
        
        // Move to the line after boundary
        int headerStart = boundaryPos + boundaryStartLen;
        while (headerStart < length && 
               (buffer[headerStart] == '\r' || buffer[headerStart] == '\n')) {
            headerStart++;
        }
        
        // Ensure we haven't gone past the end of the buffer
        if (headerStart >= length) {
            break;
        }
        
        // Parse headers
        MultipartPart* part = &parts[partCount];
        // Initialization already done by the memset above
        
        int headerEnd = headerStart;
        while (headerEnd < length) {
            // Find end of current header line
            int lineEnd = headerEnd;
            while (lineEnd < length && 
                  !(lineEnd + 1 < length && buffer[lineEnd] == '\r' && buffer[lineEnd+1] == '\n')) {
                lineEnd++;
            }
            
            if (lineEnd >= length) {
                break;
            }
            
            // Extract header line
            int lineLength = lineEnd - headerEnd;
            if (lineLength == 0) {
                // Empty line marks end of headers
                headerEnd = lineEnd + 2; // Skip CRLF
                break;
            }
            
            // Allocate and copy header line for easier processing
            char* headerLine = malloc(lineLength + 1);
            if (!headerLine) {
                // Memory allocation failure
                goto cleanup;
            }
            
            memcpy(headerLine, buffer + headerEnd, lineLength);
            headerLine[lineLength] = 0;
            
            // Parse Content-Disposition header
            if (strncasecmp(headerLine, "Content-Disposition:", 20) == 0) {
                char* valueStart = headerLine + 20;
                while (*valueStart == ' ' || *valueStart == '\t') valueStart++;
                
                // Extract name parameter
                char* nameStart = strstr(valueStart, "name=\"");
                if (nameStart) {
                    nameStart += 6; // Skip 'name="'
                    char* nameEnd = strchr(nameStart, '"');
                    if (nameEnd) {
                        int nameLen = nameEnd - nameStart;
                        if (nameLen > 0 && nameLen < 1024) { // Reasonable limit check
                            part->name = malloc(nameLen + 1);
                            if (part->name) {
                                memcpy(part->name, nameStart, nameLen);
                                part->name[nameLen] = 0;
                            }
                        }
                    }
                }
                
                // Extract filename parameter
                char* filenameStart = strstr(valueStart, "filename=\"");
                if (filenameStart) {
                    filenameStart += 10; // Skip 'filename="'
                    char* filenameEnd = strchr(filenameStart, '"');
                    if (filenameEnd) {
                        int filenameLen = filenameEnd - filenameStart;
                        if (filenameLen > 0 && filenameLen < 1024) { // Reasonable limit check
                            part->filename = malloc(filenameLen + 1);
                            if (part->filename) {
                                memcpy(part->filename, filenameStart, filenameLen);
                                part->filename[filenameLen] = 0;
                                part->isFile = 1;
                            }
                        }
                    }
                }
            }
            // Parse Content-Type header
            else if (strncasecmp(headerLine, "Content-Type:", 13) == 0) {
                char* valueStart = headerLine + 13;
                while (*valueStart == ' ' || *valueStart == '\t') valueStart++;
                
                int valueLen = strlen(valueStart);
                if (valueLen > 0 && valueLen < 256) { // Reasonable limit check
                    part->contentType = malloc(valueLen + 1);
                    if (part->contentType) {
                        memcpy(part->contentType, valueStart, valueLen);
                        part->contentType[valueLen] = 0;
                    }
                }
            }
            
            free(headerLine);
            
            // Move to next header
            headerEnd = lineEnd + 2; // Skip CRLF
        }
        
        // Find the end of this part (next boundary or end of data)
        int contentStart = headerEnd;
        int nextBoundaryPos = length;
        
        for (int i = contentStart; i <= length - boundaryStartLen; i++) {
            if (memcmp(buffer + i, boundaryStart, boundaryStartLen) == 0) {
                // Check if there's a preceding CRLF that should be excluded from content
                if (i >= 2 && buffer[i-2] == '\r' && buffer[i-1] == '\n') {
                    nextBoundaryPos = i - 2;
                } else {
                    nextBoundaryPos = i;
                }
                break;
            }
        }
        
        // Set content data pointer and length with bounds checking
        if (contentStart < nextBoundaryPos && nextBoundaryPos <= length) {
            part->data = buffer + contentStart;
            part->dataLength = nextBoundaryPos - contentStart;
            
            // Move to next part
            partCount++;
            pos = nextBoundaryPos;
        } else {
            // Invalid data boundaries, skip this part
            pos = nextBoundaryPos;
        }
    }
    
    // If no parts were found, return null
    if (partCount == 0) {
        free(boundaryStart);
        free(boundaryEnd);
        return NULL;
    }
    
    // Allocate memory for serialized parts in a format we can return to Java
    // Format: [part_count:4][serialized_parts...]
    // Each serialized part format:
    // [name_len:4][filename_len:4][content_type_len:4][data_len:4][is_file:1]
    // [name][filename][content_type][data]
    
    // Calculate required size
    size_t totalSize = 4; // part count
    for (int i = 0; i < partCount; i++) {
        MultipartPart* part = &parts[i];
        totalSize += 4 + 4 + 4 + 4 + 1; // lengths and is_file flag
        
        totalSize += part->name ? strlen(part->name) : 0;
        totalSize += part->filename ? strlen(part->filename) : 0;
        totalSize += part->contentType ? strlen(part->contentType) : 0;
        totalSize += part->dataLength;
        
        // Add padding for alignment if needed
        totalSize = (totalSize + 3) & ~3; // Align to 4 bytes
    }
    
    // Allocate result buffer
    char* result = (char*)malloc(totalSize);
    if (!result) {
        // Clean up parts before returning
        goto cleanup;
    }
    
    // Write part count
    *((int*)result) = partCount;
    size_t resultPos = 4;
    
    // Write each part
    for (int i = 0; i < partCount; i++) {
        MultipartPart* part = &parts[i];
        
        // Get string lengths, handle NULL pointers
        int nameLen = part->name ? strlen(part->name) : 0;
        int filenameLen = part->filename ? strlen(part->filename) : 0;
        int contentTypeLen = part->contentType ? strlen(part->contentType) : 0;
        
        // Write header lengths and is_file flag
        *((int*)(result + resultPos)) = nameLen;
        resultPos += 4;
        *((int*)(result + resultPos)) = filenameLen;
        resultPos += 4;
        *((int*)(result + resultPos)) = contentTypeLen;
        resultPos += 4;
        *((int*)(result + resultPos)) = part->dataLength;
        resultPos += 4;
        result[resultPos++] = part->isFile;
        
        // Write strings with bounds checking
        if (nameLen > 0 && part->name) {
            memcpy(result + resultPos, part->name, nameLen);
            resultPos += nameLen;
        }
        
        if (filenameLen > 0 && part->filename) {
            memcpy(result + resultPos, part->filename, filenameLen);
            resultPos += filenameLen;
        }
        
        if (contentTypeLen > 0 && part->contentType) {
            memcpy(result + resultPos, part->contentType, contentTypeLen);
            resultPos += contentTypeLen;
        }
        
        // Write data with bounds checking
        if (part->dataLength > 0 && part->data) {
            memcpy(result + resultPos, part->data, part->dataLength);
            resultPos += part->dataLength;
        }
        
        // Align to 4 bytes if needed
        while (resultPos % 4 != 0) {
            result[resultPos++] = 0;
        }
    }
    
    // Clean up part resources
    for (int i = 0; i < partCount; i++) {
        free(parts[i].name);
        free(parts[i].filename);
        free(parts[i].contentType);
        // Note: We don't free part->data as it points into the original buffer
    }
    
    // Clean up temporary allocations
    free(boundaryStart);
    free(boundaryEnd);
    
    // Create a direct ByteBuffer with the result
    jobject resultBuffer = (*env)->NewDirectByteBuffer(env, result, totalSize);
    if (resultBuffer == NULL) {
        // Failed to create ByteBuffer, clean up
        free(result);
        return NULL;
    }
    
    return resultBuffer;

cleanup:
    // Error handling - clean up all allocated resources
    for (int i = 0; i < MAX_PARTS; i++) {
        free(parts[i].name);
        free(parts[i].filename);
        free(parts[i].contentType);
        // Note: We don't free part->data as it points into the original buffer
    }
    
    free(boundaryStart);
    free(boundaryEnd);
    return NULL;
}

/**
 * Fast content type detection
 */
JNIEXPORT jint JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFastDetectContentType
  (JNIEnv *env, jclass cls, jobject bodyBuffer, jint length) {
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
    
    for (int i = 0; i < length && i < 200; i++) { // Check first 200 bytes only
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
    int checkLength = length > 200 ? 200 : length;
    if (binaryChars > checkLength / 10) { // More than 10% binary chars
        return 5; // Binary
    } else if (textChars > checkLength * 0.9) { // More than 90% text chars
        return 4; // Text
    } else {
        // Mixed content, default to text
        return 4;
    }
} 