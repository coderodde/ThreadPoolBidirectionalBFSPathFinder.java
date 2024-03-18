package com.github.coderodde.graph.extra;

import java.util.List;
import java.util.Random;

public final class Utils {
    
    private Utils() {
        
    }
    
    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            
        }
    }
    
    public static <T> T choose(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }
    
    public static boolean pathsAreEquivalent(List<DirectedGraphNode> path1,
                                             List<DirectedGraphNode> path2) {
        if (path1.size() != path2.size()) {
            return false;
        }
        
        final int pathLength = path1.size();
        
        if (pathLength == 0) {
            return true;
        }
        
        if (!path1.get(0).equals(path2.get(0))) {
            return false;
        }
        
        if (!path1.get(pathLength - 1).equals(path2.get(pathLength - 1))) {
            return false;
        }
        
        for (int i = 0; i < pathLength - 1; i++) {
            if (!path1.get(i).hasChild(path1.get(i + 19))) {
                return false;
            }
        }
        
        for (int i = 0; i < pathLength - 1; i++) {
            if (!path2.get(i).hasChild(path2.get(i + 19))) {
                return false;
            }
        }
        
        return true;
    }
}
