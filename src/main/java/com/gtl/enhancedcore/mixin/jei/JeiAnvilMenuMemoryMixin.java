package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.memory.ClientWorldCacheCleanup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI 15.49.0.191/.200 caches a menu containing the first real client player. */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilHelper", remap = false)
public abstract class JeiAnvilMenuMemoryMixin {
    @Shadow private static AnvilMenu ANVIL_MENU;

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void enhancedcore$registerWorldCleanup(CallbackInfo ci) {
        ClientWorldCacheCleanup.register(departed -> {
            AnvilMenu menu = ANVIL_MENU;
            if (menu == null) return;
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory inventory && inventory.player.level() == departed) {
                    ANVIL_MENU = null;
                    return;
                }
            }
        });
    }
}
