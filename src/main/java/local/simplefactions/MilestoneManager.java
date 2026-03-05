package local.simplefactions;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages server milestones — time-gated or donation-goal events.
 *
 * Milestone types:
 *   TIME  — automatically unlocks N seconds after serverStartTime
 *   GOAL  — unlocks when totalDonated >= goal amount (players donate via /fund donate)
 *
 * Data is persisted to milestones.yml in the plugin data folder.
 */
public class MilestoneManager {

    public enum MilestoneType { TIME, GOAL }

    public static class Milestone {
        public String id;
        public String description;
        public MilestoneType type;
        /** seconds (TIME) or money (GOAL) */
        public double value;
        /** For GOAL milestones: how much has been donated so far */
        public double donated;
        /** Manually forced unlock / already unlocked */
        public boolean forceUnlocked;

        /** Human-readable display of what unlocks. */
        public String unlockDescription;

        public Milestone(String id, String description, MilestoneType type,
                         double value, String unlockDescription) {
            this.id = id;
            this.description = description;
            this.type = type;
            this.value = value;
            this.unlockDescription = unlockDescription;
            this.donated = 0;
            this.forceUnlocked = false;
        }

        public boolean isUnlocked(long serverStartEpochSeconds) {
            if (forceUnlocked) return true;
            if (type == MilestoneType.TIME) {
                long elapsed = System.currentTimeMillis() / 1000L - serverStartEpochSeconds;
                return elapsed >= (long) value;
            }
            return donated >= value;
        }

        public String statusLine(long serverStartEpochSeconds) {
            if (isUnlocked(serverStartEpochSeconds)) return "§a✔ UNLOCKED";
            if (type == MilestoneType.TIME) {
                long elapsed = System.currentTimeMillis() / 1000L - serverStartEpochSeconds;
                long remaining = (long) value - elapsed;
                return "§c✘ Locked — §f" + formatDuration(Math.max(0, remaining)) + " remaining";
            }
            double pct = value == 0 ? 100 : (donated / value) * 100;
            return String.format("§c✘ Locked — §f$%s §7/ §f$%s §8(§e%.1f%%§8)",
                    formatMoney((long) donated), formatMoney((long) value), pct);
        }
    }

    // -----------------------------------------------------------------------

    private final File dataFile;
    private long serverStartEpochSeconds;
    private final Map<String, Milestone> milestones = new LinkedHashMap<>();

    public MilestoneManager(File dataFolder) {
        dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "milestones.yml");
        load();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public boolean isUnlocked(String id) {
        Milestone m = milestones.get(id.toLowerCase());
        return m == null || m.isUnlocked(serverStartEpochSeconds);
    }

    public Milestone getMilestone(String id) {
        return milestones.get(id.toLowerCase());
    }

    public Collection<Milestone> all() {
        return Collections.unmodifiableCollection(milestones.values());
    }

    public long getServerStartEpochSeconds() { return serverStartEpochSeconds; }

    // ── Mutations ────────────────────────────────────────────────────────────

    public void addMilestone(String id, String description, MilestoneType type,
                              double value, String unlockDescription) {
        milestones.put(id.toLowerCase(),
                new Milestone(id.toLowerCase(), description, type, value, unlockDescription));
        save();
    }

    public boolean removeMilestone(String id) {
        boolean removed = milestones.remove(id.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public double donate(String id, double amount) {
        Milestone m = milestones.get(id.toLowerCase());
        if (m == null || m.type != MilestoneType.GOAL) return 0;
        if (m.isUnlocked(serverStartEpochSeconds)) return 0;
        double remaining = m.value - m.donated;
        double actual = Math.min(amount, remaining);
        m.donated += actual;
        save();
        return actual;
    }

    public boolean forceUnlock(String id) {
        Milestone m = milestones.get(id.toLowerCase());
        if (m == null) return false;
        m.forceUnlocked = true;
        save();
        return true;
    }

    public boolean forceLock(String id) {
        Milestone m = milestones.get(id.toLowerCase());
        if (m == null) return false;
        m.forceUnlocked = false;
        m.donated = 0;
        save();
        return true;
    }

    /** Reset server start time to now (call on /fund reset). */
    public void resetServerStart() {
        serverStartEpochSeconds = System.currentTimeMillis() / 1000L;
        save();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            serverStartEpochSeconds = System.currentTimeMillis() / 1000L;
            // Seed default milestone for the gkits time-lock example
            milestones.put("gkits", new Milestone("gkits",
                    "Server must be open for 1 hour before GKits are available",
                    MilestoneType.TIME, 3600, "GKits unlocked"));
            save();
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        serverStartEpochSeconds = cfg.getLong("server-start", System.currentTimeMillis() / 1000L);
        if (cfg.contains("milestones")) {
            for (String id : Objects.requireNonNull(cfg.getConfigurationSection("milestones")).getKeys(false)) {
                String base = "milestones." + id;
                String desc = cfg.getString(base + ".description", "");
                MilestoneType type;
                try { type = MilestoneType.valueOf(cfg.getString(base + ".type", "TIME")); }
                catch (Exception e) { type = MilestoneType.TIME; }
                double value = cfg.getDouble(base + ".value", 3600);
                double donated = cfg.getDouble(base + ".donated", 0);
                boolean force = cfg.getBoolean(base + ".force-unlocked", false);
                String unlock = cfg.getString(base + ".unlock-description", "");
                Milestone m = new Milestone(id, desc, type, value, unlock);
                m.donated = donated;
                m.forceUnlocked = force;
                milestones.put(id, m);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("server-start", serverStartEpochSeconds);
        for (Milestone m : milestones.values()) {
            String base = "milestones." + m.id;
            cfg.set(base + ".description", m.description);
            cfg.set(base + ".type", m.type.name());
            cfg.set(base + ".value", m.value);
            cfg.set(base + ".donated", m.donated);
            cfg.set(base + ".force-unlocked", m.forceUnlocked);
            cfg.set(base + ".unlock-description", m.unlockDescription);
        }
        try { cfg.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ── Formatting helpers ───────────────────────────────────────────────────

    public static String formatDuration(long seconds) {
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String formatMoney(long amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }
}
