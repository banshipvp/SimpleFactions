package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /challenges               — view leaderboard (all players)
 * /challenge start <type>   — start a challenge  (admin)
 * /challenge end            — end + pay prizes   (admin)
 * /challenge info           — show active type   (all)
 *
 * Registers as both "challenges" and "challenge" in plugin.yml.
 */
public class ChallengeCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "simplefactions.admin";
    private final ChallengeManager manager;

    public ChallengeCommand(ChallengeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String root = label.toLowerCase();

        // /challenges  — shortcut to show leaderboard
        if (root.equals("challenges") && args.length == 0) {
            showLeaderboard(sender);
            return true;
        }

        if (args.length == 0) {
            showLeaderboard(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "info" -> showInfo(sender);
            case "start" -> {
                if (!sender.hasPermission(ADMIN_PERM)) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /challenge start <" + typeList() + ">");
                    return true;
                }
                ChallengeManager.ChallengeType type = parseType(args[1]);
                if (type == null) {
                    sender.sendMessage("§cUnknown type. Valid: " + typeList());
                    return true;
                }
                if (!manager.start(type)) {
                    sender.sendMessage("§cA challenge is already running! Use §e/challenge end §cfirst.");
                    return true;
                }
                sender.sendMessage("§aStarted challenge: §e" + type.displayName);
            }
            case "end" -> {
                if (!sender.hasPermission(ADMIN_PERM)) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                String result = manager.end();
                if (result == null) {
                    sender.sendMessage("§cNo active challenge.");
                } else {
                    sender.sendMessage("§aChallenge ended and prizes paid.");
                }
            }
            default -> showLeaderboard(sender);
        }
        return true;
    }

    private void showLeaderboard(CommandSender sender) {
        if (!manager.isActive()) {
            sender.sendMessage("§7There is no active challenge right now.");
            sender.sendMessage("§8Ask an admin to run §e/challenge start§8.");
            return;
        }
        List<Map.Entry<UUID, Integer>> top = manager.getLeaderboard(10);
        Map<UUID, String> names = manager.getNames();
        sender.sendMessage("§6§l✦ Challenge: §f" + manager.getActiveType().displayName);
        sender.sendMessage("§8─────────────────────");
        if (top.isEmpty()) {
            sender.sendMessage("§7No scores yet. Be the first!");
        }
        String[] medals = {"§6#1", "§7#2", "§c#3"};
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<UUID, Integer> entry = top.get(i);
            String rank = i < medals.length ? medals[i] : "§8#" + (i + 1);
            String name = names.getOrDefault(entry.getKey(), "?");
            sender.sendMessage(rank + " §f" + name + " §8— §e" + entry.getValue());
        }
        sender.sendMessage("§8─────────────────────");
        sender.sendMessage("§7Top 3 prizes: §61M §8/ §7500K §8/ §c250K");
    }

    private void showInfo(CommandSender sender) {
        if (!manager.isActive()) {
            sender.sendMessage("§7No active challenge.");
            return;
        }
        sender.sendMessage("§eActive challenge: §f" + manager.getActiveType().displayName);
        sender.sendMessage("§7Top 3 prizes: §61M §8/ §7500K §8/ §c250K");
    }

    private static ChallengeManager.ChallengeType parseType(String s) {
        for (ChallengeManager.ChallengeType t : ChallengeManager.ChallengeType.values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }

    private static String typeList() {
        return Arrays.stream(ChallengeManager.ChallengeType.values())
                .map(Enum::name)
                .map(String::toLowerCase)
                .collect(Collectors.joining("|"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("info", "start", "end"));
            return opts.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Arrays.stream(ChallengeManager.ChallengeType.values())
                    .map(t -> t.name().toLowerCase())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
