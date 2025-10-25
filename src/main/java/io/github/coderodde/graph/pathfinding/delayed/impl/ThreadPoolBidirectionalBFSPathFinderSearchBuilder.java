package io.github.coderodde.graph.pathfinding.delayed.impl;

import io.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import io.github.coderodde.graph.pathfinding.delayed.ProgressLogger;
import java.util.List;
import java.util.Objects;

public final class ThreadPoolBidirectionalBFSPathFinderSearchBuilder<N> {
    
    private static final class Settings<N> {
        AbstractDelayedGraphPathFinder<N> finder;
        N source;
        N target;
        AbstractNodeExpander<N> forwardSearchExpander;
        AbstractNodeExpander<N> backwardSearchExpander;
        ProgressLogger<N> forwardSearchProgressLogger;
        ProgressLogger<N> backwardSearchProgressLogger;
        ProgressLogger<N> sharedSearchProgressLogger;
    }
    
    public static <N> SourceNodeSelector<N> 
        withPathFinder(AbstractDelayedGraphPathFinder<N> finder) {
        Settings<N> settings = new Settings<>();
        settings.finder = 
                Objects.requireNonNull(finder, "The input finder is null.");
        
        return new SourceNodeSelector<>(settings);
    }
    
    public static final class SourceNodeSelector<N> {
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
    
    public static final class TargetNodeSelector<N> {
        private final Settings<N> settings;
        
        private TargetNodeSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public ExpanderSelector<N> 
        withTargetNode(final N target) {
            Objects.requireNonNull(target, "The target node is null.");
            settings.target = target;
            return new ExpanderSelector<>(settings);
        }
    }
    
    public static final class ExpanderSelector<N> {
        private final Settings<N> settings;
        
        ExpanderSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public UndirectedGraphNodeExpanderSelector<N> 
            withUndirectedGraphNodeExpander(
                    final AbstractNodeExpander<N> expander) {
                
            Objects.requireNonNull(expander, "The input expander is null.");
            settings.forwardSearchExpander  = expander;
            settings.backwardSearchExpander = expander;
            return new UndirectedGraphNodeExpanderSelector<>(settings);
        }
            
        public BackwardNodeExpanderSelector<N> 
            withForwardNodeExpander(
                    final AbstractNodeExpander<N> forwardSearchExpander) {
            Objects.requireNonNull(forwardSearchExpander,
                                   "The forward search expander is null.");
            
            settings.forwardSearchExpander = forwardSearchExpander;
            return new BackwardNodeExpanderSelector<>(settings);
        }
    }
    
    public static final class BackwardNodeExpanderSelector<N> {
        private final Settings<N> settings;

        private BackwardNodeExpanderSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public DirectedSearch<N> 
            withBackwardNodeExpander(
                    final AbstractNodeExpander<N> backwardSearchExpander) {
                
            Objects.requireNonNull(
                    backwardSearchExpander,
                    "The input backward search expander is null.");
            
            settings.backwardSearchExpander = backwardSearchExpander;
            return new DirectedSearch<>(settings);
        }
            
        public BackwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new BackwardSearchProgressLoggerSelector<>(settings);
        }
    }
    
    public static final class UndirectedGraphNodeExpanderSelector<N> {
        private final Settings<N> settings;

        UndirectedGraphNodeExpanderSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(
                    settings.source, 
                    settings.target, 
                    settings.forwardSearchExpander,
                    null, 
                    null, 
                    null);
        }
        
        public BackwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new BackwardSearchProgressLoggerSelector<>(settings);
        }
    }
    
    public static final class ForwardSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        ForwardSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public BackwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new BackwardSearchProgressLoggerSelector<>(settings);
        }
    }
    
    public static final class BackwardSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        BackwardSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public SharedSearchProgressLoggerSelector<N> 
        withBackwardSearchProgressLogger(
                final ProgressLogger<N> backwardSearchProgressLogger) {
            settings.backwardSearchProgressLogger = 
                     backwardSearchProgressLogger;
            
            return new SharedSearchProgressLoggerSelector<>(settings);
        }
    }
    
    public static final class SharedSearchProgressLoggerSelector<N> {
        private final Settings<N> settings;
        
        SharedSearchProgressLoggerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public Search<N> withSharedSearchProgressLogger(
                final ProgressLogger<N> sharedSearchProgressLogger) {
            
            this.settings.sharedSearchProgressLogger = 
                          sharedSearchProgressLogger;
            
            return new Search<>(settings);
        }
    }
    
    public static final class Search<N> {
        private final Settings<N> settings;
        
        Search(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(settings.source,
                                          settings.target,
                                          settings.forwardSearchExpander,
                                          settings.backwardSearchExpander,
                                          settings.forwardSearchProgressLogger, 
                                          settings.backwardSearchProgressLogger, 
                                          settings.sharedSearchProgressLogger);
        }
    }
    
    public static final class DirectedSearch<N> {
        private final Settings<N> settings;
        
        DirectedSearch(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(settings.source,
                                          settings.target,
                                          settings.forwardSearchExpander,
                                          settings.backwardSearchExpander,
                                          settings.forwardSearchProgressLogger, 
                                          settings.backwardSearchProgressLogger, 
                                          settings.sharedSearchProgressLogger);
        }
        
        public BackwardSearchProgressLoggerSelector<N> 
        withForwardSearchProgressLogger(
                final ProgressLogger<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressLogger = forwardSearchProgressLogger;
            return new BackwardSearchProgressLoggerSelector<>(settings);
        }
    }
}
