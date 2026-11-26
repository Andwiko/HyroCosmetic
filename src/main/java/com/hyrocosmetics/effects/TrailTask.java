package com.hyrocosmetics.effects;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.Cosmetic;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.hyrocosmetics.cosmetic.PlayerCosmeticData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TrailTask extends BukkitRunnable {

    private final HyroCosmetics plugin;

    public TrailTask(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid() || player.isDead()) continue;

            PlayerCosmeticData data = plugin.getManager().getPlayerData(player.getUniqueId());

            // 1. Particle Trail
            String trailId = data.getEquipped(CosmeticType.TRAIL);
            if (trailId != null) {
                Cosmetic c = plugin.getManager().getCosmetic(trailId);
                if (c != null && c.getParticleType() != null) {
                    spawnTrailParticle(player, c.getParticleType());
                }
            }

            // 2. Foot Trail / Footstep
            String footId = data.getEquipped(CosmeticType.FOOT);
            if (footId != null) {
                Cosmetic c = plugin.getManager().getCosmetic(footId);
                if (c != null && c.getParticleType() != null) {
                    spawnFootParticle(player, c.getParticleType());
                }
            }
        }
    }

    private void spawnTrailParticle(Player player, String particleName) {
        Location loc = player.getLocation().add(0, 0.9, 0);
        com.hyrocosmetics.utils.ParticleUtil.spawnParticle(player.getWorld(), particleName, loc, 3, 0.2, 0.3, 0.2, 0.02);
    }

    private void spawnFootParticle(Player player, String particleName) {
        Location loc = player.getLocation().add(0, 0.05, 0);
        com.hyrocosmetics.utils.ParticleUtil.spawnParticle(player.getWorld(), particleName, loc, 4, 0.2, 0.01, 0.2, 0.01);
    }
}
