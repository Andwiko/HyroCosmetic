package com.hyrocosmetics.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public final class ParticleUtil {

    private static final Map<String, String[]> ALIASES = new HashMap<>();

    static {
        // Fallback mappings between Minecraft versions
        ALIASES.put("TOTEM", new String[]{"TOTEM_OF_UNDYING", "TOTEM"});
        ALIASES.put("TOTEM_OF_UNDYING", new String[]{"TOTEM_OF_UNDYING", "TOTEM"});
        ALIASES.put("FLAME", new String[]{"FLAME", "SMALL_FLAME"});
        ALIASES.put("SOUL_FIRE_FLAME", new String[]{"SOUL_FIRE_FLAME", "SOUL"});
        ALIASES.put("SOUL", new String[]{"SOUL_FIRE_FLAME", "SOUL"});
        ALIASES.put("FIREWORK", new String[]{"FIREWORK", "FLASH"});

        // 1.21+ Particle fallbacks
        ALIASES.put("GUST", new String[]{"GUST", "SMALL_GUST", "GUST_EMITTER_SMALL", "CLOUD", "POOF"});
        ALIASES.put("WIND_EXPLOSION", new String[]{"WIND_EXPLOSION", "GUST", "EXPLOSION", "EXPLOSION_NORMAL", "POOF"});
        ALIASES.put("TRIAL_SPAWNER_DETECTION", new String[]{"TRIAL_SPAWNER_DETECTION", "TRIAL_SPAWNER_DETECTION_OMINOUS", "ELECTRIC_SPARK", "CRIT"});
        ALIASES.put("OMINOUS_SPAWNING", new String[]{"OMINOUS_SPAWNING", "RAID_OMEN", "TRIAL_OMEN", "SOUL_FIRE_FLAME", "SPELL_WITCH"});
        ALIASES.put("RAID_OMEN", new String[]{"RAID_OMEN", "OMINOUS_SPAWNING", "TRIAL_OMEN", "SOUL_FIRE_FLAME"});
        ALIASES.put("TRIAL_OMEN", new String[]{"TRIAL_OMEN", "RAID_OMEN", "OMINOUS_SPAWNING", "SOUL_FIRE_FLAME"});
        ALIASES.put("VAULT_CONNECTION", new String[]{"VAULT_CONNECTION", "ENCHANTMENT_TABLE", "PORTAL", "CRIT"});
        ALIASES.put("CHERRY_LEAVES", new String[]{"CHERRY_LEAVES", "FALLING_SPORE_BLOSSOM", "SPORE_BLOSSOM_AIR", "HEART"});
        ALIASES.put("PALE_OAK_LEAVES", new String[]{"PALE_OAK_LEAVES", "WHITE_SMOKE", "ASH", "CAMPFIRE_COSY_SMOKE"});
        ALIASES.put("WHITE_SMOKE", new String[]{"WHITE_SMOKE", "CAMPFIRE_COSY_SMOKE", "SMOKE_NORMAL", "POOF"});
    }

    private ParticleUtil() {}

    /**
     * Resolves a Particle enum value safely across 1.20 and 1.21+ versions.
     */
    public static Particle resolveParticle(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String clean = name.trim().toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");

        // 1. Direct match
        try {
            return Particle.valueOf(clean);
        } catch (IllegalArgumentException ignored) {}

        // 2. Alias resolution
        String[] candidates = ALIASES.get(clean);
        if (candidates != null) {
            for (String alias : candidates) {
                try {
                    return Particle.valueOf(alias);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 3. Heuristic matching
        for (Particle p : Particle.values()) {
            if (p.name().equalsIgnoreCase(clean) || p.name().contains(clean)) {
                return p;
            }
        }

        return null;
    }

    /**
     * Returns the Totem particle appropriate for this version (TOTEM_OF_UNDYING on 1.20.5+/1.21+, TOTEM on older).
     */
    public static Particle getTotemParticle() {
        Particle p = resolveParticle("TOTEM_OF_UNDYING");
        if (p != null) return p;
        p = resolveParticle("TOTEM");
        if (p != null) return p;
        p = resolveParticle("HAPPY_VILLAGER");
        if (p != null) return p;
        p = resolveParticle("VILLAGER_HAPPY");
        if (p != null) return p;
        return Particle.FLAME; // absolute safe fallback across all versions
    }

    /**
     * Spawns a particle safely by name without throwing exceptions.
     */
    public static boolean spawnParticle(World world, String particleName, Location loc, int count, double ox, double oy, double oz, double extra) {
        if (world == null || loc == null || particleName == null) return false;
        Particle particle = resolveParticle(particleName);
        if (particle == null) return false;

        try {
            world.spawnParticle(particle, loc, count, ox, oy, oz, extra);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
