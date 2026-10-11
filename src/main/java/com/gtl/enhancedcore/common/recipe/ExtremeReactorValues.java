package com.gtl.enhancedcore.common.recipe;


/**
 * 极限反应堆注册期与运行期共用的固定数值，统一取自 {@link ExtremeReactorFuel}。
 */
public final class ExtremeReactorValues {

    private ExtremeReactorValues() {}

    /** 发电功率（EU/t）。 */
    public static long generationEut() {
        return ExtremeReactorFuel.GENERATION_EUT;
    }

    /** 单次燃料消耗（mB）。 */
    public static long fuelAmountMb() {
        return ExtremeReactorFuel.AMOUNT_MB;
    }

    /** 单次配方时长（tick）。 */
    public static int durationTicks() {
        return ExtremeReactorFuel.DURATION_TICKS;
    }

    /** 电网结算间隔（tick）。 */
    public static int gridFlushInterval() {
        return ExtremeReactorFuel.GRID_FLUSH_INTERVAL;
    }
}
