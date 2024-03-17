package com.github.coderodde.graph.extra;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DirectedGraphBuilder {
    
    private final int nodes;
    private final int minimumNodeDegree;
    private final int maximumNodeDegree;
    private final int minimumDelay;
    private final int maximumDelay;
    private final Random random;
    
    public DirectedGraphBuilder(final int nodes,
                                final int minimumNodeDegree,
                                final int maximumNodeDegree,
                                final int minimumDelay,
                                final int maximumDelay,
                                final Random random) {
        this.nodes = nodes;
        this.minimumNodeDegree = minimumNodeDegree;
        this.maximumNodeDegree = maximumNodeDegree;
        this.minimumDelay = minimumDelay;
        this.maximumDelay = maximumDelay;
        this.random = random;
    }
    
    public List<DirectedGraphNode> getFailingGraph() {
        List<DirectedGraphNode> graph = new ArrayList<>();
        
        for (int i = 0; i < nodes; i++) {
            graph.add(new DirectedGraphNode(i, true, Integer.MAX_VALUE));
        }
        
        for (int i = 0; i < 6 * nodes; i++) {
            final int headNodeIndex = random.nextInt(nodes);
            final int tailNodeIndex = random.nextInt(nodes);
            graph.get(tailNodeIndex).addChild(graph.get(headNodeIndex));
        }
        
        return graph;
    }
  
    public GraphPair getDisconnectedGraphPair() {
        List<DirectedGraphNode> delayedDirectedGraph = 
                new ArrayList<>(nodes / 2);
        
        List<DirectedGraphNode> nondelayedDirectedGraph = 
                new ArrayList<>(nodes / 2);
        
        for (int id = 0; id < nodes; id++) {
            int delayMilliseconds = 
                    random.nextInt(maximumDelay - minimumDelay + 1) 
                    + 
                    minimumDelay;
            
            DirectedGraphNode delayedDirectedGraphNode = 
                    new DirectedGraphNode(
                            id, 
                            true,
                            delayMilliseconds);
            
            DirectedGraphNode nondelayedDirectedGraphNode =
                    new DirectedGraphNode(id);
            
            delayedDirectedGraph.add(delayedDirectedGraphNode);
            nondelayedDirectedGraph.add(nondelayedDirectedGraphNode);
        }
        
        final int totalNumberOfArcs =
                getTotalNumberOfArcs(
                        random, 
                        minimumNodeDegree, 
                        maximumNodeDegree);
        
        for (int i = 0; i < totalNumberOfArcs / 2; i++) {
            final int tailDirectedGraphNodeId = random.nextInt(nodes / 2);
            final int headDirectedGraphNodeId = random.nextInt(nodes / 2);
            
            if (headDirectedGraphNodeId != tailDirectedGraphNodeId) {
                final DirectedGraphNode nondelayedHeadDirectedGraphNode = 
                        nondelayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode nondelayedTailDirectedGraphNode = 
                        nondelayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                nondelayedTailDirectedGraphNode
                        .addChild(nondelayedHeadDirectedGraphNode);
                
                final DirectedGraphNode delayedHeadDirectedGraphNode =
                        delayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode delayedTailDirectedGraphNode =
                        delayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                delayedTailDirectedGraphNode
                        .addChild(delayedHeadDirectedGraphNode);
            }
        }
        
        for (int i = 0; i < totalNumberOfArcs / 2; i++) {
            final int tailDirectedGraphNodeId = nodes / 2 + 
                                                random.nextInt(nodes / 2);
            
            final int headDirectedGraphNodeId = nodes / 2 + 
                                                random.nextInt(nodes / 2);
            
            if (headDirectedGraphNodeId != tailDirectedGraphNodeId) {
                final DirectedGraphNode nondelayedHeadDirectedGraphNode = 
                        nondelayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode nondelayedTailDirectedGraphNode = 
                        nondelayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                nondelayedTailDirectedGraphNode
                        .addChild(nondelayedHeadDirectedGraphNode);
                
                final DirectedGraphNode delayedHeadDirectedGraphNode =
                        delayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode delayedTailDirectedGraphNode =
                        delayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                delayedTailDirectedGraphNode
                        .addChild(delayedHeadDirectedGraphNode);
            }
        }
        
        return new GraphPair(delayedDirectedGraph, nondelayedDirectedGraph);
    }
    
    public GraphPair getConnectedGraphPair() {
        List<DirectedGraphNode> delayedDirectedGraph    = new ArrayList<>();
        List<DirectedGraphNode> nondelayedDirectedGraph = new ArrayList<>();
        
        for (int id = 0; id < nodes; id++) {
            int delayMilliseconds = 
                    random.nextInt(maximumDelay - minimumDelay + 1) 
                    +
                    minimumDelay;
            
            DirectedGraphNode delayedDirectedGraphNode = 
                    new DirectedGraphNode(
                            id, 
                            true,
                            delayMilliseconds);
            
            DirectedGraphNode nondelayedDirectedGraphNode =
                    new DirectedGraphNode(id);
            
            delayedDirectedGraph.add(delayedDirectedGraphNode);
            nondelayedDirectedGraph.add(nondelayedDirectedGraphNode);
        }
        
        final int totalNumberOfArcs =
                getTotalNumberOfArcs(
                        random, 
                        minimumNodeDegree, 
                        maximumNodeDegree);
        
        for (int i = 0; i < totalNumberOfArcs; i++) {
            final int tailDirectedGraphNodeId = random.nextInt(nodes);
            final int headDirectedGraphNodeId = random.nextInt(nodes);
            
            if (headDirectedGraphNodeId != tailDirectedGraphNodeId) {
                final DirectedGraphNode nondelayedHeadDirectedGraphNode = 
                        nondelayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode nondelayedTailDirectedGraphNode = 
                        nondelayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                nondelayedTailDirectedGraphNode
                        .addChild(nondelayedHeadDirectedGraphNode);
                
                final DirectedGraphNode delayedHeadDirectedGraphNode =
                        delayedDirectedGraph.get(headDirectedGraphNodeId);
                
                final DirectedGraphNode delayedTailDirectedGraphNode =
                        delayedDirectedGraph.get(tailDirectedGraphNodeId);
                
                delayedTailDirectedGraphNode
                        .addChild(delayedHeadDirectedGraphNode);
            }
        }
        
        return new GraphPair(delayedDirectedGraph, nondelayedDirectedGraph);
    }
    
    private int getTotalNumberOfArcs(Random random, 
                                     int minimumNodeDegree,
                                     int maximumNodeDegree) {
        int totalNumberOfArcs = 0;
        
        for (int id = 0; id < nodes; id++) {
            final int outdegree = 
                    random.nextInt(
                            maximumNodeDegree - minimumNodeDegree)
                                + minimumNodeDegree 
                                + 1;
            
            totalNumberOfArcs += outdegree;
        }
        
        return totalNumberOfArcs;
    }
}
