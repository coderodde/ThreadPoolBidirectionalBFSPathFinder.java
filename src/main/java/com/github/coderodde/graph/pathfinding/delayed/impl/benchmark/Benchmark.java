package com.github.coderodde.graph.pathfinding.delayed.impl.benchmark;

import com.github.coderodde.graph.extra.BackwardNodeExpander;
import com.github.coderodde.graph.extra.DirectedGraphBuilder;
import com.github.coderodde.graph.extra.DirectedGraphNode;
import com.github.coderodde.graph.extra.ForwardNodeExpander;
import com.github.coderodde.graph.extra.GraphPair;
import com.github.coderodde.graph.extra.ReferencePathFinder;
import com.github.coderodde.graph.extra.Utils;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderBuilder;
import java.util.List;
import java.util.Random;

public final class Benchmark {
    
    private static final int NODES = 100_000;
    private static final int MINIMUM_NODE_DEGREE = 3;
    private static final int MAXIMUM_NODE_DEGREE = 10;
    private static final int MINIMUM_DELAY = 10;
    private static final int MAXIMUM_DELAY = 70;
    
    public static void main(String[] args) {
        Random random = new Random();
        GraphPair graphPair = 
                new DirectedGraphBuilder(
                        NODES, 
                        MINIMUM_NODE_DEGREE, 
                        MAXIMUM_NODE_DEGREE, 
                        MINIMUM_DELAY, 
                        MAXIMUM_DELAY, 
                        random)
                        .getConnectedGraphPair();
        
        DirectedGraphNode source = Utils.choose(graphPair.delayedGraph, random);
        DirectedGraphNode target = Utils.choose(graphPair.delayedGraph, random);
        
        System.out.printf("Source node: %s\n", source);
        System.out.printf("Target node: %s\n", target);
        
        long startTime = System.currentTimeMillis();
        
        List<DirectedGraphNode> referencePath = 
                new ReferencePathFinder()
                .search(source, target);
        
        long endTime = System.currentTimeMillis();
        
        System.out.printf("ReferencePathFinder in %d milliseconds.\n", 
                          endTime - startTime);
        
        ThreadPoolBidirectionalBFSPathFinder<DirectedGraphNode>
                threadPoolPathFinder = 
                ThreadPoolBidirectionalBFSPathFinderBuilder
                        .<DirectedGraphNode>begin()
                        .withJoinDurationMillis(110)
                        .withLockWaitMillis(4)
                        .withMasterThreadSleepDurationMillis(100)
                        .withNumberOfMasterTrials(50)
                        .withNumberOfRequestedThreads(128)
                        .withSlaveThreadSleepDurationMillis(200)
                        .end();
        
        startTime = System.currentTimeMillis();
        
        List<DirectedGraphNode> threadPoolFinderPath = 
                threadPoolPathFinder.search(source,
                                    target, 
                                    new ForwardNodeExpander(),
                                    new BackwardNodeExpander(),
                                    null,
                                    null,
                                    null);
        
        endTime = System.currentTimeMillis();
        
        System.out.printf(
                "ThreadPoolBidirectionalBFSPathFinder in %d milliseconds.\n", 
                endTime - startTime);
        
        boolean eq = Utils.pathsAreEquivalent(referencePath,
                                              threadPoolFinderPath);
        
        System.out.printf("Paths are equivalent: %b.\n", eq);
        
        String format1 =
                "%" + Integer.toString(referencePath.size()).length()
                + "d: %d\n";

        String format2 =
                "%" 
                + Integer.toString(threadPoolFinderPath.size()).length()
                + "d: %d\n";

        System.out.println("Reference finder path:");
        int lineNumber = 1;

        for (int i = 0; i < referencePath.size(); i++) {
            System.out.printf(format1, 
                              lineNumber++, 
                              referencePath.get(i).getId());
        }

        System.out.println();
        System.out.println("Thread pool finder path:");

        lineNumber = 1;

        for (int i = 0; i < threadPoolFinderPath.size(); i++) {
            System.out.printf(format2,
                              lineNumber++, 
                              threadPoolFinderPath.get(i).getId());
        }
    }
}
