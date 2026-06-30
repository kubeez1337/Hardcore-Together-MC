package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class VoteCommand implements CommandExecutor {

    private final VoteManager voteManager;

    public VoteCommand(VoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Only players can vote.", NamedTextColor.GRAY)));
            return true;
        }

        if (!voteManager.isVotingActive()) {
            player.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("There is no active vote right now.", NamedTextColor.GRAY)));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Usage: /hcvote <player>", NamedTextColor.GRAY)));
            return true;
        }

        String targetName = args[0];
        voteManager.castVote(player, targetName);
        return true;
    }
}
