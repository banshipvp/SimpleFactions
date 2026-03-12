package local.simplefactions;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages scheduled server reboots.
 * - Reads the reboot interval from config: reboot.interval (in minutes, 0 = disabled)
 * - Broadcasts countdown warnings at configured alert times
 * - At T=0: sends all online players to the lobby, then restarts
 */
public class ScheduledRebootManager {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final SimpleFactionsPlugin plugin;
    private BukkitTask countdownTask;
    private long nextRebootMs = -1L;
    private final List<Long> alertSeconds = new ArrayList<>();

    public ScheduledRebootManager(SimpleFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);

        long intervalMinutes = plugin.getConfig().getLong("reboot.interval", 0L);
        if (intervalMinutes <= 0) return;

        alertSeconds.clear();
        for (String s : plugin.getConfig().getStringList("reboot.alert-times")) {
            long secs = parseDurationSecs(s);
            if (secs > 0) alertSeconds.add(secs);
        }

        long periodMs = intervalMinutes * 60_000L;
        nextRebootMs = System.currentTimeMillis() + periodMs;
        long periodTicks = intervalMinutes * 60L * 20L;

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remaining = Math.max(0L, (nextRebootMs - System.currentTimeMillis()) / 1000L);

            if (remaining == 0) {
                executeReboot();
                nextRebootMs = System.currentTimeMillis() + periodMs;
                return;
            }

            if (alertSeconds.contains(remaining)) {
                String timeStr = formatTime(remaining);
                Bukkit.broadcastMessage("\u00a76\u00a7l[Server] \u00a7eThe server will restart in \u00a7f" + timeStr + "\u00a7e!");
            }
        }, 20L, 20L);

        plugin.getLogger().info("Scheduled reboot every " + intervalMinutes + " minutes.");
    }

    public void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        nextRebootMs = -1L;
    }

    /** Seconds until the next reboot, or -1 if not scheduled. */
    public long getSecondsUntilReboot() {
        if (nextRebootMs < 0) return -1L;
        return Math.max(0L, (nextRebootMs - System.currentTimeMillis()) / 1000L);
    }

    /** Force an immediate reboot (called by /serverclose if desired, or by the timer). */
    public void forceReboot() {
        Bukkit.broadcastMessage("\u00a7c\u00a7l[Server] \u00a7eServer is restarting now. See you soon!");
        sendAllPlayersToLobby();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Bukkit.spigot().restart();
            } catch (Throwable t) {
                Bukkit.shutdown();
            }
        }, 60L); // 3 second delay so messages/sends can process
    }

    private void executeReboot() {
        Bukkit.broadcastMessage("\u00a7c\u00a7l[Server] \u00a7eServer is restarting now. See you soon!");
        sendAllPlayersToLobby();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Bukkit.spigot().restart();
            } catch (Throwable t) {
                Bukkit.shutdown();
            }
        }, 60L);
    }

    void sendAllPlayersToLobby() {
        String hubServer = plugin.getConfig().getString("proxy.hub-server", "lobby");
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendToServer(player, hubServer);
        }
    }

    private void sendToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds >= 3600) {
            long h = totalSeconds / 3600;
            long m = (totalSeconds % 3600) / 60;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (totalSeconds >= 60) {
            long m = totalSeconds / 60;
            long s = totalSeconds % 60;
            return s > 0 ? m + "m " + s + "s" : m + "m";
        }
        return totalSeconds + "s";
    }

    private long parseDurationSecs(String text) {
        if (text == null || text.isBlank()) return 0L;
        String v = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.endsWith("h")) return Long.parseLong(v.substring(0, v.length() - 1)) * 3600L;
            if (v.endsWith("m")) return Long.parseLong(v.substring(0, v.length() - 1)) * 60L;
            if (v.endsWith("s")) return Long.parseLong(v.substring(0, v.length() - 1));
            return Long.parseLong(v);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
