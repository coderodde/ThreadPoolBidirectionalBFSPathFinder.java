package com.github.coderodde.graph.pathfinding.delayed.impl;

import com.github.coderodde.graph.extra.BackwardNodeExpander;
import com.github.coderodde.graph.extra.DirectedGraphBuilder;
import com.github.coderodde.graph.extra.DirectedGraphNode;
import com.github.coderodde.graph.extra.ForwardNodeExpander;
import com.github.coderodde.graph.extra.GraphPair;
import com.github.coderodde.graph.extra.ReferencePathFinder;
import com.github.coderodde.graph.extra.Utils;
import com.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ThreadPoolBidirectionalBFSPathFinderTest {
    
    private static final long SEED = 13L;
    private static final int NODES = 10_000;
    private static final int DISCONNECTED_GRAPH_NODES = 1000;
    private static final int MINIMUM_DISCONNECTED_GRAPH_DEGREE = 2;
    private static final int MAXIMUM_DISCONNECTED_GRAPH_DEGREE = 5;
    private static final int MINIMUM_DEGREE = 4;
    private static final int MAXIMUM_DEGREE = 6;
    private static final int MINIMUM_DELAY = 3;
    private static final int MAXIMUM_DELAY = 40;
    private static final int REQUESTED_NUMBER_OF_THREADS = 8;
    private static final int MASTER_THREAD_SLEEP_DURATION = 20;
    private static final int SLAVE_THREAD_SLEEP_DURATION = 10;
    private static final int MASTER_THREAD_TRIALS = 30;
    private static final int EXPANSION_JOIN_DURATION_MILLIS = 200;
    private static final int LOCK_WAIT_DURATION_MILLIS = 1;
    
    private final List<DirectedGraphNode> delayedDirectedGraph;
    private final List<DirectedGraphNode> nondelayedDirectedGraph;
    
    private final List<DirectedGraphNode> disconnectedDelayedDirectedGraph;
    private final List<DirectedGraphNode> disconnectedNondelayedDirectedGraph;
    
    private final List<DirectedGraphNode> failingNodeGraph;
    
    private final Random random = new Random(SEED);
    private final AbstractDelayedGraphPathFinder<DirectedGraphNode> 
            testPathFinder = 
                ThreadPoolBidirectionalBFSPathFinderBuilder
                .<DirectedGraphNode>begin()
                .withNumberOfRequestedThreads(REQUESTED_NUMBER_OF_THREADS)
                .withMasterThreadSleepDurationMillis(MASTER_THREAD_SLEEP_DURATION)
                .withSlaveThreadSleepDurationMillis(SLAVE_THREAD_SLEEP_DURATION)
                .withNumberOfMasterTrials(MASTER_THREAD_TRIALS)
                .withJoinDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)
                .withLockWaitMillis(LOCK_WAIT_DURATION_MILLIS)
                .end();
    
    private final ReferencePathFinder referencePathFinder =
            new ReferencePathFinder();
    
    public ThreadPoolBidirectionalBFSPathFinderTest() {
        final DirectedGraphBuilder directedGraphBuilder = 
                new DirectedGraphBuilder(
                        NODES, 
                        MINIMUM_DEGREE, 
                        MAXIMUM_DEGREE,
                        MINIMUM_DELAY, 
                        MAXIMUM_DELAY, 
                        random);
        
        final DirectedGraphBuilder disconnectedGraphBuilder =
                new DirectedGraphBuilder(
                        DISCONNECTED_GRAPH_NODES,
                        MINIMUM_DISCONNECTED_GRAPH_DEGREE,
                        MAXIMUM_DISCONNECTED_GRAPH_DEGREE,
                        MINIMUM_DELAY,
                        MAXIMUM_DELAY,
                        random);
        
        final GraphPair graphPair = 
                directedGraphBuilder.getConnectedGraphPair();
        
        final GraphPair disconnectedGraphPair =
                disconnectedGraphBuilder.getDisconnectedGraphPair();
        
        this.delayedDirectedGraph = graphPair.delayedGraph;
        this.nondelayedDirectedGraph = graphPair.nondelayedGraph;
        
        this.disconnectedDelayedDirectedGraph =
                disconnectedGraphPair.delayedGraph;
        
        this.disconnectedNondelayedDirectedGraph =
                disconnectedGraphPair.nondelayedGraph;
        
        this.failingNodeGraph = directedGraphBuilder.getFailingGraph();
    }
    
    @Test
    public void testCorrectnessOnSmallGraph() {
        final DirectedGraphNode nodeA  = new DirectedGraphNode(1);
        final DirectedGraphNode nodeB1 = new DirectedGraphNode(2);
        final DirectedGraphNode nodeB2 = new DirectedGraphNode(3);
        final DirectedGraphNode nodeC  = new DirectedGraphNode(4);
        
        nodeA.addChild(nodeB1);
        nodeA.addChild(nodeB2);
        nodeB1.addChild(nodeC);
        nodeB2.addChild(nodeC);
        
        final List<DirectedGraphNode> path = 
                testPathFinder.search(
                        nodeA,
                        nodeC,
                        new ForwardNodeExpander(),
                        new BackwardNodeExpander(), 
                        null, 
                        null, 
                        null);
        
        assertEquals(3, path.size());
        System.out.println("testCorrectnessOnSmallGraph() done.");
    }
                        
    
    // This test may take a several seconds.
    @Test
    public void testCorrectness() {
        final int sourceNodeIndex = 
                random.nextInt(delayedDirectedGraph.size());

        final int targetNodeIndex = 
                random.nextInt(delayedDirectedGraph.size());

        final DirectedGraphNode nondelayedGraphSource =
                nondelayedDirectedGraph.get(sourceNodeIndex);

        final DirectedGraphNode nondelayedGraphTarget =
                nondelayedDirectedGraph.get(targetNodeIndex);

        final DirectedGraphNode delayedGraphSource =
                delayedDirectedGraph.get(sourceNodeIndex);

        final DirectedGraphNode delayedGraphTarget =
                delayedDirectedGraph.get(targetNodeIndex);

        final List<DirectedGraphNode> testPath = 
                testPathFinder
                        .search(delayedGraphSource,
                                delayedGraphTarget, 
                                new ForwardNodeExpander(),
                                new BackwardNodeExpander(),
                                null,
                                null,
                                null);

        final List<DirectedGraphNode> referencePath = 
                referencePathFinder
                        .search(nondelayedGraphSource, nondelayedGraphTarget);

        assertEquals(referencePath.size(), testPath.size());
        assertEquals(referencePath.get(0), testPath.get(0));
        assertEquals(referencePath.get(referencePath.size() - 1),
                     testPath.get(testPath.size() - 1));
        
        System.out.println("testCorrectness() done.");
    }   
    
    // This test may take a several seconds too complete.
    @Test
    public void returnsEmptyPathOnDisconnectedGraph() {
        final int nodes = disconnectedDelayedDirectedGraph.size();
        final int sourceNodeIndex = random.nextInt(nodes / 2);
        final int targetNodeIndex = nodes / 2 + random.nextInt(nodes / 2);
        
        final DirectedGraphNode nondelayedGraphSource =
                    disconnectedNondelayedDirectedGraph.get(sourceNodeIndex);

        final DirectedGraphNode nondelayedGraphTarget =
                disconnectedNondelayedDirectedGraph.get(targetNodeIndex);

        final DirectedGraphNode delayedGraphSource =
                disconnectedDelayedDirectedGraph.get(sourceNodeIndex);

        final DirectedGraphNode delayedGraphTarget =
                disconnectedDelayedDirectedGraph.get(targetNodeIndex);

        final List<DirectedGraphNode> testPath = 
                testPathFinder
                        .search(delayedGraphSource,
                                delayedGraphTarget, 
                                new ForwardNodeExpander(),
                                new BackwardNodeExpander(),
                                null,
                                null,
                                null);

        final List<DirectedGraphNode> referencePath = 
                referencePathFinder
                        .search(nondelayedGraphSource, nondelayedGraphTarget);

        assertTrue(referencePath.isEmpty());
        assertTrue(testPath.isEmpty());
        
        System.out.println("returnsEmptyPathOnDisconnectedGraph() done.");
    }
    
    @Test
    public void haltsOnFailingNodes() {
        
        final DirectedGraphNode sourceNode = 
                this.failingNodeGraph
                    .get(random.nextInt(this.failingNodeGraph.size()));
        
        final DirectedGraphNode targetNode = 
                this.failingNodeGraph
                    .get(random.nextInt(this.failingNodeGraph.size()));
        
        testPathFinder.search(sourceNode, 
                              targetNode,
                              new FailingForwardNodeExpander(), 
                              new FailingBackwardNodeExpander(), 
                              null, 
                              null, 
                              null);
        
        System.out.println("haltsOnFailingNodes() done.");
    }
    
    @Test
    public void omitsFaultyLinks() {
        final DirectedGraphNode a  = new DirectedGraphNode(1, true, 100);
        final DirectedGraphNode b1 = new DirectedGraphNode(2, true, 100);
        final DirectedGraphNode b2 = new DirectedGraphNode(3, true, 100);
        final DirectedGraphNode b3 = new DirectedGraphNode(4, true, 100);
        final DirectedGraphNode c1 = new DirectedGraphNode(5, true, 10_000);
        final DirectedGraphNode c2 = new DirectedGraphNode(6, true, 10_000);
        final DirectedGraphNode d  = new DirectedGraphNode(7, true, 100);
        
        a.addChild(b1);
        b1.addChild(b2);
        b2.addChild(b3);
        b3.addChild(d);
        
        a.addChild(c1);
        c1.addChild(c2);
        c2.addChild(d);
        
        final List<DirectedGraphNode> path = 
                testPathFinder.search(
                        a, 
                        d, 
                        new ForwardNodeExpander(), 
                        new BackwardNodeExpander(), 
                        null, 
                        null, 
                        null);
        
        assertEquals(5, path.size());
        
        assertEquals(a,  path.get(0));
        assertEquals(b1, path.get(1));
        assertEquals(b2, path.get(2));
        assertEquals(b3, path.get(3));
        assertEquals(d,  path.get(4));
        
        System.out.println("omitsFaultyLinks() done.");
    }
}

final class FailingForwardNodeExpander
        extends AbstractNodeExpander<DirectedGraphNode> {

    @Override
    public List<DirectedGraphNode> generateSuccessors(final DirectedGraphNode node) {
        Utils.sleep(1_000_000);
        return node.getChildren();
    }

    @Override
    public boolean isValidNode(final DirectedGraphNode node) {
        return true;
    }
}

final class FailingBackwardNodeExpander
        extends AbstractNodeExpander<DirectedGraphNode> {

    @Override
    public List<DirectedGraphNode>
         generateSuccessors(final DirectedGraphNode node) {
        Utils.sleep(1_000_000);
        return node.getParents();
    }

    @Override
    public boolean isValidNode(final DirectedGraphNode node) {
        return true;
    }
}
