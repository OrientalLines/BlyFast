#include "blyfastnative.h"

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
                jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
                if (booleanClass == NULL) return NULL;
                jfieldID trueField = (*env)->GetStaticFieldID(env, booleanClass, "TRUE", "Ljava/lang/Boolean;");
                if (trueField == NULL) return NULL;
                return (*env)->GetStaticObjectField(env, booleanClass, trueField);
            }
            return NULL;
        case 'f':
            // Parse 'false'
            if (*cursor + 5 <= end && strncmp(*cursor, "false", 5) == 0) {
                *cursor += 5;
                jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
                if (booleanClass == NULL) return NULL;
                jfieldID falseField = (*env)->GetStaticFieldID(env, booleanClass, "FALSE", "Ljava/lang/Boolean;");
                if (falseField == NULL) return NULL;
                return (*env)->GetStaticObjectField(env, booleanClass, falseField);
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
    
    // Create a HashMap
    jclass mapClass = (*env)->FindClass(env, "java/util/HashMap");
    if (mapClass == NULL) return NULL;
    
    jmethodID mapConstructor = (*env)->GetMethodID(env, mapClass, "<init>", "()V");
    if (mapConstructor == NULL) return NULL;
    
    jobject map = (*env)->NewObject(env, mapClass, mapConstructor);
    if (map == NULL) return NULL;
    
    jmethodID putMethod = (*env)->GetMethodID(env, mapClass, "put", 
                                             "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (putMethod == NULL) {
        (*env)->DeleteLocalRef(env, map);
        return NULL;
    }
    
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
        (*env)->CallObjectMethod(env, map, putMethod, key, value);
        
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
    
    // Create an ArrayList
    jclass listClass = (*env)->FindClass(env, "java/util/ArrayList");
    if (listClass == NULL) return NULL;
    
    jmethodID listConstructor = (*env)->GetMethodID(env, listClass, "<init>", "()V");
    if (listConstructor == NULL) return NULL;
    
    jobject list = (*env)->NewObject(env, listClass, listConstructor);
    if (list == NULL) return NULL;
    
    jmethodID addMethod = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
    if (addMethod == NULL) {
        (*env)->DeleteLocalRef(env, list);
        return NULL;
    }
    
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
                    } else if (hexValue < 0x800) {
                        buffer[bufPos++] = (char)(0xC0 | (hexValue >> 6));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                    } else if (hexValue < 0xD800 || hexValue >= 0xE000) {
                        // Regular 3-byte UTF-8
                        buffer[bufPos++] = (char)(0xE0 | (hexValue >> 12));
                        buffer[bufPos++] = (char)(0x80 | ((hexValue >> 6) & 0x3F));
                        buffer[bufPos++] = (char)(0x80 | (hexValue & 0x3F));
                    } else {
                        // Surrogate pair - simplified handling
                        free(buffer);
                        return NULL; // Surrogate pairs not fully supported
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
    if (len == 0 || len > 64) {  // Reasonable limit
        return NULL;
    }
    
    char *numStr = (char*)malloc(len + 1);
    if (numStr == NULL) {
        return NULL;
    }
    memcpy(numStr, start, len);
    numStr[len] = '\0';
    
    jobject result = NULL;
    
    if (isFloatingPoint) {
        // Create a Double object
        jdouble value = strtod(numStr, NULL);
        jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
        if (doubleClass != NULL) {
            jmethodID doubleConstructor = (*env)->GetMethodID(env, doubleClass, "<init>", "(D)V");
            if (doubleConstructor != NULL) {
                result = (*env)->NewObject(env, doubleClass, doubleConstructor, value);
            }
        }
    } else {
        // Check if the number fits in a Long
        long long llvalue = strtoll(numStr, NULL, 10);
        jclass longClass = (*env)->FindClass(env, "java/lang/Long");
        if (longClass != NULL) {
            jmethodID longConstructor = (*env)->GetMethodID(env, longClass, "<init>", "(J)V");
            if (longConstructor != NULL) {
                result = (*env)->NewObject(env, longClass, longConstructor, (jlong)llvalue);
            }
        }
    }
    
    free(numStr);
    return result;
}

