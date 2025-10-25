package io.github.coderodde.graph.pathfinding.delayed.impl.benchmark;

import io.github.coderodde.graph.extra.BackwardNodeExpander;
import io.github.coderodde.graph.extra.DirectedGraphBuilder;
import io.github.coderodde.graph.extra.DirectedGraphNode;
import io.github.coderodde.graph.extra.ForwardNodeExpander;
import io.github.coderodde.graph.extra.GraphPair;
import io.github.coderodde.graph.extra.ReferencePathFinder;
import io.github.coderodde.graph.extra.Utils;
import io.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderBuilder;
import java.util.List;
import java.util.Random;

public final class Benchmark {
    
    private static final int NODES = 100_000;
    private static final int MINIMUM_NODE_DEGREE = 3;
    private static final int MAXIMUM_NODE_DEGREE = 7;
    private static final int MINIMUM_DELAY = 10;
    private static final int MAXIMUM_DELAY = 70;
    private static final int NUMBER_OF_THREADS = 313;
    
    public static void main(String[] args) {
        // 1710824771814L takes long.
        long seed = System.currentTimeMillis();
        seed = 1710824771814L;
        Random random = new Random(seed);
        
        System.out.printf("Random seed = %d.\n", seed);
        
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
        
        ReferencePathFinder referencePathFinder = new ReferencePathFinder();
        
        long startTime = System.currentTimeMillis();
        
        List<DirectedGraphNode> referencePath = 
                referencePathFinder
                .search(source, target);
        
        long endTime = System.currentTimeMillis();
        
        System.out.printf("ReferencePathFinder in %d milliseconds, " + 
                          "expanded %d nodes.\n", 
                          endTime - startTime,
                          referencePathFinder.getNumberOfExpandedNodes());
        
        ThreadPoolBidirectionalBFSPathFinder<DirectedGraphNode>
                threadPoolPathFinder = 
                ThreadPoolBidirectionalBFSPathFinderBuilder
                        .<DirectedGraphNode>begin()
                        .withExpansionDurationMillis(110)
                        .withLockWaitMillis(4)
                        .withMasterThreadSleepDurationMillis(100)
                        .withNumberOfMasterTrials(50)
                        .withNumberOfRequestedThreads(NUMBER_OF_THREADS)
                        .withSlaveThreadSleepDurationMillis(10)
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
                "ThreadPoolBidirectionalBFSPathFinder in %d milliseconds, " + 
                "expanded %d nodes.\n", 
                endTime - startTime,
                threadPoolPathFinder.getNumberOfExpandedNodes());
        
        boolean eq = Utils.pathsAreEquivalent(referencePath,
                                              threadPoolFinderPath);
        
        String format1 =
                "%" + Integer.toString(referencePath.size()).length()
                + "d: %d\n";

        String format2 =
                "%" 
                + Integer.toString(threadPoolFinderPath.size()).length()
                + "d: %d\n";

        System.out.println();
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
        
        System.out.println();
        System.out.printf("Paths are equivalent: %b.\n", eq);
    }
}
