package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.client.config.TooltipColorButton.Choice;
import com.gtl.enhancedcore.common.config.TooltipColorInput;
import com.gtl.enhancedcore.common.config.TooltipMarkup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Presets + saturation/value square + hue strip + optional HEX/RGB entry, like FTB's picker. */
final class TooltipColorScreen extends Screen {
    private final Screen parent;
    private final Consumer<Choice> accept;
    private final List<TooltipColorButton> swatches = new ArrayList<>();
    private final String[] channels = new String[3];
    private final EditBox[] channelBoxes = new EditBox[3];
    private Choice choice;
    private TooltipColorButton inheritButton, rainbowButton;
    private EditBox hexBox;
    private String hex;
    private Component error = Component.empty();
    private float hue, saturation, value;
    private boolean syncing;
    private int left, bodyWidth, pickerY, pickerHeight, fieldsX, specialY, sampleY;

    TooltipColorScreen(Screen parent, Choice current, Consumer<Choice> accept) {
        super(ConfigPage.tr("color_picker")); this.parent = parent; this.accept = accept;
        choice = current; readRgb(current.rgb());
        hex = TooltipMarkup.hex(current.rgb());
        for (int i = 0; i < 3; i++) channels[i] = Integer.toString((current.rgb() >> (16 - i * 8)) & 255);
    }
    @Override protected void init() {
        bodyWidth = Math.min(420, width - 24); left = (width - bodyWidth) / 2;
        swatches.clear();
        int gap = 4, cell = (bodyWidth - 7 * gap) / 8;
        var presets = TooltipMarkup.palette();
        for (int i = 0; i < presets.size(); i++) {
            var preset = presets.get(i);
            var swatch = addRenderableWidget(new TooltipColorButton(left + (i % 8) * (cell + gap), 34 + (i / 8) * 22,
                    cell, 18, Choice.solid(preset.rgb()), false, () -> selectRgb(preset.rgb(), null, true)));
            swatches.add(swatch);
        }
        pickerY = 88; specialY = height - 61;
        pickerHeight = Math.max(64, Math.min(128, specialY - pickerY - 19));
        int fieldWidth = 98, hueWidth = 12, squareWidth = bodyWidth - fieldWidth - hueWidth - 22;
        addRenderableWidget(new Picker(left, pickerY, squareWidth, pickerHeight, false));
        addRenderableWidget(new Picker(left + squareWidth + 6, pickerY, hueWidth, pickerHeight, true));
        fieldsX = left + bodyWidth - fieldWidth;
        hexBox = input(fieldsX + 23, pickerY, fieldWidth - 23, hex, 7);
        hexBox.setHint(Component.literal("#RRGGBB"));
        hexBox.setTooltip(Tooltip.create(ConfigPage.tr("palette.hex_help")));
        hexBox.setResponder(raw -> {
            if (syncing) return;
            hex = raw;
            var parsed = TooltipColorInput.hex(raw);
            if (parsed.isEmpty()) { error = ConfigPage.tr("palette.invalid_hex"); return; }
            selectRgb(parsed.getAsInt(), hexBox, true);
        });
        String[] labels = {"red_channel", "green_channel", "blue_channel"};
        for (int i = 0; i < 3; i++) {
            final int channel = i;
            channelBoxes[i] = input(fieldsX + 23, pickerY + 18 * (i + 1), fieldWidth - 23, channels[i], 3);
            channelBoxes[i].setTooltip(Tooltip.create(ConfigPage.tr("palette.channel_help", ConfigPage.tr("palette." + labels[i]))));
            channelBoxes[i].setResponder(raw -> {
                if (syncing) return;
                channels[channel] = raw;
                var parsed = TooltipColorInput.rgb(channels);
                if (parsed.isEmpty()) { error = ConfigPage.tr("palette.invalid_rgb"); return; }
                selectRgb(parsed.getAsInt(), channelBoxes[channel], true);
            });
        }
        sampleY = Math.max(pickerY + pickerHeight, pickerY + 70) + 5;
        int half = (bodyWidth - 6) / 2;
        inheritButton = addRenderableWidget(new TooltipColorButton(left, specialY, half, 20, Choice.inherit(), true, () -> selectSpecial(Choice.inherit())));
        inheritButton.setTooltip(Tooltip.create(ConfigPage.tr("palette.inherit_help")));
        rainbowButton = addRenderableWidget(new TooltipColorButton(left + half + 6, specialY, half, 20, Choice.animated(), true,
                () -> selectSpecial(Choice.animated())));
        Button confirm = Button.builder(ConfigPage.tr("apply"), b -> {
            if (!error.getString().isEmpty()) return;
            accept.accept(choice); onClose();
        }).bounds(left, height - 27, half, 20).build();
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(ConfigPage.tr("cancel"), b -> onClose()).bounds(left + half + 6, height - 27, half, 20).build());
        highlight();
    }
    private EditBox input(int x, int y, int width, String text, int limit) {
        var box = addRenderableWidget(new EditBox(font, x, y, width, 16, Component.empty()));
        box.setMaxLength(limit); box.setValue(text); return box;
    }
    private void readRgb(int rgb) {
        float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        if (hsv[1] > 0) hue = hsv[0]; saturation = hsv[1]; value = hsv[2];
    }
    private void selectRgb(int rgb, EditBox source, boolean updateHsv) {
        choice = Choice.solid(rgb); if (updateHsv) readRgb(rgb);
        syncing = true;
        try {
            if (source != hexBox) { hex = TooltipMarkup.hex(rgb); if (hexBox != null) hexBox.setValue(hex); }
            for (int i = 0; i < 3; i++) {
                if (source != channelBoxes[i]) {
                    channels[i] = Integer.toString((rgb >> (16 - i * 8)) & 255);
                    if (channelBoxes[i] != null) channelBoxes[i].setValue(channels[i]);
                }
            }
        } finally { syncing = false; }
        error = Component.empty(); highlight();
    }
    private void selectSpecial(Choice selected) {
        // Resolve incomplete numeric input locally; nothing reaches the parent before Apply.
        selectRgb(choice.rgb(), null, true); choice = selected; highlight();
    }
    private void highlight() {
        for (int i = 0; i < swatches.size(); i++) swatches.get(i).selected = !choice.rainbow()
                && !choice.color().isEmpty() && choice.rgb() == TooltipMarkup.palette().get(i).rgb();
        if (inheritButton != null) inheritButton.selected = !choice.rainbow() && choice.color().isEmpty();
        if (rainbowButton != null) rainbowButton.selected = choice.rainbow();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void tick() { hexBox.tick(); for (EditBox box : channelBoxes) box.tick(); }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g);
        g.fill(left - 6, 27, left + bodyWidth + 6, specialY + 23, 0xe619222e);
        g.drawCenteredString(font, title, width / 2, 10, 0xffffff);
        g.drawString(font, ConfigPage.tr("palette.custom_label"), left, 76, 0xaaaaaa, false);
        g.drawString(font, Component.literal("#"), fieldsX + 3, pickerY + 4, 0xdddddd, false);
        String[] labels = {"red_channel", "green_channel", "blue_channel"};
        int[] colors = {0xff7777, 0x77ff77, 0x7799ff};
        for (int i = 0; i < 3; i++) g.drawString(font, ConfigPage.tr("palette." + labels[i]), fieldsX + 3, pickerY + 22 + 18 * i, colors[i], false);
        g.fill(left, sampleY - 1, left + 18, sampleY + 11, 0xffb5becb);
        TooltipColorButton.chip(g, left + 1, sampleY, 16, 10, choice);
        g.drawString(font, font.plainSubstrByWidth(ConfigPage.tr("palette.current", choice.name()).getString(), bodyWidth - 25), left + 25, sampleY + 1, 0xffffff, false);
        super.render(g, x, y, partial);
        if (!error.getString().isEmpty()) g.drawString(font, font.plainSubstrByWidth(error.getString(), bodyWidth), left, height - 39, 0xff7777, false);
    }
    private final class Picker extends AbstractWidget {
        private final boolean hueStrip;
        Picker(int x, int y, int width, int height, boolean hueStrip) {
            super(x, y, width, height, ConfigPage.tr(hueStrip ? "palette.hue" : "palette.saturation_value"));
            this.hueStrip = hueStrip;
            setTooltip(Tooltip.create(ConfigPage.tr("palette.drag_help")));
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int x = getX(), y = getY();
            g.fill(x - 1, y - 1, x + width + 1, y + height + 1, isFocused() ? 0xffffffff : 0xff687589);
            if (hueStrip) {
                for (int i = 0; i < height; i++) g.fill(x, y + i, x + width, y + i + 1,
                        0xff000000 | Mth.hsvToRgb((i / (float) (height - 1)) % 1, 1, 1));
                int cy = y + Math.round(hue * (height - 1));
                g.fill(x - 2, cy - 1, x + width + 2, cy + 2, 0xffffffff);
                g.fill(x, cy, x + width, cy + 1, 0xff000000);
            } else {
                for (int i = 0; i < width; i++) g.fillGradient(x + i, y, x + i + 1, y + height,
                        0xff000000 | Mth.hsvToRgb(hue, i / (float) (width - 1), 1), 0xff000000);
                int cx = x + Math.round(saturation * (width - 1)), cy = y + Math.round((1 - value) * (height - 1));
                g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0xff000000);
                g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xff000000);
                g.fill(cx - 3, cy, cx + 4, cy + 1, 0xffffffff);
                g.fill(cx, cy - 3, cx + 1, cy + 4, 0xffffffff);
            }
        }
        private void point(double x, double y) {
            if (hueStrip) hue = Mth.clamp((float) ((y - getY()) / (height - 1)), 0, 0.99999f);
            else {
                saturation = Mth.clamp((float) ((x - getX()) / (width - 1)), 0, 1);
                value = 1 - Mth.clamp((float) ((y - getY()) / (height - 1)), 0, 1);
            }
            selectRgb(Mth.hsvToRgb(hue, saturation, value), null, false);
        }
        @Override public void onClick(double x, double y) { point(x, y); }
        @Override protected void onDrag(double x, double y, double dx, double dy) { point(x, y); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT && key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
            float delta = (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP) ? -0.01f : 0.01f;
            if (hueStrip) hue = Mth.clamp(hue + delta, 0, 0.99999f);
            else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) saturation = Mth.clamp(saturation + delta, 0, 1);
            else value = Mth.clamp(value - delta, 0, 1);
            selectRgb(Mth.hsvToRgb(hue, saturation, value), null, false); return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, ConfigPage.tr("palette.drag_help"));
        }
    }
}
