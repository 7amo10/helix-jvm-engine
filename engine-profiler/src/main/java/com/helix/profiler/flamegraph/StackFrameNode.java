package com.helix.profiler.flamegraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hierarchical prefix trie node representing a stack frame in a flame graph call tree.
 * <p>
 * Thread-safe for lock-free concurrent sample ingestion and cumulative percentage calculations.
 */
public class StackFrameNode {

    private final String name;
    private final int depth;
    private final StackFrameNode parent;
    private final AtomicLong selfValue = new AtomicLong(0);
    private final AtomicLong totalValue = new AtomicLong(0);
    private final Map<String, StackFrameNode> children = new ConcurrentHashMap<>();

    /**
     * Constructs a new StackFrameNode.
     *
     * @param name   frame name (e.g., class.method)
     * @param depth  call stack depth (0 for root)
     * @param parent parent node reference, or null for root
     */
    public StackFrameNode(String name, int depth, StackFrameNode parent) {
        this.name = Objects.requireNonNull(name, "frame name cannot be null");
        this.depth = depth;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public int getDepth() {
        return depth;
    }

    public StackFrameNode getParent() {
        return parent;
    }

    public long getSelfValue() {
        return selfValue.get();
    }

    public long getTotalValue() {
        return totalValue.get();
    }

    public void incrementSelfValue(long delta) {
        if (delta > 0) {
            selfValue.addAndGet(delta);
        }
    }

    public void incrementTotalValue(long delta) {
        if (delta > 0) {
            totalValue.addAndGet(delta);
        }
    }

    /**
     * Retrieves or creates a child node for the given frame name.
     *
     * @param childName name of the child frame
     * @return child StackFrameNode
     */
    public StackFrameNode getOrCreateChild(String childName) {
        return children.computeIfAbsent(childName, n -> new StackFrameNode(n, depth + 1, this));
    }

    /**
     * Retrieves an existing child by name, or null if absent.
     */
    public StackFrameNode getChild(String childName) {
        return children.get(childName);
    }

    /**
     * Returns an unmodifiable collection of all child nodes.
     */
    public Collection<StackFrameNode> getChildren() {
        return Collections.unmodifiableCollection(children.values());
    }

    /**
     * Returns a list of children sorted by total value descending.
     */
    public List<StackFrameNode> getChildrenSortedByTotal() {
        List<StackFrameNode> sorted = new ArrayList<>(children.values());
        sorted.sort(Comparator.comparingLong(StackFrameNode::getTotalValue).reversed());
        return sorted;
    }

    public boolean isRoot() {
        return parent == null || depth == 0;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Calculates the cumulative percentage of the total profile represented by this frame.
     *
     * @return percentage from 0.0 to 100.0
     */
    public double getCumulativePercentage() {
        StackFrameNode root = getRoot();
        long rootTotal = root != null ? root.getTotalValue() : 0;
        return rootTotal > 0 ? (totalValue.get() * 100.0 / rootTotal) : 0.0;
    }

    /**
     * Calculates the self percentage of the total profile terminating specifically at this frame.
     *
     * @return percentage from 0.0 to 100.0
     */
    public double getSelfPercentage() {
        StackFrameNode root = getRoot();
        long rootTotal = root != null ? root.getTotalValue() : 0;
        return rootTotal > 0 ? (selfValue.get() * 100.0 / rootTotal) : 0.0;
    }

    /**
     * Finds the root of this flame graph trie.
     */
    public StackFrameNode getRoot() {
        StackFrameNode curr = this;
        while (curr.parent != null) {
            curr = curr.parent;
        }
        return curr;
    }

    /**
     * Recursively searches for a node with the specified name in this subtree.
     */
    public StackFrameNode findNode(String frameName) {
        if (name.equals(frameName)) {
            return this;
        }
        for (StackFrameNode child : children.values()) {
            StackFrameNode found = child.findNode(frameName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Renders a human-readable hierarchical tree string of this node and its descendants.
     */
    public String toTreeString() {
        StringBuilder sb = new StringBuilder();
        buildTreeString(sb, "", true);
        return sb.toString();
    }

    private void buildTreeString(StringBuilder sb, String prefix, boolean isTail) {
        sb.append(prefix)
                .append(isTail ? "└── " : "├── ")
                .append(name)
                .append(" [total=").append(totalValue.get())
                .append(" (").append(String.format("%.1f", getCumulativePercentage())).append("%)")
                .append(", self=").append(selfValue.get())
                .append("]\n");

        List<StackFrameNode> sortedChildren = getChildrenSortedByTotal();
        for (int i = 0; i < sortedChildren.size(); i++) {
            boolean tail = (i == sortedChildren.size() - 1);
            sortedChildren.get(i).buildTreeString(sb, prefix + (isTail ? "    " : "│   "), tail);
        }
    }

    @Override
    public String toString() {
        return "StackFrameNode{" +
                "name='" + name + '\'' +
                ", depth=" + depth +
                ", self=" + selfValue.get() +
                ", total=" + totalValue.get() +
                ", children=" + children.size() +
                '}';
    }
}
