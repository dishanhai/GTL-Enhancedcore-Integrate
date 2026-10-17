package com.gtl.enhancedcore.client.plotinfo;

import com.gtl.enhancedcore.client.plot.PlotPreviewRenderer;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The running world's frozen recipe, received from the server rather than client configuration. */
public final class PlotWorldInfoScreen extends Screen {
    private final PlotSettings settings;
    private final PlotPreviewRenderer.Cached singlePreview = new PlotPreviewRenderer.Cached();
    private final PlotPreviewRenderer.Cached fourPreview = new PlotPreviewRenderer.Cached();
    private int left, top, bodyWidth, bodyHeight;

    private PlotWorldInfoScreen(PlotSettings settings) {
        super(tr("title"));
        this.settings = settings;
    }

    public static void open(PlotSettings settings) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) minecraft.setScreen(new PlotWorldInfoScreen(settings));
    }

    private static Component tr(String suffix, Object... values) {
        return Component.translatable("screen.gtl_enhancedcore.plot_info." + suffix, values);
    }

    @Override protected void init() {
        bodyWidth = Math.min(740, width - 24);
        bodyHeight = Math.min(430, height - 24);
        left = (width - bodyWidth) / 2; top = (height - bodyHeight) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 65, top + bodyHeight - 30, 130, 20).build());
        if (settings == null) return;
        // Compact fields use tooltips so long names remain inspectable on small GUI scales.
        int fieldWidth = (bodyWidth - 36) / 2;
        field(tr("ground", blockName(settings.groundBlock())), left + 12, top + 82, fieldWidth);
        field(tr("road", blockName(settings.roadBlock())), left + bodyWidth / 2 + 6, top + 82, fieldWidth);
        field(tr("border", blockName(settings.borderBlock())), left + 12, top + 106, fieldWidth);
        field(tr("marker", blockName(settings.markerBlock())), left + bodyWidth / 2 + 6, top + 106, fieldWidth);
    }

    private void field(Component label, int x, int y, int fieldWidth) {
        addRenderableWidget(Button.builder(label, button -> {})
                .bounds(x, y, fieldWidth, 20).tooltip(Tooltip.create(label)).build());
    }

    private static Component blockName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return Component.literal(id);
        return BuiltInRegistries.BLOCK.get(key).getName();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(left, top, left + bodyWidth, top + bodyHeight, 0xF0141C28);
        g.fill(left, top, left + bodyWidth, top + 3, 0xFF54DBD0);
        g.drawCenteredString(font, title, width / 2, top + 13, 0xEAF7F5);
        if (settings == null) {
            g.drawCenteredString(font, tr("unavailable"), width / 2, top + 55, 0xFFD592);
            g.drawWordWrap(font, tr("unavailable_hint"), left + 16, top + 80, bodyWidth - 32, 0xB6C5D5);
        } else {
            g.drawCenteredString(font, tr("size", settings.plotChunks(), settings.plotChunks(),
                    settings.plotBlocks(), settings.plotBlocks()), width / 2, top + 33, 0xA8EDE5);
            g.drawCenteredString(font, tr("layers", settings.bedrockY(), settings.bedrockY() + 1,
                    settings.surfaceY()), width / 2, top + 49, 0xC0CEDD);
            g.drawCenteredString(font, tr("road_style", styleName(), settings.roadWidth(),
                    settings.effectiveMarkerSize()), width / 2, top + 65, 0xC0CEDD);
            int size = Math.min((bodyWidth - 60) / 2, Math.max(24, bodyHeight - 200));
            int gap = Math.min(32, (bodyWidth - 2 * size) / 3);
            int first = width / 2 - gap / 2 - size, second = width / 2 + gap / 2;
            int py = top + 151;
            g.drawCenteredString(font, tr("single"), first + size / 2, py - 15, 0xEAF7F5);
            g.drawCenteredString(font, tr("four"), second + size / 2, py - 15, 0xEAF7F5);
            singlePreview.draw(g, settings, first, py, size, 1, true);
            fourPreview.draw(g, settings, second, py, size, 2, true);
            g.drawCenteredString(font, tr("locked"), width / 2, top + bodyHeight - 45, 0xD0BA8C);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private Component styleName() {
        return Component.translatable("screen.gtl_enhancedcore.plot_info.style." +
                settings.roadStyle().name().toLowerCase(Locale.ROOT));
    }

    @Override public void removed() { singlePreview.close(); fourPreview.close(); }

    @Override public boolean isPauseScreen() { return false; }
}
