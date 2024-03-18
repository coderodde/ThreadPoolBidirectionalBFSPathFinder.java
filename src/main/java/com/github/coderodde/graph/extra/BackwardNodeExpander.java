package com.github.coderodde.graph.extra;

import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import java.util.List;

public final class BackwardNodeExpander
        extends AbstractNodeExpander<DirectedGraphNode> {

    @Override
    public List<DirectedGraphNode> 
        generateSuccessors(final DirectedGraphNode node) {
        return node.getParents();
    }

    @Override
    public boolean isValidNode(final DirectedGraphNode node) {
        return true;
    }
}
