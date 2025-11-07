#include "blyfastnative.h"

// Thread safety for header storage
pthread_mutex_t headers_mutex = PTHREAD_MUTEX_INITIALIZER;

// Global storage for parsed headers
ParsedHeaders* headers_storage[MAX_HEADERS];
volatile int next_header_id = 1;

// Free list for efficient slot management
int free_header_slots[MAX_HEADERS];
int free_header_count = 0;

// Helper function to free a linked list of headers
void freeHeadersList(HeaderValue* header) {
    while (header != NULL) {
        HeaderValue* next = header->next;
        if (header->name) free(header->name);
        if (header->value) free(header->value);
        free(header);
        header = next;
    }
}

// Helper function to convert hex character to integer
int hexCharToInt(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

// Portable case-insensitive string search (replacement for strcasestr)
const char* strcasestr_portable(const char* haystack, const char* needle) {
    if (!haystack || !needle || !*needle) {
        return NULL;
    }
    
    const char* h = haystack;
    const char* n = needle;
    
    while (*h) {
        const char* h_start = h;
        const char* n_start = n;
        
        while (*h && *n && (tolower((unsigned char)*h) == tolower((unsigned char)*n))) {
            h++;
            n++;
        }
        
        if (!*n) {
            return h_start; // Found match
        }
        
        h = h_start + 1;
        n = n_start;
    }
    
    return NULL;
}

// URL decode function - decodes %XX sequences with bounds checking
int urlDecode(char* dest, const char* src, int len) {
    if (dest == NULL || src == NULL || len < 0) {
        return 0;
    }
    
    int i, j = 0;
    // Maximum expansion is 1:1 (no expansion), but we need space for null terminator
    int maxDestLen = len + 1;
    
    for (i = 0; i < len && j < maxDestLen - 1; i++) {
        if (src[i] == '%' && i + 2 < len) {
            int high = hexCharToInt(src[i + 1]);
            int low = hexCharToInt(src[i + 2]);
            if (high >= 0 && low >= 0) {
                if (j < maxDestLen - 1) {
                    dest[j++] = (char)((high << 4) | low);
                    i += 2;
                } else {
                    break; // Buffer full
                }
            } else {
                if (j < maxDestLen - 1) {
                    dest[j++] = src[i];
                } else {
                    break;
                }
            }
        } else if (src[i] == '+') {
            if (j < maxDestLen - 1) {
                dest[j++] = ' ';
            } else {
                break;
            }
        } else {
            if (j < maxDestLen - 1) {
                dest[j++] = src[i];
            } else {
                break;
            }
        }
    }
    dest[j] = '\0';
    return j;
}

