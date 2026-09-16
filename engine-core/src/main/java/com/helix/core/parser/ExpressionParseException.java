package com.helix.core.parser;

/**
 * Exception thrown when expression syntax parsing fails, providing line, column, and visual pointers.
 */
public class ExpressionParseException extends ParseException {

    private final int line;
    private final int column;
    private final String expression;

    public ExpressionParseException(String message, String expression, int line, int column) {
        super(formatErrorMessage(message, expression, line, column));
        this.line = line;
        this.column = column;
        this.expression = expression;
    }

    public ExpressionParseException(String message, String expression, int line, int column, Throwable cause) {
        super(formatErrorMessage(message, expression, line, column), cause);
        this.line = line;
        this.column = column;
        this.expression = expression;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getExpression() {
        return expression;
    }

    private static String formatErrorMessage(String message, String expression, int line, int column) {
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax error at line ").append(line).append(", column ").append(column).append(": ").append(message);
        if (expression != null && !expression.isBlank()) {
            String[] lines = expression.split("\r?\n", -1);
            int lineIdx = Math.max(0, line - 1);
            if (lineIdx < lines.length) {
                String errorLine = lines[lineIdx];
                sb.append("\n  ").append(errorLine).append("\n  ");
                int pointerPos = Math.max(0, column - 1);
                sb.append(" ".repeat(Math.min(pointerPos, errorLine.length()))).append("^");
            }
        }
        return sb.toString();
    }
}
