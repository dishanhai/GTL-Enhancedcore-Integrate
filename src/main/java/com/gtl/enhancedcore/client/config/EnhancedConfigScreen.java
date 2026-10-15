package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.EditableConfigFile;
import com.gtl.enhancedcore.common.config.TooltipOverrides;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge's Mods → Config entry point for this mod's item Tips. */
public final class EnhancedConfigScreen extends ConfigPage {
    public EnhancedConfigScreen(Screen parent) { super(parent, "title"); }
    static Path directory() { return FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore"); }
    @Override protected void init() {
        super.init(); int half = (bodyWidth - 6) / 2;
        button("tips", left, 45, bodyWidth, () -> minecraft.setScreen(new ItemTipBrowser(this)));
        button("reload_tips", left, 71, half, () -> attempt(() -> { ClientTooltipConfig.reload(); status = tr("reloaded"); }));
        button("tips_file", left + half + 6, 71, half, () -> attempt(this::openTooltipFile));
        button("copy_keys", left, 97, bodyWidth, () -> minecraft.setScreen(new CopyKeysScreen(this)));
        button("open_folder", left, 123, bodyWidth, () -> attempt(() -> {
            Files.createDirectories(directory()); Util.getPlatform().openFile(directory().toFile());
        }));
        button("back", left, height - 27, bodyWidth, this::onClose);
    }
    private void openTooltipFile() throws Exception {
        var file = new EditableConfigFile(directory().resolve("item_tooltips.json"));
        String initial = file.exists() ? file.text() : new TooltipOverrides(java.util.Map.of())
                .include(ClientTooltipConfig.itemIds()).serialize();
        minecraft.setScreen(new TextConfigScreen(this, "tips_file", "rich_hint", initial, text -> {
            var candidate = TooltipOverrides.parse(text);
            for (var entry : candidate.items().values()) for (var line : entry.lines())
                if (net.minecraft.network.chat.Component.Serializer.fromJson(line.component()) == null)
                    throw new IllegalArgumentException("Invalid component");
            file.save(candidate.include(ClientTooltipConfig.itemIds()).serialize()); ClientTooltipConfig.reload();
        }));
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawCenteredString(font, tr("subtitle"), width / 2, 29, 0xaaaaaa);
    }
}
