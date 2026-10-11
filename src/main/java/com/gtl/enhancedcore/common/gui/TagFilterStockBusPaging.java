package com.gtl.enhancedcore.common.gui;

import java.util.Objects;

/** The tag stocking bus keeps all slots active; pages only choose which slots are displayed. */
public final class TagFilterStockBusPaging {
    public static final int COLUMNS = 8;
    public static final int ROWS = 2;
    public static final int PAGE_COUNT = 10;
    public static final int SLOTS_PER_PAGE = COLUMNS * ROWS;
    public static final int SLOT_COUNT = SLOTS_PER_PAGE * PAGE_COUNT;

    private TagFilterStockBusPaging() {}

    public record SlotPosition(int page, int column, int row) {}

    public static SlotPosition position(int slot) {
        Objects.checkIndex(slot, SLOT_COUNT);
        int local = slot % SLOTS_PER_PAGE;
        return new SlotPosition(slot / SLOTS_PER_PAGE, local % COLUMNS, local / COLUMNS);
    }

    public static int clampPage(int page) {
        return Math.max(0, Math.min(PAGE_COUNT - 1, page));
    }
}
