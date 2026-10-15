package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/** Digestion and dissolution share the existing isolated-order scheduler and parallel-hatch budget. */
public final class HydrothermalLeachingComplexMachine extends TieredParallelMachine {
    public static final int CROSS_RECIPE_THREADS = 128;

    public HydrothermalLeachingComplexMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public int getThreadsForTier() {
        return CROSS_RECIPE_THREADS;
    }

    @Override
    public boolean ivMaintenancePenalty() {
        return false;
    }
}
