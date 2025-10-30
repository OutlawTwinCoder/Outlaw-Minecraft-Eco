package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class ShopInventoryHolder implements InventoryHolder {

    private final String templateKey;
    private final List<ShopOffer> offers;

    public ShopInventoryHolder(String templateKey, List<ShopOffer> offers) {
        this.templateKey = templateKey;
        this.offers = offers;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public List<ShopOffer> getOffers() {
        return offers;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
