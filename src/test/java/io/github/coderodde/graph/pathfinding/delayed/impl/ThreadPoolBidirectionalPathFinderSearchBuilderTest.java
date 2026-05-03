package io.github.coderodde.graph.pathfinding.delayed.impl;

import io.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import java.util.List;
import org.junit.Test;

public class ThreadPoolBidirectionalPathFinderSearchBuilderTest {

    private final Integer source = 1; // source
    private final Integer t = 2; // target
    private final AbstractNodeExpander<Integer> expander1 = expander();
    private final AbstractNodeExpander<Integer> expander2 = expander1;
    private final AbstractDelayedGraphPathFinder<Integer> finder = 
            new ThreadPoolBidirectionalBFSPathFinder<>();
    
    @Test
    public void testWithPathFinder() {
        // Passes if does not throw an exception.
        ThreadPoolBidirectionalPathFinderSearchBuilder
            .withPathFinder(finder)
            .withSourceNode(source)
            .withTargetNode(t)
            .withUndirectedGraphNodeExpander(expander1)
            .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder
            .withPathFinder(finder)
            .withSourceNode(source)
            .withTargetNode(t)
            .withForwardNodeExpander(expander1)
            .withBackwardNodeExpander(expander2)
            .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder
            .withPathFinder(finder)
            .withSourceNode(source)
            .withTargetNode(t)
            .withUndirectedGraphNodeExpander(expander1)
            .withSharedSearchProgressListener(null)
            .withForwardSearchProgressListener(null)
            .withBackwardSearchProgressListener(null)
            .search();
        
        ThreadPoolBidirectionalPathFinderSearchBuilder
            .withPathFinder(finder)
            .withSourceNode(source)
            .withTargetNode(t)
            .withForwardNodeExpander(expander1)
            .withBackwardNodeExpander(expander2)
            .withSharedSearchProgressListener(null)
            .withForwardSearchProgressListener(null)
            .withBackwardSearchProgressListener(null)
            .search();
    }

    private AbstractNodeExpander<Integer> expander() {
        return new AbstractNodeExpander<Integer>() {
            
            @Override
            public List<Integer> generateSuccessors(Integer node) 
                    throws Exception {
                return List.of(2, 3);
            }

            @Override
            public boolean isValidNode(Integer node) throws Exception {
                return true;
            }
        };
    }
}
