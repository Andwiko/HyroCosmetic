package com.hyrocosmetics.render;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticRenderer implements Listener {

    private final HyroCosmetics plugin;

    // Direct client-synchronized equipment cosmetics (0ms latency, locked to player
    // skeleton, NEVER blocks first-person)
    private final Map<UUID, ItemStack> playerHatMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> playerFootMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> playerHandMap = new ConcurrentHashMap<>();

    // Track active cosmetic objects for per-item offsetY and scale
    private final Map<UUID, Map<CosmeticType, Cosmetic>> playerCosmeticObjMap = new ConcurrentHashMap<>();

    // 3D Attachments (Backpacks, Wings, Balloons, Hand) using high-performance
    // Display Entities
    private final Map<UUID, Map<CosmeticType, ItemDisplay>> playerDisplayMap = new ConcurrentHashMap<>();

    // Track last move tick to prevent tickCosmetics from fighting onPlayerMove in
    // the same tick
    private final Map<UUID, Long> lastMoveTickMap = new ConcurrentHashMap<>();
    private final Map<UUID, Vector> playerVelocityMap = new ConcurrentHashMap<>();

    private BukkitTask updateTask;

    public CosmeticRenderer(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Clean up any lingering ItemDisplay passengers from previous sessions
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Entity passenger : new ArrayList<>(p.getPassengers())) {
                if (passenger instanceof ItemDisplay) {
                    p.removePassenger(passenger);
                    passenger.remove();
                }
            }
        }

        int ticks = Math.max(1, plugin.getConfig().getInt("rendering.update-ticks", 1));
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCosmetics, ticks, ticks);
    }

    public void applyCosmetic(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null)
            return;

        // Particle Trails & Kill Effects don't require 3D models
        if (cosmetic.getType() == CosmeticType.TRAIL || cosmetic.getType() == CosmeticType.KILL_EFFECT) {
            return;
        }

        // Remove existing cosmetic of this type first
        removeCosmeticType(player, cosmetic.getType());

        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Store active cosmetic object
        playerCosmeticObjMap.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(cosmetic.getType(), cosmetic);

        ItemStack modelItem = cosmetic.buildModelItem();

        // 1. FOOTWEAR: Handled via virtual equipment packets (visible to both player
        // and other viewers)
        if (cosmetic.getType() == CosmeticType.FOOT) {
            playerFootMap.put(player.getUniqueId(), modelItem);
            broadcastEquipmentChange(player, EquipmentSlot.FEET, modelItem);
            player.sendEquipmentChange(player, EquipmentSlot.FEET, modelItem);
            return;
        }

        // 2. HATS:
        // By default handled via virtual equipment packets (EquipmentSlot.HEAD) for
        // 100% native
        // head-bone synchronization (pitch, yaw, sneak, swim, jump) with zero latency &
        // zero entity overhead.
        // Can be configured to DISPLAY in config.yml if preferred.
        if (cosmetic.getType() == CosmeticType.HAT) {
            String hatMode = plugin.getConfig().getString("rendering.hat-mode", "EQUIPMENT");
            if ("EQUIPMENT".equalsIgnoreCase(hatMode)) {
                playerHatMap.put(player.getUniqueId(), modelItem);
                broadcastEquipmentChange(player, EquipmentSlot.HEAD, modelItem);
                player.sendEquipmentChange(player, EquipmentSlot.HEAD, modelItem);
                return;
            }
            spawnDisplayCosmetic(player, cosmetic, modelItem);
            return;
        }

        // 3. HAND: Natural OFF_HAND virtual equipment or 3D Display
        if (cosmetic.getType() == CosmeticType.HAND) {
            String handMode = plugin.getConfig().getString("rendering.hand-mode", "DISPLAY");
            if ("OFF_HAND".equalsIgnoreCase(handMode)) {
                playerHandMap.put(player.getUniqueId(), modelItem);
                broadcastEquipmentChange(player, EquipmentSlot.OFF_HAND, modelItem);
                player.sendEquipmentChange(player, EquipmentSlot.OFF_HAND, modelItem);
                return;
            }
            spawnDisplayCosmetic(player, cosmetic, modelItem);
            return;
        }

        // 4. BACKPACK, WINGS, BALLOONS:
        spawnDisplayCosmetic(player, cosmetic, modelItem);
    }

    // Set of players who toggled seeing their own 3D cosmetics (e.g. for F5
    // third-person / screenshots)
    private final Set<UUID> selfViewOverride = ConcurrentHashMap.newKeySet();

    public boolean isHorizontalPose(Player player) {
        if (player == null)
            return false;
        if (player.isGliding() || player.isSwimming())
            return true;
        try {
            org.bukkit.entity.Pose pose = player.getPose();
            return pose == org.bukkit.entity.Pose.SWIMMING || pose == org.bukkit.entity.Pose.FALL_FLYING
                    || pose == org.bukkit.entity.Pose.SPIN_ATTACK;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void resyncPassengerDisplays(Player player) {
        if (player == null || !player.isOnline())
            return;
        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map == null || map.isEmpty())
            return;

        for (Map.Entry<CosmeticType, ItemDisplay> entry : map.entrySet()) {
            CosmeticType type = entry.getKey();
            if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
                ItemDisplay d = entry.getValue();
                if (d != null && d.isValid()) {
                    if (!player.getPassengers().contains(d)) {
                        player.addPassenger(d);
                    }
                    d.setRotation(player.getBodyYaw(), isHorizontalPose(player) ? player.getLocation().getPitch() : 0f);
                    applyPassengerTransform(player, type, d, getCosmeticScale(player, type), player.isSneaking());
                }
            }
        }
        syncDisplayLocations(player, player.getLocation());
    }

    public boolean isSelfHidden(Player player) {
        return isSelfHidden(player, null);
    }

    public boolean isSelfHidden(Player player, CosmeticType type) {
        if (player == null)
            return false;
        if (type == CosmeticType.FOOT)
            return false;
        if (type == CosmeticType.HAND
                && "OFF_HAND".equalsIgnoreCase(plugin.getConfig().getString("rendering.hand-mode", "DISPLAY"))) {
            return false;
        }
        String mode = plugin.getConfig().getString("rendering.hide-from-self", "auto");
        boolean defaultHide;
        if ("auto".equalsIgnoreCase(mode) || "false".equalsIgnoreCase(mode)) {
            defaultHide = false;
        } else {
            defaultHide = true;
        }
        boolean overridden = selfViewOverride.contains(player.getUniqueId());
        return defaultHide ? !overridden : overridden;
    }

    private void spawnDisplayCosmetic(Player player, Cosmetic cosmetic, ItemStack modelItem) {
        CosmeticType type = cosmetic.getType();
        float scale = getCosmeticScale(player, type);

        ItemDisplayTransform transform;
        if (cosmetic.getDisplayTransform() != null && !cosmetic.getDisplayTransform().isEmpty()) {
            try {
                transform = ItemDisplayTransform.valueOf(cosmetic.getDisplayTransform().toUpperCase());
            } catch (Exception e) {
                transform = ItemDisplayTransform.FIXED;
            }
        } else {
            transform = switch (type) {
                case HAND -> ItemDisplayTransform.THIRDPERSON_RIGHTHAND;
                case HAT -> ItemDisplayTransform.HEAD;
                default -> ItemDisplayTransform.FIXED;
            };
        }

        final ItemDisplayTransform finalTransform = transform;

        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(finalTransform);
            d.setTeleportDuration(0); // Instant
            d.setInterpolationDuration(0);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setViewRange(1.0f);
        });

        if (isSelfHidden(player, type)) {
            player.hideEntity(plugin, display);
        }

        playerDisplayMap.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(type, display);

        if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
            applyPassengerTransform(player, type, display, scale, player.isSneaking());
            player.addPassenger(display);
        }
        updateSingleDisplay(player, type, display, player.getLocation());
    }

    public void applyPassengerTransform(Player player, CosmeticType type, ItemDisplay display, float scale,
            boolean sneaking) {
        Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
        Cosmetic cosmetic = cMap != null ? cMap.get(type) : null;
        float customOffsetY = (cosmetic != null && cosmetic.getOffsetY() != null) ? cosmetic.getOffsetY().floatValue()
                : 0.0f;

        float xOffset = 0.0f;
        float yOffset;
        float zOffset;
        double finalRotX;
        double finalRotY;
        double finalRotZ;

        String configKey = (type == CosmeticType.BACKPACK) ? "rendering.offsets.backpack." : "rendering.offsets.wings.";

        if (type == CosmeticType.BACKPACK) {
            double baseY = plugin.getConfig().getDouble(configKey + "passenger-y", -0.68) + customOffsetY;
            double sneakY = plugin.getConfig().getDouble(configKey + "passenger-sneak-y", -0.58) + customOffsetY;
            double baseZ = plugin.getConfig().getDouble(configKey + "passenger-z", -0.20);
            double sneakZ = plugin.getConfig().getDouble(configKey + "passenger-sneak-z", -0.24);
            double baseX = plugin.getConfig().getDouble(configKey + "passenger-x", 0.0);
            double sneakX = plugin.getConfig().getDouble(configKey + "passenger-sneak-x", baseX);

            xOffset = sneaking ? (float) sneakX : (float) baseX;
            yOffset = sneaking ? (float) sneakY : (float) baseY;
            zOffset = sneaking ? (float) sneakZ : (float) baseZ;

            double rotY = plugin.getConfig().getDouble(configKey + "rotation-y", 180.0);
            double rotZ = plugin.getConfig().getDouble(configKey + "rotation-z", 0.0);
            double rotX = plugin.getConfig().getDouble(configKey + "rotation-x", 0.0);

            double rotSneakY = plugin.getConfig().getDouble(configKey + "rotation-sneak-y",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-y", rotY));
            double rotSneakZ = plugin.getConfig().getDouble(configKey + "rotation-sneak-z",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-z", rotZ));
            double defaultSneakX = -25.0; // Torso forward hunch when sneaking
            double rotSneakX = plugin.getConfig().getDouble(configKey + "rotation-sneak-x",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-x", defaultSneakX));

            finalRotX = sneaking ? rotSneakX : rotX;
            finalRotY = sneaking ? rotSneakY : rotY;
            finalRotZ = sneaking ? rotSneakZ : rotZ;
        } else { // WINGS
            double baseY = plugin.getConfig().getDouble(configKey + "passenger-y", -0.68) + customOffsetY;
            double sneakY = plugin.getConfig().getDouble(configKey + "passenger-sneak-y", -0.58) + customOffsetY;
            double baseZ = plugin.getConfig().getDouble(configKey + "passenger-z", -0.22);
            double sneakZ = plugin.getConfig().getDouble(configKey + "passenger-sneak-z", -0.25);
            double baseX = plugin.getConfig().getDouble(configKey + "passenger-x", 0.0);
            double sneakX = plugin.getConfig().getDouble(configKey + "passenger-sneak-x", baseX);

            xOffset = sneaking ? (float) sneakX : (float) baseX;
            yOffset = sneaking ? (float) sneakY : (float) baseY;
            zOffset = sneaking ? (float) sneakZ : (float) baseZ;

            double rotY = plugin.getConfig().getDouble(configKey + "rotation-y", 0.0);
            double rotZ = plugin.getConfig().getDouble(configKey + "rotation-z", 0.0);
            double rotX = plugin.getConfig().getDouble(configKey + "rotation-x", 0.0);

            double rotSneakY = plugin.getConfig().getDouble(configKey + "rotation-sneak-y",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-y", rotY));
            double rotSneakZ = plugin.getConfig().getDouble(configKey + "rotation-sneak-z",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-z", rotZ));
            double defaultSneakX = -25.0; // Torso forward hunch when sneaking
            double rotSneakX = plugin.getConfig().getDouble(configKey + "rotation-sneak-x",
                    plugin.getConfig().getDouble(configKey + "sneak-rotation-x", defaultSneakX));

            finalRotX = sneaking ? rotSneakX : rotX;
            finalRotY = sneaking ? rotSneakY : rotY;
            finalRotZ = sneaking ? rotSneakZ : rotZ;
        }

        Quaternionf leftRot = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(finalRotY),
                (float) Math.toRadians(finalRotX),
                (float) Math.toRadians(finalRotZ));

        display.setTransformation(new Transformation(
                new Vector3f(xOffset, yOffset, zOffset),
                leftRot,
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
    }

    public float getCosmeticScale(Player player, CosmeticType type) {
        Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
        Cosmetic cosmetic = cMap != null ? cMap.get(type) : null;
        if (cosmetic != null && cosmetic.getScale() != null) {
            return cosmetic.getScale().floatValue();
        }
        double configScale = switch (type) {
            case HAT -> plugin.getConfig().getDouble("rendering.offsets.hat.scale", 1.0);
            case BACKPACK -> plugin.getConfig().getDouble("rendering.offsets.backpack.scale", 0.70);
            case WINGS -> plugin.getConfig().getDouble("rendering.offsets.wings.scale", 1.05);
            case BALLOON -> plugin.getConfig().getDouble("rendering.offsets.balloon.scale", 0.85);
            case HAND -> plugin.getConfig().getDouble("rendering.offsets.hand.scale", 0.50);
            default -> 0.70;
        };
        return (float) configScale;
    }

    public boolean toggleSelfView(Player player) {
        if (player == null)
            return false;
        if (selfViewOverride.contains(player.getUniqueId())) {
            selfViewOverride.remove(player.getUniqueId());
        } else {
            selfViewOverride.add(player.getUniqueId());
        }
        boolean hidden = isSelfHidden(player);
        applySelfVisibility(player, !hidden);
        return !hidden;
    }

    public void applySelfVisibility(Player player, boolean visible) {
        if (player == null)
            return;
        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map == null || map.isEmpty())
            return;
        for (ItemDisplay display : map.values()) {
            if (display != null && display.isValid()) {
                if (visible) {
                    player.showEntity(plugin, display);
                } else {
                    player.hideEntity(plugin, display);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;

        double moveX = to.getX() - from.getX();
        double moveY = to.getY() - from.getY();
        double moveZ = to.getZ() - from.getZ();
        float dyaw = Math.abs(from.getYaw() - to.getYaw());
        float dpitch = Math.abs(from.getPitch() - to.getPitch());
        double horizSpeedSq = moveX * moveX + moveZ * moveZ;
        double totalSpeedSq = horizSpeedSq + moveY * moveY;

        // Skip micro-jitter
        if (totalSpeedSq < 0.00005 && dyaw < 0.05f && dpitch < 0.05f) {
            return;
        }

        Player player = event.getPlayer();
        long currentTick = Bukkit.getCurrentTick();

        boolean isTranslating = (totalSpeedSq > 0.0002);
        boolean isMoving;
        Vector velocity;

        if (isTranslating) {
            velocity = new Vector(moveX, moveY, moveZ);
            playerVelocityMap.put(player.getUniqueId(), velocity);
            lastMoveTickMap.put(player.getUniqueId(), currentTick);
            isMoving = true;
        } else {
            // Positional translation is ~0 (e.g. rotation/look packet), but player might
            // still be in motion from recent ticks
            Long lastMove = lastMoveTickMap.get(player.getUniqueId());
            if (lastMove != null && (currentTick - lastMove) <= 2) {
                velocity = playerVelocityMap.getOrDefault(player.getUniqueId(), new Vector(0, 0, 0));
                isMoving = true;
            } else {
                velocity = new Vector(0, 0, 0);
                playerVelocityMap.remove(player.getUniqueId());
                isMoving = false;
            }
        }

        // Motion Extrapolation:
        // Local player movement is client-predicted and rendered 1 tick ahead of server
        // state.
        // Adding the player's 1-tick velocity vector cancels out the client-prediction
        // latency,
        // ensuring the cosmetic moves in 100% lockstep with zero pixel trailing!
        Location renderLoc = to.clone();
        if (isMoving && horizSpeedSq <= 1.5) {
            renderLoc.add(velocity.getX(), velocity.getY(), velocity.getZ());
        }

        syncDisplayLocations(player, renderLoc, isMoving);
    }

    private void syncDisplayLocations(Player player, Location loc) {
        syncDisplayLocations(player, loc, false);
    }

    private void syncDisplayLocations(Player player, Location loc, boolean isMoving) {
        if (player == null || !player.isOnline() || loc == null)
            return;
        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map == null || map.isEmpty())
            return;

        boolean isHiddenBySpectatorOrInvis = (plugin.getConfig().getBoolean("rendering.hide-when-spectator", true)
                && player.getGameMode() == GameMode.SPECTATOR)
                || (plugin.getConfig().getBoolean("rendering.hide-when-invisible", true)
                        && player.hasPotionEffect(PotionEffectType.INVISIBILITY));

        for (Map.Entry<CosmeticType, ItemDisplay> entry : map.entrySet()) {
            ItemDisplay display = entry.getValue();
            if (display != null && display.isValid()) {
                CosmeticType type = entry.getKey();

                if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
                    if (!player.getPassengers().contains(display)) {
                        player.addPassenger(display);
                    }
                    display.setRotation(player.getBodyYaw(), isHorizontalPose(player) ? loc.getPitch() : 0f);

                    float lookDownPitch = (float) plugin.getConfig().getDouble("rendering.look-down-hide-pitch", 30.0);
                    boolean shouldHideFromSelf = isSelfHidden(player, type)
                            || (loc.getPitch() > lookDownPitch && !isHorizontalPose(player));

                    // Use zero-scale transformation for client self-hiding so passenger mounting is NEVER severed
                    float targetScale = shouldHideFromSelf ? 0.0f : getCosmeticScale(player, type);
                    applyPassengerTransform(player, type, display, targetScale, player.isSneaking());

                    if (isHiddenBySpectatorOrInvis) {
                        player.hideEntity(plugin, display);
                    } else {
                        player.showEntity(plugin, display);
                    }
                    continue;
                }

                if (isSelfHidden(player, type) || (type == CosmeticType.HAT && loc.getPitch() > 45.0f)
                        || isHiddenBySpectatorOrInvis) {
                    player.hideEntity(plugin, display);
                } else {
                    player.showEntity(plugin, display);
                }

                updateSingleDisplay(player, type, display, loc, isMoving);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        boolean sneaking = event.isSneaking();
        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map != null) {
            for (Map.Entry<CosmeticType, ItemDisplay> entry : map.entrySet()) {
                CosmeticType type = entry.getKey();
                if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
                    ItemDisplay display = entry.getValue();
                    if (display != null && display.isValid()) {
                        display.setInterpolationDuration(2);
                        display.setInterpolationDelay(0);
                        applyPassengerTransform(player, type, display, getCosmeticScale(player, type), sneaking);
                    }
                }
            }
        }
        syncDisplayLocations(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        syncDisplayLocations(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            syncDisplayLocations(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    resyncPassengerDisplays(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(org.bukkit.event.vehicle.VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    resyncPassengerDisplays(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMount(org.bukkit.event.entity.EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    resyncPassengerDisplays(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    resyncPassengerDisplays(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getSlotType() == com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.HEAD) {
            ItemStack hat = playerHatMap.get(player.getUniqueId());
            if (hat != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        broadcastEquipmentChange(player, EquipmentSlot.HEAD, hat);
                        player.sendEquipmentChange(player, EquipmentSlot.HEAD, hat);
                    }
                });
            }
        } else if (event.getSlotType() == com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.FEET) {
            ItemStack foot = playerFootMap.get(player.getUniqueId());
            if (foot != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline())
                        broadcastEquipmentChange(player, EquipmentSlot.FEET, foot);
                });
            }
        }
    }

    public boolean isCosmeticItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;

        // 1. Check PDC marker
        NamespacedKey key = new NamespacedKey("hyrocosmetics", "cosmetic_item");
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return true;
        }

        // 2. Check CustomModelData against registered cosmetics (fallback protection)
        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            for (Cosmetic c : plugin.getManager().getAllCosmetics()) {
                if (c.getCustomModelData() != null && c.getCustomModelData() == cmd) {
                    return true;
                }
            }
        }

        return false;
    }

    public void sanitizePlayerInventory(Player player) {
        if (player == null || !player.isOnline())
            return;
        boolean purged = false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isCosmeticItem(item)) {
                inv.setItem(i, new ItemStack(Material.AIR));
                purged = true;
            }
        }
        if (isCosmeticItem(player.getItemOnCursor())) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
            purged = true;
        }
        if (purged) {
            player.updateInventory();
            resyncPlayerEquipment(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryCreative(org.bukkit.event.inventory.InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        int slot = event.getSlot();

        // 1. Block any cosmetic item from entering cursor or inventory slots in
        // creative mode
        if (isCosmeticItem(cursor) || isCosmeticItem(current)) {
            event.setCancelled(true);
            event.setCursor(new ItemStack(Material.AIR));
            player.setItemOnCursor(new ItemStack(Material.AIR));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    sanitizePlayerInventory(player);
                    resyncPlayerEquipment(player);
                }
            });
            return;
        }

        // 2. Prevent picking from cosmetic equipment slots in Creative mode
        // Raw slots in creative survival tab: 5 = Helmet, 8 = Boots, 45 = OffHand
        if ((slot == 5 && playerHatMap.containsKey(player.getUniqueId())) ||
                (slot == 8 && playerFootMap.containsKey(player.getUniqueId())) ||
                (slot == 45 && playerHandMap.containsKey(player.getUniqueId()))) {
            event.setCancelled(true);
            event.setCursor(new ItemStack(Material.AIR));
            player.setItemOnCursor(new ItemStack(Material.AIR));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    sanitizePlayerInventory(player);
                    resyncPlayerEquipment(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        int rawSlot = event.getRawSlot();

        // 1. If cursor or current item is a cosmetic item, cancel and purge
        if (isCosmeticItem(cursor)) {
            event.setCancelled(true);
            player.setItemOnCursor(new ItemStack(Material.AIR));
            player.updateInventory();
            return;
        }
        if (isCosmeticItem(current)) {
            event.setCancelled(true);
            event.setCurrentItem(new ItemStack(Material.AIR));
            player.updateInventory();
            return;
        }

        // 2. If clicking on equipment slot that has a cosmetic active
        if ((rawSlot == 5 && playerHatMap.containsKey(player.getUniqueId())) ||
                (rawSlot == 8 && playerFootMap.containsKey(player.getUniqueId())) ||
                (rawSlot == 45 && playerHandMap.containsKey(player.getUniqueId()))) {
            if (cursor == null || cursor.getType().isAir()) {
                event.setCancelled(true);
                player.setItemOnCursor(new ItemStack(Material.AIR));
                player.updateInventory();
                return;
            }
        }

        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR
                || rawSlot == 45
                || event.isShiftClick()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    resyncPlayerEquipment(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.getItemDrop() != null && isCosmeticItem(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            event.setCancelled(true);
            resyncPlayerEquipment(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isCosmeticItem(event.getItem())) {
            event.setCancelled(true);
            if (event.getItem() != null)
                event.getItem().setAmount(0);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (isCosmeticItem(event.getItemInHand())) {
            event.setCancelled(true);
            if (event.getItemInHand() != null)
                event.getItemInHand().setAmount(0);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isCosmeticItem(event.getMainHandItem()) || isCosmeticItem(event.getOffHandItem())) {
            event.setCancelled(true);
            resyncPlayerEquipment(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (isCosmeticItem(event.getItem().getItemStack())) {
            event.getItem().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sanitizePlayerInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null)
            return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;
            Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
            if (map != null) {
                for (Map.Entry<CosmeticType, ItemDisplay> entry : map.entrySet()) {
                    CosmeticType type = entry.getKey();
                    ItemDisplay display = entry.getValue();
                    if (display != null && display.isValid() && (entry.getKey() == CosmeticType.BACKPACK
                            || entry.getKey() == CosmeticType.WINGS)) {
                        display.setTeleportDuration(0);
                    }
                }
            }
            syncDisplayLocations(player, player.getLocation());
            resyncPlayerEquipment(player);
        }, 2L);
    }

    public float calculateBodyYaw(Player player, Location loc, boolean isMoving) {
        if (isHorizontalPose(player)) {
            return loc.getYaw();
        }

        return player.getBodyYaw();
    }

    private void updateSingleDisplay(Player player, CosmeticType type, ItemDisplay display, Location pLoc) {
        updateSingleDisplay(player, type, display, pLoc, false);
    }

    private void updateSingleDisplay(Player player, CosmeticType type, ItemDisplay display, Location pLoc,
            boolean isMoving) {
        if (display == null || !display.isValid() || pLoc == null)
            return;

        // Backpack and wings must use the torso direction, never the head direction.
        float effectiveYaw = calculateBodyYaw(player, pLoc, isMoving);

        if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
            if (!player.getPassengers().contains(display)) {
                player.addPassenger(display);
            }
            display.setRotation(player.getBodyYaw(), isHorizontalPose(player) ? pLoc.getPitch() : 0f);
            applyPassengerTransform(player, type, display, getCosmeticScale(player, type), player.isSneaking());
            return;
        }

        // Dynamic teleport duration:
        // 1 tick duration during movement enables smooth 144 FPS client-side
        // interpolation without discrete 20 FPS step jumps
        int targetTeleportDuration = isMoving ? 1 : 0;
        if (display.getTeleportDuration() != targetTeleportDuration) {
            display.setTeleportDuration(targetTeleportDuration);
        }

        boolean sneaking = player.isSneaking();
        boolean horizontal = isHorizontalPose(player);
        double bodyAngleRad = Math.toRadians(effectiveYaw);

        Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
        Cosmetic cosmetic = cMap != null ? cMap.get(type) : null;
        double customOffsetY = (cosmetic != null && cosmetic.getOffsetY() != null) ? cosmetic.getOffsetY() : 0.0;
        float defaultScale = switch (type) {
            case HAT -> 1.0f;
            case BACKPACK -> 0.70f;
            case WINGS -> 1.0f;
            case BALLOON -> 0.85f;
            case HAND -> 0.50f;
            default -> 0.70f;
        };
        float scale = (cosmetic != null && cosmetic.getScale() != null) ? cosmetic.getScale().floatValue()
                : defaultScale;

        switch (type) {
            case BACKPACK -> {
                double baseY = plugin.getConfig().getDouble("rendering.offsets.backpack.y", 0.92) + customOffsetY;
                double sneakY = plugin.getConfig().getDouble("rendering.offsets.backpack.sneak-y", 0.72)
                        + customOffsetY;
                double baseDist = plugin.getConfig().getDouble("rendering.offsets.backpack.distance", 0.18);
                double sneakDist = plugin.getConfig().getDouble("rendering.offsets.backpack.sneak-distance", 0.22);

                double dist = sneaking ? sneakDist : baseDist;
                double yOffset = horizontal ? (0.60 + customOffsetY) : (sneaking ? sneakY : baseY);
                float currentScale = scale;

                // Anti-obstruction when looking straight down (pitch > 55 deg):
                // When looking down at feet/ground, adjust offset and scale so straps/backpack
                // never block first-person camera!
                float pitch = pLoc.getPitch();
                if (pitch > 55.0f && !horizontal) {
                    float downFactor = (pitch - 55.0f) / 35.0f; // 0.0 at 55 deg, 1.0 at 90 deg
                    dist += downFactor * 0.25;
                    yOffset -= downFactor * 0.40;
                    if (pitch > 70.0f) {
                        float shrinkFactor = (pitch - 70.0f) / 20.0f;
                        currentScale *= (1.0f - shrinkFactor * 0.70f);
                    }
                }

                // Exact backward vector relative to player torso
                double xOffset = Math.sin(bodyAngleRad) * dist;
                double zOffset = -Math.cos(bodyAngleRad) * dist;

                Location loc = pLoc.clone().add(xOffset, yOffset, zOffset);
                float bpRotY = (float) plugin.getConfig().getDouble("rendering.offsets.backpack.rotation-y", 180.0);
                float bpRotSneakY = (float) plugin.getConfig().getDouble("rendering.offsets.backpack.rotation-sneak-y",
                        plugin.getConfig().getDouble("rendering.offsets.backpack.sneak-rotation-y", bpRotY));
                loc.setYaw(effectiveYaw + (sneaking ? bpRotSneakY : bpRotY));
                loc.setPitch(horizontal ? pLoc.getPitch() : (sneaking ? 25.0f : 0.0f));

                // Update transformation only if scale changed
                Transformation currentTransform = display.getTransformation();
                if (Math.abs(currentTransform.getScale().x() - currentScale) > 0.01f) {
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f),
                            new Vector3f(currentScale, currentScale, currentScale),
                            new AxisAngle4f(0f, 0f, 1f, 0f)));
                }

                display.teleport(loc);
            }
            case WINGS -> {
                double baseY = plugin.getConfig().getDouble("rendering.offsets.wings.y", 0.82) + customOffsetY;
                double sneakY = plugin.getConfig().getDouble("rendering.offsets.wings.sneak-y", 0.64) + customOffsetY;
                double baseDist = plugin.getConfig().getDouble("rendering.offsets.wings.distance", 0.18);

                double dist = sneaking ? (baseDist + 0.04) : baseDist;
                double yOffset = horizontal ? (0.65 + customOffsetY) : (sneaking ? sneakY : baseY);
                float currentScale = scale;

                // Anti-obstruction when looking straight down (pitch > 55 deg)
                float pitch = pLoc.getPitch();
                if (pitch > 55.0f && !horizontal) {
                    float downFactor = (pitch - 55.0f) / 35.0f;
                    dist += downFactor * 0.25;
                    yOffset -= downFactor * 0.45;
                    if (pitch > 70.0f) {
                        float shrinkFactor = (pitch - 70.0f) / 20.0f;
                        currentScale *= (1.0f - shrinkFactor * 0.70f);
                    }
                }

                double xOffset = Math.sin(bodyAngleRad) * dist;
                double zOffset = -Math.cos(bodyAngleRad) * dist;

                Location loc = pLoc.clone().add(xOffset, yOffset, zOffset);
                float wingsRotY = (float) plugin.getConfig().getDouble("rendering.offsets.wings.rotation-y", 0.0);
                float wingsRotSneakY = (float) plugin.getConfig().getDouble("rendering.offsets.wings.rotation-sneak-y",
                        plugin.getConfig().getDouble("rendering.offsets.wings.sneak-rotation-y", wingsRotY));
                loc.setYaw(effectiveYaw + (sneaking ? wingsRotSneakY : wingsRotY));
                loc.setPitch(horizontal ? pLoc.getPitch() : (sneaking ? 25.0f : 0.0f));

                Transformation currentTransform = display.getTransformation();
                if (Math.abs(currentTransform.getScale().x() - currentScale) > 0.01f) {
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f),
                            new Vector3f(currentScale, currentScale, currentScale),
                            new AxisAngle4f(0f, 0f, 1f, 0f)));
                }

                display.teleport(loc);
            }
            case HAT -> {
                float hatScale = getCosmeticScale(player, type);
                Transformation currentTransform = display.getTransformation();
                if (Math.abs(currentTransform.getScale().x() - hatScale) > 0.005f) {
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f),
                            new Vector3f(hatScale, hatScale, hatScale),
                            new AxisAngle4f(0f, 0f, 1f, 0f)));
                }

                double baseY = plugin.getConfig().getDouble("rendering.offsets.hat.y", 1.82) + customOffsetY;
                double sneakY = plugin.getConfig().getDouble("rendering.offsets.hat.sneak-y", 1.48) + customOffsetY;
                float hatRotY = (float) plugin.getConfig().getDouble("rendering.offsets.hat.rotation-y", 0.0);
                float hatRotSneakY = (float) plugin.getConfig().getDouble("rendering.offsets.hat.rotation-sneak-y",
                        plugin.getConfig().getDouble("rendering.offsets.hat.sneak-rotation-y", hatRotY));
                Location loc = pLoc.clone().add(0, sneaking ? sneakY : baseY, 0);
                loc.setYaw(pLoc.getYaw() + (sneaking ? hatRotSneakY : hatRotY));
                loc.setPitch(pLoc.getPitch());
                display.teleport(loc);

                boolean isHidden = (plugin.getConfig().getBoolean("rendering.hide-when-spectator", true)
                        && player.getGameMode() == GameMode.SPECTATOR)
                        || (plugin.getConfig().getBoolean("rendering.hide-when-invisible", true)
                                && player.hasPotionEffect(PotionEffectType.INVISIBILITY));

                if (isSelfHidden(player, type) || pLoc.getPitch() > 45.0f) {
                    player.hideEntity(plugin, display);
                } else if (!isHidden) {
                    player.showEntity(plugin, display);
                }
            }
            case BALLOON -> {
                double dist = plugin.getConfig().getDouble("rendering.offsets.balloon.distance", 0.45);
                double baseY = plugin.getConfig().getDouble("rendering.offsets.balloon.y", 2.15) + customOffsetY;
                double sneakY = plugin.getConfig().getDouble("rendering.offsets.balloon.sneak-y", 1.85) + customOffsetY;
                double bob = Math.sin(System.currentTimeMillis() / 320.0) * 0.07;

                double shoulderRad = Math.toRadians(effectiveYaw + 130);
                double xOffset = Math.sin(shoulderRad) * dist;
                double zOffset = -Math.cos(shoulderRad) * dist;
                double yOffset = (sneaking ? sneakY : baseY) + bob;

                Location loc = pLoc.clone().add(xOffset, yOffset, zOffset);
                float balloonRotY = (float) plugin.getConfig().getDouble("rendering.offsets.balloon.rotation-y", 0.0);
                float balloonRotSneakY = (float) plugin.getConfig().getDouble(
                        "rendering.offsets.balloon.rotation-sneak-y",
                        plugin.getConfig().getDouble("rendering.offsets.balloon.sneak-rotation-y", balloonRotY));
                loc.setYaw(effectiveYaw + (sneaking ? balloonRotSneakY : balloonRotY));
                loc.setPitch(0f);
                display.teleport(loc);
            }
            case HAND -> {
                double dist = plugin.getConfig().getDouble("rendering.offsets.hand.distance", 0.38);
                double baseY = plugin.getConfig().getDouble("rendering.offsets.hand.y", 0.95) + customOffsetY;
                double sneakY = plugin.getConfig().getDouble("rendering.offsets.hand.sneak-y", 0.70) + customOffsetY;

                double sideRad = Math.toRadians(effectiveYaw + 90);
                double xOffset = -Math.sin(sideRad) * dist;
                double zOffset = Math.cos(sideRad) * dist;
                double yOffset = sneaking ? sneakY : baseY;

                Location loc = pLoc.clone().add(xOffset, yOffset, zOffset);
                float handRotY = (float) plugin.getConfig().getDouble("rendering.offsets.hand.rotation-y", 0.0);
                float handRotSneakY = (float) plugin.getConfig().getDouble("rendering.offsets.hand.rotation-sneak-y",
                        plugin.getConfig().getDouble("rendering.offsets.hand.sneak-rotation-y", handRotY));
                loc.setYaw(effectiveYaw + (sneaking ? handRotSneakY : handRotY));
                loc.setPitch(0f);
                display.teleport(loc);
            }
            default -> {
            }
        }
    }

    public void refreshAllDisplays() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(p.getUniqueId());
            if (map != null) {
                for (Map.Entry<CosmeticType, ItemDisplay> entry : map.entrySet()) {
                    ItemDisplay d = entry.getValue();
                    if (d != null && d.isValid()) {
                        CosmeticType type = entry.getKey();
                        if (type == CosmeticType.BACKPACK || type == CosmeticType.WINGS) {
                            applyPassengerTransform(p, type, d, getCosmeticScale(p, type), p.isSneaking());
                            d.setRotation(p.getBodyYaw(), isHorizontalPose(p) ? p.getLocation().getPitch() : 0f);
                            if (!p.getPassengers().contains(d)) {
                                p.addPassenger(d);
                            }
                            if (isSelfHidden(p, type)) {
                                p.hideEntity(plugin, d);
                            } else {
                                p.showEntity(plugin, d);
                            }
                        } else {
                            d.setTeleportDuration(0);
                            updateSingleDisplay(p, type, d, p.getLocation());
                        }
                    }
                }
            }
            resyncPlayerEquipment(p);
        }
    }

    public void broadcastEquipmentChange(Player player, EquipmentSlot slot, ItemStack item) {
        if (player == null || !player.isOnline())
            return;
        ItemStack toSend = (item != null) ? item : new ItemStack(Material.AIR);
        try {
            // NEVER send fake equipment packets to the player themselves!
            // When entity == viewer, Minecraft client places the item into its own
            // inventory armor slot,
            // creating an unmovable ghost item that prevents real armor from being equipped
            // or moved.
            // Only send to OTHER viewers so other players see the cosmetic appearance.
            for (Player viewer : player.getWorld().getPlayers()) {
                if (viewer != player && viewer.canSee(player)) {
                    viewer.sendEquipmentChange(player, slot, toSend);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!joiner.isOnline())
                return;

            // Send joiner all existing equipped cosmetics of other players
            for (Player other : joiner.getWorld().getPlayers()) {
                if (other.equals(joiner))
                    continue;
                ItemStack hat = playerHatMap.get(other.getUniqueId());
                if (hat != null)
                    joiner.sendEquipmentChange(other, EquipmentSlot.HEAD, hat);
                ItemStack foot = playerFootMap.get(other.getUniqueId());
                if (foot != null)
                    joiner.sendEquipmentChange(other, EquipmentSlot.FEET, foot);
                ItemStack hand = playerHandMap.get(other.getUniqueId());
                if (hand != null)
                    joiner.sendEquipmentChange(other, EquipmentSlot.OFF_HAND, hand);
            }

            // Sanitize inventory & send joiner their own active cosmetics & clean real
            // armor
            sanitizePlayerInventory(joiner);
            resyncPlayerEquipment(joiner);
        }, 5L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;
            sanitizePlayerInventory(player);
            resyncPlayerEquipment(player);

            // Re-spawn displays in the new world
            Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
            if (map != null) {
                for (Map.Entry<CosmeticType, ItemDisplay> entry : new HashMap<>(map).entrySet()) {
                    ItemDisplay old = entry.getValue();
                    ItemStack stack = (old != null && old.isValid()) ? old.getItemStack() : null;
                    if (old != null)
                        old.remove();
                    if (stack != null) {
                        Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
                        Cosmetic c = cMap != null ? cMap.get(entry.getKey()) : null;
                        if (c != null)
                            spawnDisplayCosmetic(player, c, stack);
                    }
                }
            }
        }, 3L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        event.getDrops().removeIf(this::isCosmeticItem);
        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map != null) {
            map.values().forEach(Entity::remove);
            map.clear();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;
            resyncPlayerEquipment(player);
            plugin.getManager().loadPlayer(player);
        }, 3L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sanitizePlayerInventory(player);
            resyncPlayerEquipment(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastMoveTickMap.remove(event.getPlayer().getUniqueId());
        playerVelocityMap.remove(event.getPlayer().getUniqueId());
        removeCosmetics(event.getPlayer());
    }

    private void resyncPlayerEquipment(Player player) {
        if (player == null || !player.isOnline())
            return;

        // Ensure the player's own client ALWAYS has their REAL vanilla inventory armor
        // & offhand
        // This guarantees NO ghost items, allows real armor to be worn, moved, and
        // swapped freely!
        try {
            ItemStack realHelmet = player.getInventory().getHelmet();
            ItemStack realChest = player.getInventory().getChestplate();
            ItemStack realLegs = player.getInventory().getLeggings();
            ItemStack realBoots = player.getInventory().getBoots();
            ItemStack realOffHand = player.getInventory().getItemInOffHand();

            ItemStack cosmeticHead = playerHatMap.get(player.getUniqueId());
            ItemStack cosmeticFoot = playerFootMap.get(player.getUniqueId());
            ItemStack cosmeticHand = playerHandMap.get(player.getUniqueId());

            player.sendEquipmentChange(player, EquipmentSlot.HEAD, cosmeticHead != null ? cosmeticHead
                    : (realHelmet != null ? realHelmet : new ItemStack(Material.AIR)));
            player.sendEquipmentChange(player, EquipmentSlot.CHEST,
                    realChest != null ? realChest : new ItemStack(Material.AIR));
            player.sendEquipmentChange(player, EquipmentSlot.LEGS,
                    realLegs != null ? realLegs : new ItemStack(Material.AIR));
            player.sendEquipmentChange(player, EquipmentSlot.FEET, cosmeticFoot != null ? cosmeticFoot
                    : (realBoots != null ? realBoots : new ItemStack(Material.AIR)));
            player.sendEquipmentChange(player, EquipmentSlot.OFF_HAND, cosmeticHand != null ? cosmeticHand
                    : (realOffHand != null ? realOffHand : new ItemStack(Material.AIR)));
        } catch (Throwable ignored) {
        }

        // Virtual equipment for HEAD, FOOT and HAND broadcasted to OTHER viewers
        ItemStack head = playerHatMap.get(player.getUniqueId());
        if (head != null)
            broadcastEquipmentChange(player, EquipmentSlot.HEAD, head);

        ItemStack foot = playerFootMap.get(player.getUniqueId());
        if (foot != null)
            broadcastEquipmentChange(player, EquipmentSlot.FEET, foot);

        ItemStack hand = playerHandMap.get(player.getUniqueId());
        if (hand != null)
            broadcastEquipmentChange(player, EquipmentSlot.OFF_HAND, hand);
    }

    private void tickCosmetics() {
        boolean hideInvis = plugin.getConfig().getBoolean("rendering.hide-when-invisible", true);
        boolean hideSpectator = plugin.getConfig().getBoolean("rendering.hide-when-spectator", true);
        long currentTick = Bukkit.getCurrentTick();

        // 1. Sync 3D Displays (Visibility & background smooth position)
        for (Map.Entry<UUID, Map<CosmeticType, ItemDisplay>> entry : playerDisplayMap.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                entry.getValue().values().forEach(Entity::remove);
                continue;
            }

            boolean hidden = (hideSpectator && player.getGameMode() == GameMode.SPECTATOR) ||
                    (hideInvis && player.hasPotionEffect(PotionEffectType.INVISIBILITY));

            Long lastMove = lastMoveTickMap.get(player.getUniqueId());
            boolean recentlyMoved = (lastMove != null && (currentTick - lastMove) <= 2);

            for (Map.Entry<CosmeticType, ItemDisplay> cEntry : entry.getValue().entrySet()) {
                ItemDisplay display = cEntry.getValue();
                if (display != null && display.isValid()) {
                    display.setViewRange(hidden ? 0f : 1.0f);
                    if (!hidden) {
                        CosmeticType cType = cEntry.getKey();
                        // While player is actively moving, onPlayerMove has exclusive authority over
                        // position updates!
                        // tickCosmetics only syncs position when idle (or floating animations like
                        // BALLOON)
                        if (!recentlyMoved || cType == CosmeticType.BALLOON) {
                            updateSingleDisplay(player, cType, display, player.getLocation(), false);
                        }
                    }
                }
            }
        }

        // 2. Periodic light resync for virtual equipment cosmetics & inventory
        // anti-dupe purge (every 20 ticks = 1 second)
        if (plugin.getServer().getCurrentTick() % 20 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                sanitizePlayerInventory(p);
            }
            for (Map.Entry<UUID, ItemStack> entry : playerHatMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline())
                    broadcastEquipmentChange(p, EquipmentSlot.HEAD, entry.getValue());
            }
            for (Map.Entry<UUID, ItemStack> entry : playerFootMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline())
                    broadcastEquipmentChange(p, EquipmentSlot.FEET, entry.getValue());
            }
            for (Map.Entry<UUID, ItemStack> entry : playerHandMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline())
                    broadcastEquipmentChange(p, EquipmentSlot.OFF_HAND, entry.getValue());
            }
        }
    }

    public void removeCosmeticType(Player player, CosmeticType type) {
        if (player == null)
            return;

        if (type == CosmeticType.HAT) {
            playerHatMap.remove(player.getUniqueId());
            ItemStack real = player.getInventory().getHelmet();
            ItemStack restore = (real != null) ? real : new ItemStack(Material.AIR);
            broadcastEquipmentChange(player, EquipmentSlot.HEAD, restore);
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, restore);

            Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
            if (map != null) {
                ItemDisplay display = map.remove(CosmeticType.HAT);
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
            if (cMap != null)
                cMap.remove(CosmeticType.HAT);
            return;
        } else if (type == CosmeticType.FOOT) {
            if (playerFootMap.remove(player.getUniqueId()) != null) {
                ItemStack real = player.getInventory().getBoots();
                ItemStack restore = (real != null) ? real : new ItemStack(Material.AIR);
                broadcastEquipmentChange(player, EquipmentSlot.FEET, restore);
                player.sendEquipmentChange(player, EquipmentSlot.FEET, restore);
            }
            Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
            if (cMap != null)
                cMap.remove(CosmeticType.FOOT);
            return;
        } else if (type == CosmeticType.HAND) {
            if (playerHandMap.remove(player.getUniqueId()) != null) {
                ItemStack real = player.getInventory().getItemInOffHand();
                ItemStack restore = (real != null) ? real : new ItemStack(Material.AIR);
                broadcastEquipmentChange(player, EquipmentSlot.OFF_HAND, restore);
                player.sendEquipmentChange(player, EquipmentSlot.OFF_HAND, restore);
            }
            Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
            if (map != null) {
                ItemDisplay display = map.remove(CosmeticType.HAND);
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
            if (cMap != null)
                cMap.remove(CosmeticType.HAND);
            return;
        }

        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.get(player.getUniqueId());
        if (map != null) {
            ItemDisplay display = map.remove(type);
            if (display != null && display.isValid()) {
                player.removePassenger(display);
                display.remove();
            }
        }
        Map<CosmeticType, Cosmetic> cMap = playerCosmeticObjMap.get(player.getUniqueId());
        if (cMap != null)
            cMap.remove(type);
    }

    public void removeCosmetics(Player player) {
        if (player == null)
            return;

        removeCosmeticType(player, CosmeticType.HAT);
        removeCosmeticType(player, CosmeticType.FOOT);
        removeCosmeticType(player, CosmeticType.HAND);

        Map<CosmeticType, ItemDisplay> map = playerDisplayMap.remove(player.getUniqueId());
        if (map != null) {
            for (ItemDisplay d : map.values()) {
                if (d != null && d.isValid()) {
                    player.removePassenger(d);
                    d.remove();
                }
            }
            map.clear();
        }
        playerCosmeticObjMap.remove(player.getUniqueId());
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (Map.Entry<UUID, Map<CosmeticType, ItemDisplay>> entry : playerDisplayMap.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            for (ItemDisplay d : entry.getValue().values()) {
                if (d != null && d.isValid()) {
                    if (p != null)
                        p.removePassenger(d);
                    d.remove();
                }
            }
            entry.getValue().clear();
        }
        playerDisplayMap.clear();
        playerCosmeticObjMap.clear();

        for (UUID uuid : new HashSet<>(playerHatMap.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                removeCosmeticType(p, CosmeticType.HAT);
        }
        for (UUID uuid : new HashSet<>(playerFootMap.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                removeCosmeticType(p, CosmeticType.FOOT);
        }
        for (UUID uuid : new HashSet<>(playerHandMap.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                removeCosmeticType(p, CosmeticType.HAND);
        }
        playerHatMap.clear();
        playerFootMap.clear();
        playerHandMap.clear();
        lastMoveTickMap.clear();
    }
}
