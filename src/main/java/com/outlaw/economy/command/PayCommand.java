package com.outlaw.economy.command;

import com.outlaw.economy.core.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public PayCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("Utilisation: /pay <joueur> <montant>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return true;
        }

        if (target.equals(player)) {
            sender.sendMessage("§cVous ne pouvez pas vous envoyer de l'argent.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMontant invalide.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cLe montant doit être positif.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (!economyManager.withdraw(playerId, amount, "Pay to " + target.getName())) {
            sender.sendMessage("§cVous n'avez pas assez d'argent.");
            return true;
        }

        economyManager.deposit(target.getUniqueId(), amount, "Payment from " + player.getName());

        String formattedAmount = economyManager.format(amount);
        player.sendMessage("§aVous avez envoyé §e" + formattedAmount + "§a à §e" + target.getName());
        target.sendMessage("§aVous avez reçu §e" + formattedAmount + "§a de §e" + player.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && online.equals(sender)) {
                    continue;
                }
                if (online.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
