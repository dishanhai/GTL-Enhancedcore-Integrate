package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.config.TerminalDismantleConfig;
import com.gtl.enhancedcore.common.config.TerminalDismantleConfig.Loader;
import com.gtl.enhancedcore.common.config.TerminalDismantleConfig.Settings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Temporary files only: parsing, throttled reload, invalid-edit recovery and lifecycle isolation. */
final class TerminalDismantleConfigRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        check(TerminalDismantleConfig.parse("").equals(new Settings(1024, 32, 3, 600, 16)),
                "an empty file keeps the established dismantling defaults");
        check(TerminalDismantleConfig.parse(TerminalDismantleConfig.defaultText()).equals(TerminalDismantleConfig.defaults()),
                "generated UTF-8 default text round-trips");
        check(TerminalDismantleConfig.defaultText().contains("无需重启"), "default comments explain hot reload in UTF-8");
        check(TerminalDismantleConfig.parse("\uFEFFmaxScansPerTick=2\nunknown=preserved\n").maxScansPerTick() == 2,
                "BOM and unknown editor fields do not invalidate settings");
        for (String invalid : List.of("maxScansPerTick=0", "maxScansPerTick=65537", "maxRemovalsPerTick=0",
                "maxRemovalsPerTick=4097", "budgetMilliseconds=0", "budgetMilliseconds=21",
                "unloadedWaitTicks=19", "unloadedWaitTicks=72001", "maxConcurrentTasks=0", "maxConcurrentTasks=65",
                "maxScansPerTick=999999999999999", "budgetMilliseconds=NaN", "maxConcurrentTasks=true", "#".repeat(8001))) {
            try {
                TerminalDismantleConfig.parse(invalid);
                throw new AssertionError("invalid dismantle setting accepted: " + invalid);
            } catch (IllegalArgumentException expected) { assertions++; }
        }
        check(TerminalDismantleConfig.parse("maxScansPerTick=65536\nmaxRemovalsPerTick=4096\n"
                + "budgetMilliseconds=20\nunloadedWaitTicks=72000\nmaxConcurrentTasks=64\n")
                .equals(new Settings(65536, 4096, 20, 72000, 64)), "documented upper bounds are inclusive");

        Path root = Files.createTempDirectory("enhancedcore-dismantle-config-");
        try {
            Path file = root.resolve("GTL-Enhancedcore").resolve(TerminalDismantleConfig.FILE);
            List<String> warnings = new ArrayList<>();
            Loader loader = new Loader(file, warnings::add);
            check(!Files.exists(file), "constructing a loader does not touch any filesystem path");
            check(loader.poll(0).equals(TerminalDismantleConfig.defaults()) && Files.exists(file),
                    "the first server-owned poll creates the dedicated default file");
            Settings changed = new Settings(12, 3, 1, 40, 2);
            writeChanged(file, TerminalDismantleConfig.serialize(changed));
            check(loader.poll(19).equals(TerminalDismantleConfig.defaults()), "mtime reads are throttled to twenty ticks");
            check(loader.poll(20).equals(changed), "a file edit is adopted on the next scheduled poll");
            check(loader.current().equals(changed), "running tasks can read the new immutable snapshot immediately");

            writeChanged(file, "maxScansPerTick=0\n");
            check(loader.poll(40).equals(changed), "an invalid edit retains the complete last valid snapshot");
            Loader nextServer = new Loader(file, ignored -> { });
            check(nextServer.poll(0).equals(TerminalDismantleConfig.defaults()) && loader.current().equals(changed),
                    "a new server lifecycle does not inherit the previous server's last-valid edits");
            check(warnings.size() == 1, "an invalid edit warns once");
            loader.poll(60);
            check(warnings.size() == 1, "unchanged invalid file does not repeat warnings");
            writeChanged(file, "maxScansPerTick=0\n");
            loader.poll(80);
            check(warnings.size() == 1, "resaving the same invalid value also deduplicates warnings");
            check(Files.readString(file).equals("maxScansPerTick=0\n"), "rejected player edits remain available for correction");
            writeChanged(file, TerminalDismantleConfig.defaultText());
            check(loader.poll(100).equals(TerminalDismantleConfig.defaults()), "corrected file recovers without a restart");
            writeChanged(file, "maxScansPerTick=0\n");
            loader.poll(120);
            check(warnings.size() == 2, "a new invalid episode after recovery is reported once");

            Files.delete(file);
            check(loader.poll(140).equals(TerminalDismantleConfig.defaults()) && Files.exists(file),
                    "deleting the file safely recreates documented defaults");

            Path blocked = root.resolve("not-a-directory");
            Files.writeString(blocked, "file");
            List<String> ioWarnings = new ArrayList<>();
            Loader unavailable = new Loader(blocked.resolve(TerminalDismantleConfig.FILE), ioWarnings::add);
            check(unavailable.poll(0).equals(TerminalDismantleConfig.defaults()), "I/O failure cannot abort the server tick");
            unavailable.poll(20);
            check(ioWarnings.size() == 1, "repeated I/O failure is also warning-deduplicated");
            writeChanged(file, "#".repeat(65537));
            check(loader.poll(160).equals(TerminalDismantleConfig.defaults()), "oversized file keeps the last valid settings");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        return assertions;
    }

    private static void writeChanged(Path path, String text) throws Exception {
        long timestamp = Files.getLastModifiedTime(path).toMillis();
        Files.writeString(path, text);
        Files.setLastModifiedTime(path, FileTime.fromMillis(timestamp + 1000));
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
