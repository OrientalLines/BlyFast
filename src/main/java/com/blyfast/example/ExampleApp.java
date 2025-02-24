package com.blyfast.example;

import com.blyfast.core.Blyfast;
import com.blyfast.middleware.CommonMiddleware;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example application demonstrating the BlyFast framework.
 */
public class ExampleApp {
    // Simple in-memory store for the example
    private static final Map<Integer, User> users = new ConcurrentHashMap<>();
    private static final AtomicInteger nextId = new AtomicInteger(1);

    static {
        // Add some sample data
        addUser(new User("John Doe", "john@example.com"));
        addUser(new User("Jane Smith", "jane@example.com"));
        addUser(new User("Bob Johnson", "bob@example.com"));
    }

    public static void main(String[] args) {
        // Create the application
        Blyfast app = new Blyfast();

        // Add global middleware
        app.use(CommonMiddleware.logger());
        app.use(CommonMiddleware.responseTime());
        app.use(CommonMiddleware.cors());
        app.use(CommonMiddleware.securityHeaders());

        // Define routes

        // GET /
        app.get("/", ctx -> {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Welcome to the BlyFast API");
            response.put("version", "1.0.0");

            ctx.json(response);
        });

        // GET /users - List all users
        app.get("/users", ctx -> {
            ctx.json(users.values());
        });

        // GET /users/:id - Get a specific user
        app.get("/users/:id", ctx -> {
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
        });

        // POST /users - Create a new user
        app.post("/users", ctx -> {
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
        });

        // PUT /users/:id - Update a user
        app.put("/users/:id", ctx -> {
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
        });

        // DELETE /users/:id - Delete a user
        app.delete("/users/:id", ctx -> {
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
        });

        // Start the server
        app.port(8080).listen();
        System.out.println("Server started on http://localhost:8080");
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