package com.blyfast.middleware;

import com.blyfast.http.Context;

/**
 * Interface for middleware components.
 * Middleware can process requests before they reach route handlers
 * and can be used for cross-cutting concerns like authentication,
 * logging, CORS, etc.
 */
@FunctionalInterface
public interface Middleware {
    /**
     * Processes the request context.
     * 
     * @param ctx the context containing the request and response
     * @return true to continue processing, false to stop the middleware chain
     * @throws Exception if an error occurs during processing
     */
    boolean handle(Context ctx) throws Exception;
} 