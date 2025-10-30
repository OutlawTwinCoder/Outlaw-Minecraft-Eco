package com.outlaweco.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum ShopType {
    TOOLS("tools") {
        @Override
        public List<ShopOffer> createOffers() {
            List<ShopOffer> offers = new ArrayList<>();
            offers.add(new ShopOffer(new ItemStack(Material.IRON_PICKAXE), 75, 45));
            offers.add(new ShopOffer(new ItemStack(Material.IRON_AXE), 70, 42));
            offers.add(new ShopOffer(new ItemStack(Material.IRON_SHOVEL), 45, 27));
            offers.add(new ShopOffer(new ItemStack(Material.DIAMOND_PICKAXE), 450, 270));
            offers.add(new ShopOffer(new ItemStack(Material.SHIELD), 90, 54));
            offers.add(new ShopOffer(new ItemStack(Material.CROSSBOW), 125, 75));
            return offers;
        }
    },
    BLOCKS("blocks") {
        @Override
        public List<ShopOffer> createOffers() {
            List<ShopOffer> offers = new ArrayList<>();
            offers.add(new ShopOffer(new ItemStack(Material.WHITE_CONCRETE, 16), 40, 24));
            offers.add(new ShopOffer(new ItemStack(Material.RED_CONCRETE, 16), 40, 24));
            offers.add(new ShopOffer(new ItemStack(Material.BLUE_CONCRETE, 16), 40, 24));
            offers.add(new ShopOffer(new ItemStack(Material.GLASS, 32), 30, 18));
            offers.add(new ShopOffer(new ItemStack(Material.SEA_LANTERN, 8), 55, 33));
            offers.add(new ShopOffer(new ItemStack(Material.BRICKS, 32), 45, 27));
            return offers;
        }
    },
    FOOD("food") {
        @Override
        public List<ShopOffer> createOffers() {
            List<ShopOffer> offers = new ArrayList<>();
            offers.add(new ShopOffer(new ItemStack(Material.COOKED_BEEF, 16), 32, 20));
            offers.add(new ShopOffer(new ItemStack(Material.BREAD, 16), 18, 11));
            offers.add(new ShopOffer(new ItemStack(Material.GOLDEN_CARROT, 16), 60, 36));
            offers.add(new ShopOffer(new ItemStack(Material.CAKE, 1), 25, 15));
            offers.add(new ShopOffer(new ItemStack(Material.MUSHROOM_STEW, 4), 20, 12));
            offers.add(new ShopOffer(new ItemStack(Material.PUMPKIN_PIE, 8), 28, 17));
            return offers;
        }
    };

    private final String key;

    ShopType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public abstract List<ShopOffer> createOffers();

    public static ShopType fromString(String input) {
        for (ShopType type : values()) {
            if (type.name().equalsIgnoreCase(input) || type.key.equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (ShopType type : values()) {
            keys.add(type.getKey());
        }
        return Collections.unmodifiableList(keys);
    }
}
