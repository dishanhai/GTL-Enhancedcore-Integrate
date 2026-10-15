package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.memory.ClientWorldCacheCleanup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEI's fake grindstone also stores a real player's inventory, like its fake anvil. */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneHelper", remap = false)
public abstract class JeiGrindstoneMenuMemoryMixin {
    @Shadow private static GrindstoneMenu GRINDSTONE_MENU;
    @Unique private static boolean enhancedcore$cleanupRegistered;

    // This target has no <clinit>; register on its first normal lookup without loading JEI eagerly.
    @Inject(method = "getFakeGrindstoneMenu", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private static void enhancedcore$registerWorldCleanup(CallbackInfoReturnable<GrindstoneMenu> cir) {
        if (enhancedcore$cleanupRegistered) return;
        enhancedcore$cleanupRegistered = true;
        ClientWorldCacheCleanup.register(departed -> {
            GrindstoneMenu menu = GRINDSTONE_MENU;
            if (menu == null) return;
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory inventory && inventory.player.level() == departed) {
                    GRINDSTONE_MENU = null;
                    return;
                }
            }
        });
    }
}
