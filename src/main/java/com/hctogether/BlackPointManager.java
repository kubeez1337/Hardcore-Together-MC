package com.hctogether;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages persistent storage of Black Points – blame-votes awarded after each
 * failed hardcore run.
 *
 */
public final class BlackPointManager {

    private static final String FILE_NAME = "blackpoints.json";
    private static final long TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L;

    private final File dataFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<BlackPointEntry> entries = new ArrayList<>();

    public static class BlackPointEntry {
        public String uuid;
        public String name;
        public long timestamp;

        public BlackPointEntry() {
        }

        public BlackPointEntry(String uuid, String name, long timestamp) {
            this.uuid = uuid;
            this.name = name;
            this.timestamp = timestamp;
        }
    }

    public BlackPointManager(File pluginDataFolder, Logger logger) {
        this.dataFile = new File(pluginDataFolder, FILE_NAME);
        this.logger = logger;
        load();
    }

    public void addPoint(UUID playerUuid, String playerName) {
        addPoints(playerUuid, playerName, 1);
    }

    public void addPoints(UUID playerUuid, String playerName, int amount) {
        for (int i = 0; i < amount; i++) {
            entries.add(new BlackPointEntry(
                    playerUuid.toString(),
                    playerName,
                    System.currentTimeMillis()));
        }
        save();
    }

    public void removePoints(UUID playerUuid, int amount) {
        String uuidStr = playerUuid.toString();
        int countRemoved = 0;
        Iterator<BlackPointEntry> iterator = entries.iterator();
        while (iterator.hasNext() && countRemoved < amount) {
            BlackPointEntry entry = iterator.next();
            if (entry.uuid.equals(uuidStr)) {
                iterator.remove();
                countRemoved++;
            }
        }
        if (countRemoved > 0) {
            save();
        }
    }

    public void setPoints(UUID playerUuid, String playerName, int targetAmount) {
        String uuidStr = playerUuid.toString();
        long currentCount = entries.stream()
                .filter(e -> e.uuid.equals(uuidStr))
                .count();

        if (currentCount < targetAmount) {
            addPoints(playerUuid, playerName, targetAmount - (int) currentCount);
        } else if (currentCount > targetAmount) {
            int toRemove = (int) currentCount - targetAmount;
            removePoints(playerUuid, toRemove);
        }
    }

    public UUID getKnownUuid(String name) {
        for (BlackPointEntry entry : entries) {
            if (entry.name.equalsIgnoreCase(name)) {
                try {
                    return UUID.fromString(entry.uuid);
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            }
        }
        return null;
    }

    public LinkedHashMap<String, Integer> getLeaderboard() {
        Map<String, Integer> counts = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.name,
                        Collectors.summingInt(e -> 1)));

        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        return sorted;
    }

    public void load() {
        entries.clear();
        if (!dataFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type listType = new TypeToken<List<BlackPointEntry>>() {
            }.getType();
            List<BlackPointEntry> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                entries.addAll(loaded);
            }
            logger.info("Loaded " + entries.size() + " black point entries.");
        } catch (IOException e) {
            logger.warning("Failed to load black points data: " + e.getMessage());
        }
    }

    public void save() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(entries, writer);
        } catch (IOException e) {
            logger.warning("Failed to save black points data: " + e.getMessage());
        }
    }

    public void pruneOldEntries() {
        // disabled
    }
}
