package com.helix.core.parser;

import com.helix.api.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class RuleParserTest {

    private RuleParser parser;

    @BeforeEach
    void setUp() {
        parser = new RuleParser();
    }

    @Test
    @DisplayName("Should parse valid JSON rule with required fields")
    void testParseValidRule() throws ParseException {
        String json = """
                {
                    "name": "DiscountRule",
                    "expression": "amount > 100"
                }
                """;

        Rule rule = parser.parse(json);
        assertNotNull(rule);
        assertEquals("DiscountRule", rule.getName());
        assertEquals("amount > 100", rule.getExpression());
        assertEquals("1.0.0", rule.getVersion());
    }

    @Test
    @DisplayName("Should parse all supported input types")
    void testParseSupportedInputTypes() throws ParseException {
        String json = """
                {
                    "name": "TypeRule",
                    "expression": "x > 0",
                    "inputSchema": {
                        "a": "int",
                        "b": "integer",
                        "c": "double",
                        "d": "long",
                        "e": "boolean",
                        "f": "string",
                        "g": "object"
                    }
                }
                """;

        Rule rule = parser.parse(json);
        assertEquals(Integer.class, rule.getInputSchema().get("a"));
        assertEquals(Integer.class, rule.getInputSchema().get("b"));
        assertEquals(Double.class, rule.getInputSchema().get("c"));
        assertEquals(Long.class, rule.getInputSchema().get("d"));
        assertEquals(Boolean.class, rule.getInputSchema().get("e"));
        assertEquals(String.class, rule.getInputSchema().get("f"));
        assertEquals(Object.class, rule.getInputSchema().get("g"));
    }

    @Test
    @DisplayName("Should parse custom FQCN input types")
    void testParseCustomClassType() throws ParseException {
        String json = """
                {
                    "name": "CustomTypeRule",
                    "expression": "str.length() > 0",
                    "inputSchema": {
                        "str": "java.lang.String"
                    }
                }
                """;

        Rule rule = parser.parse(json);
        assertEquals(String.class, rule.getInputSchema().get("str"));
    }

    @Test
    @DisplayName("Should throw ParseException when missing required name field")
    void testMissingNameField() {
        String json = """
                {
                    "expression": "x > 0"
                }
                """;
        ParseException ex = assertThrows(ParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    @DisplayName("Should throw ParseException when missing required expression field")
    void testMissingExpressionField() {
        String json = """
                {
                    "name": "NoExprRule"
                }
                """;
        ParseException ex = assertThrows(ParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("expression"));
    }

    @Test
    @DisplayName("Should throw ParseException on malformed JSON syntax")
    void testMalformedJson() {
        String json = "{ name: 'invalid json without quotes' ";
        assertThrows(ParseException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("Should throw ParseException for unknown input schema type class")
    void testUnknownInputTypeClass() {
        String json = """
                {
                    "name": "InvalidTypeRule",
                    "expression": "x > 0",
                    "inputSchema": {
                        "x": "com.nonexistent.Class"
                    }
                }
                """;
        assertThrows(ParseException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("Should parse full metadata fields (version, description, category)")
    void testFullMetadata() throws ParseException {
        String json = """
                {
                    "name": "MetadataRule",
                    "version": "2.1.0",
                    "description": "Rule with metadata",
                    "category": "FINANCE",
                    "expression": "a + b"
                }
                """;
        RuleSchema rule = (RuleSchema) parser.parse(json);
        assertEquals("2.1.0", rule.getVersion());
        assertEquals("Rule with metadata", rule.getDescription());
        assertEquals("FINANCE", rule.getCategory());
    }

    @Test
    @DisplayName("Should throw ParseException for empty JSON string")
    void testEmptyJsonString() {
        assertThrows(ParseException.class, () -> parser.parse(""));
    }

    @Test
    @DisplayName("Should throw ParseException for null JSON string")
    void testNullJsonString() {
        assertThrows(ParseException.class, () -> parser.parse((String) null));
    }

    @Test
    @DisplayName("Should load JSON rule via JsonRuleLoader stream helper")
    void testJsonRuleLoaderStream() throws Exception {
        String json = """
                {
                    "name": "StreamRule",
                    "expression": "true"
                }
                """;
        ByteArrayInputStream is = new ByteArrayInputStream(json.getBytes());
        JsonRuleLoader loader = new JsonRuleLoader(parser);
        Rule rule = loader.loadFromStream(is);
        assertNotNull(rule);
        assertEquals("StreamRule", rule.getName());
    }
}
