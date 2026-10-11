package com.gtl.enhancedcore.common.config;

import java.util.OptionalInt;

/** Incomplete picker input remains a draft; only valid opaque RGB colors can be applied. */
public final class TooltipColorInput {
    private TooltipColorInput() {}
    public static OptionalInt hex(String text) {
        String normalized = text.startsWith("#") ? text : "#" + text;
        return normalized.matches("#[0-9a-fA-F]{6}")
                ? OptionalInt.of(Integer.parseInt(normalized.substring(1), 16)) : OptionalInt.empty();
    }
    public static OptionalInt rgb(String... channels) {
        if (channels.length != 3) return OptionalInt.empty();
        int color = 0;
        for (String channel : channels) {
            if (!channel.matches("[0-9]{1,3}")) return OptionalInt.empty();
            int value = Integer.parseInt(channel);
            if (value > 255) return OptionalInt.empty();
            color = color * 256 + value;
        }
        return OptionalInt.of(color);
    }
}
