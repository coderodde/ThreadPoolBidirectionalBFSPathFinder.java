# Using the API #

## Building the path finder ##

In order to build the [ThreadPoolBidirectionalBFSPathFinder](https://github.com/coderodde/ThreadPoolBidirectionalBFSPathFinder.java/blob/main/src/main/java/com/github/coderodde/graph/pathfinding/delayed/impl/ThreadPoolBidirectionalBFSPathFinder.java), you have the following (weak) fluent API:

```java
AbstractDelayedGraphPathFinder<DirectedGraphNode> 
    bfsPathFinder = 
        ThreadPoolBidirectionalPathFinderBuilder
        .<DirectedGraphNode>begin()
        .withNumberOfForwardThreads(REQUESTED_NUMBER_OF_THREADS_FORWARD)
        .withNumberOfBackwardThreads(
                REQUESTED_NUMBER_OF_THREADS_BACKWARD)
        .withExpansionDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)
        .withMasterThreadSleepDurationMillis(
                MASTER_THREAD_SLEEP_DURATION_MILLIS)
        .withSlaveThreadSleepDurationMillis(
                SLAVE_THREAD_SLEEP_DURATION_MILLIS)
        .withNumberOfMasterTrials(MASTER_THREAD_TRIALS)
        .usingBfs();
```

In order to build the [ThreadPoolBidirectionalDijkstraPathFinder](https://github.com/coderodde/ThreadPoolBidirectionalBFSPathFinder.java/blob/main/src/main/java/com/github/coderodde/graph/pathfinding/delayed/impl/ThreadPoolBidirectionalDijkstraPathFinder.java), you have the following (weak) fluent API:

```java
AbstractDelayedGraphPathFinder<DirectedGraphNode> 
    bfsPathFinder = 
        ThreadPoolBidirectionalPathFinderBuilder
        .<DirectedGraphNode>begin()
        .withNumberOfForwardThreads(REQUESTED_NUMBER_OF_THREADS_FORWARD)
        .withNumberOfBackwardThreads(
                REQUESTED_NUMBER_OF_THREADS_BACKWARD)
        .withExpansionDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)
        .withMasterThreadSleepDurationMillis(
                MASTER_THREAD_SLEEP_DURATION_MILLIS)
        .withSlaveThreadSleepDurationMillis(
                SLAVE_THREAD_SLEEP_DURATION_MILLIS)
        .withNumberOfMasterTrials(MASTER_THREAD_TRIALS)
        .usingDijkstra();
```

Instead of `.withExpansionDurationMillis(EXPANSION_JOIN_DURATION_MILLIS)`, you can use `.withExpansionDurationNanos(EXPANSION_JOIN_DURATION_NANOS)`.
Instead of `.withMasterThreadSleepDurationMillis(MASTER_THREAD_SLEEP_DURATION_MILLIS)`, you can use `.withMasterThreadSleepDurationNanos(MASTER_THREAD_SLEEP_DURATION_NANOS)`.
Instead of `.withSlaveThreadSleepDurationMillis(SLAVE_THREAD_SLEEP_DURATION_MILLIS)`, you can use `.withSlaveThreadSleepDurationNanos(SLAVE_THREAD_SLEEP_DURATION_NANOS)`.

Above, any chained method whose name starts with `with` may be ommitted, in which case the default value will be used.

## Invoking the search ##

In order to build the pathfinding invocation, there are four options available:

```java
List<DirectedGraphNode> path;

// Search in an undirected graph without progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(target)
    .withUndirectedGraphNodeExpander(expander1)
    .search();

// Search in a directed graph without progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(target)
    .withForwardNodeExpander(expander1)
    .withBackwardNodeExpander(expander2)
    .search();

// Search in an undirected graph with progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(target)
    .withUndirectedGraphNodeExpander(expander1)
    .withSharedSearchProgressListener(null)
    .withForwardSearchProgressListener(null)
    .withBackwardSearchProgressListener(null)
    .search();

// Search in a directed graph with progress listeners:
path = ThreadPoolBidirectionalBFSPathFinderSearchBuilder
    .withPathFinder(finder)
    .withSourceNode(source)
    .withTargetNode(target)
    .withForwardNodeExpander(expander1)
    .withBackwardNodeExpander(expander2)
    .withSharedSearchProgressListener(null)
    .withForwardSearchProgressListener(null)
    .withBackwardSearchProgressListener(null)
    .search();
```
