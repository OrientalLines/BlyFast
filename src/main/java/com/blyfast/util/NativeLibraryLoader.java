package com.blyfast.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class to handle native library loading across different platforms.
 */
public class NativeLibraryLoader {
    private static final Logger logger = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static final String TEMP_DIR_PREFIX = "blyfast-native-";
    
    /**
     * Loads a native library from the classpath resources.
     * 
     * @param libraryName the name of the library without platform-specific prefixes/suffixes
     * @return true if loaded successfully, false otherwise
     */
    public static boolean loadLibrary(String libraryName) {
        try {
            // Get the platform-specific library name
            String libNameWithExt = getPlatformSpecificLibraryName(libraryName);
            
            // Try to load from java.library.path first
            try {
                System.loadLibrary(libraryName);
                logger.info("Loaded native library '{}' from java.library.path", libraryName);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.debug("Could not load '{}' from java.library.path: {}", libraryName, e.getMessage());
                // Continue to load from resources
            }
            
            // Try to load directly from target/classes/native for test environment
            try {
                String targetPath = "target/classes/native/" + libNameWithExt;
                File targetFile = new File(targetPath);
                if (targetFile.exists()) {
                    System.load(targetFile.getAbsolutePath());
                    logger.info("Loaded native library '{}' from target directory", libraryName);
                    return true;
                }
            } catch (UnsatisfiedLinkError e) {
                logger.debug("Could not load '{}' from target directory: {}", libraryName, e.getMessage());
                // Continue to load from resources
            }
            
            // Load from resources
            String resourcePath = "/native/" + libNameWithExt;
            try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.error("Could not find native library '{}' in classpath at: {}", libraryName, resourcePath);
                    
                    // For debugging - list available resource paths
                    logger.info("Class loader: {}", NativeLibraryLoader.class.getClassLoader());
                    logger.info("Java library path: {}", System.getProperty("java.library.path"));
                    logger.info("Working directory: {}", System.getProperty("user.dir"));
                    
                    return false;
                }
                
                // Create a temporary directory
                Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
                tempDir.toFile().deleteOnExit();
                
                // Copy the library to the temporary directory
                Path tempLibPath = tempDir.resolve(libNameWithExt);
                Files.copy(is, tempLibPath, StandardCopyOption.REPLACE_EXISTING);
                
                // Set executable permission on Unix-like systems
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    tempLibPath.toFile().setExecutable(true, false);
                }
                
                // Load the library from the temporary directory
                System.load(tempLibPath.toAbsolutePath().toString());
                logger.info("Loaded native library '{}' from resources", libraryName);
                return true;
            }
        } catch (IOException | UnsatisfiedLinkError e) {
            logger.error("Failed to load native library '{}': {}", libraryName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the platform-specific library name with prefix and suffix.
     * 
     * @param libraryName the base library name
     * @return the platform-specific library name
     */
    private static String getPlatformSpecificLibraryName(String libraryName) {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Add prefix and suffix based on OS
        if (os.contains("win")) {
            return libraryName + ".dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + libraryName + ".dylib";
        } else {
            // Assume Linux/Unix
            return "lib" + libraryName + ".so";
        }
    }
} 