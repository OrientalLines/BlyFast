package com.blyfast.plugin;

import com.blyfast.core.Blyfast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for plugins that provides common functionality.
 */
public abstract class AbstractPlugin implements Plugin {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String name;
    protected final String version;
    
    /**
     * Creates a new plugin with the specified name and version.
     * 
     * @param name the plugin name
     * @param version the plugin version
     */
    protected AbstractPlugin(String name, String version) {
        this.name = name;
        this.version = version;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getVersion() {
        return version;
    }
    
    @Override
    public void onStart(Blyfast app) {
        logger.debug("Plugin {} v{} starting", name, version);
    }
    
    @Override
    public void onStop(Blyfast app) {
        logger.debug("Plugin {} v{} stopping", name, version);
    }
} 