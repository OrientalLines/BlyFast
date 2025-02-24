package com.blyfast.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for processing route paths and generating regex patterns.
 */
public class PathProcessor {
    private final String normalizedPath;
    private final Pattern pattern;
    private final List<String> paramNames;

    /**
     * Creates a new PathProcessor instance.
     *
     * @param normalizedPath the normalized path
     * @param pattern        the regex pattern for matching
     * @param paramNames     the list of parameter names
     */
    private PathProcessor(String normalizedPath, Pattern pattern, List<String> paramNames) {
        this.normalizedPath = normalizedPath;
        this.pattern = pattern;
        this.paramNames = paramNames;
    }

    /**
     * Gets the normalized path.
     *
     * @return the normalized path
     */
    public String getNormalizedPath() {
        return normalizedPath;
    }

    /**
     * Gets the regex pattern.
     *
     * @return the pattern
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * Gets the parameter names.
     *
     * @return the list of parameter names
     */
    public List<String> getParamNames() {
        return paramNames;
    }

    /**
     * Processes a route path and creates a PathProcessor instance.
     *
     * @param path the route path
     * @return a PathProcessor instance
     */
    public static PathProcessor process(String path) {
        // Normalize the path
        String normalizedPath = normalizePath(path);

        // Extract parameter names
        List<String> paramNames = new ArrayList<>();

        // Convert the path to a regex pattern
        StringBuilder patternBuilder = new StringBuilder("^");
        String[] segments = normalizedPath.split("/");

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }

            patternBuilder.append("/");

            // Check if this segment is a parameter
            if (segment.startsWith(":")) {
                // It's a parameter (e.g., :id)
                String paramName = segment.substring(1);
                paramNames.add(paramName);
                
                // Use a more precise regex for parameter matching
                // Match any character except slash, ensure non-empty
                patternBuilder.append("([^/]+)");
            } else if (segment.equals("*")) {
                // It's a wildcard
                patternBuilder.append(".*");
            } else {
                // It's a literal segment
                patternBuilder.append(Pattern.quote(segment));
            }
        }

        // Add trailing slash match if the original path ends with a slash
        if (path.endsWith("/")) {
            patternBuilder.append("/?");
        } else {
            patternBuilder.append("/?");
        }

        patternBuilder.append("$");

        Pattern pattern = Pattern.compile(patternBuilder.toString());
        return new PathProcessor(normalizedPath, pattern, paramNames);
    }

    /**
     * Normalizes a path by ensuring it starts with a slash and handling trailing
     * slashes.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // Ensure the path starts with a slash
        String normalized = path.startsWith("/") ? path : "/" + path;

        // Handle trailing slashes - preserve them in the normalized path
        // but handle them separately in the regex pattern
        // This is important for paths with parameters
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}