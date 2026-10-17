package com.gtl.enhancedcore.common.performance;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resumable version of GTLCore fix4's partial-layout scoring order. No world access. */
public final class DismantleLayoutPlanner {
    public record Slice(int aisleIndex, int z, boolean flipped) {}
    public record Layout(int minZ, int[] repetitions, boolean flipped, long score) {
        public boolean hasMatch() { return score > 0; }
    }

    private final int[][] limits;
    private final int center;
    private final boolean allowFlip;
    private final Map<Slice, Integer> scores = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Slice, Integer> eldest) { return size() > 8192; }
    };
    private int[] repetitions;
    private boolean flipped;
    private int aisle;
    private int z;
    private int step = 1;
    private int count = -1;
    private int scoreIndex;
    private boolean counting;
    private long score;
    private Layout normal;
    private Layout result;
    private Slice needed;

    public DismantleLayoutPlanner(int[][] limits, int center, boolean allowFlip) {
        this.limits = limits;
        this.center = center;
        this.allowFlip = allowFlip;
        repetitions = new int[limits.length];
        aisle = center;
    }

    /** Advances one control step. Caller supplies missing slice scores in bounded batches. */
    public void advance() {
        needed = null;
        if (result != null) return;
        if (aisle >= limits.length || aisle < 0) {
            if (step == 1) {
                step = -1;
                aisle = center - 1;
                z = -1;
            } else {
                Layout layout = new Layout(z + 1, repetitions, flipped, score);
                if (!flipped && allowFlip) {
                    normal = layout;
                    flipped = true;
                    repetitions = new int[limits.length];
                    aisle = center;
                    step = 1;
                    z = 0;
                    score = 0;
                } else result = normal != null && normal.score >= layout.score ? normal : layout;
            }
            count = -1;
            return;
        }
        if (count < 0) {
            counting = limits[aisle][0] != limits[aisle][1];
            count = counting ? 0 : limits[aisle][0];
            scoreIndex = 0;
        }
        if (counting) {
            if (count >= limits[aisle][1]) counting = false;
            else {
                Integer current = score(aisle, z + count * step);
                if (current == null) return;
                int adjacentAisle = aisle + step;
                Integer adjacent = adjacentAisle >= 0 && adjacentAisle < limits.length
                        ? score(adjacentAisle, z + count * step) : Integer.valueOf(0);
                if (adjacent == null) return;
                if (adjacent > current || current == 0 && count >= limits[aisle][0]) counting = false;
                else {
                    count++;
                    return;
                }
            }
        }
        if (scoreIndex < count) {
            Integer sliceScore = score(aisle, z + scoreIndex * step);
            if (sliceScore == null) return;
            score += sliceScore;
            scoreIndex++;
            return;
        }
        repetitions[aisle] = count;
        z += count * step;
        aisle += step;
        count = -1;
    }

    private Integer score(int aisle, int z) {
        Slice key = new Slice(aisle, z, flipped);
        Integer value = scores.get(key);
        if (value == null) needed = key;
        return value;
    }

    public Slice needed() { return needed; }
    public Layout result() { return result; }
    public void supply(Slice slice, int value) { scores.put(slice, value); }
}
