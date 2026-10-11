package com.gtl.enhancedcore.common.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AEItemConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEItemConfigSlotWidget;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** Reuses the original AE slot objects, parents, indices and synchronization across ten fixed pages. */
public final class PagedTagFilterStockBusWidget extends WidgetGroup {
    private static final int WIDTH = TagFilterStockBusPaging.COLUMNS * 18;
    private static final int GRID_HEIGHT = 74;
    private static final int PAGE_UPDATE = 1701;
    private static final String PREFIX = "gtl_enhancedcore.gui.tag_stock_bus.";

    private final AEItemConfigWidget inventory;
    private final List<AEItemConfigSlotWidget> slots;
    private final ButtonWidget previous, next;
    private int page;

    public PagedTagFilterStockBusWidget(int x, int y, AEItemConfigWidget inventory) {
        super(x, y, WIDTH, 96);
        this.inventory = inventory;
        slots = inventory.widgets.stream().filter(AEItemConfigSlotWidget.class::isInstance)
                .map(AEItemConfigSlotWidget.class::cast).toList();
        if (slots.size() != TagFilterStockBusPaging.SLOT_COUNT) {
            throw new IllegalArgumentException("Tag stocking bus needs " + TagFilterStockBusPaging.SLOT_COUNT
                    + " AE slots, got " + slots.size());
        }

        inventory.setSelfPosition(0, 0);
        inventory.setSize(WIDTH, GRID_HEIGHT);
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            var position = TagFilterStockBusPaging.position(i);
            slot.setSelfPosition(position.column() * 18, position.row() * 38);
        }
        addWidget(inventory);

        previous = new ButtonWidget(30, 78, 18, 18,
                new GuiTextureGroup(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_LEFT),
                click -> changePage(-1, click));
        previous.setHoverTooltips(PREFIX + "previous");
        next = new ButtonWidget(96, 78, 18, 18,
                new GuiTextureGroup(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_RIGHT),
                click -> changePage(1, click));
        next.setHoverTooltips(PREFIX + "next");
        addWidget(previous);
        addWidget(next);
        addWidget(new LabelWidget(56, 83, () -> (page + 1) + " / " + TagFilterStockBusPaging.PAGE_COUNT)
                .setClientSideWidget().setHoverTooltips(PREFIX + "capacity"));
        setPage(0);
    }

    private void changePage(int direction, ClickData click) {
        setPage(page + direction);
        if (!click.isRemote) writeUpdateInfo(PAGE_UPDATE, buffer -> buffer.writeVarInt(page));
    }

    private void setPage(int requested) {
        page = TagFilterStockBusPaging.clampPage(requested);
        inventory.disableAmount();
        for (int i = 0; i < slots.size(); i++) {
            boolean visible = TagFilterStockBusPaging.position(i).page() == page;
            slots.get(i).setVisible(visible).setActive(visible);
            slots.get(i).setSelect(false);
        }
        previous.setActive(page > 0);
        next.setActive(page < TagFilterStockBusPaging.PAGE_COUNT - 1);
    }

    @Override public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == PAGE_UPDATE) setPage(buffer.readVarInt());
        else super.readUpdateInfo(id, buffer);
    }
}
