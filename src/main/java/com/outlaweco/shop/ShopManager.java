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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private static final int GENERAL_PAGE_SIZE = 30;
    private static final int PRICE_PAGE_SIZE = 45;

    private final OutlawEconomyPlugin plugin;
    private final EconomyManager economyManager;
    private final File shopsFile;
    private FileConfiguration shopsConfig;
    private final File templatesFile;
    private FileConfiguration templatesConfig;
    private final File generalShopFile;
    private FileConfiguration generalShopConfig;
    private final File pricesFile;
    private FileConfiguration pricesConfig;
    private final NamespacedKey shopKey;
    private final Map<UUID, Shop> shops = new HashMap<>();
    private final Map<String, ShopTemplate> templates = new HashMap<>();
    private final Map<UUID, GeneralShopListing> generalListings = new LinkedHashMap<>();
    private final Map<Material, Double> adminPrices = new HashMap<>();
    private final Map<UUID, PendingPriceInput> pendingPriceInputs = new HashMap<>();
    private final Map<UUID, PendingListingInput> pendingListingInputs = new HashMap<>();
    private final List<Material> selectableMaterials;

    public ShopManager(OutlawEconomyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.shopKey = new NamespacedKey(plugin, "shop-id");
        this.selectableMaterials = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(material -> material != Material.AIR)
                .sorted(Comparator.comparing(Material::name))
                .toList();

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

        this.generalShopFile = new File(plugin.getDataFolder(), "general-shop.yml");
        if (!generalShopFile.exists()) {
            try {
                generalShopFile.getParentFile().mkdirs();
                generalShopFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer general-shop.yml: " + e.getMessage());
            }
        }
        this.generalShopConfig = YamlConfiguration.loadConfiguration(generalShopFile);

        this.pricesFile = new File(plugin.getDataFolder(), "item-prices.yml");
        if (!pricesFile.exists()) {
            try {
                pricesFile.getParentFile().mkdirs();
                pricesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer item-prices.yml: " + e.getMessage());
            }
        }
        this.pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);

        loadTemplates();
        loadShops();
        loadGeneralStore();
        loadAdminPrices();
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
                parseOffer(key, offers, entry);
            }
            template.setOffers(offers);

            ConfigurationSection categoriesSection = section.getConfigurationSection("categories");
            if (categoriesSection != null) {
                List<ShopCategory> categories = new ArrayList<>();
                for (String categoryKey : categoriesSection.getKeys(false)) {
                    ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryKey);
                    if (categorySection == null) {
                        continue;
                    }
                    ShopCategory category = new ShopCategory(categoryKey, categorySection.getString("display-name"));
                    String iconName = categorySection.getString("icon");
                    if (iconName != null && !iconName.isBlank()) {
                        Material iconMaterial = Material.matchMaterial(iconName);
                        if (iconMaterial == null) {
                            plugin.getLogger().warning("Icône invalide '" + iconName + "' pour la catégorie " + categoryKey +
                                    " du template " + key);
                        } else {
                            category.setIconMaterial(iconMaterial);
                        }
                    }
                    List<Map<?, ?>> categoryItems = categorySection.getMapList("items");
                    List<ShopOffer> categoryOffers = new ArrayList<>();
                    for (Map<?, ?> entry : categoryItems) {
                        parseOffer(key + ":" + categoryKey, categoryOffers, entry);
                    }
                    category.setOffers(categoryOffers);
                    categories.add(category);
                }
                template.setCategories(categories);
            }
            templates.put(template.getKey(), template);
        }
    }

    private void parseOffer(String context, List<ShopOffer> offers, Map<?, ?> entry) {
        String materialName = Objects.toString(entry.get("item"), "");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Matériau invalide '" + materialName + "' pour " + context);
            return;
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

    private void loadGeneralStore() {
        generalListings.clear();
        ConfigurationSection section = generalShopConfig.getConfigurationSection("listings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID listingId;
            try {
                listingId = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("ID d'annonce invalide: " + key);
                continue;
            }
            String sellerIdRaw = section.getString(key + ".seller");
            if (sellerIdRaw == null) {
                plugin.getLogger().warning("Annonce " + key + " sans vendeur.");
                continue;
            }
            UUID sellerId;
            try {
                sellerId = UUID.fromString(sellerIdRaw);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Vendeur invalide pour l'annonce " + key);
                continue;
            }
            double price = section.getDouble(key + ".price");
            if (price <= 0) {
                continue;
            }
            ItemStack item = section.getItemStack(key + ".item");
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String sellerName = section.getString(key + ".seller-name", "Inconnu");
            generalListings.put(listingId, new GeneralShopListing(listingId, sellerId, sellerName, item, price));
        }
    }

    private void loadAdminPrices() {
        adminPrices.clear();
        ConfigurationSection section = pricesConfig.getConfigurationSection("prices");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Matériau inconnu pour le prix: " + key);
                continue;
            }
            double price = section.getDouble(key);
            if (price > 0) {
                adminPrices.put(material, price);
            }
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
            case "setting":
                return handleSetting(player, args);
            default:
                player.sendMessage("§cSous-commande inconnue.");
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6Boutique OutlawEco");
        boolean isAdmin = player.hasPermission(PERMISSION_ADMIN);
        boolean canUse = isAdmin || player.hasPermission(PERMISSION_USE);

        if (canUse) {
            player.sendMessage("§e--- Utilisateur ---");
            player.sendMessage("§e/shop open <template>§7 - ouvrir une boutique PNJ");
            player.sendMessage("§e/shop open general§7 - ouvrir le magasin général (alias: /shop open shop general)");
        }

        if (isAdmin) {
            player.sendMessage("§e--- Admin ---");
            player.sendMessage("§e/shop create <template>§7 - créer une boutique PNJ");
            player.sendMessage("§e/shop remove§7 - supprimer la boutique ciblée");
            player.sendMessage("§e/shop list [templates]§7 - liste des boutiques ou templates");
            player.sendMessage("§e/shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]§7 - ajouter un objet");
            player.sendMessage("§e/shop removeitem <template> <item>§7 - retirer un objet");
            player.sendMessage("§e/shop reloadtemplates§7 - recharger les templates");
            player.sendMessage("§e/shop setting price§7 - définir les prix via le menu créatif");
        }
    }

    private boolean handleOpen(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_USE) && !player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cMerci de préciser un template ou 'general'.");
            return true;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        if (target.equals("shop") && args.length >= 3) {
            target = args[2].toLowerCase(Locale.ROOT);
        } else if (target.equals("shop")) {
            player.sendMessage("§cUsage: /shop open shop general");
            return true;
        }
        if (target.equals("general")) {
            openGeneralStore(player, 0);
            return true;
        }
        ShopTemplate template = templates.get(target);
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
        if (template.hasCategories()) {
            player.sendMessage("§cAjoutez les objets directement dans le fichier de configuration pour cette boutique à onglets.");
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
        if (template.hasCategories()) {
            player.sendMessage("§cRetirez les objets via la configuration pour cette boutique à onglets.");
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

    private boolean handleSetting(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("price")) {
            player.sendMessage("§cUsage: /shop setting price");
            return true;
        }
        pendingPriceInputs.remove(player.getUniqueId());
        openPriceSelector(player, 0);
        player.sendMessage("§7Sélectionnez un objet puis indiquez son prix dans le chat. Tapez 'cancel' pour annuler.");
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

    private void saveGeneralStore() {
        YamlConfiguration config = new YamlConfiguration();
        for (GeneralShopListing listing : generalListings.values()) {
            String base = "listings." + listing.getId();
            config.set(base + ".seller", listing.getSellerId().toString());
            config.set(base + ".seller-name", listing.getSellerName());
            config.set(base + ".price", listing.getPrice());
            config.set(base + ".item", listing.getItem());
        }
        try {
            config.save(generalShopFile);
            this.generalShopConfig = config;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer general-shop.yml: " + e.getMessage());
        }
    }

    private void saveAdminPrices() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<Material, Double> entry : adminPrices.entrySet()) {
            config.set("prices." + entry.getKey().name(), entry.getValue());
        }
        try {
            config.save(pricesFile);
            this.pricesConfig = config;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer item-prices.yml: " + e.getMessage());
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
        if (template.hasCategories()) {
            openCategorySelection(player, template);
        } else {
            openOffersInventory(player, template, null, false);
        }
    }

    private void openCategorySelection(Player player, ShopTemplate template) {
        List<ShopCategory> categories = template.getCategories();
        if (categories.isEmpty()) {
            openOffersInventory(player, template, null, false);
            return;
        }
        int size = calculateInventorySize(Math.max(1, categories.size()));
        int maxSlots = Math.min(categories.size(), size);
        List<ShopCategory> visibleCategories = new ArrayList<>();
        for (int i = 0; i < maxSlots; i++) {
            visibleCategories.add(categories.get(i));
        }
        ShopInventoryHolder holder = ShopInventoryHolder.forCategories(template.getKey(), visibleCategories);
        Inventory inventory = Bukkit.createInventory(holder, size, template.getDisplayName());
        for (int i = 0; i < visibleCategories.size(); i++) {
            inventory.setItem(i, visibleCategories.get(i).createIconItem());
        }
        player.openInventory(inventory);
    }

    private void openOffersInventory(Player player, ShopTemplate template, ShopCategory category, boolean allowBack) {
        List<ShopOffer> offers = category != null ? category.getOffers() : template.getOffers();
        boolean backButton = allowBack && template.hasCategories();
        int extraSlots = backButton ? 1 : 0;
        int size = calculateInventorySize(Math.max(1, offers.size() + extraSlots));
        int maxSlots = backButton ? size - 1 : size;
        List<ShopOffer> visibleOffers = new ArrayList<>();
        for (int i = 0; i < offers.size() && i < maxSlots; i++) {
            visibleOffers.add(offers.get(i));
        }
        ShopInventoryHolder holder = ShopInventoryHolder.forOffers(template.getKey(),
                category != null ? category.getKey() : null, visibleOffers, backButton);
        String title = category != null ? category.getDisplayName() : template.getDisplayName();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        for (int i = 0; i < visibleOffers.size(); i++) {
            ShopOffer offer = visibleOffers.get(i);
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
        if (backButton) {
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta meta = back.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eRetour");
                meta.setLore(List.of("§7Revenir à la sélection de catégories."));
                back.setItemMeta(meta);
            }
            inventory.setItem(size - 1, back);
        }
        player.openInventory(inventory);
    }

    private void openPriceSelector(Player player, int page) {
        if (selectableMaterials.isEmpty()) {
            player.sendMessage("§cAucun item disponible.");
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) selectableMaterials.size() / PRICE_PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * PRICE_PAGE_SIZE;
        int end = Math.min(start + PRICE_PAGE_SIZE, selectableMaterials.size());
        List<Material> pageMaterials = selectableMaterials.subList(start, end);

        ShopInventoryHolder holder = ShopInventoryHolder.forPriceSelector(pageMaterials, safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                "§ePrix des items (" + (safePage + 1) + "/" + totalPages + ")");

        for (int i = 0; i < pageMaterials.size(); i++) {
            Material material = pageMaterials.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                Double price = adminPrices.get(material);
                if (price != null) {
                    lore.add("§7Prix actuel: §e" + String.format(Locale.US, "%.2f", price));
                } else {
                    lore.add("§7Aucun prix défini");
                }
                lore.add("§eClique pour définir un prix");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        if (safePage > 0) {
            ItemStack previous = new ItemStack(Material.ARROW);
            ItemMeta meta = previous.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§ePage précédente");
                previous.setItemMeta(meta);
            }
            inventory.setItem(45, previous);
        }
        if (safePage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§ePage suivante");
                next.setItemMeta(meta);
            }
            inventory.setItem(53, next);
        }

        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§fDéfinir un prix");
            infoMeta.setLore(List.of(
                    "§7Cliquez sur un item pour choisir",
                    "§7puis entrez le prix dans le chat.",
                    "§7Tapez 'cancel' pour annuler."));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(49, info);

        player.openInventory(inventory);
    }

    private void openGeneralStore(Player player, int page) {
        List<GeneralShopListing> allListings = new ArrayList<>(generalListings.values());
        int totalPages = Math.max(1, (int) Math.ceil((double) allListings.size() / GENERAL_PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * GENERAL_PAGE_SIZE;
        int end = Math.min(start + GENERAL_PAGE_SIZE, allListings.size());
        List<GeneralShopListing> pageListings = allListings.subList(start, end);

        ShopInventoryHolder holder = ShopInventoryHolder.forGeneralStore(pageListings, safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                "§2Magasin général (" + (safePage + 1) + "/" + totalPages + ")");

        for (int i = 0; i < pageListings.size(); i++) {
            GeneralShopListing listing = pageListings.get(i);
            ItemStack item = listing.getItem();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("§7Vendeur: §f" + listing.getSellerName());
                lore.add("§aPrix: §e" + String.format(Locale.US, "%.2f", listing.getPrice()));
                if (listing.getSellerId().equals(player.getUniqueId())) {
                    lore.add("§7Clique pour récupérer l'objet");
                } else {
                    lore.add("§7Clique pour acheter");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        if (safePage > 0) {
            ItemStack previous = new ItemStack(Material.ARROW);
            ItemMeta meta = previous.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§ePage précédente");
                previous.setItemMeta(meta);
            }
            inventory.setItem(45, previous);
        }
        if (safePage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§ePage suivante");
                next.setItemMeta(meta);
            }
            inventory.setItem(53, next);
        }

        ItemStack add = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = add.getItemMeta();
        if (addMeta != null) {
            addMeta.setDisplayName("§aMettre en vente l'objet en main");
            addMeta.setLore(List.of(
                    "§7Retire la pile tenue en main.",
                    "§7Entrez ensuite le prix dans le chat.",
                    "§7Tapez 'cancel' pour annuler."));
            add.setItemMeta(addMeta);
        }
        inventory.setItem(49, add);

        player.openInventory(inventory);
    }

    private int calculateInventorySize(int contentCount) {
        return 54;
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ShopInventoryType type = holder.getType();
        if (type == ShopInventoryType.CATEGORIES) {
            ShopTemplate template = templates.get(holder.getTemplateKey());
            if (template == null) {
                player.closeInventory();
                return;
            }
            List<ShopCategory> categories = holder.getCategories();
            if (slot >= categories.size()) {
                return;
            }
            ShopCategory category = categories.get(slot);
            openOffersInventory(player, template, category, true);
            return;
        }

        if (type == ShopInventoryType.OFFERS) {
            ShopTemplate template = templates.get(holder.getTemplateKey());
            if (template == null) {
                player.closeInventory();
                return;
            }
            if (holder.hasBackButton() && slot == event.getInventory().getSize() - 1) {
                openCategorySelection(player, template);
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) {
                return;
            }
            List<ShopOffer> offers = holder.getOffers();
            if (slot >= offers.size()) {
                return;
            }
            ShopOffer offer = offers.get(slot);
            if (event.isLeftClick()) {
                handleBuy(player, offer);
            } else if (event.isRightClick()) {
                handleSell(player, offer);
            }
            return;
        }

        if (type == ShopInventoryType.PRICE_SELECTOR) {
            if (slot == 45 && holder.getPage() > 0) {
                openPriceSelector(player, holder.getPage() - 1);
                return;
            }
            if (slot == 53 && holder.getPage() < holder.getTotalPages() - 1) {
                openPriceSelector(player, holder.getPage() + 1);
                return;
            }
            if (slot >= holder.getMaterials().size()) {
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) {
                return;
            }
            Material material = holder.getMaterials().get(slot);
            player.closeInventory();
            pendingPriceInputs.put(player.getUniqueId(), new PendingPriceInput(material, holder.getPage()));
            player.sendMessage("§eEntrez le prix pour §6" + formatMaterialName(material) + "§e dans le chat (ou 'cancel').");
            return;
        }

        if (type == ShopInventoryType.GENERAL_STORE) {
            if (slot == 45 && holder.getPage() > 0) {
                openGeneralStore(player, holder.getPage() - 1);
                return;
            }
            if (slot == 53 && holder.getPage() < holder.getTotalPages() - 1) {
                openGeneralStore(player, holder.getPage() + 1);
                return;
            }
            if (slot == 49) {
                handleStartListing(player, holder.getPage());
                return;
            }
            if (slot >= holder.getGeneralListings().size()) {
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) {
                return;
            }
            handleListingInteraction(player, holder.getGeneralListings().get(slot), holder.getPage());
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

    private void handleStartListing(Player player, int page) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.sendMessage("§cTenez l'objet à vendre dans votre main.");
            return;
        }
        PendingListingInput existing = pendingListingInputs.remove(player.getUniqueId());
        if (existing != null) {
            giveItemBack(player, existing.item());
        }
        ItemStack item = inHand.clone();
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();
        pendingListingInputs.put(player.getUniqueId(), new PendingListingInput(item, page));
        player.closeInventory();
        player.sendMessage("§eEntrez le prix pour §6" + describeItem(item) + "§e dans le chat (ou 'cancel').");
    }

    private void handleListingInteraction(Player player, GeneralShopListing listing, int currentPage) {
        GeneralShopListing current = generalListings.get(listing.getId());
        if (current == null) {
            player.sendMessage("§cCette annonce n'est plus disponible.");
            openGeneralStore(player, normalizeGeneralPage(currentPage));
            return;
        }
        if (current.getSellerId().equals(player.getUniqueId())) {
            generalListings.remove(current.getId());
            saveGeneralStore();
            ItemStack item = current.getItem();
            giveItemBack(player, item);
            player.sendMessage("§aAnnonce retirée. L'objet vous a été rendu.");
            openGeneralStore(player, normalizeGeneralPage(currentPage));
            return;
        }
        double price = current.getPrice();
        if (!economyManager.withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cPas assez d'argent.");
            return;
        }
        ItemStack item = current.getItem();
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(remain -> player.getWorld().dropItemNaturally(player.getLocation(), remain));
        }
        economyManager.deposit(current.getSellerId(), price);
        generalListings.remove(current.getId());
        saveGeneralStore();
        String itemName = describeItem(item);
        player.sendMessage("§aAchat effectué pour §e" + formatPrice(price));
        Player seller = Bukkit.getPlayer(current.getSellerId());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("§aVotre annonce pour §6" + itemName + "§a a été vendue pour §e" + formatPrice(price) + "§a.");
        }
        openGeneralStore(player, normalizeGeneralPage(currentPage));
    }

    private int normalizeGeneralPage(int requestedPage) {
        int totalPages = Math.max(1, (int) Math.ceil((double) generalListings.size() / GENERAL_PAGE_SIZE));
        return Math.max(0, Math.min(requestedPage, totalPages - 1));
    }

    private void giveItemBack(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(remain -> player.getWorld().dropItemNaturally(player.getLocation(), remain));
        }
        player.updateInventory();
    }

    private String describeItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String base = formatMaterialName(item.getType());
        if (meta != null && meta.hasDisplayName()) {
            base = ChatColor.stripColor(meta.getDisplayName());
        }
        return base + " x" + item.getAmount();
    }

    private String formatMaterialName(Material material) {
        String lower = material.name().toLowerCase(Locale.ROOT);
        return Arrays.stream(lower.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String formatPrice(double price) {
        return String.format(Locale.US, "%.2f", price);
    }

    private boolean isCancelMessage(String message) {
        return message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("annuler");
    }

    private void handlePendingPriceChat(Player player, UUID uuid, PendingPriceInput pending, String message) {
        if (isCancelMessage(message)) {
            pendingPriceInputs.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§eDéfinition du prix annulée.");
                openPriceSelector(player, pending.page());
            });
            return;
        }
        double value;
        try {
            value = Double.parseDouble(message.replace(',', '.'));
        } catch (NumberFormatException ex) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cValeur invalide. Entrez un nombre."));
            return;
        }
        if (value <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cLe prix doit être supérieur à 0."));
            return;
        }
        pendingPriceInputs.remove(uuid);
        double price = value;
        Bukkit.getScheduler().runTask(plugin, () -> {
            adminPrices.put(pending.material(), price);
            saveAdminPrices();
            player.sendMessage("§aPrix défini pour §6" + formatMaterialName(pending.material()) + "§a: §e" + formatPrice(price));
            openPriceSelector(player, pending.page());
        });
    }

    private void handlePendingListingChat(Player player, UUID uuid, PendingListingInput pending, String message) {
        if (isCancelMessage(message)) {
            PendingListingInput removed = pendingListingInputs.remove(uuid);
            if (removed != null) {
                ItemStack item = removed.item();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    giveItemBack(player, item);
                    player.sendMessage("§eMise en vente annulée. L'objet a été rendu.");
                    openGeneralStore(player, normalizeGeneralPage(removed.page()));
                });
            }
            return;
        }
        double value;
        try {
            value = Double.parseDouble(message.replace(',', '.'));
        } catch (NumberFormatException ex) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cValeur invalide. Entrez un nombre."));
            return;
        }
        if (value <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cLe prix doit être supérieur à 0."));
            return;
        }
        PendingListingInput removed = pendingListingInputs.remove(uuid);
        if (removed == null) {
            return;
        }
        double price = value;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack item = removed.item();
            UUID listingId = UUID.randomUUID();
            GeneralShopListing listing = new GeneralShopListing(listingId, uuid, player.getName(), item, price);
            generalListings.put(listingId, listing);
            saveGeneralStore();
            player.sendMessage("§aObjet mis en vente pour §e" + formatPrice(price) + "§a.");
            openGeneralStore(player, normalizeGeneralPage(removed.page()));
        });
    }

    @EventHandler
    public void onShopDamage(EntityDamageEvent event) {
        UUID shopId = getShopId(event.getEntity());
        if (shopId != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PendingPriceInput pricePending = pendingPriceInputs.get(uuid);
        PendingListingInput listingPending = pendingListingInputs.get(uuid);
        if (pricePending == null && listingPending == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        if (pricePending != null) {
            handlePendingPriceChat(player, uuid, pricePending, message);
            return;
        }
        if (listingPending != null) {
            handlePendingListingChat(player, uuid, listingPending, message);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingListingInput listing = pendingListingInputs.remove(uuid);
        if (listing != null) {
            giveItemBack(event.getPlayer(), listing.item());
        }
        pendingPriceInputs.remove(uuid);
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
                subcommands.addAll(Arrays.asList("create", "remove", "list", "add", "removeitem", "reloadtemplates", "setting"));
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open") && canUse) {
                List<String> options = new ArrayList<>(templates.keySet());
                options.add("general");
                options.add("shop");
                return filterByPrefix(options, args[1]);
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
            if (isAdmin && args[0].equalsIgnoreCase("setting")) {
                return filterByPrefix(List.of("price"), args[1]);
            }
        }

        if (args.length == 3) {
            if (isAdmin && args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("itemshop")) {
                return filterByPrefix(templates.keySet(), args[2]);
            }
            if (args[0].equalsIgnoreCase("open") && canUse && args[1].equalsIgnoreCase("shop")) {
                return filterByPrefix(List.of("general"), args[2]);
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

    public void shutdown() {
        saveGeneralStore();
        saveAdminPrices();
    }

    private record PendingPriceInput(Material material, int page) {
    }

    private record PendingListingInput(ItemStack item, int page) {
        private PendingListingInput {
            item = Objects.requireNonNull(item, "item").clone();
        }
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
            if (template.hasCategories()) {
                for (ShopCategory category : template.getCategories()) {
                    String categoryPath = path + ".categories." + category.getKey();
                    String categoryRaw = category.getRawDisplayName();
                    if (categoryRaw != null && !categoryRaw.isBlank()) {
                        config.set(categoryPath + ".display-name", categoryRaw);
                    }
                    if (category.hasCustomIcon()) {
                        config.set(categoryPath + ".icon", category.getIconMaterial().name());
                    }
                    List<Map<String, Object>> categoryItems = new ArrayList<>();
                    for (ShopOffer offer : category.getOffers()) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("item", offer.getMaterial().name());
                        map.put("amount", offer.getAmount());
                        map.put("buy-price", offer.buyPrice());
                        map.put("sell-price", offer.sellPrice());
                        categoryItems.add(map);
                    }
                    config.set(categoryPath + ".items", categoryItems);
                }
            }
        }
        try {
            config.save(templatesFile);
            this.templatesConfig = config;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer shop-templates.yml: " + e.getMessage());
        }
    }
}
