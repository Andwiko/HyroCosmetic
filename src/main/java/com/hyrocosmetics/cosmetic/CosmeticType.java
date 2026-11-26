package com.hyrocosmetics.cosmetic;

import org.bukkit.Material;

public enum CosmeticType {
    HAT("Topi & Helm 3D", Material.DIAMOND_HELMET, "Topi atau topeng 3D di kepala."),
    BACKPACK("Ransel & Tas Punggung 3D", Material.CHEST, "Tas atau ransel taktis di punggung."),
    WINGS("Sayap 3D", Material.ELYTRA, "Sayap malaikat, naga, atau peri 3D."),
    HAND("Aksesoris Tangan 3D", Material.STICK, "Tongkat jalan, lentera melayang, atau tongkat sihir."),
    FOOT("Sepatu 3D & Jejak Kaki", Material.LEATHER_BOOTS, "Sepatu 3D dan aura pijakan langkah."),
    BALLOON("Balon 3D", Material.LEAD, "Balon mengambang dengan tali elastis."),
    TRAIL("Particle Trails", Material.BLAZE_POWDER, "Aura partikel di sekeliling tubuh saat bergerak."),
    KILL_EFFECT("Kill Effects", Material.NETHER_STAR, "Efek dramatis saat mengeliminasi musuh.");

    private final String displayName;
    private final Material icon;
    private final String description;

    CosmeticType(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
