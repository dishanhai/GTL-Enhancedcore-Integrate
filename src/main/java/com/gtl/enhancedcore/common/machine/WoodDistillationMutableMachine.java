package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/** Wood distillation uses ADD's cross-recipe scheduler with 15 base threads plus Ω. */
public final class WoodDistillationMutableMachine extends AddThreadMutableMachine {
    public static final int CROSS_RECIPE_THREADS = 15;

    public WoodDistillationMutableMachine(IMachineBlockEntity holder) {
        super(holder, "wood_distillation");
    }

    @Override
    protected int threadBaseline() {
        return CROSS_RECIPE_THREADS;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        getRecipeLogic().setUseMultipleRecipes(true);
    }
}
