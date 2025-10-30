package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShopInventoryHolder implements InventoryHolder {

    private final String templateKey;
    private final String categoryKey;
    private final String categoryDisplayName;
    private final int categoryIndex;
    private final int totalCategories;
    private final Map<Integer, ShopOffer> offerSlots = new HashMap<>();
    private final Map<Integer, Integer> categoryTargets = new HashMap<>();
    private final int page;
    private final int totalPages;

    public ShopInventoryHolder(String templateKey,
                               String categoryKey,
                               String categoryDisplayName,
                               int categoryIndex,
                               int totalCategories,
                               int page,
                               int totalPages) {
        this.templateKey = templateKey;
        this.categoryKey = categoryKey;
        this.categoryDisplayName = categoryDisplayName;
        this.categoryIndex = categoryIndex;
        this.totalCategories = totalCategories;
        this.page = page;
        this.totalPages = totalPages;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public String getCategoryDisplayName() {
        return categoryDisplayName;
    }

    public int getCategoryIndex() {
        return categoryIndex;
    }

    public int getTotalCategories() {
        return totalCategories;
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

    public void registerCategorySlot(int slot, int categoryIndex) {
        categoryTargets.put(slot, categoryIndex);
    }

    public Optional<Integer> getCategoryTarget(int slot) {
        return Optional.ofNullable(categoryTargets.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
