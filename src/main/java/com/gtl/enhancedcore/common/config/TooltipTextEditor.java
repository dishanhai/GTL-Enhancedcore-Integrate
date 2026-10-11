package com.gtl.enhancedcore.common.config;

import java.util.Objects;

/** Source-offset editing: format a selection while restoring its original trailing state. */
public final class TooltipTextEditor {
    public static final int LIMIT = 8192;
    public record Selection(int cursor, int anchor) {
        public int from() { return Math.min(cursor, anchor); }
        public int to() { return Math.max(cursor, anchor); }
        public boolean selected() { return cursor != anchor; }
    }
    public record Edit(String text, Selection selection) {}
    public static final class LimitExceeded extends IllegalArgumentException {
        private LimitExceeded() { super("Formatted text exceeds " + LIMIT + " characters"); }
    }
    private TooltipTextEditor() {}

    public static Edit apply(String input, Selection selection, String marker) {
        Objects.checkIndex(selection.cursor(), input.length() + 1);
        Objects.checkIndex(selection.anchor(), input.length() + 1);
        var change = TooltipMarkup.token(marker, 0);
        if (change == null || change.length() != marker.length() || change.field().equals("literal"))
            throw new IllegalArgumentException("Expected one formatting marker");
        int from = selection.from(), to = selection.to();
        // Never split an existing tag or a supplementary Unicode character with toolbar markup.
        for (int i = 0; i < input.length();) {
            var token = TooltipMarkup.token(input, i);
            int length = token == null ? Character.charCount(input.codePointAt(i)) : token.length();
            if (from > i && from < i + length) from = selection.selected() ? i : i + length;
            if (to > i && to < i + length) to = i + length;
            i += length;
        }
        if (!selection.selected()) {
            String text = input.substring(0, from) + marker + input.substring(from);
            bounded(text);
            return new Edit(text, new Selection(from + marker.length(), from + marker.length()));
        }
        String selected = reformat(input.substring(from, to), marker, change.field());
        String restore = TooltipMarkup.formatAt(input, to).restore();
        String text = input.substring(0, from) + marker + selected + restore + input.substring(to);
        bounded(text);
        int begin = from + marker.length(), end = begin + selected.length();
        return new Edit(text, selection.cursor() >= selection.anchor() ? new Selection(end, begin) : new Selection(begin, end));
    }
    private static String reformat(String input, String marker, String field) {
        var output = new StringBuilder();
        for (int i = 0; i < input.length();) {
            var token = TooltipMarkup.token(input, i);
            if (token == null) { output.append(input.charAt(i++)); continue; }
            if (token.field().equals("literal")) output.append(input, i, i + token.length());
            else if (field.equals("all")) { /* reset selection: remove its explicit formatting */ }
            else if (token.field().equals(field)) {
                // A legacy color also turns off all five styles; retain that part of its meaning.
                if (token.legacyColor()) for (String key : TooltipMarkup.STYLE_KEYS) output.append(TooltipMarkup.styleTag(key, false));
            } else {
                output.append(input, i, i + token.length());
                if (token.field().equals("all") || token.legacyColor() && TooltipMarkup.STYLE_KEYS.contains(field)) output.append(marker);
            }
            i += token.length();
        }
        return output.toString();
    }
    private static void bounded(String text) {
        if (text.length() > LIMIT) throw new LimitExceeded();
    }
}
