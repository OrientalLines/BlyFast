package com.blyfast.example.handlers;

import com.blyfast.core.Blyfast;
import com.blyfast.example.ExampleApp.User;
import com.blyfast.http.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Handler for user-related endpoints. */
public class UserHandler {
  // Simple in-memory store for the example
  private static final Map<Integer, User> users = new ConcurrentHashMap<>();
  private static final AtomicInteger nextId = new AtomicInteger(1);

  static {
    // Add some sample data
    addUser(new User("John Doe", "john@example.com"));
    addUser(new User("Jane Smith", "jane@example.com"));
    addUser(new User("Bob Johnson", "bob@example.com"));
  }

  /**
   * Registers all user-related routes with the application.
   *
   * @param app the Blyfast application
   */
  public static void registerRoutes(Blyfast app) {
    // GET /users - List all users
    app.get("/users", UserHandler::getAllUsers);

    // GET /users/:id - Get a specific user
    app.get("/users/:id", UserHandler::getUserById);

    // POST /users - Create a new user
    app.post("/users", UserHandler::createUser);

    // PUT /users/:id - Update a user
    app.put("/users/:id", UserHandler::updateUser);

    // DELETE /users/:id - Delete a user
    app.delete("/users/:id", UserHandler::deleteUser);
  }

  /** Handler for listing all users. */
  public static void getAllUsers(Context ctx) {
    ctx.json(users.values());
  }

  /** Handler for getting a specific user by ID. */
  public static void getUserById(Context ctx) {
    String idParam = ctx.param("id");
    try {
      int id = Integer.parseInt(idParam);
      User user = users.get(id);

      if (user != null) {
        ctx.json(user);
      } else {
        ctx.status(404).json(error("User not found with ID: " + id));
      }
    } catch (NumberFormatException e) {
      ctx.status(400).json(error("Invalid ID format: " + idParam));
    }
  }

  /** Handler for creating a new user. */
  public static void createUser(Context ctx) throws Exception {
    try {
      User newUser = ctx.parseBody(User.class);

      if (newUser.getName() == null || newUser.getName().trim().isEmpty()) {
        ctx.status(400).json(error("Name is required"));
        return;
      }

      if (newUser.getEmail() == null || newUser.getEmail().trim().isEmpty()) {
        ctx.status(400).json(error("Email is required"));
        return;
      }

      // Add the user
      addUser(newUser);

      // Return the created user
      ctx.status(201).json(newUser);
    } catch (Exception e) {
      ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
    }
  }

  /** Handler for updating a user. */
  public static void updateUser(Context ctx) throws Exception {
    String idParam = ctx.param("id");
    try {
      int id = Integer.parseInt(idParam);

      if (!users.containsKey(id)) {
        ctx.status(404).json(error("User not found with ID: " + id));
        return;
      }

      User updatedUser = ctx.parseBody(User.class);
      updatedUser.setId(id); // Ensure the ID matches the URL

      users.put(id, updatedUser);
      ctx.json(updatedUser);
    } catch (NumberFormatException e) {
      ctx.status(400).json(error("Invalid ID format: " + idParam));
    } catch (Exception e) {
      ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
    }
  }

  /** Handler for deleting a user. */
  public static void deleteUser(Context ctx) {
    String idParam = ctx.param("id");
    try {
      int id = Integer.parseInt(idParam);

      if (users.containsKey(id)) {
        users.remove(id);
        ctx.status(204).send("");
      } else {
        ctx.status(404).json(error("User not found with ID: " + id));
      }
    } catch (NumberFormatException e) {
      ctx.status(400).json(error("Invalid ID format: " + idParam));
    }
  }

  /**
   * Adds a user to the in-memory store.
   *
   * @param user the user to add
   */
  private static void addUser(User user) {
    int id = nextId.getAndIncrement();
    user.setId(id);
    users.put(id, user);
  }

  /**
   * Creates an error response.
   *
   * @param message the error message
   * @return the error response
   */
  private static Map<String, Object> error(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", true);
    response.put("message", message);
    return response;
  }

  /**
   * Gets all users.
   *
   * @return the users map
   */
  public static Map<Integer, User> getUsers() {
    return users;
  }
}
