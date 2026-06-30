package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the {@code /blackpoints} command. Allows listing points for everyone,
 * and allows OPs to add, remove, or set points for any player (online or
 * offline).
 */
public final class BlackPointsCommand implements CommandExecutor, TabCompleter {

    private final BlackPointManager pointManager;
    private final LeaderboardDisplay leaderboardDisplay;

    public BlackPointsCommand(BlackPointManager pointManager, LeaderboardDisplay leaderboardDisplay) {
        this.pointManager = pointManager;
        this.leaderboardDisplay = leaderboardDisplay;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            return handleList(sender);
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("add") || sub.equals("remove") || sub.equals("set")) {
            if (!sender.hasPermission("hctogether.admin") && !sender.isOp()) {
                sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                        .append(Component.text("You do not have permission to manage black points.",
                                NamedTextColor.GRAY)));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                        .append(Component.text("Usage: /" + label + " " + sub + " <player> [amount]",
                                NamedTextColor.GRAY)));
                return true;
            }

            return handleModification(sender, label, sub, args);
        }

        sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                .append(Component.text("Unknown subcommand. Use: /" + label + " <add/remove/set/list>",
                        NamedTextColor.GRAY)));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        LinkedHashMap<String, Integer> board = pointManager.getLeaderboard();
        if (board.isEmpty()) {
            sender.sendMessage(Component.text("ℹ ", NamedTextColor.GRAY)
                    .append(Component.text("No black points have been awarded yet.", NamedTextColor.GRAY)));
            return true;
        }

        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);
        Component header = Component.text("☠ ", NamedTextColor.DARK_RED)
                .append(Component.text("Black Points Leaderboard (All-Time):", NamedTextColor.RED,
                        TextDecoration.BOLD));

        sender.sendMessage(Component.empty());
        sender.sendMessage(separator);
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        for (Map.Entry<String, Integer> entry : board.entrySet()) {
            Component line = Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.getKey(), NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(entry.getValue() + " Black Point" + (entry.getValue() != 1 ? "s" : ""),
                            NamedTextColor.RED));
            sender.sendMessage(line);
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(separator);
        sender.sendMessage(Component.empty());

        return true;
    }

    private boolean handleModification(CommandSender sender, String label, String action, String[] args) {
        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        UUID targetUuid;
        String resolvedName;

        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
            resolvedName = targetPlayer.getName();
        } else {
            UUID knownUuid = pointManager.getKnownUuid(targetName);
            if (knownUuid != null) {
                targetUuid = knownUuid;
                resolvedName = targetName;
            } else {
                @SuppressWarnings("deprecation")
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                targetUuid = offlinePlayer.getUniqueId();
                resolvedName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
            }
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 0) {
                    sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                            .append(Component.text("Amount must be a non-negative integer.", NamedTextColor.GRAY)));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                        .append(Component.text("Invalid amount: '" + args[2] + "'. Must be a number.",
                                NamedTextColor.GRAY)));
                return true;
            }
        } else if (action.equals("set")) {
            sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Usage: /" + label + " set <player> <amount>", NamedTextColor.GRAY)));
            return true;
        }

        switch (action) {
            case "add" -> {
                pointManager.addPoints(targetUuid, resolvedName, amount);
                sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text("Added " + amount + " black point(s) to ", NamedTextColor.GRAY))
                        .append(Component.text(resolvedName, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(".", NamedTextColor.GRAY)));
            }
            case "remove" -> {
                pointManager.removePoints(targetUuid, amount);
                sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text("Removed " + amount + " black point(s) from ", NamedTextColor.GRAY))
                        .append(Component.text(resolvedName, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(".", NamedTextColor.GRAY)));
            }
            case "set" -> {
                pointManager.setPoints(targetUuid, resolvedName, amount);
                sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text("Set ", NamedTextColor.GRAY))
                        .append(Component.text(resolvedName, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text("'s black points to " + amount + ".", NamedTextColor.GRAY)));
            }
        }

        leaderboardDisplay.refreshScoreboard();
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("list");
            if (sender.hasPermission("hctogether.admin") || sender.isOp()) {
                subcommands.add("add");
                subcommands.add("remove");
                subcommands.add("set");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove") || sub.equals("set")) {
                if (sender.hasPermission("hctogether.admin") || sender.isOp()) {
                    Set<String> completions = new HashSet<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                    completions.addAll(pointManager.getLeaderboard().keySet());
                    return completions.stream()
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
