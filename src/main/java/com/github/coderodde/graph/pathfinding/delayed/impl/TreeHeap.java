package com.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class implements the tree-base heap (Dial's heap, actually) for keeping
 * graph nodes sorted by their distance to the respective terminal nodes.
 * 
 * @version 2.0.1 (May 8, 2024)
 * @since 2.0.1
 */
public final class TreeHeap<N> implements Iterable<N> {

    @Override
    public Iterator<N> iterator() {
        return new TreeHeapIterator();
    }
    
    private static final class TreeHeapNode<N> {
        N node;
        int priority;
        TreeHeapNode<N> prev;
        TreeHeapNode<N> next;
        
        TreeHeapNode(final N node, final int priority) {
            this.node = node;
            this.priority = priority;
        }
    }
    
    private final class TreeHeapIterator implements Iterator<N> {

        private int iterated = 0;
        private TreeHeapNode<N> currentTreeHeapNode;
        
        TreeHeapIterator() {
            for (int p = 0; p < accessTable.length; p++) {
                if (accessTable[p] != null) {
                    currentTreeHeapNode = accessTable[p];
                    return;
                }
            }
            
            currentTreeHeapNode = null;
        }
        
        @Override
        public boolean hasNext() {
            return iterated < size;
        }

        @Override
        public N next() {
            final N returnElement = currentTreeHeapNode.node;
            iterated++;
            currentTreeHeapNode = computeNextTreeHeapNode();
            return returnElement;
        }
        
        private TreeHeapNode<N> computeNextTreeHeapNode() {
            if (iterated == size) {
                return null;
            }
                
            if (currentTreeHeapNode.next != null) {
                return currentTreeHeapNode.next;
            }
            
            for (int p = currentTreeHeapNode.priority + 1;
                     p < accessTable.length; 
                     p++) {
                
                if (accessTable[p] != null) {
                    return accessTable[p];
                }
            }
            
            throw new IllegalStateException("Should not get here.");
        }
    }
    
    private static final int DEFAULT_ACCESS_TABLE_CAPACITY = 64;
    
    private final TreeHeapNode<N>[] accessTable;
    private final Map<N, TreeHeapNode<N>> nodeMap = new HashMap<>();
    private int size;
    
    TreeHeap(final int accessTableCapacity) {
        this.accessTable = new TreeHeapNode[accessTableCapacity];
    }
    
    TreeHeap() {
        this(DEFAULT_ACCESS_TABLE_CAPACITY);
    }
    
    void insert(final N node, final int priority) {
        final TreeHeapNode<N> newTreeHeapNode =
                new TreeHeapNode<>(node, priority);
        
        nodeMap.put(node, newTreeHeapNode);
        linkImpl(newTreeHeapNode, priority);
        size++;
    }
    
    void update(final N element, final int priority) {
        final TreeHeapNode<N> node = nodeMap.get(element);
        
        unlinkImpl(node);
        linkImpl(node, priority);
        node.priority = priority;
    }
    
    void remove(final N element) {
        final TreeHeapNode<N> node = nodeMap.get(element);
        
        if (node == null) {
            return;
        }
        
        unlinkImpl(node);
        size--;
    }
    
    private void linkImpl(final TreeHeapNode<N> node, final int priority) {
        final TreeHeapNode<N> currentBucketHead = accessTable[priority];
        
        if (currentBucketHead != null) {
            node.next = accessTable[priority];
            accessTable[priority].prev = node;
        } 
        
        accessTable[priority] = node;
    }
    
    private void unlinkImpl(final TreeHeapNode<N> node) {
        if (node.prev == null && node.next == null) {
            accessTable[node.priority] = null;
        }
        
        if (node.prev != null) {
            node.prev.next = node.next;
            node.prev = null; // Help GC.
        }
        
        if (node.next != null) {
            node.next.prev = node.prev;
            node.next = null; // Help GC.
        }
    }
}
 