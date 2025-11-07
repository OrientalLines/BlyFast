#ifndef BLYFASTNATIVE_H
#define BLYFASTNATIVE_H

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include <pthread.h>
#include <limits.h>
#include <stdint.h>

// Constants to replace magic numbers
#define MAX_HEADERS 1000
#define MAX_BOUNDARY_LEN 256
#define MAX_HEADER_NAME_LEN 1024
#define MAX_CONTENT_TYPE_LEN 256
#define MAX_PARTS 100
#define MAX_JSON_NUMBER_LEN 64
#define MAX_FORM_DATA_EXPANSION 3
#define INITIAL_ESCAPE_BUFFER_SIZE 64
#define CONTENT_TYPE_CHECK_LEN 200
#define BINARY_CHAR_THRESHOLD_PERCENT 10
#define TEXT_CHAR_THRESHOLD_PERCENT 90

// Helper macro for JNI exception checking
#define CHECK_JNI_EXCEPTION(env) \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            return NULL; \
        } \
    } while(0)

// Helper macro for JNI exception checking with cleanup
#define CHECK_JNI_EXCEPTION_CLEANUP(env, cleanup) \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            cleanup; \
            return NULL; \
        } \
    } while(0)

// Thread safety for header storage
extern pthread_mutex_t headers_mutex;

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

// Global storage for parsed headers (improved with thread safety)
extern ParsedHeaders* headers_storage[MAX_HEADERS];
extern volatile int next_header_id;

// Free list for header slot management
extern int free_header_slots[MAX_HEADERS];
extern int free_header_count;

// Utility function declarations
int urlDecode(char* dest, const char* src, int len);
int hexCharToInt(char c);
const char* strcasestr_portable(const char* haystack, const char* needle);
void freeHeadersList(HeaderValue* header);

// JSON parser function declarations
void initJsonParserCache(JNIEnv *env);
void skipWhitespace(const char **cursor, const char *end);
jobject parseJsonValue(JNIEnv *env, const char **cursor, const char *end);
jobject parseJsonObject(JNIEnv *env, const char **cursor, const char *end);
jobject parseJsonArray(JNIEnv *env, const char **cursor, const char *end);
jobject parseJsonString(JNIEnv *env, const char **cursor, const char *end);
jobject parseJsonNumber(JNIEnv *env, const char **cursor, const char *end);

// Form parser function declarations
jobject parseFormData(JNIEnv *env, char* buffer, jint length);
jobject parseMultipartForm(JNIEnv *env, char* buffer, jint length);

#endif // BLYFASTNATIVE_H

