package com.gtl.enhancedcore.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.ConfigFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 「复制 ID / 复制配方」按键功能的开关配置，支持热重载。
 *
 * <p>配置文件 {@code config/GTL-Enhancedcore/copy_keys.json}：
 * <pre>
 * {
 *   "copyItemId": true,     // 按 0 复制悬停物品 ID
 *   "copyFluidId": true,    // 按 0 复制悬停流体 ID（原 mod 没有的功能）
 *   "copyRecipe": true,     // 按 . 复制 JEI 当前配方
 *   "liveGenerateRecipe": true  // 数据包读不到配方时实时生成（覆盖代码生成的配方）
 * }
 * </pre>
 *
 * <p>热重载：{@link #tick()} 每 20 tick 比对文件修改时间，变化即重读；另有
 * {@code /gtlenhancedcore copylkeys reload} 与配置界面按钮手动重载。
 * 文件不存在时写出默认值；解析失败保留上一份有效配置并记录原因，不抛异常打断客户端。
 */
public final class CopyKeysConfig {
    private static final String FILE_NAME = "copy_keys.json";

    /** 按 0 复制物品 ID。 */
    private static volatile boolean copyItemId = true;
    /** 按 0 复制流体 ID。 */
    private static volatile boolean copyFluidId = true;
    /** 按 . 复制当前查看的配方。 */
    private static volatile boolean copyRecipe = true;
    /** 数据包原始 JSON 读不到时，用配方对象实时生成 JSON。 */
    private static volatile boolean liveGenerateRecipe = true;

    private static FileTime lastModified;
    private static long lastCheck;
    private static String error = "";

    private CopyKeysConfig() {}

    public static boolean copyItemId() { return copyItemId; }
    public static boolean copyFluidId() { return copyFluidId; }
    public static boolean copyRecipe() { return copyRecipe; }
    public static boolean liveGenerateRecipe() { return liveGenerateRecipe; }
    public static String error() { return error; }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore").resolve(FILE_NAME);
    }

    /** 热重载：每 20 tick 检查一次文件修改时间，变化则重读。 */
    public static void tick() {
        if (++lastCheck % 20 != 0) return;
        try {
            Path file = path();
            if (!Files.exists(file)) {
                if (lastModified != null) {
                    lastModified = null;
                    resetDefaults();
                }
                return;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(lastModified)) return;
            lastModified = modified;
            reload(file);
        } catch (IOException | RuntimeException failure) {
            error = failure.getMessage();
            GTLEnhancedcore.LOGGER.error("Cannot hot-reload {}; keeping previous switches", FILE_NAME, failure);
        }
    }

    /** 手动重载（配置界面按钮 / 命令）。 */
    public static void reload() throws IOException {
        Path file = path();
        ensureFile(file);
        lastModified = Files.getLastModifiedTime(file);
        reload(file);
    }

    public static void initialize() {
        try {
            reload();
        } catch (IOException | RuntimeException failure) {
            error = failure.getMessage();
            GTLEnhancedcore.LOGGER.error("Cannot load {}; using defaults", FILE_NAME, failure);
        }
    }

    private static void reload(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        boolean item = flag(json, "copyItemId", true);
        boolean fluid = flag(json, "copyFluidId", true);
        boolean recipe = flag(json, "copyRecipe", true);
        boolean live = flag(json, "liveGenerateRecipe", true);
        copyItemId = item;
        copyFluidId = fluid;
        copyRecipe = recipe;
        liveGenerateRecipe = live;
        error = "";
    }

    private static boolean flag(JsonObject json, String key, boolean fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return fallback;
        return json.get(key).getAsBoolean();
    }

    /** 写入用户可编辑的开关（配置界面用）；随后立即生效并刷新修改时间戳。 */
    public static void save(boolean item, boolean fluid, boolean recipe, boolean live) throws IOException {
        // 手写格式化输出：这个文件是给玩家直接编辑的，逐行更易读（并带注释说明每个开关）。
        String text = "{\n"
                + "  \"_comment\": \"按 0 复制物品/流体 ID，按 . 复制 JEI 当前配方；改完自动热重载，或执行 /gtlenhancedcore copykeys reload\",\n"
                + "  \"copyItemId\": " + item + ",\n"
                + "  \"copyFluidId\": " + fluid + ",\n"
                + "  \"copyRecipe\": " + recipe + ",\n"
                + "  \"liveGenerateRecipe\": " + live + "\n"
                + "}\n";
        ConfigFiles.writeAtomically(path(), text);
        lastModified = Files.getLastModifiedTime(path());
        copyItemId = item;
        copyFluidId = fluid;
        copyRecipe = recipe;
        liveGenerateRecipe = live;
        error = "";
    }

    private static void resetDefaults() {
        copyItemId = true;
        copyFluidId = true;
        copyRecipe = true;
        liveGenerateRecipe = true;
    }

    private static void ensureFile(Path file) throws IOException {
        if (Files.exists(file)) return;
        save(true, true, true, true);
    }
}
