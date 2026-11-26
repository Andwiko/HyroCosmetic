package com.hyrocosmetics.cosmetic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCosmeticData {

    private final UUID playerUuid;
    private final Map<CosmeticType, String> equipped = new ConcurrentHashMap<>();
    private final Set<String> unlocked = ConcurrentHashMap.newKeySet();

    public PlayerCosmeticData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean hasUnlocked(String cosmeticId) {
        if (cosmeticId == null) return false;
        return unlocked.contains(cosmeticId.toLowerCase());
    }

    public void addUnlocked(String cosmeticId) {
        if (cosmeticId != null) {
            unlocked.add(cosmeticId.toLowerCase());
        }
    }

    public void removeUnlocked(String cosmeticId) {
        if (cosmeticId != null) {
            unlocked.remove(cosmeticId.toLowerCase());
        }
    }

    public Set<String> getUnlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public String getEquipped(CosmeticType type) {
        return equipped.get(type);
    }

    public void setEquipped(CosmeticType type, String cosmeticId) {
        if (cosmeticId == null) {
            equipped.remove(type);
        } else {
            equipped.put(type, cosmeticId);
        }
    }

    public void unequip(CosmeticType type) {
        equipped.remove(type);
    }

    public void clearAll() {
        equipped.clear();
    }

    public Map<CosmeticType, String> getAllEquipped() {
        return Collections.unmodifiableMap(equipped);
    }
}
