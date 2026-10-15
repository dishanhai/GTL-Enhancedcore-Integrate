package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Closing JEI releases the active page; fullscreen borrowing does not close it. */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class LazyMultiblockGuiCloseMixin {
    // JEI .191/.200 has two exits: restoring an open GUI's parent and
    // delegating to Screen when it is already closed. Both release the page.
    @Inject(method = "m_7379_", at = @At("RETURN"), require = 2, allow = 2)
    private void enhanced$releasePreviewOnClose(CallbackInfo callback) {
        LazyMultiblockPreviewWidget.clearShared();
    }
}
