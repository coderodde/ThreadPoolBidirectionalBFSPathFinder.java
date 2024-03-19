package com.github.coderodde.graph.extra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class ReferencePathFinder  {

    private int numberOfExpandedNodes;
    
    public List<DirectedGraphNode> 
        search(final DirectedGraphNode source, 
               final DirectedGraphNode target) {
            
        numberOfExpandedNodes = 0;
            
        if (source.equals(target)) {
            return Arrays.asList(target);
        }
        
        final Queue<DirectedGraphNode> queueA = new ArrayDeque<>();
        final Queue<DirectedGraphNode> queueB = new ArrayDeque<>();
        
        final Map<DirectedGraphNode, DirectedGraphNode> parentMapA = 
                new HashMap<>();
        
        final Map<DirectedGraphNode, DirectedGraphNode> parentMapB = 
                new HashMap<>();
        
        final Map<DirectedGraphNode, Integer> distanceMapA =
                new HashMap<>();
        
        final Map<DirectedGraphNode, Integer> distanceMapB =
                new HashMap<>();
        
        int expandedNodes = 0;
        
        queueA.add(source);
        queueB.add(target);
        
        parentMapA.put(source, null);
        parentMapB.put(target, null);
        
        distanceMapA.put(source, 0);
        distanceMapB.put(target, 0);
        
        int bestCost = Integer.MAX_VALUE;
        DirectedGraphNode touchNode = null;
        
        while (!queueA.isEmpty() && !queueB.isEmpty()) {
            final int distanceA = distanceMapA.get(queueA.peek());
            final int distanceB = distanceMapB.get(queueB.peek());
            
            if (touchNode != null && bestCost < distanceA + distanceB) {
                return tracebackPath(touchNode, parentMapA, parentMapB);
            }
            
            if (distanceA < distanceB) {
                // Trivial load balancing.
                final DirectedGraphNode current = queueA.poll();
                
                numberOfExpandedNodes++;
                
                if (distanceMapB.containsKey(current) 
                        && bestCost > distanceMapA.get(current) +
                                      distanceMapB.get(current)) {
                    
                    bestCost = distanceMapA.get(current) + 
                               distanceMapB.get(current);
                    
                    touchNode = current;
                }
                
                for (final DirectedGraphNode child : current.getChildren()) {
                    if (!distanceMapA.containsKey(child)) {
                        distanceMapA.put(child, distanceMapA.get(current) + 1);
                        parentMapA.put(child, current);
                        queueA.add(child);
                    }
                }
            } else {
                final DirectedGraphNode current = queueB.poll();
                
                numberOfExpandedNodes++;
                
                if (distanceMapA.containsKey(current) 
                        && bestCost > distanceMapA.get(current) +
                                      distanceMapB.get(current)) {
                    
                    bestCost = distanceMapA.get(current) + 
                               distanceMapB.get(current);
                    
                    touchNode = current;
                }
                
                for (final DirectedGraphNode parent : current.getParents()) {
                    if (!distanceMapB.containsKey(parent)) {
                        distanceMapB.put(parent, distanceMapB.get(current) + 1);
                        parentMapB.put(parent, current);
                        queueB.add(parent);
                    }
                }
            }
        }
        
        return Arrays.asList();
    }
        
    public int getNumberOfExpandedNodes() {
        return numberOfExpandedNodes;
    }
        
    private static <N> List<N> tracebackPath(N touchNode, 
                                             Map<N, N> forwardParentMap,
                                             Map<N, N> backwardParentMap) {
        final List<N> path = new ArrayList<>();

        N current = touchNode;

        while (current != null) {
            path.add(current);
            current = forwardParentMap.get(current);
        }

        Collections.<String>reverse(path);
        current = backwardParentMap.get(touchNode);

        while (current != null) {
            path.add(current);
            current = backwardParentMap.get(current);
        }
        
        return path;
    }
}