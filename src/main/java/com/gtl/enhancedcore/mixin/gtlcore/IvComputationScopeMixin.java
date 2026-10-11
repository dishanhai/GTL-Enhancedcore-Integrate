package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvComputationContext;
import org.gtlcore.gtlcore.api.machine.computation.ComputationProgress;
import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve upstream transaction ownership while recording research in an isolated order. */
@Mixin(value = ComputationRecipes.Scope.class, remap = false)
public abstract class IvComputationScopeMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/api/machine/computation/ComputationProgress;gtlcore$completedComputation(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)J"),
            require = 1, allow = 1)
    private long enhanced$initializeOrder(ComputationProgress progress, GTRecipe recipe) {
        return IvComputationContext.completed(progress, recipe);
    }

    @Redirect(method = "commit", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/api/machine/computation/ComputationProgress;gtlcore$advanceComputation(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;J)V"),
            require = 1, allow = 1)
    private void enhanced$advanceOrder(ComputationProgress progress, GTRecipe recipe, long amount) {
        IvComputationContext.advance(progress, recipe, amount);
    }
}
