package com.outlaweco.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopInventoryHolder implements InventoryHolder {

    private final Shop shop;

    public ShopInventoryHolder(Shop shop) {
        this.shop = shop;
    }

    public Shop getShop() {
        return shop;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
