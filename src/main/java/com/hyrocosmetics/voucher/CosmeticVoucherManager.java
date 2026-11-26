package com.hyrocosmetics.voucher;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.hyrocosmetics.utils.ItemBuilder;
import com.hyrocosmetics.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CosmeticVoucherManager {

    private final HyroCosmetics plugin;
    private final NamespacedKey keyType;
    private final NamespacedKey keyId;

    public CosmeticVoucherManager(HyroCosmetics plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "voucher_type");
        this.keyId = new NamespacedKey(plugin, "voucher_id");
    }

    public ItemStack createVoucher(Cosmetic cosmetic, int amount) {
        if (cosmetic == null) return null;

        List<String> lore = new ArrayList<>();
        lore.add("&8Item Voucher Kosmetik Fisik");
        lore.add("&7Kategori: &e" + cosmetic.getType().getDisplayName());
        lore.add("&7ID: &b" + cosmetic.getId());
        lore.add(" ");
        lore.addAll(cosmetic.getLore());
        lore.add(" ");
        lore.add("&b✦ Bisa dijual di Auction House: &f/ah sell <harga>");
        lore.add("&b✦ Bisa ditradingkan ke sesama pemain!");
        lore.add(" ");
        lore.add("&a&l▶ KLIK KANAN &auntuk membuka kosmetik ini secara permanen!");

        ItemStack item = new ItemBuilder(Material.PAPER)
                .setName("&6&lVOUCHER KOSMETIK: &f" + cosmetic.getDisplayName())
                .setLore(lore)
                .setAmount(Math.max(1, amount))
                .setGlow(true)
                .hideAllFlags()
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyType, PersistentDataType.STRING, cosmetic.getType().name());
            pdc.set(keyId, PersistentDataType.STRING, cosmetic.getId().toLowerCase());
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isVoucher(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(keyType, PersistentDataType.STRING) && pdc.has(keyId, PersistentDataType.STRING);
    }

    public String getCosmeticType(ItemStack item) {
        if (!isVoucher(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
    }

    public String getCosmeticId(ItemStack item) {
        if (!isVoucher(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }

    public static class VoucherListener implements Listener {
        private final HyroCosmetics plugin;
        private final CosmeticVoucherManager voucherManager;

        public VoucherListener(HyroCosmetics plugin, CosmeticVoucherManager voucherManager) {
            this.plugin = plugin;
            this.voucherManager = voucherManager;
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onVoucherInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            ItemStack item = event.getItem();
            if (item == null || !voucherManager.isVoucher(item)) return;

            if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
                return;
            }

            event.setCancelled(true);
            Player player = event.getPlayer();

            String typeStr = voucherManager.getCosmeticType(item);
            String cosmeticId = voucherManager.getCosmeticId(item);

            Cosmetic cosmetic = plugin.getManager().getCosmetic(cosmeticId);
            if (cosmetic == null) {
                player.sendMessage(TextUtil.parse("&cKosmetik dengan ID '" + cosmeticId + "' tidak ditemukan dalam katalog!"));
                return;
            }

            if (plugin.getManager().isUnlocked(player, cosmetic)) {
                player.sendMessage(TextUtil.parse("&eKamu sudah memiliki kosmetik &f" + cosmetic.getDisplayName() + "&e! Voucher ini bisa kamu simpan atau jual di &6/ah&e."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Consume voucher safely (main hand or off-hand)
            if (item.getAmount() <= 1) {
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            } else {
                item.setAmount(item.getAmount() - 1);
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(item);
                } else {
                    player.getInventory().setItemInMainHand(item);
                }
            }

            // Unlock
            plugin.getManager().unlockCosmetic(player, cosmetic);

            // FX
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            Particle totemParticle = com.hyrocosmetics.utils.ParticleUtil.getTotemParticle();
            player.spawnParticle(totemParticle, player.getLocation().add(0, 1.2, 0), 40, 0.5, 0.5, 0.2);

            player.sendMessage(TextUtil.parse("&a&l★ BERHASIL MEMBUKA KOSMETIK!"));
            player.sendMessage(TextUtil.parse("&aKamu telah membuka: &f" + cosmetic.getDisplayName() + " &a(" + cosmetic.getType().getDisplayName() + ") secara permanen! Buka &e/cosmetics &auntuk memakainya."));
        }
    }
}
