# Using the API #

## Building the path finder ##

In order to build the [ThreadPoolBidirectionalBFSPathFinder](https://github.com/coderodde/ThreadPoolBidirectionalBFSPathFinder.java/blob/main/src/main/java/com/github/coderodde/graph/pathfinding/delayed/impl/ThreadPoolBidirectionalBFSPathFinder.java), you have the following (weak) fluent API:

```java
AbstractDelayedGraphPathFinder<DirectedGraphNode> 
            pathfinder = 
                ThreadPoolBidirectionalBFSPathFinderBuilder
                .<DirectedGraphNode>begin()
                .withNumberOfRequestedThreads(REQUESTED_NUMBER_OF_THREADS)
                .withMasterThreadSleepDurationMillis(MASTER_THREAD_SLEEP_DURATION)
                .withSlaveThreadSleepDurationMillis(SLAVE_THREAD_SLEEP_DURATION)
                .withNumberOfMasterTrials(MASTER_THREAD_TRIALS)
                .withJoinDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)
                .withLockWaitMillis(LOCK_WAIT_DURATION_MILLIS)
                .end();
```

Above, any chained method whose name starts with `with` may be ommitted, in which case the default value will be used.

## Invoking the search ##

In order to build the pathfinding invocation, there are four options available:

```java
List<DirectedGraphNode> path;

// Search in undirected graph without progress logging:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withUndirectedGraphNodeExpander(forwardNodeExpander)
                .search();

// Search in undirected graph with progress logging:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withUndirectedGraphNodeExpander(forwardNodeExpander)
                .withForwardSearchProgressLogger(null)
                .withBackwardSearchProgressLogger(null)
                .withSharedSearchProgressLogger(null)
                .search();

// Search in directed graph without progress logging:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withForwardNodeExpander(forwardNodeExpander)
                .withBackwardNodeExpander(backwardNodeExpander)
                .search();

// Search in directed graph with progress logging:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder.
                <DirectedGraphNode>withPathFinder(pathfinder)
                .withSourceNode(source)
                .withTargetNode(target)
                .withForwardNodeExpander(forwardNodeExpander)
                .withBackwardNodeExpander(backwardNodeExpander)
                .withForwardSearchProgressLogger(null)
                .withBackwardSearchProgressLogger(null)
                .withSharedSearchProgressLogger(null)
                .search();
```
