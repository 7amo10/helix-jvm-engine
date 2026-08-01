package com.helix.core.parser.ast;

import com.helix.core.parser.ParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Expression tokenizer and recursive descent AST parser.
 * Converts raw string expressions into structured {@link ExpressionNode} trees.
 */
public class AstBuilder {

    private enum TokenType {
        NUMBER,
        STRING,
        BOOLEAN,
        IDENTIFIER,
        OPERATOR,
        LPAREN,
        RPAREN,
        DOT,
        COMMA,
        EOF
    }

    private static class Token {
        final TokenType type;
        final String text;
        final Object value;

        Token(TokenType type, String text, Object value) {
            this.type = type;
            this.text = text;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + "(" + text + ")";
        }
    }

    /**
     * Parses an expression string into an {@link ExpressionNode}.
     *
     * @param expression raw string expression (e.g., "x > 10 && y.equals(\"test\")")
     * @return root AST node
     * @throws ParseException if syntax or tokenization fails
     */
    public ExpressionNode buildAst(String expression) throws ParseException {
        if (expression == null || expression.isBlank()) {
            throw new ParseException("Expression string cannot be null or empty");
        }

        List<Token> tokens = tokenize(expression);
        Parser parser = new Parser(tokens);
        ExpressionNode ast = parser.parseExpression();
        if (parser.currentToken().type != TokenType.EOF) {
            throw new ParseException("Unexpected token after expression: " + parser.currentToken().text);
        }
        return ast;
    }

    private List<Token> tokenize(String input) throws ParseException {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = input.length();

        while (i < len) {
            char c = input.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses, dot, comma
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", null));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", null));
                i++;
                continue;
            }
            if (c == '.') {
                tokens.add(new Token(TokenType.DOT, ".", null));
                i++;
                continue;
            }
            if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, ",", null));
                i++;
                continue;
            }

            // String literals: "..." or '...'
            if (c == '"' || c == '\'') {
                char quote = c;
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < len) {
                    char ch = input.charAt(i);
                    if (ch == quote) {
                        closed = true;
                        i++;
                        break;
                    }
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        ch = input.charAt(i);
                    }
                    sb.append(ch);
                    i++;
                }
                if (!closed) {
                    throw new ParseException("Unterminated string literal in expression");
                }
                tokens.add(new Token(TokenType.STRING, sb.toString(), sb.toString()));
                continue;
            }

            // Two-char operators: ==, !=, >=, <=, &&, ||
            if (i + 1 < len) {
                String sub2 = input.substring(i, i + 2);
                if (sub2.equals("==") || sub2.equals("!=") || sub2.equals(">=") || sub2.equals("<=") || sub2.equals("&&") || sub2.equals("||")) {
                    tokens.add(new Token(TokenType.OPERATOR, sub2, null));
                    i += 2;
                    continue;
                }
            }

            // Single-char operators: +, -, *, /, >, <, !
            if ("+-*/><!".indexOf(c) != -1) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c), null));
                i++;
                continue;
            }

            // Numbers: integer or floating point
            if (Character.isDigit(c)) {
                int start = i;
                boolean isDouble = false;
                while (i < len) {
                    char ch = input.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && i + 1 < len && Character.isDigit(input.charAt(i + 1))) {
                        isDouble = true;
                        i++;
                    } else {
                        break;
                    }
                }
                String numStr = input.substring(start, i);
                Object val;
                if (isDouble) {
                    val = Double.parseDouble(numStr);
                } else {
                    val = Long.parseLong(numStr);
                }
                tokens.add(new Token(TokenType.NUMBER, numStr, val));
                continue;
            }

            // Identifiers or Booleans (true, false)
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(input.charAt(i))) {
                    i++;
                }
                String id = input.substring(start, i);
                if ("true".equalsIgnoreCase(id) || "false".equalsIgnoreCase(id)) {
                    tokens.add(new Token(TokenType.BOOLEAN, id, Boolean.parseBoolean(id)));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, id, id));
                }
                continue;
            }

            throw new ParseException("Unexpected character in expression: '" + c + "' at position " + i);
        }

        tokens.add(new Token(TokenType.EOF, "", null));
        return tokens;
    }

    private static class Parser {
        private final List<Token> tokens;
        private int pos = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Token currentToken() {
            return tokens.get(pos);
        }

        Token consume() {
            Token t = currentToken();
            if (t.type != TokenType.EOF) {
                pos++;
            }
            return t;
        }

        Token expect(TokenType type) throws ParseException {
            Token t = currentToken();
            if (t.type != type) {
                throw new ParseException("Expected token type " + type + " but got " + t);
            }
            return consume();
        }

        // Grammar levels:
        // parseExpression -> parseLogicalOr
        // parseLogicalOr -> parseLogicalAnd ( '||' parseLogicalAnd )*
        // parseLogicalAnd -> parseEquality ( '&&' parseEquality )*
        // parseEquality -> parseRelational ( ('==' | '!=') parseRelational )*
        // parseRelational -> parseAdditive ( ('>' | '<' | '>=' | '<=') parseAdditive )*
        // parseAdditive -> parseMultiplicative ( ('+' | '-') parseMultiplicative )*
        // parseMultiplicative -> parseUnary ( ('*' | '/') parseUnary )*
        // parseUnary -> ('!' | '-') parseUnary | parsePostfix
        // parsePostfix -> parsePrimary ( '.' methodName '(' args ')' )*

        ExpressionNode parseExpression() throws ParseException {
            return parseLogicalOr();
        }

        private ExpressionNode parseLogicalOr() throws ParseException {
            ExpressionNode left = parseLogicalAnd();
            while (currentToken().type == TokenType.OPERATOR && "||".equals(currentToken().text)) {
                consume();
                ExpressionNode right = parseLogicalAnd();
                left = new BinaryOpNode(BinaryOpNode.Operator.OR, left, right);
            }
            return left;
        }

        private ExpressionNode parseLogicalAnd() throws ParseException {
            ExpressionNode left = parseEquality();
            while (currentToken().type == TokenType.OPERATOR && "&&".equals(currentToken().text)) {
                consume();
                ExpressionNode right = parseEquality();
                left = new BinaryOpNode(BinaryOpNode.Operator.AND, left, right);
            }
            return left;
        }

        private ExpressionNode parseEquality() throws ParseException {
            ExpressionNode left = parseRelational();
            while (currentToken().type == TokenType.OPERATOR && ("==".equals(currentToken().text) || "!=".equals(currentToken().text))) {
                String op = consume().text;
                ExpressionNode right = parseRelational();
                BinaryOpNode.Operator bOp = "==".equals(op) ? BinaryOpNode.Operator.EQUAL : BinaryOpNode.Operator.NOT_EQUAL;
                left = new BinaryOpNode(bOp, left, right);
            }
            return left;
        }

        private ExpressionNode parseRelational() throws ParseException {
            ExpressionNode left = parseAdditive();
            while (currentToken().type == TokenType.OPERATOR && (">".equals(currentToken().text) || "<".equals(currentToken().text) || ">=".equals(currentToken().text) || "<=".equals(currentToken().text))) {
                String opStr = consume().text;
                ExpressionNode right = parseAdditive();
                BinaryOpNode.Operator bOp = BinaryOpNode.Operator.fromSymbol(opStr);
                left = new BinaryOpNode(bOp, left, right);
            }
            return left;
        }

        private ExpressionNode parseAdditive() throws ParseException {
            ExpressionNode left = parseMultiplicative();
            while (currentToken().type == TokenType.OPERATOR && ("+".equals(currentToken().text) || "-".equals(currentToken().text))) {
                String op = consume().text;
                ExpressionNode right = parseMultiplicative();
                BinaryOpNode.Operator bOp = "+".equals(op) ? BinaryOpNode.Operator.ADD : BinaryOpNode.Operator.SUBTRACT;
                left = new BinaryOpNode(bOp, left, right);
            }
            return left;
        }

        private ExpressionNode parseMultiplicative() throws ParseException {
            ExpressionNode left = parseUnary();
            while (currentToken().type == TokenType.OPERATOR && ("*".equals(currentToken().text) || "/".equals(currentToken().text))) {
                String op = consume().text;
                ExpressionNode right = parseUnary();
                BinaryOpNode.Operator bOp = "*".equals(op) ? BinaryOpNode.Operator.MULTIPLY : BinaryOpNode.Operator.DIVIDE;
                left = new BinaryOpNode(bOp, left, right);
            }
            return left;
        }

        private ExpressionNode parseUnary() throws ParseException {
            if (currentToken().type == TokenType.OPERATOR && ("!".equals(currentToken().text) || "-".equals(currentToken().text))) {
                String opStr = consume().text;
                ExpressionNode operand = parseUnary();
                UnaryOpNode.Operator uOp = "!".equals(opStr) ? UnaryOpNode.Operator.NOT : UnaryOpNode.Operator.NEGATE;
                return new UnaryOpNode(uOp, operand);
            }
            return parsePostfix();
        }

        private ExpressionNode parsePostfix() throws ParseException {
            ExpressionNode expr = parsePrimary();
            while (currentToken().type == TokenType.DOT) {
                consume(); // '.'
                Token methodToken = expect(TokenType.IDENTIFIER);
                expect(TokenType.LPAREN); // '('
                List<ExpressionNode> args = new ArrayList<>();
                if (currentToken().type != TokenType.RPAREN) {
                    args.add(parseExpression());
                    while (currentToken().type == TokenType.COMMA) {
                        consume(); // ','
                        args.add(parseExpression());
                    }
                }
                expect(TokenType.RPAREN); // ')'
                expr = new MethodCallNode(expr, methodToken.text, args);
            }
            return expr;
        }

        private ExpressionNode parsePrimary() throws ParseException {
            Token t = currentToken();
            switch (t.type) {
                case NUMBER:
                    consume();
                    return new LiteralNode(t.value);
                case STRING:
                    consume();
                    return new LiteralNode(t.value, String.class);
                case BOOLEAN:
                    consume();
                    return new LiteralNode(t.value, Boolean.class);
                case IDENTIFIER:
                    consume();
                    return new VariableNode(t.text);
                case LPAREN:
                    consume();
                    ExpressionNode inner = parseExpression();
                    expect(TokenType.RPAREN);
                    return inner;
                default:
                    throw new ParseException("Unexpected token in expression: " + t);
            }
        }
    }
}
