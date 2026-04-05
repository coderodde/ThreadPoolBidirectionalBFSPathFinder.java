package io.github.coderodde.graph.pathfinding.delayed.impl;

import java.util.concurrent.Semaphore;
import java.util.ArrayDeque;

/**
 * A helper class for implementing {@link ThreadPoolBidirectionalBFSPathFinder}.
 */
final class SynchronizedDeque<N> extends ArrayDeque<N> {

    private final Semaphore mutex = new Semaphore(1, true);
    
    @Override
    public boolean isEmpty() {
        mutex.acquireUninterruptibly();
        boolean empty = super.isEmpty();
        mutex.release();
        return empty;
    }
    
    @Override
    public void addLast(N e) {
        mutex.acquireUninterruptibly();
        super.addLast(e);
        mutex.release();
    }
    
    @Override
    public N peekFirst() {
        mutex.acquireUninterruptibly();
        N res = super.peekFirst();
        mutex.release();
        return res;
    }
    
    @Override
    public N removeFirst() {
        mutex.acquireUninterruptibly();
        N res = super.removeFirst();
        mutex.release();
        return res;
    }
}
