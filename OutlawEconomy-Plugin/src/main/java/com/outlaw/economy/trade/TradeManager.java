package com.outlaw.economy.trade;

import com.outlaw.economy.core.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;

import java.util.*;
import java.util.stream.Collectors;

public class TradeManager implements CommandExecutor, TabCompleter, Listener {

    private final EconomyManager economyManager;
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    private final Plugin plugin;
    private final long requestTimeout;

    public TradeManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.requestTimeout = plugin.getConfig().getLong("trade.request-timeout", 30L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupRequests, 20L, 20L);
    }

    private void cleanupRequests() {
        Iterator<Map.Entry<UUID, TradeRequest>> iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TradeRequest> entry = iterator.next();
            if (entry.getValue().isExpired(requestTimeout)) {
                Player target = Bukkit.getPlayer(entry.getKey());
                Player requester = Bukkit.getPlayer(entry.getValue().getRequester());
                if (requester != null) {
                    requester.sendMessage("§cLa demande d'échange a expiré.");
                }
                if (target != null) {
                    target.sendMessage("§cLa demande d'échange de " + (requester != null ? requester.getName() : "joueur") + " a expiré.");
                }
                iterator.remove();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commandes réservées aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUsage: /trade <joueur|accept|deny|cancel>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "accept":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /trade accept <joueur>");
                    return true;
                }
                acceptRequest(player, args[1]);
                return true;
            case "deny":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /trade deny <joueur>");
                    return true;
                }
                denyRequest(player, args[1]);
                return true;
            case "cancel":
                cancelActiveTrade(player);
                return true;
            default:
                sendRequest(player, args[0]);
                return true;
        }
    }

    private void sendRequest(Player requester, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            requester.sendMessage("§cJoueur introuvable.");
            return;
        }
        if (target.equals(requester)) {
            requester.sendMessage("§cVous ne pouvez pas échanger avec vous-même.");
            return;
        }
        if (isBusy(requester.getUniqueId()) || isBusy(target.getUniqueId())) {
            requester.sendMessage("§cVous ou le joueur êtes déjà en échange.");
            return;
        }
        pendingRequests.put(target.getUniqueId(), new TradeRequest(requester.getUniqueId(), target.getUniqueId()));
        requester.sendMessage("§aDemande envoyée à §e" + target.getName());
        Component message = Component.text(requester.getName() + " vous propose un échange. ", NamedTextColor.GOLD)
                .append(Component.text("[Confirmer]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/trade accept " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Cliquez pour accepter", NamedTextColor.GREEN))))
                .append(Component.text(" "))
                .append(Component.text("[Refuser]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/trade deny " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Cliquez pour refuser", NamedTextColor.RED))));
        target.sendMessage(message);
    }

    private boolean isBusy(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    private void acceptRequest(Player target, String requesterName) {
        TradeRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null) {
            target.sendMessage("§cAucune demande d'échange en attente.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null || !requester.getUniqueId().equals(request.getRequester())) {
            target.sendMessage("§cCe joueur n'a plus de demande active.");
            pendingRequests.remove(target.getUniqueId());
            return;
        }
        if (request.isExpired(requestTimeout)) {
            target.sendMessage("§cLa demande a expiré.");
            requester.sendMessage("§cVotre demande a expiré.");
            pendingRequests.remove(target.getUniqueId());
            return;
        }
        pendingRequests.remove(target.getUniqueId());
        startSession(requester, target);
    }

    private void denyRequest(Player target, String requesterName) {
        TradeRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null) {
            target.sendMessage("§cAucune demande d'échange en attente.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester != null && requester.getUniqueId().equals(request.getRequester())) {
            requester.sendMessage("§cVotre demande a été refusée.");
        }
        pendingRequests.remove(target.getUniqueId());
        target.sendMessage("§eDemande refusée.");
    }

    private void cancelActiveTrade(Player player) {
        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cVous n'êtes pas en échange.");
            return;
        }
        session.cancel();
        endSession(session);
    }

    private void startSession(Player requester, Player target) {
        TradeSession session = new TradeSession(requester, target, economyManager);
        activeSessions.put(requester.getUniqueId(), session);
        activeSessions.put(target.getUniqueId(), session);
        requester.sendMessage("§aEchange commencé avec §e" + target.getName());
        target.sendMessage("§aEchange commencé avec §e" + requester.getName());
    }

    private void endSession(TradeSession session) {
        activeSessions.remove(session.getPlayerOne());
        activeSessions.remove(session.getPlayerTwo());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(List.of("accept", "deny", "cancel"));
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            TradeRequest request = pendingRequests.get(((Player) sender).getUniqueId());
            if (request != null) {
                Player requester = Bukkit.getPlayer(request.getRequester());
                if (requester != null) {
                    return List.of(requester.getName());
                }
            }
        }
        return List.of();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeInventoryHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = holder.getSession();
        if (session == null || !session.isPlayer(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        InventoryView view = event.getView();
        int rawSlot = event.getRawSlot();
        int topSize = view.getTopInventory().getSize();
        if (rawSlot < topSize) {
            if (session.isMoneyButton(player.getUniqueId(), rawSlot)) {
                event.setCancelled(true);
                int delta = moneyDelta(rawSlot, player.getUniqueId(), session);
                if (event.isRightClick()) {
                    delta = -delta;
                }
                if (delta != 0) {
                    session.adjustMoney(player.getUniqueId(), delta);
                }
                return;
            }
            if (session.isMoneyDisplay(rawSlot)) {
                event.setCancelled(true);
                return;
            }
            if (rawSlot == TradeSession.CANCEL_SLOT) {
                event.setCancelled(true);
                session.cancel();
                endSession(session);
                return;
            }
            if (session.isConfirmSlot(player.getUniqueId(), rawSlot)) {
                event.setCancelled(true);
                if (!session.canAccept(player.getUniqueId())) {
                    player.sendMessage("§cBalance insuffisante pour confirmer.");
                    return;
                }
                session.toggleConfirm(player.getUniqueId());
                if (session.bothConfirmed()) {
                    if (session.finalizeTrade()) {
                        endSession(session);
                    }
                }
                return;
            }
            if (!session.isTradeSlot(player.getUniqueId(), rawSlot)) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            session.resetConfirmations();
            return;
        }

        // bottom inventory interactions
        if (event.isShiftClick()) {
            event.setCancelled(true);
            player.sendMessage("§cGlissez les objets manuellement dans votre zone d'échange.");
        } else {
            session.resetConfirmations();
        }
    }

    private int moneyDelta(int slot, UUID playerId, TradeSession session) {
        boolean first = session.getPlayerOne().equals(playerId);
        if (first) {
            if (slot == 36) return 1;
            if (slot == 37) return 10;
            if (slot == 38) return 100;
        } else {
            if (slot == 40) return 1;
            if (slot == 41) return 10;
            if (slot == 42) return 100;
        }
        return 0;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeInventoryHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = holder.getSession();
        if (session == null || !session.isPlayer(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && !session.isTradeSlot(player.getUniqueId(), slot)) {
                event.setCancelled(true);
                return;
            }
        }
        session.resetConfirmations();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeInventoryHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        TradeSession session = holder.getSession();
        if (session == null) {
            return;
        }
        if (!session.isPlayer(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (activeSessions.containsKey(session.getPlayerOne()) || activeSessions.containsKey(session.getPlayerTwo())) {
                session.cancel();
                endSession(session);
            }
        });
    }
}
