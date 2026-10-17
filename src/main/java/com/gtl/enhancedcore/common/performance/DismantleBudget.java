package com.gtl.enhancedcore.common.performance;

import java.util.function.LongSupplier;

/** One shared server-tick budget, including layout scoring and all players' removals. */
public final class DismantleBudget {
    private final int scanLimit;
    private final int removalLimit;
    private final LongSupplier clock;
    private final long deadline;
    private int scans;
    private int removals;

    public DismantleBudget(int scanLimit, int removalLimit, long nanos, LongSupplier clock) {
        this.scanLimit = scanLimit;
        this.removalLimit = removalLimit;
        this.clock = clock;
        deadline = clock.getAsLong() + nanos;
    }

    public boolean available() {
        return scans < scanLimit && removals < removalLimit && clock.getAsLong() < deadline;
    }

    public boolean scan() {
        if (!available()) return false;
        scans++;
        return true;
    }

    public void removed() { removals++; }
}
