package com.github.coderodde.graph.extra;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements a node in an unweighted, directed graph.
 */
public final class DirectedGraphNode {
    
    private final int id;
    private final boolean isDelayed;
    private final int delayMilliseconds;
    private final List<DirectedGraphNode> children = new ArrayList<>();
    private final List<DirectedGraphNode> parents  = new ArrayList<>();
    
    /**
     * Constructs a directed graph node with no delay.
     * 
     * @param id the node ID. 
     */
    public DirectedGraphNode(final int id) {
        this(id, false, Integer.MAX_VALUE);
    }
    
    /**
     * Constructs a directed graph node with ID {@code id}. If {@code isDelayed}
     * is {@code true}, the node sleeps for {@code delayMilliseconds} 
     * milliseconds before returning the list of parents/children.
     * 
     * @param id                the node ID.
     * @param isDelayed         the flag specifying whether the node shall wait.
     * @param delayMilliseconds the sleep delay in milliseconds. 
     */
    public DirectedGraphNode(final int id, 
                             final boolean isDelayed,
                             final int delayMilliseconds) {
        this.id = id;
        this.isDelayed = isDelayed;
        this.delayMilliseconds = delayMilliseconds;
    }
    
    public int getId() {
        return id;
    }
    
    public void addChild(final DirectedGraphNode child) {
        children.add(child);
        child.parents.add(this);
    }
    
    public boolean hasChild(final DirectedGraphNode child) {
        return children.contains(child);
    }
    
    public List<DirectedGraphNode> getChildren() {
        if (isDelayed) {
            // Simulate network access.
            sleep(delayMilliseconds);
        }
        
        return children;
    }
    
    public List<DirectedGraphNode> getParents() {
        if (isDelayed) {
            // Simulate network access.
            sleep(delayMilliseconds);
        }
        
        return parents;
    }
    
    @Override
    public boolean equals(Object obj) {
        DirectedGraphNode other = (DirectedGraphNode) obj;
        return id == other.id;
    }
    
    @Override
    public int hashCode() {
        return id;
    }
    
    @Override
    public String toString() {
        return "[Node id = " + id + "]";
    }
    
    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            
        }
    }
}