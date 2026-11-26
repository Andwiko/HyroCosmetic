package com.hyrocosmetics.cosmetic;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsManager {

    private final HyroCosmetics plugin;
    private final Map<String, Cosmetic> cosmetics = new LinkedHashMap<>();
    private final Map<UUID, PlayerCosmeticData> playerDataMap = new ConcurrentHashMap<>();

    public CosmeticsManager(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadCosmetics();
    }

    public void loadCosmetics() {
        cosmetics.clear();

        File cosmeticsDir = new File(plugin.getDataFolder(), "cosmetics");
        String[] subfolders = {"hats", "backpacks", "wings", "hand", "foot", "balloons", "trails", "kill_effects"};

        // Create category subfolders and copy default configs if missing
        for (String sub : subfolders) {
            File folder = new File(cosmeticsDir, sub);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File defaultFile = new File(folder, sub + ".yml");
            if (!defaultFile.exists()) {
                try (InputStream in = plugin.getResource("cosmetics/" + sub + "/" + sub + ".yml")) {
                    if (in != null) {
                        Files.copy(in, defaultFile.toPath());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to copy default " + sub + ".yml: " + e.getMessage());
                }
            }
        }

        // Also check if legacy cosmetics.yml exists
        File legacyFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        if (legacyFile.exists()) {
            loadCosmeticsFromFile(legacyFile, null);
        }

        // Recursively load all YAML files inside cosmetics/ directory
        if (cosmeticsDir.exists()) {
            loadCosmeticsFromDirectory(cosmeticsDir, null);
        }

        plugin.getLogger().info("[Cosmetics] Loaded " + cosmetics.size() + " cosmetics across 8 categories from dedicated folders.");
    }

    private void loadCosmeticsFromDirectory(File dir, CosmeticType fallbackType) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Infer category from folder name
                CosmeticType inferredType = parseTypeFromFolderName(file.getName());
                loadCosmeticsFromDirectory(file, inferredType != null ? inferredType : fallbackType);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                loadCosmeticsFromFile(file, fallbackType);
            }
        }
    }

    private void loadCosmeticsFromFile(File file, CosmeticType defaultType) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.isConfigurationSection("cosmetics")
                    ? config.getConfigurationSection("cosmetics")
                    : config;

            if (section == null) return;

            for (String key : section.getKeys(false)) {
                ConfigurationSection cSec = section.getConfigurationSection(key);
                if (cSec == null) continue;

                try {
                    String typeStr = cSec.getString("type");
                    CosmeticType type = defaultType;
                    if (typeStr != null) {
                        try {
                            type = CosmeticType.valueOf(typeStr.toUpperCase());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (type == null) type = CosmeticType.HAT;

                    String name = cSec.getString("display_name", key);
                    List<String> lore = cSec.getStringList("lore");
                    String material = cSec.getString("material", "STONE");
                    Integer customModelData = cSec.contains("custom_model_data") ? cSec.getInt("custom_model_data") : null;
                    String itemsAdderItem = cSec.getString("itemsadder_item", null);
                    String particleType = cSec.getString("particle_type", null);
                    String effectAction = cSec.getString("effect_action", null);
                    double price = cSec.getDouble("price", 0.0);
                    String permission = cSec.getString("permission", "hyrocosmetics.use");
                    Double offsetY = cSec.contains("offset_y") ? cSec.getDouble("offset_y") : null;
                    Double scale = cSec.contains("scale") ? cSec.getDouble("scale") : null;
                    String transform = cSec.getString("display_transform", cSec.getString("transform", null));
                    String itemModel = cSec.getString("item_model", null);
                    String color = cSec.getString("color", null);
                    String equipSound = cSec.getString("equip_sound", null);

                    Cosmetic cosmetic = new Cosmetic(key.toLowerCase(), type, name, lore, material,
                            customModelData, itemsAdderItem, particleType, effectAction, price, permission, offsetY, scale, transform,
                            itemModel, color, equipSound);

                    cosmetics.put(key.toLowerCase(), cosmetic);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading cosmetic " + key + " in " + file.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse cosmetic file: " + file.getName() + " - " + e.getMessage());
        }
    }

    private CosmeticType parseTypeFromFolderName(String folderName) {
        String lower = folderName.toLowerCase();
        return switch (lower) {
            case "hats", "hat", "helmets", "topi" -> CosmeticType.HAT;
            case "backpacks", "backpack", "ransel", "bags" -> CosmeticType.BACKPACK;
            case "wings", "wing", "sayap" -> CosmeticType.WINGS;
            case "hand", "handheld", "handhelds", "tangan", "canes" -> CosmeticType.HAND;
            case "foot", "footwear", "boots", "shoes", "kaki" -> CosmeticType.FOOT;
            case "balloons", "balloon", "balon" -> CosmeticType.BALLOON;
            case "trails", "trail", "particles", "jejak" -> CosmeticType.TRAIL;
            case "kill_effects", "killeffects", "kill", "death_effects" -> CosmeticType.KILL_EFFECT;
            default -> null;
        };
    }

    public Cosmetic getCosmetic(String id) {
        return id != null ? cosmetics.get(id.toLowerCase()) : null;
    }

    public List<Cosmetic> getCosmeticsByType(CosmeticType type) {
        List<Cosmetic> list = new ArrayList<>();
        for (Cosmetic c : cosmetics.values()) {
            if (c.getType() == type) {
                list.add(c);
            }
        }
        return list;
    }

    public PlayerCosmeticData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerCosmeticData::new);
    }

    public void loadPlayer(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<CosmeticType, String> equipped = plugin.getDatabase().loadEquipped(player.getUniqueId());
            Set<String> unlocked = plugin.getDatabase().loadUnlocked(player.getUniqueId());
            PlayerCosmeticData data = getPlayerData(player.getUniqueId());

            for (Map.Entry<CosmeticType, String> entry : equipped.entrySet()) {
                data.setEquipped(entry.getKey(), entry.getValue());
            }

            for (String cosmeticId : unlocked) {
                data.addUnlocked(cosmeticId);
            }

            // Sync rendering on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    for (Map.Entry<CosmeticType, String> entry : data.getAllEquipped().entrySet()) {
                        Cosmetic c = getCosmetic(entry.getValue());
                        if (c != null) {
                            plugin.getRenderer().applyCosmetic(player, c);
                        }
                    }
                }
            });
        });
    }

    public void unloadPlayer(Player player) {
        plugin.getRenderer().removeCosmetics(player);
        playerDataMap.remove(player.getUniqueId());
    }

    public boolean isUnlocked(Player player, Cosmetic cosmetic) {
        if (player.hasPermission("hyrocosmetics.admin")) return true;
        PlayerCosmeticData data = getPlayerData(player.getUniqueId());
        if (data != null && data.hasUnlocked(cosmetic.getId())) return true;
        if (cosmetic.getPermission() != null && !cosmetic.getPermission().isEmpty()) {
            return player.hasPermission(cosmetic.getPermission());
        }
        return false;
    }

    public void unlockCosmetic(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) return;
        PlayerCosmeticData data = getPlayerData(player.getUniqueId());
        data.addUnlocked(cosmetic.getId());
        plugin.getDatabase().saveUnlocked(player.getUniqueId(), cosmetic.getId());
    }

    public void revokeCosmetic(Player player, Cosmetic cosmetic) {
        revokeCosmetic(player, cosmetic, false);
    }

    public void revokeCosmetic(Player player, Cosmetic cosmetic, boolean silent) {
        if (player == null || cosmetic == null) return;
        PlayerCosmeticData data = getPlayerData(player.getUniqueId());
        data.removeUnlocked(cosmetic.getId());
        plugin.getDatabase().removeUnlocked(player.getUniqueId(), cosmetic.getId());
        if (cosmetic.getId().equalsIgnoreCase(data.getEquipped(cosmetic.getType()))) {
            unequip(player, cosmetic.getType(), silent);
        }
    }

    public boolean equip(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) return false;

        PlayerCosmeticData data = getPlayerData(player.getUniqueId());

        // Check if already equipped
        if (cosmetic.getId().equalsIgnoreCase(data.getEquipped(cosmetic.getType()))) {
            unequip(player, cosmetic.getType());
            return true;
        }

        // Check unlocked or buy
        if (!isUnlocked(player, cosmetic)) {
            if (cosmetic.getPrice() > 0) {
                // Try buy via HyroCore Vault API
                boolean bought = tryPurchase(player, cosmetic.getPrice());
                if (!bought) {
                    String msg = plugin.getConfig().getString("messages.not-enough-money", "")
                            .replace("%price%", String.format("%.2f", cosmetic.getPrice()));
                    player.sendMessage(TextUtil.parse(msg));
                    return false;
                }

                unlockCosmetic(player, cosmetic);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
                com.hyrocosmetics.utils.ParticleUtil.spawnParticle(player.getWorld(), "VILLAGER_HAPPY", player.getLocation().add(0, 1.2, 0), 12, 0.4, 0.5, 0.4, 0.1);

                String boughtMsg = plugin.getConfig().getString("messages.bought-success", "")
                        .replace("%cosmetic%", cosmetic.getDisplayName())
                        .replace("%price%", String.format("%.2f", cosmetic.getPrice()));
                player.sendMessage(TextUtil.parse(boughtMsg));
            } else {
                player.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.unlocked-needed")));
                return false;
            }
        }

        data.setEquipped(cosmetic.getType(), cosmetic.getId());
        plugin.getDatabase().saveEquipped(player.getUniqueId(), cosmetic.getType(), cosmetic.getId());
        plugin.getRenderer().applyCosmetic(player, cosmetic);

        // Sound effect
        if (cosmetic.getEquipSound() != null && !cosmetic.getEquipSound().isEmpty()) {
            try {
                Sound snd = Sound.valueOf(cosmetic.getEquipSound().toUpperCase());
                player.playSound(player.getLocation(), snd, 1.0f, 1.0f);
            } catch (Throwable ignored) {
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
            }
        } else {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
        }

        String msg = plugin.getConfig().getString("messages.equipped", "")
                .replace("%cosmetic%", cosmetic.getDisplayName());
        player.sendMessage(TextUtil.parse(msg));

        // Subtle sparkle particle feedback upon equipping
        com.hyrocosmetics.utils.ParticleUtil.spawnParticle(player.getWorld(), "VILLAGER_HAPPY", player.getLocation().add(0, 1.0, 0), 6, 0.3, 0.4, 0.3, 0.05);

        // Fire CosmeticEquipEvent for HyroQuest & external integrations
        try {
            Bukkit.getPluginManager().callEvent(new com.hyrocore.api.events.CosmeticEquipEvent(player, cosmetic.getId(), cosmetic.getType().name()));
        } catch (Throwable ignored) {}

        return true;
    }

    public void unequip(Player player, CosmeticType type) {
        unequip(player, type, false);
    }

    public void unequip(Player player, CosmeticType type, boolean silent) {
        if (player == null) return;
        PlayerCosmeticData data = getPlayerData(player.getUniqueId());
        String current = data.getEquipped(type);
        if (current != null) {
            data.unequip(type);
            plugin.getDatabase().removeEquipped(player.getUniqueId(), type);
            plugin.getRenderer().removeCosmeticType(player, type);

            if (!silent) {
                Cosmetic c = getCosmetic(current);
                String name = c != null ? c.getDisplayName() : type.getDisplayName();
                String msg = plugin.getConfig().getString("messages.unequipped", "").replace("%cosmetic%", name);
                player.sendMessage(TextUtil.parse(msg));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.8f);
                com.hyrocosmetics.utils.ParticleUtil.spawnParticle(player.getWorld(), "POOF", player.getLocation().add(0, 1.0, 0), 4, 0.2, 0.3, 0.2, 0.02);
            }
        }
    }

    public void unequipAll(Player player) {
        if (player == null) return;
        PlayerCosmeticData data = getPlayerData(player.getUniqueId());
        data.clearAll();
        plugin.getDatabase().clearEquipped(player.getUniqueId());
        plugin.getRenderer().removeCosmetics(player);

        player.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.unequipped-all")));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
    }

    private boolean tryPurchase(Player player, double amount) {
        // Attempt using HyroCoreAPI if available
        try {
            Class<?> hyroCoreClass = Class.forName("com.hyrocore.HyroCore");
            Object api = hyroCoreClass.getMethod("getApi").invoke(null);
            if (api != null) {
                Method withdrawMethod = api.getClass().getMethod("withdrawMoney", org.bukkit.OfflinePlayer.class, double.class);
                return (boolean) withdrawMethod.invoke(api, player, amount);
            }
        } catch (Throwable ignored) {}

        // Fallback: If Vault is directly registered in ServicesManager
        try {
            org.bukkit.plugin.RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
            if (rsp != null) {
                Object eco = rsp.getProvider();
                Method hasMethod = eco.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class);
                Method withdrawMethod = eco.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
                if ((boolean) hasMethod.invoke(eco, player, amount)) {
                    Object response = withdrawMethod.invoke(eco, player, amount);
                    Method isSuccessMethod = response.getClass().getMethod("transactionSuccess");
                    return (boolean) isSuccessMethod.invoke(response);
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public Collection<Cosmetic> getAllCosmetics() {
        return cosmetics.values();
    }
}
