package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Current-file consolidation requested by the user. Retired files are moved to a recoverable sibling folder. */
public final class ConfigConsolidation {
    private ConfigConsolidation() {}
    public static List<String> consolidate(Path directory) throws IOException {
        StartupConfig.loadDirectory(directory);
        List<String> names = new ArrayList<>(List.of("client.toml", "chronal-altars-client.toml",
                "gtl_enhancedcore_recipe_type_sets.json", "foreign_machines.properties", "performance.properties"));
        Path duplicate = directory.resolve("gtl_enhancedcore_ae_pins.json");
        Path pins = directory.resolve("ae_pins.json");
        if (Files.isRegularFile(duplicate, LinkOption.NOFOLLOW_LINKS)) {
            String old = Files.readString(duplicate).strip();
            if (!Files.exists(pins)) ConfigFiles.writeAtomically(pins, old);
            if (old.equals("[]") || old.equals(Files.readString(pins).strip())) names.add(duplicate.getFileName().toString());
        }
        var moved = new ArrayList<String>();
        Path backup = directory.toAbsolutePath().getParent().resolve("GTL-Enhancedcore-backups")
                .resolve("retired-" + System.currentTimeMillis());
        for (String name : names) {
            Path source = directory.resolve(name);
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) continue;
            Files.createDirectories(backup);
            Files.move(source, backup.resolve(name));
            moved.add(name);
        }
        return List.copyOf(moved);
    }
}
