package com.hyrocosmetics.utils;

import org.bukkit.Bukkit;

public final class VersionUtil {

    private static final int MAJOR;
    private static final int MINOR;
    private static final int PATCH;

    static {
        int major = 1;
        int minor = 20;
        int patch = 4;
        try {
            String versionStr = Bukkit.getBukkitVersion(); // e.g. "1.21.1-R0.1-SNAPSHOT" or "1.21-R0.1-SNAPSHOT"
            int dashIndex = versionStr.indexOf('-');
            if (dashIndex > 0) {
                versionStr = versionStr.substring(0, dashIndex);
            }
            String[] parts = versionStr.split("\\.");
            if (parts.length >= 1) major = Integer.parseInt(parts[0]);
            if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
            if (parts.length >= 3) patch = Integer.parseInt(parts[2]);
            else patch = 0;
        } catch (Throwable ignored) {}

        MAJOR = major;
        MINOR = minor;
        PATCH = patch;
    }

    private VersionUtil() {}

    public static int getMajor() {
        return MAJOR;
    }

    public static int getMinor() {
        return MINOR;
    }

    public static int getPatch() {
        return PATCH;
    }

    public static boolean isAtLeast(int targetMinor) {
        return isAtLeast(targetMinor, 0);
    }

    public static boolean isAtLeast(int targetMinor, int targetPatch) {
        if (MAJOR > 1) return true;
        if (MAJOR < 1) return false;
        if (MINOR > targetMinor) return true;
        if (MINOR == targetMinor) return PATCH >= targetPatch;
        return false;
    }
}
