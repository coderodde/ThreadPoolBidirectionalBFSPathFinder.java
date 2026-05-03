package io.github.coderodde.graph.pathfinding.delayed.impl;

import io.github.coderodde.graph.extra.BackwardNodeExpander;
import io.github.coderodde.graph.extra.DirectedGraphBuilder;
import io.github.coderodde.graph.extra.DirectedGraphNode;
import io.github.coderodde.graph.extra.ForwardNodeExpander;
import io.github.coderodde.graph.extra.GraphPair;
import io.github.coderodde.graph.extra.ReferenceDijkstraPathFinder;
import io.github.coderodde.graph.extra.Utils;
import io.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ThreadPoolBidirectionalDijkstraPathFinderTest {
    
    private static final long SEED = 13L;
    private static final int NODES = 100_000;
    private static final int DISCONNECTED_GRAPH_NODES = 1000;
    private static final int MINIMUM_DISCONNECTED_GRAPH_DEGREE = 2;
    private static final int MAXIMUM_DISCONNECTED_GRAPH_DEGREE = 5;
    private static final int MINIMUM_DEGREE = 4;
    private static final int MAXIMUM_DEGREE = 6;
    private static final int MINIMUM_DELAY = 3;
    private static final int MAXIMUM_DELAY = 40;
    private static final int REQUESTED_NUMBER_OF_THREADS_FORWARD  = 32;
    private static final int REQUESTED_NUMBER_OF_THREADS_BACKWARD = 32;
    private static final int MASTER_THREAD_SLEEP_DURATION_MILLIS = 20;
    private static final int SLAVE_THREAD_SLEEP_DURATION_MILLIS = 10;
    private static final int MASTER_THREAD_TRIALS = 30;
    private static final int EXPANSION_JOIN_DURATION_MILLIS = 200;
    
    private final List<DirectedGraphNode> delayedDirectedGraph;
    
    private final List<DirectedGraphNode> disconnectedDelayedDirectedGraph;
    private final List<DirectedGraphNode> disconnectedNondelayedDirectedGraph;
    
    private final List<DirectedGraphNode> failingNodeGraph;
    
    private final Random random = new Random(SEED);
    private final AbstractDelayedGraphPathFinder<DirectedGraphNode> 
            testPathFinder = 
                ThreadPoolBidirectionalPathFinderBuilder
                .<DirectedGraphNode>begin()
                .withNumberOfForwardThreads(REQUESTED_NUMBER_OF_THREADS_FORWARD)
                .withNumberOfBackwardThreads(
                        REQUESTED_NUMBER_OF_THREADS_BACKWARD)
                .withMasterThreadSleepDurationMillis(
                        MASTER_THREAD_SLEEP_DURATION_MILLIS)
                .withSlaveThreadSleepDurationMillis(
                        SLAVE_THREAD_SLEEP_DURATION_MILLIS)
                .withNumberOfMasterTrials(MASTER_THREAD_TRIALS)
                .withExpansionDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)
                .dijkstra();
    
    private final ReferenceDijkstraPathFinder referencePathFinder =
              new ReferenceDijkstraPathFinder();
    
    public ThreadPoolBidirectionalDijkstraPathFinderTest() {
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
        
        this.disconnectedDelayedDirectedGraph =
                disconnectedGraphPair.delayedGraph;
        
        this.disconnectedNondelayedDirectedGraph =
                disconnectedGraphPair.nondelayedGraph;
        
        this.failingNodeGraph = directedGraphBuilder.getFailingGraph();
    }
    
    @Test
    public void isNotShortestPathAlgo() {
        System.out.println("--- Begin: isNotShortestPathAlgo()");
        
        final DirectedGraphNode s = new DirectedGraphNode(0, 500);
        final DirectedGraphNode a = new DirectedGraphNode(1, 5);
        final DirectedGraphNode b = new DirectedGraphNode(2, 15);
        final DirectedGraphNode c = new DirectedGraphNode(3, 10);
        final DirectedGraphNode d = new DirectedGraphNode(4, 1000);
        final DirectedGraphNode t = new DirectedGraphNode(5, 3);
        
        s.addChild(a);
        a.addChild(b);
        b.addChild(c);
        c.addChild(t);
        
        s.addChild(d);
        d.addChild(t);
        
        List<DirectedGraphNode> path = 
                testPathFinder.search(s, 
                                      t,
                                      new ForwardNodeExpander(), 
                                      new BackwardNodeExpander(), 
                                      null,
                                      null, 
                                      null);
    
        assertEquals(5, path.size());
        assertEquals(s, path.get(0));
        assertEquals(a, path.get(1));
        assertEquals(b, path.get(2));
        assertEquals(c, path.get(3));
        assertEquals(t, path.get(4));
        
        System.out.println("--- End:   isNotShortestPathAlgo()");
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
        
        System.out.println("testCorrectness() begin:");
        System.out.println(
                "(This test may fail depending on timing of the finder.)");
        
        final int sourceNodeIndex = 
                random.nextInt(delayedDirectedGraph.size());

        final int targetNodeIndex = 
                random.nextInt(delayedDirectedGraph.size());

        final DirectedGraphNode delayedGraphSource =
                delayedDirectedGraph.get(sourceNodeIndex);

        final DirectedGraphNode delayedGraphTarget =
                delayedDirectedGraph.get(targetNodeIndex);

        System.out.println("Running ThreadPoolBidirectionalDijkstraPathFinder...");
        long start = System.currentTimeMillis();
        final List<DirectedGraphNode> testPath = 
                testPathFinder
                        .search(delayedGraphSource,
                                delayedGraphTarget, 
                                new ForwardNodeExpander(),
                                new BackwardNodeExpander(),
                                null,
                                null,
                                null);
        
        long end = System.currentTimeMillis();
        
        System.out.printf(
                "ThreadPoolBidirectionalDijkstraPathFinder took %d milliseconds.\n", 
                end - start);

        System.out.println("Running ReferenceDijkstraPathFinder...");
        
        start = System.currentTimeMillis();
        final List<DirectedGraphNode> referencePath = 
                referencePathFinder
                        .search(delayedGraphSource, delayedGraphTarget);
        
        end = System.currentTimeMillis();
        
        System.out.printf("ReferenceDijkstraPathFinder took %d milliseconds.\n",
                          end - start);

        assertEquals(referencePath.size(), testPath.size());
        assertEquals(referencePath.get(0), testPath.get(0));
        assertEquals(referencePath.get(referencePath.size() - 1),
                     testPath.get(testPath.size() - 1));
        
        if (referencePath.size() == testPath.size()) {
            System.out.println("Reference path:");
            
            for (final DirectedGraphNode node : referencePath) {
                System.out.println(node);
            }
            
            System.out.println();
            System.out.println("Test path:");
            
            for (final DirectedGraphNode node : testPath) {
                System.out.println(node);
            }
        }
        
        System.out.println("testCorrectness() done.");
    }   
    
    // This test may take a several seconds too complete.
    @Test
    public void returnsEmptyPathOnDisconnectedGraph() {
        System.out.println("--- Begin: returnsEmptyPathOnDisconnectedGraph()");
        
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
        
        System.out.println("--- End:   returnsEmptyPathOnDisconnectedGraph()");
    }
    
    @Test
    public void haltsOnFailingNodes() {
        System.out.println("--- Begin: haltsOnFailingNodes()");
        
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
        
        System.out.println("--- End:   haltsOnFailingNodes()");
    }
    
    @Test
    public void omitsFaultyLinks() {
        final DirectedGraphNode a  = new DirectedGraphNode(1, 100);
        final DirectedGraphNode b1 = new DirectedGraphNode(2, 100);
        final DirectedGraphNode b2 = new DirectedGraphNode(3, 100);
        final DirectedGraphNode b3 = new DirectedGraphNode(4, 100);
        final DirectedGraphNode c1 = new DirectedGraphNode(5, 10_000);
        final DirectedGraphNode c2 = new DirectedGraphNode(6, 10_000);
        final DirectedGraphNode d  = new DirectedGraphNode(7, 100);
        
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
    
    @Test
    public void halt() {
        final DirectedGraphNode source = new DirectedGraphNode(1, 10_000);
        final DirectedGraphNode target = new DirectedGraphNode(2, 10_000);
        
        source.addChild(target);
        
        final AbstractNodeExpander<DirectedGraphNode> forwardNodeExpander = 
                new ForwardNodeExpander();
        
        final AbstractNodeExpander<DirectedGraphNode> backwardNodeExpander = 
                new BackwardNodeExpander();
        
        AbstractDelayedGraphPathFinder<DirectedGraphNode> finder1 =
                ThreadPoolBidirectionalPathFinderBuilder
                .<DirectedGraphNode>begin()
                .withExpansionDurationMillis(1000)
                .withNumberOfForwardThreads(10)
                .withNumberOfBackwardThreads(10)
                .dijkstra();
        
        AbstractDelayedGraphPathFinder<DirectedGraphNode> finder2 =
                ThreadPoolBidirectionalPathFinderBuilder
                .<DirectedGraphNode>begin()
                .withExpansionDurationMillis(1000)
                .withNumberOfForwardThreads(10)
                .withNumberOfBackwardThreads(10)
                .dijkstra();
        
        final Runnable runnable1 = new Runnable() {
            
            @Override
            public void run() {
                ThreadPoolBidirectionalPathFinderSearchBuilder
                        .<DirectedGraphNode>withPathFinder(finder1)
                        .withSourceNode(source)
                        .withTargetNode(target)
                        .withForwardNodeExpander(forwardNodeExpander)
                        .search();
            }
        };
        
        final Runnable runnable2 = new Runnable() {
            
            @Override
            public void run() {
                ThreadPoolBidirectionalPathFinderSearchBuilder
                        .<DirectedGraphNode>withPathFinder(finder2)
                        .withSourceNode(source)
                        .withTargetNode(target)
                        .withForwardNodeExpander(forwardNodeExpander)
                        .search();
            }
        };
        
        new Thread(runnable1).start();
        new Thread(runnable2).start();
        
        System.out.println(
                "Sleeping 3 seconds before halting the first finder.");
        
        Utils.sleep(3000);
        finder1.halt();
        System.out.println("First finder halted!");
        
        System.out.println(
                "Sleeping 2 seconds before halting the second finder.");
        
        Utils.sleep(2000);
        finder2.halt();
        
        System.out.println("Second finder halted!");
    }
    
    @Test
    public void fluentApiSearchBuilding() {
        System.out.println("--- Begin: fluentApiSearchBuilding()");
        
        DirectedGraphNode source = new DirectedGraphNode(1);
        DirectedGraphNode target = new DirectedGraphNode(2);
        
        AbstractDelayedGraphPathFinder<DirectedGraphNode> pathfinder = 
                ThreadPoolBidirectionalPathFinderBuilder.
                        <DirectedGraphNode>begin().dijkstra();
        
        AbstractNodeExpander<DirectedGraphNode> forwardNodeExpander = 
                new ForwardNodeExpander();
        
        AbstractNodeExpander<DirectedGraphNode> backwardNodeExpander = 
                new BackwardNodeExpander();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withUndirectedGraphNodeExpander(forwardNodeExpander)
                .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withUndirectedGraphNodeExpander(forwardNodeExpander)
                .withSharedSearchProgressListener(null)
                .withForwardSearchProgressListener(null)
                .withBackwardSearchProgressListener(null)
                .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withForwardNodeExpander(forwardNodeExpander)
                .withBackwardNodeExpander(backwardNodeExpander)
                .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withForwardNodeExpander(forwardNodeExpander)
                
                .withBackwardNodeExpander(backwardNodeExpander)
                .withSharedSearchProgressListener(null)
                .withForwardSearchProgressListener(null)
                .withBackwardSearchProgressListener(null)
                .search();
        
        System.out.println("--- End:   fluentApiSearchBuilding()");
    }
    
    @Test
    public void undirectedGraphTest() {
        System.out.println("undirectedGraphTest()");
        
        DirectedGraphNode s = new DirectedGraphNode(1);
        DirectedGraphNode t = new DirectedGraphNode(2);
        DirectedGraphNode a = new DirectedGraphNode(3);
        DirectedGraphNode b = new DirectedGraphNode(4);
        
        s.addChild(a);
        a.addChild(s);
        
        a.addChild(b);
        b.addChild(a);
        
        b.addChild(t);
        t.addChild(b);
        
        AbstractDelayedGraphPathFinder<DirectedGraphNode> pathfinder = 
                ThreadPoolBidirectionalPathFinderBuilder.
                        <DirectedGraphNode>begin()
                        .withNumberOfForwardThreads(2)
                        .withNumberOfBackwardThreads(2)
                        .dijkstra();
        
        List<DirectedGraphNode> path = 
                ThreadPoolBidirectionalPathFinderSearchBuilder
               .<DirectedGraphNode>
                withPathFinder(pathfinder)
               .withSourceNode(s)
               .withTargetNode(t)
               .withForwardNodeExpander (new ForwardNodeExpander())
               .withBackwardNodeExpander(new ForwardNodeExpander())
               .search();
        
        assertEquals(4, path.size());
    }
}
