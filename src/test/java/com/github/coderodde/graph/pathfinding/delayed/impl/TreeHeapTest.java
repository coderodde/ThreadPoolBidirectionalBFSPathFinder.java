package com.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;

public class TreeHeapTest {
    
    @Test
    public void insertUpdateIterate() {
        TreeHeap<Integer> heap = new TreeHeap<>();
        
        heap.insert(1, 1);
        heap.insert(2, 2);
        heap.insert(3, 3);
        heap.insert(4, 4);
        
        heap.update(1, 5);
        heap.update(4, 0);
        
        Iterator<Integer> iter = heap.iterator();
        
        assertEquals(Integer.valueOf(4), iter.next());
        assertEquals(Integer.valueOf(2), iter.next());
        assertEquals(Integer.valueOf(3), iter.next());
        assertEquals(Integer.valueOf(1), iter.next());
        
        assertFalse(iter.hasNext());
    }
}
