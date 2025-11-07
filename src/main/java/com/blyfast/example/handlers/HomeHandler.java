package com.blyfast.example.handlers;

import com.blyfast.core.Blyfast;
import com.blyfast.http.Context;
import java.util.HashMap;
import java.util.Map;

/** Handler for the home route and general info. */
public class HomeHandler {

  /**
   * Registers home-related routes with the application.
   *
   * @param app the Blyfast application
   */
  public static void registerRoutes(Blyfast app) {
    // GET / - Home route
    app.get("/", HomeHandler::home);
  }

  /** Handler for the home route. */
  public static void home(Context ctx) {
    Map<String, Object> response = new HashMap<>();
    response.put("message", "Welcome to the BlyFast API");
    response.put("version", "1.0.0");

    ctx.json(response);
  }
}
