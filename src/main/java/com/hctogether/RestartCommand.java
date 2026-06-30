package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Handles the {@code /hcrestart} command triggered by clicking the
 * [PLAY AGAIN] button after a Game Over, or run forcefully by operators.
 */
public final class RestartCommand implements CommandExecutor, TabCompleter {

    private final SyncListener listener;

    public RestartCommand(SyncListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        boolean force = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("force")) {
            if (sender.isOp() || !(sender instanceof Player)) {
                force = true;
            } else {
                sender.sendMessage(
                        Component.text("✖ ", NamedTextColor.RED)
                                .append(Component.text("Only server operators can use the force parameter.",
                                        NamedTextColor.GRAY)));
                return true;
            }
        }

        if (!force && !listener.isGameOver()) {
            sender.sendMessage(
                    Component.text("✖ ", NamedTextColor.RED)
                            .append(Component.text("Run still in progress. ", NamedTextColor.GRAY))
                            .append(Component.text("(Use '/hcrestart force' to force reset)",
                                    NamedTextColor.DARK_GRAY)));
            return true;
        }

        if (sender instanceof Player player) {
            player.sendMessage(
                    Component.text("⟳ ", NamedTextColor.GREEN)
                            .append(Component.text("Initiating world reset...", NamedTextColor.GREEN)));
        }

        listener.performWorldReset();
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1 && (sender.isOp() || !(sender instanceof Player))) {
            if ("force".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("force");
            }
        }
        return Collections.emptyList();
    }
}
