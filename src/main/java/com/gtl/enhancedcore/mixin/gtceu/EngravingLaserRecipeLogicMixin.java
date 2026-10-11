package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.ComputationWaivers;
import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A free research recipe must use ordinary tick progress instead of waiting for paid CWU. */
@Mixin(value = RecipeLogic.class, remap = false)
public abstract class EngravingLaserRecipeLogicMixin {
    @Shadow @Final protected IRecipeLogicMachine machine;

    @ModifyVariable(method = "setupRecipe", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, allow = 1)
    private GTRecipe enhanced$freeComputationRecipe(GTRecipe recipe) {
        if (!(machine instanceof WorkableElectricMultiblockMachine controller)
                || !ComputationWaivers.engravingLaserPlant(controller.getDefinition().getId().toString(), controller.isFormed()))
            return recipe;
        if (!recipe.inputs.containsKey(CWURecipeCapability.CAP) && !recipe.tickInputs.containsKey(CWURecipeCapability.CAP)
                && !recipe.data.getBoolean("duration_is_total_cwu")) return recipe;
        var free = recipe.copy();
        free.inputs.remove(CWURecipeCapability.CAP);
        free.tickInputs.remove(CWURecipeCapability.CAP);
        free.data = free.data.copy();
        free.data.putBoolean("duration_is_total_cwu", false);
        free.data.remove(ComputationAmounts.TOTAL_CWU);
        return free;
    }
}
