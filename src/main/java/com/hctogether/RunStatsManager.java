package com.hctogether;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tracks run-specific statistics (damage dealt, damage taken, and hunger
 * restored)
 * in-memory and persists them to disk to survive server restarts/disconnects.
 */
public final class RunStatsManager {

    private static final String FILE_NAME = "run_stats.json";

    private final File dataFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerRunStats> runStatsMap = new HashMap<>();

    public static class PlayerRunStats {
        public double damageTaken = 0.0;
        public double damageDealt = 0.0;
        public int hungerRestored = 0;

        public PlayerRunStats() {
        }
    }

    public RunStatsManager(File pluginDataFolder, Logger logger) {
        this.dataFile = new File(pluginDataFolder, FILE_NAME);
        this.logger = logger;
        load();
    }

    public void addDamageTaken(UUID uuid, double amount) {
        PlayerRunStats stats = runStatsMap.computeIfAbsent(uuid, k -> new PlayerRunStats());
        stats.damageTaken += amount;
    }

    public void addDamageDealt(UUID uuid, double amount) {
        PlayerRunStats stats = runStatsMap.computeIfAbsent(uuid, k -> new PlayerRunStats());
        stats.damageDealt += amount;
    }

    public void addHungerRestored(UUID uuid, int amount) {
        PlayerRunStats stats = runStatsMap.computeIfAbsent(uuid, k -> new PlayerRunStats());
        stats.hungerRestored += amount;
    }

    public double getDamageTaken(UUID uuid) {
        PlayerRunStats stats = runStatsMap.get(uuid);
        return stats != null ? stats.damageTaken : 0.0;
    }

    public double getDamageDealt(UUID uuid) {
        PlayerRunStats stats = runStatsMap.get(uuid);
        return stats != null ? stats.damageDealt : 0.0;
    }

    public int getHungerRestored(UUID uuid) {
        PlayerRunStats stats = runStatsMap.get(uuid);
        return stats != null ? stats.hungerRestored : 0;
    }

    public void reset() {
        runStatsMap.clear();
        save();
    }

    public void load() {
        runStatsMap.clear();
        if (!dataFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type mapType = new TypeToken<Map<UUID, PlayerRunStats>>() {
            }.getType();
            Map<UUID, PlayerRunStats> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                runStatsMap.putAll(loaded);
            }
            logger.info("Loaded run statistics for " + runStatsMap.size() + " players.");
        } catch (IOException e) {
            logger.warning("Failed to load run statistics data: " + e.getMessage());
        }
    }

    public void save() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(runStatsMap, writer);
        } catch (IOException e) {
            logger.warning("Failed to save run statistics data: " + e.getMessage());
        }
    }
}
