package com.gtl.enhancedcore.mixin.mc.client;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the backing {@link MultilineTextField} of a {@link MultiLineEditBox}.
 * <p>
 * The Tips line editor needs the caret/selection to insert an inline markup tag around the
 * selected text; {@code MultiLineEditBox} only publishes {@code getValue}/{@code setValue},
 * and its public {@code charTyped} path would replace the selection instead of wrapping it.
 */
@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {

    @Accessor("textField")
    MultilineTextField gtl$textField();
}
