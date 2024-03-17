package com.github.coderodde.graph.extra;

public final class Utils {
    
    private Utils() {
        
    }
    
    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            
        }
    }
}
