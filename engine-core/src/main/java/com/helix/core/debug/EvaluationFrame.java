package com.helix.core.debug;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable snapshot of JVM execution frame state at an AST condition evaluation point.
 */
public class EvaluationFrame {

    private final int clauseIndex;
    private final String description;
    private final String operator;
    private final Object leftValue;
    private final Object rightValue;
    private final boolean outcome;
    private final Map<String, Object> variables;
    private final long timestamp;

    public EvaluationFrame(int clauseIndex, String description, String operator,
                           Object leftValue, Object rightValue, boolean outcome,
                           Map<String, Object> variables) {
        this.clauseIndex = clauseIndex;
        this.description = description;
        this.operator = operator;
        this.leftValue = leftValue;
        this.rightValue = rightValue;
        this.outcome = outcome;
        this.variables = variables != null ? Collections.unmodifiableMap(new TreeMap<>(variables)) : Collections.emptyMap();
        this.timestamp = System.currentTimeMillis();
    }

    public int getClauseIndex() {
        return clauseIndex;
    }

    public String getDescription() {
        return description;
    }

    public String getOperator() {
        return operator;
    }

    public Object getLeftValue() {
        return leftValue;
    }

    public Object getRightValue() {
        return rightValue;
    }

    public boolean isOutcome() {
        return outcome;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "EvaluationFrame{" +
                "clauseIndex=" + clauseIndex +
                ", description='" + description + '\'' +
                ", operator='" + operator + '\'' +
                ", leftValue=" + leftValue +
                ", rightValue=" + rightValue +
                ", outcome=" + outcome +
                ", variables=" + variables +
                '}';
    }
}
