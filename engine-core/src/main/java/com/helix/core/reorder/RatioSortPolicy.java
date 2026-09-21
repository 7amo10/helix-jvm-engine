package com.helix.core.reorder;

import com.helix.profiler.node.NodeStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Analytical baseline reordering policy sorting candidate clauses in ascending
 * order of their cost-to-failure ratio (C_i / F_i).
 *
 * <p>Clauses with the lowest ratio (fastest to evaluate and most likely to fail)
 * are placed first to maximize short-circuit probability.</p>
 */
public class RatioSortPolicy implements ReorderingPolicy {

    private static final double DEFAULT_ML_RATIO = 100_000.0;
    private static final double DEFAULT_CHEAP_RATIO = 50.0;

    @Override
    public List<Integer> determineOrder(List<NodeStats> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        if (nodes.size() == 1) {
            return List.of(0);
        }

        int n = nodes.size();
        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            indices.add(i);
        }

        // Stable sort ascending by ratio
        indices.sort(Comparator.comparingDouble(i -> computeRatio(nodes.get(i))));
        return indices;
    }

    /**
     * Computes the effective cost-to-failure ratio for a node.
     *
     * @param stats node statistics
     * @return ratio value
     */
    public double computeRatio(NodeStats stats) {
        if (stats == null) {
            return DEFAULT_CHEAP_RATIO;
        }

        if (stats.getExecutionCount() > 0) {
            double fRate = stats.getFailureRate();
            if (fRate > 0.0) {
                return stats.getCostToFailureRatio();
            }
            // Apply optimistic prior to avoid downstream short-circuit blindness
            return stats.getAverageCostNanos() / 0.50;
        }

        if (stats.getNodeId() != null && stats.getNodeId().toLowerCase().contains("ml")) {
            return DEFAULT_ML_RATIO;
        }
        return DEFAULT_CHEAP_RATIO;
    }
}
