package com.hctogether;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class VoteManager {

    private static final int VOTE_DURATION_SECONDS = 15;

    private final HardcoreTogetherPlugin plugin;
    private final BlackPointManager pointManager;
    private final LeaderboardDisplay leaderboardDisplay;

    private volatile boolean votingActive = false;

    private final Map<UUID, UUID> votes = new HashMap<>();

    private final Map<UUID, String> candidates = new LinkedHashMap<>();

    private Runnable onVotingComplete;

    public VoteManager(HardcoreTogetherPlugin plugin, BlackPointManager pointManager,
            LeaderboardDisplay leaderboardDisplay) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        this.leaderboardDisplay = leaderboardDisplay;
    }

    public boolean isVotingActive() {
        return votingActive;
    }

    public void startVoting(Runnable onComplete) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();

        if (online.size() <= 1) {
            if (onComplete != null)
                onComplete.run();
            return;
        }

        this.onVotingComplete = onComplete;
        votes.clear();
        candidates.clear();

        for (Player p : online) {
            candidates.put(p.getUniqueId(), p.getName());
        }

        votingActive = true;

        for (Player voter : online) {
            sendVoteUI(voter);
        }

        new BukkitRunnable() {
            int secondsLeft = VOTE_DURATION_SECONDS;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    concludeVoting();
                    cancel();
                    return;
                }

                NamedTextColor color;
                if (secondsLeft <= 5) {
                    color = NamedTextColor.RED;
                } else if (secondsLeft <= 10) {
                    color = NamedTextColor.YELLOW;
                } else {
                    color = NamedTextColor.GREEN;
                }

                Component actionbar = Component.text("⚖ ", NamedTextColor.GOLD)
                        .append(Component.text(secondsLeft, color, TextDecoration.BOLD))
                        .append(Component.text("s to vote for Black Point", NamedTextColor.GRAY));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(actionbar);
                }

                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void sendVoteUI(Player voter) {
        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);

        Component header = Component.text("  ⚖ ", NamedTextColor.GOLD)
                .append(Component.text("VOTE: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Who caused the failure?", NamedTextColor.GRAY))
                .append(Component.text(" (15s)", NamedTextColor.DARK_GRAY));

        voter.sendMessage(Component.empty());
        voter.sendMessage(separator);
        voter.sendMessage(header);
        voter.sendMessage(Component.empty());

        for (Map.Entry<UUID, String> entry : candidates.entrySet()) {
            boolean isSelf = entry.getKey().equals(voter.getUniqueId());
            String candidateName = entry.getValue();
            String displayName = isSelf ? candidateName + " (You)" : candidateName;

            Component hoverComponent;
            if (isSelf) {
                hoverComponent = Component.text("Click to blame ", NamedTextColor.RED)
                        .append(Component.text("yourself", NamedTextColor.AQUA))
                        .append(Component.text(" for this failure!", NamedTextColor.RED));
            } else {
                hoverComponent = Component.text("Click to blame ", NamedTextColor.RED)
                        .append(Component.text(candidateName, NamedTextColor.AQUA))
                        .append(Component.text(" for this failure!", NamedTextColor.RED));
            }

            Component voteButton = Component.text("    ")
                    .append(Component.text("  ▶ ", NamedTextColor.DARK_RED))
                    .append(Component.text(displayName, NamedTextColor.AQUA, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/hcvote " + candidateName))
                            .hoverEvent(HoverEvent.showText(hoverComponent)));

            voter.sendMessage(voteButton);
        }

        voter.sendMessage(Component.empty());
        voter.sendMessage(separator);
        voter.sendMessage(Component.empty());
    }

    public boolean castVote(Player voter, String targetName) {
        if (!votingActive)
            return false;

        if (votes.containsKey(voter.getUniqueId())) {
            voter.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("You have already voted!", NamedTextColor.GRAY)));
            return false;
        }

        UUID targetUuid = null;
        String resolvedName = null;
        for (Map.Entry<UUID, String> entry : candidates.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                targetUuid = entry.getKey();
                resolvedName = entry.getValue();
                break;
            }
        }

        if (targetUuid == null) {
            voter.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Player not found: " + targetName, NamedTextColor.GRAY)));
            return false;
        }

        votes.put(voter.getUniqueId(), targetUuid);

        voter.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                .append(Component.text("You voted for ", NamedTextColor.GRAY))
                .append(Component.text(resolvedName, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GRAY)));

        int totalVotes = votes.size();
        int totalVoters = candidates.size();
        Component progress = Component.text("⚖ ", NamedTextColor.GOLD)
                .append(Component.text(totalVotes + "/" + (totalVoters) + " ", NamedTextColor.YELLOW))
                .append(Component.text("votes cast", NamedTextColor.GRAY));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(progress);
        }

        long eligibleVoters = candidates.size();
        if (totalVotes >= eligibleVoters) {
            concludeVoting();
        }

        return true;
    }

    private void concludeVoting() {
        if (!votingActive)
            return;
        votingActive = false;

        Map<UUID, Integer> tally = new HashMap<>();
        for (UUID targetUuid : votes.values()) {
            tally.merge(targetUuid, 1, Integer::sum);
        }

        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);

        if (tally.isEmpty()) {
            Component noVotes = Component.text("  ⚖ ", NamedTextColor.GOLD)
                    .append(Component.text("No votes were cast. No Black Point awarded.", NamedTextColor.GRAY));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(Component.empty());
                p.sendMessage(separator);
                p.sendMessage(noVotes);
                p.sendMessage(separator);
                p.sendMessage(Component.empty());
            }
        } else {
            int maxVotes = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);

            List<UUID> winners = new ArrayList<>();
            for (Map.Entry<UUID, Integer> entry : tally.entrySet()) {
                if (entry.getValue() == maxVotes) {
                    winners.add(entry.getKey());
                }
            }

            Component header = Component.text("  ⚖ ", NamedTextColor.GOLD)
                    .append(Component.text("VOTE RESULTS", NamedTextColor.RED, TextDecoration.BOLD));

            List<Component> resultLines = new ArrayList<>();
            tally.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        String name = candidates.getOrDefault(entry.getKey(), "Unknown");
                        int voteCount = entry.getValue();
                        boolean isWinner = winners.contains(entry.getKey());

                        Component line;
                        if (isWinner) {
                            line = Component.text("    ☠ ", NamedTextColor.DARK_RED)
                                    .append(Component.text(name, NamedTextColor.RED, TextDecoration.BOLD))
                                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(voteCount + " vote" + (voteCount != 1 ? "s" : ""),
                                            NamedTextColor.YELLOW))
                                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                                    .append(Component.text("+1 Black Point!", NamedTextColor.DARK_RED,
                                            TextDecoration.BOLD));
                        } else {
                            line = Component.text("    ○ ", NamedTextColor.GRAY)
                                    .append(Component.text(name, NamedTextColor.AQUA))
                                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(voteCount + " vote" + (voteCount != 1 ? "s" : ""),
                                            NamedTextColor.GRAY));
                        }
                        resultLines.add(line);
                    });

            for (UUID winnerUuid : winners) {
                String winnerName = candidates.getOrDefault(winnerUuid, "Unknown");
                pointManager.addPoint(winnerUuid, winnerName);
            }

            leaderboardDisplay.refreshScoreboard();

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(Component.empty());
                p.sendMessage(separator);
                p.sendMessage(header);
                p.sendMessage(Component.empty());
                for (Component line : resultLines) {
                    p.sendMessage(line);
                }
                p.sendMessage(Component.empty());
                p.sendMessage(separator);
                p.sendMessage(Component.empty());
            }
        }

        if (onVotingComplete != null) {
            onVotingComplete.run();
        }
    }
}
