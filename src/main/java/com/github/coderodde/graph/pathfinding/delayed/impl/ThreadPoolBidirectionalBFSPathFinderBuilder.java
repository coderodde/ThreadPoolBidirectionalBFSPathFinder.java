package com.github.coderodde.graph.pathfinding.delayed.impl;

import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_EXPANSION_JOIN_DURATION_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_LOCK_WAIT_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_MASTER_TRIALS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_THREADS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.MINIMUM_MASTER_THREAD_SLEEP_DURATION_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.MINIMUM_NUMBER_OF_THREADS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.MINIMUM_EXPANSION_JOIN_DURATION_MILLIS;
import static com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder.MINIMUM_LOCK_WAIT_MILLIS;

/**
 *
 * @version 1.0.0 (March 17, 2024)
 */
public final class ThreadPoolBidirectionalBFSPathFinderBuilder<N> {
    
    private static final class Settings {
        int numberOfRequestedThreads = DEFAULT_NUMBER_OF_THREADS;
        
        int masterThreadSleepDurationMillis = 
                DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS;
        
        int slaveThreadSleepDurationMillis = 
                DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS;
        
        int numberOfMasterTrials = DEFAULT_NUMBER_OF_MASTER_TRIALS;
        
        int expansionJoinDurationMillis = 
                DEFAULT_EXPANSION_JOIN_DURATION_MILLIS;
        
        int lockWaitMillis = DEFAULT_LOCK_WAIT_MILLIS;
    }
    
    public static <N> Builder<N> begin() {
        return new Builder<>();
    }
    
    public static final class Builder<N> {
        
        private final Settings settings = new Settings();
        
        public Builder<N> withNumberOfRequestedThreads(
                int numberOfRequestedThreads) {
            settings.numberOfRequestedThreads = 
                    Math.max(numberOfRequestedThreads, 
                             MINIMUM_NUMBER_OF_THREADS);
                    
            return this;
        }
        
        public Builder<N> withMasterThreadSleepDurationMillis(
                int masterThreadSleepDurationMillis) {
            settings.masterThreadSleepDurationMillis = 
                    Math.max(masterThreadSleepDurationMillis, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_MILLIS);
                    
            return this;
        }
        
        public Builder<N> withSlaveThreadSleepDurationMillis(
                int slaveThreadSleepDurationMillis) {
            settings.slaveThreadSleepDurationMillis = 
                    Math.max(slaveThreadSleepDurationMillis, 
                             MINIMUM_MASTER_THREAD_SLEEP_DURATION_MILLIS);
                    
            return this;
        }
        
        public Builder<N> withNumberOfMasterTrials(
                int numberOfMasterTrials) {
            settings.numberOfMasterTrials = 
                    Math.max(numberOfMasterTrials, 
                             MINIMUM_NUMBER_OF_THREADS);
                    
            return this;
        }
        
        public Builder<N> withJoinDurationMillis(
                int expansionJoinDurationMillis) {
            settings.expansionJoinDurationMillis = 
                    Math.max(expansionJoinDurationMillis, 
                             MINIMUM_EXPANSION_JOIN_DURATION_MILLIS);
                    
            return this;
        }
        
        public Builder<N> withLockWaitMillis(
                int lockWaitMillis) {
            settings.lockWaitMillis = 
                    Math.max(lockWaitMillis, 
                             MINIMUM_LOCK_WAIT_MILLIS);
                    
            return this;
        }
        
        public ThreadPoolBidirectionalBFSPathFinder<N> end() {
            return new ThreadPoolBidirectionalBFSPathFinder<>(
                    settings.numberOfRequestedThreads,
                    settings.masterThreadSleepDurationMillis,
                    settings.slaveThreadSleepDurationMillis,
                    settings.numberOfMasterTrials,
                    settings.expansionJoinDurationMillis,
                    settings.lockWaitMillis);
        }
    }
}
