package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * Central listener that keeps every online player's health, hunger, and
 * saturation perfectly synchronised.
 *
 */
public final class SyncListener implements Listener {

    private final HardcoreTogetherPlugin plugin;
    private final VoteManager voteManager;
    private final LeaderboardDisplay leaderboardDisplay;
    private final StatsManager statsManager;

    private volatile boolean synchronizing = false;

    private volatile boolean gameOver = false;

    private volatile boolean awaitingRestart = false;

    public SyncListener(HardcoreTogetherPlugin plugin, VoteManager voteManager,
            LeaderboardDisplay leaderboardDisplay, StatsManager statsManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
        this.leaderboardDisplay = leaderboardDisplay;
        this.statsManager = statsManager;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (synchronizing || gameOver)
            return;
        if (!(event.getEntity() instanceof Player damaged))
            return;

        if (plugin.getRunStatsManager() != null) {
            plugin.getRunStatsManager().addDamageTaken(damaged.getUniqueId(), event.getFinalDamage());
        }

        double newHealth = damaged.getHealth() - event.getFinalDamage();

        broadcastDamageLog(damaged, event, event.getFinalDamage(), Math.max(0.0, newHealth));

        if (newHealth <= 0) {
            return;
        }

        newHealth = Math.max(0.0, Math.min(newHealth, damaged.getMaxHealth()));

        syncHealth(damaged, newHealth);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (synchronizing || gameOver)
            return;
        if (!(event.getEntity() instanceof Player healed))
            return;

        double newHealth = healed.getHealth() + event.getAmount();
        newHealth = Math.max(0.0, Math.min(newHealth, healed.getMaxHealth()));

        syncHealth(healed, newHealth);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (synchronizing || gameOver)
            return;
        if (!(event.getEntity() instanceof Player eater))
            return;

        int newFoodLevel = event.getFoodLevel();
        float saturation = eater.getSaturation();

        int diff = newFoodLevel - eater.getFoodLevel();
        if (diff > 0 && plugin.getRunStatsManager() != null) {
            plugin.getRunStatsManager().addHungerRestored(eater.getUniqueId(), diff);
        }

        syncHunger(eater, newFoodLevel, saturation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (synchronizing || gameOver)
            return;
        if (event.getDamager() instanceof Player attacker) {
            if (plugin.getRunStatsManager() != null) {
                plugin.getRunStatsManager().addDamageDealt(attacker.getUniqueId(), event.getFinalDamage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameOver)
            return;

        Player died = event.getEntity();

        Component deathMessage = event.deathMessage();
        Component subtitle;
        if (deathMessage != null) {
            subtitle = deathMessage.color(NamedTextColor.GRAY);
        } else {
            subtitle = Component.text(died.getName() + " died.", NamedTextColor.GRAY);
        }

        triggerGameOver(died, subtitle);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            leaderboardDisplay.show(joining);
            HardcoreTogetherCommand.sendInfo(joining, plugin);
        }, 2L);

        if (gameOver) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                joining.setGameMode(GameMode.SPECTATOR);
                joining.showTitle(Title.title(
                        Component.text("GAME OVER", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("The hardcore run has already ended.", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(5), Duration.ofSeconds(2))));
                if (awaitingRestart) {
                    sendPlayAgainButton(joining);
                }
            }, 1L);
            return;
        }

        Player reference = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(joining.getUniqueId())) {
                reference = online;
                break;
            }
        }

        if (reference == null) {
            return;
        }

        final Player ref = reference;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronizing = true;
            try {
                joining.setHealth(ref.getHealth());
                joining.setFoodLevel(ref.getFoodLevel());
                joining.setSaturation(ref.getSaturation());
            } finally {
                synchronizing = false;
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameOver)
            return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
        }, 1L);
    }

    private void broadcastDamageLog(Player damaged, EntityDamageEvent event,
            double damageAmount, double remainingHealth) {

        double damageHearts = damageAmount / 2.0;
        double remainingHearts = remainingHealth / 2.0;

        String causeText = formatDamageCause(event);

        NamedTextColor damageColor;
        if (damageHearts >= 5.0) {
            damageColor = NamedTextColor.DARK_RED;
        } else if (damageHearts >= 2.0) {
            damageColor = NamedTextColor.RED;
        } else {
            damageColor = NamedTextColor.GOLD;
        }

        NamedTextColor healthColor;
        if (remainingHearts <= 0) {
            healthColor = NamedTextColor.DARK_RED;
        } else if (remainingHearts <= 3.0) {
            healthColor = NamedTextColor.RED;
        } else if (remainingHearts <= 6.0) {
            healthColor = NamedTextColor.YELLOW;
        } else {
            healthColor = NamedTextColor.GREEN;
        }

        Component message = Component.text("⚔ ", NamedTextColor.DARK_RED)
                .append(Component.text(damaged.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" took ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f♥", damageHearts), damageColor, TextDecoration.BOLD))
                .append(Component.text(" damage ", NamedTextColor.GRAY))
                .append(Component.text("(" + causeText + ") ", NamedTextColor.WHITE))
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.1f♥", remainingHearts), healthColor))
                .append(Component.text(" remaining]", NamedTextColor.DARK_GRAY));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private String formatDamageCause(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity attacker = byEntity.getDamager();
            if (attacker instanceof Player attackerPlayer) {
                return attackerPlayer.getName();
            }
            Component customName = attacker.customName();
            if (customName != null) {
                return formatEntityTypeName(attacker.getType().name());
            }
            return formatEntityTypeName(attacker.getType().name());
        }
        return switch (event.getCause()) {
            case BLOCK_EXPLOSION -> "Explosion";
            case CONTACT -> "Cactus";
            case CRAMMING -> "Cramming";
            case CUSTOM -> "Unknown";
            case DRAGON_BREATH -> "Dragon Breath";
            case DROWNING -> "Drowning";
            case ENTITY_ATTACK -> "Mob";
            case ENTITY_EXPLOSION -> "Creeper";
            case ENTITY_SWEEP_ATTACK -> "Sweep Attack";
            case FALL -> "Fall Damage";
            case FALLING_BLOCK -> "Falling Block";
            case FIRE -> "Fire";
            case FIRE_TICK -> "Burning";
            case FLY_INTO_WALL -> "Kinetic Energy";
            case FREEZE -> "Freezing";
            case HOT_FLOOR -> "Magma Block";
            case KILL -> "/kill";
            case LAVA -> "Lava";
            case LIGHTNING -> "Lightning";
            case MAGIC -> "Magic";
            case POISON -> "Poison";
            case PROJECTILE -> "Projectile";
            case SONIC_BOOM -> "Sonic Boom";
            case STARVATION -> "Starvation";
            case SUFFOCATION -> "Suffocation";
            case SUICIDE -> "Suicide";
            case THORNS -> "Thorns";
            case VOID -> "Void";
            case WITHER -> "Wither";
            case WORLD_BORDER -> "World Border";
            default -> formatEntityTypeName(event.getCause().name());
        };
    }

    private String formatEntityTypeName(String enumName) {
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1))
                        .append(' ');
            }
        }
        return sb.toString().trim();
    }

    private void syncHealth(Player source, double newHealth) {
        synchronizing = true;
        try {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            for (Player p : online) {
                if (p.getUniqueId().equals(source.getUniqueId()))
                    continue;
                if (p.isDead())
                    continue;

                double clamped = Math.max(0.0, Math.min(newHealth, p.getMaxHealth()));
                p.setHealth(clamped);
            }
        } finally {
            synchronizing = false;
        }
    }

    private void syncHunger(Player source, int foodLevel, float saturation) {
        synchronizing = true;
        try {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            for (Player p : online) {
                if (p.getUniqueId().equals(source.getUniqueId()))
                    continue;
                if (p.isDead())
                    continue;

                p.setFoodLevel(foodLevel);
                p.setSaturation(saturation);
            }
        } finally {
            synchronizing = false;
        }
    }

    private void triggerGameOver(Player whoDied, Component subtitle) {
        gameOver = true;
        synchronizing = true;
        statsManager.recordFailure(Bukkit.getOnlinePlayers(), whoDied);

        try {
            Title gameOverTitle = Title.title(
                    Component.text("GAME OVER", NamedTextColor.RED, TextDecoration.BOLD),
                    subtitle,
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(2)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(gameOverTitle);

                if (!p.getUniqueId().equals(whoDied.getUniqueId()) && !p.isDead()) {
                    p.setHealth(0);
                }
            }
        } finally {
            synchronizing = false;
        }

        if (plugin.getStopwatchManager() != null) {
            Component finalTimeMsg = Component.text("⏱ Final Run Time: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getStopwatchManager().getFormattedTime(), NamedTextColor.GOLD,
                            TextDecoration.BOLD));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(Component.empty());
                p.sendMessage(finalTimeMsg);
                p.sendMessage(Component.empty());
            }
        }

        if (plugin.getRunStatsManager() != null) {
            Component statHeader = Component.text("☠ Run Statistics:", NamedTextColor.GOLD, TextDecoration.BOLD);
            List<Component> statLines = new ArrayList<>();
            statLines.add(statHeader);

            for (Player p : Bukkit.getOnlinePlayers()) {
                double dealt = plugin.getRunStatsManager().getDamageDealt(p.getUniqueId()) / 2.0;
                double taken = plugin.getRunStatsManager().getDamageTaken(p.getUniqueId()) / 2.0;
                int hunger = plugin.getRunStatsManager().getHungerRestored(p.getUniqueId());

                Component playerLine = Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(p.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(":", NamedTextColor.GRAY));

                Component detailsLine = Component.text("    ⚔ Dealt: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format(java.util.Locale.US, "%.1f♥", dealt),
                                NamedTextColor.GREEN))
                        .append(Component.text(" | 🛡 Taken: ", NamedTextColor.GRAY))
                        .append(Component.text(String.format(java.util.Locale.US, "%.1f♥", taken), NamedTextColor.RED))
                        .append(Component.text(" | 🍗 Hunger Restored: ", NamedTextColor.GRAY))
                        .append(Component.text(hunger, NamedTextColor.YELLOW));

                statLines.add(playerLine);
                statLines.add(detailsLine);
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(Component.empty());
                for (Component line : statLines) {
                    p.sendMessage(line);
                }
                p.sendMessage(Component.empty());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setGameMode(GameMode.SPECTATOR);
            }
        }, 40L);

        startRestartCountdown();
    }

    private void startRestartCountdown() {
        new BukkitRunnable() {
            int secondsLeft = 10;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    cancel();
                    startVotingPhase();
                    return;
                }

                NamedTextColor countColor;
                if (secondsLeft <= 3) {
                    countColor = NamedTextColor.RED;
                } else if (secondsLeft <= 6) {
                    countColor = NamedTextColor.YELLOW;
                } else {
                    countColor = NamedTextColor.GREEN;
                }

                Component actionbar = Component.text("⏱ ", NamedTextColor.GRAY)
                        .append(Component.text(secondsLeft, countColor, TextDecoration.BOLD))
                        .append(Component.text("s until voting begins", NamedTextColor.GRAY));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(actionbar);
                }

                secondsLeft--;
            }
        }.runTaskTimer(plugin, 60L, 20L);
    }

    private void startVotingPhase() {
        voteManager.startVoting(() -> {
            awaitingRestart = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                sendPlayAgainButton(p);
            }
        });
    }

    private void sendPlayAgainButton(Player player) {
        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);

        Component playAgain = Component.text("")
                .append(Component.text("         "))
                .append(Component.text("☠ ", NamedTextColor.RED))
                .append(Component.text("The hardcore run has ended.  ", NamedTextColor.GRAY))
                .append(Component.text("[", NamedTextColor.DARK_GREEN))
                .append(Component.text(" ▶ PLAY AGAIN ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/hcrestart"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to reset the world and start a new run!", NamedTextColor.GREEN))))
                .append(Component.text("]", NamedTextColor.DARK_GREEN)
                        .clickEvent(ClickEvent.runCommand("/hcrestart"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to reset the world and start a new run!",
                                        NamedTextColor.GREEN))));

        player.sendMessage(Component.empty());
        player.sendMessage(separator);
        player.sendMessage(playAgain);
        player.sendMessage(separator);
        player.sendMessage(Component.empty());
    }

    public void performWorldReset() {
        if (plugin.getStopwatchManager() != null) {
            plugin.getStopwatchManager().reset();
        }

        if (plugin.getRunStatsManager() != null) {
            plugin.getRunStatsManager().reset();
        }

        Component restarting = Component.text("")
                .append(Component.text("⟳ ", NamedTextColor.AQUA))
                .append(Component.text("Resetting world and restarting server...", NamedTextColor.AQUA,
                        TextDecoration.BOLD));

        Title restartTitle = Title.title(
                Component.text("RESTARTING", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("New world generating...", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(3), Duration.ofMillis(500)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(restarting);
            p.showTitle(restartTitle);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.kick(Component.text("\n")
                        .append(Component.text("⟳ Server is restarting with a fresh world!\n", NamedTextColor.GREEN,
                                TextDecoration.BOLD))
                        .append(Component.text("Rejoin in a few seconds to play again.", NamedTextColor.GRAY)));
            }

            File serverDir = Bukkit.getWorldContainer();
            String[] worldDirs = { "world", "world_nether", "world_the_end" };

            for (String worldName : worldDirs) {
                if (Bukkit.getWorld(worldName) != null) {
                    Bukkit.unloadWorld(worldName, false);
                }
                File worldDir = new File(serverDir, worldName);
                if (worldDir.exists()) {
                    deleteDirectory(worldDir);
                    plugin.getLogger().info("Deleted world folder: " + worldDir.getAbsolutePath());
                }
            }

            Bukkit.getServer().shutdown();
        }, 60L);
    }

    private void deleteDirectory(File dir) {
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File file : contents) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
