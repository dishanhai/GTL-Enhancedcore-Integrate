package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gtl.enhancedcore.client.config.PreviewLayerConfig;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps GTLCore's repeated-click layer selector from advancing when locally disabled. */
@Mixin(value = MultiblockInWorldPreviewRenderer.class, remap = false)
abstract class MultiblockPreviewLayerConfigMixin {
    @Shadow private static BlockPos LAST_POS;
    @Shadow private static int LAST_LAYER;

    @Inject(method = "showPreview", at = @At("HEAD"), remap = false)
    private static void gtl_enhancedcore$disableLayerCycling(BlockPos pos,
            com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller,
            int duration, CallbackInfo ci) {
        if (!PreviewLayerConfig.enabled()) {
            LAST_POS = null;
            LAST_LAYER = -1;
        }
    }
}
