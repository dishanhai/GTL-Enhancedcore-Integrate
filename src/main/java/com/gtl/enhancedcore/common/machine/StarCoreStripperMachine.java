package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine;

/** UHV fragment collector using ADD cross-recipe batching and the upstream collection rates. */
public final class StarCoreStripperMachine extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine {
    /** 跨配方线程。 */
    public static final int CROSS_RECIPE_THREADS = 128;
    /** 能耗倍率。 */
    public static final double ENERGY_MULTIPLIER = 256.0;
    /** 时长倍率。 */
    public static final double DURATION_MULTIPLIER = 0.25;

    public StarCoreStripperMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override public RecipeLogic createRecipeLogic(Object... args) {
        var logic = new FixedThreadRecipeLogic(this);
        logic.setReduction(ENERGY_MULTIPLIER,
                DURATION_MULTIPLIER);
        return logic;
    }

    private static final class FixedThreadRecipeLogic extends GTLAddMultipleRecipesLogic {
        private FixedThreadRecipeLogic(GTLAddWorkableElectricParallelHatchMultipleRecipesMachine machine) {
            super(machine);
        }

        @Override public int getMultipleThreads() {
            return CROSS_RECIPE_THREADS;
        }
    }
}
