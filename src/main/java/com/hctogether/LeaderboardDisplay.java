package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LeaderboardDisplay {

    private static final String OBJECTIVE_NAME = "hct_blackpts";

    private final HardcoreTogetherPlugin plugin;
    private final BlackPointManager pointManager;

    private Scoreboard leaderboard;

    public LeaderboardDisplay(HardcoreTogetherPlugin plugin, BlackPointManager pointManager) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        createScoreboard();
        startRefreshTask();
    }

    private void createScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        leaderboard = manager.getNewScoreboard();

        Objective objective = leaderboard.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                Component.text("☠ Black Points", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void refreshScoreboard() {
        Objective objective = leaderboard.getObjective(OBJECTIVE_NAME);
        if (objective == null)
            return;

        for (String entry : leaderboard.getEntries()) {
            leaderboard.resetScores(entry);
        }

        LinkedHashMap<String, Integer> board = pointManager.getLeaderboard();

        if (board.isEmpty()) {
            objective.getScore("§7No points yet").setScore(0);
            return;
        }

        int rank = board.size();
        for (Map.Entry<String, Integer> entry : board.entrySet()) {
            String displayName = "§c" + entry.getKey();
            if (displayName.length() > 40) {
                displayName = displayName.substring(0, 40);
            }
            objective.getScore(displayName).setScore(entry.getValue());
            rank--;
        }
    }

    public void show(Player player) {
        player.setScoreboard(leaderboard);
    }

    public void showAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p);
        }
    }

    private void startRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshScoreboard();
            }
        }.runTaskTimer(plugin, 20L, 1200L);
    }
}
