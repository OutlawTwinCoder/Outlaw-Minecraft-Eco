package com.outlaweco.shop;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ShopTemplate {
    private final String key;
    private String displayNameRaw;
    private final List<ShopOffer> offers;

    public ShopTemplate(String key, String displayNameRaw, List<ShopOffer> offers) {
        this.key = key;
        this.displayNameRaw = displayNameRaw == null ? key : displayNameRaw;
        this.offers = new ArrayList<>(offers);
    }

    public String getKey() {
        return key;
    }

    public String getDisplayNameRaw() {
        return displayNameRaw;
    }

    public void setDisplayNameRaw(String displayNameRaw) {
        this.displayNameRaw = displayNameRaw == null ? key : displayNameRaw;
    }

    public String getDisplayName() {
        return ChatColor.translateAlternateColorCodes('&', displayNameRaw);
    }

    public List<ShopOffer> getOffers() {
        return Collections.unmodifiableList(offers);
    }

    public void addOffer(ShopOffer offer) {
        offers.add(offer);
    }

    public boolean removeOffer(Material material) {
        Iterator<ShopOffer> iterator = offers.iterator();
        while (iterator.hasNext()) {
            ShopOffer offer = iterator.next();
            if (offer.getMaterial() == material) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
