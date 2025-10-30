package com.outlaweco.trade;

import com.outlaweco.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TradeSession {

    public static final int[] PLAYER_ONE_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    public static final int[] PLAYER_TWO_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    public static final int CONFIRM_SLOT_ONE = 45;
    public static final int CONFIRM_SLOT_TWO = 53;
    public static final int CANCEL_SLOT = 49;
    private static final int[] MONEY_SLOTS_ONE = {36, 37, 38};
    private static final int[] MONEY_SLOTS_TWO = {40, 41, 42};
    private static final int MONEY_DISPLAY_ONE = 39;
    private static final int MONEY_DISPLAY_TWO = 43;

    private final EconomyManager economyManager;
    private final UUID playerOne;
    private final UUID playerTwo;
    private final Inventory inventory;
    private final Map<UUID, Integer> moneyOffers = new HashMap<>();
    private final Map<UUID, Boolean> confirmations = new HashMap<>();

    public TradeSession(Player one, Player two, EconomyManager economyManager) {
        this.playerOne = one.getUniqueId();
        this.playerTwo = two.getUniqueId();
        this.economyManager = economyManager;
        this.inventory = Bukkit.createInventory(new TradeInventoryHolder(this), 54,
                Component.text("Echange: " + one.getName() + " & " + two.getName()));
        moneyOffers.put(playerOne, 0);
        moneyOffers.put(playerTwo, 0);
        confirmations.put(playerOne, false);
        confirmations.put(playerTwo, false);
        decorateInventory();
        one.openInventory(inventory);
        two.openInventory(inventory);
        sendStatus();
    }

    private void decorateInventory() {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(""));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        clearTradeSlots();
        updateMoneyButtons();
        updateConfirmButtons();
        inventory.setItem(CANCEL_SLOT, createItem(Material.BARRIER, Component.text("Annuler l'échange", NamedTextColor.RED)));
    }

    public void clearTradeSlots() {
        for (int slot : PLAYER_ONE_SLOTS) {
            inventory.setItem(slot, null);
        }
        for (int slot : PLAYER_TWO_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    private ItemStack createItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void updateMoneyButtons() {
        ItemStack plusOne = createItem(Material.GOLD_NUGGET, Component.text("+1", NamedTextColor.GOLD));
        ItemStack plusTen = createItem(Material.GOLD_INGOT, Component.text("+10", NamedTextColor.GOLD));
        ItemStack plusHundred = createItem(Material.GOLD_BLOCK, Component.text("+100", NamedTextColor.GOLD));
        inventory.setItem(MONEY_SLOTS_ONE[0], plusOne);
        inventory.setItem(MONEY_SLOTS_ONE[1], plusTen);
        inventory.setItem(MONEY_SLOTS_ONE[2], plusHundred);
        inventory.setItem(MONEY_SLOTS_TWO[0], plusOne);
        inventory.setItem(MONEY_SLOTS_TWO[1], plusTen);
        inventory.setItem(MONEY_SLOTS_TWO[2], plusHundred);
        refreshMoneyDisplays();
    }

    private void updateConfirmButtons() {
        inventory.setItem(CONFIRM_SLOT_ONE, confirmationItem(playerOne));
        inventory.setItem(CONFIRM_SLOT_TWO, confirmationItem(playerTwo));
    }

    private ItemStack confirmationItem(UUID playerId) {
        boolean confirmed = confirmations.getOrDefault(playerId, false);
        Material material = confirmed ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String text = confirmed ? "Confirmé" : "En attente";
        return createItem(material, Component.text(text, confirmed ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    public boolean isPlayer(UUID uuid) {
        return uuid.equals(playerOne) || uuid.equals(playerTwo);
    }

    public Player getPlayer(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public UUID getPlayerOne() {
        return playerOne;
    }

    public UUID getPlayerTwo() {
        return playerTwo;
    }

    public void adjustMoney(UUID playerId, int amount) {
        int current = moneyOffers.getOrDefault(playerId, 0);
        int updated = Math.max(0, current + amount);
        moneyOffers.put(playerId, updated);
        confirmations.put(playerId, false);
        confirmations.put(other(playerId), false);
        refreshMoneyDisplays();
        updateConfirmButtons();
        sendStatus();
    }

    private void refreshMoneyDisplays() {
        inventory.setItem(MONEY_DISPLAY_ONE, createItem(Material.PAPER,
                Component.text("Offre: " + moneyOffers.get(playerOne), NamedTextColor.YELLOW)));
        inventory.setItem(MONEY_DISPLAY_TWO, createItem(Material.PAPER,
                Component.text("Offre: " + moneyOffers.get(playerTwo), NamedTextColor.YELLOW)));
    }

    public boolean isConfirmSlot(UUID playerId, int slot) {
        if (playerId.equals(playerOne)) {
            return slot == CONFIRM_SLOT_ONE;
        }
        if (playerId.equals(playerTwo)) {
            return slot == CONFIRM_SLOT_TWO;
        }
        return false;
    }

    public void toggleConfirm(UUID playerId) {
        confirmations.put(playerId, !confirmations.getOrDefault(playerId, false));
        updateConfirmButtons();
        sendStatus();
    }

    public void resetConfirmations() {
        confirmations.put(playerOne, false);
        confirmations.put(playerTwo, false);
        updateConfirmButtons();
    }

    public boolean bothConfirmed() {
        return confirmations.getOrDefault(playerOne, false) && confirmations.getOrDefault(playerTwo, false);
    }

    public void sendStatus() {
        Player one = getPlayer(playerOne);
        Player two = getPlayer(playerTwo);
        if (one != null) {
            one.sendActionBar(Component.text("Offre: " + moneyOffers.get(playerOne) + "$", NamedTextColor.GOLD));
        }
        if (two != null) {
            two.sendActionBar(Component.text("Offre: " + moneyOffers.get(playerTwo) + "$", NamedTextColor.GOLD));
        }
    }

    public int getMoneyOffer(UUID playerId) {
        return moneyOffers.getOrDefault(playerId, 0);
    }

    public int[] getSlotsFor(UUID playerId) {
        return playerId.equals(playerOne) ? PLAYER_ONE_SLOTS : PLAYER_TWO_SLOTS;
    }

    public boolean isTradeSlot(UUID playerId, int rawSlot) {
        int[] slots = getSlotsFor(playerId);
        for (int slot : slots) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    public boolean isMoneyButton(UUID playerId, int slot) {
        if (playerId.equals(playerOne)) {
            return slot == MONEY_SLOTS_ONE[0] || slot == MONEY_SLOTS_ONE[1] || slot == MONEY_SLOTS_ONE[2];
        }
        if (playerId.equals(playerTwo)) {
            return slot == MONEY_SLOTS_TWO[0] || slot == MONEY_SLOTS_TWO[1] || slot == MONEY_SLOTS_TWO[2];
        }
        return false;
    }

    public boolean isMoneyDisplay(int slot) {
        return slot == MONEY_DISPLAY_ONE || slot == MONEY_DISPLAY_TWO;
    }

    public UUID other(UUID playerId) {
        return playerId.equals(playerOne) ? playerTwo : playerOne;
    }

    public boolean canAccept(UUID playerId) {
        Player player = getPlayer(playerId);
        if (player == null) {
            return false;
        }
        int offer = getMoneyOffer(playerId);
        return economyManager.has(playerId, offer);
    }

    public boolean finalizeTrade() {
        Player one = getPlayer(playerOne);
        Player two = getPlayer(playerTwo);
        if (one == null || two == null) {
            cancel();
            return false;
        }
        if (!canAccept(playerOne) || !canAccept(playerTwo)) {
            one.sendMessage("§cBalance insuffisante pour finaliser.");
            two.sendMessage("§cBalance insuffisante pour finaliser.");
            resetConfirmations();
            return false;
        }
        List<ItemStack> toOne = collectItems(PLAYER_TWO_SLOTS);
        List<ItemStack> toTwo = collectItems(PLAYER_ONE_SLOTS);
        int offerOne = getMoneyOffer(playerOne);
        int offerTwo = getMoneyOffer(playerTwo);
        boolean withdrawnOne = economyManager.withdraw(playerOne, offerOne);
        boolean withdrawnTwo = economyManager.withdraw(playerTwo, offerTwo);
        if (!withdrawnOne || !withdrawnTwo) {
            if (withdrawnOne) {
                economyManager.deposit(playerOne, offerOne);
            }
            if (withdrawnTwo) {
                economyManager.deposit(playerTwo, offerTwo);
            }
            one.sendMessage("§cErreur lors du paiement.");
            two.sendMessage("§cErreur lors du paiement.");
            resetConfirmations();
            return false;
        }
        economyManager.deposit(playerTwo, offerOne);
        economyManager.deposit(playerOne, offerTwo);
        giveItems(one, toOne);
        giveItems(two, toTwo);
        one.sendMessage("§aEchange réussi !");
        two.sendMessage("§aEchange réussi !");
        close();
        return true;
    }

    private List<ItemStack> collectItems(int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
            remaining.values().forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
        }
    }

    public void cancel() {
        returnItems(playerOne, PLAYER_ONE_SLOTS);
        returnItems(playerTwo, PLAYER_TWO_SLOTS);
        Player one = getPlayer(playerOne);
        Player two = getPlayer(playerTwo);
        if (one != null) {
            one.sendMessage("§cEchange annulé.");
        }
        if (two != null) {
            two.sendMessage("§cEchange annulé.");
        }
        close();
    }

    private void returnItems(UUID ownerId, int[] slots) {
        Player owner = getPlayer(ownerId);
        Player fallback = getPlayer(other(ownerId));
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (owner != null) {
                owner.getInventory().addItem(item);
            } else if (fallback != null) {
                fallback.getWorld().dropItemNaturally(fallback.getLocation(), item);
            } else if (!Bukkit.getWorlds().isEmpty()) {
                Bukkit.getWorlds().get(0).dropItemNaturally(Bukkit.getWorlds().get(0).getSpawnLocation(), item);
            }
            inventory.setItem(slot, null);
        }
    }

    private void close() {
        Player one = getPlayer(playerOne);
        Player two = getPlayer(playerTwo);
        if (one != null && one.getOpenInventory().getTopInventory().equals(inventory)) {
            one.closeInventory();
        }
        if (two != null && two.getOpenInventory().getTopInventory().equals(inventory)) {
            two.closeInventory();
        }
    }
}
