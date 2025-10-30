package com.outlaweco;

import com.outlaweco.api.EconomyAPI;
import com.outlaweco.api.EconomyService;
import com.outlaweco.economy.EconomyManager;
import com.outlaweco.shop.ShopManager;
import com.outlaweco.trade.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class OutlawEconomyPlugin extends JavaPlugin implements Listener {

    private EconomyManager economyManager;
    private ShopManager shopManager;
    private TradeManager tradeManager;

    public static OutlawEconomyPlugin getInstance() {
        return getPlugin(OutlawEconomyPlugin.class);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.economyManager = new EconomyManager(this);
        this.shopManager = new ShopManager(this);
        this.tradeManager = new TradeManager(this, economyManager);

        Bukkit.getServicesManager().register(EconomyService.class, economyManager, this, ServicePriority.Normal);
        EconomyAPI.register(economyManager);

        Bukkit.getPluginManager().registerEvents(shopManager, this);
        Bukkit.getPluginManager().registerEvents(tradeManager, this);
        Bukkit.getPluginManager().registerEvents(economyManager, this);

        registerCommands();

        for (Player player : Bukkit.getOnlinePlayers()) {
            economyManager.initializePlayer(player);
        }

        getLogger().info("OutlawEconomy enabled.");
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.saveAll();
        }
        EconomyAPI.unregister();
        Bukkit.getServicesManager().unregister(EconomyService.class, economyManager);
    }

    private void registerCommands() {
        PluginCommand balanceCommand = getCommand("balance");
        if (balanceCommand != null) {
            balanceCommand.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                double balance = economyManager.getBalance(player.getUniqueId());
                sender.sendMessage("§aVotre argent: §e" + String.format("%.2f", balance));
                return true;
            });
        }

        PluginCommand payCommand = getCommand("pay");
        if (payCommand != null) {
            payCommand.setExecutor(new com.outlaweco.economy.command.PayCommand(economyManager));
            payCommand.setTabCompleter(new com.outlaweco.economy.command.PayCommand(economyManager));
        }

        PluginCommand tradeCommand = getCommand("trade");
        if (tradeCommand != null) {
            tradeCommand.setExecutor(tradeManager);
            tradeCommand.setTabCompleter(tradeManager);
        }

        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(shopManager);
            shopCommand.setTabCompleter(shopManager);
        }
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}
