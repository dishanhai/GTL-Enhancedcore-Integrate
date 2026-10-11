package com.gtl.enhancedcore.common.config;

import java.util.*;

/** Inline authoring tags. Unknown/incomplete tags remain visible; [[ escapes a literal bracket. */
public final class TooltipMarkup {
    private static final int MAX_TAG_DISTANCE = 256;
    /** [字体=...] must fit within the parser's tag boundary. */
    public static final int INLINE_FONT_LIMIT = MAX_TAG_DISTANCE - 1 - "字体=".length();
    public record ColorPreset(String id, String tag, int rgb) {}
    private static final List<ColorPreset> PALETTE = List.of(
            new ColorPreset("black", "黑色", 0x000000), new ColorPreset("dark_blue", "深蓝", 0x0000aa),
            new ColorPreset("dark_green", "深绿", 0x00aa00), new ColorPreset("dark_aqua", "深青", 0x00aaaa),
            new ColorPreset("dark_red", "深红", 0xaa0000), new ColorPreset("dark_purple", "深紫", 0xaa00aa),
            new ColorPreset("gold", "金色", 0xffaa00), new ColorPreset("gray", "灰色", 0xaaaaaa),
            new ColorPreset("dark_gray", "深灰", 0x555555), new ColorPreset("blue", "蓝色", 0x5555ff),
            new ColorPreset("green", "绿色", 0x55ff55), new ColorPreset("aqua", "青色", 0x55ffff),
            new ColorPreset("red", "红色", 0xff5555), new ColorPreset("light_purple", "浅紫", 0xff55ff),
            new ColorPreset("yellow", "黄色", 0xffff55), new ColorPreset("white", "白色", 0xffffff));
    private static final Map<String, Integer> COLORS = colors();
    private static final Map<String, String> STYLE_TAGS = Map.of(
            "bold", "粗体", "italic", "斜体", "underlined", "下划线", "strikethrough", "删除线", "obfuscated", "随机字符");
    public static final List<String> STYLE_KEYS = List.of("bold", "italic", "underlined", "strikethrough", "obfuscated");
    private static final Map<String, String> STYLES = Map.ofEntries(
            Map.entry("粗体", "bold"), Map.entry("加粗", "bold"), Map.entry("bold", "bold"),
            Map.entry("斜体", "italic"), Map.entry("italic", "italic"),
            Map.entry("下划线", "underlined"), Map.entry("underlined", "underlined"),
            Map.entry("删除线", "strikethrough"), Map.entry("strikethrough", "strikethrough"),
            Map.entry("随机字符", "obfuscated"), Map.entry("obfuscated", "obfuscated"));

    /** Null overrides inherit the line/component defaults; explicit color cancels the rainbow locally. */
    public record Span(String text, Integer color, Boolean rainbow, String font, Map<String, Boolean> styles) {
        public Span { styles = Map.copyOf(styles); }
        public boolean isRainbow(boolean fallback) { return rainbow == null ? fallback : rainbow; }
    }
    /** Inline state at a source offset, independent of the component/line defaults. */
    public record Formatting(Integer color, Boolean rainbow, String font, Map<String, Boolean> styles) {
        public Formatting { styles = Map.copyOf(styles); }
        public boolean style(String key, boolean fallback) { return styles.getOrDefault(key, fallback); }
        public String restore() {
            var tags = new StringBuilder("[重置]");
            if (color != null) tags.append('[').append(hex(color)).append(']');
            if (Boolean.TRUE.equals(rainbow)) tags.append("[动态彩虹]");
            else if (Boolean.FALSE.equals(rainbow) && color == null) tags.append("[关闭彩虹]");
            if (font != null) tags.append("[字体=").append(font).append(']');
            for (String key : STYLE_KEYS) if (styles.containsKey(key)) tags.append(styleTag(key, styles.get(key)));
            return tags.toString();
        }
    }
    private static final class State {
        Integer color;
        Boolean rainbow;
        String font;
        final Map<String, Boolean> styles = new HashMap<>();
        State copy() {
            var next = new State(); next.color = color; next.rainbow = rainbow; next.font = font;
            next.styles.putAll(styles); return next;
        }
        Formatting formatting() { return new Formatting(color, rainbow, font, styles); }
    }
    private TooltipMarkup() {}

    public static List<ColorPreset> palette() { return PALETTE; }
    public static Integer color(String value) {
        String name = value.trim().toLowerCase(Locale.ROOT);
        if (name.matches("#[0-9a-f]{6}")) return Integer.parseInt(name.substring(1), 16);
        return COLORS.get(name);
    }
    public static String hex(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb & 0xffffff); }
    public static String styleTag(String key, boolean enabled) {
        String tag = STYLE_TAGS.get(key);
        if (tag == null) throw new IllegalArgumentException("Unknown text style: " + key);
        return "[" + (enabled ? "" : "/") + tag + "]";
    }

    public static List<Span> parse(String input) {
        var output = new ArrayList<Span>(); var text = new StringBuilder(); var state = new State();
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if (c == '[' && i + 1 < input.length() && input.charAt(i + 1) == '[') {
                text.append('['); i += 2; continue;
            }
            if (c == '[') {
                int end = input.indexOf(']', i + 1);
                if (end > i && end - i <= MAX_TAG_DISTANCE) {
                    var next = state.copy();
                    if (apply(next, input.substring(i + 1, end))) {
                        emit(output, text, state); state = next; i = end + 1; continue;
                    }
                    // Keep unknown tags as a unit; nested bracket-looking text is not reinterpreted.
                    text.append(input, i, end + 1); i = end + 1; continue;
                }
            }
            Token legacy = legacy(input, i);
            if (legacy != null) {
                emit(output, text, state); apply(state, legacy); i += legacy.length(); continue;
            }
            text.append(c); i++;
        }
        emit(output, text, state); return List.copyOf(output);
    }
    public static Formatting formatAt(String input, int position) {
        Objects.checkIndex(position, input.length() + 1);
        var state = new State();
        for (int i = 0; i < position;) {
            Token token = token(input, i);
            if (token != null && i + token.length() <= position) {
                if (!token.field().equals("literal")) apply(state, token);
                i += token.length();
            } else i++;
        }
        return state.formatting();
    }
    /** Recognised tokens are shared by parsing and the editor; escaped/unknown tags stay literal. */
    record Token(int length, String tag, String field, boolean legacyColor) {}
    static Token token(String input, int offset) {
        if (input.charAt(offset) == '[') {
            if (offset + 1 < input.length() && input.charAt(offset + 1) == '[')
                return new Token(2, "", "literal", false);
            int end = input.indexOf(']', offset + 1);
            if (end > offset && end - offset <= MAX_TAG_DISTANCE) {
                String tag = input.substring(offset + 1, end);
                String field = field(tag);
                return new Token(end - offset + 1, tag, field == null ? "literal" : field, false);
            }
        }
        return legacy(input, offset);
    }
    private static Token legacy(String input, int offset) {
        if (input.charAt(offset) != '§' || offset + 1 >= input.length()) return null;
        char code = Character.toLowerCase(input.charAt(offset + 1));
        int color = "0123456789abcdef".indexOf(code);
        if (color >= 0) return new Token(2, PALETTE.get(color).id(), "color", true);
        String key = switch (code) {
            case 'k' -> "obfuscated"; case 'l' -> "bold"; case 'm' -> "strikethrough";
            case 'n' -> "underlined"; case 'o' -> "italic"; case 'r' -> "reset"; default -> null;
        };
        if (key != null) return new Token(2, key, key.equals("reset") ? "all" : key, false);
        if (code == 'x' && offset + 14 <= input.length()) {
            var hex = new StringBuilder("#");
            for (int i = offset + 2; i < offset + 14; i += 2) {
                if (input.charAt(i) != '§' || Character.digit(input.charAt(i + 1), 16) < 0) return null;
                hex.append(input.charAt(i + 1));
            }
            return new Token(14, hex.toString(), "color", true);
        }
        return null;
    }
    private static void apply(State state, Token token) {
        apply(state, token.tag());
        if (token.legacyColor()) for (String key : STYLE_KEYS) state.styles.put(key, false);
    }
    private static String field(String raw) {
        String tag = raw.trim().toLowerCase(Locale.ROOT);
        if (color(tag) != null || Set.of("动态彩虹", "彩虹", "rainbow", "关闭彩虹", "/rainbow", "/动态彩虹", "默认颜色", "inherit_color").contains(tag)) return "color";
        if (tag.equals("重置") || tag.equals("reset")) return "all";
        String style = STYLES.get(tag.startsWith("/") ? tag.substring(1) : tag);
        if (style != null) return style;
        if (tag.equals("默认字体") || tag.equals("inherit_font")) return "font";
        if ((tag.startsWith("字体=") || tag.startsWith("font="))
                && raw.substring(raw.indexOf('=') + 1).trim().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return "font";
        return null;
    }
    private static void emit(List<Span> output, StringBuilder text, State state) {
        if (!text.isEmpty()) {
            output.add(new Span(text.toString(), state.color, state.rainbow, state.font, state.styles));
            text.setLength(0);
        }
    }
    private static boolean apply(State state, String raw) {
        String tag = raw.trim().toLowerCase(Locale.ROOT);
        Integer color = color(tag);
        if (color != null) { state.color = color; state.rainbow = false; return true; }
        if (tag.equals("动态彩虹") || tag.equals("彩虹") || tag.equals("rainbow")) {
            state.rainbow = true; return true;
        }
        if (tag.equals("关闭彩虹") || tag.equals("/rainbow") || tag.equals("/动态彩虹")) {
            state.rainbow = false; return true;
        }
        if (tag.equals("默认颜色") || tag.equals("inherit_color")) { state.color = null; state.rainbow = null; return true; }
        if (tag.equals("默认字体") || tag.equals("inherit_font")) { state.font = null; return true; }
        if (tag.equals("重置") || tag.equals("reset")) {
            state.color = null; state.rainbow = null; state.font = null; state.styles.clear(); return true;
        }
        boolean enabled = !tag.startsWith("/");
        String style = STYLES.get(enabled ? tag : tag.substring(1));
        if (style != null) { state.styles.put(style, enabled); return true; }
        if (tag.startsWith("字体=") || tag.startsWith("font=")) {
            String font = raw.substring(raw.indexOf('=') + 1).trim();
            if (font.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { state.font = font; return true; }
        }
        return false;
    }
    private static Map<String, Integer> colors() {
        var result = new HashMap<String, Integer>();
        for (var color : PALETTE) { result.put(color.id(), color.rgb()); result.put(color.tag(), color.rgb()); }
        return Map.copyOf(result);
    }
}
