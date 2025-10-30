package com.outlaweco.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShopTab {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private Material iconMaterial;
    private final List<ShopOffer> offers = new ArrayList<>();

    public ShopTab(String key) {
        this.key = key.toLowerCase();
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.rawDisplayName = displayName;
        if (displayName == null || displayName.isBlank()) {
            this.displayName = ChatColor.YELLOW + key.substring(0, 1).toUpperCase() + key.substring(1);
        } else {
            this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        }
    }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(Material iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public List<ShopOffer> getOffers() {
        return Collections.unmodifiableList(offers);
    }

    public void setOffers(List<ShopOffer> offers) {
        this.offers.clear();
        if (offers != null) {
            this.offers.addAll(offers);
        }
    }

    public void addOffer(ShopOffer offer) {
        this.offers.add(offer);
    }

    public boolean removeOffer(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return false;
        }
        return offers.removeIf(offer -> offer.getMaterial() == material);
    }

    public ItemStack createIconItem() {
        Material material = iconMaterial;
        if (material == null) {
            if (!offers.isEmpty()) {
                material = offers.get(0).getMaterial();
            } else {
                material = Material.CHEST;
            }
        }
        return new ItemStack(material);
    }
}
