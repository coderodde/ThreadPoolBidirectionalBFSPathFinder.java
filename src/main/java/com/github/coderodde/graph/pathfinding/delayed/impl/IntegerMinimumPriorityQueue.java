package com.github.coderodde.graph.pathfinding.delayed.impl;

/**
 * This interface defines the API for minimum priority queues with integer 
 * priority keys.
 * 
 * @param <D> the data type of the satellite datums.
 * 
 * @version 1.0.0 (May 10, 2024)
 * @since 1.0.0 (May 10, 2024)
 */
public interface IntegerMinimumPriorityQueue<D> extends Iterable<D>, Cloneable {
    
    /**
     * Inserts a new datum {@code datum} to this heap with the given priority 
     * {@code priority}.
     * 
     * @param datum    the datum to insert.
     * @param priority the priority key to set.
     */
    public void insert(final D datum, final int priority);
    
    /**
     * Updates the priority of the satellite datum {@code datum} to 
     * {@code priority}. This method can handle both increasing and decreasing 
     * of the priority key.
     * 
     * @param datum    the datum of which priority to update.
     * @param priority the new priority of {@code datum}. 
     */
    public void updatePriority(final D datum, final int priority);
    
    /**
     * Returns the minimal priority throughout the contents of this heap. If 
     * this heap is empty, {@code -1} is returned.
     * 
     * @return the minimal priority.
     */
    public int minimumPriority();
    
    /**
     * Returns the datum with the lowest priority key, or {@code null} if this
     * heap is empty.
     * 
     * @return the datum with the lowest priority key, or {@code null} if this
     *         heap is empty.
     */
    public D minimumNode();

    /**
     * Returns {@code true} if the {@code datum} is stored in this heap.
     * 
     * @param datum the query datum.
     * 
     * @return {@code true} if the {@code datum} is stored in this heap.
     */
    public boolean containsDatum(final D datum);
    
    /**
     * Returns the current priority of the input datum.
     * 
     * @param datum the datum to query.
     * @return the current priority of {@code datum}.
     */
    public int getPriority(final D datum);
    
    /**
     * Removes and returns the datum with the lowest priority key, or 
     * {@code null} if this heap is empty.
     * 
     * @return the datum with the lowest priority key, or {@code null} if this 
     *         heap is empty.
     */
    public D extractMinimum();
    
    /**
     * Removes the datum {@code datum} from this heap.
     * 
     * @param datum to remove.
     */
    public void remove(final D datum);
    
    /**
     * Clears all the data from this heap.
     */
    public void clear();
    
    /**
     * Since the heap cannot contract the collision chain table, the remedy to 
     * do that is to clone it which will return another heap with the same 
     * content, but with the table as small as is necessary to accommodate also
     * the maximum priority nodes.
     * 
     * @return the clone of this heap.
     */
    public Object clone();
    
    /**
     * Returns the number of datums stored in this heap.
     * 
     * @return the number of datums stored in this heap.
     */
    public int size();
    
    /**
     * Returns {@code true} if this heap is empty.
     * 
     * @return {@code true} if this heap is empty.
     */
    public boolean isEmpty();
}
