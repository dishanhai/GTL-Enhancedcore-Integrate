package com.gtl.enhancedcore.common.config;

import java.util.ArrayList;
import java.util.List;

/** The vanilla GUI minimum is 320x240; a narrow GUI wraps the formatting toolbar. */
public record TooltipEditorLayout(List<Rect> toolbar, int hintY, int textY, int textHeight,
                                  int controlsY, int previewY, int saveY) {
    public record Rect(int x, int y, int width, int height) {}
    public TooltipEditorLayout { toolbar = List.copyOf(toolbar); }
    public static TooltipEditorLayout create(int bodyWidth, int height) {
        int[] widths = {96, 22, 22, 22, 22, 22, 104, 52, 22};
        var slots = new ArrayList<Rect>();
        int x = 0, y = 32;
        for (int w : widths) {
            if (x != 0 && x + w > bodyWidth) { x = 0; y += 24; }
            slots.add(new Rect(x, y, w, 20)); x += w + 3;
        }
        int hintY = y + 24, textY = hintY + 14;
        return new TooltipEditorLayout(slots, hintY, textY, Math.max(32, height - textY - 109),
                height - 91, height - 67, height - 27);
    }
}
