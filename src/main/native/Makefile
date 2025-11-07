# Makefile for compiling BlyFast native library

# Variables
JAVA_HOME ?= $(shell java -XshowSettings:properties -version 2>&1 | grep java.home | awk '{print $$3}')
JAVAC = $(JAVA_HOME)/bin/javac
JAVA = $(JAVA_HOME)/bin/java
JAVAH = $(JAVA_HOME)/bin/javah

CC = gcc
CFLAGS = -fPIC -shared -O3 -march=native -mtune=native -Wall
INCLUDES = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -I$(JAVA_HOME)/include/darwin -I$(JAVA_HOME)/include/win32

# Platform-specific settings
UNAME = $(shell uname)
ifeq ($(UNAME), Linux)
	LIB_PREFIX = lib
	LIB_SUFFIX = .so
	PLATFORM_CFLAGS = 
endif
ifeq ($(UNAME), Darwin)
	LIB_PREFIX = lib
	LIB_SUFFIX = .dylib
	PLATFORM_CFLAGS = -mmacosx-version-min=10.9
endif
ifeq ($(OS), Windows_NT)
	LIB_PREFIX = 
	LIB_SUFFIX = .dll
	PLATFORM_CFLAGS = 
endif

# Targets
TARGET = $(LIB_PREFIX)blyfastnative$(LIB_SUFFIX)
SOURCES = blyfastnative.c utils.c json_parser.c http_headers.c form_parser.c
RESOURCES_DIR = ../../resources/native

all: $(TARGET)

$(TARGET): $(SOURCES)
	@mkdir -p $(RESOURCES_DIR)
	$(CC) $(CFLAGS) $(PLATFORM_CFLAGS) $(INCLUDES) -o $(TARGET) $(SOURCES)
	@mkdir -p ../../../target/classes/native
	@mkdir -p ../../../target/test-classes/native
	cp $(TARGET) $(RESOURCES_DIR)/
	cp $(TARGET) ../../../target/classes/native/
	cp $(TARGET) ../../../target/test-classes/native/
	@echo "Native library $(TARGET) compiled and installed to all directories"

header:
	@mkdir -p ../../classes
	$(JAVAC) -d ../../classes ../../java/com/blyfast/nativeopt/NativeOptimizer.java
	$(JAVAH) -classpath ../../classes -o blyfastnative.h com.blyfast.nativeopt.NativeOptimizer

clean:
	@echo "Cleaning native libraries"
	@rm -f $(TARGET) $(RESOURCES_DIR)/$(TARGET) ../../../target/classes/native/$(TARGET) ../../../target/test-classes/native/$(TARGET)
	@echo "Clean completed"

.PHONY: all clean header 