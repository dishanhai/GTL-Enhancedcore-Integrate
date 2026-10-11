package com.gtl.enhancedcore.common.config;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;

/** Registry-independent, versioned authoring format. Built-in tooltips remain the default. */
public final class TooltipOverrides {
    public static final int MAX_CHARACTERS = 2_000_000;
    public static final int MAX_LINES = 256;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final Set<String> FIELDS = Set.of("text", "translate", "fallback", "with", "extra", "color", "font",
            "bold", "italic", "underlined", "strikethrough", "obfuscated");

    public enum Mode { DEFAULT, APPEND, PREPEND, REPLACE }
    public enum Visibility { ALWAYS, SHIFT, NO_SHIFT }
    public record Line(JsonElement component, Visibility visibility, boolean rainbow) {
        public Line { component = component.deepCopy(); }
        public boolean visible(boolean shift) {
            return visibility == Visibility.ALWAYS || (visibility == Visibility.SHIFT) == shift;
        }
        public static Line text(String text) {
            var object = new JsonObject(); object.addProperty("text", text);
            return new Line(object, Visibility.ALWAYS, false);
        }
    }
    public record Entry(Mode mode, List<Line> lines) {
        public Entry { lines = List.copyOf(lines); }
        public static Entry defaults() { return new Entry(Mode.DEFAULT, List.of()); }
    }
    private final SortedMap<String, Entry> items;

    public TooltipOverrides(Map<String, Entry> items) { this.items = Collections.unmodifiableSortedMap(new TreeMap<>(items)); }
    public Map<String, Entry> items() { return items; }
    public Entry entry(String id) { return items.getOrDefault(id, Entry.defaults()); }
    public TooltipOverrides with(String id, Entry entry) {
        var copy = new TreeMap<>(items); copy.put(id, entry); return new TooltipOverrides(copy);
    }
    public TooltipOverrides include(Collection<String> ids) {
        var copy = new TreeMap<>(items); ids.forEach(id -> copy.putIfAbsent(id, Entry.defaults()));
        return new TooltipOverrides(copy);
    }

    public static TooltipOverrides parse(String text) {
        if (text.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Tooltip file exceeds 2,000,000 characters");
        var root = JsonParser.parseString(text).getAsJsonObject();
        if (!root.has("schema") || !root.get("schema").getAsString().equals("1"))
            throw new IllegalArgumentException("Expected schema 1");
        var source = root.getAsJsonObject("items");
        if (source == null || source.size() > 4096) throw new IllegalArgumentException("Expected items object (at most 4096 items)");
        var result = new TreeMap<String, Entry>();
        source.entrySet().forEach(pair -> {
            if (!ID.matcher(pair.getKey()).matches() || !pair.getKey().startsWith("gtl_enhancedcore:"))
                throw new IllegalArgumentException("Expected a GTL-Enhancedcore item ID: " + pair.getKey());
            result.put(pair.getKey(), parseEntry(pair.getValue().getAsJsonObject()));
        });
        return new TooltipOverrides(result);
    }

    public static Entry parseEntry(String text) { return parseEntry(JsonParser.parseString(text).getAsJsonObject()); }
    private static Entry parseEntry(JsonObject object) {
        Mode mode = Mode.valueOf(string(object, "mode", "default").toUpperCase(Locale.ROOT));
        var lines = new ArrayList<Line>();
        if (object.has("lines")) {
            var array = object.getAsJsonArray("lines");
            if (array.size() > MAX_LINES) throw new IllegalArgumentException("At most 256 lines per item");
            for (var element : array) {
                var row = element.getAsJsonObject();
                JsonElement component = row.get("component");
                validateComponent(component, 0);
                Visibility visibility = Visibility.valueOf(string(row, "visibility", "always").toUpperCase(Locale.ROOT));
                lines.add(new Line(component, visibility, bool(row, "rainbow", false)));
            }
        }
        return new Entry(mode, lines);
    }

    public static void validateComponent(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth > 24) throw new IllegalArgumentException("Invalid or deeply nested text component");
        if (value.isJsonPrimitive()) {
            if (!value.getAsJsonPrimitive().isString() || value.getAsString().length() > 8192)
                throw new IllegalArgumentException("Text must be a string of at most 8192 characters");
            return;
        }
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().isEmpty() || value.getAsJsonArray().size() > 256)
                throw new IllegalArgumentException("Expected 1–256 text segments");
            for (var child : value.getAsJsonArray()) validateComponent(child, depth + 1);
            return;
        }
        var object = value.getAsJsonObject();
        if (!object.has("text") && !object.has("translate")) throw new IllegalArgumentException("Expected text or translate");
        for (String key : object.keySet()) {
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("Unsupported text field: " + key);
            switch (key) {
                case "extra", "with" -> {
                    var array = object.getAsJsonArray(key);
                    if (array.size() > 256 || (key.equals("extra") && array.isEmpty()))
                        throw new IllegalArgumentException("Invalid text segment count");
                    for (var child : array) {
                        if (key.equals("with") && child.isJsonPrimitive() && !child.getAsJsonPrimitive().isString()) continue;
                        validateComponent(child, depth + 1);
                    }
                }
                case "bold", "italic", "underlined", "strikethrough", "obfuscated" -> bool(object, key, false);
                default -> {
                    String raw = string(object, key, "");
                    if (raw.length() > 8192) throw new IllegalArgumentException("Text field is too long");
                    if (key.equals("font") && !ID.matcher(raw).matches()) throw new IllegalArgumentException("Invalid font ID: " + raw);
                    if (key.equals("color") && !COLORS.contains(raw) && !raw.matches("#[0-9a-fA-F]{6}"))
                        throw new IllegalArgumentException("Color must be a Minecraft color name or #RRGGBB");
                }
            }
        }
    }

    public static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        var element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be text");
        return element.getAsString();
    }
    public static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        var element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be true/false");
        return element.getAsBoolean();
    }

    public static JsonObject toJson(Entry entry) {
        var object = new JsonObject(); object.addProperty("mode", entry.mode().name().toLowerCase(Locale.ROOT));
        var lines = new JsonArray();
        for (Line line : entry.lines()) {
            var row = new JsonObject(); row.add("component", line.component().deepCopy());
            row.addProperty("visibility", line.visibility().name().toLowerCase(Locale.ROOT));
            row.addProperty("rainbow", line.rainbow()); lines.add(row);
        }
        object.add("lines", lines); return object;
    }
    public static String serialize(Entry entry) { return JSON.toJson(toJson(entry)); }
    public String serialize() {
        var root = new JsonObject(); root.addProperty("schema", 1);
        var map = new JsonObject(); items.forEach((id, entry) -> map.add(id, toJson(entry)));
        root.add("items", map); return JSON.toJson(root) + "\n";
    }

    /** The first tooltip row is the item name. Default and append modes preserve existing component identities. */
    public static <T> List<T> compose(List<T> original, List<T> custom, Mode mode) {
        if (mode == Mode.DEFAULT) return new ArrayList<>(original);
        var result = new ArrayList<T>();
        if (!original.isEmpty()) result.add(original.getFirst());
        if (mode == Mode.PREPEND || mode == Mode.REPLACE) result.addAll(custom);
        if (mode != Mode.REPLACE && original.size() > 1) result.addAll(original.subList(1, original.size()));
        if (mode == Mode.APPEND) result.addAll(custom);
        return result;
    }
}
