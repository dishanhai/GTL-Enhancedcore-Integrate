package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Protects the GTLCore structure detector overrides against upstream ABI drift and mode regressions. */
final class StructureDetectRegression {
    private static int assertions;

    static int run() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/StructureDetectNormalModeMixin.java"));
        check(mixin.contains("method = \"onItemUseFirst\"") && mixin.contains("tag.putBoolean(\"isFlipped\", false)"),
                "legacy mode state is cleared and the mode toggle interaction is intercepted");
        check(mixin.contains("@WrapMethod(method = \"check\"")
                        && mixin.contains("@Redirect(method = \"check\"")
                        && mixin.contains("upwards, false, invert)"),
                "the pinned GTLCore scan call forces mirror=false while preserving the normal rotation candidates");
        check(mixin.contains("StructureDetectScanContext.record(matched && !state.hasError())")
                        && mixin.contains("scope.finish(original.call(controller, pattern, false))")
                        && mixin.contains("finally") && mixin.contains("scope.close()"),
                "successful normal rotations suppress errors from other failed rotations");
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject();
        check(config.getAsJsonArray("mixins").asList().stream()
                        .filter(entry -> entry.getAsString().equals("gtlcore.StructureDetectNormalModeMixin"))
                        .count() == 1,
                "detector behavior override is registered once on both logical sides");
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + lang + ".json"))).getAsJsonObject();
            check(translations.has("message.gtl_enhancedcore.structure_detect.normal_only"),
                    "normal-only detector feedback is localized: " + lang);
            check(translations.has("item.gtlcore.structure_detect.tooltip.1")
                            && !translations.get("item.gtlcore.structure_detect.tooltip.1").getAsString()
                                    .toLowerCase(java.util.Locale.ROOT).contains("mirror")
                            && translations.has("message.gtlcore.detection_mode_mirrored"),
                    "GTLCore mirror-mode tooltip and fallback status are replaced: " + lang);
        }
        assertions += StructureDetectScanContextRegression.run();
        return assertions;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
