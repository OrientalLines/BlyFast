package com.blyfast.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Utility class for generating a stylish ASCII banner and system information 
 * for application startup.
 */
public class Banner {
    
    private static final String[] BLYFAST_BANNER = {
        "  ____  _  __   _______ _    ____ _____ ",
        " | __ )| | \\ \\ / /  ___/ \\  / ___|_   _|",
        " |  _ \\| |  \\ V /| |_ / _ \\ \\___ \\ | |  ",
        " | |_) | |___| | |  _/ ___ \\ ___) || |  ",
        " |____/|_____|_| |_|/_/   \\_\\____/ |_|  ",
        "                                         "
    };
    
    /**
     * Generates a stylish banner with system information
     * 
     * @param host host the server is running on
     * @param port port the server is listening on
     * @return the formatted banner string
     */
    public static String generate(String host, int port) {
        StringBuilder sb = new StringBuilder();
        String lineSeparator = System.lineSeparator();
        
        // Add the banner
        for (String line : BLYFAST_BANNER) {
            sb.append(ConsoleColors.BLUE_BOLD).append(line).append(ConsoleColors.RESET).append(lineSeparator);
        }
        
        sb.append(lineSeparator);
        
        // Get system properties
        Properties props = System.getProperties();
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        
        // Add system information
        sb.append(ConsoleColors.CYAN_BOLD).append(" :: Blyfast Framework ::").append(ConsoleColors.RESET);
        sb.append("       ").append(ConsoleColors.WHITE_BOLD);
        sb.append("(v1.0.0)").append(ConsoleColors.RESET).append(lineSeparator).append(lineSeparator);
        
        // Add server info
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sb.append(ConsoleColors.PURPLE).append(" ⚡ Started at: ").append(ConsoleColors.WHITE)
            .append(timestamp).append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add server address
        sb.append(ConsoleColors.PURPLE).append(" 🔗 Server URL: ").append(ConsoleColors.WHITE)
            .append("http://").append(host.equals("0.0.0.0") ? "localhost" : host).append(":")
            .append(port).append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add Java info
        sb.append(ConsoleColors.PURPLE).append(" ☕ Java version: ").append(ConsoleColors.WHITE)
            .append(props.getProperty("java.version"))
            .append(" (").append(props.getProperty("java.vendor")).append(")")
            .append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add OS info
        sb.append(ConsoleColors.PURPLE).append(" 💻 OS: ").append(ConsoleColors.WHITE)
            .append(props.getProperty("os.name")).append(" ")
            .append(props.getProperty("os.version")).append(" (")
            .append(props.getProperty("os.arch")).append(")")
            .append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add JVM info
        sb.append(ConsoleColors.PURPLE).append(" 🧠 JVM: ").append(ConsoleColors.WHITE)
            .append(runtimeMx.getVmName()).append(" (")
            .append(runtimeMx.getVmVendor()).append(", ")
            .append(runtimeMx.getVmVersion()).append(")")
            .append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add processor info
        sb.append(ConsoleColors.PURPLE).append(" 🔄 Processors: ").append(ConsoleColors.WHITE)
            .append(Runtime.getRuntime().availableProcessors())
            .append(ConsoleColors.RESET).append(lineSeparator);
        
        // Add host name
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            sb.append(ConsoleColors.PURPLE).append(" 🖥️ Host: ").append(ConsoleColors.WHITE)
                .append(hostname)
                .append(ConsoleColors.RESET).append(lineSeparator);
        } catch (UnknownHostException e) {
            // Ignore hostname if not available
        }
        
        // Add a nice line separator
        sb.append(lineSeparator).append(ConsoleColors.GREEN_BOLD)
          .append(" 🚀 Blyfast is ready to handle requests!")
          .append(ConsoleColors.RESET).append(lineSeparator);
        
        return sb.toString();
    }
    
    /**
     * Displays the banner directly to the console.
     * This method first clears any previous console content to ensure
     * the banner is displayed cleanly.
     * 
     * @param host host the server is running on
     * @param port port the server is listening on
     */
    public static void display(String host, int port) {
        // Clear console before displaying banner (attempts to work on both Windows and Unix-like)
        try {
            final String os = System.getProperty("os.name");
            
            if (os.contains("Windows")) {
                // For Windows
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                // For Unix-like systems
                System.out.print("\033[H\033[2J"); 
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore exceptions if we can't clear the console
        }
        
        // Print the banner
        System.out.println(generate(host, port));
    }
} 