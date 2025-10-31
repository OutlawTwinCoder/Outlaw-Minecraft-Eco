package com.outlaw.economy.shop;

import org.bukkit.ChatColor;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopTemplate {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private final List<ShopOffer> offers = new ArrayList<>();
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopTemplate(String key, String displayName) {
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
            this.displayName = ChatColor.YELLOW + "Boutique " + key;
        } else {
            this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        }
    }

    public String getRawDisplayName() {
        return rawDisplayName;
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
        offers.add(offer);
    }

    public boolean removeOffer(String materialName) {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(materialName);
        if (material == null) {
            return false;
        }
        return offers.removeIf(offer -> offer.getMaterial() == material);
    }

    public void setCategories(List<ShopCategory> categories) {
        this.categories.clear();
        if (categories != null) {
            for (ShopCategory category : categories) {
                this.categories.put(category.getKey().toLowerCase(Locale.ROOT), category);
            }
        }
    }

    public boolean hasCategories() {
        return !categories.isEmpty();
    }

    public List<ShopCategory> getCategories() {
        return List.copyOf(categories.values());
    }

    public ShopCategory getCategory(String key) {
        if (key == null) {
            return null;
        }
        return categories.get(key.toLowerCase(Locale.ROOT));
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
        for (ShopCategory category : categories.values()) {
            updated += category.updateBuyPrice(material, price);
        }
        return updated;
    }

    public OptionalDouble findBuyPrice(Material material) {
        for (ShopOffer offer : offers) {
            if (offer.getMaterial() == material) {
                return OptionalDouble.of(offer.buyPrice());
            }
        }
        for (ShopCategory category : categories.values()) {
            OptionalDouble found = category.findBuyPrice(material);
            if (found.isPresent()) {
                return found;
            }
        }
        return OptionalDouble.empty();
    }
}
