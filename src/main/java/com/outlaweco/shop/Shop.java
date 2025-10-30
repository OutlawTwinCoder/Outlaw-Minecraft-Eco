package com.outlaweco.shop;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

public class Shop {

    private final UUID id;
    private final ShopType type;
    private final Location location;
    private Villager villager;
    private Inventory inventory;
    private List<ShopOffer> offers;

    public Shop(UUID id, ShopType type, Location location) {
        this.id = id;
        this.type = type;
        this.location = location;
    }

    public UUID getId() {
        return id;
    }

    public ShopType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public Villager getVillager() {
        return villager;
    }

    public void setVillager(Villager villager) {
        this.villager = villager;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public List<ShopOffer> getOffers() {
        return offers;
    }

    public void setOffers(List<ShopOffer> offers) {
        this.offers = offers;
    }

    public Villager spawn() {
        Location spawnLocation = location.clone();
        Villager spawned = (Villager) location.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
        spawned.setAI(false);
        spawned.setInvulnerable(true);
        spawned.setCustomName("§eBoutique " + type.name());
        spawned.setCustomNameVisible(true);
        spawned.setVillagerType(Villager.Type.PLAINS);
        this.villager = spawned;
        return spawned;
    }
}
