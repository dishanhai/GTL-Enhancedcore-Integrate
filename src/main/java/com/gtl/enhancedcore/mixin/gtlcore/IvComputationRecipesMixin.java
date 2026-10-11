package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvComputationContext;
import org.gtlcore.gtlcore.api.machine.computation.ComputationProgress;
import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The upstream scheduler must see the current order's remaining long research work. */
@Mixin(value = ComputationRecipes.class, remap = false)
public abstract class IvComputationRecipesMixin {
    @Redirect(method = "maximum", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/api/machine/computation/ComputationProgress;gtlcore$completedComputation(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)J"),
            require = 1, allow = 1)
    private static long enhanced$orderProgress(ComputationProgress progress, GTRecipe recipe) {
        return IvComputationContext.completed(progress, recipe);
    }
}
