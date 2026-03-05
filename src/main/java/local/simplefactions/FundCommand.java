package local.simplefactions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /fund — view and interact with server milestones.
 *
 * Player usage:
 *   /fund                   - list all milestones
 *   /fund info <id>         - detailed info
 *   /fund donate <id> <amt> - donate money toward a GOAL milestone
 *
 * Admin (simplefactions.admin):
 *   /fund add <id> time <seconds> <description>
 *   /fund add <id> goal <amount> <description>
 *   /fund remove <id>
 *   /fund forceunlock <id>
 *   /fund forcelock <id>
 *   /fund reset             - reset server start time to now
 */
public class FundCommand implements CommandExecutor, TabCompleter {

    private final MilestoneManager milestones;
    private final EconomyManager economy;

    public FundCommand(MilestoneManager milestones, EconomyManager economy) {
        this.milestones = milestones;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            listMilestones(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /fund info <id>"); return true; }
                infoMilestone(sender, args[1]);
            }

            case "donate" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
                if (args.length < 3) { sender.sendMessage("§cUsage: /fund donate <id> <amount>"); return true; }
                donateMilestone(player, args[1], args[2]);
            }

            case "add" -> {
                if (!sender.hasPermission("simplefactions.admin")) {
                    sender.sendMessage("§cNo permission."); return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /fund add <id> <time|goal> <value> <description...>");
                    return true;
                }
                addMilestone(sender, args);
            }

            case "remove" -> {
                if (!sender.hasPermission("simplefactions.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /fund remove <id>"); return true; }
                if (milestones.removeMilestone(args[1])) {
                    sender.sendMessage("§a✓ Milestone §e" + args[1] + " §aremoved.");
                } else {
                    sender.sendMessage("§cMilestone not found: §e" + args[1]);
                }
            }

            case "forceunlock" -> {
                if (!sender.hasPermission("simplefactions.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /fund forceunlock <id>"); return true; }
                if (milestones.forceUnlock(args[1])) {
                    sender.sendMessage("§a✓ Milestone §e" + args[1] + " §aforce-unlocked.");
                } else {
                    sender.sendMessage("§cMilestone not found.");
                }
            }

            case "forcelock" -> {
                if (!sender.hasPermission("simplefactions.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /fund forcelock <id>"); return true; }
                if (milestones.forceLock(args[1])) {
                    sender.sendMessage("§a✓ Milestone §e" + args[1] + " §aforce-locked.");
                } else {
                    sender.sendMessage("§cMilestone not found.");
                }
            }

            case "reset" -> {
                if (!sender.hasPermission("simplefactions.admin")) { sender.sendMessage("§cNo permission."); return true; }
                milestones.resetServerStart();
                sender.sendMessage("§a✓ Server start time reset to now. All time-based milestones restart.");
            }

            default -> sender.sendMessage("§cUsage: §e/fund §7[info|donate|add|remove|forceunlock|forcelock|reset]");
        }

        return true;
    }

    private void listMilestones(CommandSender sender) {
        Collection<MilestoneManager.Milestone> all = milestones.all();
        sender.sendMessage("§5▬▬▬▬▬▬▬▬▬▬ §d§lServer Milestones §5▬▬▬▬▬▬▬▬▬▬");
        if (all.isEmpty()) {
            sender.sendMessage("§7No milestones configured.");
            return;
        }
        long start = milestones.getServerStartEpochSeconds();
        for (MilestoneManager.Milestone m : all) {
            String status = m.isUnlocked(start) ? "§a✔" : "§c✘";
            sender.sendMessage(status + " §f" + m.id + " §8— §7" + m.description);
        }
        sender.sendMessage("§7Use §e/fund info <id> §7for details.");
        sender.sendMessage("§5▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void infoMilestone(CommandSender sender, String id) {
        MilestoneManager.Milestone m = milestones.getMilestone(id);
        if (m == null) { sender.sendMessage("§cMilestone not found: §e" + id); return; }
        long start = milestones.getServerStartEpochSeconds();
        sender.sendMessage("§5▬▬▬▬▬▬▬▬▬▬ §d§l" + m.id + " §5▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§7Description: §f" + m.description);
        sender.sendMessage("§7Type: §f" + m.type.name());
        if (m.type == MilestoneManager.MilestoneType.TIME) {
            sender.sendMessage("§7Requires: §fServer open for §e" + MilestoneManager.formatDuration((long) m.value));
        } else {
            sender.sendMessage("§7Goal: §f$" + MilestoneManager.formatMoney((long) m.value));
            sender.sendMessage("§7Donated: §a$" + MilestoneManager.formatMoney((long) m.donated));
            sender.sendMessage("§7Remaining: §e$" + MilestoneManager.formatMoney((long) Math.max(0, m.value - m.donated)));
        }
        sender.sendMessage("§7Unlocks: §f" + m.unlockDescription);
        sender.sendMessage("§7Status: " + m.statusLine(start));
        sender.sendMessage("§5▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void donateMilestone(Player player, String id, String amountStr) {
        MilestoneManager.Milestone m = milestones.getMilestone(id);
        if (m == null) { player.sendMessage("§cMilestone not found: §e" + id); return; }
        if (m.type != MilestoneManager.MilestoneType.GOAL) {
            player.sendMessage("§cMilestone §e" + id + " §cis time-based — donations don't apply."); return;
        }
        if (m.isUnlocked(milestones.getServerStartEpochSeconds())) {
            player.sendMessage("§cMilestone §e" + id + " §cis already unlocked."); return;
        }
        if (!economy.isEnabled()) { player.sendMessage("§cEconomy is not available."); return; }

        double amount;
        try { amount = Double.parseDouble(amountStr); }
        catch (NumberFormatException e) { player.sendMessage("§cInvalid amount."); return; }
        if (amount <= 0) { player.sendMessage("§cAmount must be positive."); return; }

        if (!economy.has(player, amount)) {
            player.sendMessage("§cYou don't have §e$" + (long) amount + "§c."); return;
        }

        double donated = milestones.donate(id, amount);
        if (donated <= 0) { player.sendMessage("§cCould not donate."); return; }
        economy.withdrawPlayer(player, donated);
        player.sendMessage("§a✓ Donated §e$" + MilestoneManager.formatMoney((long) donated)
                + " §ato §d" + id + "§a.");

        if (m.isUnlocked(milestones.getServerStartEpochSeconds())) {
            player.getServer().broadcastMessage(
                    "§5§l[MILESTONE] §d" + m.unlockDescription + " §5§lhas been unlocked!");
        } else {
            double remaining = m.value - m.donated;
            player.sendMessage("§7§e$" + MilestoneManager.formatMoney((long) remaining)
                    + " §7more needed to unlock §f" + m.id + "§7.");
        }
    }

    private void addMilestone(CommandSender sender, String[] args) {
        // /fund add <id> <time|goal> <value> <description...>
        String id = args[1].toLowerCase();
        String typeStr = args[2].toLowerCase();
        MilestoneManager.MilestoneType type;
        if (typeStr.equals("time")) type = MilestoneManager.MilestoneType.TIME;
        else if (typeStr.equals("goal")) type = MilestoneManager.MilestoneType.GOAL;
        else { sender.sendMessage("§cType must be §etime §cor §egoal§c."); return; }

        double value;
        try { value = Double.parseDouble(args[3]); }
        catch (NumberFormatException e) { sender.sendMessage("§cInvalid value."); return; }

        String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        String unlockDesc = "§f" + id + " §aunlocked";

        milestones.addMilestone(id, desc, type, value, unlockDesc);
        sender.sendMessage("§a✓ Milestone §e" + id + " §acreated (" + type.name()
                + " — " + (type == MilestoneManager.MilestoneType.TIME
                ? MilestoneManager.formatDuration((long) value)
                : "$" + MilestoneManager.formatMoney((long) value)) + ").");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = java.util.Arrays.asList("info", "donate");
            if (sender.hasPermission("simplefactions.admin")) {
                subs = java.util.Arrays.asList("info", "donate", "add", "remove",
                        "forceunlock", "forcelock", "reset");
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("reset")) {
            return milestones.all().stream().map(m -> m.id)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
