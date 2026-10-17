package com.gtl.enhancedcore.common.performance;

import java.util.List;

/** Claims each committed drop once. An uncertain third-party transfer is never replayed. */
public final class DismantleDropLedger<T> {
    private final List<T> drops;
    private int next;

    public DismantleDropLedger(List<T> drops) { this.drops = drops; }
    public boolean hasNext() { return next < drops.size(); }
    public T claim() { return drops.get(next++); }

}
