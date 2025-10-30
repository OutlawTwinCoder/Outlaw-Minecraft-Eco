package com.outlaweco.shop;

import org.bukkit.Material;
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
    private final List<Material> materials;
    private final List<GeneralShopListing> generalListings;
    private final int page;
    private final int totalPages;

    private ShopInventoryHolder(String templateKey, ShopInventoryType type, String categoryKey, List<ShopOffer> offers,
                                List<ShopCategory> categories, boolean hasBackButton, List<Material> materials,
                                List<GeneralShopListing> generalListings, int page, int totalPages) {
        this.templateKey = templateKey;
        this.type = type;
        this.categoryKey = categoryKey;
        this.offers = offers;
        this.categories = categories;
        this.hasBackButton = hasBackButton;
        this.materials = materials;
        this.generalListings = generalListings;
        this.page = page;
        this.totalPages = totalPages;
    }

    public static ShopInventoryHolder forCategories(String templateKey, List<ShopCategory> categories) {
        return new ShopInventoryHolder(templateKey, ShopInventoryType.CATEGORIES, null,
                List.of(), List.copyOf(categories), false, List.of(), List.of(), 0, 0);
    }

    public static ShopInventoryHolder forOffers(String templateKey, String categoryKey, List<ShopOffer> offers,
                                                boolean hasBackButton) {
        return new ShopInventoryHolder(templateKey, ShopInventoryType.OFFERS, categoryKey,
                List.copyOf(offers), List.of(), hasBackButton, List.of(), List.of(), 0, 0);
    }

    public static ShopInventoryHolder forPriceSelector(List<Material> materials, int page, int totalPages) {
        return new ShopInventoryHolder(null, ShopInventoryType.PRICE_SELECTOR, null,
                List.of(), List.of(), false, List.copyOf(materials), List.of(), page, totalPages);
    }

    public static ShopInventoryHolder forGeneralStore(List<GeneralShopListing> listings, int page, int totalPages) {
        return new ShopInventoryHolder(null, ShopInventoryType.GENERAL_STORE, null,
                List.of(), List.of(), false, List.of(), List.copyOf(listings), page, totalPages);
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

    public List<Material> getMaterials() {
        return materials;
    }

    public List<GeneralShopListing> getGeneralListings() {
        return generalListings;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
