package com.gtl.enhancedcore.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtl.enhancedcore.common.config.TooltipOverrides.Line;
import com.gtl.enhancedcore.common.config.TooltipOverrides.Visibility;

/** A transactional line draft; opening/cancelling an editor never changes the source component. */
public final class TooltipLineDraft {
    private final Line original;
    private final JsonObject baseline, component;
    private final String originalText;
    private final boolean flattenedText;
    private String text;
    private Visibility visibility;
    private boolean rainbow;

    public TooltipLineDraft(Line line, String displayText) {
        original = new Line(line.component(), line.visibility(), line.rainbow());
        if (line.component().isJsonObject()) component = line.component().getAsJsonObject().deepCopy();
        else {
            component = new JsonObject(); component.addProperty("text", "");
            var extra = new JsonArray(); extra.add(line.component().deepCopy()); component.add("extra", extra);
        }
        baseline = component.deepCopy();
        flattenedText = !line.component().isJsonObject() || !component.has("text");
        text = originalText = flattenedText ? displayText : TooltipOverrides.string(component, "text", "");
        visibility = line.visibility(); rainbow = line.rainbow();
    }
    public String text() { return text; }
    public void text(String value) { text = value; }
    public String color() { return TooltipOverrides.string(component, "color", ""); }
    public void color(String value) { property("color", value); }
    public String font() { return TooltipOverrides.string(component, "font", ""); }
    public void font(String value) { property("font", value); }
    private void property(String key, String value) {
        if (value.isBlank()) component.remove(key); else component.addProperty(key, value.trim());
    }
    public boolean style(String key) { return TooltipOverrides.bool(component, key, false); }
    public void style(String key, boolean enabled) {
        if (!enabled && !baseline.has(key)) component.remove(key); else component.addProperty(key, enabled);
    }
    public Visibility visibility() { return visibility; }
    public void visibility(Visibility value) { visibility = value; }
    public boolean rainbow() { return rainbow; }
    public void rainbow(boolean value) { rainbow = value; }
    public void resetStyle() {
        component.remove("color"); component.remove("font");
        TooltipMarkup.STYLE_KEYS.forEach(component::remove); rainbow = false;
    }
    public boolean dirty() {
        return !text.equals(originalText) || !component.equals(baseline)
                || visibility != original.visibility() || rainbow != original.rainbow();
    }
    public Line line() {
        if (!dirty()) return new Line(original.component(), original.visibility(), original.rainbow());
        var result = component.deepCopy();
        if (!text.equals(originalText)) {
            result.remove("translate"); result.remove("fallback"); result.remove("with");
            if (flattenedText) result.remove("extra");
            result.addProperty("text", text);
        }
        TooltipOverrides.validateComponent(result, 0);
        return new Line(result, visibility, rainbow);
    }
}
