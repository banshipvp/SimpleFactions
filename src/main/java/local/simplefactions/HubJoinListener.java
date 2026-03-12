package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HubJoinListener implements Listener {

    private final JavaPlugin plugin;

    public HubJoinListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The hub world no longer exists on this server — it is a separate Velocity server.
     * If a player's saved location is in the old hub world (e.g., from before the
     * migration), teleport them to the main world's spawn so they don't get stuck.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Schedule 1 tick later so the player's world is fully loaded before we check
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().getName().equalsIgnoreCase("hub")) {
                World spawn = Bukkit.getWorld("faction_spawn");
                if (spawn == null) spawn = Bukkit.getWorld("world"); // fallback
                if (spawn != null) {
                    player.teleport(spawn.getSpawnLocation());
                }
            }
        }, 1L);
    }
}
