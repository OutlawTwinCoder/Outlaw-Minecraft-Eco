package com.outlaweco.economy;

import com.outlaweco.api.EconomyService;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager implements EconomyService, Listener {

    private final Plugin plugin;
    private final Map<UUID, Double> balances = Collections.synchronizedMap(new HashMap<>());
    private final File balanceFile;
    private final FileConfiguration balanceConfig;

    public EconomyManager(Plugin plugin) {
        this.plugin = plugin;
        this.balanceFile = new File(plugin.getDataFolder(), "balances.yml");
        if (!balanceFile.exists()) {
            try {
                balanceFile.getParentFile().mkdirs();
                balanceFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer balances.yml: " + e.getMessage());
            }
        }
        this.balanceConfig = YamlConfiguration.loadConfiguration(balanceFile);
        loadBalances();
    }

    private void loadBalances() {
        for (String key : balanceConfig.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                double value = balanceConfig.getDouble(key);
                balances.put(id, value);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Entrée invalide dans balances.yml: " + key);
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            balanceConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            balanceConfig.save(balanceFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer balances.yml: " + e.getMessage());
        }
    }

    public void ensureAccount(UUID playerId) {
        balances.putIfAbsent(playerId, plugin.getConfig().getDouble("economy.starting-balance", 0));
    }

    public void deposit(Player player, double amount) {
        deposit(player.getUniqueId(), amount);
        player.sendMessage("§aVous avez reçu §e" + String.format("%.2f", amount) + "§a.");
    }

    public boolean withdraw(Player player, double amount) {
        boolean success = withdraw(player.getUniqueId(), amount);
        if (success) {
            player.sendMessage("§c" + String.format("%.2f", amount) + " a été retiré de votre compte.");
        }
        return success;
    }

    @Override
    public double getBalance(UUID playerId) {
        ensureAccount(playerId);
        return balances.getOrDefault(playerId, 0d);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public void setBalance(UUID playerId, double amount) {
        balances.put(playerId, Math.max(0, amount));
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        if (amount <= 0) {
            return;
        }
        balances.put(playerId, getBalance(playerId) + amount);
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        double balance = getBalance(playerId);
        if (balance < amount) {
            return false;
        }
        balances.put(playerId, balance - amount);
        return true;
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return getBalance(playerId) >= amount;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureAccount(event.getPlayer().getUniqueId());
    }
}
