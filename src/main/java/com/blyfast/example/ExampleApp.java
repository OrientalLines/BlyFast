package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.CommonMiddleware;
import com.blyfast.example.handlers.ApiHandler;
import com.blyfast.example.handlers.HandlerRegistry;

/**
 * Example application demonstrating the BlyFast framework with modular handlers.
 */
public class ExampleApp {
    
    public static void main(String[] args) {
        // Create the application
        Blyfast app = new Blyfast();

        // Add global middleware
        app.use(CommonMiddleware.logger());
        app.use(CommonMiddleware.responseTime());
        app.use(CommonMiddleware.cors());
        app.use(CommonMiddleware.securityHeaders());

        // Register an instance-based handler manually
        HandlerRegistry.register(new ApiHandler());
        
        // Optionally discover handlers using ServiceLoader
        // HandlerRegistry.discoverHandlers();
        
        // Register all handlers with the application
        HandlerRegistry.registerAllHandlers(app);

        // Start the server
        app.port(8080).listen();
        System.out.println("Server started on http://localhost:8080");
    }

    /**
     * Example user class.
     */
    public static class User {
        private Integer id;
        private String name;
        private String email;

        public User() {
        }

        public User(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}