package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Handles the {@code /failedruns} command. Displays the count of failed runs
 * and
 * failures caused for every currently connected player.
 */
public final class FailedRunsCommand implements CommandExecutor {

    private final StatsManager statsManager;

    public FailedRunsCommand(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        if (onlinePlayers.isEmpty()) {
            sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("No players are currently connected.", NamedTextColor.GRAY)));
            return true;
        }

        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);
        Component header = Component.text("☠ ", NamedTextColor.DARK_RED)
                .append(Component.text("Hardcore Failure Stats (Online Players):", NamedTextColor.RED,
                        TextDecoration.BOLD));

        sender.sendMessage(Component.empty());
        sender.sendMessage(separator);
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        for (Player p : onlinePlayers) {
            int failedRuns = statsManager.getFailedRuns(p.getUniqueId());
            int failuresCaused = statsManager.getFailuresCaused(p.getUniqueId());

            Component playerStat = Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(p.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(failedRuns + " failed run" + (failedRuns != 1 ? "s" : ""),
                            NamedTextColor.YELLOW))
                    .append(Component.text(" (caused ", NamedTextColor.GRAY))
                    .append(Component.text(failuresCaused, NamedTextColor.RED))
                    .append(Component.text(")", NamedTextColor.GRAY));

            sender.sendMessage(playerStat);
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(separator);
        sender.sendMessage(Component.empty());

        return true;
    }
}
