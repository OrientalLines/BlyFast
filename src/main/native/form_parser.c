#include "blyfastnative.h"

/**
 * Parse form data from URL-encoded format - now with URL decoding
 */
jobject parseFormData(JNIEnv *env, char* buffer, jint length) {
    if (buffer == NULL || length <= 0) {
        return NULL;
    }
    
    // Allocate memory for the result
    // Format: Each entry has [key_length:4][value_length:4][key][value]
    // with entries packed one after another
    // We allocate more space to account for URL decoding expansion
    char* result = (char*)malloc(length * 3); // Extra space for URL decoding
    if (result == NULL) {
        return NULL;
    }
    
    // Temporary buffers for URL decoding
    char* keyBuffer = (char*)malloc(length + 1);
    char* valueBuffer = (char*)malloc(length + 1);
    if (keyBuffer == NULL || valueBuffer == NULL) {
        free(result);
        if (keyBuffer) free(keyBuffer);
        if (valueBuffer) free(valueBuffer);
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
            
            // URL decode the key
            int decodedKeyLen = urlDecode(keyBuffer, buffer + keyStart, keyLength);
            
            // Write key length (4 bytes)
            *((int*)(result + resultPos)) = decodedKeyLen;
            resultPos += 4;
            
            // Copy decoded key
            memcpy(result + resultPos, keyBuffer, decodedKeyLen);
            resultPos += decodedKeyLen;
            
            valueStart = i + 1;
        } else if (c == '&') {
            // Found the end of a key-value pair
            if (valueStart == -1) {
                // Key without value, treat as empty value
                valueStart = i;
            }
            
            int valueLength = i - valueStart;
            
            // URL decode the value
            int decodedValueLen = urlDecode(valueBuffer, buffer + valueStart, valueLength);
            
            // Write value length (4 bytes)
            *((int*)(result + resultPos)) = decodedValueLen;
            resultPos += 4;
            
            // Copy decoded value
            memcpy(result + resultPos, valueBuffer, decodedValueLen);
            resultPos += decodedValueLen;
            
            // Reset for next pair
            keyStart = i + 1;
            valueStart = -1;
        }
    }
    
    // Clean up temporary buffers
    free(keyBuffer);
    free(valueBuffer);
    
    // Create a direct ByteBuffer with the result
    jobject resultBuffer = (*env)->NewDirectByteBuffer(env, result, resultPos);
    
    // Note: Memory will be managed by Java's ByteBuffer cleanup
    return resultBuffer;
}

/**
 * Parse multipart form data - robust implementation with improved error handling
 * 
 * This parser can handle:
 * - Proper Content-Disposition header parsing (name, filename)
 * - Content-Type headers for parts
 * - Efficient boundary scanning
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

