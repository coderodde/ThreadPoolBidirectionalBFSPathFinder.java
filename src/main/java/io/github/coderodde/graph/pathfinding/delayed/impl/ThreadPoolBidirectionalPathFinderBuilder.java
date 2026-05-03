package io.github.coderodde.graph.pathfinding.delayed.impl;

import static io.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.*;

/**
 * This class provides a builder for path finders.
 * 
 * @param <N> the graph node type.
 */
public final class ThreadPoolBidirectionalPathFinderBuilder<N> {
    
    private static final class Settings {
        int numberOfForwardThreads = DEFAULT_NUMBER_OF_THREADS;
        
        int numberOfBackwardThreads = DEFAULT_NUMBER_OF_THREADS;
        
        long masterThreadSleepDurationNanos = 
                DEFAULT_MASTER_THREAD_SLEEP_DURATION_NANOS;
        
        long slaveThreadSleepDurationNanos = 
                DEFAULT_SLAVE_THREAD_SLEEP_DURATION_NANOS;
        
        int numberOfMasterTrials = DEFAULT_NUMBER_OF_MASTER_TRIALS;
        
        long expansionJoinDurationNanos = 
                DEFAULT_EXPANSION_JOIN_DURATION_NANOS;
    }
    
    /**
     * Initiates building of the path finder.
     * 
     * @param <N> the node data type.
     * @return the builder object.
     */
    public static <N> Builder<N> begin() {
        return new Builder<>();
    }
    
    /**
     * This class implements the actual builder class.
     * 
     * @param <N> the node data type.
     */
    public static final class Builder<N> {
        
        private final Settings settings = new Settings();
        
        /**
         * Selects the number of forward threads.
         * 
         * @param numberOfForwardThreads the number of forward threads.
         * 
         * @return this builder. 
         */
        public Builder<N> withNumberOfForwardThreads(
                int numberOfForwardThreads) {
            settings.numberOfForwardThreads = 
                    Math.max(numberOfForwardThreads, 
                             MINIMUM_NUMBER_OF_THREADS);
                    
            return this;
        }
        
        /**
         * Selects the number of backward threads.
         * 
         * @param numberOfBackwardThreads the number of forward threads.
         * 
         * @return this builder. 
         */
        public Builder<N> withNumberOfBackwardThreads(
                int numberOfBackwardThreads) {
            settings.numberOfBackwardThreads = 
                    Math.max(numberOfBackwardThreads, 
                             MINIMUM_NUMBER_OF_THREADS);
                    
            return this;
        }
        
        /**
         * Selects the master thread sleep duration in milliseconds.
         * 
         * @param masterThreadSleepDurationMillis sleep duration in
         *                                        milliseconds.
         * @return this builder.
         */
        public Builder<N> withMasterThreadSleepDurationMillis(
                int masterThreadSleepDurationMillis) {
            settings.masterThreadSleepDurationNanos = 
                    Math.max((long)(masterThreadSleepDurationMillis) 
                                * 1_000_000L, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Selects the master thread sleep duration in nanoseconds.
         * 
         * @param masterThreadSleepDurationNanos sleep duration in nanoseconds.
         * 
         * @return this builder. 
         */
        public Builder<N> withMasterThreadSleepDurationNanos(
                long masterThreadSleepDurationNanos) {
            settings.masterThreadSleepDurationNanos = 
                    Math.max(masterThreadSleepDurationNanos, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Selects the slave thread sleep duration in milliseconds.
         * 
         * @param slaveThreadSleepDurationMillis sleep duration in
         *                                       millisecoinds.
         * @return this builder.
         */
        public Builder<N> withSlaveThreadSleepDurationMillis(
                int slaveThreadSleepDurationMillis) {
            settings.slaveThreadSleepDurationNanos = 
                    Math.max((long)(slaveThreadSleepDurationMillis) 
                                * 1_000_000L, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Selects the slave thread sleep duration in nanoseconds.
         * 
         * @param slaveThreadSleepDurationNanos sleep duration in nanoseconds.
         * 
         * @return this builder. 
         */
        public Builder<N> withSlaveThreadSleepDurationNanos(
                long slaveThreadSleepDurationNanos) {
            settings.slaveThreadSleepDurationNanos = 
                    Math.max(slaveThreadSleepDurationNanos, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Selects the maximum master thread trials to find non-empty queue.
         * 
         * @param numberOfMasterTrials the number of master trials.
         * 
         * @return this builder.
         */
        public Builder<N> withNumberOfMasterTrials(
                int numberOfMasterTrials) {
            settings.numberOfMasterTrials = 
                    Math.max(numberOfMasterTrials, MINIMUM_NUMBER_OF_MASTER_TRIALS);
                    
            return this;
        }
        
        /**
         * Selects the expansion duration in milliseconds.
         * 
         * @param expansionJoinDurationMillis expansion duration in 
         *                                    milliseconds.
         * @return this builder.
         */
        public Builder<N> withExpansionDurationMillis(
                int expansionJoinDurationMillis) {
            settings.expansionJoinDurationNanos = 
                    Math.max((long)(expansionJoinDurationMillis) * 1_000_000L, 
                             MINIMUM_EXPANSION_JOIN_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Selects the expansion duration in nanoseconds.
         * 
         * @param expansionJoinDurationNanos expansion duration in nanoseconds.
         * 
         * @return this builder. 
         */
        public Builder<N> withExpansionDurationNanos(
                long expansionJoinDurationNanos) {
            settings.expansionJoinDurationNanos = 
                    Math.max(expansionJoinDurationNanos, 
                             MINIMUM_EXPANSION_JOIN_DURATION_NANOS);
                    
            return this;
        }
        
        /**
         * Builds the path finder relying on the breadth-first search.
         * 
         * @return the path finder.
         */
        public ThreadPoolBidirectionalBFSPathFinder<N> bfs() {
            return new ThreadPoolBidirectionalBFSPathFinder<>(
                    settings.numberOfForwardThreads,
                    settings.numberOfBackwardThreads,
                    settings.masterThreadSleepDurationNanos,
                    settings.slaveThreadSleepDurationNanos,
                    settings.numberOfMasterTrials,
                    settings.expansionJoinDurationNanos);
        }
        
        /**
         * Builds the path finder relying on the Dijkstra's algorithm.
         * 
         * @return the path finder.
         */
        public ThreadPoolBidirectionalDijkstraPathFinder<N> dijkstra() {
            return new ThreadPoolBidirectionalDijkstraPathFinder<>(
                    settings.numberOfForwardThreads,
                    settings.numberOfBackwardThreads,
                    settings.masterThreadSleepDurationNanos,
                    settings.slaveThreadSleepDurationNanos,
                    settings.numberOfMasterTrials,
                    settings.expansionJoinDurationNanos);
        }
    }
}
