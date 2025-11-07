package com.blyfast.example.handlers;

import com.blyfast.core.Blyfast;

/**
 * Interface for route handlers. Classes implementing this interface can be automatically registered
 * with the application.
 */
public interface RouteHandler {

  /**
   * Registers routes with the application.
   *
   * @param app the Blyfast application
   */
  void registerRoutes(Blyfast app);
}
