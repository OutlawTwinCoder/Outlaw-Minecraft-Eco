package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShopInventoryHolder implements InventoryHolder {

    private final String templateKey;
    private final Map<Integer, ShopOffer> offerSlots = new HashMap<>();
    private final Map<Integer, String> tabTargets = new HashMap<>();
    private final int page;
    private final int totalPages;

    public ShopInventoryHolder(String templateKey, int page, int totalPages) {
        this.templateKey = templateKey;
        this.page = page;
        this.totalPages = totalPages;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void registerOfferSlot(int slot, ShopOffer offer) {
        offerSlots.put(slot, offer);
    }

    public ShopOffer getOffer(int slot) {
        return offerSlots.get(slot);
    }

    public void registerTabSlot(int slot, String template) {
        tabTargets.put(slot, template);
    }

    public Optional<String> getTabTarget(int slot) {
        return Optional.ofNullable(tabTargets.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
