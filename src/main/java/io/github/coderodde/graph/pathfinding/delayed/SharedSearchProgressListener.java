package io.github.coderodde.graph.pathfinding.delayed;

import java.util.List;

/**
 * This class provides the API and default implementation of shared progress 
 * logging facilities. 
 *
 * @param <N> the actual node type.
 */
public abstract class SharedSearchProgressListener<N> {
    
    /**
     * This method should be called whenever the search is initiated.
     * 
     * @param source the source node.
     * @param target the target node.
     */
    public void onBeginSearch(final N source, final N target) {}

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
