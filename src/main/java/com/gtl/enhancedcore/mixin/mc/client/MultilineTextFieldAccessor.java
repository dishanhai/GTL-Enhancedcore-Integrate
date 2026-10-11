package com.gtl.enhancedcore.mixin.mc.client;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The actual selection anchor, including backwards and repeated-word selections. */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("selectCursor")
    int gtl$selectionAnchor();
}
