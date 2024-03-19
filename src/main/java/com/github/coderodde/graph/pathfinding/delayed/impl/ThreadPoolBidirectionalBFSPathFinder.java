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
import java.util.Random;
import java.util.Set;
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
 */
public class ThreadPoolBidirectionalBFSPathFinder<N> 
extends AbstractDelayedGraphPathFinder<N> {
    
    private static final boolean FORWARD_SEARCH_STATE = true;
    private static final boolean BACKWARD_SEARCH_STATE = false;
    
    /**
     * The default number of threads performing the search.
     */
    static final int DEFAULT_NUMBER_OF_THREADS = 8;
    
    /**
     * The default number of milliseconds a master thread sleeps when it finds
     * the frontier queue empty.
     */
    static final int DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS = 10;

    /**
     * The default number of milliseconds a slave thread sleeps when it finds
     * the frontier queue empty.
     */
    static final int DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS = 10;

    /**
     * The default upper bound on the number of times a master thread hibernates
     * due to the frontier queue being empty before the entire search is 
     * terminated.
     */
    static final int DEFAULT_NUMBER_OF_MASTER_TRIALS = 50;
    
    /**
     * The default number of milliseconds to wait on thread joining.
     */
    static final int DEFAULT_EXPANSION_JOIN_DURATION_MILLIS = 1_000;
    
    /**
     * The default number of milliseconds to wait for the lock. 
     */
    static final int DEFAULT_LOCK_WAIT_MILLIS = 5;

    /**
     * The minimum number of threads to allow. One thread per each of the two
     * search directions.
     */
    static final int MINIMUM_NUMBER_OF_THREADS = 4;

    /**
     * The minimum number of milliseconds a <b>master thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final int MINIMUM_MASTER_THREAD_SLEEP_DURATION_MILLIS = 1;

    /**
     * The minimum number of milliseconds a <b>slave thread</b> sleeps when it 
     * finds the frontier queue empty.
     */
    static final int MINIMUM_SLAVE_THREAD_SLEEP_DURATION_MILLIS = 1;

    /**
     * The lower bound on the amount of trials.
     */
    static final int MINIMUM_NUMBER_OF_TRIALS = 1;
    
    /**
     * The minimum number of milliseconds to wait upon thread joining.
     */
    static final int MINIMUM_EXPANSION_JOIN_DURATION_MILLIS = 1;
    
    /**
     * The minimum number of milliseconds to wait for the lock.
     */
    static final int MINIMUM_LOCK_WAIT_MILLIS = 1;

    /**
     * Caches the requested number of threads to use in the search process. This
     * field accounts for both forward and backward search direction.
     */
    private final int numberOfThreads;

    /**
     * The duration of sleeping in milliseconds for the master threads.
     */
    private final int masterThreadSleepDurationMillis;

    /**
     * The duration of sleeping in milliseconds for the slave threads.
     */
    private final int slaveThreadSleepDurationMillis;

    /**
     * While a master thread waits the frontier queue to become non-empty, the
     * master thread makes at most {@code masterThreadTrials} sleeping sessions
     * before giving up and terminating the search.
     */
    private final int masterThreadTrials;
    
    /**
     * The maximum number of milliseconds for waiting the expansion thread.
     */
    private final int expansionJoinDurationMillis;
    
    /**
     * The maximum number of milliseconds to wait for the lock.
     */
    private final int lockWaitDurationMillis;
    
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
     * @param masterThreadSleepDurationMillis the number of milliseconds a master 
     *                                  thread sleeps whenever it discovers the
     *                                  frontier queue being empty.
     * @param slaveThreadSleepDurationMillis  the number of milliseconds a slave
     *                                  thread sleeps whenever it discovers the
     *                                  frontier queue being empty.
     * @param masterThreadTrials        the number of times the master thread
     *                                  hibernates itself before terminating the
     *                                  entire search.
     * @param expansionThreadJoinDurationMillis the number of milliseconds to 
     *                                          wait for the expansion thread.
     * @param lockWaitDurationMillis the number of milliseconds to wait for 
     *                               the lock.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int numberOfThreads,
            final int masterThreadSleepDurationMillis,
            final int slaveThreadSleepDurationMillis,
            final int masterThreadTrials,
            final int expansionThreadJoinDurationMillis,
            final int lockWaitDurationMillis) {
        
        this.numberOfThreads = Math.max(numberOfThreads, 
                                        MINIMUM_NUMBER_OF_THREADS);

        this.masterThreadSleepDurationMillis = 
                Math.max(masterThreadSleepDurationMillis,
                         MINIMUM_MASTER_THREAD_SLEEP_DURATION_MILLIS);

        this.slaveThreadSleepDurationMillis = 
                Math.max(slaveThreadSleepDurationMillis,
                         MINIMUM_SLAVE_THREAD_SLEEP_DURATION_MILLIS);

        this.masterThreadTrials = 
                Math.max(masterThreadTrials,
                         MINIMUM_NUMBER_OF_TRIALS);
        
        this.expansionJoinDurationMillis = 
                Math.max(expansionThreadJoinDurationMillis,
                         MINIMUM_EXPANSION_JOIN_DURATION_MILLIS);
        
        this.lockWaitDurationMillis = 
                Math.max(lockWaitDurationMillis,
                         MINIMUM_LOCK_WAIT_MILLIS);
    }

    /**
     * Construct this path finder using default sleeping duration.
     * 
     * @param requestedNumberOfThreads the requested number of search threads.
     */
    public ThreadPoolBidirectionalBFSPathFinder(
            final int requestedNumberOfThreads) {
        
        this(requestedNumberOfThreads, 
             DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS,
             DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS,
             DEFAULT_NUMBER_OF_MASTER_TRIALS,
             DEFAULT_EXPANSION_JOIN_DURATION_MILLIS,
             DEFAULT_LOCK_WAIT_MILLIS);
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
            
        Objects.requireNonNull(forwardSearchNodeExpander, 
                               "The forward search node expander is null.");

        Objects.requireNonNull(backwardSearchNodeExpander,
                               "The backward search node expander is null.");

        // Check the validity of the source node:
        if (!forwardSearchNodeExpander.isValidNode(source)) {
            final String exceptionMessage = 
                    "The source node (" + source + ") was rejected by the " +
                    "forward search node expander.";
            
            LOGGER.log(Level.SEVERE, exceptionMessage);
            
            throw new IllegalArgumentException(exceptionMessage);
        }

        // Check the validity of the target node:
        if (!backwardSearchNodeExpander.isValidNode(target)) {
            final String exceptionMessage = 
                    "The target node (" + target + ") was rejected by the " +
                    "backward search node expander.";
            
            LOGGER.log(Level.SEVERE, exceptionMessage);
            
            throw new IllegalArgumentException(
                    exceptionMessage);
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
                                        lockWaitDurationMillis);

        // Create the state obj6/ect shared by all the threads working on forward
        // search direction:
        final SearchState<N> forwardSearchState = 
                new SearchState<>(source,
                                  FORWARD_SEARCH_STATE);

        // Create the state object shared by all the threads working on backward
        // search direction:
        final SearchState<N> backwardSearchState = 
                new SearchState<>(target, 
                                  BACKWARD_SEARCH_STATE);
        
        sharedSearchState.setForwardSearchState(forwardSearchState);
        sharedSearchState.setBackwardSearchState(backwardSearchState);
        
        forwardSearchState.setOppositeSearchState(backwardSearchState);
        backwardSearchState.setOppositeSearchState(forwardSearchState);

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
                                          masterThreadSleepDurationMillis,
                                          masterThreadTrials,
                                          expansionJoinDurationMillis);

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
                                              slaveThreadSleepDurationMillis,
                                              masterThreadTrials,
                                              expansionJoinDurationMillis);

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
                                           masterThreadSleepDurationMillis,
                                           masterThreadTrials,
                                           expansionJoinDurationMillis);

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
                                               slaveThreadSleepDurationMillis,
                                               masterThreadTrials,
                                               expansionJoinDurationMillis);

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

        // Construct and return the path:
        return sharedSearchState.getPath();
    }
    
    private static final class ExpansionThread<N> extends Thread {
        
        private final N node;
        private final AbstractNodeExpander<N> expander;
        private volatile List<N> successorList = Collections.emptyList();
        
        ExpansionThread(final N node, final AbstractNodeExpander<N> expander) {
            this.node = node;
            this.expander = expander;
        }
        
        @Override
        public void run() {
            successorList = expander.generateSuccessors(node);
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
        private final Semaphore mutex = new Semaphore(1, false);

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
         * The duration in milliseconds for waiting to the lock.
         */
        private final int lockWaitDuration;

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
                          final int lockWaitDuration) {
            this.source = source;
            this.target = target;
            this.sharedProgressLogger = sharedProgressLogger;
            this.lockWaitDuration = lockWaitDuration;
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
                return mutex.tryAcquire(lockWaitDuration, 
                                        TimeUnit.MILLISECONDS);
                
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
            if (forwardSearchState.containsNode(current) &&
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
         * Asks every single thread to exit.
         */
        void requestExit() {
            forwardSearchState .requestThreadsToExit();
            backwardSearchState.requestThreadsToExit();
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
         * The opposite search direction state.
         */
        private SearchState<N> oppositeSearchState;
        
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
         * Is set to {@code true} if this thread is a forward search thread. 
         * {@code false} if this thread is a backward search thread.
         */
        private final boolean isForwardDirectionState;

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
         * The semaphore protecting the {@code runningThreadSet} and
         * {@code sleepingThreadSet}.
         */
        private final Semaphore mutex = new Semaphore(1, true);
        
        /**
         * The mutex for controlling access to the thread sets.
         */
        private final Semaphore threadSetsMutex = new Semaphore(1, true);
        
        /**
         * The random number generator.
         */
        private final Random random = new Random();

        /**
         * Constructs the search state object.
         * 
         * @param initialNode          the node from which the search begins. If
         *                             this state object is used in the forward
         *                             search, this node should be the source 
         *                             node. Otherwise, if this state object is
         *                             used in the backward search, this node
         *                             should be the target node.
         */
        SearchState(final N initialNode, 
                    final boolean isForwardDirectionThread) {
            
            this.isForwardDirectionState = isForwardDirectionThread; 
            queue.add(initialNode);
            parents.put(initialNode, null);
            distance.put(initialNode, 0);
        }
        
        void setOppositeSearchState(SearchState<N> oppositeSearchState) {
            this.oppositeSearchState = oppositeSearchState;
        }
        
        int getUniqueRandomThreadId() {
            while (true) {
                int candidateThreadId = random.nextInt(Integer.MAX_VALUE);
                
                if (threadIdIsUnique(candidateThreadId)) {
                    return candidateThreadId;
                }
            }
        }
        
        private boolean threadIdIsUnique(int threadId) {
            threadSetsMutex.acquireUninterruptibly();
            
            boolean isUnique = true;
            
            for (AbstractSearchThread<N> thread : runningThreadSet) {
                if (thread.getThreadId() == threadId) {
                    isUnique = false;
                    break;
                }
            }
            
            for (AbstractSearchThread<N> thread : sleepingThreadSet) {
                if (thread.getThreadId() == threadId) {
                    isUnique = false;
                    break;
                }
            }
            
            threadSetsMutex.release();
            
            return isUnique;
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
            mutex.acquireUninterruptibly();
        }
        
        void unlockThreadSetMutex() {
            mutex.release();
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

        /**
         * Tells all the thread working on current direction to exit so that the
         * threads may be joined.
         */
        void requestThreadsToExit() {
            lockThreadSetMutex();
            
            for (final StoppableThread thread : runningThreadSet) {
                thread.requestThreadToExit();
            }
            
            for (final StoppableThread thread : sleepingThreadSet) {
                thread.requestThreadToExit();
            }
            
            unlockThreadSetMutex();
        }
    }

    /**
     * This abstract class defines a thread that may be asked to terminate.
     */
    private abstract static class StoppableThread extends Thread {

        /**
         * If set to {@code true}, this thread should exit.
         */
        private volatile boolean exit;

        /**
         * Returns the value of the exit flag.
         * 
         * @return the value of the exit flag.
         */
        boolean getExitFlag() {
            return exit;
        }
        
        /**
         * Sends a request to finish the work.
         */
        void requestThreadToExit() {
            exit = true;
        }
    }

    /**
     * This abstract class defines a thread that may be asked to go to sleep.
     */
    private abstract static class SleepingStoppableThread 
            extends StoppableThread {

        /**
         * Holds the flag indicating whether this thread is put to sleep.
         */
        protected volatile boolean sleepRequested = false;

        /**
         * The number of milliseconds to sleep during each hibernation.
         */
        protected final int threadSleepDuration;

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
         * @param threadSleepDuration the number of milliseconds to sleep each 
         *                            time.
         * @param threadSleepTrials   the maximum number of trials to hibernate
         *                            a master thread before giving up.
         */
        SleepingStoppableThread(final int threadSleepDuration,
                                final int threadSleepTrials,
                                final boolean isMasterThread) {
            this.threadSleepDuration = threadSleepDuration;
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
    private abstract static class AbstractSearchThread<N> 
            extends SleepingStoppableThread {

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
        private final int expansionJoinDuration;

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
         * @param threadSleepDuration  the duration of sleeping in milliseconds
         *                             always when a thread finds the frontier 
         *                             queue empty.
         * @param threadSleepTrials    the maximum number of hibernation trials
         *                             before a master thread gives up and 
         *                             terminates the entire search process. If
         *                             this thread is a slave thread, this 
         *                             parameter is ignored.
         */
        AbstractSearchThread(final int id,
                             final AbstractNodeExpander<N> nodeExpander,
                             final SearchState<N> searchState, 
                             final SharedSearchState<N> sharedSearchState,
                             final boolean isMasterThread,
                             final ProgressLogger<N> searchProgressLogger,
                             final int threadSleepDuration,
                             final int threadSleepTrials,
                             final int expansionJoinDuration) {
            
            super(threadSleepDuration, 
                  threadSleepTrials, 
                  isMasterThread);
            
            this.threadId                    = id;
            this.nodeExpander          = nodeExpander;
            this.searchState           = searchState;
            this.sharedSearchState     = sharedSearchState;
            this.searchProgressLogger  = searchProgressLogger;
            this.expansionJoinDuration = expansionJoinDuration;
        }

        @Override
        public void run() {
            while (true) {
                if (getExitFlag()) {
                    return;
                }
                
                if (sleepRequested) {
                    mysleep(threadSleepDuration);
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
        
        int getThreadId() {
            return threadId;
        }

        /**
         * Returns the number of nodes expanded by this search thread.
         * 
         * @return the number of nodes.
         */
        int getNumberOfExpandedNodes() {
            return numberOfExpandedNodes;
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
                mysleep(threadSleepDuration);
                currentHead = searchState.getQueueHead();
                
                if (currentHead != null) {
                    break;
                }
            }
            
            if (currentHead == null) {
                // We have run out of trials and the queue is still empty; halt.
                sharedSearchState.requestExit();
            } else {
                searchState.wakeupAllSleepingThreads();
            }
        }
        
        private void processCurrentInSlaveThread() {
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
            unlock();
            
            if (sharedSearchState.pathIsOptimal()) {
                sharedSearchState.requestExit();
                return;
            }
            
            if (searchProgressLogger != null) {
                searchProgressLogger.onExpansion(current);
            }
            
            numberOfExpandedNodes++;
            
            expand();
        }
        
        private void lock() {
            while (!sharedSearchState.lock());
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
            
            ExpansionThread<N> expansionThread =
                    new ExpansionThread<>(current, nodeExpander);
            
            expansionThread.start();
            
            mysleep(expansionJoinDuration);

            if (expansionThread.isAlive()) {
                lock();
                searchState.oppositeSearchState.queue.remove(current);
                unlock();
                
                // Stop this thread.
                requestThreadToExit();
                
                searchState.threadSetsMutex.acquireUninterruptibly();
                searchState.runningThreadSet.remove(this);
                searchState.sleepingThreadSet.remove(this);
                searchState.threadSetsMutex.release();
                
                AbstractSearchThread<N> thread; 
                
                // Spawn another, new thread to continue instead of this stuck
                // thread:
                if (searchState.isForwardDirectionState) {
                    
                    thread = 
                        new ForwardSearchThread<>(
                            searchState.getUniqueRandomThreadId(),
                            nodeExpander,
                            searchState,
                            sharedSearchState,
                            false,
                            searchProgressLogger,
                            threadSleepDuration,
                            threadSleepTrials,
                            expansionJoinDuration);
                    
                    
                } else {
                    
                    thread = 
                        new ForwardSearchThread<>(
                            searchState.getUniqueRandomThreadId(),
                            nodeExpander,
                            searchState,
                            sharedSearchState,
                            false,
                            searchProgressLogger,
                            threadSleepDuration,
                            threadSleepTrials,
                            expansionJoinDuration);
                }
                
                thread.sleepRequested = false;
                thread.start();
                
                searchState.threadSetsMutex.acquireUninterruptibly();
                searchState.runningThreadSet.add(thread);
                searchState.threadSetsMutex.release();
                
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
    private static final class ForwardSearchThread<N> 
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
                final int threadSleepDuration,
                final int threadSleepTrials,
                final int expansionJoinDuration) {
            
            super(id,
                  nodeExpander,
                  searchState, 
                  sharedSearchState,
                  isMasterThread,
                  searchProgressLogger,
                  threadSleepDuration,
                  threadSleepTrials,
                  expansionJoinDuration);
        }
    }

    /**
     * This class implements a search thread searching in backward direction.
     */
    private static final class BackwardSearchThread<N> 
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
                             final int threadSleepDuration,
                             final int threadSleepTrials,
                             final int expansionJoinDuration) {
           super(id,
                 nodeExpander,
                 searchState,
                 sharedSearchState,
                 isMasterThread,
                 searchProgressLogger,
                 threadSleepDuration,
                 threadSleepTrials,
                 expansionJoinDuration);
        }
    }
    
    /**
     * This method puts the calling thread to sleep for {@code milliseconds}
     * milliseconds.
     * 
     * @param milliseconds the number of milliseconds to sleep for.
     */
    private static void mysleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Interrupted while sleeping: {}.", ex);
        }
    }
}
