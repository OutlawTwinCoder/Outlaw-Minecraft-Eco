package com.outlaw.economy.shop;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class GeneralShopListing {

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final double price;

    public GeneralShopListing(UUID id, UUID sellerId, String sellerName, ItemStack item, double price) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }
}
