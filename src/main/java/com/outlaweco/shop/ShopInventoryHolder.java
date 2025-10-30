package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopInventoryHolder implements InventoryHolder {

    private final String templateKey;

    public ShopInventoryHolder(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
