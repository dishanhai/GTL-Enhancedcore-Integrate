package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.TooltipOverrides;
import com.gtl.enhancedcore.common.config.TooltipOverrides.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

final class ItemTipScreen extends ConfigPage {
    private final String id;
    private final ItemStack stack;
    private Mode mode;
    private final List<Line> lines = new ArrayList<>();
    private int page;
    private Button preview;
    ItemTipScreen(Screen parent, String id) {
        super(parent, "edit_item"); this.id = id;
        stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
        setEntry(ClientTooltipConfig.entry(id));
    }
    private void setEntry(Entry entry) { mode = entry.mode(); lines.clear(); lines.addAll(entry.lines()); }
    private Entry draft() { return new Entry(mode, lines); }
    @Override protected void init() {
        super.init(); int half = (bodyWidth - 6) / 2;
        button("mode." + mode.name().toLowerCase(Locale.ROOT), left, 45, half, () -> {
            var next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            // Leaving "replace" drops every authored line from the rendered tip; ask first.
            if (mode == Mode.REPLACE && next == Mode.DEFAULT && !lines.isEmpty()) {
                minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) { mode = Mode.DEFAULT; lines.clear(); }
                    minecraft.setScreen(this);
                }, tr("mode_reset_title"), tr("mode_reset_warning")));
                return;
            }
            mode = next; rebuildWidgets();
        });
        preview = button("preview", left + half + 6, 45, half, () -> {});
        int third = (bodyWidth - 12) / 3;
        button("add_line", left, 69, third, () -> {
            if (lines.size() >= TooltipOverrides.MAX_LINES) { notice(tr("line_limit")); return; }
            minecraft.setScreen(new TipLineScreen(this, Line.text(""), line -> {
                lines.add(line); if (mode == Mode.DEFAULT) mode = Mode.APPEND;
            }));
        });
        button("copy_original", left + third + 6, 69, third, () -> attempt(() -> {
            var original = ClientTooltipConfig.original(stack);
            var copied = new ArrayList<Line>();
            for (int i = 1; i < original.size(); i++) {
                var source = original.get(i);
                // Keep animated rows animated and keep the authored visibility instead of flattening.
                copied.add(new Line(Component.Serializer.toJsonTree(source), Visibility.ALWAYS,
                        ClientTooltipConfig.isAnimated(source)));
            }
            var entry = new Entry(Mode.REPLACE, copied);
            TooltipOverrides.parseEntry(TooltipOverrides.serialize(entry));
            setEntry(entry); page = 0; rebuildWidgets();
        }));
        button("advanced", left + (third + 6) * 2, 69, third, () -> minecraft.setScreen(new TextConfigScreen(this,
                "advanced", "rich_hint", TooltipOverrides.serialize(draft()), text -> setEntry(TooltipOverrides.parseEntry(text)))));
        int rows = Math.max(1, (height - 193) / 24);
        int pages = Math.max(1, (lines.size() + rows - 1) / rows);
        // Clamp by the last page that still contains a row, otherwise deleting rows can leave a blank page.
        page = Math.min(page, pages - 1);
        if (page * rows > lines.size()) page = Math.max(0, (Math.max(1, lines.size()) - 1) / rows);
        for (int i = page * rows; i < Math.min(lines.size(), (page + 1) * rows); i++) {
            int index = i, y = 98 + (i % rows) * 24;
            var component = Component.Serializer.fromJson(lines.get(i).component());
            button(Component.literal((i + 1) + ". ").append(component), left, y, bodyWidth - 84,
                    () -> minecraft.setScreen(new TipLineScreen(this, lines.get(index), line -> lines.set(index, line))));
            // Cross-page moves follow the row instead of leaving the player on a page that lost it.
            button(Component.literal("↑"), left + bodyWidth - 80, y, 24, () -> {
                Collections.swap(lines, index, index - 1);
                page = Math.min(page, Math.max(0, (Math.max(1, lines.size()) - 1) / rows));
                rebuildWidgets();
            }).active = i > 0;
            button(Component.literal("↓"), left + bodyWidth - 53, y, 24, () -> {
                Collections.swap(lines, index, index + 1);
                rebuildWidgets();
            }).active = i < lines.size() - 1;
            button(Component.literal("×"), left + bodyWidth - 26, y, 26, () -> {
                lines.remove(index);
                if (mode == Mode.DEFAULT) mode = Mode.APPEND;
                rebuildWidgets();
            });
        }
        button(Component.literal("<"), left, height - 89, 32, () -> { page = Math.max(0, page - 1); rebuildWidgets(); }).active = page > 0;
        button(tr("page", page + 1, pages), left + 38, height - 89, bodyWidth - 76, () -> {}).active = false;
        button(Component.literal(">"), left + bodyWidth - 32, height - 89, 32,
                () -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); }).active = page < pages - 1;
        button("reset", left, height - 65, bodyWidth, () -> { setEntry(Entry.defaults()); rebuildWidgets(); });
        button("save", left, height - 27, half, () -> attempt(() -> { ClientTooltipConfig.save(id, draft()); clearStatus(); onClose(); }));
        button("cancel", left + half + 6, height - 27, half, this::onClose);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, clipped(Component.literal(id), bodyWidth), left, 29, 0xaaaaaa);
        // DEFAULT discards the authored rows, so say so instead of showing a list that does nothing.
        if (mode == Mode.DEFAULT && !lines.isEmpty()) {
            g.drawString(font, clipped(tr("default_ignores_lines"), bodyWidth), left, height - 105, 0xff5555);
        } else if (!lines.isEmpty() && mode != Mode.DEFAULT) {
            g.drawString(font, clipped(tr("draft_lines", lines.size()), bodyWidth), left, height - 105, 0x55ff55);
        }
        if (preview != null && preview.isHovered()) {
            attempt(() -> g.renderTooltip(font,
                    TooltipOverrides.compose(ClientTooltipConfig.original(stack), ClientTooltipConfig.render(draft(), hasShiftDown()), mode),
                    Optional.empty(), x, y));
        }
    }
}
