package com.hyrocosmetics.storage;

import com.hyrocosmetics.HyroCosmetics;
import com.hyrocosmetics.cosmetic.CosmeticType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CosmeticsDatabase {

    private final HyroCosmetics plugin;
    private HikariDataSource dataSource;

    public CosmeticsDatabase(HyroCosmetics plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "cosmetics.db"));
        if (!plugin.getDataFolder().exists()) {
            if (!plugin.getDataFolder().mkdirs() && !plugin.getDataFolder().isDirectory()) {
                plugin.getLogger().severe("[Database] Gagal membuat folder data plugin: " + plugin.getDataFolder());
                return false;
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.addDataSourceProperty("busy_timeout", "5000");
        config.setPoolName("HyroCosmeticsPool");

        try {
            this.dataSource = new HikariDataSource(config);
            if (!createTables()) {
                shutdown();
                return false;
            }
            plugin.getLogger().info("[Database] Cosmetics SQLite database initialized.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Failed to initialize cosmetics database!", e);
            shutdown();
            return false;
        }
    }

    private boolean createTables() {
        String query = "CREATE TABLE IF NOT EXISTS hyro_player_cosmetics (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "cosmetic_type VARCHAR(20) NOT NULL, " +
                "cosmetic_id VARCHAR(64) NOT NULL, " +
                "PRIMARY KEY (player_uuid, cosmetic_type)" +
                ");";

        String queryUnlocked = "CREATE TABLE IF NOT EXISTS hyro_unlocked_cosmetics (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "cosmetic_id VARCHAR(64) NOT NULL, " +
                "PRIMARY KEY (player_uuid, cosmetic_id)" +
                ");";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             PreparedStatement stmt2 = conn.prepareStatement(queryUnlocked)) {
            stmt.execute();
            stmt2.execute();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Failed to create player cosmetics tables!", e);
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is null! Database not connected.");
        }
        return dataSource.getConnection();
    }

    public void saveEquipped(UUID uuid, CosmeticType type, String cosmeticId) {
        String sql = "INSERT OR REPLACE INTO hyro_player_cosmetics (player_uuid, cosmetic_type, cosmetic_id) VALUES (?, ?, ?);";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, type.name());
                stmt.setString(3, cosmeticId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to save equipped cosmetic!", e);
            }
        });
    }

    public void removeEquipped(UUID uuid, CosmeticType type) {
        String sql = "DELETE FROM hyro_player_cosmetics WHERE player_uuid = ? AND cosmetic_type = ?;";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, type.name());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to remove equipped cosmetic!", e);
            }
        });
    }

    public void clearEquipped(UUID uuid) {
        String sql = "DELETE FROM hyro_player_cosmetics WHERE player_uuid = ?;";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to clear cosmetics!", e);
            }
        });
    }

    public Map<CosmeticType, String> loadEquipped(UUID uuid) {
        Map<CosmeticType, String> map = new HashMap<>();
        String sql = "SELECT cosmetic_type, cosmetic_id FROM hyro_player_cosmetics WHERE player_uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        CosmeticType type = CosmeticType.valueOf(rs.getString("cosmetic_type"));
                        String id = rs.getString("cosmetic_id");
                        map.put(type, id);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to load equipped cosmetics!", e);
        }
        return map;
    }

    public void saveUnlocked(UUID uuid, String cosmeticId) {
        String sql = "INSERT OR IGNORE INTO hyro_unlocked_cosmetics (player_uuid, cosmetic_id) VALUES (?, ?);";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, cosmeticId.toLowerCase());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to save unlocked cosmetic!", e);
            }
        });
    }

    public void removeUnlocked(UUID uuid, String cosmeticId) {
        String sql = "DELETE FROM hyro_unlocked_cosmetics WHERE player_uuid = ? AND cosmetic_id = ?;";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, cosmeticId.toLowerCase());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to remove unlocked cosmetic!", e);
            }
        });
    }

    public java.util.Set<String> loadUnlocked(UUID uuid) {
        java.util.Set<String> set = new java.util.HashSet<>();
        String sql = "SELECT cosmetic_id FROM hyro_unlocked_cosmetics WHERE player_uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("cosmetic_id").toLowerCase());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to load unlocked cosmetics!", e);
        }
        return set;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
