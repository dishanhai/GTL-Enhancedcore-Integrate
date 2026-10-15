package com.gtl.enhancedcore.mixin.ldlib.client;

import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact LDLib 1.0.33.b callback access used only for owned preview delegates. */
@Mixin(value = ModularUI.class, remap = false)
public interface PreviewCloseListenersAccessor extends LazyMultiblockPreviewWidget.CloseListenerAccess {
    @Override
    @Accessor("uiCloseCallback")
    List<Runnable> enhanced$previewCloseListeners();
}
