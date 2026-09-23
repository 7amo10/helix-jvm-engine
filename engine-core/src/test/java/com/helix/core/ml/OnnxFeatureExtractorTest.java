package com.helix.core.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.api.ExecutionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxFeatureExtractor Tests")
class OnnxFeatureExtractorTest {

    private static OrtEnvironment env;

    @BeforeAll
    static void initEnv() {
        env = OrtEnvironment.getEnvironment();
    }

    @AfterAll
    static void closeEnv() {
        if (env != null) {
            env.close();
        }
    }

    private OnnxModelDescriptor createDescriptor() {
        return OnnxModelDescriptor.builder()
                .modelName("test_model")
                .version("1.0.0")
                .modelPath("/dummy/path.onnx")
                .inputFeatures(List.of("amount", "is_flagged", "count", "score", "ratio"))
                .outputTensorName("probabilities")
                .outputIndex(1)
                .build();
    }

    @Test
    @DisplayName("Should accurately extract multi-type numeric features into float array")
    void testExtractSingleRow() {
        OnnxModelDescriptor descriptor = createDescriptor();
        OnnxFeatureExtractor extractor = new OnnxFeatureExtractor();

        ExecutionContext ctx = new ExecutionContext(Map.of(
                "amount", 1250.75,         // Double
                "is_flagged", true,        // Boolean -> 1.0f
                "count", 42,               // Integer
                "score", 99.5f,            // Float
                "ratio", new BigDecimal("3.1415") // BigDecimal
        ));

        float[] features = extractor.extractFeatures(descriptor, ctx);
        assertEquals(5, features.length);
        assertEquals(1250.75f, features[0], 0.001f);
        assertEquals(1.0f, features[1], 0.001f);
        assertEquals(42.0f, features[2], 0.001f);
        assertEquals(99.5f, features[3], 0.001f);
        assertEquals(3.1415f, features[4], 0.001f);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when required feature is missing")
    void testMissingFeatureThrows() {
        OnnxModelDescriptor descriptor = createDescriptor();
        OnnxFeatureExtractor extractor = new OnnxFeatureExtractor();

        ExecutionContext ctx = new ExecutionContext(Map.of(
                "amount", 100.0,
                "count", 1
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extractFeatures(descriptor, ctx));
        assertTrue(ex.getMessage().contains("Missing required feature"));
    }

    @Test
    @DisplayName("Should extract batch features and assemble valid native OnnxTensor")
    void testBatchExtractAndTensorAssembly() throws Exception {
        OnnxModelDescriptor descriptor = createDescriptor();
        OnnxFeatureExtractor extractor = new OnnxFeatureExtractor();

        ExecutionContext ctx1 = new ExecutionContext(Map.of(
                "amount", 100.0, "is_flagged", false, "count", 1, "score", 10.0f, "ratio", 1.0
        ));
        ExecutionContext ctx2 = new ExecutionContext(Map.of(
                "amount", 200.0, "is_flagged", true, "count", 2, "score", 20.0f, "ratio", 2.0
        ));

        float[][] batchMatrix = extractor.extractBatchFeatures(descriptor, List.of(ctx1, ctx2));
        assertEquals(2, batchMatrix.length);
        assertEquals(5, batchMatrix[0].length);
        assertEquals(0.0f, batchMatrix[0][1]);
        assertEquals(1.0f, batchMatrix[1][1]);

        try (OnnxTensor tensor = extractor.createTensor(env, descriptor, List.of(ctx1, ctx2))) {
            assertNotNull(tensor);
            assertArrayEquals(new long[]{2, 5}, tensor.getInfo().getShape());
        }
    }
}
