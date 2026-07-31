package com.helix.core.parser;

import com.helix.api.Rule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for loading JSON rules from file system, classpath resources, or input streams.
 */
public class JsonRuleLoader {

    private final RuleParser parser;

    public JsonRuleLoader() {
        this.parser = new RuleParser();
    }

    public JsonRuleLoader(RuleParser parser) {
        this.parser = parser;
    }

    public Rule loadFromFile(Path filePath) throws IOException, ParseException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return parser.parse(content);
    }

    public Rule loadFromResource(String resourcePath) throws IOException, ParseException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parser.parse(content);
        }
    }
}
