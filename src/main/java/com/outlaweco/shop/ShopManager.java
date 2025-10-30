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
import org.bukkit.configuration.ConfigurationSection;
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

    private static final String PERMISSION_USE = "outlaweco.use";
    private static final String PERMISSION_ADMIN = "outlawecoadmin";

    private final OutlawEconomyPlugin plugin;
    private final EconomyManager economyManager;
    private final File shopsFile;
    private FileConfiguration shopsConfig;
    private final File templatesFile;
    private FileConfiguration templatesConfig;
    private final NamespacedKey shopKey;
    private final Map<UUID, Shop> shops = new HashMap<>();
    private final Map<String, ShopTemplate> templates = new HashMap<>();

    public ShopManager(OutlawEconomyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.shopKey = new NamespacedKey(plugin, "shop-id");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

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

        this.templatesFile = new File(plugin.getDataFolder(), "shop-templates.yml");
        if (!templatesFile.exists()) {
            plugin.saveResource("shop-templates.yml", false);
        }
        this.templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);

        loadTemplates();
        loadShops();
    }

    private void loadTemplates() {
        templates.clear();
        ConfigurationSection root = templatesConfig.getConfigurationSection("templates");
        if (root == null) {
            plugin.getLogger().warning("Aucun template de boutique trouvé dans shop-templates.yml");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String displayName = section.getString("display-name");
            ShopTemplate template = new ShopTemplate(key, displayName);

            List<Map<?, ?>> items = section.getMapList("items");
            List<ShopOffer> offers = new ArrayList<>();
            for (Map<?, ?> entry : items) {
                String materialName = Objects.toString(entry.get("item"), "");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("Matériau invalide '" + materialName + "' pour le template " + key);
                    continue;
                }
                int amount = 1;
                Object amountObj = entry.get("amount");
                if (amountObj instanceof Number number) {
                    amount = Math.max(1, number.intValue());
                }
                double buyPrice = getDouble(entry.get("buy-price"), 0.0);
                double sellPrice = getDouble(entry.get("sell-price"), 0.0);
                ItemStack stack = new ItemStack(material, amount);
                offers.add(new ShopOffer(stack, buyPrice, sellPrice));
            }
            template.setOffers(offers);
            templates.put(template.getKey(), template);
        }
    }

    private double getDouble(Object value, double def) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void loadShops() {
        shops.clear();
        for (String key : shopsConfig.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ID de boutique invalide: " + key);
                continue;
            }
            String templateKey = shopsConfig.getString(key + ".template");
            if (templateKey == null || templateKey.isBlank()) {
                plugin.getLogger().warning("Template manquant pour la boutique " + key);
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
            Shop shop = new Shop(id, templateKey, loc);
            spawnShopEntity(shop);
            shops.put(id, shop);
        }
    }

    private void spawnShopEntity(Shop shop) {
        ShopTemplate template = templates.get(shop.getTemplateKey());
        Villager villager = shop.spawn(template);
        if (villager == null) {
            return;
        }
        villager.setVillagerLevel(5);
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(shopKey, PersistentDataType.STRING, shop.getId().toString());
    }

    private void saveShop(Shop shop) {
        String base = shop.getId().toString();
        Location loc = shop.getLocation();
        shopsConfig.set(base + ".template", shop.getTemplateKey());
        shopsConfig.set(base + ".world", Objects.requireNonNull(loc.getWorld()).getName());
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

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open":
                return handleOpen(player, args);
            case "create":
                return handleCreate(player, args);
            case "remove":
                return handleRemove(player);
            case "list":
                return handleList(player, args);
            case "add":
                return handleAdd(player, args);
            case "removeitem":
                return handleRemoveItem(player, args);
            case "reloadtemplates":
                return handleReload(player);
            default:
                player.sendMessage("§cSous-commande inconnue.");
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6Boutique OutlawEco");
        if (player.hasPermission(PERMISSION_USE) || player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§e/shop open <template>§7 - ouvrir une boutique");
        }
        if (player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§e/shop create <template>§7 - créer une boutique PNJ");
            player.sendMessage("§e/shop remove§7 - supprimer la boutique ciblée");
            player.sendMessage("§e/shop list [templates]§7 - liste des boutiques ou templates");
            player.sendMessage("§e/shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]§7 - ajouter un objet");
            player.sendMessage("§e/shop removeitem <template> <item>§7 - retirer un objet");
            player.sendMessage("§e/shop reloadtemplates§7 - recharger les templates");
        }
    }

    private boolean handleOpen(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_USE) && !player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cMerci de préciser un template: " + String.join(", ", templates.keySet()));
            return true;
        }
        ShopTemplate template = templates.get(args[1].toLowerCase(Locale.ROOT));
        if (template == null) {
            player.sendMessage("§cTemplate introuvable.");
            return true;
        }
        openTemplate(player, template);
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cMerci de préciser un template: " + String.join(", ", templates.keySet()));
            return true;
        }
        String templateKey = args[1].toLowerCase(Locale.ROOT);
        ShopTemplate template = templates.get(templateKey);
        if (template == null) {
            player.sendMessage("§cTemplate introuvable.");
            return true;
        }
        createShop(player, templateKey);
        player.sendMessage("§aBoutique créée avec le template §e" + templateKey + "§a.");
        return true;
    }

    private boolean handleRemove(Player player) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        removeShop(player);
        return true;
    }

    private boolean handleList(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("templates")) {
            listTemplates(player);
        } else {
            listShops(player);
        }
        return true;
    }

    private boolean handleAdd(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 6 || !args[1].equalsIgnoreCase("itemshop")) {
            player.sendMessage("§cUsage: /shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]");
            return true;
        }
        String templateKey = args[2].toLowerCase(Locale.ROOT);
        ShopTemplate template = templates.get(templateKey);
        if (template == null) {
            player.sendMessage("§cTemplate introuvable.");
            return true;
        }
        Material material = Material.matchMaterial(args[3]);
        if (material == null) {
            player.sendMessage("§cMatériau invalide.");
            return true;
        }
        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[4]));
        } catch (NumberFormatException ex) {
            player.sendMessage("§cQuantité invalide.");
            return true;
        }
        double buyPrice;
        try {
            buyPrice = Double.parseDouble(args[5]);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cPrix d'achat invalide.");
            return true;
        }
        double sellPrice = 0.0;
        if (args.length >= 7) {
            try {
                sellPrice = Double.parseDouble(args[6]);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cPrix de vente invalide.");
                return true;
            }
        }

        template.addOffer(new ShopOffer(new ItemStack(material, amount), buyPrice, sellPrice));
        saveTemplates();
        player.sendMessage("§aObjet ajouté au template §e" + templateKey + "§a.");
        return true;
    }

    private boolean handleRemoveItem(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /shop removeitem <template> <item>");
            return true;
        }
        String templateKey = args[1].toLowerCase(Locale.ROOT);
        ShopTemplate template = templates.get(templateKey);
        if (template == null) {
            player.sendMessage("§cTemplate introuvable.");
            return true;
        }
        boolean removed = template.removeOffer(args[2]);
        if (!removed) {
            player.sendMessage("§cAucun objet ne correspond à ce matériau.");
            return true;
        }
        saveTemplates();
        player.sendMessage("§aObjet supprimé du template §e" + templateKey + "§a.");
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        this.templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        loadTemplates();
        updateVillagers();
        player.sendMessage("§aTemplates rechargés.");
        return true;
    }

    private void updateVillagers() {
        for (Shop shop : shops.values()) {
            ShopTemplate template = templates.get(shop.getTemplateKey());
            if (template == null) {
                continue;
            }
            Villager villager = shop.getVillager();
            if (villager != null) {
                villager.setCustomName(template.getDisplayName());
            }
        }
    }

    private void createShop(Player player, String templateKey) {
        Location loc = player.getLocation();
        UUID id = UUID.randomUUID();
        Shop shop = new Shop(id, templateKey, loc);
        spawnShopEntity(shop);
        shops.put(id, shop);
        saveShop(shop);
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
            ShopTemplate template = templates.get(shop.getTemplateKey());
            String display = template != null ? ChatColor.stripColor(template.getDisplayName()) : shop.getTemplateKey();
            player.sendMessage("§e- " + display + " §7@ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    private void listTemplates(Player player) {
        if (templates.isEmpty()) {
            player.sendMessage("§7Aucun template disponible.");
            return;
        }
        player.sendMessage("§6Templates disponibles:");
        for (ShopTemplate template : templates.values()) {
            player.sendMessage("§e- " + template.getKey() + "§7 (" + ChatColor.stripColor(template.getDisplayName()) + ")");
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

    private void openTemplate(Player player, ShopTemplate template) {
        Inventory inventory = Bukkit.createInventory(new ShopInventoryHolder(template.getKey(), template.getOffers()), 27, template.getDisplayName());
        List<ShopOffer> offers = template.getOffers();
        for (int i = 0; i < offers.size() && i < inventory.getSize(); i++) {
            ShopOffer offer = offers.get(i);
            ItemStack item = offer.item();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("§aAchat: §e" + String.format(Locale.US, "%.2f", offer.buyPrice()));
                if (offer.sellPrice() > 0) {
                    lore.add("§cVente: §e" + String.format(Locale.US, "%.2f", offer.sellPrice()));
                    lore.add("§7Clique gauche pour acheter");
                    lore.add("§7Clique droit pour vendre");
                } else {
                    lore.add("§7Clique gauche pour acheter");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }
        player.openInventory(inventory);
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
        if (!player.hasPermission(PERMISSION_USE) && !player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette boutique.");
            return;
        }
        ShopTemplate template = templates.get(shop.getTemplateKey());
        if (template == null) {
            player.sendMessage("§cTemplate introuvable pour cette boutique.");
            return;
        }
        openTemplate(player, template);
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
        List<ShopOffer> offers = holder.getOffers();
        int slot = event.getRawSlot();
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
        double price = offer.buyPrice();
        if (price > 0 && !economyManager.withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cPas assez d'argent.");
            return;
        }
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(offer.item());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        if (price > 0) {
            player.sendMessage("§aAchat effectué pour §e" + String.format(Locale.US, "%.2f", price));
        } else {
            player.sendMessage("§aAchat effectué.");
        }
    }

    private void handleSell(Player player, ShopOffer offer) {
        double sellPrice = offer.sellPrice();
        if (sellPrice <= 0) {
            player.sendMessage("§cCet objet ne peut pas être vendu.");
            return;
        }
        ItemStack itemToSell = offer.item();
        if (!player.getInventory().containsAtLeast(itemToSell, itemToSell.getAmount())) {
            player.sendMessage("§cVous n'avez pas les objets nécessaires.");
            return;
        }
        removeItems(player.getInventory(), itemToSell);
        economyManager.deposit(player.getUniqueId(), sellPrice);
        player.sendMessage("§aVente effectuée pour §e" + String.format(Locale.US, "%.2f", sellPrice));
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
                amountToRemove = 0;
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        boolean isAdmin = player.hasPermission(PERMISSION_ADMIN);
        boolean canUse = isAdmin || player.hasPermission(PERMISSION_USE);

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (canUse) {
                subcommands.add("open");
            }
            if (isAdmin) {
                subcommands.addAll(Arrays.asList("create", "remove", "list", "add", "removeitem", "reloadtemplates"));
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open") && canUse) {
                return filterByPrefix(templates.keySet(), args[1]);
            }
            if (isAdmin && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("removeitem"))) {
                return filterByPrefix(templates.keySet(), args[1]);
            }
            if (isAdmin && args[0].equalsIgnoreCase("list")) {
                return filterByPrefix(List.of("templates"), args[1]);
            }
            if (isAdmin && args[0].equalsIgnoreCase("add")) {
                return filterByPrefix(List.of("itemshop"), args[1]);
            }
        }

        if (args.length == 3 && isAdmin) {
            if (args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("itemshop")) {
                return filterByPrefix(templates.keySet(), args[2]);
            }
        }

        return List.of();
    }

    private List<String> filterByPrefix(Collection<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }

    private void saveTemplates() {
        YamlConfiguration config = new YamlConfiguration();
        for (ShopTemplate template : templates.values()) {
            String path = "templates." + template.getKey();
            String rawDisplay = template.getRawDisplayName();
            if (rawDisplay != null && !rawDisplay.isBlank()) {
                config.set(path + ".display-name", rawDisplay);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (ShopOffer offer : template.getOffers()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("item", offer.getMaterial().name());
                map.put("amount", offer.getAmount());
                map.put("buy-price", offer.buyPrice());
                map.put("sell-price", offer.sellPrice());
                items.add(map);
            }
            config.set(path + ".items", items);
        }
        try {
            config.save(templatesFile);
            this.templatesConfig = config;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer shop-templates.yml: " + e.getMessage());
        }
    }
}
