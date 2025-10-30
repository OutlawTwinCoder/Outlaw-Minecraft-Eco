package com.outlaweco.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ShopCategory {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private final List<ShopOffer> offers = new ArrayList<>();
    private Material icon;
    private String rawIconName;

    public ShopCategory(String key, String displayName) {
        this.key = key.toLowerCase(Locale.ROOT);
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
            this.displayName = ChatColor.YELLOW + formatKey(key);
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
        ensureIcon();
    }

    public void addOffer(ShopOffer offer) {
        offers.add(offer);
        ensureIcon();
    }

    public boolean removeOffer(Material material) {
        boolean removed = offers.removeIf(offer -> offer.getMaterial() == material);
        if (removed) {
            ensureIcon();
        }
        return removed;
    }

    public void setIcon(Material icon, String rawIconName) {
        this.icon = icon;
        this.rawIconName = rawIconName;
    }

    public Material getIcon() {
        ensureIcon();
        return icon != null ? icon : Material.CHEST;
    }

    public String getRawIconName() {
        return rawIconName;
    }

    public boolean isDefaultCategory() {
        return "default".equals(key) && (rawDisplayName == null || rawDisplayName.isBlank()) && (rawIconName == null || rawIconName.isBlank());
    }

    private void ensureIcon() {
        if (icon == null) {
            if (!offers.isEmpty()) {
                icon = offers.get(0).getMaterial();
            }
        } else if (rawIconName == null && offers.stream().noneMatch(offer -> offer.getMaterial() == icon)) {
            icon = !offers.isEmpty() ? offers.get(0).getMaterial() : null;
        }
        if (icon == null) {
            icon = Material.CHEST;
        }
    }

    private String formatKey(String key) {
        if (key.isEmpty()) {
            return key;
        }
        String[] parts = key.replace('-', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
