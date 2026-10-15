package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.memory.JeiRecipeTransferButtonMemory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A JEI layout survives world changes; its transfer button must not retain the old menu. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButton", remap = false)
public abstract class JeiRecipeTransferButtonMemoryMixin implements JeiRecipeTransferButtonMemory.Access {
    @Shadow private AbstractContainerMenu parentContainer;

    @Inject(method = "<init>(Lmezz/jei/api/gui/drawable/IDrawable;"
            + "Lmezz/jei/api/gui/IRecipeLayoutDrawable;Ljava/lang/Runnable;)V",
            at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void enhancedcore$registerButton(CallbackInfo ci) {
        JeiRecipeTransferButtonMemory.register(this);
    }

    @Override
    public void enhancedcore$releaseDepartedMenu(Level departed) {
        if (parentContainer != null && enhancedcore$menuBelongsTo(parentContainer, departed)) parentContainer = null;
    }

    @Override
    public boolean enhancedcore$references(Level level) {
        return parentContainer != null && enhancedcore$menuBelongsTo(parentContainer, level);
    }

    @Unique
    private static boolean enhancedcore$menuBelongsTo(AbstractContainerMenu menu, Level level) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory && inventory.player.level() == level) return true;
        }
        return false;
    }
}
