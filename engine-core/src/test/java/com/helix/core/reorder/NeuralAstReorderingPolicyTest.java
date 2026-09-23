package com.helix.core.reorder;

import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.profiler.node.NodeStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NeuralAstReorderingPolicy Unit Tests")
class NeuralAstReorderingPolicyTest {

    private static LocalModelRegistry registry;
    private static OnnxSessionPool sessionPool;

    @BeforeAll
    static void setUp() {
        URL modelUrl = NeuralAstReorderingPolicyTest.class.getResource("/models/ast_reorder_policy.onnx");
        assertNotNull(modelUrl, "ast_reorder_policy.onnx must exist in test resources");
        File modelFile = new File(modelUrl.getFile());

        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("ast_reorder_policy")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(List.of("observation"))
                .outputTensorName("action_logits")
                .outputIndex(0)
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(descriptor);
        sessionPool = new OnnxSessionPool(registry, 4);
    }

    @AfterAll
    static void tearDown() {
        if (sessionPool != null) {
            sessionPool.close();
        }
    }

    @Test
    @DisplayName("Should accurately sort candidate clauses, pushing expensive ML node to evaluation tail")
    void testNeuralPolicyPushesMlToTail() {
        NeuralAstReorderingPolicy policy = new NeuralAstReorderingPolicy(sessionPool);

        // Node 0: Expensive ML node (200,000 ns cost, 5% failure rate)
        NodeStats mlNode = new NodeStats("FraudRule", "clause_ml_fraud_model");
        for (int i = 0; i < 95; i++) mlNode.record(200_000, true);
        for (int i = 0; i < 5; i++) mlNode.record(200_000, false);

        // Node 1: Cheap check (50 ns cost, 90% failure rate)
        NodeStats cheapNode = new NodeStats("FraudRule", "clause_amount_greater_than");
        for (int i = 0; i < 10; i++) cheapNode.record(50, true);
        for (int i = 0; i < 90; i++) cheapNode.record(50, false);

        List<Integer> order = policy.determineOrder(List.of(mlNode, cheapNode));
        assertEquals(List.of(1, 0), order, "Neural policy must select cheap predicate first and push ML node to tail");
    }

    @Test
    @DisplayName("Should sort 3 candidate clauses properly with action masking")
    void testThreeClausesWithActionMasking() {
        NeuralAstReorderingPolicy policy = new NeuralAstReorderingPolicy(sessionPool);

        // Node 0: ML node (200,000 ns, 10% failure)
        NodeStats mlNode = new NodeStats("MultiRule", "clause_ml");
        for (int i = 0; i < 90; i++) mlNode.record(200_000, true);
        for (int i = 0; i < 10; i++) mlNode.record(200_000, false);

        // Node 1: Fast & highly selective check (30 ns, 80% failure)
        NodeStats fastHighFail = new NodeStats("MultiRule", "clause_fast_fail");
        for (int i = 0; i < 20; i++) fastHighFail.record(30, true);
        for (int i = 0; i < 80; i++) fastHighFail.record(30, false);

        // Node 2: Moderate check (500 ns, 50% failure)
        NodeStats moderateNode = new NodeStats("MultiRule", "clause_moderate");
        for (int i = 0; i < 50; i++) moderateNode.record(500, true);
        for (int i = 0; i < 50; i++) moderateNode.record(500, false);

        List<Integer> order = policy.determineOrder(List.of(mlNode, fastHighFail, moderateNode));

        assertEquals(3, order.size());
        assertEquals(1, order.get(0), "Fastest & most failing check must be selected first");
        assertEquals(0, order.get(2), "ML node must be pushed to the evaluation tail");
    }

    @Test
    @DisplayName("Should seamlessly fallback to analytical ratio sort if session pool or model is unavailable")
    void testSeamlessFallbackWhenModelUnavailable() {
        // Create policy with null session pool or invalid registry
        LocalModelRegistry emptyRegistry = new LocalModelRegistry();
        OnnxSessionPool emptyPool = new OnnxSessionPool(emptyRegistry, 2);

        NeuralAstReorderingPolicy policy = new NeuralAstReorderingPolicy(emptyPool);

        NodeStats slow = new NodeStats("FallbackRule", "slow");
        slow.record(100_000, false); // ratio 100,000

        NodeStats fast = new NodeStats("FallbackRule", "fast");
        fast.record(50, false); // ratio 50

        List<Integer> order = policy.determineOrder(List.of(slow, fast));
        assertEquals(List.of(1, 0), order, "Must seamlessly fallback to analytical ratio sort when model is absent");

        emptyPool.close();
    }

    @Test
    @DisplayName("Should handle edge cases: empty list, single element, or null list")
    void testEdgeCases() {
        NeuralAstReorderingPolicy policy = new NeuralAstReorderingPolicy(sessionPool);

        assertTrue(policy.determineOrder(List.of()).isEmpty());
        assertTrue(policy.determineOrder(null).isEmpty());

        NodeStats single = new NodeStats("Rule", "single");
        assertEquals(List.of(0), policy.determineOrder(List.of(single)));
    }
}
