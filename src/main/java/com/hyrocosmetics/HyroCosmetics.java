package com.hyrocosmetics;

import com.hyrocosmetics.commands.CosmeticsCommand;
import com.hyrocosmetics.cosmetic.CosmeticsManager;
import com.hyrocosmetics.effects.KillEffectListener;
import com.hyrocosmetics.effects.TrailTask;
import com.hyrocosmetics.gui.WardrobeGui;
import com.hyrocosmetics.render.CosmeticRenderer;
import com.hyrocosmetics.storage.CosmeticsDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public final class HyroCosmetics extends JavaPlugin implements Listener {

    private static HyroCosmetics instance;

    private CosmeticsDatabase database;
    private CosmeticsManager manager;
    private CosmeticRenderer renderer;
    private TrailTask trailTask;
    private com.hyrocosmetics.voucher.CosmeticVoucherManager voucherManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=================================================");
        String version = getPluginMeta().getVersion();
        getLogger().info("   HYROCOSMETICS v" + version + " by Hyrost");
        getLogger().info("   Advanced 3D Non-Destructive Cosmetics Engine (1.21+ Ready)");
        getLogger().info("   (Hats, Backpacks, Wings, Hand, Foot, Balloons, Trails, Kills)");
        getLogger().info("=================================================");

        saveDefaultConfig();
        setupOwnerDirectories();

        // 1. Storage
        this.database = new CosmeticsDatabase(this);
        if (!this.database.initialize()) {
            getLogger().severe("[Database] HyroCosmetics tidak dapat dimulai karena database gagal diinisialisasi.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Manager & Cosmetics Catalog
        this.manager = new CosmeticsManager(this);
        this.manager.initialize();

        // 3. Renderer Engine
        this.renderer = new CosmeticRenderer(this);
        this.renderer.initialize();

        // 4. Effects
        this.trailTask = new TrailTask(this);
        this.trailTask.runTaskTimer(this, 2L, 2L);

        // 5. Voucher Manager (Opsi 16)
        this.voucherManager = new com.hyrocosmetics.voucher.CosmeticVoucherManager(this);
        Bukkit.getPluginManager().registerEvents(new com.hyrocosmetics.voucher.CosmeticVoucherManager.VoucherListener(this, this.voucherManager), this);

        // 6. GUI & Event Listeners
        WardrobeGui.loadConfig(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new WardrobeGui(this, null), this);
        Bukkit.getPluginManager().registerEvents(new KillEffectListener(this), this);

        // 7. Register Commands
        if (getCommand("cosmetics") != null) {
            CosmeticsCommand cmd = new CosmeticsCommand(this);
            getCommand("cosmetics").setExecutor(cmd);
            getCommand("cosmetics").setTabCompleter(cmd);
        }

        // Auto-load cosmetics for already online players (e.g. upon reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.loadPlayer(p);
        }

        getLogger().info("[HyroCosmetics] All 8 cosmetic categories ready!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[HyroCosmetics] Cleaning up cosmetic models and shutting down...");

        if (trailTask != null) {
            trailTask.cancel();
        }

        if (renderer != null) {
            renderer.shutdown();
        }

        if (database != null) {
            database.shutdown();
        }

        instance = null;
        getLogger().info("[HyroCosmetics] Disabled cleanly.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.unloadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        renderer.removeCosmetics(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                manager.loadPlayer(event.getPlayer());
            }
        }, 10L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        renderer.removeCosmetics(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                manager.loadPlayer(event.getPlayer());
            }
        }, 5L);
    }

    public static HyroCosmetics getInstance() {
        return instance;
    }

    public CosmeticsDatabase getDatabase() {
        return database;
    }

    public CosmeticsManager getManager() {
        return manager;
    }

    public CosmeticRenderer getRenderer() {
        return renderer;
    }

    public com.hyrocosmetics.voucher.CosmeticVoucherManager getVoucherManager() {
        return voucherManager;
    }

    private void setupOwnerDirectories() {
        String[] categories = {"hats", "backpacks", "wings", "hand", "foot", "balloons", "trails", "kill_effects"};

        // 1. Dedicated 3D model folders for server owners
        for (String cat : categories) {
            File bbDir = new File(getDataFolder(), "models/bbmodels/" + cat);
            createDirectory(bbDir);

            File jsonDir = new File(getDataFolder(), "models/custom_json/" + cat);
            createDirectory(jsonDir);
        }

        // 2. Resource pack folder & modern 1.21+ pack.mcmeta
        File rpDir = new File(getDataFolder(), "resourcepack");
        createDirectory(rpDir);
        File rpItemModels = new File(getDataFolder(), "resourcepack/assets/minecraft/models/item");
        createDirectory(rpItemModels);
        File rpTextures = new File(getDataFolder(), "resourcepack/assets/minecraft/textures/custom");
        createDirectory(rpTextures);

        File mcmetaFile = new File(rpDir, "pack.mcmeta");
        if (!mcmetaFile.exists()) {
            try {
                String mcmetaContent = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 46,\n" +
                        "    \"supported_formats\": {\"min_inclusive\": 15, \"max_inclusive\": 99},\n" +
                        "    \"description\": \"HyroCosmetics Official 3D Resource Pack (Minecraft 1.20 - 26.x)\"\n" +
                        "  }\n" +
                        "}\n";
                Files.writeString(mcmetaFile.toPath(), mcmetaContent);
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.WARNING, "[Setup] Gagal membuat pack.mcmeta default.", e);
            }
        }

        // 3. Auto-deploy Indonesian Guide for Server Owner
        File guideFile = new File(getDataFolder(), "PANDUAN_MODEL_DAN_RESOURCEPACK.md");
        if (!guideFile.exists()) {
            try (InputStream in = getResource("PANDUAN_MODEL_DAN_RESOURCEPACK.md")) {
                if (in != null) {
                    Files.copy(in, guideFile.toPath());
                }
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.WARNING,
                        "[Setup] Gagal menyalin PANDUAN_MODEL_DAN_RESOURCEPACK.md.", e);
            }
        }
    }

    private void createDirectory(File directory) {
        if (directory.exists()) {
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            getLogger().warning("[Setup] Gagal membuat direktori: " + directory.getAbsolutePath());
        }
    }
}
