package com.outlaweco.shop;

import com.outlaweco.OutlawEconomyPlugin;
import com.outlaweco.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopManager implements CommandExecutor, TabCompleter, Listener {

    private static final String PLAYER_PERMISSION = "outlaweco.use";
    private static final String ADMIN_PERMISSION = "outlawecoadmin.use";
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00");

    private final OutlawEconomyPlugin plugin;
    private final EconomyManager economyManager;
    private final ShopTemplateManager templateManager;
    private final File shopsFile;
    private final FileConfiguration shopsConfig;
    private final NamespacedKey shopKey;
    private final Map<UUID, Shop> shops = new HashMap<>();

    public ShopManager(OutlawEconomyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.templateManager = new ShopTemplateManager(plugin);
        this.shopKey = new NamespacedKey(plugin, "shop-id");
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            try {
                File parent = shopsFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
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
            String templateKey = shopsConfig.getString(key + ".template");
            if (templateKey == null) {
                templateKey = shopsConfig.getString(key + ".type");
            }
            if (templateKey == null) {
                plugin.getLogger().warning("Boutique " + key + " sans template.");
                continue;
            }
            ShopTemplate template = templateManager.getTemplate(templateKey);
            if (template == null) {
                plugin.getLogger().warning("Template inconnu '" + templateKey + "' pour la boutique " + key);
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
            Shop shop = new Shop(id, template.getKey(), loc);
            spawnShopEntity(shop, template);
            shops.put(id, shop);
        }
    }

    private void spawnShopEntity(Shop shop, ShopTemplate template) {
        Villager villager = shop.spawn(template.getDisplayName());
        villager.setVillagerLevel(5);
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(shopKey, PersistentDataType.STRING, shop.getId().toString());
    }

    private void saveShop(Shop shop) {
        String base = shop.getId().toString();
        Location loc = shop.getLocation();
        shopsConfig.set(base + ".template", shop.getTemplateKey());
        shopsConfig.set(base + ".type", null);
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
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open":
                return handleOpenCommand(sender, args);
            case "templates":
                return handleTemplatesCommand(sender);
            case "create":
                return handleCreateCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender);
            case "list":
                return handleListCommand(sender, args);
            case "add":
                return handleAddItemCommand(sender, args);
            case "removeitem":
                return handleRemoveItemCommand(sender, args);
            case "reloadtemplates":
                return handleReloadTemplates(sender);
            default:
                sender.sendMessage("§cSous-commande inconnue.");
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleOpenCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!sender.hasPermission(PLAYER_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission d'ouvrir les boutiques.");
            return true;
        }
        int templateIndex = 1;
        if (args.length >= 2 && "shop".equalsIgnoreCase(args[1])) {
            templateIndex = 2;
        }
        if (args.length <= templateIndex) {
            sender.sendMessage("§cUtilisation: /shop open [shop] <nom>");
            listTemplates(sender);
            return true;
        }
        String templateKey = args[templateIndex];
        ShopTemplate template = templateManager.getTemplate(templateKey);
        if (template == null) {
            sender.sendMessage("§cAucune boutique trouvée pour '" + templateKey + "'.");
            listTemplates(sender);
            return true;
        }
        openTemplate(player, template);
        return true;
    }

    private boolean handleTemplatesCommand(CommandSender sender) {
        if (!sender.hasPermission(PLAYER_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        listTemplates(sender);
        return true;
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUtilisation: /shop create [shop] <template>");
            return true;
        }
        int templateIndex = args[1].equalsIgnoreCase("shop") ? 2 : 1;
        if (args.length <= templateIndex) {
            sender.sendMessage("§cUtilisation: /shop create [shop] <template>");
            return true;
        }
        String templateKey = args[templateIndex];
        ShopTemplate template = templateManager.getTemplate(templateKey);
        if (template == null) {
            sender.sendMessage("§cTemplate inconnu '" + templateKey + "'. Utilisez /shop templates pour la liste.");
            return true;
        }
        createShop(player, template);
        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        removeShop(player);
        return true;
    }

    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("templates")) {
            listTemplates(sender);
        } else {
            listShops(sender);
        }
        return true;
    }

    private boolean handleAddItemCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 6 || !args[1].equalsIgnoreCase("itemshop")) {
            sender.sendMessage("§cUtilisation: /shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]");
            return true;
        }
        String templateKey = args[2];
        String materialName = args[3];
        Material material = Material.matchMaterial(materialName, true);
        if (material == null) {
            sender.sendMessage("§cMatériel inconnu: " + materialName);
            return true;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cQuantité invalide: " + args[4]);
            return true;
        }
        double buyPrice;
        try {
            buyPrice = Double.parseDouble(args[5]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cPrix d'achat invalide: " + args[5]);
            return true;
        }
        double sellPrice = buyPrice;
        if (args.length >= 7) {
            try {
                sellPrice = Double.parseDouble(args[6]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cPrix de vente invalide: " + args[6]);
                return true;
            }
        }
        ShopOffer offer = new ShopOffer(material, quantity, buyPrice, sellPrice);
        templateManager.addOffer(templateKey, offer);
        sender.sendMessage("§aOffre ajoutée à la boutique §e" + templateKey + "§a.");
        return true;
    }

    private boolean handleRemoveItemCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUtilisation: /shop removeitem <template> <item>");
            return true;
        }
        String templateKey = args[1];
        String materialName = args[2];
        Material material = Material.matchMaterial(materialName, true);
        if (material == null) {
            sender.sendMessage("§cMatériel inconnu: " + materialName);
            return true;
        }
        boolean removed = templateManager.removeOffer(templateKey, material);
        if (removed) {
            sender.sendMessage("§aOffre supprimée de la boutique §e" + templateKey + "§a.");
        } else {
            sender.sendMessage("§cAucune offre trouvée pour cet objet dans §e" + templateKey + "§c.");
        }
        return true;
    }

    private boolean handleReloadTemplates(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        templateManager.reloadTemplates();
        for (Shop shop : shops.values()) {
            ShopTemplate template = templateManager.getTemplate(shop.getTemplateKey());
            if (template != null && shop.getVillager() != null) {
                shop.getVillager().setCustomName(template.getDisplayName());
            }
        }
        sender.sendMessage("§aTemplates rechargés depuis le fichier.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lBoutiques OutlawEconomy");
        if (sender.hasPermission(PLAYER_PERMISSION)) {
            sender.sendMessage("§e/shop open <boutique>§7 - ouvrir une boutique sans PNJ");
            sender.sendMessage("§e/shop templates§7 - voir les boutiques disponibles");
        }
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§e/shop create <template>§7 - créer un PNJ boutique");
            sender.sendMessage("§e/shop remove§7 - supprimer le PNJ ciblé");
            sender.sendMessage("§e/shop list [templates]§7 - lister les boutiques ou templates");
            sender.sendMessage("§e/shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]§7 - ajouter un objet");
            sender.sendMessage("§e/shop removeitem <template> <item>§7 - retirer un objet");
            sender.sendMessage("§e/shop reloadtemplates§7 - recharger les templates depuis le fichier");
        }
    }

    private void createShop(Player player, ShopTemplate template) {
        Location loc = player.getLocation();
        UUID id = UUID.randomUUID();
        Shop shop = new Shop(id, template.getKey(), loc);
        spawnShopEntity(shop, template);
        shops.put(id, shop);
        saveShop(shop);
        player.sendMessage("§aBoutique créée: §e" + template.getDisplayName());
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

    private void listShops(CommandSender sender) {
        if (shops.isEmpty()) {
            sender.sendMessage("§7Aucune boutique créée.");
            return;
        }
        sender.sendMessage("§6Boutiques disponibles:");
        for (Shop shop : shops.values()) {
            ShopTemplate template = templateManager.getTemplate(shop.getTemplateKey());
            String display = template != null ? template.getDisplayName() : shop.getTemplateKey();
            Location loc = shop.getLocation();
            sender.sendMessage("§e- " + ChatColor.stripColor(display) + " §7@ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    private void listTemplates(CommandSender sender) {
        List<ShopTemplate> templates = new ArrayList<>(templateManager.getTemplates());
        if (templates.isEmpty()) {
            sender.sendMessage("§7Aucun template de boutique n'est défini.");
            return;
        }
        sender.sendMessage("§6Templates disponibles:");
        for (ShopTemplate template : templates) {
            sender.sendMessage("§e- " + template.getKey() + " §7(" + ChatColor.stripColor(template.getDisplayName()) + ")");
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
        if (args.length == 0) {
            return Collections.emptyList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("open");
            base.add("templates");
            base.add("create");
            base.add("remove");
            base.add("list");
            base.add("add");
            base.add("removeitem");
            base.add("reloadtemplates");
            return base.stream().filter(s -> s.startsWith(sub)).collect(Collectors.toList());
        }
        if (sub.equals("open")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("shop");
                suggestions.addAll(templateManager.getTemplateKeys());
                return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("shop")) {
                return templateManager.getTemplateKeys().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        } else if (sub.equals("create")) {
            if (args.length == 2) {
                return Collections.singletonList("shop");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("shop")) {
                return templateManager.getTemplateKeys().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        } else if (sub.equals("add")) {
            if (args.length == 2) {
                return Collections.singletonList("itemshop");
            }
            if (args.length == 3) {
                return templateManager.getTemplateKeys().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (args.length == 4) {
                return Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(name -> name.startsWith(args[3].toUpperCase(Locale.ROOT)))
                        .limit(50)
                        .collect(Collectors.toList());
            }
        } else if (sub.equals("removeitem")) {
            if (args.length == 2) {
                return templateManager.getTemplateKeys().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                return Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(name -> name.startsWith(args[2].toUpperCase(Locale.ROOT)))
                        .limit(50)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
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
        Player player = event.getPlayer();
        if (!player.hasPermission(PLAYER_PERMISSION)) {
            player.sendMessage("§cVous n'avez pas la permission d'ouvrir les boutiques.");
            return;
        }
        ShopTemplate template = templateManager.getTemplate(shop.getTemplateKey());
        if (template == null) {
            player.sendMessage("§cCette boutique n'est plus disponible.");
            return;
        }
        openTemplate(player, template);
    }

    private void openTemplate(Player player, ShopTemplate template) {
        List<ShopOffer> offers = template.getOffers();
        if (offers.isEmpty()) {
            player.sendMessage("§cCette boutique ne contient aucun objet.");
            return;
        }
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(offers.size() / 9.0)));
        int size = rows * 9;
        Inventory inventory = Bukkit.createInventory(new ShopInventoryHolder(template.getKey()), size, template.getDisplayName());
        for (int i = 0; i < offers.size() && i < inventory.getSize(); i++) {
            ShopOffer offer = offers.get(i);
            ItemStack stack = offer.createItemStack();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + formatMaterial(offer.getMaterial()));
                List<String> lore = new ArrayList<>();
                lore.add("§7Quantité: §f" + offer.getAmount());
                lore.add("§aAchat: §e" + PRICE_FORMAT.format(offer.getBuyPrice()));
                lore.add("§cVente: §e" + PRICE_FORMAT.format(offer.getSellPrice()));
                lore.add("§7Clique gauche pour acheter");
                lore.add("§7Clique droit pour vendre");
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(i, stack);
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
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopTemplate template = templateManager.getTemplate(holder.getTemplateKey());
        if (template == null) {
            player.closeInventory();
            player.sendMessage("§cCette boutique n'est plus disponible.");
            return;
        }
        if (!player.hasPermission(PLAYER_PERMISSION)) {
            player.sendMessage("§cVous n'avez pas la permission d'interagir avec les boutiques.");
            return;
        }
        int slot = event.getRawSlot();
        List<ShopOffer> offers = template.getOffers();
        if (slot >= offers.size()) {
            return;
        }
        ShopOffer offer = offers.get(slot);
        if (event.isLeftClick()) {
            handleBuy(player, offer);
        } else if (event.isRightClick()) {
            handleSell(player, offer);
        }
    }

    private void handleBuy(Player player, ShopOffer offer) {
        double price = offer.getBuyPrice();
        if (!economyManager.withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cPas assez d'argent.");
            return;
        }
        ItemStack stack = offer.createItemStack();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.sendMessage("§aAchat effectué: §f" + offer.getAmount() + "x " + formatMaterial(offer.getMaterial()) + " §apour §e" + PRICE_FORMAT.format(price));
    }

    private void handleSell(Player player, ShopOffer offer) {
        if (offer.getSellPrice() <= 0) {
            player.sendMessage("§cCette boutique n'achète pas cet objet.");
            return;
        }
        ItemStack sample = new ItemStack(offer.getMaterial(), offer.getAmount());
        if (!player.getInventory().containsAtLeast(sample, offer.getAmount())) {
            player.sendMessage("§cVous n'avez pas les objets nécessaires.");
            return;
        }
        removeItems(player.getInventory(), offer.getMaterial(), offer.getAmount());
        economyManager.deposit(player.getUniqueId(), offer.getSellPrice());
        player.sendMessage("§aVente effectuée: §f" + offer.getAmount() + "x " + formatMaterial(offer.getMaterial()) + " §apour §e" + PRICE_FORMAT.format(offer.getSellPrice()));
    }

    private void removeItems(PlayerInventory inventory, Material material, int amount) {
        int amountToRemove = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && amountToRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int remove = Math.min(stack.getAmount(), amountToRemove);
            stack.setAmount(stack.getAmount() - remove);
            amountToRemove -= remove;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
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

    private String formatMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }
}
