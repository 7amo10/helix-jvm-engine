package com.helix.agent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration holder for Java Agent startup parameters and options.
 */
public class AgentConfiguration {

    private boolean enableRetransformation;
    private String targetPackages;
    private String logLevel;

    public AgentConfiguration() {
        this.enableRetransformation = true;
        this.targetPackages = "com.helix";
        this.logLevel = "INFO";
    }

    public static AgentConfiguration parse(String agentArgs) {
        AgentConfiguration config = new AgentConfiguration();
        if (agentArgs == null || agentArgs.isBlank()) {
            return config;
        }

        try {
            Properties props = new Properties();
            String formatted = agentArgs.replace(",", "\n").replace(";", "\n");
            try (InputStream in = new ByteArrayInputStream(formatted.getBytes(StandardCharsets.UTF_8))) {
                props.load(in);
            }

            if (props.containsKey("retransform")) {
                config.setEnableRetransformation(Boolean.parseBoolean(props.getProperty("retransform")));
            }
            if (props.containsKey("packages")) {
                config.setTargetPackages(props.getProperty("packages"));
            }
            if (props.containsKey("logLevel")) {
                config.setLogLevel(props.getProperty("logLevel"));
            }
            if (props.containsKey("configFile")) {
                Path configPath = Path.of(props.getProperty("configFile"));
                if (Files.exists(configPath)) {
                    Properties fileProps = new Properties();
                    try (InputStream fis = Files.newInputStream(configPath)) {
                        fileProps.load(fis);
                    }
                    if (fileProps.containsKey("retransform")) {
                        config.setEnableRetransformation(Boolean.parseBoolean(fileProps.getProperty("retransform")));
                    }
                    if (fileProps.containsKey("packages")) {
                        config.setTargetPackages(fileProps.getProperty("packages"));
                    }
                    if (fileProps.containsKey("logLevel")) {
                        config.setLogLevel(fileProps.getProperty("logLevel"));
                    }
                }
            }
        } catch (Exception e) {
            throw new AgentInitializationException("Failed to parse agent arguments: " + agentArgs, e);
        }
        return config;
    }

    public boolean isEnableRetransformation() {
        return enableRetransformation;
    }

    public void setEnableRetransformation(boolean enableRetransformation) {
        this.enableRetransformation = enableRetransformation;
    }

    public String getTargetPackages() {
        return targetPackages;
    }

    public void setTargetPackages(String targetPackages) {
        this.targetPackages = targetPackages;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
