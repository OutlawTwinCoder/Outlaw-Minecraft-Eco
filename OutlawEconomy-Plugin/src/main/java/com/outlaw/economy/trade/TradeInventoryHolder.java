package com.outlaw.economy.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TradeInventoryHolder implements InventoryHolder {

    private final TradeSession session;

    public TradeInventoryHolder(TradeSession session) {
        this.session = session;
    }

    public TradeSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
