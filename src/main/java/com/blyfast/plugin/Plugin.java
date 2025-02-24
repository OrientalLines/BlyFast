package com.blyfast.plugin;

import com.blyfast.core.Blyfast;

/**
 * Interface for plugins that can be registered with the Blyfast framework.
 * Plugins can extend the framework's functionality in a modular way.
 */
public interface Plugin {

    /**
     * Registers the plugin with the Blyfast application.
     * This method is called when the plugin is added to the application.
     * 
     * @param app the Blyfast application instance
     */
    void register(Blyfast app);

    /**
     * Gets the name of the plugin.
     * 
     * @return the plugin name
     */
    String getName();

    /**
     * Gets the version of the plugin.
     * 
     * @return the plugin version
     */
    String getVersion();

    /**
     * Called when the application is starting.
     * 
     * @param app the Blyfast application instance
     */
    default void onStart(Blyfast app) {
        // Default implementation does nothing
    }

    /**
     * Called when the application is stopping.
     * 
     * @param app the Blyfast application instance
     */
    default void onStop(Blyfast app) {
        // Default implementation does nothing
    }
}