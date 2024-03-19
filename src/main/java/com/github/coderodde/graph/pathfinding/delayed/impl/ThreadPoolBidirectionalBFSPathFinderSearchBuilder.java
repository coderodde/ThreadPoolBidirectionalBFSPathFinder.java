package com.github.coderodde.graph.pathfinding.delayed.impl;

import com.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import com.github.coderodde.graph.pathfinding.delayed.ProgressLogger;
import java.util.List;
import java.util.Objects;

public final class ThreadPoolBidirectionalBFSPathFinderSearchBuilder<N> {
    
    private static final class Settings<N> {
        AbstractDelayedGraphPathFinder<N> finder;
        N source;
        N target;
        AbstractNodeExpander<N> uniformExpander;
        AbstractNodeExpander<N> forwardExpander;
        AbstractNodeExpander<N> backwardExpander;
        ProgressLogger<N> forwardSearchProgressLogger;
        ProgressLogger<N> backeardSearchProgressLogger;
        ProgressLogger<N> sharedSearchProgressLogger;
    }
    
    public static <N> SourceNodeSelector<N> 
        withPathFinder(AbstractDelayedGraphPathFinder<N> finder) {
        Settings<N> settings = new Settings<>();
        settings.finder = 
                Objects.requireNonNull(finder, "The input finder is null.");
        
        return new SourceNodeSelector<>(settings);
    }
    
    private static final class SourceNodeSelector<N> {
        private final Settings<N> settings;
        
        private SourceNodeSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public TargetNodeSelector<N> withSourceNode(final N source) {
            Objects.requireNonNull(source, "The target node is null.");
            settings.source = source;
            return new TargetNodeSelector<>(settings);
        }
    }
    
    private static final class TargetNodeSelector<N> {
        private final Settings<N> settings;
        
        TargetNodeSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        ExpanderSelector<N> 
        withTargetNode(final N target) {
            Objects.requireNonNull(target, "The target node is null.");
            settings.target = target;
            
            return new ExpanderSelector<>(settings);
        }
    }
    
    private static final class ExpanderSelector<N> {
        private final Settings<N> settings;
        
        ExpanderSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        UndirectedGraphNodeExpander<N> 
            withUndirectedGraphNodeExpander(
                    final AbstractNodeExpander<N> expander) {
                
            Objects.requireNonNull(expander, "The input expander is null.");
            settings.uniformExpander = expander;
            return new UndirectedGraphNodeExpander<>(settings);
        }
    }
    
    private static final class UndirectedGraphNodeExpander<N> {
        private final Settings<N> settings;

        UndirectedGraphNodeExpander(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(
                    settings.source, 
                    settings.target, 
                    settings.uniformExpander, 
                    null, 
                    null, 
                    null);
        }
        
        ForwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new ForwardSearchProgressLoggerSelector<>(settings);
        }
    }
    
    private static final class ForwardSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        ForwardSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        BackwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new BackwardSearchProgressLoggerSelector<>(settings);
        }
    }
    
    private static final class BackwardSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        BackwardSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        SharedSearchProgressLoggerSelector<N> 
        withBackwardSearchProgressLogger(
                final ProgressLogger<N> backwardSearchProgressLogger) {
            settings.backeardSearchProgressLogger = 
                     backwardSearchProgressLogger;
            
            return new SharedSearchProgressLoggerSelector<>(settings);
        }
    }
    
    private static final class SharedSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        SharedSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        Search<N> withSharedSearchProgressLogger(
                final ProgressLogger<N> sharedSearchProgressLogger) {
            
            this.settings.sharedSearchProgressLogger = 
                          sharedSearchProgressLogger;
            
            return new Search<>(settings);
        }
    }
    
    private static final class Search<N> {
        private final Settings<N> settings;
        
        Search(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(settings.source,
                                          settings.target,
                                          settings.uniformExpander,
                                          settings.forwardSearchProgressLogger, 
                                          settings.backeardSearchProgressLogger, 
                                          settings.sharedSearchProgressLogger);
        }
    }
}
