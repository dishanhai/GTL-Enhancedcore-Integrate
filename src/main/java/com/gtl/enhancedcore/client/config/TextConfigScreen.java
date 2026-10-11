package com.gtl.enhancedcore.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Multiline editor for structured configuration; invalid edits never reach the backing file. */
final class TextConfigScreen extends ConfigPage {
    @FunctionalInterface interface Saver { void save(String value) throws Exception; }
    private final String hint;
    private final Saver saver;
    private String draft;
    private MultiLineEditBox editor;
    TextConfigScreen(Screen parent, String title, String hint, String initial, Saver saver) {
        super(parent, title); this.hint = hint; this.draft = initial; this.saver = saver;
    }
    @Override protected void init() {
        super.init();
        editor = addRenderableWidget(new MultiLineEditBox(font, left, 54, bodyWidth - 8,
                Math.max(30, height - 118), Component.empty(), title));
        editor.setCharacterLimit(2_000_000); editor.setValue(draft); editor.setValueListener(s -> draft = s);
        button("save", left, height - 27, bodyWidth / 2 - 3, () -> attempt(() -> {
            saver.save(draft); onClose();
        }));
        button("cancel", left + bodyWidth / 2 + 3, height - 27, bodyWidth / 2 - 3, this::onClose);
        setInitialFocus(editor);
    }
    @Override public void tick() { editor.tick(); }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawWordWrap(font, tr(hint), left, 29, bodyWidth, 0xaaaaaa);
    }
}
