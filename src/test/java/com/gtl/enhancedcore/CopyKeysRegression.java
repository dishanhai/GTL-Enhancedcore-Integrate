package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 「按 0 复制物品/流体 ID」「按 . 复制配方 JSON」功能回归。
 *
 * <p>移植自 JEI-Copy-Recipe-Json，并按用户 2026-10-12 的要求补上：
 * 流体 ID、配置文件开关、热重载、以及数据包读不到时实时生成配方 JSON。
 */
public final class CopyKeysRegression {
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        keyBindings();
        idCopy();
        recipeCopy();
        configuration();
        mixinWiring();
        return assertions;
    }

    /** 按键默认绑定 0 与 .，且注册到自己的分类下。 */
    private static void keyBindings() throws Exception {
        String client = source("client/copy/CopyKeysClient.java");
        check(client.contains("GLFW_KEY_0"), "The ID copy key defaults to 0 as requested");
        check(client.contains("GLFW_KEY_PERIOD"), "The recipe copy key defaults to the period key as requested");
        check(client.contains("key.categories.gtl_enhancedcore"), "Both keys live in the mod's own category");
        check(client.contains("RegisterKeyMappingsEvent"), "The keys are registered through Forge");
        check(client.contains("ScreenEvent.KeyPressed.Pre"),
                "Key handling works in any screen, so container and JEI views both respond");
    }

    /** 物品与流体都要能复制，且输出带引号的 JSON 片段。 */
    private static void idCopy() throws Exception {
        String actions = source("client/copy/CopyActions.java");
        check(actions.contains("copyItem(") && actions.contains("copyFluid("),
                "Both item and fluid IDs can be copied");
        check(actions.contains("gtl_enhancedcore.copy.fluid"),
                "The fluid branch has its own feedback message");
        String clipboard = source("client/copy/RecipeClipboard.java");
        check(clipboard.contains("return \"\\\"\" + id + \"\\\"\";"),
                "IDs are copied as quoted JSON fragments, matching the chosen format");
        // 流体要同时用 Forge 与 vanilla 注册表取 ID：流体注册在 vanilla 注册表。
        check(clipboard.contains("ForgeRegistries.ITEMS.getKey") && clipboard.contains("BuiltInRegistries.FLUID.getKey"),
                "Item and fluid IDs come from the correct registries");
        // 流体悬停判定必须走 JEI，因为流体不会触发物品 tooltip。
        String lookup = source("client/copy/CopyHoverLookup.java");
        check(lookup.contains("getIngredientUnderMouse"),
                "Fluid hover is resolved through JEI's ingredient lookup");
        check(lookup.contains("isLoaded(\"jei\")"),
                "The JEI-only path is guarded so a JEI-less install stays safe");
    }

    /** 配方 JSON：数据包优先，实时生成为兜底（原 mod 只有前者）。 */
    private static void recipeCopy() throws Exception {
        String clipboard = source("client/copy/RecipeClipboard.java");
        check(clipboard.contains("recipes/"), "The datapack recipe file is preferred when present");
        check(clipboard.contains("liveJson("), "A live generator exists for code-registered recipes");
        check(clipboard.contains("recipe instanceof GTRecipe gt"), "GT recipes expose their typed contents");
        check(clipboard.contains("gt_recipe_type") && clipboard.contains("duration"),
                "Generated JSON carries the GT recipe type and duration");
        check(clipboard.contains("tick_inputs") && clipboard.contains("tick_outputs"),
                "Tick contents are exported too, so nothing is silently dropped");
        // 实时生成受开关控制。
        check(clipboard.contains("CopyKeysConfig.liveGenerateRecipe()"),
                "Live generation can be disabled from the configuration");
    }

    /** 配置文件：四个开关、可写、热重载、失败不致命。 */
    private static void configuration() throws Exception {
        String config = source("client/config/CopyKeysConfig.java");
        for (String key : new String[]{"copyItemId", "copyFluidId", "copyRecipe", "liveGenerateRecipe"}) {
            check(config.contains(key), "The configuration exposes the switch " + key);
        }
        check(config.contains("Files.getLastModifiedTime"),
                "The file timestamp is polled so edits hot-reload without a restart");
        check(config.contains("ConfigFiles.writeAtomically"),
                "Saving reuses the project's atomic writer");
        check(config.contains("GTLEnhancedcore.LOGGER.error"),
                "A malformed file is logged and the previous switches are kept");
        // 默认值必须与实现一致（构造函数/字段初始值）。
        check(CopyKeysConfig.copyItemId() && CopyKeysConfig.copyFluidId()
                        && CopyKeysConfig.copyRecipe() && CopyKeysConfig.liveGenerateRecipe(),
                "All four switches default to enabled");
        // 客户端 tick 里挂上热重载。
        check(source("client/copy/CopyKeysClient.java").contains("CopyKeysConfig.tick()"),
                "The client tick drives configuration hot-reload");
    }

    /** JEI mixin 必须注册且仅在 JEI 存在时应用，并且不吞掉非本功能的输入。 */
    private static void mixinWiring() throws Exception {
        String mixins = Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"));
        check(mixins.contains("\"jei.JeiRecipeCopyMixin\""), "The JEI recipe copy mixin is registered as client-only");
        String mixin = source("mixin/jei/JeiRecipeCopyMixin.java");
        check(mixin.contains("RecipeLayoutWithButtons$RecipeLayoutUserInputHandler"),
                "The mixin targets JEI's recipe input handler, where the recipe object is available");
        check(mixin.contains("getRegistryName"), "The recipe registry ID is read from the recipe category");
        check(mixin.contains("recipeCopyActive(key)"),
                "Unmatched keys fall through so JEI's own bindings keep working");
        check(mixin.contains("input.isSimulate()"), "Simulated input does not trigger a copy");
        check(!mixin.contains("@Overwrite"), "The mixin injects instead of overwriting upstream behaviour");
        String plugin = source("common/performance/PerformanceMixinPlugin.java");
        check(plugin.contains("JEI_RECIPE_COPY") && plugin.contains("shouldApplyMixin"),
                "The mixin is skipped when JEI is absent");
        // 中英两侧的按键与消息键必须齐全。
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            String json = Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + lang + ".json"));
            for (String key : new String[]{"key.gtl_enhancedcore.copy_id", "key.gtl_enhancedcore.copy_recipe",
                    "gtl_enhancedcore.copy.item", "gtl_enhancedcore.copy.fluid", "gtl_enhancedcore.copy.recipe"}) {
                check(json.contains("\"" + key + "\""), "Missing language key in " + lang + ": " + key);
            }
        }
        // 消息键带 %s 占位符。
        String zh = Files.readString(Path.of("src/main/resources/assets/gtl_enhancedcore/lang/zh_cn.json"));
        var pattern = Pattern.compile("\"gtl_enhancedcore\\.copy\\.(item|fluid|recipe)\": \"[^\"]*%s[^\"]*\"");
        check(pattern.matcher(zh).results().count() >= 3, "The copy feedback messages keep their placeholders");
    }

    private static String source(String relative) throws Exception {
        return Files.readString(JAVA.resolve(relative));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
