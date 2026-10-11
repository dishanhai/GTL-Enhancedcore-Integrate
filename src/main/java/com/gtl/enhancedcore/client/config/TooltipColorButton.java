package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.TooltipMarkup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A labelled color chip, also used for the sixteen palette swatches. */
final class TooltipColorButton extends AbstractButton {
    record Choice(String color, boolean rainbow) {
        static Choice inherit() { return new Choice("", false); }
        static Choice animated() { return new Choice("", true); }
        static Choice solid(int rgb) { return new Choice(TooltipMarkup.hex(rgb), false); }
        int rgb() { Integer value = TooltipMarkup.color(color); return value == null ? 0xffffff : value; }
        Component name() {
            if (rainbow) return ConfigPage.tr("palette.rainbow");
            if (color.isEmpty()) return ConfigPage.tr("palette.inherit");
            for (var preset : TooltipMarkup.palette()) if (preset.rgb() == rgb()) return ConfigPage.tr("palette." + preset.id());
            return ConfigPage.tr("palette.custom", TooltipMarkup.hex(rgb()));
        }
        String marker() {
            if (rainbow) return "[动态彩虹]";
            if (color.isEmpty()) return "[默认颜色]";
            for (var preset : TooltipMarkup.palette()) if (preset.rgb() == rgb()) return "[" + preset.tag() + "]";
            return "[" + TooltipMarkup.hex(rgb()) + "]";
        }
    }
    private Choice choice;
    private final boolean labelled;
    private final Runnable action;
    boolean selected;
    TooltipColorButton(int x, int y, int width, int height, Choice choice, boolean labelled, Runnable action) {
        super(x, y, width, height, choice.name());
        this.labelled = labelled; this.action = action; choice(choice);
    }
    void choice(Choice value) {
        choice = value; setMessage(value.name());
        setTooltip(Tooltip.create(value.name().copy().append(value.color().isEmpty() ? "" : " · " + TooltipMarkup.hex(value.rgb()))));
    }
    @Override public void onPress() { action.run(); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, selected ? 0xffffd568 : isHoveredOrFocused() ? 0xffe0e0e0 : 0xff606977);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, isHoveredOrFocused() ? 0xff3d4859 : 0xff242b36);
        if (labelled) {
            chip(g, x + 4, y + 5, 10, height - 10, choice);
            var font = Minecraft.getInstance().font;
            g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), width - 23), x + 19, y + (height - 8) / 2, 0xffffff, false);
        } else chip(g, x + 3, y + 3, width - 6, height - 6, choice);
    }
    static void chip(GuiGraphics g, int x, int y, int w, int h, Choice choice) {
        if (choice.rainbow()) {
            float phase = (System.currentTimeMillis() % 6000L) / 6000.0f;
            for (int i = 0; i < w; i++) g.fill(x + i, y, x + i + 1, y + h,
                    0xff000000 | Mth.hsvToRgb((phase + i / (float) w) % 1.0f, 0.75f, 1));
        } else if (choice.color().isEmpty()) {
            for (int i = 0; i < w; i += 2) for (int j = 0; j < h; j += 2)
                g.fill(x + i, y + j, x + Math.min(w, i + 2), y + Math.min(h, j + 2),
                        ((i / 2 + j / 2) % 2 == 0) ? 0xffb5b5b5 : 0xff555555);
        } else g.fill(x, y, x + w, y + h, 0xff000000 | choice.rgb());
    }
}
