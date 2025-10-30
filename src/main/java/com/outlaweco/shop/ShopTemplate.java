package com.outlaweco.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ShopTemplate {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private final List<ShopCategory> categories = new ArrayList<>();
    private final Map<String, ShopCategory> categoryLookup = new LinkedHashMap<>();
    private Material icon;
    private String rawIconName;

    public ShopTemplate(String key, String displayName) {
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
            this.displayName = ChatColor.YELLOW + "Boutique " + key;
        } else {
            this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        }
    }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public void setCategories(List<ShopCategory> categories) {
        this.categories.clear();
        this.categoryLookup.clear();
        if (categories != null) {
            for (ShopCategory category : categories) {
                addCategoryInternal(category);
            }
        }
        ensureDefaultCategory();
        ensureIcon();
    }

    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public ShopCategory getCategory(int index) {
        if (index < 0 || index >= categories.size()) {
            return null;
        }
        return categories.get(index);
    }

    public ShopCategory getCategory(String categoryKey) {
        if (categoryKey == null) {
            return null;
        }
        return categoryLookup.get(categoryKey.toLowerCase(Locale.ROOT));
    }

    public List<ShopOffer> getOffersForCategory(String categoryKey) {
        ShopCategory category = getCategory(categoryKey);
        return category != null ? category.getOffers() : List.of();
    }

    public void setOffers(List<ShopOffer> offers) {
        ShopCategory category = new ShopCategory("default", null);
        category.setOffers(offers);
        setCategories(List.of(category));
    }

    public void addOffer(ShopOffer offer) {
        ShopCategory category = ensureDefaultCategory();
        category.addOffer(offer);
        ensureIcon();
    }

    public boolean removeOffer(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return false;
        }
        boolean removed = false;
        for (ShopCategory category : categories) {
            removed |= category.removeOffer(material);
        }
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

    public boolean isSingleDefaultCategory() {
        return categories.size() == 1 && categories.get(0).isDefaultCategory();
    }

    private ShopCategory ensureDefaultCategory() {
        if (categories.isEmpty()) {
            ShopCategory category = new ShopCategory("default", null);
            addCategoryInternal(category);
        }
        return categories.get(0);
    }

    private void addCategoryInternal(ShopCategory category) {
        if (category == null) {
            return;
        }
        ShopCategory existing = categoryLookup.put(category.getKey(), category);
        if (existing != null) {
            categories.remove(existing);
        }
        categories.add(category);
    }

    private void ensureIcon() {
        if (icon == null || (rawIconName == null && categories.stream().noneMatch(category -> category.getIcon() == icon))) {
            icon = categories.stream()
                    .map(ShopCategory::getIcon)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(icon);
        }
        if (icon == null) {
            icon = Material.CHEST;
        }
    }
}
