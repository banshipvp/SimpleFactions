package local.simplefactions;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Listens for world events and forwards relevant ones to ChallengeManager.
 */
public class ChallengeListener implements Listener {

    private final ChallengeManager manager;

    public ChallengeListener(ChallengeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(), ChallengeManager.ChallengeType.BLOCKS_MINED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        manager.increment(killer.getUniqueId(), killer.getName(), ChallengeManager.ChallengeType.PLAYER_KILLS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        // Skip player deaths (handled above)
        if (e.getEntityType() == EntityType.PLAYER) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        manager.increment(killer.getUniqueId(), killer.getName(), ChallengeManager.ChallengeType.MOB_KILLS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(), ChallengeManager.ChallengeType.FISH_CAUGHT, 1);
    }
}
