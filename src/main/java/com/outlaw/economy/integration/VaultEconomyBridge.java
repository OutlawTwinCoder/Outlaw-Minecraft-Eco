package com.outlaw.economy.integration;

import com.outlaw.economy.core.EconomyManager;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class VaultEconomyBridge extends AbstractEconomy {

    private final Plugin plugin;
    private final EconomyManager economyManager;

    public VaultEconomyBridge(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return plugin.getName();
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return economyManager.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return economyManager.currencyCode();
    }

    @Override
    public String currencyNameSingular() {
        return economyManager.currencyCode();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(resolvePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && economyManager.accountExists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(resolvePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) {
            return 0;
        }
        return economyManager.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(resolvePlayer(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return player != null && economyManager.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdraw(resolvePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return deposit(resolvePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createAccount(resolvePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return createAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notSupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private OfflinePlayer resolvePlayer(String playerName) {
        if (playerName == null) {
            return null;
        }
        return Bukkit.getOfflinePlayer(playerName);
    }

    private EconomyResponse withdraw(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, ResponseType.FAILURE, "Player not found");
        }
        if (amount <= 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Amount must be positive");
        }
        UUID playerId = player.getUniqueId();
        if (!economyManager.accountExists(playerId)) {
            return new EconomyResponse(0, 0, ResponseType.FAILURE, "Account does not exist");
        }
        boolean success = economyManager.withdraw(playerId, amount, "Vault withdraw");
        double balance = economyManager.getBalance(playerId);
        return success
            ? new EconomyResponse(amount, balance, ResponseType.SUCCESS, null)
            : new EconomyResponse(0, balance, ResponseType.FAILURE, "Insufficient funds");
    }

    private EconomyResponse deposit(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, ResponseType.FAILURE, "Player not found");
        }
        if (amount <= 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Amount must be positive");
        }
        UUID playerId = player.getUniqueId();
        economyManager.ensureAccount(playerId);
        boolean success = economyManager.deposit(playerId, amount, "Vault deposit");
        double balance = economyManager.getBalance(playerId);
        return success
            ? new EconomyResponse(amount, balance, ResponseType.SUCCESS, null)
            : new EconomyResponse(0, balance, ResponseType.FAILURE, "Deposit failed");
    }

    private boolean createAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (economyManager.accountExists(playerId)) {
            return false;
        }
        economyManager.ensureAccount(playerId);
        return true;
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Bank support is not available");
    }
}
