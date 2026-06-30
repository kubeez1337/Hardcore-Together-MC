package com.hctogether;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages the speedrun stopwatch.
 * Pauses when no players are on the server or the game is over.
 * Resets on each new run. Persisted to disk across restarts.
 */
public final class StopwatchManager {

    private static final String FILE_NAME = "stopwatch.json";

    private final HardcoreTogetherPlugin plugin;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private long elapsedTimeMillis = 0;
    private long lastActiveTime = 0;
    private BukkitTask tickTask;

    public static class StopwatchData {
        public long elapsedTimeMillis;

        public StopwatchData() {
        }

        public StopwatchData(long elapsedTimeMillis) {
            this.elapsedTimeMillis = elapsedTimeMillis;
        }
    }

    public StopwatchManager(HardcoreTogetherPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        save();
    }

    public void reset() {
        elapsedTimeMillis = 0;
        lastActiveTime = 0;
        save();
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    private void tick() {
        boolean hasPlayers = !Bukkit.getOnlinePlayers().isEmpty();
        boolean isGameOver = plugin.getSyncListener() != null && plugin.getSyncListener().isGameOver();

        if (hasPlayers && !isGameOver) {
            long now = System.currentTimeMillis();
            if (lastActiveTime == 0) {
                lastActiveTime = now;
            } else {
                long delta = now - lastActiveTime;
                elapsedTimeMillis += delta;
                lastActiveTime = now;
            }

            Component actionbarText = formatStopwatch(elapsedTimeMillis);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(actionbarText);
            }
        } else {
            lastActiveTime = 0;
        }
    }

    public void load() {
        if (!dataFile.exists()) {
            elapsedTimeMillis = 0;
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            StopwatchData data = gson.fromJson(reader, StopwatchData.class);
            if (data != null) {
                elapsedTimeMillis = data.elapsedTimeMillis;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load stopwatch data: " + e.getMessage());
        }
    }

    public void save() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(new StopwatchData(elapsedTimeMillis), writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stopwatch data: " + e.getMessage());
        }
    }

    public String getFormattedTime() {
        long seconds = elapsedTimeMillis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    private Component formatStopwatch(long elapsed) {
        String timeStr = getFormattedTime();
        return Component.text("⏱ ", NamedTextColor.GOLD)
                .append(Component.text(timeStr, NamedTextColor.WHITE, TextDecoration.BOLD));
    }
}
