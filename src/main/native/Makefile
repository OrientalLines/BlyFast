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
SOURCES = blyfastnative.c
RESOURCES_DIR = ../../resources/native

all: $(TARGET)

$(TARGET): $(SOURCES)
	mkdir -p $(RESOURCES_DIR)
	$(CC) $(CFLAGS) $(PLATFORM_CFLAGS) $(INCLUDES) -o $(TARGET) $(SOURCES)
	cp $(TARGET) $(RESOURCES_DIR)/

header:
	$(JAVAC) -d ../../classes ../../java/com/blyfast/nativeopt/NativeOptimizer.java
	$(JAVAH) -classpath ../../classes -o blyfastnative.h com.blyfast.nativeopt.NativeOptimizer

clean:
	rm -f $(TARGET) $(RESOURCES_DIR)/$(TARGET)

.PHONY: all clean header 