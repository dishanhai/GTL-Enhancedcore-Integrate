package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registered personal worlds retain overworld recipe conditions despite their unique dimension keys. */
@Mixin(value = DimensionCondition.class, remap = false)
public abstract class PersonalPlotDimensionConditionMixin {
    @Shadow private ResourceLocation dimension;

    @Inject(method = "test", at = @At("RETURN"), cancellable = true, remap = false, require = 1, allow = 1)
    private void enhanced$personalOverworld(GTRecipe recipe, RecipeLogic logic, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !Level.OVERWORLD.location().equals(dimension)) return;
        Level level = logic.getMachine().self().getLevel();
        if (level != null && BuiltinDimensionTypes.OVERWORLD.equals(level.dimensionTypeId())
                && PersonalPlotWorlds.isRegisteredPersonalLevel(level)) {
            cir.setReturnValue(true);
        }
    }
}
