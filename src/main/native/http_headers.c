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
    
    // Thread-safe ID assignment with free list optimization
    pthread_mutex_lock(&headers_mutex);
    jlong newId = 0;
    
    // First try free list (O(1))
    if (free_header_count > 0) {
        newId = free_header_slots[--free_header_count];
    } else {
        // Use sequential allocation
        newId = next_header_id++;
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
    }
    
    headers->id = newId;
    headers_storage[newId] = headers; // Store immediately while holding lock
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
            // Trim trailing whitespace from header name
            while (name_len > 0 && (line_start[name_len - 1] == ' ' || line_start[name_len - 1] == '\t')) {
                name_len--;
            }
            if (name_len > MAX_HEADER_NAME_LEN) {
                name_len = MAX_HEADER_NAME_LEN;
            }
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
    
    // Headers already stored during ID assignment
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
    
    // Thread-safe access - keep lock during access to prevent free
    pthread_mutex_lock(&headers_mutex);
    ParsedHeaders* headers = headers_storage[headersId];
    if (headers == NULL) {
        pthread_mutex_unlock(&headers_mutex);
        return NULL;
    }
    
    // Get the header name while holding lock
    const char *nameStr = (*env)->GetStringUTFChars(env, headerName, NULL);
    if (nameStr == NULL) {
        pthread_mutex_unlock(&headers_mutex);
        return NULL; // OutOfMemoryError
    }
    
    // Find the header
    HeaderValue* current = headers->first;
    jstring result = NULL;
    
    while (current != NULL) {
        if (strcasecmp(current->name, nameStr) == 0) {
            // Found the header, return its value
            result = (*env)->NewStringUTF(env, current->value);
            CHECK_JNI_EXCEPTION(env);
            break;
        }
        current = current->next;
    }
    
    pthread_mutex_unlock(&headers_mutex);
    (*env)->ReleaseStringUTFChars(env, headerName, nameStr);
    
    return result;
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
    
    // Add slot back to free list if there's room (with bounds checking)
    if (free_header_count < MAX_HEADERS) {
        free_header_slots[free_header_count++] = (int)headersId;
    }
    
    pthread_mutex_unlock(&headers_mutex);
    
    // Free header values (safe to do outside lock)
    freeHeadersList(headers->first);
    
    // Free headers structure
    free(headers);
}

