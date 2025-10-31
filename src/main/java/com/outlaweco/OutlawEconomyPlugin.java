package com.outlaweco;

import com.outlaweco.api.EconomyAPI;
import com.outlaweco.api.EconomyService;
import com.outlaweco.economy.EconomyManager;
import com.outlaweco.economy.command.BalanceCommand;
import com.outlaweco.economy.command.MoneyAdminCommand;
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
        if (shopManager != null) {
            shopManager.shutdown();
        }
        EconomyAPI.unregister();
        Bukkit.getServicesManager().unregister(EconomyService.class, economyManager);
    }

    private void registerCommands() {
        PluginCommand balanceCommand = getCommand("balance");
        if (balanceCommand != null) {
            BalanceCommand executor = new BalanceCommand(economyManager);
            balanceCommand.setExecutor(executor);
            balanceCommand.setTabCompleter(executor);
        }

        PluginCommand payCommand = getCommand("pay");
        if (payCommand != null) {
            com.outlaweco.economy.command.PayCommand executor = new com.outlaweco.economy.command.PayCommand(economyManager);
            payCommand.setExecutor(executor);
            payCommand.setTabCompleter(executor);
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

        PluginCommand shopNpcCommand = getCommand("shoppnj");
        if (shopNpcCommand != null) {
            shopNpcCommand.setExecutor(shopManager);
            shopNpcCommand.setTabCompleter(shopManager);
        }

        MoneyAdminCommand moneyAdminCommand = new MoneyAdminCommand(economyManager);

        PluginCommand giveMoneyCommand = getCommand("givemoney");
        if (giveMoneyCommand != null) {
            giveMoneyCommand.setExecutor(moneyAdminCommand);
            giveMoneyCommand.setTabCompleter(moneyAdminCommand);
        }

        PluginCommand removeMoneyCommand = getCommand("removemoney");
        if (removeMoneyCommand != null) {
            removeMoneyCommand.setExecutor(moneyAdminCommand);
            removeMoneyCommand.setTabCompleter(moneyAdminCommand);
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
