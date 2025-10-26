package io.github.coderodde.graph.pathfinding.delayed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This class provides the API and default implementation of a progress logging 
 * facilities. 
 * 
 * @version 1.0.0 (March 17, 2024)
 * @param <N> the actual node type.
 */
public class ProgressLogger<N> {
    
    /**
     * The entry class describing a node and its expansion time duration.
     * 
     * @param <N> the graph node type.
     */
    public static final class ExpansionEntry<N> {
        
        /**
         * The expanded node.
         */
        private final N node;
        
        /**
         * The expansion duration.
         */
        private final long durationMillis;
        
        ExpansionEntry(final N node, 
                       final long durationMillis) {
            
            this.node = Objects.requireNonNull(node);
            this.durationMillis = durationMillis;
        }
        
        public N getNode() {
            return node;
        }
        
        public long getDurationMillis() {
            return durationMillis;
        }
    }
    
    /**
     * The list of expansion entries.
     */
    private final List<ExpansionEntry> expansionEntries = new ArrayList<>();
    
    /**
     * Caches the sum of all expansion durations.
     */
    private long sumOfExpansionDurations = 0L;
    
    /**
     * This method should be called whenever the search is initiated.
     * 
     * @param source the source node.
     * @param target the target node.
     */
    public void onBeginSearch(final N source, final N target) {
        expansionEntries.clear();
        sumOfExpansionDurations = 0L;
    }

    /**
     * This method should be called whenever the search expands the node 
     * {@code node}.
     * 
     * @param node the node being expanded.
     * @param durationMillis the duration of expansion operation in 
     *                       milliseconds.
     */
    public void onExpansion(final N node, final long durationMillis) {
        expansionEntries.add(
                new ExpansionEntry(node, 
                                   durationMillis));
        
        sumOfExpansionDurations += durationMillis;
    }

    /**
     * Returns an unmodifiable list view of all the expansion entries in this 
     * progress logger.
     * 
     * @return the list of expansion entries.
     */
    public List<ExpansionEntry> getExpansionEntries() {
        return Collections.unmodifiableList(expansionEntries);
    }
    
    /**
     * Returns the number of expansions this progress logger registered.
     * 
     * @return the number of expansions.
     */
    public int getNumberOfExpansions() {
        return expansionEntries.size();
    }
    
    /**
     * Returns the sum of all expansion durations.
     * 
     * @return the sum of all expansion durations.
     */
    public long getSumOfExpansionDurations() {
        return sumOfExpansionDurations;
    }
    
    /**
     * Returns the mean of expansions.
     * 
     * @return the mean of expansions.
     */
    public double getMeanExpansionDuration() {
        if (expansionEntries.isEmpty()) {
            throw new IllegalStateException(
                    "The expansion entry list is empty");
        }
        
        return (double) sumOfExpansionDurations / 
               (double) expansionEntries.size();
    }
    
    /**
     * This method should be called whenever the search is generating a neighbor
     * node of the node being expanded.
     * 
     * @param node the generated neighbor node.
     */
    public void onNeighborGeneration(final N node) {}

    /**
     * This method should be called whenever the search is improving the 
     * distance of the input node.
     * 
     * @param node the node whose tentative shortest path distance has been 
     *             improved by the search.
     */
    public void onNeighborImprovement(final N node) {}

    /**
     * This method should be called whenever the search has found a shortest 
     * path.
     * 
     * @param path the shortest path found. 
     */
    public void onShortestPath(final List<N> path) {}

    /**
     * This method should be called whenever the target node is not reachable 
     * from the source node and the search process must stop without finding a 
     * path.
     * 
     * @param source the requested source node.
     * @param target the requested target node.
     */
    public void onTargetUnreachable(final N source, final N target) {}
}
