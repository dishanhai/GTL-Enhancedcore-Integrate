package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.ConfigCatalog;
import com.gtl.enhancedcore.common.config.ServerConfigEditor;
import com.gtl.enhancedcore.network.ConfigFilePackets;
import com.gtl.enhancedcore.network.GTLEnhancedcoreNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** No static screen references or callbacks survive disconnects; late replies are ignored. */
public final class RemoteConfigScreen extends ConfigPage {
    private static int sequence;
    private final ConfigCatalog kind;
    private String draft = "", expected = "";
    private boolean loaded, pending;
    private int requestId;
    private long started;
    private MultiLineEditBox editor;
    private Button save, controls;
    RemoteConfigScreen(Screen parent, ConfigCatalog kind) { super(parent, kind.key()); this.kind = kind; }
    @Override protected void init() {
        super.init();
        editor = addRenderableWidget(new MultiLineEditBox(font, left, 57, bodyWidth - 8,
                Math.max(30, height - 124), Component.empty(), title));
        editor.setCharacterLimit(8_000); editor.setValue(draft); editor.setValueListener(value -> draft = value);
        save = button("save", left, height - 27, bodyWidth / 2 - 3, () -> send(true));
        save.active = loaded && !pending;
        button("cancel", left + bodyWidth / 2 + 3, height - 27, bodyWidth / 2 - 3, this::onClose);
        if (kind == ConfigCatalog.STARTUP || kind == ConfigCatalog.COMMON) {
            controls = button("rule.controls", left + bodyWidth - 90, 31, 90, () -> attempt(() ->
                    minecraft.setScreen(new ConfigRulesScreen(this, kind, draft, text -> {
                        draft = text; minecraft.setScreen(this); send(true);
                    }))));
            controls.active = loaded && !pending;
        }
        if (!loaded && !pending) send(false);
    }
    private void send(boolean write) {
        if (pending || minecraft.getConnection() == null) return;
        pending = true; requestId = ++sequence; started = System.currentTimeMillis(); save.active = false;
        if (controls != null) controls.active = false;
        notice(tr(write && kind == ConfigCatalog.SINGULARITY ? "reload_pending" : "request_pending"));
        GTLEnhancedcoreNetworkHandler.CHANNEL.sendToServer(new ConfigFilePackets.Request(requestId, kind, write, expected, write ? draft : ""));
    }
    public static void receive(ConfigFilePackets.Response response) {
        if (Minecraft.getInstance().screen instanceof RemoteConfigScreen screen) screen.accept(response);
    }
    private void accept(ConfigFilePackets.Response response) {
        if (!pending || response.id() != requestId) return;
        pending = false;
        if (!response.error().isEmpty()) {
            var error = response.error().startsWith("config.gtl_enhancedcore.")
                    ? Component.translatable(response.error()) : Component.literal(response.error());
            notice(tr("error", error)); save.active = loaded;
            if (controls != null) controls.active = loaded;
            return;
        }
        if (response.saved()) {
            expected = ServerConfigEditor.digest(response.text());
            notice(tr("saved_effect", tr("effect." + kind.effect)));
        } else {
            draft = response.text(); expected = ServerConfigEditor.digest(draft); loaded = true;
            editor.setValue(draft); clearStatus();
            rebuildWidgets();
        }
        save.active = loaded;
        if (controls != null) controls.active = loaded;
    }
    @Override public void tick() {
        editor.tick();
        if (pending && System.currentTimeMillis() - started > 120_000L) {
            pending = false; save.active = loaded;
            if (controls != null) controls.active = loaded;
            notice(tr("request_timeout"));
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, clipped(tr("effect." + kind.effect), bodyWidth - 98), left, 31, 0xaaaaaa);
    }
}
