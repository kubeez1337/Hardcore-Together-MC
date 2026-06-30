package com.hctogether;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hardcore Together – Shared Hardcore Speedrun Plugin for Paper 1.21.1.
 */
public final class HardcoreTogetherPlugin extends JavaPlugin {

    private SyncListener syncListener;
    private StopwatchManager stopwatchManager;
    private RunStatsManager runStatsManager;

    public SyncListener getSyncListener() {
        return syncListener;
    }

    public StopwatchManager getStopwatchManager() {
        return stopwatchManager;
    }

    public RunStatsManager getRunStatsManager() {
        return runStatsManager;
    }

    @Override
    public void onEnable() {
        this.runStatsManager = new RunStatsManager(getDataFolder(), getLogger());

        BlackPointManager pointManager = new BlackPointManager(getDataFolder(), getLogger());

        LeaderboardDisplay leaderboardDisplay = new LeaderboardDisplay(this, pointManager);
        VoteManager voteManager = new VoteManager(this, pointManager, leaderboardDisplay);

        StatsManager statsManager = new StatsManager(getDataFolder(), getLogger());

        this.syncListener = new SyncListener(this, voteManager, leaderboardDisplay, statsManager);
        getServer().getPluginManager().registerEvents(syncListener, this);

        this.stopwatchManager = new StopwatchManager(this);
        this.stopwatchManager.start();

        RestartCommand restartCommand = new RestartCommand(syncListener);
        getCommand("hcrestart").setExecutor(restartCommand);
        getCommand("hcrestart").setTabCompleter(restartCommand);
        getCommand("hcvote").setExecutor(new VoteCommand(voteManager));
        getCommand("failedruns").setExecutor(new FailedRunsCommand(statsManager));

        BlackPointsCommand bpCommand = new BlackPointsCommand(pointManager, leaderboardDisplay);
        getCommand("blackpoints").setExecutor(bpCommand);
        getCommand("blackpoints").setTabCompleter(bpCommand);

        getCommand("hardcoretogether").setExecutor(new HardcoreTogetherCommand(this));

        enforceHardcore();

        leaderboardDisplay.refreshScoreboard();
        leaderboardDisplay.showAll();

        getLogger().info("Hardcore Together enabled – shared hardcore mode is active!");
    }

    @Override
    public void onDisable() {
        if (stopwatchManager != null) {
            stopwatchManager.stop();
        }
        if (runStatsManager != null) {
            runStatsManager.save();
        }
        getLogger().info("Hardcore Together disabled.");
    }

    private void enforceHardcore() {
        for (World world : getServer().getWorlds()) {
            if (!world.isHardcore()) {
                world.setHardcore(true);
                getLogger().info("Set world '" + world.getName() + "' to hardcore mode.");
            }
            if (world.getDifficulty() != Difficulty.HARD) {
                world.setDifficulty(Difficulty.HARD);
                getLogger().info("Set world '" + world.getName() + "' difficulty to HARD.");
            }
        }
    }
}
