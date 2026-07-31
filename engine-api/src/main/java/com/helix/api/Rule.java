package com.helix.api;

import java.util.Map;

/**
 * Represents an uncompiled rule definition with metadata and input requirements.
 */
public interface Rule {

    /**
     * Gets the unique name of the rule.
     *
     * @return rule name
     */
    String getName();

    /**
     * Gets the version of the rule.
     *
     * @return rule version
     */
    String getVersion();

    /**
     * Gets a description of what the rule does.
     *
     * @return rule description
     */
    String getDescription();

    /**
     * Gets the raw expression string representing the rule logic.
     *
     * @return expression string
     */
    String getExpression();

    /**
     * Gets the expected input schema mapping parameter names to expected types.
     *
     * @return map of variable name to type class
     */
    Map<String, Class<?>> getInputSchema();
}
