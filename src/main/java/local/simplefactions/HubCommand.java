package local.simplefactions;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /hub – sends the player to the Hub server via Velocity/BungeeCord plugin messaging.
 * The target server name is read from config: proxy.hub-server (default: "hub").
 */
public class HubCommand implements CommandExecutor {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;

    public HubCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        // Register the outgoing channel so plugin messages can be sent
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cOnly players can use /hub.");
            return true;
        }

        String hubServer = plugin.getConfig().getString("proxy.hub-server", "lobby");
        sendToServer(player, hubServer);
        player.sendMessage("\u00a7eSending you to the \u00a76Hub\u00a7e...");
        return true;
    }

    private void sendToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }
}
