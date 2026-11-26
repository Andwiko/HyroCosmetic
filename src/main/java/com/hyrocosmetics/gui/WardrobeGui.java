package com.hyrocosmetics.gui;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.hyrocosmetics.cosmetic.PlayerCosmeticData;
import com.hyrocosmetics.utils.ItemBuilder;
import com.hyrocosmetics.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class WardrobeGui implements InventoryHolder, Listener {

    private final HyroCosmetics plugin;
    private final Player player;
    private boolean isCategoryView = false;
    private CosmeticType currentType = null;
    private int currentPage = 1;
    private int maxPages = 1;
    private final Map<Integer, CosmeticType> slotCategoryMap = new HashMap<>();
    private final Map<Integer, Cosmetic> slotCosmeticMap = new HashMap<>();
    private Inventory inventory;

    private static FileConfiguration menuConfig;

    public WardrobeGui(HyroCosmetics plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public static void loadConfig(HyroCosmetics plugin) {
        File file = new File(plugin.getDataFolder(), "menugui.yml");
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("menugui.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to copy default menugui.yml: " + e.getMessage());
            }
        }
        menuConfig = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration getMenuConfig() {
        if (menuConfig == null) {
            loadConfig(HyroCosmetics.getInstance());
        }
        return menuConfig;
    }

    public static void open(HyroCosmetics plugin, Player player) {
        WardrobeGui gui = new WardrobeGui(plugin, player);
        gui.openMainMenu();
    }

    public void openMainMenu() {
        this.isCategoryView = false;
        this.currentType = null;
        slotCategoryMap.clear();
        slotCosmeticMap.clear();

        FileConfiguration cfg = getMenuConfig();
        String title = cfg.getString("main_menu.title", "&6&lLEMARI KOSMETIK (WARDROBE)");
        int size = cfg.getInt("main_menu.size", 45);

        WardrobeHolder holder = new WardrobeHolder(this, false);
        this.inventory = Bukkit.createInventory(holder, size, TextUtil.parse(title));
        holder.setInventory(this.inventory);

        // Fillers
        String fillerMat = cfg.getString("main_menu.filler.material", "GRAY_STAINED_GLASS_PANE");
        String fillerName = cfg.getString("main_menu.filler.display_name", " ");
        ItemStack filler = ItemBuilder.fromIdentifier(fillerMat).setName(fillerName).build();
        List<Integer> fillerSlots = cfg.getIntegerList("main_menu.filler.slots");
        for (int slot : fillerSlots) {
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, filler);
            }
        }

        PlayerCosmeticData data = plugin.getManager().getPlayerData(player.getUniqueId());

        // 8 Categories
        setupCategory("hat", CosmeticType.HAT, data, cfg, size);
        setupCategory("backpack", CosmeticType.BACKPACK, data, cfg, size);
        setupCategory("wings", CosmeticType.WINGS, data, cfg, size);
        setupCategory("hand", CosmeticType.HAND, data, cfg, size);
        setupCategory("foot", CosmeticType.FOOT, data, cfg, size);
        setupCategory("balloon", CosmeticType.BALLOON, data, cfg, size);
        setupCategory("trail", CosmeticType.TRAIL, data, cfg, size);
        setupCategory("kill_effect", CosmeticType.KILL_EFFECT, data, cfg, size);

        // Profil & Ringkasan Kosmetik Aktif di Slot 4
        List<String> overviewLore = new ArrayList<>();
        overviewLore.add("&7Ringkasan perlengkapan kosmetik aktif:");
        overviewLore.add(" ");
        for (CosmeticType ct : CosmeticType.values()) {
            String eqId = data.getEquipped(ct);
            Cosmetic eq = eqId != null ? plugin.getManager().getCosmetic(eqId) : null;
            String status = eq != null ? "&a" + eq.getDisplayName() : "&8Kosong";
            overviewLore.add("&e" + ct.getDisplayName() + ": " + status);
        }
        overviewLore.add(" ");
        overviewLore.add("&7Pemain lain dapat melihat semua kosmetik ini!");
        inventory.setItem(4, ItemBuilder.fromIdentifier("PLAYER_HEAD")
                .setSkullOwner(player)
                .setName("&6&l✦ Lemari Kosmetik &f" + player.getName())
                .setLore(overviewLore)
                .build());

        // Unequip All Button
        int unequipSlot = cfg.getInt("main_menu.unequip_all.slot", 32);
        if (unequipSlot >= 0 && unequipSlot < size) {
            String mat = cfg.getString("main_menu.unequip_all.material", "SHEARS");
            String name = cfg.getString("main_menu.unequip_all.display_name", "&c&lCopot Semua Kosmetik");
            List<String> lore = cfg.getStringList("main_menu.unequip_all.lore");
            inventory.setItem(unequipSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        // Camera View Mode (Toggle Self-View for F5 vs First-Person)
        int toggleSlot = cfg.getInt("main_menu.toggle_view.slot", 30);
        if (toggleSlot >= 0 && toggleSlot < size) {
            boolean hidden = plugin.getRenderer().isSelfHidden(player);
            String mat = cfg.getString("main_menu.toggle_view.material", "ENDER_EYE");
            String name = hidden ? "&e&lMode Kamera: &7First-Person" : "&a&lMode Kamera: &fOrang Ketiga (F5)";
            List<String> lore = hidden ? List.of(
                    "&7Kosmetik disembunyikan dari layar Anda",
                    "&7agar pandangan first-person tetap bersih.",
                    "&aPemain lain tetap dapat melihat kosmetik Anda!",
                    "",
                    "&eKlik untuk beralih ke Mode F5 (Terlihat)!"
            ) : List.of(
                    "&7Kosmetik ditampilkan ke diri sendiri",
                    "&7(Cocok untuk berfoto atau mode kamera F5).",
                    "&aPemain lain tetap dapat melihat kosmetik Anda!",
                    "",
                    "&eKlik untuk beralih ke Mode First-Person (Hide)!"
            );
            inventory.setItem(toggleSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        // Close Button
        int closeSlot = cfg.getInt("main_menu.close.slot", 40);
        if (closeSlot >= 0 && closeSlot < size) {
            String mat = cfg.getString("main_menu.close.material", "BARRIER");
            String name = cfg.getString("main_menu.close.display_name", "&c&lTutup Lemari");
            List<String> lore = cfg.getStringList("main_menu.close.lore");
            inventory.setItem(closeSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        player.openInventory(inventory);
    }

    private void setupCategory(String key, CosmeticType type, PlayerCosmeticData data, FileConfiguration cfg, int invSize) {
        String path = "main_menu.categories." + key;
        int slot = cfg.getInt(path + ".slot", -1);
        if (slot < 0 || slot >= invSize) return;

        String equippedId = data.getEquipped(type);
        Cosmetic active = equippedId != null ? plugin.getManager().getCosmetic(equippedId) : null;
        String statusStr = active != null ? "&a" + active.getDisplayName() : "&8Tidak ada";

        String mat = cfg.getString(path + ".material", type.getIcon().name());
        String name = cfg.getString(path + ".display_name", type.getDisplayName());
        List<String> rawLore = cfg.getStringList(path + ".lore");
        List<String> parsedLore = new ArrayList<>();
        for (String l : rawLore) {
            parsedLore.add(l.replace("%status%", statusStr));
        }

        ItemStack item = ItemBuilder.fromIdentifier(mat)
                .setName(name)
                .setLore(parsedLore)
                .hideAllFlags()
                .build();

        inventory.setItem(slot, item);
        slotCategoryMap.put(slot, type);
    }

    public void openCategoryMenu(CosmeticType type) {
        openCategoryMenu(type, 1);
    }

    public void openCategoryMenu(CosmeticType type, int page) {
        this.isCategoryView = true;
        this.currentType = type;
        this.currentPage = Math.max(1, page);
        slotCategoryMap.clear();
        slotCosmeticMap.clear();

        FileConfiguration cfg = getMenuConfig();
        String rawTitle = cfg.getString("category_menu.title", "&8Lemari &7» &6&l%category%");
        String title = rawTitle.replace("%category%", type.getDisplayName());
        int size = cfg.getInt("category_menu.size", 54);

        WardrobeHolder holder = new WardrobeHolder(this, true);
        this.inventory = Bukkit.createInventory(holder, size, TextUtil.parse(title));
        holder.setInventory(this.inventory);

        List<Cosmetic> list = plugin.getManager().getCosmeticsByType(type);
        PlayerCosmeticData data = plugin.getManager().getPlayerData(player.getUniqueId());
        String equippedId = data.getEquipped(type);

        int itemsPerPage = 45;
        this.maxPages = Math.max(1, (int) Math.ceil((double) list.size() / itemsPerPage));
        if (this.currentPage > this.maxPages) this.currentPage = this.maxPages;

        int startIndex = (this.currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, list.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Cosmetic c = list.get(i);
            boolean isEquipped = c.getId().equalsIgnoreCase(equippedId);
            boolean isUnlocked = plugin.getManager().isUnlocked(player, c);

            ItemStack icon = c.buildGuiIcon(isEquipped, isUnlocked);
            inventory.setItem(slot, icon);
            slotCosmeticMap.put(slot, c);
            slot++;
        }

        // Filler
        String fillerMat = cfg.getString("category_menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        String fillerName = cfg.getString("category_menu.filler.display_name", " ");
        ItemStack filler = ItemBuilder.fromIdentifier(fillerMat).setName(fillerName).build();
        List<Integer> fillerSlots = cfg.getIntegerList("category_menu.filler.slots");
        for (int fSlot : fillerSlots) {
            if (fSlot >= 0 && fSlot < size) {
                inventory.setItem(fSlot, filler);
            }
        }

        // Back Button (Slot 45)
        int backSlot = cfg.getInt("category_menu.back_button.slot", 45);
        if (backSlot >= 0 && backSlot < size) {
            String mat = cfg.getString("category_menu.back_button.material", "ARROW");
            String name = cfg.getString("category_menu.back_button.display_name", "&e&l« Kembali ke Lemari Utama");
            List<String> lore = cfg.getStringList("category_menu.back_button.lore");
            inventory.setItem(backSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        // Pagination buttons if items exceed 1 page
        if (this.maxPages > 1) {
            if (this.currentPage > 1) {
                inventory.setItem(47, new ItemBuilder(Material.SPECTRAL_ARROW)
                        .setName("&a&l« Halaman Sebelumnya")
                        .addLoreLine("&7Klik untuk menuju ke halaman " + (this.currentPage - 1) + ".")
                        .build());
            } else {
                inventory.setItem(47, new ItemBuilder(Material.ARROW)
                        .setName("&8« Halaman Sebelumnya")
                        .addLoreLine("&7Anda berada di halaman pertama.")
                        .build());
            }

            inventory.setItem(48, new ItemBuilder(Material.PAPER)
                    .setName("&6&lHalaman &e" + this.currentPage + " &6/ &e" + this.maxPages)
                    .addLoreLine("&7Total: &b" + list.size() + " &7kosmetik dalam kategori ini.")
                    .build());

            if (this.currentPage < this.maxPages) {
                inventory.setItem(51, new ItemBuilder(Material.SPECTRAL_ARROW)
                        .setName("&a&lHalaman Selanjutnya »")
                        .addLoreLine("&7Klik untuk menuju ke halaman " + (this.currentPage + 1) + ".")
                        .build());
            } else {
                inventory.setItem(51, new ItemBuilder(Material.ARROW)
                        .setName("&8Halaman Selanjutnya »")
                        .addLoreLine("&7Anda berada di halaman terakhir.")
                        .build());
            }
        }

        // Unequip Category Button (Slot 49)
        int unequipSlot = cfg.getInt("category_menu.unequip_button.slot", 49);
        if (unequipSlot >= 0 && unequipSlot < size) {
            String mat = cfg.getString("category_menu.unequip_button.material", "RED_DYE");
            String name = cfg.getString("category_menu.unequip_button.display_name", "&c&lLepas %category%").replace("%category%", type.getDisplayName());
            List<String> lore = cfg.getStringList("category_menu.unequip_button.lore");
            inventory.setItem(unequipSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        // Close Button (Slot 53)
        int closeSlot = cfg.getInt("category_menu.close_button.slot", 53);
        if (closeSlot >= 0 && closeSlot < size) {
            String mat = cfg.getString("category_menu.close_button.material", "BARRIER");
            String name = cfg.getString("category_menu.close_button.display_name", "&c&lTutup");
            List<String> lore = cfg.getStringList("category_menu.close_button.lore");
            inventory.setItem(closeSlot, ItemBuilder.fromIdentifier(mat).setName(name).setLore(lore).build());
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WardrobeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryCreative(org.bukkit.event.inventory.InventoryCreativeEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WardrobeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WardrobeHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (clicker.getGameMode() == org.bukkit.GameMode.CREATIVE && event.getClick().isCreativeAction()) {
            return;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int rawSlot = event.getSlot();
        WardrobeGui gui = holder.getGui();
        FileConfiguration cfg = getMenuConfig();

        if (!gui.isCategoryView) {
            int unequipAllSlot = cfg.getInt("main_menu.unequip_all.slot", 32);
            int closeSlot = cfg.getInt("main_menu.close.slot", 40);
            int toggleSlot = cfg.getInt("main_menu.toggle_view.slot", 30);

            if (gui.slotCategoryMap.containsKey(rawSlot)) {
                CosmeticType type = gui.slotCategoryMap.get(rawSlot);
                gui.openCategoryMenu(type);
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            } else if (rawSlot == toggleSlot) {
                boolean visible = gui.plugin.getRenderer().toggleSelfView(clicker);
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, visible ? 1.4f : 0.8f);
                if (visible) {
                    clicker.sendMessage(TextUtil.parse("&aMode Tampilan Kosmetik: &fDITAMPILKAN KE DIRI SENDIRI &a(Cocok untuk mode F5 / foto)."));
                } else {
                    clicker.sendMessage(TextUtil.parse("&eMode Tampilan Kosmetik: &fDISEMBUNYIKAN DARI DIRI SENDIRI &e(Layar first-person bersih tanpa halangan)."));
                }
                gui.openMainMenu();
            } else if (rawSlot == unequipAllSlot) {
                gui.plugin.getManager().unequipAll(clicker);
                gui.openMainMenu();
            } else if (rawSlot == closeSlot) {
                clicker.closeInventory();
            }
        } else {
            int backSlot = cfg.getInt("category_menu.back_button.slot", 45);
            int unequipSlot = cfg.getInt("category_menu.unequip_button.slot", 49);
            int closeSlot = cfg.getInt("category_menu.close_button.slot", 53);

            if (gui.slotCosmeticMap.containsKey(rawSlot)) {
                Cosmetic c = gui.slotCosmeticMap.get(rawSlot);
                if (gui.plugin.getManager().equip(clicker, c)) {
                    gui.openCategoryMenu(gui.currentType, gui.currentPage);
                }
            } else if (rawSlot == backSlot) {
                gui.openMainMenu();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            } else if (rawSlot == 47 && gui.maxPages > 1 && gui.currentPage > 1) {
                gui.openCategoryMenu(gui.currentType, gui.currentPage - 1);
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            } else if (rawSlot == 51 && gui.maxPages > 1 && gui.currentPage < gui.maxPages) {
                gui.openCategoryMenu(gui.currentType, gui.currentPage + 1);
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            } else if (rawSlot == unequipSlot) {
                gui.plugin.getManager().unequip(clicker, gui.currentType);
                gui.openCategoryMenu(gui.currentType, gui.currentPage);
            } else if (rawSlot == closeSlot) {
                clicker.closeInventory();
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
