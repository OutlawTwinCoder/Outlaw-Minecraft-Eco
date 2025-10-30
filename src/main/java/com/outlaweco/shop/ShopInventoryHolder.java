package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class ShopInventoryHolder implements InventoryHolder {

    private final String templateKey;
    private final ShopInventoryType type;
    private final String categoryKey;
    private final List<ShopOffer> offers;
    private final List<ShopCategory> categories;
    private final boolean hasBackButton;

    private ShopInventoryHolder(String templateKey, ShopInventoryType type, String categoryKey, List<ShopOffer> offers,
                                List<ShopCategory> categories, boolean hasBackButton) {
        this.templateKey = templateKey;
        this.type = type;
        this.categoryKey = categoryKey;
        this.offers = offers;
        this.categories = categories;
        this.hasBackButton = hasBackButton;
    }

    public static ShopInventoryHolder forCategories(String templateKey, List<ShopCategory> categories) {
        return new ShopInventoryHolder(templateKey, ShopInventoryType.CATEGORIES, null,
                List.of(), List.copyOf(categories), false);
    }

    public static ShopInventoryHolder forOffers(String templateKey, String categoryKey, List<ShopOffer> offers,
                                                boolean hasBackButton) {
        return new ShopInventoryHolder(templateKey, ShopInventoryType.OFFERS, categoryKey,
                List.copyOf(offers), List.of(), hasBackButton);
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public ShopInventoryType getType() {
        return type;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public List<ShopOffer> getOffers() {
        return offers;
    }

    public List<ShopCategory> getCategories() {
        return categories;
    }

    public boolean hasBackButton() {
        return hasBackButton;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
