package local.simplefactions;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Handles /serverclose and /serveropen.
 *
 * /serverclose – sends all online players to the lobby, locks the server so
 *               non-staff players (rank < MOD, level < 60) cannot rejoin.
 * /serveropen  – unlocks the server so anyone can join.
 *
 * Also listens on PlayerLoginEvent to enforce the lock.
 */
public class ServerLockCommand implements CommandExecutor, Listener {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final int STAFF_LEVEL = 60; // PlayerRank.MOD.getLevel()

    private final SimpleFactionsPlugin plugin;
    private final PlayerRankManager rankManager;
    private final ScheduledRebootManager rebootManager;
    private volatile boolean locked = false;

    public ServerLockCommand(SimpleFactionsPlugin plugin,
                             PlayerRankManager rankManager,
                             ScheduledRebootManager rebootManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.rebootManager = rebootManager;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    public boolean isLocked() { return locked; }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = label.toLowerCase();

        if (sub.equals("serverclose")) {
            if (!sender.hasPermission("simplefactions.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            locked = true;
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "[Server] " +
                    ChatColor.YELLOW + "The server is now " + ChatColor.RED + "closed" +
                    ChatColor.YELLOW + " to the public. Staff can still join.");
            // Evacuate non-staff players
            int sent = 0;
            String hub = plugin.getConfig().getString("proxy.hub-server", "lobby");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (rankManager.getRank(player).getLevel() < STAFF_LEVEL) {
                    player.sendMessage(ChatColor.YELLOW + "The server is closing for maintenance. Sending you to the hub...");
                    sendToServer(player, hub);
                    sent++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Server locked. Sent " + sent + " player(s) to " + hub + ".");
            return true;
        }

        if (sub.equals("serveropen")) {
            if (!sender.hasPermission("simplefactions.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            locked = false;
            Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[Server] " +
                    ChatColor.YELLOW + "The server is now " + ChatColor.GREEN + "open" +
                    ChatColor.YELLOW + " to all players!");
            sender.sendMessage(ChatColor.GREEN + "Server unlocked.");
            return true;
        }

        return false;
    }

    // ── Login gate ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!locked) return;

        PlayerRank rank = rankManager.getRankAtLogin(event.getPlayer().getUniqueId());
        if (rank.getLevel() >= STAFF_LEVEL) return; // Staff can always join

        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                ChatColor.RED + "The server is currently closed for maintenance.\n" +
                ChatColor.YELLOW + "Please try again later.");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void sendToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }
}
