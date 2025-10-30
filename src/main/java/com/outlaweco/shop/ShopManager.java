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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
    private static final int INVENTORY_SIZE = 54;
    private static final int CATEGORY_PREVIOUS_SLOT = 0;
    private static final int CATEGORY_NEXT_SLOT = 8;
    private static final int[] CATEGORY_DISPLAY_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] OFFER_SLOTS;
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    static {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) { // rows 1-4 correspond to slots 9-44
            for (int col = 0; col < 9; col++) {
                slots.add(row * 9 + col);
            }
        }
        OFFER_SLOTS = slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private final OutlawEconomyPlugin plugin;
    private final EconomyManager economyManager;
    private final File shopsFile;
    private FileConfiguration shopsConfig;
    private final File templatesFile;
    private FileConfiguration templatesConfig;
    private final NamespacedKey shopKey;
    private final Map<UUID, Shop> shops = new HashMap<>();
    private final Map<String, ShopTemplate> templates = new LinkedHashMap<>();

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
            String iconName = section.getString("icon");
            if (iconName != null && !iconName.isBlank()) {
                Material iconMaterial = Material.matchMaterial(iconName);
                if (iconMaterial != null) {
                    template.setIcon(iconMaterial, iconName);
                } else {
                    plugin.getLogger().warning("Icône invalide '" + iconName + "' pour le template " + key);
                }
            }

            List<ShopCategory> categories = new ArrayList<>();

            List<Map<?, ?>> rootItems = section.getMapList("items");
            if (rootItems != null && !rootItems.isEmpty()) {
                ShopCategory defaultCategory = new ShopCategory("default", null);
                defaultCategory.setOffers(parseOffers(key, "default", rootItems));
                categories.add(defaultCategory);
            }

            ConfigurationSection categoriesSection = section.getConfigurationSection("categories");
            if (categoriesSection != null) {
                for (String categoryKey : categoriesSection.getKeys(false)) {
                    ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryKey);
                    if (categorySection == null) {
                        continue;
                    }
                    ShopCategory category = new ShopCategory(categoryKey, categorySection.getString("display-name"));
                    String categoryIconName = categorySection.getString("icon");
                    if (categoryIconName != null && !categoryIconName.isBlank()) {
                        Material categoryIcon = Material.matchMaterial(categoryIconName);
                        if (categoryIcon != null) {
                            category.setIcon(categoryIcon, categoryIconName);
                        } else {
                            plugin.getLogger().warning("Icône invalide '" + categoryIconName + "' pour la catégorie " + categoryKey + " du template " + key);
                        }
                    }
                    List<Map<?, ?>> categoryItems = categorySection.getMapList("items");
                    category.setOffers(parseOffers(key, categoryKey, categoryItems));
                    categories.add(category);
                }
            }

            template.setCategories(categories);
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

    private List<ShopOffer> parseOffers(String templateKey, String categoryKey, List<Map<?, ?>> items) {
        List<ShopOffer> offers = new ArrayList<>();
        if (items == null) {
            return offers;
        }
        String context = categoryKey != null ? " (catégorie " + categoryKey + ")" : "";
        for (Map<?, ?> entry : items) {
            String materialName = Objects.toString(entry.get("item"), "");
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Matériau invalide '" + materialName + "' pour le template " + templateKey + context);
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
        return offers;
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
        openTemplate(player, template, 0, 0, -1);
    }

    private void openTemplate(Player player, ShopTemplate template, int categoryIndex) {
        openTemplate(player, template, categoryIndex, 0, -1);
    }

    private void openTemplate(Player player, ShopTemplate template, int categoryIndex, int page) {
        openTemplate(player, template, categoryIndex, page, -1);
    }

    private void openTemplate(Player player, ShopTemplate template, int categoryIndex, int page, int categoryPageOverride) {
        if (template == null) {
            player.sendMessage("§cBoutique introuvable.");
            return;
        }

        List<ShopCategory> categories = template.getCategories();
        if (categories.isEmpty()) {
            player.sendMessage("§cAucune catégorie disponible pour cette boutique.");
            return;
        }

        if (categoryIndex < 0) {
            categoryIndex = 0;
        } else if (categoryIndex >= categories.size()) {
            categoryIndex = categories.size() - 1;
        }

        int categoriesPerPage = CATEGORY_DISPLAY_SLOTS.length;
        int totalCategoryPages = Math.max(1, (int) Math.ceil(categories.size() / (double) categoriesPerPage));
        int categoryPage = categoryIndex / categoriesPerPage;
        if (categoryPageOverride >= 0) {
            categoryPage = Math.max(0, Math.min(categoryPageOverride, totalCategoryPages - 1));
        }

        int pageStartIndex = categoryPage * categoriesPerPage;
        int pageEndIndex = Math.min(pageStartIndex + categoriesPerPage, categories.size());
        if (categoryIndex < pageStartIndex || categoryIndex >= pageEndIndex) {
            categoryIndex = pageStartIndex;
        }

        ShopCategory category = categories.get(categoryIndex);
        List<ShopOffer> offers = category.getOffers();

        int maxItemsPerPage = OFFER_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil(offers.size() / (double) maxItemsPerPage));
        if (page < 0) {
            page = 0;
        } else if (page >= totalPages) {
            page = totalPages - 1;
        }

        ShopInventoryHolder holder = new ShopInventoryHolder(
                template.getKey(),
                category.getKey(),
                category.getDisplayName(),
                categoryIndex,
                categories.size(),
                categoryPage,
                totalCategoryPages,
                categoriesPerPage,
                page,
                totalPages
        );
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, template.getDisplayName());

        int startIndex = page * maxItemsPerPage;
        for (int slotIndex = 0; slotIndex < maxItemsPerPage; slotIndex++) {
            int offerIndex = startIndex + slotIndex;
            if (offerIndex >= offers.size()) {
                break;
            }
            ShopOffer offer = offers.get(offerIndex);
            int slot = OFFER_SLOTS[slotIndex];
            ItemStack displayItem = buildOfferItem(offer);
            inventory.setItem(slot, displayItem);
            holder.registerOfferSlot(slot, offer);
        }

        populateCategoryTabs(holder, inventory, categories, categoryIndex);
        populateNavigation(holder, inventory, category);

        player.openInventory(inventory);
    }

    private ItemStack buildOfferItem(ShopOffer offer) {
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
        return item;
    }

    private void populateCategoryTabs(ShopInventoryHolder holder, Inventory inventory, List<ShopCategory> categories, int activeIndex) {
        for (int slot : CATEGORY_DISPLAY_SLOTS) {
            inventory.setItem(slot, createFiller());
        }

        int startIndex = holder.getCategoryPage() * holder.getCategoriesPerPage();
        int endIndex = Math.min(startIndex + holder.getCategoriesPerPage(), categories.size());
        int slotPointer = 0;
        for (int categoryIndex = startIndex; categoryIndex < endIndex; categoryIndex++) {
            int slot = CATEGORY_DISPLAY_SLOTS[slotPointer++];
            ShopCategory category = categories.get(categoryIndex);
            ItemStack icon = new ItemStack(category.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(category.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Clique pour ouvrir");
                if (categoryIndex == activeIndex) {
                    lore.add("§a(onglet actuel)");
                    meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot, icon);
            holder.registerCategorySlot(slot, categoryIndex);
        }

        if (holder.getTotalCategoryPages() > 1 && holder.getCategoryPage() > 0) {
            List<String> lore = List.of("§7Page §e" + holder.getCategoryPage() + "§7/§e" + holder.getTotalCategoryPages());
            inventory.setItem(CATEGORY_PREVIOUS_SLOT, createNavItem(Material.ARROW, "§aCatégories précédentes", lore));
            holder.registerCategoryPageSlot(CATEGORY_PREVIOUS_SLOT, holder.getCategoryPage() - 1);
        } else {
            inventory.setItem(CATEGORY_PREVIOUS_SLOT, createFiller());
        }

        if (holder.getTotalCategoryPages() > 1 && holder.getCategoryPage() + 1 < holder.getTotalCategoryPages()) {
            List<String> lore = List.of("§7Page §e" + (holder.getCategoryPage() + 2) + "§7/§e" + holder.getTotalCategoryPages());
            inventory.setItem(CATEGORY_NEXT_SLOT, createNavItem(Material.ARROW, "§aCatégories suivantes", lore));
            holder.registerCategoryPageSlot(CATEGORY_NEXT_SLOT, holder.getCategoryPage() + 1);
        } else {
            inventory.setItem(CATEGORY_NEXT_SLOT, createFiller());
        }
    }

    private void populateNavigation(ShopInventoryHolder holder, Inventory inventory, ShopCategory category) {
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, createFiller());
        }

        inventory.setItem(CLOSE_SLOT, createNavItem(Material.BARRIER, "§cFermer", List.of("§7Clique pour fermer")));

        if (holder.getPage() > 0) {
            inventory.setItem(PREVIOUS_SLOT, createNavItem(Material.ARROW, "§aPage précédente", List.of("§7Aller à la page §e" + holder.getPage() + "§7/§e" + holder.getTotalPages())));
        }

        if (holder.getPage() + 1 < holder.getTotalPages()) {
            inventory.setItem(NEXT_SLOT, createNavItem(Material.ARROW, "§aPage suivante", List.of("§7Aller à la page §e" + (holder.getPage() + 2) + "§7/§e" + holder.getTotalPages())));
        }

        inventory.setItem(50, createNavItem(Material.PAPER, "§ePage " + (holder.getPage() + 1) + "§7/§e" + holder.getTotalPages(), List.of()));
        inventory.setItem(47, createNavItem(Material.NAME_TAG, category.getDisplayName(), List.of("§7Catégorie actuelle")));
    }

    private ItemStack createFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createNavItem(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
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

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        ShopTemplate currentTemplate = templates.get(holder.getTemplateKey());
        if (slot == PREVIOUS_SLOT && holder.getPage() > 0 && currentTemplate != null) {
            openTemplate(player, currentTemplate, holder.getCategoryIndex(), holder.getPage() - 1, holder.getCategoryPage());
            return;
        }

        if (slot == NEXT_SLOT && holder.getPage() + 1 < holder.getTotalPages() && currentTemplate != null) {
            openTemplate(player, currentTemplate, holder.getCategoryIndex(), holder.getPage() + 1, holder.getCategoryPage());
            return;
        }

        Optional<Integer> categoryPageTarget = holder.getCategoryPageTarget(slot);
        if (categoryPageTarget.isPresent() && currentTemplate != null) {
            int targetPage = categoryPageTarget.get();
            List<ShopCategory> categories = currentTemplate.getCategories();
            if (!categories.isEmpty()) {
                int categoriesPerPage = holder.getCategoriesPerPage();
                int newCategoryIndex = targetPage * categoriesPerPage;
                if (newCategoryIndex >= categories.size()) {
                    newCategoryIndex = categories.size() - 1;
                }
                openTemplate(player, currentTemplate, newCategoryIndex, 0, targetPage);
            }
            return;
        }

        Optional<Integer> targetCategory = holder.getCategoryTarget(slot);
        if (targetCategory.isPresent() && currentTemplate != null) {
            openTemplate(player, currentTemplate, targetCategory.get(), 0);
            return;
        }

        ShopOffer offer = holder.getOffer(slot);
        if (offer == null) {
            return;
        }

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
            String rawIcon = template.getRawIconName();
            if (rawIcon != null && !rawIcon.isBlank()) {
                config.set(path + ".icon", rawIcon);
            }
            if (template.isSingleDefaultCategory()) {
                ShopCategory category = template.getCategory(0);
                if (category != null) {
                    config.set(path + ".items", serializeOffers(category.getOffers()));
                }
            } else {
                for (ShopCategory category : template.getCategories()) {
                    String categoryPath = path + ".categories." + category.getKey();
                    String categoryDisplay = category.getRawDisplayName();
                    if (categoryDisplay != null && !categoryDisplay.isBlank()) {
                        config.set(categoryPath + ".display-name", categoryDisplay);
                    }
                    String categoryIcon = category.getRawIconName();
                    if (categoryIcon != null && !categoryIcon.isBlank()) {
                        config.set(categoryPath + ".icon", categoryIcon);
                    }
                    config.set(categoryPath + ".items", serializeOffers(category.getOffers()));
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

    private List<Map<String, Object>> serializeOffers(List<ShopOffer> offers) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (offers == null) {
            return items;
        }
        for (ShopOffer offer : offers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("item", offer.getMaterial().name());
            map.put("amount", offer.getAmount());
            map.put("buy-price", offer.buyPrice());
            map.put("sell-price", offer.sellPrice());
            items.add(map);
        }
        return items;
    }
}
