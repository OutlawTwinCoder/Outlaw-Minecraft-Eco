package com.outlaweco.shop;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ShopTemplate {

    private final String key;
    private String displayName;
    private String rawDisplayName;
    private final Map<String, ShopTab> tabs = new LinkedHashMap<>();

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

    public List<ShopTab> getTabs() {
        return List.copyOf(tabs.values());
    }

    public List<String> getTabKeys() {
        return new ArrayList<>(tabs.keySet());
    }

    public void clearTabs() {
        tabs.clear();
    }

    public void addTab(ShopTab tab) {
        if (tab == null) {
            return;
        }
        tabs.put(tab.getKey(), tab);
    }

    public void setTabs(List<ShopTab> newTabs) {
        clearTabs();
        if (newTabs != null) {
            newTabs.forEach(this::addTab);
        }
    }

    public Optional<ShopTab> getTab(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tabs.get(key.toLowerCase()));
    }

    public Optional<ShopTab> getDefaultTab() {
        return tabs.values().stream().findFirst();
    }

    public List<ShopOffer> getOffers() {
        List<ShopOffer> offers = new ArrayList<>();
        for (ShopTab tab : tabs.values()) {
            offers.addAll(tab.getOffers());
        }
        return Collections.unmodifiableList(offers);
    }

    public void addOffer(ShopOffer offer) {
        getDefaultTab().ifPresent(tab -> tab.addOffer(offer));
    }

    public boolean removeOffer(String materialName) {
        boolean removed = false;
        for (ShopTab tab : tabs.values()) {
            removed |= tab.removeOffer(materialName);
        }
        return removed;
    }
}
