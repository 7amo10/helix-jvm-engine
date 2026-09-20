package com.helix.cli.repl;

import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.FieldAccessNode;
import com.helix.core.parser.ast.FunctionCallNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders colorized Unicode tree visualizations of Helix AST structures.
 */
public class AstTreeRenderer {

    private static final String BRANCH = "├── ";
    private static final String LAST_BRANCH = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String SPACE = "    ";

    /**
     * Renders the given AST node as a colorized tree representation.
     *
     * @param root AST root node
     * @return colorized string tree
     */
    public String render(ExpressionNode root) {
        if (root == null) {
            return "<empty>";
        }
        StringBuilder sb = new StringBuilder();
        renderNode(root, "", true, sb);
        return sb.toString();
    }

    private void renderNode(ExpressionNode node, String prefix, boolean isTail, StringBuilder sb) {
        sb.append(prefix);
        sb.append(isTail ? LAST_BRANCH : BRANCH);

        String childPrefix = prefix + (isTail ? SPACE : VERTICAL);
        List<ExpressionNode> children = new ArrayList<>();

        if (node instanceof RuleNode rule) {
            sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_CYAN)
                    .append("RuleNode: ").append(rule.getName())
                    .append(TerminalRenderer.ANSI_RESET)
                    .append(" [expr: \"").append(rule.getExpression()).append("\"]\n");
            children.add(rule.getRootNode());
        } else if (node instanceof ComparisonNode comp) {
            sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_YELLOW)
                    .append("ComparisonNode: ").append(comp.getOperator().name())
                    .append(" (").append(comp.getOperator().getSymbol()).append(")")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.add(comp.getLeft());
            children.add(comp.getRight());
        } else if (node instanceof BinaryExpressionNode bin) {
            sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_YELLOW)
                    .append("BinaryExpressionNode: ").append(bin.getOperator().name())
                    .append(" (").append(bin.getOperator().getSymbol()).append(")")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.add(bin.getLeft());
            children.add(bin.getRight());
        } else if (node instanceof BinaryOpNode bin) {
            sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_YELLOW)
                    .append("BinaryOpNode: ").append(bin.getOperator().name())
                    .append(" (").append(bin.getOperator().getSymbol()).append(")")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.add(bin.getLeft());
            children.add(bin.getRight());
        } else if (node instanceof UnaryOpNode un) {
            sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_YELLOW)
                    .append("UnaryOpNode: ").append(un.getOperator().name())
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.add(un.getOperand());
        } else if (node instanceof FieldAccessNode field) {
            sb.append(TerminalRenderer.ANSI_BOLD).append("\u001B[35m")
                    .append("FieldAccessNode: .").append(field.getFieldName())
                    .append(TerminalRenderer.ANSI_RESET)
                    .append(" [fullPath: ").append(field.getFullPath()).append("]\n");
            children.add(field.getTarget());
        } else if (node instanceof FunctionCallNode fn) {
            sb.append(TerminalRenderer.ANSI_BOLD).append("\u001B[35m")
                    .append("FunctionCallNode: ").append(fn.getFunctionName()).append("()")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.addAll(fn.getArguments());
        } else if (node instanceof OnnxInferenceNode onnx) {
            sb.append(TerminalRenderer.ANSI_BOLD).append("\u001B[34m")
                    .append("OnnxInferenceNode: ML(\"").append(onnx.getModelName()).append("\"")
                    .append(onnx.getOutputTensorName() != null ? ", \"" + onnx.getOutputTensorName() + "\"" : "")
                    .append(")")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
        } else if (node instanceof MethodCallNode method) {
            sb.append(TerminalRenderer.ANSI_BOLD).append("\u001B[35m")
                    .append("MethodCallNode: .").append(method.getMethodName()).append("()")
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
            children.add(method.getTarget());
            children.addAll(method.getArguments());
        } else if (node instanceof VariableNode var) {
            sb.append(TerminalRenderer.ANSI_CYAN)
                    .append("VariableNode: ").append(var.getName())
                    .append(TerminalRenderer.ANSI_RESET).append("\n");
        } else if (node instanceof LiteralNode lit) {
            String typeName = lit.getType() != null ? lit.getType().getSimpleName() : "Object";
            sb.append(TerminalRenderer.ANSI_GREEN)
                    .append("LiteralNode: ").append(lit.getValue())
                    .append(TerminalRenderer.ANSI_RESET)
                    .append(" (").append(typeName).append(")\n");
        } else {
            sb.append(node.toString()).append("\n");
        }

        for (int i = 0; i < children.size(); i++) {
            boolean last = (i == children.size() - 1);
            renderNode(children.get(i), childPrefix, last, sb);
        }
    }
}
