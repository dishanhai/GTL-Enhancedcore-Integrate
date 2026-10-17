package com.gtl.enhancedcore.common.util;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.locks.Lock;

/** Separate visual-state traversal from the mutable structure match context. */
public final class ActiveBlockSnapshot {
    private ActiveBlockSnapshot() {}

    public static LongIterator iterator(LongSet positions, Lock patternLock) {
        long[] snapshot;
        patternLock.lock();
        try {
            snapshot = positions.toLongArray();
        } finally {
            patternLock.unlock();
        }
        // World updates can reenter structure checks. Never hold the lock across those callbacks.
        return LongIterators.wrap(snapshot);
    }
}
