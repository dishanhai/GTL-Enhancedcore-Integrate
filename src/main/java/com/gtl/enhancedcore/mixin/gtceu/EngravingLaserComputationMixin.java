package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtl.enhancedcore.common.recipe.ComputationWaivers;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.Map;

/** Filter the consumer's CWU before GTLCore's aggregate match/commit wrappers run. */
@Mixin(value = GTRecipe.class, remap = false)
public abstract class EngravingLaserComputationMixin {

    @ModifyArg(method = "matchRecipe(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe$ActionResult;",
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;matchRecipeContents(Lcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Ljava/util/Map;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe$ActionResult;"),
            index = 2, require = 2, allow = 2)
    private Map<RecipeCapability<?>, List<Content>> enhanced$freeComputationMatch(
            IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents, boolean isTick) {
        return enhanced$inputs(io, holder, contents);
    }

    @ModifyArg(method = {"handleRecipeIO", "handleTickRecipeIO"},
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;handleRecipe(Lcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;ZLjava/util/Map;Ljava/util/Map;)Z"),
            index = 3, require = 2, allow = 2)
    private Map<RecipeCapability<?>, List<Content>> enhanced$freeComputationCommit(
            IO io, IRecipeCapabilityHolder holder, boolean isTick, Map<RecipeCapability<?>, List<Content>> contents,
            Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        return enhanced$inputs(io, holder, contents);
    }

    @Unique
    private static Map<RecipeCapability<?>, List<Content>> enhanced$inputs(
            IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents) {
        if (io == IO.IN && holder instanceof WorkableElectricMultiblockMachine machine
                && ComputationWaivers.engravingLaserPlant(machine.getDefinition().getId().toString(), machine.isFormed())) {
            return ComputationWaivers.withoutInput(contents, CWURecipeCapability.CAP);
        }
        return contents;
    }
}
