package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks a single active server challenge and awards top-3 on end.
 *
 * Challenge types:
 *   BLOCKS_MINED  — BlockBreakEvent
 *   PLAYER_KILLS  — PlayerDeathEvent (killer is a player)
 *   MOB_KILLS     — EntityDeathEvent (killer is a player)
 *   FISH_CAUGHT   — PlayerFishEvent (CAUGHT_FISH)
 *   CRATES_OPENED — tracked externally via increment(player, CRATES_OPENED)
 */
public class ChallengeManager {

    public enum ChallengeType {
        BLOCKS_MINED("Most Blocks Mined"),
        PLAYER_KILLS("Most Player Kills"),
        MOB_KILLS("Most Mob Kills"),
        FISH_CAUGHT("Most Fish Caught"),
        CRATES_OPENED("Most Crates Opened");

        public final String displayName;
        ChallengeType(String displayName) { this.displayName = displayName; }
    }

    /** Prize pool for top 3 places. */
    private static final long[] PRIZES = { 1_000_000L, 500_000L, 250_000L };

    private final EconomyManager economyManager;

    /** Active challenge type, null when no challenge is running. */
    private ChallengeType activeType = null;

    /** player UUID → score */
    private final Map<UUID, Integer> scores = new LinkedHashMap<>();

    /** UUID → last-known name (for display) */
    private final Map<UUID, String> names = new HashMap<>();

    public ChallengeManager(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    // ── Admin API ────────────────────────────────────────────────────────────

    /** Start a new challenge, resetting scores. */
    public boolean start(ChallengeType type) {
        if (activeType != null) return false; // already running
        activeType = type;
        scores.clear();
        names.clear();
        Bukkit.broadcastMessage("§6§l[Challenge] §r§eA new challenge has started: §f" + type.displayName + "§e!");
        Bukkit.broadcastMessage("§7Use §e/challenges §7to view the leaderboard.");
        return true;
    }

    /**
     * End the active challenge, pay top 3, broadcast results.
     * Returns summary string for admin confirmation.
     */
    public String end() {
        if (activeType == null) return null;

        List<Map.Entry<UUID, Integer>> sorted = scores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l[Challenge] §r§eChallenge ended: §f").append(activeType.displayName).append("§e!\n");

        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<UUID, Integer> entry = sorted.get(i);
            String name = names.getOrDefault(entry.getKey(), "?");
            long prize = PRIZES[i];
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            economyManager.depositPlayer(op, prize);
            String place = i == 0 ? "§6§l#1" : i == 1 ? "§7§l#2" : "§c§l#3";
            sb.append(place).append(" §f").append(name)
              .append(" §7— §a").append(entry.getValue()).append(" score")
              .append(" §8| §6$").append(formatMoney(prize)).append("\n");
        }
        if (sorted.isEmpty()) {
            sb.append("§7No participants. No prizes awarded.");
        }

        String summary = sb.toString().trim();
        Bukkit.broadcastMessage(summary);
        activeType = null;
        scores.clear();
        names.clear();
        return summary;
    }

    // ── Listener API ─────────────────────────────────────────────────────────

    /** Increment player's score for the given type (only if it matches active). */
    public void increment(UUID playerUuid, String playerName, ChallengeType type, int amount) {
        if (activeType != type) return;
        scores.merge(playerUuid, amount, Integer::sum);
        names.putIfAbsent(playerUuid, playerName);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public ChallengeType getActiveType() { return activeType; }

    public boolean isActive() { return activeType != null; }

    /** Returns top N entries sorted by score descending. */
    public List<Map.Entry<UUID, Integer>> getLeaderboard(int limit) {
        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public int getScore(UUID playerId) {
        return scores.getOrDefault(playerId, 0);
    }

    public Map<UUID, String> getNames() { return Collections.unmodifiableMap(names); }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public static String formatMoney(long amount) {
        if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
        if (amount >= 1_000)     return (amount / 1_000) + "K";
        return String.valueOf(amount);
    }
}
