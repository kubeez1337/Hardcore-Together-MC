package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the {@code /hardcoretogether} command. Displays info, status, and
 * help.
 */
public final class HardcoreTogetherCommand implements CommandExecutor {

        private final HardcoreTogetherPlugin plugin;

        public HardcoreTogetherCommand(HardcoreTogetherPlugin plugin) {
                this.plugin = plugin;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender,
                        @NotNull Command command,
                        @NotNull String label,
                        @NotNull String[] args) {
                sendInfo(sender, plugin);
                return true;
        }

        public static void sendInfo(CommandSender sender, HardcoreTogetherPlugin plugin) {
                Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                                TextDecoration.STRIKETHROUGH);
                Component header = Component.text("☠ ", NamedTextColor.DARK_RED)
                                .append(Component.text("Hardcore Together ", NamedTextColor.RED, TextDecoration.BOLD))
                                .append(Component.text("v" + plugin.getDescription().getVersion(),
                                                NamedTextColor.GRAY));

                Component desc = Component.text(
                                "Shared Hardcore Speedrun – all players share health and hunger. One mistake can end it all.",
                                NamedTextColor.GRAY);

                String timerStr = "00:00";
                String stateStr = "Paused/Inactive";
                if (plugin.getStopwatchManager() != null) {
                        timerStr = plugin.getStopwatchManager().getFormattedTime();
                        boolean hasPlayers = !org.bukkit.Bukkit.getOnlinePlayers().isEmpty();
                        boolean isGameOver = plugin.getSyncListener() != null && plugin.getSyncListener().isGameOver();
                        if (hasPlayers && !isGameOver) {
                                stateStr = "Active";
                        } else if (isGameOver) {
                                stateStr = "Game Over";
                        }
                }

                Component statusHeader = Component.text("⏱ Status:", NamedTextColor.GOLD, TextDecoration.BOLD);
                Component statusLine = Component.text("  • Speedrun Timer: ", NamedTextColor.GRAY)
                                .append(Component.text(timerStr, NamedTextColor.WHITE, TextDecoration.BOLD))
                                .append(Component.text(" (" + stateStr + ")", NamedTextColor.GRAY));

                Component helpHeader = Component.text("⌨ Commands:", NamedTextColor.GOLD, TextDecoration.BOLD);
                Component helpRuns = Component.text("  • ", NamedTextColor.DARK_GRAY)
                                .append(Component.text("/failedruns", NamedTextColor.AQUA))
                                .append(Component.text(" — View hardcore failure statistics.", NamedTextColor.GRAY));
                Component helpBP = Component.text("  • ", NamedTextColor.DARK_GRAY)
                                .append(Component.text("/blackpoints", NamedTextColor.AQUA))
                                .append(Component.text(" — View the all-time blame leaderboard.", NamedTextColor.GRAY));
                Component helpRestart = Component.text("  • ", NamedTextColor.DARK_GRAY)
                                .append(Component.text("/hcrestart", NamedTextColor.AQUA))
                                .append(Component.text(" — Restart and generate a new world (after Game Over).",
                                                NamedTextColor.GRAY));

                sender.sendMessage(separator);
                sender.sendMessage(header);
                sender.sendMessage(desc);
                sender.sendMessage(Component.empty());

                sender.sendMessage(statusHeader);
                sender.sendMessage(statusLine);
                sender.sendMessage(Component.empty());

                sender.sendMessage(helpHeader);
                sender.sendMessage(helpRuns);
                sender.sendMessage(helpBP);
                sender.sendMessage(helpRestart);

                sender.sendMessage(separator);
                sender.sendMessage(Component.text("https://github.com/kubeez1337/Hardcore-Together-MC",
                                NamedTextColor.GRAY,
                                TextDecoration.BOLD));
                sender.sendMessage(separator);

        }
}
