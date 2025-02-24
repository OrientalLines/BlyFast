package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.CommonMiddleware;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Example application demonstrating the FileSystem middleware.
 */
public class FileSystemExample {

    public static void main(String[] args) throws Exception {
        // Create a temporary directory for our static files
        Path staticDir = createExampleStaticFiles();
        
        // Create the application
        Blyfast app = new Blyfast();
        
        // Add common middleware
        app.use(CommonMiddleware.logger());
        app.use(CommonMiddleware.responseTime());
        
        // Add FileSystem middleware to serve files from our static directory
        // This will serve files under the "/static" URL prefix
        app.use(CommonMiddleware.fileSystem(staticDir.toString(), "/static"));
        
        // Add another FileSystem middleware to serve files directly from the root URL
        // This has lower priority than explicit routes
        app.use(CommonMiddleware.fileSystem(staticDir.toString()));
        
        // Define a custom API route
        app.get("/api/status", ctx -> {
            Map<String, Object> status = new HashMap<>();
            status.put("status", "running");
            status.put("staticDir", staticDir.toString());
            ctx.json(status);
        });
        
        // Start the server on port 8080
        app.port(8080).listen();
        
        System.out.println("FileSystem example started on http://localhost:8080");
        System.out.println("Try accessing these URLs:");
        System.out.println("- http://localhost:8080/style.css");
        System.out.println("- http://localhost:8080/static/style.css");
        System.out.println("- http://localhost:8080/static/index.html");
        System.out.println("- http://localhost:8080/api/status");
    }
    
    /**
     * Creates example static files for the demonstration.
     * 
     * @return the path to the temporary directory
     * @throws IOException if an I/O error occurs
     */
    private static Path createExampleStaticFiles() throws IOException {
        // Create a temporary directory
        Path tempDir = Files.createTempDirectory("blyfast-static");
        
        // Create an HTML file
        Path htmlFile = tempDir.resolve("index.html");
        String htmlContent = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "    <title>Blyfast FileSystem Example</title>\n"
                + "    <link rel=\"stylesheet\" href=\"style.css\">\n"
                + "</head>\n"
                + "<body>\n"
                + "    <h1>Welcome to Blyfast!</h1>\n"
                + "    <p>This page is served by the FileSystem middleware.</p>\n"
                + "    <p>Check out the <a href=\"/api/status\">API status</a>.</p>\n"
                + "</body>\n"
                + "</html>";
        Files.write(htmlFile, htmlContent.getBytes());
        
        // Create a CSS file
        Path cssFile = tempDir.resolve("style.css");
        String cssContent = "body {\n"
                + "    font-family: Arial, sans-serif;\n"
                + "    max-width: 800px;\n"
                + "    margin: 0 auto;\n"
                + "    padding: 20px;\n"
                + "    line-height: 1.6;\n"
                + "}\n"
                + "h1 {\n"
                + "    color: #0066cc;\n"
                + "}\n"
                + "a {\n"
                + "    color: #cc6600;\n"
                + "    text-decoration: none;\n"
                + "}\n"
                + "a:hover {\n"
                + "    text-decoration: underline;\n"
                + "}";
        Files.write(cssFile, cssContent.getBytes());
        
        // Add a cleanup hook to delete the temporary directory on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        
        return tempDir;
    }
    
    /**
     * Recursively deletes a directory.
     * 
     * @param directory the directory to delete
     * @throws IOException if an I/O error occurs
     */
    private static void deleteDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file);
                    }
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory);
        }
    }
} 