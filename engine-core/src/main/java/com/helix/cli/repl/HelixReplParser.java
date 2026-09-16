package com.helix.cli.repl;

import org.jline.reader.EOFError;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;

/**
 * JLine 3 Parser for the Helix REPL supporting multiline expression editing.
 * Detects unclosed parentheses, unterminated strings, and trailing operators,
 * seamlessly prompting for continuation lines.
 */
public class HelixReplParser implements Parser {

    private final DefaultParser defaultParser;

    public HelixReplParser() {
        this.defaultParser = new DefaultParser();
        this.defaultParser.setEscapeChars(new char[0]); // avoid escaping issues with rule expressions
    }

    @Override
    public ParsedLine parse(String line, int cursor, ParseContext context) throws EOFError {
        if (context == ParseContext.ACCEPT_LINE) {
            String trimmed = line.trim();

            // REPL commands (starting with ':') are single line
            if (trimmed.startsWith(":")) {
                return defaultParser.parse(line, cursor, context);
            }

            // Check unclosed quotes
            boolean inDoubleQuote = false;
            boolean inSingleQuote = false;
            int parenDepth = 0;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\\') {
                    i++; // skip escaped char
                    continue;
                }
                if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                } else if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (!inDoubleQuote && !inSingleQuote) {
                    if (c == '(') {
                        parenDepth++;
                    } else if (c == ')') {
                        parenDepth--;
                    }
                }
            }

            if (inDoubleQuote || inSingleQuote) {
                throw new EOFError(-1, -1, "Unterminated string literal", "quote");
            }
            if (parenDepth > 0) {
                throw new EOFError(-1, -1, "Unclosed parenthesis", "paren");
            }

            // Check trailing operator
            if (trimmed.endsWith("&&") || trimmed.endsWith("||") || trimmed.endsWith("+")
                    || trimmed.endsWith("-") || trimmed.endsWith("*") || trimmed.endsWith("/")
                    || trimmed.endsWith("%") || trimmed.endsWith("==") || trimmed.endsWith("!=")
                    || trimmed.endsWith(">") || trimmed.endsWith("<") || trimmed.endsWith(">=")
                    || trimmed.endsWith("<=")) {
                throw new EOFError(-1, -1, "Trailing operator", "operator");
            }
        }

        return defaultParser.parse(line, cursor, context);
    }
}
