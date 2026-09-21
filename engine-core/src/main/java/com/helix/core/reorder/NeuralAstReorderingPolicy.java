package com.helix.core.reorder;

import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.profiler.node.NodeStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Neural AST Reordering Policy utilizing the embedded ast_reorder_policy.onnx model.
 *
 * <p>Encodes candidate AST node statistics into an 82-dimensional float observation vector,
 * evaluates candidate action logits via {@link OnnxSessionPool}, and applies action masking
 * to decode an optimal permutation sequence.</p>
 *
 * <p>Seamlessly falls back to {@link RatioSortPolicy} if the neural policy model or
 * runtime session pool is unavailable.</p>
 */
public class NeuralAstReorderingPolicy implements ReorderingPolicy {

    private static final Logger log = LoggerFactory.getLogger(NeuralAstReorderingPolicy.class);

    public static final String POLICY_MODEL_NAME = "ast_reorder_policy";
    public static final String INPUT_TENSOR_NAME = "observation";
    public static final String OUTPUT_TENSOR_NAME = "action_logits";
    public static final int MAX_CANDIDATE_NODES = 20;
    public static final int OBSERVATION_DIM = 82;

    private final OnnxSessionPool sessionPool;
    private final RatioSortPolicy fallbackPolicy;

    public NeuralAstReorderingPolicy() {
        this(null);
    }

    public NeuralAstReorderingPolicy(OnnxSessionPool sessionPool) {
        this.sessionPool = sessionPool;
        this.fallbackPolicy = new RatioSortPolicy();
    }

    @Override
    public List<Integer> determineOrder(List<NodeStats> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        if (nodes.size() == 1) {
            return List.of(0);
        }

        int n = nodes.size();
        if (n > MAX_CANDIDATE_NODES) {
            log.debug("Candidate nodes count ({}) exceeds neural model max ({}). Falling back to ratio sort.",
                    n, MAX_CANDIDATE_NODES);
            return fallbackPolicy.determineOrder(nodes);
        }

        OnnxSessionPool pool = this.sessionPool != null ? this.sessionPool : OnnxModelExecutor.getSessionPool();
        if (pool == null) {
            log.debug("No active OnnxSessionPool available for neural reordering. Falling back to ratio sort.");
            return fallbackPolicy.determineOrder(nodes);
        }

        try {
            return decodePermutation(pool, nodes);
        } catch (Exception e) {
            log.warn("Neural AST reordering failed ({}). Falling back to ratio sort.", e.getMessage());
            return fallbackPolicy.determineOrder(nodes);
        }
    }

    private List<Integer> decodePermutation(OnnxSessionPool pool, List<NodeStats> nodes) {
        int n = nodes.size();
        float[][] obs = new float[1][OBSERVATION_DIM];
        encodeObservation(obs[0], nodes, n, 0);

        float[][] logitsMatrix = pool.executeTensorInference(
                POLICY_MODEL_NAME, INPUT_TENSOR_NAME, obs, OUTPUT_TENSOR_NAME
        );
        float[] logits = logitsMatrix[0];

        // Action masking: mask unpopulated candidate node slots
        for (int j = n; j < MAX_CANDIDATE_NODES; j++) {
            logits[j] = Float.NEGATIVE_INFINITY;
        }

        // Iteratively decode permutation sequence using action masking
        List<Integer> permutation = new ArrayList<>(n);
        boolean[] used = new boolean[n];

        for (int step = 0; step < n; step++) {
            int bestAction = -1;
            float maxLogit = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (!used[j] && logits[j] > maxLogit) {
                    maxLogit = logits[j];
                    bestAction = j;
                }
            }

            if (bestAction == -1) {
                for (int j = 0; j < n; j++) {
                    if (!used[j]) {
                        bestAction = j;
                        break;
                    }
                }
            }

            used[bestAction] = true;
            permutation.add(bestAction);
        }

        return permutation;
    }

    /**
     * Encodes candidate node statistics into an 82-dimensional float observation vector.
     *
     * @param obs   82-dimensional target float array
     * @param nodes candidate nodes
     * @param n     number of active candidate nodes
     * @param step  current decoding step (0 to n - 1)
     */
    public void encodeObservation(float[] obs, List<NodeStats> nodes, int n, int step) {
        for (int i = 0; i < MAX_CANDIDATE_NODES; i++) {
            int offset = 4 * i;
            if (i < n) {
                NodeStats s = nodes.get(i);
                if (s != null) {
                    double avgCostNanos = s.getAverageCostNanos();
                    double failureRate = s.getFailureRate();
                    long execCount = s.getExecutionCount();
                    double ratio = s.getCostToFailureRatio();

                    if (execCount == 0) {
                        boolean isMl = s.getNodeId() != null && s.getNodeId().toLowerCase().contains("ml");
                        avgCostNanos = isMl ? 200_000.0 : 50.0;
                        failureRate = isMl ? 0.05 : 0.90;
                        ratio = avgCostNanos / failureRate;
                    } else if (s.getFailureCount() == 0) {
                        failureRate = 0.50;
                        ratio = avgCostNanos / 0.50;
                    }

                    // Feature 0: Execution cost in milliseconds (normalized)
                    obs[offset + 0] = (float) (avgCostNanos / 1_000_000.0);
                    // Feature 1: Failure rate in [0.0, 1.0]
                    obs[offset + 1] = (float) failureRate;
                    // Feature 2: Execution count normalized
                    obs[offset + 2] = (float) Math.min(1.0, execCount / 1000.0);
                    // Feature 3: Cost-to-failure ratio normalized
                    obs[offset + 3] = (float) Math.min(1.0, ratio / 1_000_000.0);
                }
            }
        }
        obs[80] = (float) n;
        obs[81] = (float) step;
    }
}
