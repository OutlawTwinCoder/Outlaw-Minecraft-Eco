package com.outlaweco.shop;

import com.outlaweco.OutlawEconomyPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class ShopTemplateManager {

    private final OutlawEconomyPlugin plugin;
    private final Logger logger;
    private final File templateFile;
    private final YamlConfiguration templateConfig;
    private final Map<String, ShopTemplate> templates = new LinkedHashMap<>();

    public ShopTemplateManager(OutlawEconomyPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.templateFile = new File(plugin.getDataFolder(), "shop-templates.yml");
        this.templateConfig = YamlConfiguration.loadConfiguration(templateFile);
        loadTemplates();
    }

    private void loadTemplates() {
        templates.clear();
        ConfigurationSection section = templateConfig.getConfigurationSection("templates");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String displayName = section.getString(key + ".display-name", key);
            List<Map<String, Object>> items = templateConfig.getMapList("templates." + key + ".items");
            List<ShopOffer> offers = new ArrayList<>();
            for (Map<String, Object> data : items) {
                ShopOffer offer = parseOffer(key, data);
                if (offer != null) {
                    offers.add(offer);
                }
            }
            ShopTemplate template = new ShopTemplate(key, displayName, offers);
            templates.put(normalize(key), template);
        }
    }

    private ShopOffer parseOffer(String templateKey, Map<String, Object> data) {
        Object materialRaw = data.get("material");
        if (!(materialRaw instanceof String materialName)) {
            logger.warning("Entrée de boutique invalide pour " + templateKey + ": material manquant");
            return null;
        }
        Material material = Material.matchMaterial(materialName, true);
        if (material == null) {
            logger.warning("Material inconnu '" + materialName + "' pour la boutique " + templateKey);
            return null;
        }
        int quantity = 1;
        Object quantityObj = data.get("quantity");
        if (quantityObj instanceof Number number) {
            quantity = Math.max(1, number.intValue());
        }
        Object stackObj = data.get("stack");
        if (stackObj instanceof Boolean stack && stack && !(quantityObj instanceof Number)) {
            quantity = material.getMaxStackSize();
        }
        double buyPrice = 0.0D;
        Object buyRaw = data.get("buy-price");
        if (buyRaw instanceof Number number) {
            buyPrice = number.doubleValue();
        } else if (buyRaw instanceof String str) {
            buyPrice = parseDouble(str, 0.0D);
        }
        double sellPrice = buyPrice;
        Object sellRaw = data.get("sell-price");
        if (sellRaw instanceof Number number) {
            sellPrice = number.doubleValue();
        } else if (sellRaw instanceof String str) {
            sellPrice = parseDouble(str, buyPrice);
        } else if (data.containsKey("price")) {
            Object price = data.get("price");
            if (price instanceof Number number) {
                buyPrice = number.doubleValue();
                sellPrice = buyPrice;
            } else if (price instanceof String str) {
                buyPrice = parseDouble(str, buyPrice);
                sellPrice = buyPrice;
            }
        }
        return new ShopOffer(material, quantity, buyPrice, sellPrice);
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public ShopTemplate getTemplate(String key) {
        if (key == null) {
            return null;
        }
        return templates.get(normalize(key));
    }

    public ShopTemplate ensureTemplate(String key) {
        ShopTemplate existing = getTemplate(key);
        if (existing != null) {
            return existing;
        }
        ShopTemplate created = new ShopTemplate(key, key, new ArrayList<>());
        templates.put(normalize(key), created);
        saveTemplate(created);
        return created;
    }

    public Collection<ShopTemplate> getTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public List<String> getTemplateKeys() {
        List<String> keys = new LinkedList<>();
        for (ShopTemplate template : templates.values()) {
            keys.add(template.getKey());
        }
        return keys;
    }

    public boolean addOffer(String templateKey, ShopOffer offer) {
        ShopTemplate template = ensureTemplate(templateKey);
        template.addOffer(offer);
        saveTemplate(template);
        return true;
    }

    public boolean removeOffer(String templateKey, Material material) {
        ShopTemplate template = getTemplate(templateKey);
        if (template == null) {
            return false;
        }
        boolean removed = template.removeOffer(material);
        if (removed) {
            saveTemplate(template);
        }
        return removed;
    }

    public void reloadTemplates() {
        try {
            templateConfig.load(templateFile);
            loadTemplates();
        } catch (IOException | InvalidConfigurationException e) {
            logger.severe("Impossible de recharger shop-templates.yml: " + e.getMessage());
        }
    }

    private void saveTemplate(ShopTemplate template) {
        String base = "templates." + template.getKey();
        templateConfig.set(base + ".display-name", template.getDisplayNameRaw());
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ShopOffer offer : template.getOffers()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("material", offer.getMaterial().name());
            data.put("quantity", offer.getAmount());
            data.put("buy-price", offer.getBuyPrice());
            data.put("sell-price", offer.getSellPrice());
            serialized.add(data);
        }
        templateConfig.set(base + ".items", serialized);
        saveFile();
    }

    private void saveFile() {
        try {
            templateConfig.save(templateFile);
        } catch (IOException e) {
            logger.severe("Impossible d'enregistrer shop-templates.yml: " + e.getMessage());
        }
    }

    private String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }
}
