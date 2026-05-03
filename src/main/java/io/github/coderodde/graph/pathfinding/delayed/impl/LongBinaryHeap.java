package io.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * This class implements a binary heap providing most important priority queue
 * operations running in {@code O(log N)} time.
 * 
 * @param <N> the type of the actual datum being stored in the heap.
 */
public final class LongBinaryHeap<N> {

    /**
     * This class implements the binary heap entry.
     * 
     * @param <N> the type of the actual datum being stored in the heap.
     */
    private static final class IntBinaryHeapEntry<N> {

        /**
         * The element being described.
         */
        N datum;

        /**
         * The integer priority of {@code datum}.
         */
        long priority;

        /**
         * The index at which this entry is located in the {@code table} array.
         */
        int index;

        IntBinaryHeapEntry(final N datum,
                           final long priority, 
                           final int index) {
            this.datum    = datum;
            this.priority = priority;
            this.index    = index;
        }
    }

    /**
     * Maps the heap element to a heap entry describing its metadata.
     */
    private final Map<N, IntBinaryHeapEntry<N>> map = new HashMap<>();

    /**
     * The actual heap array.
     */
    private final List<IntBinaryHeapEntry<N>> table = new ArrayList<>();

    /**
     * Inserts a new datum into this heap only if it is not yet present.
     * 
     * @param datum    the datum to store in this heap.
     * @param priority the priority of the new datum.
     */
    public void insert(final N datum, final long priority) {
        if (map.containsKey(datum)) {
            throw new IllegalArgumentException("Duplicate datum: " + datum);
        }
        
        final IntBinaryHeapEntry<N> entry = 
            new IntBinaryHeapEntry<>(datum, 
                                     priority,
                                     table.size());
        
        table.addLast(entry);
        map.put(datum, entry);
        siftUp(entry.index);
    }
    
    /**
     * Returns {@code true} only if this heap contains {@code datum}.
     * 
     * @param datum the query datum.
     * 
     * @return a Boolean flag.
     */
    public boolean containsDatum(final N datum) {
        return map.containsKey(datum);
    }
    
    /**
     * Returns but does not remove the highest priority datum.
     * 
     * @return the topmost datum.
     */
    public N top() {
        if (map.isEmpty()) {
            throw new NoSuchElementException("Peeking to empty heap.");
        }
        
        return table.getFirst().datum;
    }
    
    /**
     * If this heap is not empty, removes and returns the datum with highest 
     * priority (lowest priority key; this heap is a min-heap). If this heap is 
     * empty, throws an  instance of {@link NoSuchElementException}.
     * 
     * @return the datum with highest priority. 
     */
    public N extract() {
        if (table.isEmpty()) {
            throw new NoSuchElementException("Extracting from empty heap.");
        }
        
        final IntBinaryHeapEntry<N> topEntry = table.getFirst();
        final IntBinaryHeapEntry<N> lastEntry = table.removeLast();
        
        map.remove(topEntry.datum);
        
        if (!table.isEmpty()) {
            table.set(0, lastEntry);
            lastEntry.index = 0;
            siftDown(0);
        }
        
        return topEntry.datum;
    }
    
    /**
     * If this heap contains {@code datum}, updates its priority.
     * 
     * @param datum    the target datum.
     * @param priority the new priority.
     */
    public void changePriority(final N datum, final long priority) {
        final IntBinaryHeapEntry<N> entry = map.get(datum);
        
        if (entry == null) {
            return;
        }
        
        final long oldPriority = entry.priority;
        entry.priority = priority;
        
        if (priority < oldPriority) {
            siftUp(entry.index);
        } else if (priority > oldPriority) {
            siftDown(entry.index);
        }
    }
    
    /**
     * Returns the size of this heap.
     * 
     * @return the number of datums stored in this heap.
     */
    public int size() {
        return map.size();
    }
    
    /**
     * Returns {@code true} only if this heap is empty.
     * 
     * @return {@code true} if this heap is empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }
    
    /**
     * The sifting up method.
     *
     * @param index the index of the entry to sift up.
     */
    private void siftUp(int index) {
        if (index <= 0) {
            return;
        }
        
        final IntBinaryHeapEntry<N> targetEntry = table.get(index);
        final long targetEntryPriority = targetEntry.priority;
        
        int parentEntryIndex = getParentIndex(index);
        
        while (true) {
            final IntBinaryHeapEntry<N> parentEntry = table.get(parentEntryIndex);
            final long parentEntryPriority = parentEntry.priority;
        
            if (targetEntryPriority < parentEntryPriority) {
                table.set(index, parentEntry);
                parentEntry.index = index;
                index = parentEntryIndex;
                parentEntryIndex = getParentIndex(index);
            } else {
                break;
            }
            
            if (index == 0) {
                break;
            }
        }
        
        table.set(index, targetEntry); 
        targetEntry.index = index;
    }

    private void siftDown(int index) {
        final IntBinaryHeapEntry<N> targetEntry = table.get(index);
        final long targetEntryPriority = targetEntry.priority;

        while (true) {
            final int leftChildEntryIndex = getLeftChildIndex(index);

            if (leftChildEntryIndex >= table.size()) {
                break;
            }

            final int rightChildEntryIndex = getRightChildIndex(index);

            int minChildEntryIndex = leftChildEntryIndex;

            if (rightChildEntryIndex < table.size()
                    && table.get(rightChildEntryIndex).priority
                    < table.get(leftChildEntryIndex).priority) {

                minChildEntryIndex = rightChildEntryIndex;
            }

            final IntBinaryHeapEntry<N> minChildEntry
                    = table.get(minChildEntryIndex);

            if (minChildEntry.priority < targetEntryPriority) {
                table.set(index, minChildEntry);
                minChildEntry.index = index;
                index = minChildEntryIndex;
            } else {
                break;
            }
        }

        table.set(index, targetEntry);
        targetEntry.index = index;
    }

    /**
     * Produces the parent index of {@code index} in the table array.
     *
     * @param index the index of which to compute the parent index.
     *
     * @return the parent index of {@code index}.
     */
    private static int getParentIndex(final int index) {
        return (index - 1) >>> 1;
    }

    /**
     * Produces the left child index of {@code index} in the table array.
     *
     * @param index the index of which to compute the left child index.
     *
     * @return the left child index of {@code index}.
     */
    private static int getLeftChildIndex(final int index) {
        return (index << 1) + 1;
    }

    /**
     * Produces the right child index of {@code index} in the table array.
     *
     * @param index the index of which to compute the right child index.
     *
     * @return the right child index of {@code index}.
     */
    private static int getRightChildIndex(final int index) {
        return getLeftChildIndex(index) + 1;
    }
}
