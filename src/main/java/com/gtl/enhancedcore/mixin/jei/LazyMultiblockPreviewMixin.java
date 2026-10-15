package com.gtl.enhancedcore.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;

/** Runs the precise factory/category postApply edits after GTLCore has merged. */
@Mixin(targets = {
        "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper",
        "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory"
}, remap = false, priority = 100)
public abstract class LazyMultiblockPreviewMixin {}
