package com.blyfast.example.handlers;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for API-related routes.
 * This class implements the RouteHandler interface.
 */
public class ApiHandler implements RouteHandler {

    /**
     * Registers API routes with the application.
     *
     * @param app the Blyfast application
     */
    @Override
    public void registerRoutes(Blyfast app) {
        // GET /api/status - API status route
        app.get("/api/status", this::getStatus);
        
        // GET /api/version - API version route
        app.get("/api/version", this::getVersion);
    }
    
    /**
     * Handler for the API status route.
     */
    public void getStatus(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "operational");
        response.put("time", System.currentTimeMillis());

        ctx.json(response);
    }
    
    /**
     * Handler for the API version route.
     */
    public void getVersion(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        response.put("version", "1.0.0");
        response.put("framework", "BlyFast");

        ctx.json(response);
    }
} 