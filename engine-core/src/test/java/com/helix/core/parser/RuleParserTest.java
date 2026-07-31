package com.helix.core.parser;

import com.helix.api.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleParserTest {

    private RuleParser parser;

    @BeforeEach
    void setUp() {
        parser = new RuleParser();
    }

    @Test
    @DisplayName("Should parse valid simple JSON rule")
    void testParseValidSimpleRule() throws ParseException {
        String json = """
                {
                    "name": "SimpleDiscountRule",
                    "expression": "amount > 100",
                    "version": "1.2.0",
                    "description": "Applies discount for large purchases",
                    "category": "DISCOUNT"
                }
                """;

        Rule rule = parser.parse(json);
        assertNotNull(rule);
        assertEquals("SimpleDiscountRule", rule.getName());
        assertEquals("amount > 100", rule.getExpression());
        assertEquals("1.2.0", rule.getVersion());
        assertEquals("Applies discount for large purchases", rule.getDescription());
        assertTrue(rule instanceof RuleSchema);
        assertEquals("DISCOUNT", ((RuleSchema) rule).getCategory());
    }

    @Test
    @DisplayName("Should parse rule with input schema supporting all primitive & object types")
    void testParseInputSchemaTypes() throws ParseException {
        String json = """
                {
                    "name": "TypedRule",
                    "expression": "x > 10 && y < 20.5",
                    "inputSchema": {
                        "x": "int",
                        "y": "double",
                        "id": "long",
                        "flag": "boolean",
                        "name": "string",
                        "custom": "java.lang.Object"
                    }
                }
                """;

        Rule rule = parser.parse(json);
        assertNotNull(rule);
        assertEquals(Integer.class, rule.getInputSchema().get("x"));
        assertEquals(Double.class, rule.getInputSchema().get("y"));
        assertEquals(Long.class, rule.getInputSchema().get("id"));
        assertEquals(Boolean.class, rule.getInputSchema().get("flag"));
        assertEquals(String.class, rule.getInputSchema().get("name"));
        assertEquals(Object.class, rule.getInputSchema().get("custom"));
    }

    @Test
    @DisplayName("Should throw ParseException when JSON is null or blank")
    void testParseBlankContent() {
        assertThrows(ParseException.class, () -> parser.parse(""));
        assertThrows(ParseException.class, () -> parser.parse(null));
    }

    @Test
    @DisplayName("Should throw ParseException when mandatory fields are missing")
    void testMissingRequiredFields() {
        String jsonMissingExpression = """
                {
                    "name": "IncompleteRule"
                }
                """;
        assertThrows(ParseException.class, () -> parser.parse(jsonMissingExpression));

        String jsonMissingName = """
                {
                    "expression": "x + 1"
                }
                """;
        assertThrows(ParseException.class, () -> parser.parse(jsonMissingName));
    }

    @Test
    @DisplayName("Should throw ParseException when JSON is malformed")
    void testMalformedJson() {
        String malformedJson = "{ name: 'broken' expression: ";
        assertThrows(ParseException.class, () -> parser.parse(malformedJson));
    }

    @Test
    @DisplayName("Should throw ParseException for unknown type in schema")
    void testUnknownSchemaType() {
        String invalidTypeJson = """
                {
                    "name": "InvalidTypeRule",
                    "expression": "x > 0",
                    "inputSchema": {
                        "x": "com.invalid.NonExistentType"
                    }
                }
                """;
        assertThrows(ParseException.class, () -> parser.parse(invalidTypeJson));
    }
}
