package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing an in-process machine learning inference invocation via ONNX Runtime.
 *
 * <p>Produces a scalar floating-point score (posterior probability or confidence score)
 * within the range [0.0, 1.0] that can participate in downstream relational and boolean
 * guard expressions (e.g., {@code ML(fraud_model_v1) > 0.85}).</p>
 */
public class OnnxInferenceNode implements ExpressionNode {

    private final String modelName;
    private final String outputTensorName;
    private final int outputIndex;

    /**
     * Constructs an OnnxInferenceNode with default output tensor mapping ("probabilities", index 1).
     *
     * @param modelName unique name of the target ONNX model
     */
    public OnnxInferenceNode(String modelName) {
        this(modelName, "probabilities", 1);
    }

    /**
     * Constructs an OnnxInferenceNode with custom output tensor mapping and index.
     *
     * @param modelName        unique name of the target ONNX model
     * @param outputTensorName target output tensor identifier in the ONNX graph
     * @param outputIndex      index within the output tensor to extract
     */
    public OnnxInferenceNode(String modelName, String outputTensorName, int outputIndex) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName cannot be null or blank");
        }
        this.modelName = modelName.trim();
        this.outputTensorName = (outputTensorName != null && !outputTensorName.isBlank())
                ? outputTensorName.trim()
                : "probabilities";
        if (outputIndex < 0) {
            throw new IllegalArgumentException("outputIndex cannot be negative: " + outputIndex);
        }
        this.outputIndex = outputIndex;
    }

    public String getModelName() {
        return modelName;
    }

    public String getOutputTensorName() {
        return outputTensorName;
    }

    public int getOutputIndex() {
        return outputIndex;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnnxInferenceNode that = (OnnxInferenceNode) o;
        return outputIndex == that.outputIndex
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(outputTensorName, that.outputTensorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, outputTensorName, outputIndex);
    }

    @Override
    public String toString() {
        if ("probabilities".equals(outputTensorName) && outputIndex == 1) {
            return "ML(" + modelName + ")";
        }
        return "ML(" + modelName + ", \"" + outputTensorName + "\", " + outputIndex + ")";
    }
}
