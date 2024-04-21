package com.github.coderodde.graph.pathfinding.delayed.impl;

import com.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import com.github.coderodde.graph.pathfinding.delayed.ProgressLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a parallel, bidirectional breadth-first search in order
 * to find an unweighted shortest path from a given source node to a given 
 * target node. The underlying algorithm is the bidirectional breadth-first
 * search. However, multiple threads may work on a single search direction in
 * order to speed up the computation: for each search direction (forward and 
 * backward), the algorithm maintains concurrent state, such as the frontier 
 * queue; many threads may pop the queue, expand the node and append the 
 * neighbours to that queue.
 * 
 * @param <N> the actual graph node type.
 * 
 * @version 2.0.0 (Apr 21, 2024)
 * @since 1.0.0
 */
public final class ThreadPoolBidirectionalBFSPathFinder<N> 
extends AbstractDelayedGraphPathFinder<N> {
    
    private static final Map<ThreadPoolBidirectionalBFSPathFinder,
                             SharedSearchState> INSTANCE_MAP = 
                            new ConcurrentHashMap<>();
    
    /**
     * The default number of threads performing the search.
     */
    public static final int DEFAULT_NUMBER_OF_THREADS = 8;
    
    /**
     * The default number of nanoseconds a master thread sleeps when it finds
     * the frontier queue empty.
     */
    public static final long
            DEFAULT_MASTER_THREAD_SLEEP_DURATION_NANOS = 10_000_000l;

    /**
     * The default number of nanoseconds a slave thread sleeps when it finds
     * the frontier queue empty.
     */
    public static final long 
            DEFAULT_SLAVE_THREAD_SLEEP_DURATION_NANOS = 10_000_000L;

    /**
     * The default upper bound on the number of times a master thread hibernates
     * due to the frontier queue being empty before the entire search is 
     * terminated.
     */
    public static final int DEFAULT_NUMBER_OF_MASTER_TRIALS = 50;
    
    /**
     * The default number of milliseconds to wait on thread joining.
     */
    public static final long DEFAULT_EXPANSION_JOIN_DURATION_NANOS = 1_000_000L;
    
    /**
     * The default number of milliseconds to wait for the lock. 
     */
    public static final long DEFAULT_LOCK_WAIT_NANOS = 5_000_000L;

    /**
     * The minimum number of threads to allow. One thread per each of the two
     * search directions.
     */
    static final int MINIMUM_NUMBER_OF_THREADS = 4;

    /**
     * The minimum number of milliseconds a <b>master thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final long MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS = 1L;

    /**
     * The minimum number of milliseconds a <b>slave thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final long MINIMUM_SLAVE_THREAD_SLEEP_DURATION_NANOS = 1L;

    /**
     * The lower bound on the amount of trials.
     */
    static final int MINIMUM_NUMBER_OF_TRIALS = 1;
    
    /**
     * The minimum number of milliseconds to wait upon thread joining.
     */
    static final long MINIMUM_EXPANSION_JOIN_DURATION_NANOS = 1_000_000L;
    
    /**
     * The minimum number of milliseconds to wait for the lock.
     */
    static final long MINIMUM_LOCK_WAIT_NANOS = 1_000L;

    /**
     * Caches the requested number of threads to use in the search process. This
     * field accounts for both forward and backward search direction.
     */
    private final int numberOfThreads;

    /**
     * The duration of sleeping in milliseconds for the master threads.
     */
    private final long masterThreadSleepDurationNanos;

    /**
     * The duration of sleeping in milliseconds for the slave threads.
     */
    private final long slaveThreadSleepDurationNanos;

    /**
     * While a master thread waits the frontier queue to become non-empty, the
     * master thread makes at most {@code masterThreadTrials} sleeping sessions
     * before giving up and terminating the search.
     */
    private final int masterThreadTrials;
    
    /**
     * The maximum number of milliseconds for waiting the expansion thread.
     */
    private final long expansionJoinDurationNanos;
    
    /**
     * The maximum number of milliseconds to wait for the lock.
     */
    private final long lockWaitDurationNanos;
    
    /**
     * Indicates whether the previous search was halted.
     */
    private volatile boolean wasHalted = false;
    
    /**
     * The logging facility used to log abnormal activity.
     */
    private static final Logger LOGGER = 
            Logger.getLogger(
                    ThreadPoolBidirectionalBFSPathFinder
                            .class
                            .getSimpleName());

    /**
     * Constructs this path finder.
     * 
     * @param numberOfThreads  the requested number of search threads.
     * @param masterThreadSleepDurationNanos the number of nanoseconds a master 
     *                                       thread sleeps whenever it discovers 
     *                                       the frontier queue being empty.
     * @param slaveThreadSleepDurationNanos  the number of nanoseconds a slave
     *                                       thread sleeps whenever it discovers 
     *                                       the frontier queue being empty.
     * @param masterThreadTrials the number of times the master thread 
     *                           hibernates itself before terminating the entire 
     *                           search.
     * @param expansionThreadJoinDurationNanos the number of milliseconds to 
     *                                         wait for the expansion thread.
     * @param lockWaitDurationNanos the number of milliseconds to wait for the 
     *                              lock.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int numberOfThreads,
            final long masterThreadSleepDurationNanos,
            final long slaveThreadSleepDurationNanos,
            final int masterThreadTrials,
            final long expansionThreadJoinDurationNanos,
            final long lockWaitDurationNanos) {
        
        this.numberOfThreads = Math.max(numberOfThreads, 
                                        MINIMUM_NUMBER_OF_THREADS);

        this.masterThreadSleepDurationNanos = 
                Math.max(masterThreadSleepDurationNanos,
                         MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);

        this.slaveThreadSleepDurationNanos = 
                Math.max(slaveThreadSleepDurationNanos,
                         MINIMUM_SLAVE_THREAD_SLEEP_DURATION_NANOS);

        this.masterThreadTrials = 
                Math.max(masterThreadTrials,
                         MINIMUM_NUMBER_OF_TRIALS);
        
        this.expansionJoinDurationNanos = 
                Math.max(expansionThreadJoinDurationNanos,
                         MINIMUM_EXPANSION_JOIN_DURATION_NANOS);
        
        this.lockWaitDurationNanos = 
                Math.max(lockWaitDurationNanos,
                         MINIMUM_LOCK_WAIT_NANOS);
    }

    /**
     * Construct this path finder using default sleeping duration.
     * 
     * @param requestedNumberOfThreads the requested number of search threads.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int requestedNumberOfThreads) {
        
        this(requestedNumberOfThreads, 
             DEFAULT_MASTER_THREAD_SLEEP_DURATION_NANOS,
             DEFAULT_SLAVE_THREAD_SLEEP_DURATION_NANOS,
             DEFAULT_NUMBER_OF_MASTER_TRIALS,
             DEFAULT_EXPANSION_JOIN_DURATION_NANOS,
             DEFAULT_LOCK_WAIT_NANOS);
    }
    
    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public long getMasterThreadSleepDurationNanos() {
        return masterThreadSleepDurationNanos;
    }

    public long getSlaveThreadSleepDurationNanos() {
        return slaveThreadSleepDurationNanos;
    }

    public int getMasterThreadTrials() {
        return masterThreadTrials;
    }

    public long getExpansionJoinDurationNanos() {
        return expansionJoinDurationNanos;
    }

    public long getLockWaitDurationNanos() {
        return lockWaitDurationNanos;
    }
    
    public int getNumberOfExpandedNodes() {
        return numberOfExpandedNodes;
    }
    
    public long getDurationMillis() {
        return duration;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public List<N> 
        search(final N source, 
               final N target, 
               final AbstractNodeExpander<N> forwardSearchNodeExpander, 
               final AbstractNodeExpander<N> backwardSearchNodeExpander, 
               final ProgressLogger<N> forwardSearchProgressLogger, 
               final ProgressLogger<N> backwardSearchProgressLogger, 
               final ProgressLogger<N> sharedSearchProgressLogger) {
            
        LOGGER.log(Level.INFO, 
                   "[Search configuration:]\n{0}", 
                   new ConfigurationInfo());
            
        wasHalted = false;
            
        Objects.requireNonNull(forwardSearchNodeExpander, 
                               "The forward search node expander is null.");

        Objects.requireNonNull(backwardSearchNodeExpander,
                               "The backward search node expander is null.");

        boolean isValidSourceNode;
        
        try {
            isValidSourceNode = forwardSearchNodeExpander.isValidNode(source);
        } catch (final Exception ex) {
            isValidSourceNode = false;
        }
        
        if (!isValidSourceNode) {
            final String exceptionMessage =
                    "The source node (" + source + ") was rejected by the " +
                    "forward search node expander.";

            throw new IllegalArgumentException(exceptionMessage);
        }

        boolean isValidTargetNode;
        
        try {
            isValidTargetNode = backwardSearchNodeExpander.isValidNode(target);
        } catch (final Exception ex) {
            isValidTargetNode = false;
        }
        
        if (!isValidTargetNode) {
            final String exceptionMessage =
                    "The target node (" + target + ") was rejected by the " +
                    "backward search node expander.";

            throw new IllegalArgumentException(exceptionMessage);
        }
        
        // Possibly log the beginning of the search:
        if (sharedSearchProgressLogger != null) {
            sharedSearchProgressLogger.onBeginSearch(source, target);
        }

        // This path finder collects some performance related statistics:
        this.duration = System.currentTimeMillis();

        // Compute the numbers of threads for each of the search direction:
        final int forwardSearchThreadCount  = numberOfThreads / 2;
        final int backwardSearchThreadCount = numberOfThreads - 
                                              forwardSearchThreadCount;

        // Create the state object shared by both the search direction:
        final SharedSearchState<N> sharedSearchState = 
                new SharedSearchState<>(source, 
                                        target, 
                                        sharedSearchProgressLogger,
                                        lockWaitDurationNanos);
        
        INSTANCE_MAP.put(this, sharedSearchState);

        // Create the state obj6/ect shared by all the threads working on forward
        // search direction:
        final SearchState<N> forwardSearchState = new SearchState<>(source);

        // Create the state object shared by all the threads working on backward
        // search direction:
        final SearchState<N> backwardSearchState = new SearchState<>(target);
        
        sharedSearchState.setForwardSearchState(forwardSearchState);
        sharedSearchState.setBackwardSearchState(backwardSearchState);
        
        // The array holding all forward search threads:
        final ForwardSearchThread<N>[] forwardSearchThreads =
                new ForwardSearchThread[forwardSearchThreadCount];

        // Below, the value of 'sleepDuration' is ignored since the thread being 
        // created is a master thread that never sleeps.
        forwardSearchThreads[0] = 
                new ForwardSearchThread<>(0, 
                                          forwardSearchNodeExpander,
                                          forwardSearchState,
                                          sharedSearchState,
                                          true,
                                          forwardSearchProgressLogger,
                                          masterThreadSleepDurationNanos,
                                          masterThreadTrials,
                                          expansionJoinDurationNanos,
                                          this);

        // Spawn the forward search master thread:
        forwardSearchState.introduceThread(forwardSearchThreads[0]);           
        forwardSearchThreads[0].start();

        // Create and spawn all the slave threads working on forward search 
        // direction.
        for (int i = 1; i < forwardSearchThreadCount; ++i) {
            forwardSearchThreads[i] = 
                    new ForwardSearchThread<>(i,
                                              forwardSearchNodeExpander,
                                              forwardSearchState,
                                              sharedSearchState,
                                              false,
                                              forwardSearchProgressLogger,
                                              slaveThreadSleepDurationNanos,
                                              masterThreadTrials,
                                              expansionJoinDurationNanos,
                                              this);

            forwardSearchState.introduceThread(forwardSearchThreads[i]);
            forwardSearchThreads[i].start();
        }

        // The array holding all backward search threads:
        final BackwardSearchThread<N>[] backwardSearchThreads =
                new BackwardSearchThread[backwardSearchThreadCount];

        // Below, the value of 'sleepDuration' is ignored since the thread being
        // created is a master thread that never sleeps.
        backwardSearchThreads[0] = 
                new BackwardSearchThread<>(forwardSearchThreads.length,
                                           backwardSearchNodeExpander,
                                           backwardSearchState,
                                           sharedSearchState,
                                           true,
                                           backwardSearchProgressLogger,
                                           masterThreadSleepDurationNanos,
                                           masterThreadTrials,
                                           expansionJoinDurationNanos,
                                           this);

        // Spawn the backward search master thread:
        backwardSearchState.introduceThread(backwardSearchThreads[0]);
        backwardSearchThreads[0].start();

        // Create and spawn all the slave threads working on backward search
        // direction:
        for (int i = 1; i < backwardSearchThreadCount; ++i) {
            backwardSearchThreads[i] = 
                    new BackwardSearchThread<>(forwardSearchThreads.length + i,
                                               backwardSearchNodeExpander,
                                               backwardSearchState,
                                               sharedSearchState,
                                               false,
                                               backwardSearchProgressLogger,
                                               slaveThreadSleepDurationNanos,
                                               masterThreadTrials,
                                               expansionJoinDurationNanos,
                                               this);

            backwardSearchState.introduceThread(backwardSearchThreads[i]);
            backwardSearchThreads[i].start();
        }

        // Wait all forward search threads to finish their work:
        for (final ForwardSearchThread<N> thread : forwardSearchThreads) {
            try {
                thread.join();
            } catch (final InterruptedException ex) {
                LOGGER.log(Level.WARNING, 
                           "Forward thread {} was interrupted: {}.", 
                           new Object[]{ thread, ex });
            }
        }

        // Wait all backward search threads to finish their work: 
        for (final BackwardSearchThread<N> thread : backwardSearchThreads) {
            try {
                thread.join();
            } catch (final InterruptedException ex) {
                LOGGER.log(Level.WARNING, 
                           "Backward thread {} was interrupted: {}.", 
                           new Object[]{ thread, ex });
            }
        }

        // Record the duration of the search:
        this.duration = System.currentTimeMillis() - this.duration;

        // Count the number of expanded nodes over all threads:
        this.numberOfExpandedNodes = 0;
        
        forwardSearchState.lockThreadSetMutex();
        
        for (final AbstractSearchThread<N> thread 
                : forwardSearchState.runningThreadSet) {
            
            this.numberOfExpandedNodes += thread.numberOfExpandedNodes;
        }
        
        for (final AbstractSearchThread<N> thread 
                : backwardSearchState.runningThreadSet) {
            
            this.numberOfExpandedNodes += thread.numberOfExpandedNodes;
        }
        
        forwardSearchState.unlockThreadSetMutex();

        INSTANCE_MAP.remove(this);
        
        // Construct and return the path:
        return sharedSearchState.getPath();
    }

    @Override
    public void halt() {
        final SharedSearchState<N> sharedSearchState = INSTANCE_MAP.get(this);
        
        if (sharedSearchState != null) {
            sharedSearchState.requestGlobalStop();
        }
        
        wasHalted = true;
    }
    
    public boolean wasHalted() {
        return wasHalted;
    }
    
    private static final class ExpansionThread<N> extends Thread {
        
        private final N node;
        private final AbstractNodeExpander<N> expander;
        private volatile List<N> successorList = Collections.emptyList();
        
        ExpansionThread(final N node,
                        final AbstractNodeExpander<N> expander) {
            this.node = node;
            this.expander = expander;
        }
        
        @Override
        public void run() {
            try {
                successorList = expander.generateSuccessors(node);
            } catch (final Exception ex) {
                System.out.printf(
                        "ERROR: %s could not expand article node \"%s\".\n", 
                        expander.getClass().getSimpleName(), 
                        node);
                
                successorList = Collections.emptyList();
            }
        }
        
        List<N> getSuccessorList() {
            return successorList;
        }
    }

    /**
     * This class holds the state shared by the two search directions.
     */
    private static final class SharedSearchState<N> {
        
        /**
         * The source node.
         */
        private final N source;

        /**
         * The target node. 
         */
        private final N target;
        
        /**
         * The global flag indicating that the search must stop.
         */
        private volatile boolean stopRequested = false;

        /**
         * The state of all the forward search threads.
         */
        private SearchState<N> forwardSearchState;

        /**
         * The state of all the backward search threads.
         */
        private SearchState<N> backwardSearchState;
        
        /**
         * The mutex to use in order to synchronize all the concurrent 
         * operations.
         */
        private final Semaphore mutex = new Semaphore(1, true);

        /**
         * Caches the best known length from the source to the target nodes.
         */
        private volatile int bestPathLengthSoFar = Integer.MAX_VALUE;
        
        /**
         * The best search frontier touch node so far.
         */
        private volatile N touchNode;

        /**
         * The progress logger for reporting the progress.
         */
        private final ProgressLogger<N> sharedProgressLogger;
        
        /**
         * The duration in nanosseconds for waiting to the lock.
         */
        private final long lockWaitDurationNanos;

        /**
         * Constructs a shared state information object for the search.
         * 
         * @param source               the source node.
         * @param target               the target node.
         * @param forwardSearchState   the state of the forward search
         *                             direction.
         * @param backwardSearchState  the state of the backward search
         *                             direction.
         * @param sharedProgressLogger the progress logger for logging the 
         *                             overall progress of the path finder.
         */
        SharedSearchState(final N source,
                          final N target, 
                          final ProgressLogger<N> sharedProgressLogger,
                          final long lockWaitDuration) {
            this.source = source;
            this.target = target;
            this.sharedProgressLogger = sharedProgressLogger;
            this.lockWaitDurationNanos = lockWaitDuration;
        }
        
        void requestGlobalStop() {
            stopRequested = true;
        }
        
        boolean globalStopRequested() {
            return stopRequested;
        }
        
        void setForwardSearchState(final SearchState<N> forwardSearchState) {
            this.forwardSearchState = forwardSearchState;
        }
        
        void setBackwardSearchState(final SearchState<N> backwardSearchState) {
            this.backwardSearchState = backwardSearchState;
        }
        
        /**
         * Locks this shared state.
         * 
         * @throws Exception if mutex acquisition fails.
         */
        boolean lock() {
            
            try {
                return mutex.tryAcquire(lockWaitDurationNanos,
                                        TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                final String exceptionMessage =
                        "Mutex lock threw, permits = " 
                        + mutex.availablePermits() 
                        + ".";
                
                LOGGER.log(Level.SEVERE, exceptionMessage);
                
                throw new RuntimeException();
            }
        }
        
        /**
         * Unlocks this shared state.
         */
        void unlock() {
            mutex.release();
        }
        
        /**
         * Attempts to update the best known path.
         * 
         * @param current the touch node candidate.
         */
        void updateSearchState(final N current) {
            if (forwardSearchState .containsNode(current) &&
                backwardSearchState.containsNode(current)) {
                
                final int currentDistance = 
                        forwardSearchState .getDistanceOf(current) +
                        backwardSearchState.getDistanceOf(current);

                if (bestPathLengthSoFar > currentDistance) {
                    bestPathLengthSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }

        boolean pathIsOptimal() {
            if (touchNode == null) {
                return false;
            }

            final N forwardSearchHead = forwardSearchState.getQueueHead();

            if (forwardSearchHead == null) {
                return false;
            }

            final N backwardSearchHead = backwardSearchState.getQueueHead();

            if (backwardSearchHead == null) {
                return false;
            }

            final int distance =
                  forwardSearchState .getDistanceOf(forwardSearchHead) +
                  backwardSearchState.getDistanceOf(backwardSearchHead);
            
            return distance > bestPathLengthSoFar;
        }

        /**
         * Constructs a shortest path and returns it as a list. If the target
         * node is unreachable from the source node, returns an empty list.
         * 
         * @return a shortest path found, or an empty list if target node is not 
         *         reachable from the source node.
         * @throws Exception if mutex acquisition fails.
         */
        List<N> getPath() {
            if (touchNode == null) {
                if (sharedProgressLogger != null) {
                    sharedProgressLogger.onTargetUnreachable(source, target);
                }
                
                return new ArrayList<>();
            }
            
            final List<N> path = new ArrayList<>();

            N current = touchNode;

            while (current != null) {
                path.add(current);
                current = forwardSearchState.getParentOf(current);
            }

            Collections.<String>reverse(path);
            current = backwardSearchState.parents.get(touchNode);

            while (current != null) {
                path.add(current);
                current = backwardSearchState.getParentOf(current);
            }

            if (sharedProgressLogger != null) {
                sharedProgressLogger.onShortestPath(path);
            }
            
            return path;
        }
    }

    /**
     * This class holds all the state of a single search direction.
     */
    private static final class SearchState<N> {
        
        /**
         * This FIFO queue contains the queue of nodes reached but not yet 
         * expanded. It is called the <b>search frontier</b>.
         */
        private final Deque<N> queue = new ArrayDeque<>();
        
        /**
         * This map maps each discovered node to its predecessor on the shortest 
         * path.
         */
        private final Map<N, N> parents = new HashMap<>();

        /**
         * This map maps each discovered node to its shortest path distance from
         * the source node.
         */
        private final Map<N, Integer> distance = new HashMap<>();
        
        /**
         * The set of all the threads working on this particular direction.
         */
        private final Set<AbstractSearchThread<N>> runningThreadSet = 
                Collections.synchronizedSet(new HashSet<>());

        /**
         * The set of all <b>slave</b> threads that are currently sleeping.
         */
        private final Set<AbstractSearchThread<N>> sleepingThreadSet =
                Collections.synchronizedSet(new HashSet<>());
        
        /**
         * The mutex for controlling access to the thread sets.
         */
        private final Semaphore threadSetsMutex = new Semaphore(1, true);

        /**
         * Constructs the search state object.
         * 
         * @param initialNode the node from which the search begins. If this 
         *                    state object is used in the forward search, this 
         *                    node should be the source node. Otherwise, if this 
         *                    state object is used in the backward search, this 
         *                    node should be the target node.
         */
        SearchState(final N initialNode) {
            queue.add(initialNode);
            parents.put(initialNode, null);
            distance.put(initialNode, 0);
        }

        N removeQueueHead() {
            if (queue.isEmpty()) {
                return null;
            }
            
            N head = queue.remove();
            
            return head;
        }
        
        N getQueueHead() {
            return queue.peek();
        }
        
        boolean containsNode(final N node) {
            return distance.containsKey(node);
        }
        
        Integer getDistanceOf(final N node) {
            Integer dist = distance.get(node);
            return dist;
        }
        
        N getParentOf(final N node) {
            return parents.get(node);
        }
        
        void lockThreadSetMutex() {
            threadSetsMutex.acquireUninterruptibly();
        }
        
        void unlockThreadSetMutex() {
            threadSetsMutex.release();
        }
        
        /**
         * Tries to set the new node in the data structures.
         * 
         * @param node        the node to process.
         * @param predecessor the predecessor node. In the forward search 
         *                    direction, it is the tail of the arc, and in
         *                    the backward search direction, it is the head of 
         *                    the arc.
         * @return {@code true} if the {@code node} was not added, {@code false}
         *         otherwise.
         */
        private boolean trySetNodeInfo(final N node, final N predecessor) {
            if (containsNode(node)) {
                // Nothing to set.
                return false;
            }
            
            distance.put(node, getDistanceOf(predecessor) + 1);
            parents.put(node, predecessor);
            queue.addLast(node);
            return true;
        }
        
        private void tryUpdateIfImprovementPossible(
                final N node, 
                final N predecessor) {
            
            if (distance.get(node) > distance.get(predecessor) + 1) {
                distance.put(node, distance.get(predecessor) + 1);
                parents.put(node, predecessor);
            }
        }
        
        /**
         * Introduces a new thread to this search direction.
         * 
         * @param thread the thread to introduce.
         */
        void introduceThread(final AbstractSearchThread<N> thread) {
            lockThreadSetMutex();
            thread.putThreadToSleep(false);
            runningThreadSet.add(thread);
            unlockThreadSetMutex();
        }

        /**
         * Asks the argument thread to go to sleep and adds it to the set of
         * sleeping slave threads.
         * 
         * @param thread the <b>slave</b> thread to hibernate.
         */
        void putThreadToSleep(final AbstractSearchThread<N> thread) {
            thread.putThreadToSleep(true);
            lockThreadSetMutex();
            runningThreadSet.remove(thread);
            sleepingThreadSet.add(thread);
            unlockThreadSetMutex();
        }
        
        /**
         * Wakes up all the sleeping slave threads.
         */
        void wakeupAllSleepingThreads() { 
            lockThreadSetMutex();
            
            for (final AbstractSearchThread<N> thread : sleepingThreadSet) {
                thread.putThreadToSleep(false);
                runningThreadSet.add(thread);
            }
            
            sleepingThreadSet.clear();
            unlockThreadSetMutex();
        }
    }

    /**
     * This abstract class defines a thread that may be asked to go to sleep.
     */
    private abstract static class SleepingThread extends Thread {

        /**
         * Holds the flag indicating whether this thread is put to sleep.
         */
        protected volatile boolean sleepRequested = false;

        /**
         * The number of nanoseconds to sleep during each hibernation.
         */
        protected final long threadSleepDurationNanos;

        /**
         * The maximum number of times a master thread hibernates itself before
         * giving up and terminating the entire search.
         */
        protected final int threadSleepTrials;
        
        /**
         * The boolean flag indicating whether this thread is a master thread. 
         * If not, it is called a slave thread.
         */
        protected final boolean isMasterThread;

        /**
         * Constructs this thread supporting sleeping.
         * 
         * @param threadSleepDurationNanos the number of milliseconds to sleep each 
         *                            time.
         * @param threadSleepTrials   the maximum number of trials to hibernate
         *                            a master thread before giving up.
         */
        SleepingThread(final long threadSleepDurationNanos,
                       final int threadSleepTrials,
                       final boolean isMasterThread) {
            
            this.threadSleepDurationNanos = threadSleepDurationNanos;
            this.threadSleepTrials   = threadSleepTrials;
            this.isMasterThread = isMasterThread;
        }

        /**
         * Sets the current sleep status of this thread.
         * 
         * @param toSleep indicates whether to put this thread to sleep or 
         *                wake it up.
            return isMasterThread;
         */
        void putThreadToSleep(final boolean toSleep) {
            this.sleepRequested = toSleep;
        }
    }

    /**
     * This class defines all the state that should appear in threads working in
     * both search direction.
     * 
     * @param <N> the actual node type.
     */
    private static abstract class AbstractSearchThread<N> 
            extends SleepingThread {

        /**
         * The ID of this thread.
         */
        protected final int threadId;

        /**
         * Holds the reference to the class responsible for computing the 
         * neighbour nodes of a given node.
         */
        protected final AbstractNodeExpander<N> nodeExpander;
        
        /**
         * The entire state of this search thread, shared possibly with other
         * threads working on the same search direction.
         */
        protected final SearchState<N> searchState;

        /**
         * The state shared by both the directions.
         */
        protected final SharedSearchState<N> sharedSearchState;

        /**
         * The progress logger.
         */
        protected final ProgressLogger<N> searchProgressLogger;
        
        /**
         * The number of milliseconds for waiting for the node expansion.
         */
        private final long expansionJoinDurationNanos;
        
        /**
         * Caches the amount of nodes expanded by this thread.
         */
        private int numberOfExpandedNodes;

        /**
         * Construct this search thread.
         * 
         * @param id                   the ID number of this thread. Must be
         *                             unique over <b>all</b> search threads.
         * @param nodeExpander         the node expander responsible for 
         *                             generating the neighbours in this search
         *                             thread.
         * @param searchState          the search state object.
         * @param sharedSearchState    the search state object shared with both
         *                             forward search threads and backward
         *                             search threads.
         * @param isMasterThread       indicates whether this search thread is a
         *                             master thread or a slave thread.
         * @param searchProgressLogger the progress logger for the search 
         *                             direction of this search thread.
         * @param threadSleepDurationNanos the duration of sleeping in 
         *                                 nanoseconds always when a thread 
         *                                 finds the frontier queue empty.
         * @param threadSleepTrials    the maximum number of hibernation trials
         *                             before a master thread gives up and 
         *                             terminates the entire search process. If
         *                             this thread is a slave thread, this 
         *                             parameter is ignored.
         * @param expansionJoinDurationNanos the duration in nanoseconds for the
         *                                   expander thread to return results.
         * @param finder the current finder.
         */
        AbstractSearchThread(final int id,
                             final AbstractNodeExpander<N> nodeExpander,
                             final SearchState<N> searchState, 
                             final SharedSearchState<N> sharedSearchState,
                             final boolean isMasterThread,
                             final ProgressLogger<N> searchProgressLogger,
                             final long threadSleepDurationNanos,
                             final int threadSleepTrials,
                             final long expansionJoinDurationNanos,
                             final AbstractDelayedGraphPathFinder<N> finder) {
            
            super(threadSleepDurationNanos, 
                  threadSleepTrials, 
                  isMasterThread);
            
            this.threadId              = id;
            this.nodeExpander          = nodeExpander;
            this.searchState           = searchState;
            this.sharedSearchState     = sharedSearchState;
            this.searchProgressLogger  = searchProgressLogger;
            this.expansionJoinDurationNanos = expansionJoinDurationNanos;
        }

        @Override
        public void run() {
            while (true) {
                if (sharedSearchState.globalStopRequested()) {
                    return;
                }
                
                if (sleepRequested) {
                    mysleep(threadSleepDurationNanos);
                    continue;
                }
                
                if (isMasterThread) {
                    processCurrentInMasterThread();
                } else {
                    processCurrentInSlaveThread();
                }
            }
        }
        
        /**
         * {@inheritDoc }
         */
        @Override
        public boolean equals(final Object other) {
            if (other == null) {
                return false;
            }

            if (!getClass().equals(other.getClass())) {
                return false;
            }

            return threadId == ((AbstractSearchThread) other).threadId;
        }

        /**
         * {@inheritDoc }
         */
        @Override
        public int hashCode() {
            return threadId;
        }

        /**
         * {@inheritDoc }
         */
        @Override
        public String toString() {
            return "[Thread ID: " + threadId + ", master: " + isMasterThread + "]";
        }
        
        /**
         * Processes the current node in the master thread.
         * 
         * @param head the candidate frontier queue head node.
         */
        private void processCurrentInMasterThread() {
            lock();
            final N head = searchState.getQueueHead();
            unlock();
            
            searchState.wakeupAllSleepingThreads();
            
            if (head != null) {
                return;
            }
            
            N currentHead = null;
            
            for (int trials = 0; trials < threadSleepTrials; trials++) {
                mysleep(threadSleepDurationNanos);
                currentHead = searchState.getQueueHead();
                
                if (currentHead != null) {
                    break;
                }
            }
            
            if (currentHead == null) {
                // We have run out of trials and the queue is still empty; halt.
                sharedSearchState.requestGlobalStop();
            } else {
                searchState.wakeupAllSleepingThreads();
            }
        }
        
        private void processCurrentInSlaveThread() {
            if (sharedSearchState.globalStopRequested()) {
                return;
            }
            
            if (sleepRequested) {
                mysleep(threadSleepDurationNanos);
                return;
            }
            
            lock();
            final N current = searchState.getQueueHead();
            unlock();
                
            if (current == null) {
                // Nothing to do, go to sleep.
                searchState.putThreadToSleep(this);
                return;
            }
            
            searchState.wakeupAllSleepingThreads();
            
            lock();
            sharedSearchState.updateSearchState(current);
            
            if (sharedSearchState.pathIsOptimal()) {
                unlock();
                sharedSearchState.requestGlobalStop();
                return;
            }
            
            unlock();
            
            if (searchProgressLogger != null) {
                searchProgressLogger.onExpansion(current);
            }
            
            numberOfExpandedNodes++;
            
            expand();
        }
        
        private void lock() {
            while (!sharedSearchState.lock()) {
                
            }
        }
        
        private void unlock() {
            sharedSearchState.unlock();
        }
        
        /**
         * Expands the current node.
         * 
         * @param current the node of which to generate the successor nodes.
         */
        private void expand() {
            lock();
            final N current = searchState.removeQueueHead();
            unlock();
            
            if (current == null) {
                return;
            }
            
            final ExpansionThread<N> expansionThread =
                    new ExpansionThread<>(current, nodeExpander);
            
            expansionThread.setDaemon(true);
            
            expansionThread.start();
            
            try {
                expansionThread.join(expansionJoinDurationNanos);
            } catch (InterruptedException ex) {
                return;
            }
            
            if (expansionThread.successorList.isEmpty()) {
                return;
            }
            
            // Once here, the expansion completed within expansionJoinDuration!
            lock();
            sharedSearchState.updateSearchState(current);
            unlock();
            
            for (final N successor : expansionThread.getSuccessorList()) {
                lock();
                
                if (searchState.trySetNodeInfo(successor, current)) {
                    if (searchProgressLogger != null) {
                        searchProgressLogger
                                .onNeighborGeneration(successor);
                    }
                } else {
                    if (searchProgressLogger != null) {
                        searchProgressLogger
                                .onNeighborImprovement(successor);
                    }
                    
                    searchState.tryUpdateIfImprovementPossible(
                                successor,
                                current);
                }
                
                unlock();
            }
        }
    }
    
    /**
     * This class implements a search thread searching in forward direction.
     */
    private final class ForwardSearchThread<N> 
            extends AbstractSearchThread<N> {

        /**
         * Constructs a forward search thread.
         * 
         * @param id                   the ID of this thread. Must be unique 
         *                             over <b>all</b> search threads.
         * @param nodeExpander         the node expander responsible for 
         *                             generating the neighbour nodes of a given
         *                             node.
         * @param searchState          the search state object.
         * @param sharedSearchState    the shared search state object.
         * @param isMasterThread       indicates whether this thread is a master
         *                             or a slave thread.
         * @param searchProgressLogger the progress logger for logging the 
         *                             progress of this thread.
         * @param threadSleepDuration  the number of milliseconds to sleep 
         *                             whenever a thread finds the frontier 
         *                             queue empty.
         * @param threadSleepTrials    the maximum number of times a master
         *                             thread hibernates itself before giving 
         *                             up.
         */
        ForwardSearchThread(
                final int id,
                final AbstractNodeExpander<N> nodeExpander,
                final SearchState<N> searchState, 
                final SharedSearchState<N> sharedSearchState,
                final boolean isMasterThread,
                final ProgressLogger<N> searchProgressLogger,
                final long threadSleepDuration,
                final int threadSleepTrials,
                final long expansionJoinDuration,
                final AbstractDelayedGraphPathFinder<N> finder) {
            
            super(id,
                  nodeExpander,
                  searchState, 
                  sharedSearchState,
                  isMasterThread,
                  searchProgressLogger,
                  threadSleepDuration,
                  threadSleepTrials,
                  expansionJoinDuration,
                  finder);
        }
    }

    /**
     * This class implements a search thread searching in backward direction.
     */
    private final class BackwardSearchThread<N> 
            extends AbstractSearchThread<N> {

        /**
         * Constructs a backward search thread.
         * 
         * @param id                   the ID of this thread. Must be unique 
         *                             over <b>all</b> search threads.
         * @param nodeExpander         the node expander responsible for 
         *                             generating the neighbour nodes of a given
         *                             node.
         * @param searchState          the search state object.
         * @param sharedSearchState    the shared search state object.
         * @param isMasterThread       indicates whether this thread a master or
         *                             a slave thread.
         * @param searchProgressLogger the progress logger for logging the 
         *                             progress of this thread.
         * @param threadSleepDuration  the number of milliseconds to sleep 
         *                             whenever a slave thread finds the
         *                             frontier queue empty.
         * @param threadSleepTrials    the maximum number of times a master
         *                             thread hibernates itself before giving 
         *                             up.
         */
        BackwardSearchThread(final int id,
                             final AbstractNodeExpander<N> nodeExpander,
                             final SearchState<N> searchState, 
                             final SharedSearchState<N> sharedSearchState,
                             final boolean isMasterThread,
                             final ProgressLogger<N> searchProgressLogger,
                             final long threadSleepDuration,
                             final int threadSleepTrials,
                             final long expansionJoinDuration,
                             final AbstractDelayedGraphPathFinder<N> finder) {
           super(id,
                 nodeExpander,
                 searchState,
                 sharedSearchState,
                 isMasterThread,
                 searchProgressLogger,
                 threadSleepDuration,
                 threadSleepTrials,
                 expansionJoinDuration,
                 finder);
        }
    }
    
    private final class ConfigurationInfo {
        
        private final int numberOfThreads =
                ThreadPoolBidirectionalBFSPathFinder.this.numberOfThreads;
        
        private final int masterThreadTrials = 
                ThreadPoolBidirectionalBFSPathFinder.this.masterThreadTrials;
        
        private final long masterThreadSleepDuration = 
                ThreadPoolBidirectionalBFSPathFinder
                .this
                .masterThreadSleepDurationNanos;
        
        private final long slaveThreadSleepDuration = 
                ThreadPoolBidirectionalBFSPathFinder
                .this
                .slaveThreadSleepDurationNanos;
        
        private final long expansionJoinDurationNanos =
                ThreadPoolBidirectionalBFSPathFinder
                .this
                .expansionJoinDurationNanos;
        
        private final long lockWaitDurationNanos = 
                ThreadPoolBidirectionalBFSPathFinder.this.lockWaitDurationNanos;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            
            sb.append(String.format("numberOfThreads:            %,d.\n", numberOfThreads));
            sb.append(String.format("masterThreadTrials:         %,d.\n", masterThreadTrials));
            sb.append(String.format("masterThreadSleepDuration:  %,d.\n", masterThreadSleepDuration));
            sb.append(String.format("slaveThreadSleepDuration:   %,d.\n", slaveThreadSleepDuration));
            sb.append(String.format("expansionJoinDurationNanos: %,d.\n", expansionJoinDurationNanos));
            sb.append(String.format("lockWaitDurationNanos:      %,d.\n", lockWaitDurationNanos));
            
            return sb.toString();
        }
    }
    
    /**
     * This method puts the calling thread to sleep for {@code milliseconds}
     * milliseconds.
     * 
     * @param nanoseconds the number of milliseconds to sleep for.
     */
    private static void mysleep(final long nanoseconds) {
        try {
            Thread.sleep(nanoseconds / 1_000_000L, 
                         (int)(nanoseconds % 1_000_000L));
        } catch (final InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Interrupted while sleeping: {}.", ex);
        }
    }
}
