package com.outlaweco.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ShopOffer {
    private final Material material;
    private final int amount;
    private final double buyPrice;
    private final double sellPrice;

    public ShopOffer(Material material, int amount, double buyPrice, double sellPrice) {
        this.material = material;
        this.amount = Math.max(1, amount);
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public ItemStack createItemStack() {
        return new ItemStack(material, amount);
    }
}
