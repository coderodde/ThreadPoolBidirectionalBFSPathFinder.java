package io.github.coderodde.graph.pathfinding.delayed.impl;

import io.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import io.github.coderodde.graph.pathfinding.delayed.DirectionProgressListener;
import io.github.coderodde.graph.pathfinding.delayed.SharedSearchProgressListener;
import java.util.List;
import java.util.Objects;

public final class ThreadPoolBidirectionalBFSPathFinderSearchBuilder<N> {
    
    private static final class Settings<N> {
        AbstractDelayedGraphPathFinder<N> finder;
        N source;
        N target;
        AbstractNodeExpander<N> forwardSearchExpander;
        AbstractNodeExpander<N> backwardSearchExpander;
        SharedSearchProgressListener<N> sharedSearchProgressListener;
        DirectionProgressListener<N> forwardSearchProgressListener;
        DirectionProgressListener<N> backwardSearchProgressListener;
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
        
        public SharedSearchProgressListenerSelector<N> 
            withUndirectedGraphNodeExpander(
                    final AbstractNodeExpander<N> expander) {
                
            Objects.requireNonNull(expander, "The input expander is null.");
            settings.forwardSearchExpander  = expander;
            settings.backwardSearchExpander = expander;
            return new SharedSearchProgressListenerSelector<>(settings);
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
        
        public SharedSearchProgressListenerSelector<N> 
            withBackwardNodeExpander(
                    final AbstractNodeExpander<N> expander) {
            settings.backwardSearchExpander = expander;
            return new SharedSearchProgressListenerSelector<>(settings);
        }
            
        public DirectedSearch<N> search() {
            return new DirectedSearch<>(settings);
        }
    }
    
    public static final class UndirectedGraphNodeExpanderSelector<N> {
        private final Settings<N> settings;

        UndirectedGraphNodeExpanderSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public SharedSearchProgressListenerSelector<N> 
        withSharedSearchProgressListener(
                final SharedSearchProgressListener<N> listener) {
            settings.sharedSearchProgressListener = listener;
            return new SharedSearchProgressListenerSelector<>(settings);
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
    }
    
    public static final class SharedSearchProgressListenerSelector<N> {
        private final Settings<N> settings;

        SharedSearchProgressListenerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public ForwardSearchProgressListenerSelector<N> 
        withSharedSearchProgressListener(
                final SharedSearchProgressListener<N> listener) {
            this.settings.sharedSearchProgressListener = listener;
            return new ForwardSearchProgressListenerSelector<>(settings);
        }
        
        public List<N> search() {
            return settings.finder.search(
                    settings.source, 
                    settings.target, 
                    settings.forwardSearchExpander,
                    settings.backwardSearchExpander,
                    settings.sharedSearchProgressListener,
                    settings.forwardSearchProgressListener,
                    settings.backwardSearchProgressListener);
        }
    }
    
    public static final class ForwardSearchProgressListenerSelector<N> {
        private final Settings<N> settings;
        
        ForwardSearchProgressListenerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public BackwardSearchProgressListenerSelector<N> 
        withForwardSearchProgressListener(
                final DirectionProgressListener<N> listener) {
            settings.forwardSearchProgressListener = listener;
            return new BackwardSearchProgressListenerSelector<>(settings);
        }
    }
    
    public static final class BackwardSearchProgressListenerSelector<N> {
        private final Settings<N> settings;
        
        BackwardSearchProgressListenerSelector(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public DirectedSearch<N> withBackwardSearchProgressListener(final DirectionProgressListener<N> listener) {
            settings.backwardSearchProgressListener = listener;
            return new DirectedSearch<>(settings);
        }
    }
    
    public static final class DirectedSearch<N> {
        private final Settings<N> settings;
        
        DirectedSearch(final Settings<N> settings) {
            this.settings = settings;
        }
        
        public List<N> search() {
            return settings.finder.search(
                    settings.source,
                    settings.target,
                    settings.forwardSearchExpander,
                    settings.backwardSearchExpander,
                    settings.sharedSearchProgressListener,
                    settings.forwardSearchProgressListener, 
                    settings.backwardSearchProgressListener);
        }
        
        public BackwardSearchProgressListenerSelector<N> 
        withForwardSearchProgressLogger(
                final DirectionProgressListener<N> forwardSearchProgressLogger) {
            settings.forwardSearchProgressListener = forwardSearchProgressLogger;
            return new BackwardSearchProgressListenerSelector<>(settings);
        }
    }
}
