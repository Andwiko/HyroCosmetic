package com.hyrocosmetics.effects;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.hyrocosmetics.utils.ParticleUtil;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.meta.FireworkMeta;

public class KillEffectListener implements Listener {

    private final HyroCosmetics plugin;

    public KillEffectListener(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        String killEffectId = plugin.getManager().getPlayerData(killer.getUniqueId()).getEquipped(CosmeticType.KILL_EFFECT);
        if (killEffectId == null) return;

        Cosmetic cosmetic = plugin.getManager().getCosmetic(killEffectId);
        if (cosmetic == null) return;

        Location deathLoc = event.getEntity().getLocation();
        if (deathLoc.getWorld() == null) return;
        String action = cosmetic.getEffectAction();
        if (action == null) return;

        switch (action.toUpperCase()) {
            case "LIGHTNING" -> {
                deathLoc.getWorld().strikeLightningEffect(deathLoc);
            }
            case "FIREWORK" -> {
                Firework fw = deathLoc.getWorld().spawn(deathLoc.clone().add(0, 1, 0), Firework.class);
                FireworkMeta fwm = fw.getFireworkMeta();
                fwm.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                        .withFade(Color.WHITE)
                        .trail(true)
                        .flicker(true)
                        .build());
                fwm.setPower(1);
                fw.setFireworkMeta(fwm);
                fw.detonate();
            }
            case "SOUL" -> {
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "SOUL_FIRE_FLAME", deathLoc.clone().add(0, 1, 0), 45, 0.5, 0.6, 0.5, 0.05);
                try {
                    deathLoc.getWorld().playSound(deathLoc, Sound.valueOf("PARTICLE_SOUL_ESCAPE"), 1.0f, 1.0f);
                } catch (Throwable ignored) {
                    deathLoc.getWorld().playSound(deathLoc, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 1.0f);
                }
            }
            case "BLOOD" -> {
                try {
                    Particle blockCrack = ParticleUtil.resolveParticle("BLOCK_CRACK");
                    if (blockCrack == null) blockCrack = ParticleUtil.resolveParticle("BLOCK");
                    if (blockCrack != null) {
                        deathLoc.getWorld().spawnParticle(blockCrack, deathLoc.clone().add(0, 1, 0), 70, 0.5, 0.6, 0.5, Material.REDSTONE_BLOCK.createBlockData());
                    } else {
                        ParticleUtil.spawnParticle(deathLoc.getWorld(), "REDSTONE", deathLoc.clone().add(0, 1, 0), 70, 0.5, 0.6, 0.5, 1.0);
                    }
                } catch (Throwable ignored) {
                    ParticleUtil.spawnParticle(deathLoc.getWorld(), "REDSTONE", deathLoc.clone().add(0, 1, 0), 70, 0.5, 0.6, 0.5, 1.0);
                }
                deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
                deathLoc.getWorld().playSound(deathLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
            }
            case "WIND_BURST" -> {
                // 1.21 Breeze wind burst explosion
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "WIND_EXPLOSION", deathLoc.clone().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.0);
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "GUST", deathLoc.clone().add(0, 1.0, 0), 20, 0.5, 0.5, 0.5, 0.05);
                try {
                    deathLoc.getWorld().playSound(deathLoc, Sound.valueOf("ENTITY_BREEZE_WIND_BURST"), 1.0f, 1.0f);
                } catch (Throwable ignored) {
                    deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
                }
            }
            case "MACE_SLAM" -> {
                // 1.21 Mace ground smash shockwave
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "DUST_PLUME", deathLoc.clone().add(0, 0.2, 0), 40, 1.2, 0.1, 1.2, 0.02);
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "CRIT", deathLoc.clone().add(0, 0.5, 0), 30, 0.8, 0.4, 0.8, 0.2);
                try {
                    deathLoc.getWorld().playSound(deathLoc, Sound.valueOf("ITEM_MACE_SMASH_GROUND_HEAVY"), 1.0f, 1.0f);
                } catch (Throwable ignored) {
                    try {
                        deathLoc.getWorld().playSound(deathLoc, Sound.valueOf("ITEM_MACE_SMASH_GROUND"), 1.0f, 1.0f);
                    } catch (Throwable ignored2) {
                        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.8f);
                    }
                }
            }
            case "OMINOUS_SKULL" -> {
                // 1.21 Trial Omen spawner activation
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "OMINOUS_SPAWNING", deathLoc.clone().add(0, 1.2, 0), 35, 0.5, 0.6, 0.5, 0.05);
                ParticleUtil.spawnParticle(deathLoc.getWorld(), "RAID_OMEN", deathLoc.clone().add(0, 1.0, 0), 12, 0.4, 0.4, 0.4, 0.02);
                try {
                    deathLoc.getWorld().playSound(deathLoc, Sound.valueOf("BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE"), 1.0f, 1.0f);
                } catch (Throwable ignored) {
                    deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);
                }
            }
            default -> {}
        }
    }
}
