package com.gtl.enhancedcore.common.performance;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
/** Checks whether the existing formed layout is complete enough for dismantling. */
public final class TerminalDismantleGuard {

    private TerminalDismantleGuard() {}

    public static boolean candidate(boolean air, boolean controller, boolean preserved) {
        return !air && !controller && !preserved;
    }

    public static int emptySpan(int coordinate, int direction) {
        return direction > 0 ? 16 - Math.floorMod(coordinate, 16) : Math.floorMod(coordinate, 16) + 1;
    }

    public static boolean canReuseFormedLayout(IMultiController controller, BlockPattern pattern,
            MultiblockState state) {
        int[] repetitions = state.getMatchContext().get("gtlcore:matched_repetitions");
        return canReuseFormedLayout(controller.isFormed(), repetitions == null ? -1 : repetitions.length,
                pattern.aisleRepetitions.length, state.getMatchContext().containsKey("gtlcore:matched_min_z"));
    }

    public static boolean canReuseFormedLayout(boolean formed, int repetitionCount, int expectedAisleCount,
            boolean hasMinimumZ) {
        return formed && repetitionCount == expectedAisleCount && hasMinimumZ;
    }

}
