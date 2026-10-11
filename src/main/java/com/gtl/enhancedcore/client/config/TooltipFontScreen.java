package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.TooltipMarkup;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Named built-in font choices; a resource ID is needed only for a custom resource-pack font. */
final class TooltipFontScreen extends ConfigPage {
    private final Consumer<String> accept;
    private final boolean localScope;
    private final Map<String, Button> presets = new LinkedHashMap<>();
    private String fontId;
    private EditBox fontBox;
    TooltipFontScreen(Screen parent, String current, boolean localScope, Consumer<String> accept) {
        super(parent, "font_picker"); fontId = current; this.localScope = localScope; this.accept = accept;
    }
    static Component name(String id) {
        return switch (id) {
            case "" -> tr("font_choice.inherit"); case "minecraft:default" -> tr("font_choice.default");
            case "minecraft:uniform" -> tr("font_choice.uniform"); default -> tr("font_choice.custom", id);
        };
    }
    @Override protected void init() {
        super.init(); presets.clear();
        String[] ids = {"", "minecraft:default", "minecraft:uniform"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            var preset = button(name(id), left, 34 + i * 24, bodyWidth, () -> {
                fontId = id; fontBox.setValue(id); clearStatus(); highlight();
            });
            presets.put(id, preset);
        }
        // Keep a long pre-existing resource ID visible; refuse it in local markup instead of truncating it.
        int limit = localScope ? TooltipMarkup.INLINE_FONT_LIMIT : 256;
        fontBox = field("font", fontId, left, 124, bodyWidth, Math.max(limit, fontId.length()));
        fontBox.setHint(tr("font_choice.custom_hint"));
        fontBox.setResponder(value -> { fontId = value; clearStatus(); highlight(); });
        fontBox.setTooltip(Tooltip.create(tr("font")));
        int half = (bodyWidth - 6) / 2;
        button("apply", left, height - 27, half, () -> {
            String id = fontId.trim();
            if (!id.isEmpty() && !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { notice(tr("font_invalid")); return; }
            if (localScope && id.length() > TooltipMarkup.INLINE_FONT_LIMIT) { notice(tr("font_mark_limit", TooltipMarkup.INLINE_FONT_LIMIT)); return; }
            accept.accept(id); onClose();
        });
        button("cancel", left + half + 6, height - 27, half, this::onClose);
        highlight();
    }
    private void highlight() { presets.forEach((id, button) -> button.setMessage(name(id).copy().append(id.equals(fontId) ? " ✓" : ""))); }
    @Override public void tick() { fontBox.tick(); }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, clipped(tr("font_choice.custom_label"), bodyWidth), left, 112, 0xaaaaaa);
        var id = ResourceLocation.tryParse(fontId.trim().isEmpty() ? "minecraft:default" : fontId.trim());
        if (id != null) g.drawString(font, clipped(tr("font_sample").copy().withStyle(style -> style.withFont(id)), bodyWidth), left, 156, 0xffffff);
    }
}
