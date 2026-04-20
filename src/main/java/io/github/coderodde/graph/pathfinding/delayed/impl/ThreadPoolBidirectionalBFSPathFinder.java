package io.github.coderodde.graph.pathfinding.delayed.impl;

import io.github.coderodde.graph.pathfinding.delayed.AbstractDelayedGraphPathFinder;
import io.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import io.github.coderodde.graph.pathfinding.delayed.DirectionProgressListener;
import io.github.coderodde.graph.pathfinding.delayed.SharedSearchProgressListener;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a parallel, bidirectional breadth-first search in order
 * to find an unweighted (not necessarily <b>shortest</b>) path from a given
 * source node to a given target node. The underlying algorithm is the 
 * bidirectional breadth-first search. However, multiple threads may work on a 
 * single search direction in order to speed up the computation: for each search 
 * direction (forward and backward), the algorithm maintains concurrent state, 
 * such as the frontier queue; many threads may pop the queue, expand the node 
 * and append the neighbours to that queue.
 * 
 * Basically, this concurrent algoirthm solves the <i>earliest path problem</i>.
 * The algorithm should benefit from multithreading in case the input graph is
 * <i>delayed</i>, i.e., generating successors of a node takes substantial time
 * such as, for example, half a second.
 * 
 * @param <N> the actual graph node type.
 */
public final class ThreadPoolBidirectionalBFSPathFinder<N> 
extends AbstractDelayedGraphPathFinder<N> {
    
    /**
     * 
     */
    private static final Map<ThreadPoolBidirectionalBFSPathFinder,
                             SharedSearchState> INSTANCE_MAP = 
                            new ConcurrentHashMap<>();
    
    /**
     * The default number of threads performing the search in one direction. One
     * thread is reserved for the master thread and another remaining for the 
     * slave threads.
     */
    public static final int DEFAULT_NUMBER_OF_THREADS = 8;

    /**
     * The minimum number of threads to allow. One thread per each of the two
     * search directions.
     */
    static final int MINIMUM_NUMBER_OF_THREADS = 4;
    
    /**
     * The default number of nanoseconds a master thread sleeps when it finds
     * the frontier queue empty.
     */
    public static final long
            DEFAULT_MASTER_THREAD_SLEEP_DURATION_NANOS = 10_000_000L;

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
     * The lower bound on the amount of master thread trials.
     */
    static final int MINIMUM_NUMBER_OF_MASTER_TRIALS = 1;
    
    /**
     * The default number of nanoseconds to wait on thread joining.
     */
    public static final long DEFAULT_EXPANSION_JOIN_DURATION_NANOS = 
            4_000_000_000L;
    
    /**
     * The default number of nanoseconds to wait on thread joining.
     */
    public static final int DEFAULT_EXPANSION_JOIN_DURATION_MILLIS = 4_000;
    
    /**
     * The minimum number of nanoseconds a <b>master thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final long MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS = 1L;

    /**
     * The minimum number of nanoseconds a <b>slave thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final long MINIMUM_SLAVE_THREAD_SLEEP_DURATION_NANOS = 1L;
    
    /**
     * The minimum number of nanoseconds to wait upon thread joining.
     */
    static final long MINIMUM_EXPANSION_JOIN_DURATION_NANOS = 1_000_000L;
    
    /**
     * Caches the requested number of forward threads to use in the search 
     * process.
     */
    private final int numberOfForwardThreads;

    /**
     * Caches the requested number of backward threads to use in the search 
     * process.
     */
    private final int numberOfBackwardThreads;

    /**
     * The duration of sleeping in nanoseconds for the master threads.
     */
    private final long masterThreadSleepDurationNanos;

    /**
     * The duration of sleeping in nanoseconds for the slave threads.
     */
    private final long slaveThreadSleepDurationNanos;

    /**
     * While a master thread waits the frontier queue to become non-empty, the
     * master thread makes at most {@code masterThreadTrials} sleeping sessions
     * before giving up and terminating the search.
     */
    private final int masterThreadTrials;
    
    /**
     * The maximum number of nanoseconds for waiting the expansion thread.
     */
    private final long expansionJoinDurationNanos;
    
    /**
     * Indicates whether the current search is halted.
     */
    private volatile boolean wasHalted = false;
    
    private final SharedSearchProgressListener<N> sharedSearchProgressListener;
    
    /**
     * The forward search progress logger.
     */
    private final DirectionProgressListener<N> forwardProgressLogger;
    
    /**
     * The backward search progress logger.
     */
    private final DirectionProgressListener<N> backwardProgressLogger;
    
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
     * @param numberOfForwardThreads  the number of forward search threads.
     * @param numberOfBackwardThreads the number of backward search threads.
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
     * @param sharedProgressListener the shared search progress listener.
     * @param forwardProgressListener  the forward search progress listener.
     * @param backwardProgressListener the backward search progress listener.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int  numberOfForwardThreads,
            final int  numberOfBackwardThreads,
            final long masterThreadSleepDurationNanos,
            final long slaveThreadSleepDurationNanos,
            final int  masterThreadTrials,
            final long expansionThreadJoinDurationNanos,
            final SharedSearchProgressListener<N> sharedProgressListener,
            final DirectionProgressListener<N> forwardProgressListener,
            final DirectionProgressListener<N> backwardProgressListener) {
        
        this.numberOfForwardThreads = Math.max(numberOfForwardThreads, 
                                               MINIMUM_NUMBER_OF_THREADS);
        
        this.numberOfBackwardThreads = Math.max(numberOfBackwardThreads, 
                                                MINIMUM_NUMBER_OF_THREADS);

        this.masterThreadSleepDurationNanos = 
                Math.max(masterThreadSleepDurationNanos,
                         MINIMUM_MASTER_THREAD_SLEEP_DURATION_NANOS);

        this.slaveThreadSleepDurationNanos = 
                Math.max(slaveThreadSleepDurationNanos,
                         MINIMUM_SLAVE_THREAD_SLEEP_DURATION_NANOS);

        this.masterThreadTrials = 
                Math.max(masterThreadTrials,
                         MINIMUM_NUMBER_OF_MASTER_TRIALS);
        
        this.expansionJoinDurationNanos = 
                Math.max(expansionThreadJoinDurationNanos,
                         MINIMUM_EXPANSION_JOIN_DURATION_NANOS);
        
        this.sharedSearchProgressListener = sharedProgressListener;
        this.forwardProgressLogger  = forwardProgressListener;
        this.backwardProgressLogger = backwardProgressListener;
    }
    
    /**
     * Constructs this path finder.
     * 
     * @param numberOfForwardThreads  the number of forward search threads.
     * @param numberOfBackwardThreads the number of backward search threads.
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
            final int  numberOfForwardThreads,
            final int  numberOfBackwardThreads,
            final long masterThreadSleepDurationNanos,
            final long slaveThreadSleepDurationNanos,
            final int  masterThreadTrials,
            final long expansionThreadJoinDurationNanos) {
        
        this(numberOfForwardThreads,
             numberOfBackwardThreads,
             masterThreadSleepDurationNanos,
             slaveThreadSleepDurationNanos,
             masterThreadTrials,
             expansionThreadJoinDurationNanos,
             null,
             null,
             null);
    }

    /**
     * Construct this path finder using default sleeping duration.
     * 
     * @param numberOfForwardThreads the number of forward search threads.
     * @param numberOfBackwardThreads the number of backward search threads.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int numberOfForwardThreads,
            final int numberOfBackwardThreads) {
        
        this(numberOfForwardThreads, 
             numberOfBackwardThreads,
             DEFAULT_MASTER_THREAD_SLEEP_DURATION_NANOS,
             DEFAULT_SLAVE_THREAD_SLEEP_DURATION_NANOS,
             DEFAULT_NUMBER_OF_MASTER_TRIALS,
             DEFAULT_EXPANSION_JOIN_DURATION_NANOS);
    }
    
    /**
     * Constructs the pathfinder. There will be {@code numberOfThreads} forward
     * search threads and {@code numberOfThreads} backward threads.
     * 
     * @param numberOfThreads the number of search threads per direction.
     */
    public  ThreadPoolBidirectionalBFSPathFinder(final int numberOfThreads) {
        this(numberOfThreads, numberOfThreads);
    }
    
    /**
     * Constructs a pathfinder with all default parameters.
     */
    public ThreadPoolBidirectionalBFSPathFinder() {
        this(DEFAULT_NUMBER_OF_THREADS);
    }
    
    public SharedSearchProgressListener getSharedSearchProgressListener() {
        return sharedSearchProgressListener;    
    }
    
    public DirectionProgressListener getForwardProgressListener() {
        return forwardProgressLogger;
    }
    
    public DirectionProgressListener getBackwardProgressListener() {
        return backwardProgressLogger;
    }
    
    public int getNumberOfForwardThreads() {
        return numberOfForwardThreads;
    }
    
    public int getNumberOfBackwardThreads() {
        return numberOfBackwardThreads;
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

    /**
     * {@inheritDoc }
     */
    @Override
    public List<N> 
        search(final N source, 
               final N target, 
               final AbstractNodeExpander<N> forwardSearchNodeExpander, 
               final AbstractNodeExpander<N> backwardSearchNodeExpander, 
               final SharedSearchProgressListener<N> sharedSearchProgressListener,
               final DirectionProgressListener<N> forwardSearchProgressListener, 
               final DirectionProgressListener<N> 
                       backwardSearchProgressListener) {
            
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
        if (sharedSearchProgressListener != null) {
            sharedSearchProgressListener.onBeginSearch(source, target);
        }

        if (source.equals(target)) {
            // Bidirectional BFS cannot handle this edge case properly, so 
            // hand-coded:
            if (sharedSearchProgressListener != null) {
                sharedSearchProgressListener.onShortestPath(List.of(source));
            }
                
            return List.of(source);
        }
        
        // This path finder collects some performance related statistics:
        this.duration = System.currentTimeMillis();

        // Create the state object shared by both the search direction:
        final SharedSearchState<N> sharedSearchState = 
                new SharedSearchState<>();
        
        INSTANCE_MAP.put(this, sharedSearchState);

        // Create the state obj6/ect shared by all the threads working on forward
        // search direction:
        final SearchState<N> forwardSearchState = new SearchState<>(source);

        // Create the state object shared by all the threads working on backward
        // search direction:
        final SearchState<N> backwardSearchState = new SearchState<>(target);
        
        sharedSearchState.setForwardSearchState(forwardSearchState);
        sharedSearchState.setBackwardSearchState(backwardSearchState);
        
        // The array holding all forward slave threads and the forward master
        // thread:
        final ForwardSearchThread<N>[] forwardSearchThreads =
                new ForwardSearchThread[numberOfForwardThreads + 1];

        // Below, the value of 'sleepDuration' is ignored since the thread being 
        // created is a master thread that never sleeps.
        forwardSearchThreads[0] = 
                new ForwardSearchThread<>(0, 
                                          forwardSearchNodeExpander,
                                          forwardSearchState,
                                          sharedSearchState,
                                          true,
                                          forwardSearchProgressListener,
                                          masterThreadSleepDurationNanos,
                                          masterThreadTrials,
                                          expansionJoinDurationNanos,
                                          this);

        // Spawn the forward search master thread:
        forwardSearchState.introduceThread(forwardSearchThreads[0]);           
        forwardSearchThreads[0].start();

        // Create and spawn all the slave threads working on forward search 
        // direction.
        for (int i = 1; i < forwardSearchThreads.length; ++i) {
            forwardSearchThreads[i] = 
                    new ForwardSearchThread<>(i,
                                              forwardSearchNodeExpander,
                                              forwardSearchState,
                                              sharedSearchState,
                                              false,
                                              forwardSearchProgressListener,
                                              slaveThreadSleepDurationNanos,
                                              masterThreadTrials,
                                              expansionJoinDurationNanos,
                                              this);

            forwardSearchState.introduceThread(forwardSearchThreads[i]);
            forwardSearchThreads[i].start();
        }

        // The array holding all backward slave threads and the backward master
        // thread:
        final BackwardSearchThread<N>[] backwardSearchThreads =
                new BackwardSearchThread[numberOfBackwardThreads + 1];

        // Below, the value of 'sleepDuration' is ignored since the thread being
        // created is a master thread that never sleeps.
        backwardSearchThreads[0] = 
                new BackwardSearchThread<>(forwardSearchThreads.length,
                                           backwardSearchNodeExpander,
                                           backwardSearchState,
                                           sharedSearchState,
                                           true,
                                           backwardSearchProgressListener,
                                           masterThreadSleepDurationNanos,
                                           masterThreadTrials,
                                           expansionJoinDurationNanos,
                                           this);

        // Spawn the backward search master thread:
        backwardSearchState.introduceThread(backwardSearchThreads[0]);
        backwardSearchThreads[0].start();

        // Create and spawn all the slave threads working on backward search
        // direction:
        for (int i = 1; i < backwardSearchThreads.length; ++i) {
            backwardSearchThreads[i] = 
                    new BackwardSearchThread<>(forwardSearchThreads.length + i,
                                               backwardSearchNodeExpander,
                                               backwardSearchState,
                                               sharedSearchState,
                                               false,
                                               backwardSearchProgressListener,
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
        
        for (final AbstractSearchThread<N> thread 
                : forwardSearchState.runningThreadSet) {
            
            this.numberOfExpandedNodes += thread.numberOfExpandedNodes;
        }
        
        for (final AbstractSearchThread<N> thread 
                : backwardSearchState.runningThreadSet) {
            
            this.numberOfExpandedNodes += thread.numberOfExpandedNodes;
        }

        INSTANCE_MAP.remove(this);
        sharedSearchState.lock();
        List<N> path = sharedSearchState.getShortestPath();
        sharedSearchState.unlock();
        
        if (sharedSearchProgressListener != null) {
            if (path.isEmpty()) {
                sharedSearchProgressListener.onTargetUnreachable(source, 
                                                                 target);
            } else {
                sharedSearchProgressListener.onShortestPath(path);
            }
        }
        
        return path;
    }

    @Override
    public void halt() {
        final SharedSearchState<N> sharedSearchState = INSTANCE_MAP.get(this);
        
        if (sharedSearchState != null) {
            wasHalted = true;
            sharedSearchState.requestGlobalHalt();
        }
    }
    
    @Override
    public boolean wasHalted() {
        return wasHalted;
    }

    private static final class ExpansionThread<N> extends Thread {
        
        private final N node;
        private final AbstractNodeExpander<N> expander;
        private volatile List<N> successorList;
        
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
                LOGGER.log(Level.SEVERE, 
                           "Could not expand article node \"{0}\": {1}", 
                           new Object[]{ node, ex });
                
                successorList = List.of();
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
         * The global flag indicating that the search must halt immediately and
         * prematurely.
         */
        private volatile boolean haltRequested = false;

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
         * Caches the best known length from the source node to the target node.
         */
        private volatile int bestPathLengthSoFar = Integer.MAX_VALUE;
        
        /**
         * The best search frontier touch node so far.
         */
        private volatile N touchNode;
        
        /**
         * Stores the shortest path. Empty, if none found.
         */
        private final List<N> shortestPath = new ArrayList<>();
        
        /**
         * Returns {@code true} if and only if the earliest path is found.
         * 
         * @return a flag indicating that the earliest path is found.
         */
        private boolean pathIsReady() {
            if (touchNode == null) {
                return false;
            }
            
            final N forwardSearchHead  = forwardSearchState.peekQueueHead();
            final N backwardSearchHead = backwardSearchState.peekQueueHead();
            
            if (forwardSearchHead == null || backwardSearchHead == null) {
                return false;
            }
            
            final int distance =
                    forwardSearchState .getDistanceOf(forwardSearchHead) +
                    backwardSearchState.getDistanceOf(backwardSearchHead);
            
            return distance > bestPathLengthSoFar;
        }
        
        /**
         * Requests the premature halt.
         */
        private void requestGlobalHalt() {
            haltRequested = true;
        }
        
        /**
         * Reads the halting flag.
         * 
         * @return the halting flag. 
         */
        boolean globalHaltRequested() {
            return haltRequested;
        }
        
        private void setForwardSearchState(
                final SearchState<N> forwardSearchState) {
            
            this.forwardSearchState = forwardSearchState;
        }
        
        private void setBackwardSearchState(
                final SearchState<N> backwardSearchState) {
            this.backwardSearchState = backwardSearchState;
        }
        
        /**
         * Locks this shared state.
         * 
         * @throws Exception if mutex acquisition fails.
         */
        private void lock() {
            mutex.acquireUninterruptibly();
        }
        
        /**
         * Unlocks this shared state.
         */
        private void unlock() {
            mutex.release();
        }
        
        /**
         * Tries to update the meeting node.
         * 
         * @param current the meeting node candidate. 
         */
        private void updateTouchNode(final N current) {
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

        /**
         * Returns a shortest path and returns it as a list. If the target node 
         * is unreachable from the source node, returns an empty list.
         * 
         * @return a shortest path found, or an empty list if target node is not 
         *         reachable from the source node.
         * 
         * @throws Exception if mutex acquisition fails.
         */
        private List<N> getShortestPath() {
            loadShortestPath();
            return shortestPath;
        }
        
        /**
         * Loads a shortest path if there is any path at all.
         */
        private void loadShortestPath() {
            if (touchNode == null) {
                // No paths at all.
                return;
            }
            
            shortestPath.clear();
            
            N current = touchNode;

            while (current != null) {
                shortestPath.add(current);
                current = forwardSearchState.parents.get(current);
            }

            Collections.reverse(shortestPath);
            current = backwardSearchState.parents.get(touchNode);

            while (current != null) {
                shortestPath.add(current);
                current = backwardSearchState.parents.get(current);
            }
        } 
    }

    /**
     * This class holds all the state of a single search direction.
     */
    private static final class SearchState<N> {
        
        /**
         * The search frontier FIFO queue.
         */
        private final Deque<N> queue = new ArrayDeque<>();
        
        /**
         * This map maps each discovered node to its best distance estimate.
         */
        private final Map<N, Integer> distances = new HashMap<>();
        
        /**
         * This map maps each discovered node to its predecessor on the current 
         * shortest path.
         */
        private final Map<N, N> parents = new HashMap<>();
        
        /**
         * The set of all the threads working on this particular direction.
         */
        private final Set<AbstractSearchThread<N>> runningThreadSet = 
                new HashSet<>();

        /**
         * The set of all <b>slave</b> threads that are currently sleeping.
         */
        private final Set<AbstractSearchThread<N>> sleepingThreadSet =
                new HashSet<>();
        
        /**
         * The number of active expansion threads running.
         */
        private int activeExpansions = 0;
        
        /**
         * The mutex for controlling access to the thread sets
         * (woke up/sleeping).
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
        private SearchState(final N initialNode) {
            queue.addLast(initialNode);
            parents.put(initialNode, null);
            distances.put(initialNode, 0);
        }
        
        /**
         * Returns the frontier queue.
         * 
         * @return the frontier queue.
         */
        private Deque<N> getSearchFrontierDeque() {
            return queue;
        }
        
        /**
         * Returns {@code true} only if the {@code node} was previously 
         * encountered.
         * 
         * @param node the node to test.
         * 
         * @return {@code true} if {@code node} was previously encountered.
         */
        private boolean containsNode(final N node) {
            return parents.containsKey(node);
        }
        
        /**
         * Get the best known distance from the terminal node to {@code node}.
         * 
         * @param node the node to test.
         * 
         * @return the best known distance to {@code node}.
         */
        private int getDistanceOf(final N node) {
            return distances.get(node);
        }
        
        /**
         * Returns the head node of the queue or {@code null} if the queue is 
         * empty.
         * 
         * @return the queue head node.
         */
        private N peekQueueHead() {
            return queue.peekFirst();
        }
        
        /**
         * Removes and returns the queue head node if the queue is non-empty.
         * Returns {@code null} otherwise.
         * 
         * @return the queue head node. 
         */
        private N removeQueueHead() {
            if (queue.isEmpty()) {
                return null;
            }
            
            return queue.remove();
        }
        
        /**
         * Attempts to set the arc {@code <predecessor, current>}.
         * 
         * @param current     the head node of the arc.
         * @param predecessor the tail node of the arc.
         * 
         * @return {@code true} if and only if the input arc was not already 
         *         present in the search data structures.
         */
        private boolean trySetNodeInfo(final N current, final N predecessor) {
            if (distances.containsKey(current)) {
                // Nothing to set.
                return false;
            }
            
            distances.put(current, getDistanceOf(predecessor) + 1);
            parents.put(current, predecessor);
            queue.addLast(current);
            return true;
        }
        
        /**
         * Attempts to tighten the distance estimate for {@code node}.
         * 
         * @param node        the node whose distance estimate to tighten.
         * @param predecessor the predecessor node of {@code node}.
         */
        private void tryUpdateIfImprovementPossible(
            final N node, 
            final N predecessor) {
            
            if (distances.get(node) > distances.get(predecessor) + 1) { 
                distances.put(node,   distances.get(predecessor) + 1);
                parents.put(node, predecessor);
            }
        }
        
        /**
         * Locks the mutex for search threads.
         */
        private void lockThreadSetMutex() {
            threadSetsMutex.acquireUninterruptibly();
        }
        
        /**
         * Unlocks the mutex for search threads.
         */
        private void unlockThreadSetMutex() {
            threadSetsMutex.release();
        }
        
        /**
         * Introduces a new thread to this search direction.
         * 
         * @param thread the thread to introduce.
         */
        private void introduceThread(final AbstractSearchThread<N> thread) {
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
        private void putThreadToSleep(final AbstractSearchThread<N> thread) {
            lockThreadSetMutex();
            thread.putThreadToSleep(true);
            runningThreadSet.remove(thread);
            sleepingThreadSet.add(thread);
            unlockThreadSetMutex();
        }
        
        /**
         * Wakes up all the sleeping slave threads.
         */
        private void wakeupAllSleepingThreads() { 
            lockThreadSetMutex();
            
            for (final AbstractSearchThread<N> thread : sleepingThreadSet) {
                thread.putThreadToSleep(false);
                runningThreadSet.add(thread);
            }
            
            sleepingThreadSet.clear();
            unlockThreadSetMutex();
        }
        
        private void incrementActiveExpansions() {
            ++activeExpansions;
        }
        
        private void decrementActiveExpansions() {
            --activeExpansions;
        }
        
        private boolean hasActiveExpansions() {
            return activeExpansions > 0;
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
         * The Boolean flag indicating whether this thread is a master thread. 
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
        protected final DirectionProgressListener<N> searchProgressListener;
        
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
                             final DirectionProgressListener<N> searchProgressLogger,
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
            this.searchProgressListener  = searchProgressLogger;
            this.expansionJoinDurationNanos = expansionJoinDurationNanos;
        }

        /**
         * Runs this search thread.
         */
        @Override
        public void run() {
            while (true) {
                if (sharedSearchState.globalHaltRequested()) {
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
            return String.format("[Thread ID: %d, master: %b]",
                                 threadId,
                                 isMasterThread);
        }
        
        /**
         * Processes the current node in the master thread.
         * 
         * @param head the candidate frontier queue head node.
         */
        private void processCurrentInMasterThread() {
            N head;
            boolean hasActiveExpansions;
            
            lock();
            head = searchState.peekQueueHead();
            hasActiveExpansions = searchState.hasActiveExpansions();
            unlock();
            
            searchState.wakeupAllSleepingThreads();
            
            if (head != null || hasActiveExpansions) {
                return;
            }
            
            N currentHead = null;
            boolean currentHasActiveExpansions = false;
            
            for (int i = 0; i < threadSleepTrials; ++i) {
                mysleep(threadSleepDurationNanos);
                
                lock();
                currentHead = searchState.peekQueueHead();
                currentHasActiveExpansions = searchState.hasActiveExpansions();
                unlock();
                
                if (currentHead != null || currentHasActiveExpansions) {
                    break;
                }
            }
            
            if (currentHead == null && !currentHasActiveExpansions) {
                // No new nodes in the queue. Abandon search:
                sharedSearchState.requestGlobalHalt();
            } else {
                // Wake all slave threads working on this search direction:
                searchState.wakeupAllSleepingThreads();
            }
        }
        
        /**
         * Processes the queue head node in a slave thread.
         */
        private void processCurrentInSlaveThread() {
            if (sharedSearchState.globalHaltRequested()) {
                return;
            }
            
            if (sleepRequested) {
                mysleep(threadSleepDurationNanos);
                return;
            }
            
            lock();
            final N current = searchState.removeQueueHead();
            unlock();
            
            if (current == null) {
                // Frontier queue is empty. Go to sleep zzz...
                searchState.putThreadToSleep(this);
                return;
            }
            
            searchState.wakeupAllSleepingThreads();
            
            lock();
            sharedSearchState.updateTouchNode(current);
            
            if (sharedSearchState.pathIsReady()) {
                unlock();
                sharedSearchState.requestGlobalHalt();
                // Wake all the search threads so that the can read the halt 
                // flag:
//                searchState.wakeupAllSleepingThreads();
                return;
            }
            
            unlock();
            
            ++numberOfExpandedNodes;
            
            final long startTime = System.currentTimeMillis();
            
            // Removes the queue head node from the queue and expands it:
            expand(current);
            
            final long endTime = System.currentTimeMillis();
            final long expansionDuration = endTime - startTime;
            
            if (searchProgressListener != null) {
                searchProgressListener.onExpansion(current, expansionDuration);
            }
        }
        
        /**
         * Locks the global mutex.
         */
        private void lock() {
            sharedSearchState.lock();
        }
        
        /**
         * Unlocks the global mutex.
         */
        private void unlock() {
            sharedSearchState.unlock();
        }
        
        /**
         * Expands the current node.
         * 
         * @param current the node of which to generate the successor nodes.
         */
        private void expand(final N current) {
            if (current == null) {
                // Once here, nothing to do:
                return;
            }
            
            lock();
            searchState.incrementActiveExpansions();
            unlock();
            
            try {
                final ExpansionThread<N> expansionThread = 
                        new ExpansionThread<>(current, nodeExpander);

                expansionThread.setDaemon(true); // Important!
                expansionThread.start();

                try {
//                    System.out.println("yeah " + (expansionJoinDurationNanos));
                    expansionThread.join(
                            expansionJoinDurationNanos / 1_000_000L, 
                      (int)(expansionJoinDurationNanos % 1_000_000L));
                } catch (InterruptedException ex) {
                    // Did not get the response from the node expander:
                    LOGGER.log(Level.SEVERE, "Expansion thread threw: {0}", ex);
                    return;
                }

                if (expansionThread.isAlive()) {
                    LOGGER.log(Level.WARNING,
                               "Expansion of node \"{0}\" times out.", 
                               current);
                    return;
                }

                final List<N> successors = expansionThread.getSuccessorList();

                if (successors == null) {
                    return;
                }

                lock();
                sharedSearchState.updateTouchNode(current);
                unlock();

                // Processes the successors of the current node:
                for (final N successor : successors) {
                    lock();

                    if (searchState.trySetNodeInfo(successor, current)) {
                        if (searchProgressListener != null) {
                            searchProgressListener
                                    .onNeighborGeneration(successor);
                        }
                    } else {
                        if (searchProgressListener != null) {
                            searchProgressListener
                                    .onNeighborImprovement(successor);
                        }

                        searchState.tryUpdateIfImprovementPossible(successor, 
                                                                   current);
                    }

                    unlock();
                }
            } finally {
                lock();
                searchState.decrementActiveExpansions();
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
                final DirectionProgressListener<N> searchProgressLogger,
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
                             final DirectionProgressListener<N> searchProgressLogger,
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