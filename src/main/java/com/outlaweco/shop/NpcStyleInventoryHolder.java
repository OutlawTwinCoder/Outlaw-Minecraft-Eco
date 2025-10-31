package com.outlaweco.shop;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class NpcStyleInventoryHolder implements InventoryHolder {

    public enum Mode {
        TYPE_SELECTION,
        PROFESSION_SELECTION
    }

    private final Mode mode;
    private final List<Villager.Type> villagerTypes;
    private final List<Villager.Profession> villagerProfessions;
    private final Villager.Type selectedType;

    private NpcStyleInventoryHolder(Mode mode, List<Villager.Type> villagerTypes,
                                    List<Villager.Profession> villagerProfessions,
                                    Villager.Type selectedType) {
        this.mode = mode;
        this.villagerTypes = villagerTypes;
        this.villagerProfessions = villagerProfessions;
        this.selectedType = selectedType;
    }

    public static NpcStyleInventoryHolder forTypes(List<Villager.Type> types) {
        return new NpcStyleInventoryHolder(Mode.TYPE_SELECTION, List.copyOf(types), List.of(), null);
    }

    public static NpcStyleInventoryHolder forProfessions(Villager.Type selectedType, List<Villager.Profession> professions) {
        return new NpcStyleInventoryHolder(Mode.PROFESSION_SELECTION, List.of(), List.copyOf(professions), selectedType);
    }

    public Mode getMode() {
        return mode;
    }

    public List<Villager.Type> getVillagerTypes() {
        return villagerTypes;
    }

    public List<Villager.Profession> getVillagerProfessions() {
        return villagerProfessions;
    }

    public Villager.Type getSelectedType() {
        return selectedType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
