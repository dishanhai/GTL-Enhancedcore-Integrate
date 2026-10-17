package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.machine.hatch.AdaptiveMaintenanceHatchMachine;
import org.gtlcore.gtlcore.common.recipe.condition.GravityCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the adaptive hatch satisfy either of GTLCore's discrete gravity requirements. */
@Mixin(value = GravityCondition.class, remap = false)
public abstract class AdaptiveMaintenanceGravityConditionMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true, remap = false)
    private void enhanced$satisfyAnyGravity(GTRecipe recipe, RecipeLogic logic, CallbackInfoReturnable<Boolean> cir) {
        if (logic.getMachine() instanceof MultiblockControllerMachine controller
                && controller.self().getParts().stream().anyMatch(AdaptiveMaintenanceHatchMachine.class::isInstance)) {
            cir.setReturnValue(true);
        }
    }
}
