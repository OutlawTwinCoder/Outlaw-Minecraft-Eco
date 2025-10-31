package com.outlaw.economy.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ShopOffer {
    private final ItemStack item;
    private final double buyPrice;
    private final double sellPrice;

    public ShopOffer(ItemStack item, double buyPrice, double sellPrice) {
        this.item = item;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public ItemStack item() {
        return item.clone();
    }

    public Material getMaterial() {
        return item.getType();
    }

    public int getAmount() {
        return item.getAmount();
    }

    public double buyPrice() {
        return buyPrice;
    }

    public double sellPrice() {
        return sellPrice;
    }
}
