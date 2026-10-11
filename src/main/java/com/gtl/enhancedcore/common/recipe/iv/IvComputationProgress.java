package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;

/** Exact research work belongs to one committed order, even when orders share a RecipeLogic. */
public final class IvComputationProgress {
    private long completed;

    public long completed() { return completed; }

    public long completed(long total) {
        if (total <= 0 || completed > total) throw new IllegalArgumentException("Invalid order computation progress");
        return completed;
    }

    public void advance(long total, long amount) {
        long previous = completed(total);
        if (amount < 0 || amount > total - previous)
            throw new IllegalArgumentException("Order computation exceeds remaining research");
        completed += amount;
    }

    public int projected(long total, int duration) {
        completed(total);
        if (duration < 1) throw new IllegalArgumentException("Invalid research display duration");
        if (completed == total) return duration;
        return Math.min(duration - 1, BigInteger.valueOf(completed).multiply(BigInteger.valueOf(duration))
                .divide(BigInteger.valueOf(total)).intValueExact());
    }

    public void restore(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative saved order computation");
        completed = amount;
    }

    public void reset() { completed = 0; }
}
