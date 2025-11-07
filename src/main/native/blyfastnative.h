#ifndef BLYFASTNATIVE_H
#define BLYFASTNATIVE_H

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include <pthread.h>

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
#define MAX_HEADERS 1000
extern ParsedHeaders* headers_storage[MAX_HEADERS];
extern volatile int next_header_id;

// Utility function declarations
int urlDecode(char* dest, const char* src, int len);
int hexCharToInt(char c);
const char* strcasestr_portable(const char* haystack, const char* needle);
void freeHeadersList(HeaderValue* header);

// JSON parser function declarations
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

