package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.ConfigFiles;
import com.gtl.enhancedcore.common.config.PreviewLayerSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** Local client option for the repeated-click structure preview layer selector. */
public final class PreviewLayerConfig {
    public static final String FILE = "preview.properties";
    private static volatile boolean enabled = true;

    private PreviewLayerConfig() {}

    public static void initialize() {
        enabled = read(directory().resolve(FILE));
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String serialize(boolean value) {
        return "# Client-only setting. Restart is not required.\npreviewLayers=" + value + "\n";
    }

    public static boolean parse(String text) {
        return PreviewLayerSettings.parse(text);
    }

    public static String update(String original, boolean value) {
        parse(original);
        return serialize(value);
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore");
    }

    private static boolean read(Path path) {
        try {
            if (!Files.exists(path)) ConfigFiles.writeAtomically(path, serialize(true));
            return parse(Files.readString(path));
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
