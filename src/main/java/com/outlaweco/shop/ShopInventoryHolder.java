package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class ShopInventoryHolder implements InventoryHolder {

    public enum ViewType {
        TAB_SELECTION,
        TAB_CONTENT
    }

    private final ViewType viewType;
    private final String templateKey;
    private final List<String> tabKeys;
    private final List<ShopOffer> offers;
    private final String tabKey;
    private final int backSlot;

    public ShopInventoryHolder(String templateKey, List<String> tabKeys) {
        this.viewType = ViewType.TAB_SELECTION;
        this.templateKey = templateKey;
        this.tabKeys = List.copyOf(tabKeys);
        this.offers = List.of();
        this.tabKey = null;
        this.backSlot = -1;
    }

    public ShopInventoryHolder(String templateKey, String tabKey, List<ShopOffer> offers, int backSlot) {
        this.viewType = ViewType.TAB_CONTENT;
        this.templateKey = templateKey;
        this.tabKeys = List.of();
        this.offers = List.copyOf(offers);
        this.tabKey = tabKey;
        this.backSlot = backSlot;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public List<String> getTabKeys() {
        return tabKeys;
    }

    public List<ShopOffer> getOffers() {
        return offers;
    }

    public String getTabKey() {
        return tabKey;
    }

    public int getBackSlot() {
        return backSlot;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
