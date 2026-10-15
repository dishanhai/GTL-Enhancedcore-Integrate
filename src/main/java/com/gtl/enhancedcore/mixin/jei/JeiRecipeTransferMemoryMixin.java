package com.gtl.enhancedcore.mixin.jei;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI only uses this negative cache for contains/add; a menu must not keep its player/world alive. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeTransferManager", remap = false)
public abstract class JeiRecipeTransferMemoryMixin {
    @Shadow @Final @Mutable private Set<AbstractContainerMenu> unsupportedContainers;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void enhancedcore$weakUnsupportedContainers(CallbackInfo ci) {
        unsupportedContainers = Collections.newSetFromMap(new WeakHashMap<>());
    }
}
