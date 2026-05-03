package io.github.coderodde.graph.pathfinding.delayed.impl;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

public class IntBinaryHeapTest {

    private LongBinaryHeap<Integer> heap;
    
    @Before
    public void before() {
        heap = new LongBinaryHeap<>();
    }
    
    @Test
    public void insert() {
        heap.insert(2, 2);
        heap.insert(1, 1);
        heap.insert(4, 4);
        heap.insert(3, 3);
        
        assertEquals(4, heap.size());
        
        assertTrue(heap.containsDatum(Integer.valueOf(1)));
        assertTrue(heap.containsDatum(Integer.valueOf(2)));
        assertTrue(heap.containsDatum(Integer.valueOf(3)));
        assertTrue(heap.containsDatum(Integer.valueOf(4)));
        
        assertFalse(heap.containsDatum(Integer.valueOf(0)));
        assertFalse(heap.containsDatum(Integer.valueOf(5)));
        
        heap.changePriority(4, 0);
        heap.changePriority(2, 5);
        
        assertEquals(Integer.valueOf(4), heap.extract());
        assertEquals(Integer.valueOf(1), heap.extract());
        assertEquals(Integer.valueOf(3), heap.extract());
        assertEquals(Integer.valueOf(2), heap.extract());
    }
}
