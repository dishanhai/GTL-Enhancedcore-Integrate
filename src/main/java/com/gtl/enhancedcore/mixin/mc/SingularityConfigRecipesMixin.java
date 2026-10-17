package com.gtl.enhancedcore.mixin.mc;

import com.google.gson.JsonElement;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Rebuild only our compressor recipes on every normal data reload, then let GT rebuild its lookup. */
@Mixin(RecipeManager.class)
public abstract class SingularityConfigRecipesMixin {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private Map<ResourceLocation, JsonElement> enhanced$reloadCompressor(Map<ResourceLocation, JsonElement> recipes) {
        return InfinitySingularityRecipeLoader.reloadRecipes(recipes);
    }
}
