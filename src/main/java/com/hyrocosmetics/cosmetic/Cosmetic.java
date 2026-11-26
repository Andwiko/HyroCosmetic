package com.hyrocosmetics.cosmetic;

import com.hyrocosmetics.utils.ItemBuilder;
import com.hyrocosmetics.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class Cosmetic {

    private final String id;
    private final CosmeticType type;
    private final String displayName;
    private final List<String> lore;
    private final String material;
    private final Integer customModelData;
    private final String itemsAdderItem;
    private final String particleType;
    private final String effectAction;
    private final double price;
    private final String permission;
    private final Double offsetY;
    private final Double scale;
    private final String displayTransform;
    private final String itemModel;
    private final String color;
    private final String equipSound;

    public Cosmetic(String id, CosmeticType type, String displayName, List<String> lore, String material,
                    Integer customModelData, String itemsAdderItem, String particleType, String effectAction,
                    double price, String permission) {
        this(id, type, displayName, lore, material, customModelData, itemsAdderItem, particleType, effectAction, price, permission, null, null, null, null, null, null);
    }

    public Cosmetic(String id, CosmeticType type, String displayName, List<String> lore, String material,
                    Integer customModelData, String itemsAdderItem, String particleType, String effectAction,
                    double price, String permission, Double offsetY, Double scale) {
        this(id, type, displayName, lore, material, customModelData, itemsAdderItem, particleType, effectAction, price, permission, offsetY, scale, null, null, null, null);
    }

    public Cosmetic(String id, CosmeticType type, String displayName, List<String> lore, String material,
                    Integer customModelData, String itemsAdderItem, String particleType, String effectAction,
                    double price, String permission, Double offsetY, Double scale, String displayTransform) {
        this(id, type, displayName, lore, material, customModelData, itemsAdderItem, particleType, effectAction, price, permission, offsetY, scale, displayTransform, null, null, null);
    }

    public Cosmetic(String id, CosmeticType type, String displayName, List<String> lore, String material,
                    Integer customModelData, String itemsAdderItem, String particleType, String effectAction,
                    double price, String permission, Double offsetY, Double scale, String displayTransform,
                    String itemModel, String color, String equipSound) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.lore = lore != null ? lore : new ArrayList<>();
        this.material = material != null ? material : "STONE";
        this.customModelData = customModelData;
        this.itemsAdderItem = itemsAdderItem;
        this.particleType = particleType;
        this.effectAction = effectAction;
        this.price = Math.max(0, price);
        this.permission = permission;
        this.offsetY = offsetY;
        this.scale = scale;
        this.displayTransform = displayTransform;
        this.itemModel = itemModel;
        this.color = color;
        this.equipSound = equipSound;
    }

    public String getId() {
        return id;
    }

    public CosmeticType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getMaterial() {
        return material;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getItemsAdderItem() {
        return itemsAdderItem;
    }

    public String getParticleType() {
        return particleType;
    }

    public String getEffectAction() {
        return effectAction;
    }

    public double getPrice() {
        return price;
    }

    public String getPermission() {
        return permission;
    }

    public Double getOffsetY() {
        return offsetY;
    }

    public Double getScale() {
        return scale;
    }

    public String getDisplayTransform() {
        return displayTransform;
    }

    public String getItemModel() {
        return itemModel;
    }

    public String getColor() {
        return color;
    }

    public String getEquipSound() {
        return equipSound;
    }

    /**
     * Builds the 3D model item used for rendering.
     */
    public ItemStack buildModelItem() {
        ItemStack item = null;
        if (itemsAdderItem != null && !itemsAdderItem.isEmpty()) {
            item = ItemBuilder.tryGetCustomPluginItem(itemsAdderItem);
        }

        ItemBuilder builder;
        if (item != null) {
            builder = new ItemBuilder(item);
            if (customModelData != null && customModelData > 0) {
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasCustomModelData()) {
                    builder.setCustomModelData(customModelData);
                }
            }
        } else {
            Material mat = ItemBuilder.matchSafeMaterial(material);
            builder = new ItemBuilder(mat);
            if (customModelData != null && customModelData > 0) {
                builder.setCustomModelData(customModelData);
            }
        }

        // Modern 1.21.2+ Vanilla Item Model support
        if (itemModel != null && !itemModel.isEmpty()) {
            builder.setItemModel(itemModel);
        }

        // Hex / RGB Tinting for leather items
        if (color != null && !color.isEmpty()) {
            builder.setColor(color);
        }

        builder.hideAllFlags();
        ItemStack built = builder.build();
        ItemMeta meta = built.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey("hyrocosmetics", "cosmetic_item"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(new NamespacedKey("hyrocosmetics", "cosmetic_type"), PersistentDataType.STRING, type.name());
            built.setItemMeta(meta);
        }
        return built;
    }

    /**
     * Builds the icon shown inside the Wardrobe GUI menu.
     */
    public ItemStack buildGuiIcon(boolean isEquipped, boolean isUnlocked) {
        ItemStack item = buildModelItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(displayName));

            List<Component> currentLore = new ArrayList<>(TextUtil.parseList(lore));
            currentLore.add(Component.empty());
            currentLore.add(TextUtil.parse("&8------------------------"));

            if (isEquipped) {
                currentLore.add(TextUtil.parse("&a&l✔ SEDANG DIPAKAI"));
                currentLore.add(TextUtil.parse("&eKlik Kiri untuk melepas kosmetik."));
            } else if (isUnlocked) {
                currentLore.add(TextUtil.parse("&b&lTERBUKA"));
                currentLore.add(TextUtil.parse("&eKlik Kiri untuk memakai kosmetik!"));
            } else {
                currentLore.add(TextUtil.parse("&c&l✖ TERKUNCI"));
                if (price > 0) {
                    currentLore.add(TextUtil.parse("&6Harga Beli: &a$" + String.format("%.2f", price)));
                    currentLore.add(TextUtil.parse("&eKlik Kiri untuk membeli dan memakai!"));
                } else {
                    currentLore.add(TextUtil.parse("&7Izin yang dibutuhkan: &e" + permission));
                }
            }

            meta.lore(currentLore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
