package com.gtl.enhancedcore.common.machine.hatch;


/** Configured physical budget; GTLCore owns recipe aggregation and research progress. */
public final class CreativeComputationPolicy {

    /** 每 tick 提供的最大 CWU。 */
    public static final int MAX_CWU_PER_TICK = 2_100_000_000;

    private CreativeComputationPolicy() {}

    /** 固定每 tick 最大 CWU。 */
    public static int maxCwuPerTick() {
        return MAX_CWU_PER_TICK;
    }
}
