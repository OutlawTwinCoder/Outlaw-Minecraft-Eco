package com.outlaweco.shop;

import com.outlaweco.OutlawEconomyPlugin;
import com.outlaweco.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ShopManager implements CommandExecutor, TabCompleter, Listener {

    private final OutlawEconomyPlugin plugin;
    private final EconomyManager economyManager;
    private final File shopsFile;
    private final FileConfiguration shopsConfig;
    private final NamespacedKey shopKey;
    private final Map<UUID, Shop> shops = new HashMap<>();

    public ShopManager(OutlawEconomyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.shopKey = new NamespacedKey(plugin, "shop-id");
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            try {
                shopsFile.getParentFile().mkdirs();
                shopsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer shops.yml: " + e.getMessage());
            }
        }
        this.shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        loadShops();
    }

    private void loadShops() {
        for (String key : shopsConfig.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ID de boutique invalide: " + key);
                continue;
            }
            String typeName = shopsConfig.getString(key + ".type");
            ShopType type = ShopType.fromString(typeName);
            if (type == null) {
                plugin.getLogger().warning("Type de boutique invalide pour " + key);
                continue;
            }
            String worldName = shopsConfig.getString(key + ".world");
            double x = shopsConfig.getDouble(key + ".x");
            double y = shopsConfig.getDouble(key + ".y");
            double z = shopsConfig.getDouble(key + ".z");
            float yaw = (float) shopsConfig.getDouble(key + ".yaw");
            float pitch = (float) shopsConfig.getDouble(key + ".pitch");
            if (Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("Monde introuvable pour la boutique " + key);
                continue;
            }
            Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            Shop shop = new Shop(id, type, loc);
            shop.setOffers(type.createOffers());
            spawnShopEntity(shop);
            shops.put(id, shop);
        }
    }

    private void spawnShopEntity(Shop shop) {
        Villager villager = shop.spawn();
        villager.setVillagerLevel(5);
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(shopKey, PersistentDataType.STRING, shop.getId().toString());
    }

    private void saveShop(Shop shop) {
        String base = shop.getId().toString();
        shopsConfig.set(base + ".type", shop.getType().getKey());
        Location loc = shop.getLocation();
        shopsConfig.set(base + ".world", loc.getWorld().getName());
        shopsConfig.set(base + ".x", loc.getX());
        shopsConfig.set(base + ".y", loc.getY());
        shopsConfig.set(base + ".z", loc.getZ());
        shopsConfig.set(base + ".yaw", loc.getYaw());
        shopsConfig.set(base + ".pitch", loc.getPitch());
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer shops.yml: " + e.getMessage());
        }
    }

    private void deleteShop(UUID shopId) {
        shops.remove(shopId);
        shopsConfig.set(shopId.toString(), null);
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer shops.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commandes réservées aux joueurs.");
            return true;
        }

        if (!player.hasPermission("outlaweconomy.admin")) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§e/shop create <type>§7 - créer une boutique");
            player.sendMessage("§e/shop remove§7 - supprimer la boutique ciblée");
            player.sendMessage("§e/shop list§7 - liste des boutiques");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cMerci de préciser un type: " + String.join(", ", ShopType.keys()));
                    return true;
                }
                ShopType type = ShopType.fromString(args[1]);
                if (type == null) {
                    player.sendMessage("§cType invalide. Types: " + String.join(", ", ShopType.keys()));
                    return true;
                }
                createShop(player, type);
                return true;
            case "remove":
                removeShop(player);
                return true;
            case "list":
                listShops(player);
                return true;
            default:
                player.sendMessage("§cSous-commande inconnue.");
                return true;
        }
    }

    private void createShop(Player player, ShopType type) {
        Location loc = player.getLocation();
        UUID id = UUID.randomUUID();
        Shop shop = new Shop(id, type, loc);
        shop.setOffers(type.createOffers());
        spawnShopEntity(shop);
        shops.put(id, shop);
        saveShop(shop);
        player.sendMessage("§aBoutique créée: §e" + type.name());
    }

    private void removeShop(Player player) {
        Entity target = player.getTargetEntity(5);
        if (!(target instanceof Villager villager)) {
            player.sendMessage("§cVisez un villageois de boutique.");
            return;
        }
        UUID shopId = getShopId(villager);
        if (shopId == null) {
            player.sendMessage("§cCe villageois n'est pas géré par OutlawEconomy.");
            return;
        }
        villager.remove();
        deleteShop(shopId);
        player.sendMessage("§aBoutique supprimée.");
    }

    private void listShops(Player player) {
        if (shops.isEmpty()) {
            player.sendMessage("§7Aucune boutique créée.");
            return;
        }
        player.sendMessage("§6Boutiques disponibles:");
        for (Shop shop : shops.values()) {
            Location loc = shop.getLocation();
            player.sendMessage("§e- " + shop.getType().name() + " §7@ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    private UUID getShopId(Entity entity) {
        if (entity instanceof Villager villager) {
            PersistentDataContainer data = villager.getPersistentDataContainer();
            String id = data.get(shopKey, PersistentDataType.STRING);
            if (id != null) {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("outlaweconomy.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return ShopType.keys().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        UUID shopId = getShopId(entity);
        if (shopId == null) {
            return;
        }
        event.setCancelled(true);
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        openShop(event.getPlayer(), shop);
    }

    private void openShop(Player player, Shop shop) {
        Inventory inventory = Bukkit.createInventory(new ShopInventoryHolder(shop), 27, "§eBoutique " + shop.getType().name());
        List<ShopOffer> offers = shop.getOffers();
        for (int i = 0; i < offers.size() && i < inventory.getSize(); i++) {
            ShopOffer offer = offers.get(i);
            ItemStack item = offer.item();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("§aAchat: §e" + String.format("%.2f", offer.buyPrice()));
                lore.add("§cVente: §e" + String.format("%.2f", offer.sellPrice()));
                lore.add("§7Clique gauche pour acheter");
                lore.add("§7Clique droit pour vendre");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == org.bukkit.Material.AIR) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Shop shop = holder.getShop();
        int slot = event.getRawSlot();
        if (slot >= shop.getOffers().size()) {
            return;
        }
        ShopOffer offer = shop.getOffers().get(slot);
        if (event.isLeftClick()) {
            handleBuy(player, offer);
        } else if (event.isRightClick()) {
            handleSell(player, offer);
        }
    }

    private void handleBuy(Player player, ShopOffer offer) {
        double price = offer.buyPrice();
        if (!economyManager.withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cPas assez d'argent.");
            return;
        }
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(offer.item());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.sendMessage("§aAchat effectué pour §e" + String.format("%.2f", price));
    }

    private void handleSell(Player player, ShopOffer offer) {
        ItemStack itemToSell = offer.item();
        if (!player.getInventory().containsAtLeast(itemToSell, itemToSell.getAmount())) {
            player.sendMessage("§cVous n'avez pas les objets nécessaires.");
            return;
        }
        removeItems(player.getInventory(), itemToSell);
        economyManager.deposit(player.getUniqueId(), offer.sellPrice());
        player.sendMessage("§aVente effectuée pour §e" + String.format("%.2f", offer.sellPrice()));
    }

    private void removeItems(org.bukkit.inventory.PlayerInventory inventory, ItemStack item) {
        int amountToRemove = item.getAmount();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(item)) {
                continue;
            }
            if (stack.getAmount() > amountToRemove) {
                stack.setAmount(stack.getAmount() - amountToRemove);
                break;
            } else {
                amountToRemove -= stack.getAmount();
                contents[i] = null;
            }
            if (amountToRemove <= 0) {
                break;
            }
        }
        inventory.setContents(contents);
    }

    @EventHandler
    public void onShopDamage(EntityDamageEvent event) {
        UUID shopId = getShopId(event.getEntity());
        if (shopId != null) {
            event.setCancelled(true);
        }
    }
}
