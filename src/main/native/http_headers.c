#include "blyfastnative.h"

/**
 * Fast native HTTP header parsing - improved with proper cleanup
 */
JNIEXPORT jlong JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeParseHttpHeaders
  (JNIEnv *env, jclass cls, jobject headerBytes, jint length) {
    if (headerBytes == NULL || length <= 0) {
        return 0;
    }
    
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
    
    // Thread-safe ID assignment
    pthread_mutex_lock(&headers_mutex);
    jlong newId = next_header_id++;
    if (newId >= MAX_HEADERS) {
        // Wrap around and find available slot
        newId = 1;
        while (newId < MAX_HEADERS && headers_storage[newId] != NULL) {
            newId++;
        }
        if (newId >= MAX_HEADERS) {
            pthread_mutex_unlock(&headers_mutex);
            free(headers);
            return 0; // No available slots
        }
    }
    headers->id = newId;
    pthread_mutex_unlock(&headers_mutex);
    
    // Parse headers
    char* pos = buffer;
    char* end = buffer + length;
    char* line_start = pos;
    char* line_end;
    char* colon;
    
    while (pos < end) {
        // Find end of line - optimized
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
                freeHeadersList(headers->first);
                free(headers);
                return 0;
            }
            
            // Extract name (trim trailing whitespace)
            size_t name_len = colon - line_start;
            header->name = (char*)malloc(name_len + 1);
            if (!header->name) {
                free(header);
                freeHeadersList(headers->first);
                free(headers);
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
                freeHeadersList(headers->first);
                free(headers);
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
    
    // Store headers in global storage with thread safety
    pthread_mutex_lock(&headers_mutex);
    if (headers->id < MAX_HEADERS) {
        headers_storage[headers->id] = headers;
    } else {
        pthread_mutex_unlock(&headers_mutex);
        freeHeadersList(headers->first);
        free(headers);
        return 0;
    }
    pthread_mutex_unlock(&headers_mutex);
    
    return headers->id;
}

/**
 * Retrieves a header value by name from previously parsed headers - thread-safe
 */
JNIEXPORT jstring JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeGetHeader
  (JNIEnv *env, jclass cls, jlong headersId, jstring headerName) {
    if (headerName == NULL) {
        return NULL;
    }
    
    // Check if headers ID is valid
    if (headersId <= 0 || headersId >= MAX_HEADERS) {
        return NULL;
    }
    
    // Thread-safe access
    pthread_mutex_lock(&headers_mutex);
    ParsedHeaders* headers = headers_storage[headersId];
    if (headers == NULL) {
        pthread_mutex_unlock(&headers_mutex);
        return NULL;
    }
    pthread_mutex_unlock(&headers_mutex);
    
    // Get the header name
    const char *nameStr = (*env)->GetStringUTFChars(env, headerName, NULL);
    if (nameStr == NULL) {
        return NULL; // OutOfMemoryError
    }
    
    // Find the header
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
 * Releases resources associated with previously parsed headers - thread-safe
 */
JNIEXPORT void JNICALL Java_com_blyfast_nativeopt_NativeOptimizer_nativeFreeHeaders
  (JNIEnv *env, jclass cls, jlong headersId) {
    // Check if headers ID is valid
    if (headersId <= 0 || headersId >= MAX_HEADERS) {
        return;
    }
    
    // Thread-safe cleanup
    pthread_mutex_lock(&headers_mutex);
    ParsedHeaders* headers = headers_storage[headersId];
    if (headers == NULL) {
        pthread_mutex_unlock(&headers_mutex);
        return;
    }
    headers_storage[headersId] = NULL;  // Clear slot first
    pthread_mutex_unlock(&headers_mutex);
    
    // Free header values
    freeHeadersList(headers->first);
    
    // Free headers structure
    free(headers);
}

