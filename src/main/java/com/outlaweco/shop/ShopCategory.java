package com.outlaweco.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

public class ShopCategory {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private Material iconMaterial;
    private final List<ShopOffer> offers = new ArrayList<>();

    public ShopCategory(String key, String displayName) {
        this.key = key.toLowerCase();
        setDisplayName(displayName);
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
            this.displayName = ChatColor.YELLOW + key;
        } else {
            this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        }
    }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public void setIconMaterial(Material material) {
        this.iconMaterial = material;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public boolean hasCustomIcon() {
        return iconMaterial != null;
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

    public int updateBuyPrice(Material material, double price) {
        int updated = 0;
        for (int i = 0; i < offers.size(); i++) {
            ShopOffer offer = offers.get(i);
            if (offer.getMaterial() != material) {
                continue;
            }
            ItemStack item = offer.item();
            offers.set(i, new ShopOffer(item, price, offer.sellPrice()));
            updated++;
        }
        return updated;
    }

    public OptionalDouble findBuyPrice(Material material) {
        for (ShopOffer offer : offers) {
            if (offer.getMaterial() == material) {
                return OptionalDouble.of(offer.buyPrice());
            }
        }
        return OptionalDouble.empty();
    }

    public ItemStack createIconItem() {
        ItemStack base;
        if (iconMaterial != null) {
            base = new ItemStack(iconMaterial);
        } else if (!offers.isEmpty()) {
            base = offers.get(0).item();
        } else {
            base = new ItemStack(Material.BARRIER);
        }
        base.setAmount(1);
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of("§7Clique pour afficher les articles."));
            base.setItemMeta(meta);
        }
        return base;
    }
}

