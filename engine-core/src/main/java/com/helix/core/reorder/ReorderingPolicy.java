package com.helix.core.reorder;

import com.helix.profiler.node.NodeStats;
import java.util.List;

/**
 * Service Provider Interface (SPI) defining strategies for determining
 * the optimal evaluation ordering of candidate AST clauses.
 */
public interface ReorderingPolicy {

    /**
     * Determines the optimal evaluation order of candidate AST clauses.
     *
     * @param nodes list of node statistics corresponding to candidate clauses
     * @return list of 0-based indices representing the new evaluation sequence
     */
    List<Integer> determineOrder(List<NodeStats> nodes);
}
