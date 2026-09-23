package com.helix.core.parser;

import com.helix.core.bytecode.ConstantFolder;
import com.helix.core.bytecode.DeadCodeEliminator;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.FieldAccessNode;
import com.helix.core.parser.ast.FunctionCallNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance, lightweight recursive-descent parser for human-readable rule expressions.
 * Compiles expressions directly into Helix AST nodes with microsecond latency (&lt;20 µs).
 *
 * <p>Grammar precedence (lowest to highest):
 * <ol>
 *   <li>Logical OR: {@code ||}</li>
 *   <li>Logical AND: {@code &&}</li>
 *   <li>Equality: {@code ==}, {@code !=}</li>
 *   <li>Relational / Comparison: {@code <}, {@code <=}, {@code >}, {@code >=}</li>
 *   <li>Additive: {@code +}, {@code -}</li>
 *   <li>Multiplicative: {@code *}, {@code /}, {@code %}</li>
 *   <li>Unary prefix: {@code !}, {@code -}, {@code +}</li>
 *   <li>Postfix: field access {@code .field}, method call {@code .method(args)}</li>
 *   <li>Primary: literals, function calls {@code fn(args)}, variables, grouped {@code (expr)}</li>
 * </ol>
 */
public class ExpressionRuleParser {

    private final ConstantFolder constantFolder = new ConstantFolder();
    private final DeadCodeEliminator deadCodeEliminator = new DeadCodeEliminator();

    /**
     * Parses a raw expression string into an AST {@link ExpressionNode}.
     *
     * @param expression expression text
     * @return parsed AST root node
     * @throws ExpressionParseException if expression has syntax errors
     */
    public ExpressionNode parse(String expression) throws ExpressionParseException {
        if (expression == null || expression.isBlank()) {
            throw new ExpressionParseException("Expression cannot be null or blank", expression, 1, 1);
        }
        ParserState state = new ParserState(expression);
        ExpressionNode root = state.parseExpression();
        return root;
    }

    /**
     * Parses and optimizes an expression using constant folding and dead code elimination.
     *
     * @param expression expression text
     * @return optimized AST root node
     * @throws ExpressionParseException if expression has syntax errors
     */
    public ExpressionNode parseAndFold(String expression) throws ExpressionParseException {
        ExpressionNode ast = parse(expression);
        return deadCodeEliminator.eliminate(constantFolder.fold(ast));
    }

    /**
     * Parses an expression, optimizes it, and validates types against the given schema.
     *
     * @param expression expression text
     * @param schema variable schema
     * @return optimized and type-checked AST ExpressionNode
     * @throws ExpressionParseException if syntax is invalid
     * @throws TypeMismatchException if type validation fails
     */
    public ExpressionNode parseAndValidate(String expression, Map<String, Class<?>> schema)
            throws ExpressionParseException, TypeMismatchException {
        ExpressionNode folded = parseAndFold(expression);
        if (schema != null && !schema.isEmpty()) {
            TypeContext typeContext = new TypeContext(schema);
            TypeChecker checker = new TypeChecker(typeContext);
            checker.check(folded);
        }
        return folded;
    }

    /**
     * Parses a named rule with the specified metadata and schema, applying folding and type checking.
     *
     * @param name rule name
     * @param version rule version
     * @param description rule description
     * @param category rule category
     * @param expression expression text
     * @param schema variable schema
     * @return compiled {@link RuleNode}
     */
    public RuleNode parseRule(String name, String version, String description, String category,
                              String expression, Map<String, Class<?>> schema)
            throws ExpressionParseException, TypeMismatchException {
        if (name == null || name.isBlank()) {
            throw new ExpressionParseException("Rule name cannot be null or blank", expression, 1, 1);
        }
        ExpressionNode validatedAst = parseAndValidate(expression, schema);
        return new RuleNode(name, version, description, category, expression, schema, validatedAst);
    }

    /**
     * Parses a named rule with the specified schema, applying folding and type checking.
     */
    public RuleNode parseRule(String name, String expression, Map<String, Class<?>> schema)
            throws ExpressionParseException, TypeMismatchException {
        return parseRule(name, "1.0.0", "", "DEFAULT", expression, schema);
    }

    public RuleNode parseRule(String name, String expression) throws ExpressionParseException {
        try {
            return parseRule(name, expression, Collections.emptyMap());
        } catch (TypeMismatchException e) {
            throw new ExpressionParseException("Type checking error: " + e.getMessage(), expression, 1, 1, e);
        }
    }

    /**
     * Validates expression syntax and type compatibility against a schema.
     */
    public void validate(String expression, Map<String, Class<?>> schema)
            throws ExpressionParseException, TypeMismatchException {
        parseAndValidate(expression, schema);
    }

    /**
     * Validates expression syntax.
     */
    public void validate(String expression) throws ExpressionParseException {
        parse(expression);
    }

    // =========================================================================
    // Lexer & Recursive-Descent Parser Implementation
    // =========================================================================

    private enum TokenType {
        EOF,
        NUMBER,
        STRING,
        BOOLEAN,
        NULL,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        AND,
        OR,
        NOT,
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        LPAREN,
        RPAREN,
        DOT,
        COMMA
    }

    private static class Token {
        final TokenType type;
        final Object value;
        final int line;
        final int column;

        Token(TokenType type, Object value, int line, int column) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return type + (value != null ? "(" + value + ")" : "") + "@" + line + ":" + column;
        }
    }

    private static class ParserState {
        private final String expression;
        private final char[] text;
        private final int length;
        private int pos = 0;
        private int line = 1;
        private int col = 1;

        private Token currentToken;

        ParserState(String expression) throws ExpressionParseException {
            this.expression = expression;
            this.text = expression.toCharArray();
            this.length = text.length;
            this.currentToken = nextToken();
        }

        ExpressionNode parseExpression() throws ExpressionParseException {
            ExpressionNode expr = parseLogicalOr();
            if (currentToken.type != TokenType.EOF) {
                throw new ExpressionParseException(
                        "Unexpected token '" + currentToken.value + "' after expression",
                        expression, currentToken.line, currentToken.column);
            }
            return expr;
        }

        private ExpressionNode parseLogicalOr() throws ExpressionParseException {
            ExpressionNode left = parseLogicalAnd();
            while (match(TokenType.OR)) {
                ExpressionNode right = parseLogicalAnd();
                left = new BinaryExpressionNode(BinaryOpNode.Operator.OR, left, right);
            }
            return left;
        }

        private ExpressionNode parseLogicalAnd() throws ExpressionParseException {
            ExpressionNode left = parseEquality();
            while (match(TokenType.AND)) {
                ExpressionNode right = parseEquality();
                left = new BinaryExpressionNode(BinaryOpNode.Operator.AND, left, right);
            }
            return left;
        }

        private ExpressionNode parseEquality() throws ExpressionParseException {
            ExpressionNode left = parseRelational();
            while (check(TokenType.EQ) || check(TokenType.NE)) {
                Token opTok = advance();
                BinaryOpNode.Operator op = (opTok.type == TokenType.EQ)
                        ? BinaryOpNode.Operator.EQUAL
                        : BinaryOpNode.Operator.NOT_EQUAL;
                ExpressionNode right = parseRelational();
                left = new ComparisonNode(op, left, right);
            }
            return left;
        }

        private ExpressionNode parseRelational() throws ExpressionParseException {
            ExpressionNode left = parseAdditive();
            while (check(TokenType.GT) || check(TokenType.GE) || check(TokenType.LT) || check(TokenType.LE)) {
                Token opTok = advance();
                BinaryOpNode.Operator op = switch (opTok.type) {
                    case GT -> BinaryOpNode.Operator.GREATER_THAN;
                    case GE -> BinaryOpNode.Operator.GREATER_EQUAL;
                    case LT -> BinaryOpNode.Operator.LESS_THAN;
                    case LE -> BinaryOpNode.Operator.LESS_EQUAL;
                    default -> throw new IllegalStateException();
                };
                ExpressionNode right = parseAdditive();
                left = new ComparisonNode(op, left, right);
            }
            return left;
        }

        private ExpressionNode parseAdditive() throws ExpressionParseException {
            ExpressionNode left = parseMultiplicative();
            while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
                Token opTok = advance();
                BinaryOpNode.Operator op = (opTok.type == TokenType.PLUS)
                        ? BinaryOpNode.Operator.ADD
                        : BinaryOpNode.Operator.SUBTRACT;
                ExpressionNode right = parseMultiplicative();
                left = new BinaryExpressionNode(op, left, right);
            }
            return left;
        }

        private ExpressionNode parseMultiplicative() throws ExpressionParseException {
            ExpressionNode left = parseUnary();
            while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
                Token opTok = advance();
                BinaryOpNode.Operator op = switch (opTok.type) {
                    case STAR -> BinaryOpNode.Operator.MULTIPLY;
                    case SLASH -> BinaryOpNode.Operator.DIVIDE;
                    case PERCENT -> BinaryOpNode.Operator.MODULO;
                    default -> throw new IllegalStateException();
                };
                ExpressionNode right = parseUnary();
                left = new BinaryExpressionNode(op, left, right);
            }
            return left;
        }

        private ExpressionNode parseUnary() throws ExpressionParseException {
            if (match(TokenType.NOT)) {
                ExpressionNode operand = parseUnary();
                return new UnaryOpNode(UnaryOpNode.Operator.NOT, operand);
            }
            if (match(TokenType.MINUS)) {
                ExpressionNode operand = parseUnary();
                if (operand instanceof LiteralNode lit && lit.getValue() instanceof Number num) {
                    if (num instanceof Double d) return new LiteralNode(-d);
                    if (num instanceof Long l) return new LiteralNode(-l);
                    if (num instanceof Integer i) return new LiteralNode(-i);
                }
                return new UnaryOpNode(UnaryOpNode.Operator.NEGATE, operand);
            }
            if (match(TokenType.PLUS)) {
                return parseUnary();
            }
            return parsePostfix();
        }

        private ExpressionNode parsePostfix() throws ExpressionParseException {
            ExpressionNode expr = parsePrimary();
            while (match(TokenType.DOT)) {
                Token memberTok = consume(TokenType.IDENTIFIER, "Expected property or method name after '.'");
                String memberName = (String) memberTok.value;
                if (match(TokenType.LPAREN)) {
                    List<ExpressionNode> args = parseArguments();
                    expr = new MethodCallNode(expr, memberName, args);
                } else {
                    expr = new FieldAccessNode(expr, memberName);
                }
            }
            return expr;
        }

        private ExpressionNode parsePrimary() throws ExpressionParseException {
            if (check(TokenType.NUMBER)) {
                Token t = advance();
                return new LiteralNode(t.value, t.value.getClass());
            }
            if (check(TokenType.STRING)) {
                Token t = advance();
                return new LiteralNode(t.value, String.class);
            }
            if (check(TokenType.BOOLEAN)) {
                Token t = advance();
                return new LiteralNode(t.value, Boolean.class);
            }
            if (check(TokenType.NULL)) {
                advance();
                return new LiteralNode(null, Object.class);
            }
            if (check(TokenType.IDENTIFIER)) {
                Token t = advance();
                String name = (String) t.value;
                if (match(TokenType.LPAREN)) {
                    List<ExpressionNode> args = parseArguments();
                    if ("ML".equalsIgnoreCase(name)) {
                        return parseOnnxInferenceNode(t, args);
                    }
                    return new FunctionCallNode(name, args);
                }
                return new VariableNode(name);
            }
            if (match(TokenType.LPAREN)) {
                ExpressionNode inner = parseLogicalOr();
                consume(TokenType.RPAREN, "Expected ')' after expression");
                return inner;
            }
            if (check(TokenType.EOF)) {
                throw new ExpressionParseException(
                        "Unexpected end of expression", expression, currentToken.line, currentToken.column);
            }
            throw new ExpressionParseException(
                    "Unexpected token: '" + currentToken.value + "'", expression, currentToken.line, currentToken.column);
        }

        private List<ExpressionNode> parseArguments() throws ExpressionParseException {
            List<ExpressionNode> args = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    args.add(parseLogicalOr());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Expected ')' after argument list");
            return args;
        }

        private ExpressionNode parseOnnxInferenceNode(Token mlToken, List<ExpressionNode> args) throws ExpressionParseException {
            if (args.isEmpty()) {
                throw new ExpressionParseException(
                        "ML() requires at least a model name argument",
                        expression, mlToken.line, mlToken.column
                );
            }
            if (args.size() > 3) {
                throw new ExpressionParseException(
                        "ML() accepts at most 3 arguments (modelName, outputTensorName, outputIndex), got " + args.size(),
                        expression, mlToken.line, mlToken.column
                );
            }

            ExpressionNode arg0 = args.get(0);
            String modelName;
            if (arg0 instanceof VariableNode vn) {
                modelName = vn.getName();
            } else if (arg0 instanceof LiteralNode ln && ln.getValue() instanceof String s) {
                modelName = s;
            } else {
                throw new ExpressionParseException(
                        "ML() model name must be an identifier or string literal, got: " + arg0,
                        expression, mlToken.line, mlToken.column
                );
            }

            String outputTensorName = "probabilities";
            int outputIndex = 1;

            if (args.size() >= 2) {
                ExpressionNode arg1 = args.get(1);
                if (arg1 instanceof LiteralNode ln && ln.getValue() instanceof String s) {
                    outputTensorName = s;
                } else if (arg1 instanceof VariableNode vn) {
                    outputTensorName = vn.getName();
                } else {
                    throw new ExpressionParseException(
                            "ML() outputTensorName must be a string literal or identifier, got: " + arg1,
                            expression, mlToken.line, mlToken.column
                    );
                }
            }

            if (args.size() == 3) {
                ExpressionNode arg2 = args.get(2);
                if (arg2 instanceof LiteralNode ln && ln.getValue() instanceof Number n) {
                    outputIndex = n.intValue();
                } else {
                    throw new ExpressionParseException(
                            "ML() outputIndex must be an integer literal, got: " + arg2,
                            expression, mlToken.line, mlToken.column
                    );
                }
            }

            return new com.helix.core.parser.ast.OnnxInferenceNode(modelName, outputTensorName, outputIndex);
        }

        private boolean check(TokenType type) {
            return currentToken.type == type;
        }

        private boolean match(TokenType type) throws ExpressionParseException {
            if (check(type)) {
                advance();
                return true;
            }
            return false;
        }

        private Token advance() throws ExpressionParseException {
            Token prev = currentToken;
            currentToken = nextToken();
            return prev;
        }

        private Token consume(TokenType expected, String errorMsg) throws ExpressionParseException {
            if (check(expected)) {
                return advance();
            }
            throw new ExpressionParseException(
                    errorMsg + ", found '" + (currentToken.value != null ? currentToken.value : currentToken.type) + "'",
                    expression, currentToken.line, currentToken.column);
        }

        // --- Fast Lexer ---

        private Token nextToken() throws ExpressionParseException {
            skipWhitespaceAndComments();

            if (pos >= length) {
                return new Token(TokenType.EOF, null, line, col);
            }

            int startLine = line;
            int startCol = col;
            char c = text[pos];

            // Punctuation & single-char operators
            switch (c) {
                case '(' -> { advanceChar(); return new Token(TokenType.LPAREN, "(", startLine, startCol); }
                case ')' -> { advanceChar(); return new Token(TokenType.RPAREN, ")", startLine, startCol); }
                case ',' -> { advanceChar(); return new Token(TokenType.COMMA, ",", startLine, startCol); }
                case '+' -> { advanceChar(); return new Token(TokenType.PLUS, "+", startLine, startCol); }
                case '-' -> { advanceChar(); return new Token(TokenType.MINUS, "-", startLine, startCol); }
                case '*' -> { advanceChar(); return new Token(TokenType.STAR, "*", startLine, startCol); }
                case '/' -> { advanceChar(); return new Token(TokenType.SLASH, "/", startLine, startCol); }
                case '%' -> { advanceChar(); return new Token(TokenType.PERCENT, "%", startLine, startCol); }
                case '.' -> {
                    if (pos + 1 < length && isDigit(text[pos + 1])) {
                        return readNumber(startLine, startCol);
                    }
                    advanceChar();
                    return new Token(TokenType.DOT, ".", startLine, startCol);
                }
                case '&' -> {
                    if (pos + 1 < length && text[pos + 1] == '&') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.AND, "&&", startLine, startCol);
                    }
                    throw new ExpressionParseException(
                            "Unexpected character '&', did you mean '&&'?", expression, startLine, startCol);
                }
                case '|' -> {
                    if (pos + 1 < length && text[pos + 1] == '|') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.OR, "||", startLine, startCol);
                    }
                    throw new ExpressionParseException(
                            "Unexpected character '|', did you mean '||'?", expression, startLine, startCol);
                }
                case '!' -> {
                    if (pos + 1 < length && text[pos + 1] == '=') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.NE, "!=", startLine, startCol);
                    }
                    advanceChar();
                    return new Token(TokenType.NOT, "!", startLine, startCol);
                }
                case '=' -> {
                    if (pos + 1 < length && text[pos + 1] == '=') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.EQ, "==", startLine, startCol);
                    }
                    throw new ExpressionParseException(
                            "Unexpected character '=', did you mean '=='?", expression, startLine, startCol);
                }
                case '>' -> {
                    if (pos + 1 < length && text[pos + 1] == '=') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.GE, ">=", startLine, startCol);
                    }
                    advanceChar();
                    return new Token(TokenType.GT, ">", startLine, startCol);
                }
                case '<' -> {
                    if (pos + 1 < length && text[pos + 1] == '=') {
                        advanceChar();
                        advanceChar();
                        return new Token(TokenType.LE, "<=", startLine, startCol);
                    }
                    advanceChar();
                    return new Token(TokenType.LT, "<", startLine, startCol);
                }
                case '"', '\'' -> {
                    return readString(c, startLine, startCol);
                }
                default -> {
                    if (isDigit(c)) {
                        return readNumber(startLine, startCol);
                    }
                    if (isIdentifierStart(c)) {
                        return readIdentifier(startLine, startCol);
                    }
                    throw new ExpressionParseException(
                            "Unexpected character: '" + c + "'", expression, startLine, startCol);
                }
            }
        }

        private void advanceChar() {
            if (pos < length) {
                if (text[pos] == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
                pos++;
            }
        }

        private void skipWhitespaceAndComments() throws ExpressionParseException {
            while (pos < length) {
                char c = text[pos];
                if (c == ' ' || c == '\t' || c == '\r') {
                    advanceChar();
                } else if (c == '\n') {
                    advanceChar();
                } else if (c == '/' && pos + 1 < length && text[pos + 1] == '/') {
                    // Line comment
                    advanceChar();
                    advanceChar();
                    while (pos < length && text[pos] != '\n') {
                        advanceChar();
                    }
                } else if (c == '/' && pos + 1 < length && text[pos + 1] == '*') {
                    // Block comment
                    int startLine = line;
                    int startCol = col;
                    advanceChar();
                    advanceChar();
                    boolean closed = false;
                    while (pos < length) {
                        if (text[pos] == '*' && pos + 1 < length && text[pos + 1] == '/') {
                            advanceChar();
                            advanceChar();
                            closed = true;
                            break;
                        }
                        advanceChar();
                    }
                    if (!closed) {
                        throw new ExpressionParseException(
                                "Unterminated block comment", expression, startLine, startCol);
                    }
                } else {
                    break;
                }
            }
        }

        private Token readString(char quote, int startLine, int startCol) throws ExpressionParseException {
            advanceChar(); // consume opening quote
            StringBuilder sb = new StringBuilder();
            boolean closed = false;
            while (pos < length) {
                char c = text[pos];
                if (c == '\\') {
                    advanceChar();
                    if (pos >= length) break;
                    char esc = text[pos];
                    switch (esc) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(esc);
                    }
                    advanceChar();
                } else if (c == quote) {
                    advanceChar();
                    closed = true;
                    break;
                } else {
                    sb.append(c);
                    advanceChar();
                }
            }
            if (!closed) {
                throw new ExpressionParseException(
                        "Unterminated string literal", expression, startLine, startCol);
            }
            return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
        }

        private Token readNumber(int startLine, int startCol) throws ExpressionParseException {
            int start = pos;
            boolean isFloat = false;

            if (text[pos] == '.') {
                isFloat = true;
                advanceChar();
            }

            while (pos < length && isDigit(text[pos])) {
                advanceChar();
            }

            if (!isFloat && pos < length && text[pos] == '.' && pos + 1 < length && isDigit(text[pos + 1])) {
                isFloat = true;
                advanceChar();
                while (pos < length && isDigit(text[pos])) {
                    advanceChar();
                }
            }

            // Exponent
            if (pos < length && (text[pos] == 'e' || text[pos] == 'E')) {
                isFloat = true;
                advanceChar();
                if (pos < length && (text[pos] == '+' || text[pos] == '-')) {
                    advanceChar();
                }
                while (pos < length && isDigit(text[pos])) {
                    advanceChar();
                }
            }

            // Type suffixes
            boolean isLong = false;
            if (pos < length && (text[pos] == 'l' || text[pos] == 'L')) {
                isLong = true;
                advanceChar();
            } else if (pos < length && (text[pos] == 'd' || text[pos] == 'D' || text[pos] == 'f' || text[pos] == 'F')) {
                isFloat = true;
                advanceChar();
            }

            String numStr = expression.substring(start, pos);
            try {
                Number val;
                if (isFloat) {
                    val = Double.parseDouble(numStr);
                } else if (isLong) {
                    String clean = numStr.substring(0, numStr.length() - 1);
                    val = Long.parseLong(clean);
                } else {
                    try {
                        val = Integer.parseInt(numStr);
                    } catch (NumberFormatException nfe) {
                        val = Long.parseLong(numStr);
                    }
                }
                return new Token(TokenType.NUMBER, val, startLine, startCol);
            } catch (NumberFormatException e) {
                throw new ExpressionParseException(
                        "Invalid number literal: '" + numStr + "'", expression, startLine, startCol, e);
            }
        }

        private Token readIdentifier(int startLine, int startCol) {
            int start = pos;
            while (pos < length && isIdentifierPart(text[pos])) {
                advanceChar();
            }
            String ident = expression.substring(start, pos);
            return switch (ident) {
                case "true" -> new Token(TokenType.BOOLEAN, Boolean.TRUE, startLine, startCol);
                case "false" -> new Token(TokenType.BOOLEAN, Boolean.FALSE, startLine, startCol);
                case "null" -> new Token(TokenType.NULL, null, startLine, startCol);
                default -> new Token(TokenType.IDENTIFIER, ident, startLine, startCol);
            };
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isIdentifierStart(char c) {
            return Character.isLetter(c) || c == '_' || c == '$';
        }

        private static boolean isIdentifierPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }
    }
}
