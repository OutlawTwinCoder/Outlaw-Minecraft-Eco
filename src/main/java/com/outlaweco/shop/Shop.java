package com.outlaweco.shop;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.UUID;

public class Shop {

    private final UUID id;
    private final String templateKey;
    private final Location location;
    private Villager villager;

    public Shop(UUID id, String templateKey, Location location) {
        this.id = id;
        this.templateKey = templateKey.toLowerCase();
        this.location = location;
    }

    public UUID getId() {
        return id;
    }

    public String getTemplateKey() {
        return templateKey;
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

    public Villager spawn(ShopTemplate template) {
        Location spawnLocation = location.clone();
        Villager spawned = (Villager) location.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
        spawned.setAI(false);
        spawned.setInvulnerable(true);
        String display = template != null ? template.getDisplayName() : "§eBoutique";
        spawned.setCustomName(display);
        spawned.setCustomNameVisible(false);
        spawned.setVillagerType(Villager.Type.PLAINS);
        this.villager = spawned;
        return spawned;
    }
}
