package com.gtl.enhancedcore.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 更新公告配置（GTL-Enhancedcore，2026-09-02）。
 *
 * 单独一份 JSON 配置：{@code config/GTL-Enhancedcore/changelog.json}，与 common.toml 分开，
 * 因为公告是长文本 + 多段结构，TOML 表达起来很难看，且整合包作者每版都要改这一处。
 *
 * <p>结构（首次启动自动生成示例，即 1.1 公告）：
 * <pre>
 * {
 *   "enabled": true,          // 总开关
 *   "version": "1.1",         // 公告版本号；改了它，"不再提示"的记录自动失效，公告会重新弹一次
 *   "title": "GregTech Leisure-增强版：1.1",
 *   "sections": [             // 分段；每段一个小标题 + 若干行
 *     { "heading": "问题修复", "lines": ["...", "..."] }
 *   ]
 * }
 * </pre>
 *
 * <p>"不再提示" 记录写在 {@code config/GTL-Enhancedcore/changelog_seen.txt}（存的是版本号），
 * 不写进本配置，避免玩家点了按钮就把整合包作者的配置文件改脏。
 */
public final class ChangelogConfig {

    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("GTL-Enhancedcore").resolve("changelog.json");
    private static final Path SEEN_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("GTL-Enhancedcore").resolve("changelog_seen.txt");

    /** 写出默认配置用的格式化 Gson（不转义中文）。 */
    private static final com.google.gson.Gson GSON = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    private static ChangelogConfig loaded;

    private final boolean enabled;
    private final String version;
    private final String title;
    private final List<Section> sections;

    private ChangelogConfig(boolean enabled, String version, String title, List<Section> sections) {
        this.enabled = enabled;
        this.version = version;
        this.title = title;
        this.sections = sections;
    }

    /** 公告一段：小标题 + 正文行。 */
    public record Section(String heading, List<String> lines) {}

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getVersion() {
        return this.version;
    }

    public String getTitle() {
        return this.title;
    }

    public List<Section> getSections() {
        return this.sections;
    }

    /** 公告是否有实际内容（标题或任意一段非空）。 */
    public boolean hasContent() {
        if (!this.title.isBlank()) {
            return true;
        }
        for (Section section : this.sections) {
            if (!section.heading().isBlank() || !section.lines().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 读取配置（懒加载并缓存）；文件不存在时写出 1.1 示例。 */
    public static ChangelogConfig get() {
        if (loaded == null) {
            loaded = load();
        }
        return loaded;
    }

    /** Called after a validated in-game edit; the next announcement uses the current file. */
    public static void reload() {
        loaded = load();
    }

    /** 是否该在本次启动弹出公告：总开关开 + 有内容 + 当前版本未被"不再提示"。 */
    public static boolean shouldShow() {
        ChangelogConfig config = get();
        if (!config.isEnabled() || !config.hasContent()) {
            return false;
        }
        return !config.getVersion().equals(readSeenVersion());
    }

    /** 记录"不再提示"：把当前公告版本号写入 seen 文件。 */
    public static void markSeen() {
        ChangelogConfig config = get();
        try {
            Files.createDirectories(SEEN_FILE.getParent());
            ConfigFiles.writeAtomically(SEEN_FILE, config.getVersion());
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 写入不再提示标记失败: {}", SEEN_FILE, exception);
        }
    }

    private static String readSeenVersion() {
        try {
            if (Files.exists(SEEN_FILE)) {
                return Files.readString(SEEN_FILE, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 读取不再提示标记失败: {}", SEEN_FILE, exception);
        }
        return "";
    }

    private static ChangelogConfig load() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeDefault();
                return defaults();
            }
            JsonObject root = JsonParser.parseString(
                    Files.readString(CONFIG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            String version = root.has("version") ? root.get("version").getAsString() : "";
            String title = root.has("title") ? root.get("title").getAsString() : "";
            List<Section> sections = new ArrayList<>();
            if (root.has("sections")) {
                for (JsonElement element : root.getAsJsonArray("sections")) {
                    JsonObject object = element.getAsJsonObject();
                    String heading = object.has("heading") ? object.get("heading").getAsString() : "";
                    List<String> lines = new ArrayList<>();
                    if (object.has("lines")) {
                        for (JsonElement line : object.getAsJsonArray("lines")) {
                            lines.add(line.getAsString());
                        }
                    }
                    sections.add(new Section(heading, Collections.unmodifiableList(lines)));
                }
            }
            return new ChangelogConfig(enabled, version, title, Collections.unmodifiableList(sections));
        } catch (Exception exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 读取 {} 失败，改用内置示例", CONFIG_FILE, exception);
            return defaults();
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            ConfigFiles.writeAtomically(CONFIG_FILE, toJson(defaults()));
            GTLEnhancedcore.LOGGER.info("[Changelog] 已生成默认更新公告配置: {}", CONFIG_FILE);
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 生成默认配置失败: {}", CONFIG_FILE, exception);
        }
    }

    private static String toJson(ChangelogConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", config.isEnabled());
        root.addProperty("version", config.getVersion());
        root.addProperty("title", config.getTitle());
        JsonArray sections = new JsonArray();
        for (Section section : config.getSections()) {
            JsonObject object = new JsonObject();
            object.addProperty("heading", section.heading());
            JsonArray lines = new JsonArray();
            for (String line : section.lines()) {
                lines.add(line);
            }
            object.add("lines", lines);
            sections.add(object);
        }
        root.add("sections", sections);
        return GSON.toJson(root);
    }

    /** 内置默认 = 2.9 更新公告（用户 2026-10-17 提供，桌面更新说明.txt）。 */
    private static ChangelogConfig defaults() {
        List<Section> sections = List.of(
                new Section("问题修复", List.of(
                        "修复了星核剥离装置可能造成的空指针崩溃",
                        "修复了样板生成器在预览样板的时候 下一页会误触到底层",
                        "优化了内存占用过高的问题",
                        "修复了部分跨配方类型并行机器jade与内部GUI显示不一致的问题")),
                new Section("机制更改", List.of(
                        "在配置文件里面增加了是否替换原版结构的配置 可以自行开启或者关闭",
                        "木化工厂添加15条跨配方线程",
                        "shift右键预览结构 二次点击的层级显示 增加可配置的关闭按钮",
                        "14台末晶谐振器数值增强",
                        "删除了多方块结构里面看不到的实体方块 新档内存占用会显著降低",
                        "样板检测工具 移除镜像模式并且优化了逻辑",
                        "EAEP下单页面的shift右键打开容器距离调整为2万",
                        "优化了终端拆除模式卡顿的问题",
                        "终端拆除模式 现在在拆除一次后会自动关闭",
                        "伏羲八卦锻天炉 获得了跨配方类型并行 并获得了128条跨配方线程",
                        "修改了ME元件集成仓的遍寻与保存逻辑 现在性能会更好",
                        "LV与MV的无线充能器重构 改为单方块机器")),
                new Section("添加", List.of(
                        "自适应维护仓 自动识别所有维护环境 支持所有维护环境",
                        "私人维度创建器 每个玩家独立的维度 支持高度自定义",
                        "私人维度传送器 仅由OP自定义维度 玩家无法自定义 但任然保持玩家独立维度")));
        return new ChangelogConfig(true, "2.9", "GregTech Leisure-增强版：2.9", sections);

    }
}
