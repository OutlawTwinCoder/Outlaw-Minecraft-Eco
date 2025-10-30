package com.outlaweco.shop;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShopTemplate {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private final List<ShopOffer> offers = new ArrayList<>();

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
}
