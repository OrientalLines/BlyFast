package com.blyfast.nativeopt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.blyfast.util.NativeLibraryLoader;

/**
 * Provides native optimizations for the BlyFast framework.
 * This class leverages JNI and Unsafe operations to maximize performance.
 */
public class NativeOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(NativeOptimizer.class);
    private static final AtomicBoolean NATIVE_LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean UNSAFE_AVAILABLE = new AtomicBoolean(false);
    private static Unsafe UNSAFE;
    
    // Define OS-specific native library file names
    private static final String LIBRARY_NAME = "blyfastnative";
    private static final String NATIVE_LIB_LINUX = "lib" + LIBRARY_NAME + ".so";
    private static final String NATIVE_LIB_MAC = "lib" + LIBRARY_NAME + ".dylib";
    private static final String NATIVE_LIB_WINDOWS = LIBRARY_NAME + ".dll";
    
    // Cache for performance critical operations
    private static final ThreadLocal<ByteBuffer> DIRECT_BUFFER_CACHE = ThreadLocal.withInitial(() -> 
            ByteBuffer.allocateDirect(64 * 1024)); // 64KB buffer

    static {
        // Initialize Unsafe for low-level memory operations
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
            UNSAFE_AVAILABLE.set(true);
            logger.debug("Initialized Unsafe for low-level memory operations");
        } catch (Exception e) {
            logger.warn("Failed to initialize Unsafe. Some optimizations will be disabled.", e);
        }
        
        // Try to load native library
        try {
            loadNativeLibrary();
        } catch (Exception e) {
            logger.warn("Failed to load native optimization library. Will use Java fallback methods.", e);
        }
        
        // Initialize JVM optimization flags if running on HotSpot
        initJvmOptimizations();
    }
    
    /**
     * Attempts to load the native library.
     */
    private static void loadNativeLibrary() {
        logger.info("Initializing native optimizations for BlyFast");
        
        if (NATIVE_LOADED.get()) {
            logger.debug("Native library already loaded");
            return;
        }
        
        // Use the NativeLibraryLoader utility to load the library
        boolean loaded = NativeLibraryLoader.loadLibrary(LIBRARY_NAME);
        
        if (loaded) {
            NATIVE_LOADED.set(true);
            // Initialize platform-specific JVM optimizations
            initJvmOptimizations();
            logger.info("Native optimizations initialized successfully");
            
            // Verify native methods can be called - this helps catch issues early
            try {
                // Simple test call to verify linking
                String testResult = nativeEscapeJson("test");
                logger.debug("Native method test successful: {}", testResult != null);
            } catch (UnsatisfiedLinkError e) {
                // If this fails, reset the loaded flag
                NATIVE_LOADED.set(false);
                logger.error("Failed to call native method after loading library: {}", e.getMessage());
            }
        } else {
            logger.warn("Native library could not be loaded. Performance will be reduced.");
        }
    }
    
    /**
     * Forces reloading of the native library.
     * This can be useful in testing scenarios.
     */
    public static void forceReload() {
        NATIVE_LOADED.set(false);
        try {
            loadNativeLibrary();
        } catch (Exception e) {
            logger.error("Failed to reload native library: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sets JVM optimization flags for HotSpot JVM if running on Oracle/OpenJDK.
     */
    private static void initJvmOptimizations() {
        try {
            // Enable various JIT compiler hints
            // Set max inline cache size for better polymorphic call performance
            System.setProperty("java.lang.Integer.IntegerCache.high", "20000");
            
            // Force classloading to improve startup performance
            // Critical classes for request handling
            Class.forName("com.blyfast.http.Request");
            Class.forName("com.blyfast.http.Response");
            Class.forName("com.blyfast.http.Context");
            
            logger.debug("JVM optimizations initialized");
        } catch (Exception e) {
            logger.warn("Failed to initialize some JVM optimizations", e);
        }
    }

    // Native method declarations - these would be implemented in C
    
    /**
     * Fast native JSON string escaping.
     * 
     * @param input the string to escape
     * @return the escaped string
     */
    public static native String nativeEscapeJson(String input);
    
    /**
     * Fast native JSON parsing.
     * 
     * @param input the JSON string to parse
     * @return ByteBuffer containing the parsed structure
     */
    public static native ByteBuffer nativeParseJson(String input);
    
    /**
     * Fast native HTTP header parsing.
     * 
     * @param headerBytes the header bytes
     * @param length the length of the data
     * @return object ID for the parsed headers
     */
    public static native long nativeParseHttpHeaders(ByteBuffer headerBytes, int length);
    
    /**
     * Retrieves a header value by name from previously parsed headers.
     * 
     * @param headersId the ID of the parsed headers
     * @param headerName the name of the header to retrieve
     * @return the header value, or null if not found
     */
    public static native String nativeGetHeader(long headersId, String headerName);
    
    /**
     * Releases resources associated with previously parsed headers.
     * 
     * @param headersId the ID of the parsed headers to free
     */
    public static native void nativeFreeHeaders(long headersId);
    
    /**
     * Optimized memory copy - copies from source to destination buffer.
     * 
     * @param srcBuffer the source buffer
     * @param srcOffset the source offset
     * @param dstBuffer the destination buffer
     * @param dstOffset the destination offset
     * @param length the number of bytes to copy
     */
    public static native void nativeMemoryCopy(ByteBuffer srcBuffer, int srcOffset, 
                                               ByteBuffer dstBuffer, int dstOffset, int length);
    
    /**
     * Analyzes HTTP body based on content type.
     * 
     * @param bodyBuffer the body data
     * @param length the length of the data
     * @param contentType the Content-Type header value
     * @return an integer code representing the body type (0=unknown, 1=JSON, 2=form, 3=multipart, 4=text, 5=binary)
     */
    public static native int nativeAnalyzeHttpBody(ByteBuffer bodyBuffer, int length, String contentType);
    
    /**
     * Optimized string to bytes conversion with direct memory.
     * Falls back to Java implementation if native library isn't available.
     * 
     * @param input the string to convert
     * @return a direct ByteBuffer containing the UTF-8 bytes
     */
    public static ByteBuffer stringToDirectBytes(String input) {
        if (NATIVE_LOADED.get()) {
            return nativeStringToBytes(input);
        } else {
            // Fallback to Java implementation
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer buffer = DIRECT_BUFFER_CACHE.get();
            buffer.clear();
            
            if (bytes.length <= buffer.capacity()) {
                buffer.put(bytes);
                buffer.flip();
                return buffer;
            } else {
                // For large strings, allocate a new direct buffer
                ByteBuffer largeBuffer = ByteBuffer.allocateDirect(bytes.length);
                largeBuffer.put(bytes);
                largeBuffer.flip();
                return largeBuffer;
            }
        }
    }
    
    /**
     * Native implementation of string to direct bytes conversion.
     */
    private static native ByteBuffer nativeStringToBytes(String input);
    
    /**
     * Optimized memory operations using Unsafe.
     * Copies source array to destination without bounds checking.
     * 
     * @param src source array
     * @param srcPos starting position in the source array
     * @param dest destination array
     * @param destPos starting position in the destination array
     * @param length the number of elements to be copied
     */
    public static void unsafeCopyMemory(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (UNSAFE_AVAILABLE.get()) {
            long srcOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + srcPos;
            long destOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + destPos;
            UNSAFE.copyMemory(src, srcOffset, dest, destOffset, length);
        } else {
            // Fallback to System.arraycopy
            System.arraycopy(src, srcPos, dest, destPos, length);
        }
    }
    
    /**
     * Optimized array allocation using Unsafe.
     * 
     * @param size the size of the array to allocate
     * @return a new byte array
     */
    public static byte[] unsafeAllocateByteArray(int size) {
        if (UNSAFE_AVAILABLE.get()) {
            try {
                // Allocate array without zeroing memory in newer JDKs if available
                // Try reflection for JDK9+ method first
                try {
                    Method allocateUninitializedArray = Unsafe.class.getMethod(
                        "allocateUninitializedArray", Class.class, int.class);
                    return (byte[]) allocateUninitializedArray.invoke(UNSAFE, byte.class, size);
                } catch (NoSuchMethodException e) {
                    // Fall back to allocateInstance for older JDKs (less efficient)
                    byte[] array = (byte[]) UNSAFE.allocateInstance(byte[].class);
                    Field lengthField = byte[].class.getDeclaredField("length");
                    // Use unsafe to set the length field
                    long lengthOffset = UNSAFE.objectFieldOffset(lengthField);
                    UNSAFE.putInt(array, lengthOffset, size);
                    return array;
                }
            } catch (Exception e) {
                // Fallback to standard allocation
                return new byte[size];
            }
        } else {
            return new byte[size];
        }
    }
    
    /**
     * Returns whether native optimizations are available.
     * 
     * @return true if native library is loaded
     */
    public static boolean isNativeOptimizationAvailable() {
        return NATIVE_LOADED.get();
    }
    
    /**
     * Returns whether Unsafe optimizations are available.
     * 
     * @return true if Unsafe is available
     */
    public static boolean isUnsafeAvailable() {
        return UNSAFE_AVAILABLE.get();
    }

    /**
     * Java fallback implementation for fast JSON string escaping.
     */
    private static String javaEscapeJson(String input) {
        if (input == null) return null;
        
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
    
    /**
     * Fast JSON string escaping with native fallback.
     * 
     * @param input the string to escape
     * @return the escaped string
     */
    public static String escapeJson(String input) {
        if (NATIVE_LOADED.get()) {
            try {
                logger.debug("Using native JSON escaping for input: {}", input);
                return nativeEscapeJson(input);
            } catch (UnsatisfiedLinkError e) {
                logger.warn("Native method nativeEscapeJson failed, falling back to Java implementation. Error: {}", e.getMessage());
                logger.debug("Stack trace:", e);
                
                // Print JVM info for debugging
                logger.debug("Java library path: {}", System.getProperty("java.library.path"));
                logger.debug("Native library loaded flag: {}", NATIVE_LOADED.get());
                
                NATIVE_LOADED.set(false); // Reset flag since native methods aren't working
                return javaEscapeJson(input);
            }
        }
        logger.debug("Using Java JSON escaping for input: {}", input);
        return javaEscapeJson(input);
    }
} 