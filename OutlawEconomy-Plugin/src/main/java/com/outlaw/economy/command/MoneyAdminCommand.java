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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MoneyAdminCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public MoneyAdminCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("outlawecoadmin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUtilisation: /" + command.getName().toLowerCase() + " <joueur> <montant>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cJoueur introuvable: " + args[0]);
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cMontant invalide: " + args[1]);
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cLe montant doit être supérieur à zéro.");
            return true;
        }

        UUID targetId = target.getUniqueId();
        economyManager.ensureAccount(targetId);
        String targetName = target.getName() != null ? target.getName() : args[0];
        Player onlineTarget = target.getPlayer();

        if (command.getName().equalsIgnoreCase("givemoney")) {
            economyManager.deposit(targetId, amount, "Admin grant by " + sender.getName());
            sender.sendMessage("§aVous avez donné §e" + economyManager.format(amount) + " " + economyManager.currencyCode() + " §aà §e" + targetName + "§a.");
            if (onlineTarget != null) {
                onlineTarget.sendMessage("§aVous avez reçu §e" + economyManager.format(amount) + " " + economyManager.currencyCode() + " §ade la part d'un administrateur.");
            }
            return true;
        }

        double currentBalance = economyManager.getBalance(targetId);
        double newBalance = Math.max(0, currentBalance - amount);
        economyManager.setBalance(targetId, newBalance);
        double removed = currentBalance - newBalance;

        sender.sendMessage("§cVous avez retiré §e" + economyManager.format(removed) + " " + economyManager.currencyCode() + " §cdu compte de §e" + targetName + "§c.");
        if (onlineTarget != null) {
            onlineTarget.sendMessage("§cUn administrateur a retiré §e" + economyManager.format(removed) + " " + economyManager.currencyCode() + " §cde votre compte.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("outlawecoadmin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            suggestions.addAll(economyManager.getAllBalances().keySet().stream()
                    .map(uuid -> {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        return offlinePlayer.getName();
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toList()));
            return suggestions;
        }
        return List.of();
    }
}
