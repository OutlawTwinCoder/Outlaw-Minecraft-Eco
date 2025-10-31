package com.outlaw.economy.command;

import com.outlaw.economy.core.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public BalanceCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command without arguments.");
                return true;
            }
            double balance = economyManager.getBalance(player.getUniqueId());
            sender.sendMessage("§aVotre argent: §e" + economyManager.format(balance) + " " + economyManager.currencyCode());
            return true;
        }

        if (!sender.hasPermission("outlawecoadmin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            Map<UUID, Double> balances = economyManager.getAllBalances();
            if (balances.isEmpty()) {
                sender.sendMessage("§cAucun compte n'a encore été créé.");
                return true;
            }

            sender.sendMessage("§6Balances de tous les joueurs:");
            balances.entrySet().stream()
                    .map(entry -> {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                        String name = offlinePlayer.getName();
                        if (name == null || name.isBlank()) {
                            name = entry.getKey().toString();
                        }
                        return new PlayerBalance(name, entry.getValue());
                    })
                    .sorted(Comparator.comparing(playerBalance -> playerBalance.name.toLowerCase(Locale.ROOT)))
                    .forEach(playerBalance -> sender.sendMessage("§e" + playerBalance.name + "§7: §a" + economyManager.format(playerBalance.balance) + " " + economyManager.currencyCode()));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cJoueur introuvable: " + args[0]);
            return true;
        }

        double balance = economyManager.getBalance(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage("§e" + targetName + "§7 possède §a" + economyManager.format(balance) + " " + economyManager.currencyCode() + "§7.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("outlawecoadmin")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            return suggestions;
        }
        return List.of();
    }

    private record PlayerBalance(String name, double balance) {
    }
}
