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

// Search in an undirected graph without progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(t)
    .withUndirectedGraphNodeExpander(expander1)
    .search();

// Search in a directed graph without progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(t)
    .withForwardNodeExpander(expander1)
    .withBackwardNodeExpander(expander2)
    .search();

// Search in an undirected graph with progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(t)
    .withUndirectedGraphNodeExpander(expander1)
    .withSharedSearchProgressListener(null)
    .withForwardSearchProgressListener(null)
    .withBackwardSearchProgressListener(null)
    .search();

// Search in a ddirected graph with progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(t)
    .withForwardNodeExpander(expander1)
    .withBackwardNodeExpander(expander2)
    .withSharedSearchProgressListener(null)
    .withForwardSearchProgressListener(null)
    .withBackwardSearchProgressListener(null)
    .search();```
