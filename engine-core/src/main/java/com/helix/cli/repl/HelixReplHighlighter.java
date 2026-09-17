package com.helix.cli.repl;

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Real-time syntax highlighter for the Helix REPL.
 * Applies color styling to commands, operators, string literals, numbers, and identifiers.
 */
public class HelixReplHighlighter implements Highlighter {

    private static final Set<String> COMMANDS = Set.of(
            ":help", ":eval", ":set", ":context", ":vars", ":ast", ":bytecode", ":perf",
            ":load", ":clear", ":exit", ":quit", ":break", ":step", ":inspect", ":continue", ":c", ":debug"
    );

    private static final Set<String> BUILTINS = Set.of(
            "ml", "len", "length", "max", "min", "abs", "now"
    );

    private static final Set<String> KEYWORDS = Set.of(
            "true", "false", "null"
    );

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return new AttributedString("");
        }

        AttributedStringBuilder asb = new AttributedStringBuilder();
        int len = buffer.length();
        int i = 0;

        while (i < len) {
            char c = buffer.charAt(i);

            // Comments
            if (c == '/' && i + 1 < len && buffer.charAt(i + 1) == '/') {
                asb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint());
                asb.append(buffer.substring(i));
                break;
            }

            // REPL Command at start
            if (c == ':' && (i == 0 || buffer.substring(0, i).trim().isEmpty())) {
                int start = i;
                while (i < len && !Character.isWhitespace(buffer.charAt(i))) {
                    i++;
                }
                String cmd = buffer.substring(start, i);
                if (COMMANDS.contains(cmd.toLowerCase())) {
                    asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN));
                } else {
                    asb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                }
                asb.append(cmd);
                continue;
            }

            // String literals
            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i;
                i++;
                while (i < len && buffer.charAt(i) != quote) {
                    if (buffer.charAt(i) == '\\' && i + 1 < len) {
                        i++;
                    }
                    i++;
                }
                if (i < len) i++; // consume closing quote
                asb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                asb.append(buffer.substring(start, i));
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(buffer.charAt(i)) || buffer.charAt(i) == '.'
                        || buffer.charAt(i) == 'L' || buffer.charAt(i) == 'l'
                        || buffer.charAt(i) == 'e' || buffer.charAt(i) == 'E')) {
                    i++;
                }
                asb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
                asb.append(buffer.substring(start, i));
                continue;
            }

            // Operators
            if (c == '&' || c == '|' || c == '!' || c == '=' || c == '<' || c == '>'
                    || c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW));
                asb.append(c);
                i++;
                continue;
            }

            // Identifiers / keywords / functions
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(buffer.charAt(i)) || buffer.charAt(i) == '_' || buffer.charAt(i) == '$')) {
                    i++;
                }
                String word = buffer.substring(start, i);
                if (KEYWORDS.contains(word)) {
                    asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.MAGENTA));
                } else if (BUILTINS.contains(word.toLowerCase())) {
                    asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW));
                } else {
                    asb.style(AttributedStyle.DEFAULT);
                }
                asb.append(word);
                continue;
            }

            // Punctuation and whitespace
            asb.style(AttributedStyle.DEFAULT);
            asb.append(c);
            i++;
        }

        return asb.toAttributedString();
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
        // No-op
    }

    @Override
    public void setErrorIndex(int errorIndex) {
        // No-op
    }
}
