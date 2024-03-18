package com.github.coderodde.graph.extra;

import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import java.util.List;

public final class ForwardNodeExpander
        extends AbstractNodeExpander<DirectedGraphNode> {

    @Override
    public List<DirectedGraphNode> 
        generateSuccessors(final DirectedGraphNode node) {
        return node.getChildren();
    }

    @Override
    public boolean isValidNode(final DirectedGraphNode node) {
        return true;
    }
}
