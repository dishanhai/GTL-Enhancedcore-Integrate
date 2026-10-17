package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

/** Independent UTF-8 file and last-valid hot-reload snapshot; no game classes needed for parsing. */
public final class TerminalDismantleConfig {
    public static final String FILE = "terminal_dismantle.properties";
    public static final int RECHECK_TICKS = 20;
    public static final int MAX_CHARACTERS = 8000;
    private static final int MAX_BYTES = 32000;
    public record Settings(int maxScansPerTick, int maxRemovalsPerTick, int budgetMilliseconds,
                           int unloadedWaitTicks, int maxConcurrentTasks) {}

    private TerminalDismantleConfig() {}

    public static Settings defaults() { return new Settings(1024, 32, 3, 600, 16); }
    public static String defaultText() { return serialize(defaults()); }

    /** Caller owns lifecycle/thread selection; existing player edits are never rewritten. */
    public static void ensureDefaultConfig(Path file) throws IOException {
        if (!Files.exists(file)) ConfigFiles.writeAtomically(file, defaultText());
    }

    public static Settings parse(String text) {
        if (text.length() > MAX_CHARACTERS) throw new IllegalArgumentException("file exceeds 8000 characters");
        Properties p = new Properties();
        try { p.load(new StringReader(text.startsWith("\uFEFF") ? text.substring(1) : text)); }
        catch (IOException impossible) { throw new IllegalArgumentException(impossible); }
        Settings fallback = defaults();
        return new Settings(number(p, "maxScansPerTick", fallback.maxScansPerTick(), 1, 65536),
                number(p, "maxRemovalsPerTick", fallback.maxRemovalsPerTick(), 1, 4096),
                number(p, "budgetMilliseconds", fallback.budgetMilliseconds(), 1, 20),
                number(p, "unloadedWaitTicks", fallback.unloadedWaitTicks(), 20, 72000),
                number(p, "maxConcurrentTasks", fallback.maxConcurrentTasks(), 1, 64));
    }

    private static int number(Properties p, String key, int fallback, int min, int max) {
        String raw = p.getProperty(key, Integer.toString(fallback)).trim();
        try {
            int value = Integer.parseInt(raw);
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key + ": expected " + min + ".." + max + ", got " + raw);
    }

    public static String serialize(Settings s) {
        return "# 终极终端分批拆除 / Ultimate Terminal batched dismantling\n"
                + "# UTF-8；每20个服务端tick检查修改，下一tick批次使用最新有效配置，无需重启。\n"
                + "# Invalid edits retain the last valid settings. Checked every 20 server ticks.\n"
                + "# 全服共享预算；耗时是操作之间的软上限，无法中断第三方单次回调。\n"
                + "# maxScansPerTick: 每tick最多扫描格数 / scanned cells, 1..65536\n"
                + "maxScansPerTick=" + s.maxScansPerTick() + "\n"
                + "# maxRemovalsPerTick: 每tick最多成功移除方块 / successful removals, 1..4096\n"
                + "maxRemovalsPerTick=" + s.maxRemovalsPerTick() + "\n"
                + "# budgetMilliseconds: 每tick软耗时上限 / soft time budget in ms, 1..20\n"
                + "budgetMilliseconds=" + s.budgetMilliseconds() + "\n"
                + "# unloadedWaitTicks: 未加载区块最多等待tick / unloaded-chunk wait, 20..72000\n"
                + "unloadedWaitTicks=" + s.unloadedWaitTicks() + "\n"
                + "# maxConcurrentTasks: 同时拆除任务上限 / admitted concurrent tasks, 1..64\n"
                + "# 调低并发数不会取消已有任务，只影响新任务。 / Lowering this limit affects new tasks only.\n"
                + "maxConcurrentTasks=" + s.maxConcurrentTasks() + "\n";
    }

    /** One instance per server lifecycle, confined to its server thread. */
    public static final class Loader {
        private final Path file;
        private final Consumer<String> warning;
        private Settings current = defaults();
        private FileTime modified;
        private Object fileKey;
        private long size = -1;
        private long lastCheckedTick = Long.MIN_VALUE;
        private String lastFailure;

        public Loader(Path file, Consumer<String> warning) {
            this.file = file;
            this.warning = warning;
        }

        public Settings current() { return current; }

        public Settings poll(long serverTick) {
            if (lastCheckedTick != Long.MIN_VALUE && serverTick >= lastCheckedTick
                    && serverTick - lastCheckedTick < RECHECK_TICKS) return current;
            lastCheckedTick = serverTick;
            try {
                ensureDefaultConfig(file);
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                if (attributes.lastModifiedTime().equals(modified) && attributes.size() == size
                        && Objects.equals(attributes.fileKey(), fileKey)) return current;
                modified = attributes.lastModifiedTime();
                size = attributes.size();
                fileKey = attributes.fileKey();
                if (size > MAX_BYTES) throw new IllegalArgumentException("file exceeds 32000 bytes");
                Settings loaded = parse(Files.readString(file));
                current = loaded;
                lastFailure = null;
            } catch (IOException | RuntimeException failure) {
                if (failure instanceof IOException) {
                    // A temporary lock can clear without a new mtime. Retry I/O on the next poll.
                    modified = null;
                    fileKey = null;
                    size = -1;
                }
                String description = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                if (!description.equals(lastFailure)) {
                    lastFailure = description;
                    warning.accept("Terminal dismantle config rejected; retaining last valid settings ("
                            + file + "): " + description);
                }
            }
            return current;
        }
    }
}
