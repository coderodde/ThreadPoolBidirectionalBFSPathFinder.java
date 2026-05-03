package io.github.coderodde.graph.extra;

import io.github.coderodde.graph.pathfinding.delayed.impl.LongBinaryHeap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReferenceDijkstraPathFinder  {
    
    public List<DirectedGraphNode> 
        search(final DirectedGraphNode source, 
               final DirectedGraphNode target) {
            
        if (source.equals(target)) {
            return Arrays.asList(target);
        }
        
        final LongBinaryHeap<DirectedGraphNode> openA = new LongBinaryHeap<>();
        final LongBinaryHeap<DirectedGraphNode> openB = new LongBinaryHeap<>();
        
        final Set<DirectedGraphNode> closedA = new HashSet<>();
        final Set<DirectedGraphNode> closedB = new HashSet<>();
        
        final Map<DirectedGraphNode, DirectedGraphNode> parentMapA = 
                new HashMap<>();
        
        final Map<DirectedGraphNode, DirectedGraphNode> parentMapB = 
                new HashMap<>();
        
        final Map<DirectedGraphNode, Long> distanceMapA =
                new HashMap<>();
        
        final Map<DirectedGraphNode, Long> distanceMapB =
                new HashMap<>();
        
        openA.insert(source, 0L);
        openB.insert(target, 0L);
        
        parentMapA.put(source, null);
        parentMapB.put(target, null);
        
        distanceMapA.put(source, 0L);
        distanceMapB.put(target, 0L);
        
        // The cost of the best known shortest path candidate:
        long mu = Long.MAX_VALUE;
        DirectedGraphNode touchA = null;
        DirectedGraphNode touchB = null;
        
        while (!openA.isEmpty() && !openB.isEmpty()) {
            
            final DirectedGraphNode currentA = openA.top();
            final DirectedGraphNode currentB = openB.top();
            
            if (openA.size() + closedA.size() <= 
                openB.size() + closedB.size()) {
                
                openA.extract();
                closedA.add(currentA);
                
                final long beforeExpansionNanos = System.nanoTime();
                final List<DirectedGraphNode> children = currentA.getChildren();
                final long afterExpansionNanos = System.nanoTime();
                final long arcWeight = afterExpansionNanos
                                     - beforeExpansionNanos;
                
                for (final DirectedGraphNode child : children) {
                    if (closedA.contains(child)) {
                        continue;
                    }
                    
                    final long tentativeScore = distanceMapA.get(currentA)
                                              + arcWeight;
                    
                    boolean updated = false;
                    
                    if (!distanceMapA.containsKey(child)) {
                        updated = true;
                        distanceMapA.put(child, tentativeScore);
                        parentMapA.put(child, currentA);
                        openA.insert(child, tentativeScore);
                    } else if (distanceMapA.get(child) > tentativeScore) {
                        updated = true;
                        distanceMapA.put(child, tentativeScore);
                        parentMapA.put(child, currentA);
                        openA.changePriority(child, tentativeScore);
                    }
                    
                    if (updated && closedB.contains(child)) {
                        final long w = distanceMapA.get(currentA) 
                                     + arcWeight
                                     + distanceMapB.get(child);
                        
                        if (mu > w) {
                            mu = w;
                            touchA = currentA;
                            touchB = child;
                        }
                        
                    }
                }
            } else {
                openB.extract();
                closedB.add(currentB);
                
                final long beforeExpansionNanos = System.nanoTime();
                final List<DirectedGraphNode> parents = currentB.getParents();
                final long afterExpansionNanos = System.nanoTime();
                final long arcWeight = afterExpansionNanos
                                     - beforeExpansionNanos;
                
                for (final DirectedGraphNode parent : parents) {
                    if (closedB.contains(parent)) {
                        continue;
                    }
                    
                    final long tentativeScore = distanceMapB.get(currentB)
                                              + arcWeight;
                    
                    boolean updated = false;
                    
                    if (!distanceMapB.containsKey(parent)) {
                        updated = true;
                        distanceMapB.put(parent, tentativeScore);
                        parentMapB.put(parent, currentB);
                        openB.insert(parent, tentativeScore);
                    } else if (distanceMapB.get(parent) > tentativeScore) {
                        updated = true;
                        distanceMapB.put(parent, tentativeScore);
                        parentMapB.put(parent, currentB);
                        openB.changePriority(parent, tentativeScore);
                    }
                    
                    if (updated && closedA.contains(parent)) {
                        final long w = distanceMapA.get(parent)
                                     + arcWeight
                                     + distanceMapB.get(currentB);
                        
                        if (mu > w) {
                            mu = w;
                            touchA = parent;
                            touchB = currentB;
                        }
                    }
                }
            }
        
            if (distanceMapA.containsKey(currentA) && 
                distanceMapB.containsKey(currentB)) {
                
                final long score = distanceMapA.get(currentA)
                                 + distanceMapB.get(currentB);
                
                if (score > mu) {
                    return tracebackPath(touchA,
                                         touchB,
                                         parentMapA, 
                                         parentMapB);
                }
            }
        }
        
        return Arrays.asList();
    }
    
    private static List<DirectedGraphNode> tracebackPath(
        final DirectedGraphNode touchA,
        final DirectedGraphNode touchB,
        final Map<DirectedGraphNode, DirectedGraphNode> parentsA,
        final Map<DirectedGraphNode, DirectedGraphNode> parentsB) {
        List<DirectedGraphNode> path = new ArrayList<>();
        
        DirectedGraphNode current = touchA;
        
        while (current != null) {
            path.addLast(current);
            current = parentsA.get(current);
        }
        
        Collections.reverse(path);
        
        current = touchB;
        
        while (current != null) {
            path.addLast(current);
            current = parentsB.get(current);
        }
        
        return path;
    }
}