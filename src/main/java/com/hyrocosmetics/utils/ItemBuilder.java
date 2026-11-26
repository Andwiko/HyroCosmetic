package com.hyrocosmetics.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material != null ? material : Material.STONE);
    }

    public ItemBuilder(ItemStack item) {
        this.item = item != null ? item.clone() : new ItemStack(Material.STONE);
    }

    public static ItemStack tryGetCustomPluginItem(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;

        String idTrim = identifier.trim();

        // 1. Check ItemsAdder first (exact, namespace:id, or known namespaces)
        ItemStack iaDirect = resolveItemsAdderFlexible(idTrim);
        if (iaDirect != null) return iaDirect;

        // 2. Check if ItemsAdder format (ia:id, itemsadder:id)
        if (idTrim.toLowerCase().startsWith("ia:") || idTrim.toLowerCase().startsWith("itemsadder:")) {
            String iaId = idTrim.substring(idTrim.indexOf(":") + 1);
            ItemStack iaItem = resolveItemsAdderFlexible(iaId);
            if (iaItem != null) return iaItem;
        }

        // 3. Check if Nexo format (nexo:id or nx:id)
        if (idTrim.toLowerCase().startsWith("nexo:") || idTrim.toLowerCase().startsWith("nx:")) {
            String nexoId = idTrim.substring(idTrim.indexOf(":") + 1);
            ItemStack nexoItem = resolveNexoFlexible(nexoId);
            if (nexoItem != null) return nexoItem;
        }

        // 4. Check if Oraxen format (oraxen:id or ox:id)
        if (idTrim.toLowerCase().startsWith("oraxen:") || idTrim.toLowerCase().startsWith("ox:")) {
            String oxId = idTrim.substring(idTrim.indexOf(":") + 1);
            ItemStack oxItem = tryGetOraxenItem(oxId);
            if (oxItem != null) return oxItem;
        }

        // 5. Generic colon format (namespace:id)
        if (idTrim.contains(":")) {
            ItemStack iaItem = resolveItemsAdderFlexible(idTrim);
            if (iaItem != null) return iaItem;

            String stripped = idTrim.substring(idTrim.indexOf(":") + 1);
            ItemStack oxItem = tryGetOraxenItem(stripped);
            if (oxItem != null) return oxItem;

            ItemStack nexoItem = resolveNexoFlexible(idTrim);
            if (nexoItem != null) return nexoItem;
        }

        return null;
    }

    public static ItemStack resolveItemsAdderFlexible(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String clean = id.trim();
        if (clean.toLowerCase().startsWith("ia:") || clean.toLowerCase().startsWith("itemsadder:")) {
            clean = clean.substring(clean.indexOf(":") + 1);
        }

        // 1. Direct try
        ItemStack item = tryGetItemsAdderItem(clean);
        if (item != null) return item;

        // 2. If it contains namespace
        if (clean.contains(":")) {
            String sub = clean.substring(clean.indexOf(":") + 1);
            item = tryGetItemsAdderItem(sub);
            if (item != null) return item;
        } else {
            // Try known cosmetic namespaces
            String[] namespaces = {
                "ax_free_hats_pack",
                "adventure_backpacks",
                "ax_wings_pack",
                "witch_pack",
                "hyrost",
                "hmccosmetics"
            };
            for (String ns : namespaces) {
                item = tryGetItemsAdderItem(ns + ":" + clean);
                if (item != null) return item;
            }
        }
        return null;
    }

    private static ItemStack resolveNexoFlexible(String id) {
        if (id == null || id.isEmpty()) return null;
        ItemStack res = tryGetNexoItem(id);
        if (res != null) return res;

        if (id.contains(":")) {
            String stripped = id.substring(id.indexOf(":") + 1);
            res = tryGetNexoItem(stripped);
            if (res != null) return res;

            String underscore = id.replace(":", "_");
            res = tryGetNexoItem(underscore);
            if (res != null) return res;

            if (underscore.endsWith("_backpack")) {
                res = tryGetNexoItem(underscore.substring(0, underscore.length() - 9));
                if (res != null) return res;
            }
            if (underscore.endsWith("_hat")) {
                res = tryGetNexoItem(underscore.substring(0, underscore.length() - 4));
                if (res != null) return res;
            }
        }
        return null;
    }

    public static ItemStack tryGetNexoItem(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try {
            Class<?> nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            try {
                Method m = nexoClass.getMethod("itemFromId", String.class);
                Object res = m.invoke(null, id);
                if (res instanceof ItemStack is) return is.clone();
                if (res != null) {
                    Method buildM = res.getClass().getMethod("build");
                    ItemStack is = (ItemStack) buildM.invoke(res);
                    if (is != null) return is.clone();
                }
            } catch (NoSuchMethodException ignored) {}

            try {
                Method m = nexoClass.getMethod("getItemById", String.class);
                Object res = m.invoke(null, id);
                if (res instanceof ItemStack is) return is.clone();
                if (res != null) {
                    Method buildM = res.getClass().getMethod("build");
                    ItemStack is = (ItemStack) buildM.invoke(res);
                    if (is != null) return is.clone();
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    public static ItemStack tryGetItemsAdderItem(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstanceMethod = csClass.getMethod("getInstance", String.class);
            Method getItemStackMethod = csClass.getMethod("getItemStack");
            Object cs = getInstanceMethod.invoke(null, id);
            if (cs != null) {
                ItemStack iaItem = (ItemStack) getItemStackMethod.invoke(cs);
                if (iaItem != null) return iaItem.clone();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static ItemStack tryGetOraxenItem(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try {
            Class<?> oxClass = Class.forName("io.thill.oraxen.api.OraxenItems");
            Method getItemByIdMethod = oxClass.getMethod("getItemById", String.class);
            Object item = getItemByIdMethod.invoke(null, id);
            if (item instanceof ItemStack oxItem) {
                return oxItem.clone();
            } else if (item != null) {
                Method buildMethod = item.getClass().getMethod("build");
                ItemStack oxItem = (ItemStack) buildMethod.invoke(item);
                if (oxItem != null) return oxItem.clone();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Material matchSafeMaterial(String name) {
        if (name == null || name.trim().isEmpty()) return Material.STONE;
        String clean = name.trim().toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");

        Material mat = Material.getMaterial(clean);
        if (mat == null) {
            mat = Material.matchMaterial(clean);
        }
        if (mat == null) {
            try {
                mat = Material.valueOf(clean);
            } catch (Exception ignored) {}
        }
        return mat != null ? mat : Material.STONE;
    }

    public static ItemBuilder fromIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return new ItemBuilder(Material.STONE);
        }

        // Try custom plugin item first
        ItemStack custom = tryGetCustomPluginItem(identifier);
        if (custom != null) {
            return new ItemBuilder(custom);
        }

        // Safe modern material lookup without legacy triggers
        Material mat = matchSafeMaterial(identifier);
        return new ItemBuilder(mat);
    }

    public ItemBuilder setAmount(int amount) {
        this.item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder amount(int amount) {
        return setAmount(amount);
    }

    public ItemBuilder setGlow(boolean glow) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean applied = false;
            // 1. Try modern 1.20.5+ / 1.21+ setEnchantmentGlintOverride
            try {
                Method m = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
                m.invoke(meta, glow);
                applied = true;
            } catch (Throwable ignored) {}

            // 2. Legacy fallback
            if (!applied && glow) {
                try {
                    org.bukkit.enchantments.Enchantment unbreaking = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                    if (unbreaking != null) {
                        meta.addEnchant(unbreaking, 1, true);
                    }
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } catch (Throwable ignored) {}
            }
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        return setGlow(glow);
    }

    public ItemBuilder setColor(Color color) {
        if (color == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
            item.setItemMeta(leatherMeta);
        }
        return this;
    }

    public ItemBuilder setColor(String hexOrRgb) {
        if (hexOrRgb == null || hexOrRgb.trim().isEmpty()) return this;
        String clean = hexOrRgb.trim();
        try {
            if (clean.startsWith("#")) {
                clean = clean.substring(1);
            } else if (clean.startsWith("0x") || clean.startsWith("0X")) {
                clean = clean.substring(2);
            }
            int rgb = Integer.parseInt(clean, 16);
            return setColor(Color.fromRGB(rgb));
        } catch (Exception ignored) {}
        return this;
    }

    public ItemBuilder setSkullOwner(org.bukkit.OfflinePlayer player) {
        if (player == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            item.setItemMeta(skullMeta);
        }
        return this;
    }

    public ItemBuilder setItemModel(String itemModel) {
        if (itemModel == null || itemModel.trim().isEmpty()) return this;
        String clean = itemModel.trim().toLowerCase();
        NamespacedKey key;
        if (clean.contains(":")) {
            String[] parts = clean.split(":", 2);
            key = new NamespacedKey(parts[0], parts[1]);
        } else {
            key = NamespacedKey.minecraft(clean);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                Method m = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
                m.invoke(meta, key);
                item.setItemMeta(meta);
            } catch (Throwable ignored) {}
        }
        return this;
    }

    public ItemBuilder setName(String name) {
        if (name == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setName(Component component) {
        if (component == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(component);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setLore(List<String> loreLines) {
        if (loreLines == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(TextUtil.parseList(loreLines));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> current = meta.lore();
            if (current == null) current = new ArrayList<>();
            current.add(TextUtil.parse(line));
            meta.lore(current);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setCustomModelData(Integer data) {
        if (data == null || data <= 0) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(data);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return item.clone();
    }
}
