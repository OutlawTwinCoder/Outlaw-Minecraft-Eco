package com.outlaw.economy.core;

import com.outlaw.economy.api.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager implements EconomyService, Listener {

    private final Plugin plugin;
    private static final String SCOREBOARD_OBJECTIVE = "outlaweco";
    private static final String BALANCE_TEAM = "balanceValue";
    private static final String BALANCE_ENTRY = ChatColor.DARK_GREEN.toString();

    private final Map<UUID, Double> balances = Collections.synchronizedMap(new HashMap<>());
    private final File balanceFile;
    private final FileConfiguration balanceConfig;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

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
        updateBalanceDisplay(playerId);
    }

    public void initializePlayer(Player player) {
        ensureAccount(player.getUniqueId());
        setupSidebar(player);
        updateBalanceDisplay(player.getUniqueId());
    }

    public void deposit(Player player, double amount) {
        deposit(player.getUniqueId(), amount);
        player.sendMessage("§aVous avez reçu §e" + format(amount) + "§a.");
    }

    public boolean withdraw(Player player, double amount) {
        boolean success = withdraw(player.getUniqueId(), amount);
        if (success) {
            player.sendMessage("§c" + format(amount) + " a été retiré de votre compte.");
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

    public void setBalance(UUID playerId, double amount) {
        balances.put(playerId, Math.max(0, amount));
        updateBalanceDisplay(playerId);
    }

    @Override
    public boolean deposit(UUID playerId, double amount, String reason) {
        if (amount <= 0) {
            return false;
        }
        balances.put(playerId, getBalance(playerId) + amount);
        updateBalanceDisplay(playerId);
        logTransaction("deposit", playerId, amount, reason);
        return true;
    }

    public boolean deposit(UUID playerId, double amount) {
        return deposit(playerId, amount, "");
    }

    @Override
    public boolean withdraw(UUID playerId, double amount, String reason) {
        if (amount <= 0) {
            return false;
        }
        double balance = getBalance(playerId);
        if (balance < amount) {
            return false;
        }
        balances.put(playerId, balance - amount);
        updateBalanceDisplay(playerId);
        logTransaction("withdraw", playerId, amount, reason);
        return true;
    }

    public boolean withdraw(UUID playerId, double amount) {
        return withdraw(playerId, amount, "");
    }

    public boolean has(UUID playerId, double amount) {
        return getBalance(playerId) >= amount;
    }

    public Map<UUID, Double> getAllBalances() {
        synchronized (balances) {
            return Collections.unmodifiableMap(new HashMap<>(balances));
        }
    }

    @Override
    public String format(double amount) {
        synchronized (decimalFormat) {
            return decimalFormat.format(amount);
        }
    }

    @Override
    public String currencyCode() {
        return plugin.getConfig().getString("economy.currency-name", "Coins");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        initializePlayer(event.getPlayer());
    }

    private void setupSidebar(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard current = player.getScoreboard();
        Objective objective = current != null ? current.getObjective(SCOREBOARD_OBJECTIVE) : null;

        if (current == null || current == manager.getMainScoreboard() || objective == null) {
            Scoreboard scoreboard = manager.getNewScoreboard();
            Objective newObjective = scoreboard.registerNewObjective(SCOREBOARD_OBJECTIVE, "dummy", ChatColor.GOLD + "Argent");
            newObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
            applyBlankNumberFormat(newObjective);

            Team balanceTeam = scoreboard.registerNewTeam(BALANCE_TEAM);
            balanceTeam.addEntry(BALANCE_ENTRY);
            newObjective.getScore(BALANCE_ENTRY).setScore(1);

            player.setScoreboard(scoreboard);
            return;
        }

        objective.setDisplayName(ChatColor.GOLD + "Argent");
        applyBlankNumberFormat(objective);
    }

    private void updateBalanceDisplay(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        setupSidebar(player);
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(SCOREBOARD_OBJECTIVE);
        applyBlankNumberFormat(objective);

        Team team = scoreboard.getTeam(BALANCE_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(BALANCE_TEAM);
            team.addEntry(BALANCE_ENTRY);
            if (objective != null) {
                objective.getScore(BALANCE_ENTRY).setScore(1);
            }
        }

        double balance = balances.computeIfAbsent(
                playerId,
                id -> plugin.getConfig().getDouble("economy.starting-balance", 0)
        );
        team.setPrefix(ChatColor.GREEN + decimalFormat.format(balance) + " " + currencyCode());
    }

    private void applyBlankNumberFormat(Objective objective) {
        if (objective == null) {
            return;
        }

        objective.numberFormat(NumberFormat.blank());
    }

    private void logTransaction(String type, UUID playerId, double amount, String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        plugin.getLogger().fine(() -> String.format("%s %.2f to %s (%s)", type, amount, playerId, reason));
    }
}
