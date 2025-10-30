package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ShopInventoryHolder implements InventoryHolder {

    public enum ViewType {
        TAB_SELECTION,
        TAB_CONTENT
    }

    private final ViewType viewType;
    private final String templateKey;
    private final Map<Integer, String> tabSlots;
    private final Map<Integer, ShopOffer> offerSlots;
    private final String tabKey;
    private final int backSlot;

    private ShopInventoryHolder(
            ViewType viewType,
            String templateKey,
            Map<Integer, String> tabSlots,
            Map<Integer, ShopOffer> offerSlots,
            String tabKey,
            int backSlot
    ) {
        this.viewType = viewType;
        this.templateKey = templateKey;
        this.tabSlots = tabSlots;
        this.offerSlots = offerSlots;
        this.tabKey = tabKey;
        this.backSlot = backSlot;
    }

    public static ShopInventoryHolder forTabSelection(String templateKey, Map<Integer, String> tabSlots) {
        Map<Integer, String> safeSlots = Collections.unmodifiableMap(new LinkedHashMap<>(tabSlots));
        return new ShopInventoryHolder(ViewType.TAB_SELECTION, templateKey, safeSlots, Collections.emptyMap(), null, -1);
    }

    public static ShopInventoryHolder forTabContent(String templateKey, String tabKey, Map<Integer, ShopOffer> offerSlots, int backSlot) {
        Map<Integer, ShopOffer> safeSlots = Collections.unmodifiableMap(new LinkedHashMap<>(offerSlots));
        return new ShopInventoryHolder(ViewType.TAB_CONTENT, templateKey, Collections.emptyMap(), safeSlots, tabKey, backSlot);
    }

    public ViewType getViewType() {
        return viewType;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public Optional<String> getTabKey(int slot) {
        return Optional.ofNullable(tabSlots.get(slot));
    }

    public Optional<ShopOffer> getOffer(int slot) {
        return Optional.ofNullable(offerSlots.get(slot));
    }

    public String getActiveTabKey() {
        return tabKey;
    }

    public boolean isBackSlot(int slot) {
        return backSlot != -1 && backSlot == slot;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
