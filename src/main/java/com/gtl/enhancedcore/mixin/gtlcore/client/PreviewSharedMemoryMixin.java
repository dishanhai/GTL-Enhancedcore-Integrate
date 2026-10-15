package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPatternMemory;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.util.Map;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend the existing logout cleanup to objects retained by JEI's wrappers. */
@Mixin(value = PatternPreviewWidget.class, remap = false)
public abstract class PreviewSharedMemoryMixin {
    @Shadow private static TrackedDummyWorld LEVEL;
    @Shadow @Final private static Map<MultiblockMachineDefinition, ?> CACHE;

    @Inject(method = "clearSharedPreviewState", at = @At("HEAD"), require = 1, allow = 1)
    private static void enhanced$releaseRetainedPreviewData(CallbackInfo callback) {
        LazyMultiblockPreviewWidget.clearAll();
        for (Object value : CACHE.values()) {
            // MBPattern[] remains an Object[] without referencing its private type.
            if (value instanceof Object[] patterns) {
                for (Object pattern : patterns) {
                    if (pattern instanceof PreviewPatternMemory memory) memory.enhanced$releasePreviewMemory();
                }
            }
        }
        if (LEVEL != null) {
            LEVEL.clear();
            // A stale UI slot can retain an old dummy controller even after
            // its widget is detached. Clear also releases map backing arrays.
            if (LEVEL.renderedBlocks instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap<?, ?> map) map.trim();
            if (LEVEL.renderedBlocks instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<?, ?> map) map.trim();
            if (LEVEL.blockEntities instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap<?, ?> map) map.trim();
            if (LEVEL.blockEntities instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<?, ?> map) map.trim();
        }
    }
}
