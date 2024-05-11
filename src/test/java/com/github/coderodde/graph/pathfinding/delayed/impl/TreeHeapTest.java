package com.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;

public class TreeHeapTest {
    
    @Test
    public void all() {
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
        
        assertEquals(Integer.valueOf(4), heap.minimumNode());
        assertEquals(0, heap.minimumPriority());
        
        assertEquals(Integer.valueOf(4), heap.extractMinimum());
        assertEquals(3, heap.size());
        
        assertEquals(Integer.valueOf(2), heap.extractMinimum());
        assertEquals(2, heap.size());
        
        assertEquals(Integer.valueOf(3), heap.extractMinimum());
        assertEquals(1, heap.size());
        
        assertEquals(Integer.valueOf(1), heap.extractMinimum());
        assertEquals(0, heap.size());
    }
    
    @Test
    public void remove() {
        TreeHeap<String> heap = new TreeHeap<>();
        
        heap.insert("a",  1);
        heap.insert("b",  2);
        heap.insert("c",  3);
        heap.insert("c_", 3);
        
        assertEquals("a",  heap.extractMinimum());
        assertEquals("b",  heap.extractMinimum());
        assertEquals("c_", heap.extractMinimum());
        assertEquals("c",  heap.extractMinimum());
        
        assertEquals(0, heap.size());
    }
}
