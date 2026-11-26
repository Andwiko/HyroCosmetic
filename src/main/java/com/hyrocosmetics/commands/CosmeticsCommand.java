package com.hyrocosmetics.commands;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.hyrocosmetics.gui.WardrobeGui;
import com.hyrocosmetics.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final HyroCosmetics plugin;

    public CosmeticsCommand(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.player-only")));
                return true;
            }

            if (!player.hasPermission("hyrocosmetics.use")) {
                player.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }

            WardrobeGui.open(plugin, player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu", "wardrobe" -> {
                if (sender instanceof Player player) {
                    WardrobeGui.open(plugin, player);
                } else {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.player-only")));
                }
            }
            case "unequipall" -> {
                if (sender instanceof Player player) {
                    plugin.getManager().unequipAll(player);
                }
            }
            case "unequip" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.player-only")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(TextUtil.parse("&eGunakan: /cosmetics unequip <hat|backpack|wings|hand|foot|balloon|trail|kill_effect>"));
                    return true;
                }
                try {
                    CosmeticType type = CosmeticType.valueOf(args[1].toUpperCase());
                    plugin.getManager().unequip(player, type);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(TextUtil.parse("&cKategori kosmetik '" + args[1] + "' tidak valid!"));
                }
            }
            case "toggleview", "viewself" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.player-only")));
                    return true;
                }
                boolean visible = plugin.getRenderer().toggleSelfView(player);
                if (visible) {
                    player.sendMessage(TextUtil.parse("&aMode Tampilan Kosmetik: &fDITAMPILKAN KE DIRI SENDIRI &a(Cocok untuk mode F5 / foto)."));
                } else {
                    player.sendMessage(TextUtil.parse("&eMode Tampilan Kosmetik: &fDISEMBUNYIKAN DARI DIRI SENDIRI &e(Layar first-person bersih tanpa halangan)."));
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("hyrocosmetics.admin")) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission")));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getManager().loadCosmetics();
                WardrobeGui.loadConfig(plugin);
                plugin.getRenderer().refreshAllDisplays();
                sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.reload-success")));
            }
            case "equip" -> {
                if (!sender.hasPermission("hyrocosmetics.admin")) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse("&eGunakan: /cosmetics equip <cosmetic_id> [pemain]"));
                    return true;
                }
                Cosmetic c = plugin.getManager().getCosmetic(args[1]);
                if (c == null) {
                    sender.sendMessage(TextUtil.parse("&cKosmetik '" + args[1] + "' tidak ditemukan!"));
                    return true;
                }
                Player target = null;
                if (args.length >= 3) {
                    if (Bukkit.getPluginManager().isPluginEnabled("HyroCore")) {
                        try {
                            target = com.hyrocore.utils.PlayerResolver.resolve(args, 2);
                        } catch (Throwable ignored) {}
                    }
                    if (target == null) {
                        target = Bukkit.getPlayer(args[2]);
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                }
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(TextUtil.parse("&cPemain target tidak online!"));
                    return true;
                }
                plugin.getManager().equip(target, c);
                sender.sendMessage(TextUtil.parse("&aBerhasil memasang kosmetik " + c.getDisplayName() + " untuk " + target.getName() + "!"));
            }
            case "voucher" -> {
                if (!sender.hasPermission("hyrocosmetics.admin")) {
                    sender.sendMessage(TextUtil.parse("&cAkses ditolak! Hanya admin yang dapat membuat voucher kosmetik manual."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("&eGunakan: /cosmetics voucher <pemain> <cosmetic_id> [jumlah]"));
                    return true;
                }

                int amount = 1;
                int cosmeticIndex = args.length - 1;
                try {
                    int parsed = Integer.parseInt(args[args.length - 1]);
                    amount = Math.max(1, parsed);
                    cosmeticIndex = args.length - 2;
                } catch (NumberFormatException ignored) {}

                if (cosmeticIndex < 2 && args.length > 2) {
                    cosmeticIndex = 2;
                }

                Cosmetic c = plugin.getManager().getCosmetic(args[cosmeticIndex]);
                if (c == null) {
                    c = plugin.getManager().getCosmetic(args[args.length - 1]);
                    if (c != null) {
                        cosmeticIndex = args.length - 1;
                        amount = 1;
                    }
                }

                String[] playerArgs = Arrays.copyOfRange(args, 1, cosmeticIndex);
                Player target = null;
                if (Bukkit.getPluginManager().isPluginEnabled("HyroCore")) {
                    try {
                        target = com.hyrocore.utils.PlayerResolver.resolve(playerArgs, 0);
                    } catch (Throwable ignored) {}
                }
                if (target == null && args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                }

                if (target == null || !target.isOnline()) {
                    sender.sendMessage(TextUtil.parse("&cPemain tidak ditemukan atau sedang offline!"));
                    return true;
                }
                if (c == null) {
                    sender.sendMessage(TextUtil.parse("&cKosmetik tidak ditemukan!"));
                    return true;
                }

                ItemStack voucher = plugin.getVoucherManager().createVoucher(c, amount);
                java.util.HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(voucher);
                if (!overflow.isEmpty()) {
                    for (ItemStack left : overflow.values()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), left);
                    }
                    target.sendMessage(TextUtil.parse("&e[HyroCosmetics] Inventory penuh! Voucher kosmetik dijatuhkan di dekatmu."));
                }
                sender.sendMessage(TextUtil.parse("&aBerhasil memberikan " + amount + "x Voucher Kosmetik &f" + c.getDisplayName() + " &akepada &b" + target.getName() + "&a!"));
                target.sendMessage(TextUtil.parse("&aKamu menerima " + amount + "x Voucher Kosmetik &f" + c.getDisplayName() + "&a! Klik kanan untuk menggunakannya atau jual di &e/ah&a."));
            }
            case "convert" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("&cHanya pemain in-game yang dapat mengonversi kosmetik ke voucher!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse("&eGunakan: /cosmetics convert <cosmetic_id>"));
                    return true;
                }
                Cosmetic c = plugin.getManager().getCosmetic(args[1]);
                if (c == null) {
                    sender.sendMessage(TextUtil.parse("&cKosmetik '" + args[1] + "' tidak ditemukan!"));
                    return true;
                }
                if (!plugin.getManager().isUnlocked(player, c)) {
                    sender.sendMessage(TextUtil.parse("&cKamu belum membuka atau memiliki kosmetik ini!"));
                    return true;
                }
                if (player.getInventory().firstEmpty() == -1) {
                    sender.sendMessage(TextUtil.parse("&cInventory kamu penuh! Harap sediakan minimal 1 slot kosong untuk menerima voucher fisik."));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }
                plugin.getManager().revokeCosmetic(player, c, true);
                ItemStack voucher = plugin.getVoucherManager().createVoucher(c, 1);
                java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(voucher);
                if (!overflow.isEmpty()) {
                    for (ItemStack left : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                }
                player.sendMessage(TextUtil.parse("&aSukses mengemas kosmetik &f" + c.getDisplayName() + " &amenjadi &6Item Voucher Fisik&a! Siap dijual di &e/ah sell <harga>&a atau ditradingkan."));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            }
            case "list" -> {
                String filterCat = (args.length >= 2) ? args[1].toUpperCase() : null;
                CosmeticType filterType = null;
                if (filterCat != null) {
                    try {
                        filterType = CosmeticType.valueOf(filterCat);
                    } catch (IllegalArgumentException ignored) {}
                }

                sender.sendMessage(TextUtil.parse("&6&l=== HyroCosmetics Catalog ==="));
                int count = 0;
                for (Cosmetic c : plugin.getManager().getAllCosmetics()) {
                    if (filterType != null && c.getType() != filterType) continue;
                    count++;
                    String modelInfo = c.getCustomModelData() != null ? "CMD:" + c.getCustomModelData() : (c.getItemModel() != null ? "Model:" + c.getItemModel() : c.getMaterial());
                    sender.sendMessage(TextUtil.parse("&e- &b" + c.getId() + " &7[" + c.getType().name() + "] &f" + c.getDisplayName() + " &8(" + modelInfo + ")"));
                }
                sender.sendMessage(TextUtil.parse("&7Total loaded: &a" + count + " &7kosmetik."));
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse("&eGunakan: /cosmetics info <cosmetic_id>"));
                    return true;
                }
                Cosmetic c = plugin.getManager().getCosmetic(args[1]);
                if (c == null) {
                    sender.sendMessage(TextUtil.parse("&cKosmetik '" + args[1] + "' tidak ditemukan!"));
                    return true;
                }
                sender.sendMessage(TextUtil.parse("&6&l--- Detail Kosmetik: &f" + c.getId() + " &6&l---"));
                sender.sendMessage(TextUtil.parse("&eNama Tampilan: &f" + c.getDisplayName()));
                sender.sendMessage(TextUtil.parse("&eKategori: &b" + c.getType().getDisplayName()));
                sender.sendMessage(TextUtil.parse("&eMaterial: &7" + c.getMaterial()));
                if (c.getCustomModelData() != null) sender.sendMessage(TextUtil.parse("&eCustomModelData: &6" + c.getCustomModelData()));
                if (c.getItemModel() != null) sender.sendMessage(TextUtil.parse("&eVanilla ItemModel: &6" + c.getItemModel()));
                if (c.getColor() != null) sender.sendMessage(TextUtil.parse("&eColor Tint: &a" + c.getColor()));
                if (c.getParticleType() != null) sender.sendMessage(TextUtil.parse("&eParticle: &d" + c.getParticleType()));
                if (c.getEffectAction() != null) sender.sendMessage(TextUtil.parse("&eKill Action: &c" + c.getEffectAction()));
                sender.sendMessage(TextUtil.parse("&eHarga: &a$" + String.format("%.2f", c.getPrice())));
                sender.sendMessage(TextUtil.parse("&eIzin (Permission): &7" + c.getPermission()));
                return true;
            }
            case "offset" -> {
                if (!sender.hasPermission("hyrocosmetics.admin")) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission")));
                    return true;
                }
                if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
                    sender.sendMessage(TextUtil.parse("&6&l--- Live Cosmetic Offsets ---"));
                    for (String type : Arrays.asList("hat", "backpack", "wings", "hand", "balloon")) {
                        double y = plugin.getConfig().getDouble("rendering.offsets." + type + ".y", 0.0);
                        double sneakY = plugin.getConfig().getDouble("rendering.offsets." + type + ".sneak-y", 0.0);
                        double scale = plugin.getConfig().getDouble("rendering.offsets." + type + ".scale", 1.0);
                        double dist = plugin.getConfig().getDouble("rendering.offsets." + type + ".distance", 0.0);
                        sender.sendMessage(TextUtil.parse("&e" + type.toUpperCase() + ": &7y=" + y + ", sneak-y=" + sneakY + ", scale=" + scale + ", dist=" + dist));
                    }
                    sender.sendMessage(TextUtil.parse("&bGunakan: /cosmetics offset <type> <y|sneak-y|scale|distance> <nilai>"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.parse("&eGunakan: /cosmetics offset <hat|backpack|wings|hand|balloon> <y|sneak-y|scale|distance> <nilai>"));
                    return true;
                }
                String type = args[1].toLowerCase();
                String param = args[2].toLowerCase().replace("_", "-");
                double val;
                try {
                    val = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.parse("&cNilai harus berupa angka desimal (contoh: 1.40 atau 0.28)!"));
                    return true;
                }
                String path = "rendering.offsets." + type + "." + param;
                plugin.getConfig().set(path, val);
                plugin.saveConfig();
                plugin.getRenderer().refreshAllDisplays();
                sender.sendMessage(TextUtil.parse("&aSukses! &e" + path + " &adiatur ke &6" + val + "&a. Tampilan pemain langsung diperbarui."));
                return true;
            }
            default -> {
                sender.sendMessage(TextUtil.parse("&6&l--- HyroCosmetics Commands ---"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics &7- Buka lemari kosmetik"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics list [kategori] &7- Lihat katalog kosmetik terpasang"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics info <id> &7- Lihat rincian detail kosmetik"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics convert <id> &7- Kemas kosmetik menjadi voucher fisik (untuk /ah)"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics unequipall &7- Lepas semua kosmetik"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics viewself &7- Tampilkan/sembunyikan kosmetik saat melihat diri sendiri"));
                sender.sendMessage(TextUtil.parse("&e/cosmetics reload &7- Muat ulang konfigurasi"));
                if (sender.hasPermission("hyrocosmetics.admin")) {
                    sender.sendMessage(TextUtil.parse("&e/cosmetics voucher <pemain> <id> [jumlah] &7- Berikan voucher"));
                    sender.sendMessage(TextUtil.parse("&e/cosmetics offset <type> <param> <nilai> &7- Atur posisi model 3D live"));
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("menu", "convert", "unequip", "unequipall", "viewself", "toggleview", "list", "info", "reload"));
            if (sender.hasPermission("hyrocosmetics.admin")) {
                list.add("equip");
                list.add("voucher");
                list.add("offset");
            }
            return list;
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("unequip") || args[0].equalsIgnoreCase("list"))) {
            return Arrays.asList("hat", "backpack", "wings", "hand", "foot", "balloon", "trail", "kill_effect");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("equip") || args[0].equalsIgnoreCase("convert") || args[0].equalsIgnoreCase("info"))) {
            List<String> list = new ArrayList<>();
            plugin.getManager().getAllCosmetics().forEach(c -> list.add(c.getId()));
            return list;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("offset")) {
            return Arrays.asList("hat", "backpack", "wings", "hand", "balloon", "list");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("offset")) {
            return Arrays.asList("y", "sneak-y", "scale", "distance", "rotation-y", "rotation-sneak-y");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("offset")) {
            return Arrays.asList("0.63", "0.70", "1.0", "1.40", "1.10", "0.38");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("voucher")) {
            return null; // players
        } else if (args.length == 3 && args[0].equalsIgnoreCase("voucher")) {
            List<String> list = new ArrayList<>();
            plugin.getManager().getAllCosmetics().forEach(c -> list.add(c.getId()));
            return list;
        }
        return List.of();
    }
}
