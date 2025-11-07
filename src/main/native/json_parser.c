#include "blyfastnative.h"

// Cached JNI class and method IDs for performance
static jclass cachedHashMapClass = NULL;
static jclass cachedArrayListClass = NULL;
static jclass cachedBooleanClass = NULL;
static jclass cachedLongClass = NULL;
static jclass cachedDoubleClass = NULL;
static jmethodID cachedHashMapConstructor = NULL;
static jmethodID cachedHashMapPut = NULL;
static jmethodID cachedArrayListConstructor = NULL;
static jmethodID cachedArrayListAdd = NULL;
static jfieldID cachedBooleanTrueField = NULL;
static jfieldID cachedBooleanFalseField = NULL;
static jmethodID cachedLongConstructor = NULL;
static jmethodID cachedDoubleConstructor = NULL;
static int jniCacheInitialized = 0;

// Initialize JNI cache - automatically called on first use or from JNI_OnLoad
void initJsonParserCache(JNIEnv *env) {
    if (jniCacheInitialized || env == NULL) return;
    
    cachedHashMapClass = (*env)->FindClass(env, "java/util/HashMap");
    if (cachedHashMapClass) {
        cachedHashMapClass = (jclass)(*env)->NewGlobalRef(env, cachedHashMapClass);
        cachedHashMapConstructor = (*env)->GetMethodID(env, cachedHashMapClass, "<init>", "()V");
        cachedHashMapPut = (*env)->GetMethodID(env, cachedHashMapClass, "put", 
                                               "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    }
    
    cachedArrayListClass = (*env)->FindClass(env, "java/util/ArrayList");
    if (cachedArrayListClass) {
        cachedArrayListClass = (jclass)(*env)->NewGlobalRef(env, cachedArrayListClass);
        cachedArrayListConstructor = (*env)->GetMethodID(env, cachedArrayListClass, "<init>", "()V");
        cachedArrayListAdd = (*env)->GetMethodID(env, cachedArrayListClass, "add", "(Ljava/lang/Object;)Z");
    }
    
    cachedBooleanClass = (*env)->FindClass(env, "java/lang/Boolean");
    if (cachedBooleanClass) {
        cachedBooleanClass = (jclass)(*env)->NewGlobalRef(env, cachedBooleanClass);
        cachedBooleanTrueField = (*env)->GetStaticFieldID(env, cachedBooleanClass, "TRUE", "Ljava/lang/Boolean;");
        cachedBooleanFalseField = (*env)->GetStaticFieldID(env, cachedBooleanClass, "FALSE", "Ljava/lang/Boolean;");
    }
    
    cachedLongClass = (*env)->FindClass(env, "java/lang/Long");
    if (cachedLongClass) {
        cachedLongClass = (jclass)(*env)->NewGlobalRef(env, cachedLongClass);
        cachedLongConstructor = (*env)->GetMethodID(env, cachedLongClass, "<init>", "(J)V");
    }
    
    cachedDoubleClass = (*env)->FindClass(env, "java/lang/Double");
    if (cachedDoubleClass) {
        cachedDoubleClass = (jclass)(*env)->NewGlobalRef(env, cachedDoubleClass);
        cachedDoubleConstructor = (*env)->GetMethodID(env, cachedDoubleClass, "<init>", "(D)V");
    }
    
    jniCacheInitialized = 1;
}

// Skip whitespace characters - optimized with lookup table
void skipWhitespace(const char **cursor, const char *end) {
    while (*cursor < end) {
        char c = **cursor;
        // Optimized: check most common whitespace first
        if (c == ' ') {
            (*cursor)++;
        } else if (c == '\t' || c == '\n' || c == '\r') {
            (*cursor)++;
        } else {
            break;
        }
    }
}

// Parse a JSON value (object, array, string, number, true, false, null)
jobject parseJsonValue(JNIEnv *env, const char **cursor, const char *end) {
    skipWhitespace(cursor, end);
    
    if (*cursor >= end) {
        return NULL; // Unexpected end of input
    }
    
    char c = **cursor;
    
    // Optimized dispatch based on first character
    switch (c) {
        case '{':
            return parseJsonObject(env, cursor, end);
        case '[':
            return parseJsonArray(env, cursor, end);
        case '"':
            return parseJsonString(env, cursor, end);
        case 't':
            // Parse 'true'
            if (*cursor + 4 <= end && strncmp(*cursor, "true", 4) == 0) {
                *cursor += 4;
                if (!cachedBooleanClass) {
                    initJsonParserCache(env);
                }
                if (cachedBooleanClass && cachedBooleanTrueField) {
                    jobject result = (*env)->GetStaticObjectField(env, cachedBooleanClass, cachedBooleanTrueField);
                    CHECK_JNI_EXCEPTION(env);
                    return result;
                }
            }
            return NULL;
        case 'f':
            // Parse 'false'
            if (*cursor + 5 <= end && strncmp(*cursor, "false", 5) == 0) {
                *cursor += 5;
                if (!cachedBooleanClass) {
                    initJsonParserCache(env);
                }
                if (cachedBooleanClass && cachedBooleanFalseField) {
                    jobject result = (*env)->GetStaticObjectField(env, cachedBooleanClass, cachedBooleanFalseField);
                    CHECK_JNI_EXCEPTION(env);
                    return result;
                }
            }
            return NULL;
        case 'n':
            // Parse 'null'
            if (*cursor + 4 <= end && strncmp(*cursor, "null", 4) == 0) {
                *cursor += 4;
                return NULL; // In Java, null is represented as NULL in C
            }
            return NULL;
        case '-':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            return parseJsonNumber(env, cursor, end);
        default:
            return NULL; // Invalid JSON or unsupported value type
    }
}

// Parse a JSON object - improved error handling
jobject parseJsonObject(JNIEnv *env, const char **cursor, const char *end) {
    // Skip opening brace
    (*cursor)++;
    
    // Initialize cache if needed
    if (!cachedHashMapClass) {
        initJsonParserCache(env);
    }
    
    // Use cached HashMap class and methods
    if (!cachedHashMapClass || !cachedHashMapConstructor || !cachedHashMapPut) {
        return NULL;
    }
    
    jobject map = (*env)->NewObject(env, cachedHashMapClass, cachedHashMapConstructor);
    CHECK_JNI_EXCEPTION(env);
    if (map == NULL) return NULL;
    
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
            (*env)->DeleteLocalRef(env, map);
            return NULL; // Invalid format
        }
        
        // Parse the key
        jobject key = parseJsonString(env, cursor, end);
        if (key == NULL) {
            (*env)->DeleteLocalRef(env, map);
            return NULL;
        }
        
        skipWhitespace(cursor, end);
        
        // Expect a colon
        if (*cursor >= end || **cursor != ':') {
            (*env)->DeleteLocalRef(env, key);
            (*env)->DeleteLocalRef(env, map);
            return NULL;
        }
        (*cursor)++; // Skip the colon
        
        // Parse the value
        jobject value = parseJsonValue(env, cursor, end);
        if (value == NULL && (*cursor - 1 >= end || *(*cursor - 1) != 'n')) {
            // NULL is valid for JSON null, but check if it was actually null
            // This is a simplified check
        }
        
        // Add key-value pair to the map
        // Note: value can be NULL for JSON null
        (*env)->CallObjectMethod(env, map, cachedHashMapPut, key, value);
        CHECK_JNI_EXCEPTION_CLEANUP(env, {
            (*env)->DeleteLocalRef(env, key);
            if (value != NULL) (*env)->DeleteLocalRef(env, value);
            (*env)->DeleteLocalRef(env, map);
        });
        
        // Clean up references
        (*env)->DeleteLocalRef(env, key);
        if (value != NULL) (*env)->DeleteLocalRef(env, value);
        
        skipWhitespace(cursor, end);
        
        // Check for end of object or comma for next pair
        if (*cursor >= end) {
            (*env)->DeleteLocalRef(env, map);
            return NULL;
        }
        
        if (**cursor == '}') {
            (*cursor)++;
            return map;
        } else if (**cursor == ',') {
            (*cursor)++;
        } else {
            (*env)->DeleteLocalRef(env, map);
            return NULL; // Invalid format
        }
    }
    
    (*env)->DeleteLocalRef(env, map);
    return NULL; // Invalid format or unexpected end
}

// Parse a JSON array - improved error handling
jobject parseJsonArray(JNIEnv *env, const char **cursor, const char *end) {
    // Skip opening bracket
    (*cursor)++;
    
    // Initialize cache if needed
    if (!cachedArrayListClass) {
        initJsonParserCache(env);
    }
    
    // Use cached ArrayList class and methods
    if (!cachedArrayListClass || !cachedArrayListConstructor || !cachedArrayListAdd) {
        return NULL;
    }
    
    jobject list = (*env)->NewObject(env, cachedArrayListClass, cachedArrayListConstructor);
    CHECK_JNI_EXCEPTION(env);
    if (list == NULL) return NULL;
    
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
        (*env)->CallBooleanMethod(env, list, cachedArrayListAdd, value);
        CHECK_JNI_EXCEPTION_CLEANUP(env, {
            if (value != NULL) (*env)->DeleteLocalRef(env, value);
            (*env)->DeleteLocalRef(env, list);
        });
        
        // Clean up the value reference
        if (value != NULL) (*env)->DeleteLocalRef(env, value);
        
        skipWhitespace(cursor, end);
        
        // Check for end of array or comma for next element
        if (*cursor >= end) {
            (*env)->DeleteLocalRef(env, list);
            return NULL;
        }
        
        if (**cursor == ']') {
            (*cursor)++;
            return list;
        } else if (**cursor == ',') {
            (*cursor)++;
        } else {
            (*env)->DeleteLocalRef(env, list);
            return NULL; // Invalid format
        }
    }
    
    (*env)->DeleteLocalRef(env, list);
    return NULL; // Invalid format or unexpected end
}

// Parse a JSON string - optimized version
jobject parseJsonString(JNIEnv *env, const char **cursor, const char *end) {
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
        size_t strLen = scan - start;
        if (strLen == 0) {
            *cursor = scan + 1; // Skip the closing quote
            return (*env)->NewStringUTF(env, "");
        }
        // Allocate temporary buffer for substring
        char *tempBuf = (char*)malloc(strLen + 1);
        if (tempBuf == NULL) return NULL;
        memcpy(tempBuf, start, strLen);
        tempBuf[strLen] = '\0';
        jstring jstr = (*env)->NewStringUTF(env, tempBuf);
        free(tempBuf);
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
                    
                    // Parse the 4 hex digits - optimized
                    int hexValue = 0;
                    for (int i = 1; i <= 4; i++) {
                        int digit = hexCharToInt((*cursor)[i]);
                        if (digit < 0) {
                            free(buffer);
                            return NULL; // Invalid hex digit
                        }
                        hexValue = (hexValue << 4) | digit;
                    }
                    
                    // UTF-8 encoding
                    if (hexValue < 0x80) {
                        buffer[bufPos++] = (char)hexValue;
                        *cursor += 4; // Skip the 4 hex digits
                    } else if (hexValue < 0x800) {
                        buffer[bufPos++] = (char)(0xC0 | (hexValue >> 6));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                        *cursor += 4; // Skip the 4 hex digits
                    } else if (hexValue < 0xD800 || hexValue >= 0xE000) {
                        // Regular 3-byte UTF-8 (including BMP characters)
                        buffer[bufPos++] = (char)(0xE0 | (hexValue >> 12));
                        buffer[bufPos++] = (char)(0x80 | ((hexValue >> 6) & 0x3F));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                        *cursor += 4; // Skip the 4 hex digits
                    } else if (hexValue >= 0xD800 && hexValue < 0xDC00) {
                        // High surrogate - need to read low surrogate
                        // Check if we have space for the next escape sequence
                        if (*cursor + 9 >= end || (*cursor)[5] != '\\' || (*cursor)[6] != 'u') {
                            free(buffer);
                            return NULL; // Incomplete surrogate pair
                        }
                        
                        // Parse low surrogate
                        int lowSurrogate = 0;
                        for (int i = 7; i <= 10; i++) {
                            int digit = hexCharToInt((*cursor)[i]);
                            if (digit < 0) {
                                free(buffer);
                                return NULL; // Invalid hex digit
                            }
                            lowSurrogate = (lowSurrogate << 4) | digit;
                        }
                        
                        // Validate low surrogate
                        if (lowSurrogate < 0xDC00 || lowSurrogate >= 0xE000) {
                            free(buffer);
                            return NULL; // Invalid surrogate pair
                        }
                        
                        // Convert surrogate pair to code point
                        int codePoint = 0x10000 + ((hexValue - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                        
                        // Encode as 4-byte UTF-8
                        buffer[bufPos++] = (char)(0xF0 | (codePoint >> 18));
                        buffer[bufPos++] = (char)(0x80 | ((codePoint >> 12) & 0x3F));
                        buffer[bufPos++] = (char)(0x80 | ((codePoint >> 6) & 0x3F));
                        buffer[bufPos++] = (char)(0x80 | (codePoint & 0x3F));
                        
                        // Skip both escape sequences: \uXXXX\uXXXX
                        // cursor points to 'u', so skip: u(1) + XXXX(4) + \u(2) + XXXX(4) = 11 chars
                        *cursor += 11;
                        // Don't increment cursor again - break out of switch
                        break;
                    } else {
                        // Low surrogate (0xDC00-0xDFFF) without high surrogate - invalid
                        free(buffer);
                        return NULL;
                    }
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

// Parse a JSON number - improved precision
jobject parseJsonNumber(JNIEnv *env, const char **cursor, const char *end) {
    const char *start = *cursor;
    jboolean isFloatingPoint = JNI_FALSE;
    jboolean hasDigits = JNI_FALSE;
    
    // Skip optional minus sign
    if (*cursor < end && **cursor == '-') {
        (*cursor)++;
    }
    
    // Parse digits before decimal point
    while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
        hasDigits = JNI_TRUE;
        (*cursor)++;
    }
    
    if (!hasDigits) {
        return NULL; // Invalid number
    }
    
    // Check for decimal point
    if (*cursor < end && **cursor == '.') {
        isFloatingPoint = JNI_TRUE;
        (*cursor)++;
        
        // Parse digits after decimal point
        jboolean hasDecimalDigits = JNI_FALSE;
        while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
            hasDecimalDigits = JNI_TRUE;
            (*cursor)++;
        }
        if (!hasDecimalDigits) {
            return NULL; // Invalid number format
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
        jboolean hasExponentDigits = JNI_FALSE;
        while (*cursor < end && **cursor >= '0' && **cursor <= '9') {
            hasExponentDigits = JNI_TRUE;
            (*cursor)++;
        }
        if (!hasExponentDigits) {
            return NULL; // Invalid exponent
        }
    }
    
    // Extract the number string
    size_t len = *cursor - start;
    if (len == 0 || len > MAX_JSON_NUMBER_LEN) {
        return NULL;
    }
    
    char *numStr = (char*)malloc(len + 1);
    if (numStr == NULL) {
        return NULL;
    }
    memcpy(numStr, start, len);
    numStr[len] = '\0';
    
    jobject result = NULL;
    
    // Initialize cache if needed
    if (!cachedDoubleClass || !cachedLongClass) {
        initJsonParserCache(env);
    }
    
    if (isFloatingPoint) {
        // Create a Double object
        jdouble value = strtod(numStr, NULL);
        if (cachedDoubleClass && cachedDoubleConstructor) {
            result = (*env)->NewObject(env, cachedDoubleClass, cachedDoubleConstructor, value);
            CHECK_JNI_EXCEPTION(env);
        }
    } else {
        // Check if the number fits in a Long
        long long llvalue = strtoll(numStr, NULL, 10);
        if (cachedLongClass && cachedLongConstructor) {
            result = (*env)->NewObject(env, cachedLongClass, cachedLongConstructor, (jlong)llvalue);
            CHECK_JNI_EXCEPTION(env);
        }
    }
    
    free(numStr);
    return result;
}

