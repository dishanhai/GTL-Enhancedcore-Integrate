package com.gtl.enhancedcore.client.config;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Small yes/no page used before an action that would discard authored tooltip lines.
 * Kept local so the config screens do not depend on a loader-specific confirmation dialog.
 */
final class ConfirmScreen extends ConfigPage {
    private final Component message;
    private final Consumer<Boolean> answer;

    ConfirmScreen(Consumer<Boolean> answer, Component title, Component message) {
        super(null, "confirm");
        this.answer = answer;
        this.message = message;
    }

    @Override protected void init() {
        super.init();
        int half = (bodyWidth - 6) / 2;
        button("confirm", left, height - 51, half, () -> answer.accept(true));
        button("cancel", left + half + 6, height - 51, half, () -> answer.accept(false));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xffffff);
        int y = height / 2 - 16;
        for (var line : font.split(message, bodyWidth)) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xffcc66);
            y += 12;
        }
        super.render(graphics, mouseX, mouseY, partial);
    }

    @Override public void onClose() { answer.accept(false); }
}
