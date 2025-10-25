package io.github.coderodde.graph.extra;

import java.util.List;

public final class GraphPair {
    public final List<DirectedGraphNode> delayedGraph;
    public final List<DirectedGraphNode> nondelayedGraph;
    
    public GraphPair(List<DirectedGraphNode> delayedGraph,
                     List<DirectedGraphNode> nondelayedGraph) {
        this.delayedGraph = delayedGraph;
        this.nondelayedGraph = nondelayedGraph;
    }
}
