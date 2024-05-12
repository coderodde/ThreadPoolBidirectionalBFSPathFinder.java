package com.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * This class implements a priority queue data structure called a Dial's heap.
 * 
 * @param <D> the type of the satellite data.
 * 
 * @version 1.0.0 (May 10, 2024)
 * @since 1.0.0
 */
public class DialsHeap<D> implements IntegerMinimumPriorityQueue<D> {
    
    /**
     * This static inner class implements the node type for this heap.
     * 
     * @param <D> the satellite data type.
     */
    private static final class DialsHeapNode<D> {
        
        /**
         * The actual satellite datum.
         */
        final D datum;
        
        /**
         * The priority key of this node. Must be at least zero (0).
         */
        int priority;
        
        /**
         * The previous node in the collision chain or {@code null} if this node
         * is the head of the collision chain.
         */
        DialsHeapNode<D> prev;
        
        /**
         * The next node in the collision chain or {@code null} if this node is
         * the tail of the collision chain.
         */
        DialsHeapNode<D> next;
        
        /**
         * Constructs a new heap node.'
         * 
         * @param datum    the satellite datum.
         * @param priority the priority key.
         */
        DialsHeapNode(final D datum, final int priority) {
            this.datum = datum;
            this.priority = priority;
        }
    }
    
    /**
     * This inner class implements the iterator over all satellite data in this
     * heap in the ascending priority key order.
     */
    private final class DialsHeapIterator implements Iterator<D> {

        /**
         * Caches the number of nodes already iterated.
         */
        private int iterated = 0;
        
        /**
         * The current heap node.
         */
        private DialsHeapNode<D> currentDialsHeapNode;
        
        /**
         * Constructs a new iterator over the enclosing heap.
         */
        private DialsHeapIterator() {
            // Attempt to find the head node:
            for (final DialsHeapNode<D> headNode : table) {
                if (headNode != null) {
                    currentDialsHeapNode = headNode;
                    return;
                }
            }
            
            // Once here, the heap is empty, return null:
            currentDialsHeapNode = null;
        }
        
        /**
         * {@inheritDoc } 
         */
        @Override
        public boolean hasNext() {
            return iterated < nodeMap.size();
        }

        /**
         * {@inheritDoc} 
         */
        @Override
        public D next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Nothing to iterate left.");
            }
            
            final D returnElement = currentDialsHeapNode.datum;
            iterated++;
            currentDialsHeapNode = computeNextDialsHeapNode();
            return returnElement;
        }
        
        /**
         * Returns the next heap node.
         * 
         * @return the next heap node in the iteration order.
         */
        private DialsHeapNode<D> computeNextDialsHeapNode() {
            if (iterated == nodeMap.size()) {
                // Once here, iteration is complete.
                return null;
            }
                
            if (currentDialsHeapNode.next != null) {
                // currentDialsHeapNode has minimum priority key, move to its 
                // right sibling/neighbor in the collision chain:
                return currentDialsHeapNode.next;
            }
            
            // Search the next smallest priority node:
            for (int p = currentDialsHeapNode.priority + 1;
                     p < table.length; 
                     p++) {
                
                if (table[p] != null) {
                    // Found!
                    return table[p];
                }
            }
            
            // We should never ever get here.
            throw new IllegalStateException("Should not get here.");
        }
    }
    
    /**
     * The default table capacity.
     */
    private static final int DEFAULT_TABLE_CAPACITY = 8;
    
    /**
     * The table mapping each slot to the head of a collision chain.
     */
    private DialsHeapNode<D>[] table;
    
    /**
     * The map mapping the satellite datums to their respective heap nodes.
     */
    private final Map<D, DialsHeapNode<D>> nodeMap = new HashMap<>();
    
    /**
     * Constructs a heap with {@code tableCapacity} as the capacity of the 
     * internal collision chain table.
     * 
     * @param tableCapacity the requested collision chain capacity.
     */
    public DialsHeap(final int tableCapacity) {
        this.table = new DialsHeapNode[tableCapacity];
    }
    
    /**
     * Constructs a heap0 with default collision chain table capacity.
     */
    public DialsHeap() {
        this(DEFAULT_TABLE_CAPACITY);
    }
    
    /**
     * {@inheritDoc }
     */
    @Override
    public Iterator<D> iterator() {
        return new DialsHeapIterator();
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public void insert(final D datum, final int priority) {
        
        if (nodeMap.containsKey(datum)) {
            // Once here, the input datum is already in this heap. Use 
            // updatePriority for adjusting the priority key.
            return;
        }
        
        checkPriority(priority);
        
        if (mustExpand(priority)) {
            expand(priority);
        }
        
        final DialsHeapNode<D> newTreeHeapNode =
                new DialsHeapNode<>(datum, priority);
        
        nodeMap.put(datum, newTreeHeapNode);
        linkImpl(newTreeHeapNode, priority);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public void updatePriority(final D datum, final int priority) {
        
        if (mustExpand(priority)) {
            expand(priority);
        }
        
        final DialsHeapNode<D> node = nodeMap.get(datum);
        
        unlinkImpl(node);
        linkImpl(node, priority);
        node.priority = priority;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public int minimumPriority() {
        return accessMinimumPriorityNode().priority;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public D minimumNode() {
        if (nodeMap.isEmpty()) {
            return null;
        }
        
        return accessMinimumPriorityNode().datum;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public boolean containsDatum(final D datum) {
        return nodeMap.containsKey(datum);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public int getPriority(final D datum) {
        return nodeMap.get(datum).priority;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public D extractMinimum() {
        if (nodeMap.isEmpty()) {
            return null;
        }
        
        final DialsHeapNode<D> treeNode = accessMinimumPriorityNode();
        
        unlinkImpl(treeNode);
        nodeMap.remove(treeNode.datum);
        return treeNode.datum;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public void remove(final D datum) {
        unlinkImpl(nodeMap.get(datum));
        nodeMap.remove(datum);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public void clear() {
        nodeMap.clear();
        Arrays.fill(table, null);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public Object clone() {
        final int maximumPriorityKey = getMaximumPriority();
        final int cloneCapacity = getNextCapacity(maximumPriorityKey);
        final DialsHeap<D> copy = new DialsHeap<>(cloneCapacity);
        
        for (final Map.Entry<D, DialsHeapNode<D>> entry : nodeMap.entrySet()) {
            copy.insert(entry.getValue().datum, entry.getValue().priority);
        }
        
        return copy;
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public int size() {
        return nodeMap.size();
    }
    
    /**
     * {@inheritDoc } 
     */
    @Override
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }
    
    /**
     * Returns the head of the collision chain with the lowest priority key.
     * 
     * @return the head of the collision chain with the lowest priority key.
     */
    private DialsHeapNode<D> accessMinimumPriorityNode() {
        for (int p = 0; p != table.length; p++) {
            if (table[p] != null) {
                return table[p];
            }
        }
        
        throw new IllegalStateException("Should not get here.");
    }
    
    /**
     * Links the node {@code node} to the head of the collision chain with 
     * priority key {@code priority}.
     * 
     * @param node     the node to link.
     * @param priority the priority key to link with.
     */
    private void linkImpl(final DialsHeapNode<D> node, final int priority) {
        final DialsHeapNode<D> currentBucketHead = table[priority];
        
        if (currentBucketHead != null) {
            node.next = currentBucketHead;
            currentBucketHead.prev = node;
        } 
        
        table[priority] = node;
    }
    
    /**
     * Unlinks the node {@code node} from this heap.
     * 
     * @param node the node to unlink.
     */
    private void unlinkImpl(final DialsHeapNode<D> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
            node.prev = null;
            
            if (node.next != null) {
                node.next.prev = node.prev;
                node.next = null;
            }
        } else {
            // Once here, node.prev == null!
            if (node.next != null) {
                node.next.prev = null;
                table[node.priority] = node.next;
                node.next = null;
            } else {
                // Remove the last node in the collision chain:
                table[node.priority] = null;
            }
        }
    }
    
    /**
     * Returns {@code true} if this heap's table cannot accommodate a node with
     * priority {@code priority}.
     * 
     * @param priority the priority to query.
     * 
     * @return {@code true} if the table must be expanded.
     */
    private boolean mustExpand(final int priority) {
        return priority >= table.length;
    }
    
    /**
     * Expands the internal table {@code table} such that it can accommodate the
     * priority key {@code priority}, while being smallest such table.
     * 
     * @param priority the requested priority key.
     */
    private void expand(final int priority) {
        final int nextCapacity = getNextCapacity(priority);
        this.table = Arrays.copyOf(table, nextCapacity);
    }
    
    /**
     * Returns the capacity that is sufficiently large in order to accommodate
     * the heap nodes with priority {@code priority}.
     * 
     * @param priority the requested priority.
     * 
     * @return the next capacity to expand with. 
     */
    private int getNextCapacity(final int priority) {
        int nextCapacity = table.length;
        
        while (nextCapacity <= priority) {
            nextCapacity *= 2;
        }
        
        return nextCapacity;
    }
    
    /**
     * Returns the maximum priority key in this heap.
     * 
     * @return the maximum priority key in this heap.
     */
    private int getMaximumPriority() {
        for (int priority = table.length - 1; priority >= 0; priority--) {
            if (table[priority] != null) {
                return priority;
            }
        }
        
        return -1;
    }
    
    /**
     * Makes sure that the input priority is non-negative.
     * 
     * @param priority the priority to check.
     * 
     * @throws IllegalArgumentException if the input priority is negative.
     */
    private void checkPriority(final int priority) {
        if (priority < 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "The input priority is negtive (%d).\n",
                            priority));
        }
    }
}
 