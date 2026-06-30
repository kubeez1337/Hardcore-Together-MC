package com.hctogether;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages persistent tracking of failed runs and failures caused per player.
 * Saved in {@code plugins/HardcoreTogether/stats.json} to persist across server
 * resets.
 */
public final class StatsManager {

    private static final String FILE_NAME = "stats.json";

    private final File dataFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerStats> statsMap = new HashMap<>();

    public static class PlayerStats {
        public String name;
        public int failedRuns;
        public int failuresCaused;

        public PlayerStats() {
        }

        public PlayerStats(String name) {
            this.name = name;
            this.failedRuns = 0;
            this.failuresCaused = 0;
        }
    }

    public StatsManager(File pluginDataFolder, Logger logger) {
        this.dataFile = new File(pluginDataFolder, FILE_NAME);
        this.logger = logger;
        load();
    }

    public void recordFailure(Collection<? extends Player> onlinePlayers, Player causer) {
        for (Player p : onlinePlayers) {
            UUID uuid = p.getUniqueId();
            PlayerStats stats = statsMap.computeIfAbsent(uuid, k -> new PlayerStats(p.getName()));
            stats.name = p.getName();
            stats.failedRuns++;
        }

        if (causer != null) {
            UUID causerUuid = causer.getUniqueId();
            PlayerStats stats = statsMap.computeIfAbsent(causerUuid, k -> new PlayerStats(causer.getName()));
            stats.failuresCaused++;
        }

        save();
    }

    public int getFailedRuns(UUID uuid) {
        PlayerStats stats = statsMap.get(uuid);
        return stats != null ? stats.failedRuns : 0;
    }

    public int getFailuresCaused(UUID uuid) {
        PlayerStats stats = statsMap.get(uuid);
        return stats != null ? stats.failuresCaused : 0;
    }

    public void load() {
        statsMap.clear();
        if (!dataFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type mapType = new TypeToken<Map<UUID, PlayerStats>>() {
            }.getType();
            Map<UUID, PlayerStats> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                statsMap.putAll(loaded);
            }
            logger.info("Loaded statistics for " + statsMap.size() + " players.");
        } catch (IOException e) {
            logger.warning("Failed to load statistics data: " + e.getMessage());
        }
    }

    public void save() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(statsMap, writer);
        } catch (IOException e) {
            logger.warning("Failed to save statistics data: " + e.getMessage());
        }
    }
}
