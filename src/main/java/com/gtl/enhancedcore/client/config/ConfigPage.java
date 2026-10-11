package com.gtl.enhancedcore.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared vanilla controls and a bounded status area for the local configuration screens. */
abstract class ConfigPage extends Screen {
    protected final Screen parent;
    protected Component status = Component.empty();
    protected int left, bodyWidth;
    protected ConfigPage(Screen parent, String title) { super(tr(title)); this.parent = parent; }
    public static Component tr(String key, Object... args) { return Component.translatable("config.gtl_enhancedcore." + key, args); }
    @Override protected void init() { bodyWidth = Math.min(640, width - 24); left = (width - bodyWidth) / 2; }
    protected Button button(Component text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(text, b -> action.run()).bounds(x, y, w, 20).build());
    }
    protected Button button(String key, int x, int y, int w, Runnable action) { return button(tr(key), x, y, w, action); }
    /** Variant that hands the button to the action, so a toggle can relabel itself in place. */
    protected Button button(Component text, int x, int y, int w, java.util.function.Consumer<Button> action) {
        return addRenderableWidget(Button.builder(text, action::accept).bounds(x, y, w, 20).build());
    }
    protected Button button(String key, int x, int y, int w, java.util.function.Consumer<Button> action) {
        return button(tr(key), x, y, w, action);
    }
    protected EditBox field(String label, String value, int x, int y, int w, int limit) {
        var box = addRenderableWidget(new EditBox(font, x, y, w, 20, tr(label)));
        box.setMaxLength(limit); box.setValue(value); box.setTooltip(Tooltip.create(tr(label))); return box;
    }
    @FunctionalInterface protected interface CheckedAction { void run() throws Exception; }
    protected void attempt(CheckedAction action) {
        try { action.run(); }
        catch (Exception ex) { failure(ex); }
    }
    /** Errors are shown as a full-width banner, not only as hover text on a thin strip. */
    protected void failure(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        status = tr("error", message);
        statusExpiry = System.currentTimeMillis() + 12_000L;
    }
    private long statusExpiry;
    /** True while a recent error is still worth showing. */
    protected boolean statusVisible() { return !status.getString().isEmpty() && System.currentTimeMillis() < statusExpiry; }
    protected void clearStatus() { status = Component.empty(); statusExpiry = 0L; }
    /** Shows a transient notice with the same 12-second lifetime as a failure banner. */
    protected void notice(Component message) {
        status = message;
        statusExpiry = System.currentTimeMillis() + 12_000L;
    }
    protected net.minecraft.util.FormattedCharSequence clipped(Component component, int width) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(component, width));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xffffff);
        super.render(graphics, mouseX, mouseY, partial);
        if (!statusVisible()) {
            drawStatusBanner(graphics);
            return;
        }
        // Wrap the message so long validation errors stay readable without hovering.
        var wrapped = font.split(status, bodyWidth);
        int y = height - 43;
        for (int i = 0; i < Math.min(wrapped.size(), 3); i++) {
            graphics.drawString(font, wrapped.get(i), left, y + i * 10, 0xffcc66);
        }
    }
    private void drawStatusBanner(GuiGraphics graphics) {
        if (status.getString().isEmpty()) return;
        graphics.drawString(font, clipped(status, bodyWidth), left, height - 43, 0xffcc66);
        if (statusExpiry != 0L) clearStatus();
    }
}
