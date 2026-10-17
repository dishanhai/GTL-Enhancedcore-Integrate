package com.gtl.enhancedcore.common.config;

import java.nio.file.Path;

/** Machine switches are independent and frozen when Mixin bootstraps. */
public final class MachineOverrideConfig {
    public static final String FILE_NAME = "GTL-Enhancedcore/startup.properties";
    public static final String STRUCTURES = "replaceOriginalStructures";
    public static final String ENHANCEMENTS = "enhanceOriginalMachines";
    private MachineOverrideConfig() {}
    public record Settings(boolean replaceOriginalStructures, boolean enhanceOriginalMachines) {}
    private static final class Loaded {
        private static final StartupConfig.Settings STARTUP = StartupConfig.current();
        private static final Settings SETTINGS = new Settings(STARTUP.replaceOriginalStructures(), STARTUP.enhanceOriginalMachines());
    }
    public static Settings current() { return Loaded.SETTINGS; }
    public static Settings read(Path path) {
        var settings = StartupConfig.read(path);
        return new Settings(settings.replaceOriginalStructures(), settings.enhanceOriginalMachines());
    }
}
