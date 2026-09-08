package com.nexuscraft.nexusgate;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Two related but separate things, both stored in access.yml:
 *
 * 1. A log of every gamer tag that has ever successfully typed the correct
 *    password (first seen, last seen, how many times, which platform) --
 *    the "who actually has access" list the admin asked for.
 *
 * 2. A NexusGate-specific ban list. This is deliberately independent of the
 *    server's normal /ban: it revokes ONE specific player's ability to use
 *    the shared password gate at all (checked before they can even finish
 *    connecting), without touching the shared password itself and without
 *    touching the server's own ban list. Useful for "this person knows the
 *    password but shouldn't anymore" without having to rotate the password
 *    for everyone else.
 *
 * Same "always read fresh off disk, never cache" approach as PasswordManager
 * -- this file is tiny and low-traffic (only touched on login/ban/unban), so
 * the disk round trip is cheap and it means hand-editing access.yml takes
 * effect immediately, same as config.yml already does.
 */
public final class AccessManager {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final class KnownPlayer {
        public UUID uuid;
        public String name;
        public long firstSeenMillis;
        public long lastSeenMillis;
        public int logins;
        public String kind;
    }

    public static final class BanEntry {
        public UUID uuid;
        public String name;
        public String reason;
        public String bannedBy;
        public long bannedAtMillis;
    }

    private final JavaPlugin plugin;
    private final File file;

    public AccessManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "access.yml");
    }

    // ---------------------------------------------------------------
    // Known players -- every gamer tag that has ever typed the correct password
    // ---------------------------------------------------------------

    public synchronized void recordSuccess(UUID uuid, String name, String kind) {
        YamlConfiguration yaml = load();
        String base = "known." + uuid;
        long now = System.currentTimeMillis();
        long firstSeen = yaml.getLong(base + ".first-seen", now);
        int logins = yaml.getInt(base + ".logins", 0) + 1;

        yaml.set(base + ".name", name);
        yaml.set(base + ".first-seen", firstSeen);
        yaml.set(base + ".last-seen", now);
        yaml.set(base + ".logins", logins);
        yaml.set(base + ".kind", kind);
        save(yaml);
    }

    public synchronized List<KnownPlayer> listKnownPlayers() {
        YamlConfiguration yaml = load();
        List<KnownPlayer> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("known");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                KnownPlayer kp = new KnownPlayer();
                kp.uuid = UUID.fromString(key);
                kp.name = section.getString(key + ".name", "?");
                kp.firstSeenMillis = section.getLong(key + ".first-seen", 0);
                kp.lastSeenMillis = section.getLong(key + ".last-seen", 0);
                kp.logins = section.getInt(key + ".logins", 0);
                kp.kind = section.getString(key + ".kind", "java");
                result.add(kp);
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID key -- skip a corrupt/hand-edited entry rather than fail.
            }
        }
        result.sort((a, b) -> Long.compare(b.lastSeenMillis, a.lastSeenMillis));
        return result;
    }

    // ---------------------------------------------------------------
    // Bans -- NexusGate-specific, independent of the server's own /ban
    // ---------------------------------------------------------------

    public synchronized void ban(UUID uuid, String name, String reason, String bannedBy) {
        YamlConfiguration yaml = load();
        String base = "bans." + uuid;
        yaml.set(base + ".name", name);
        yaml.set(base + ".reason", (reason == null || reason.isBlank()) ? "(no reason given)" : reason);
        yaml.set(base + ".banned-by", bannedBy);
        yaml.set(base + ".banned-at", System.currentTimeMillis());
        save(yaml);
    }

    /** Returns true if a ban actually existed and was removed. */
    public synchronized boolean unban(UUID uuid) {
        YamlConfiguration yaml = load();
        String base = "bans." + uuid;
        if (!yaml.isSet(base)) {
            return false;
        }
        yaml.set(base, null);
        save(yaml);
        return true;
    }

    public synchronized boolean isBanned(UUID uuid) {
        return getBan(uuid) != null;
    }

    public synchronized BanEntry getBan(UUID uuid) {
        YamlConfiguration yaml = load();
        String base = "bans." + uuid;
        if (!yaml.isSet(base)) {
            return null;
        }
        BanEntry b = new BanEntry();
        b.uuid = uuid;
        b.name = yaml.getString(base + ".name", "?");
        b.reason = yaml.getString(base + ".reason", "(no reason given)");
        b.bannedBy = yaml.getString(base + ".banned-by", "?");
        b.bannedAtMillis = yaml.getLong(base + ".banned-at", 0);
        return b;
    }

    public synchronized List<BanEntry> listBans() {
        YamlConfiguration yaml = load();
        List<BanEntry> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("bans");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                BanEntry b = getBan(uuid);
                if (b != null) {
                    result.add(b);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    public static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }
        return DISPLAY.format(Instant.ofEpochMilli(epochMillis));
    }

    // ---------------------------------------------------------------
    // Disk I/O
    // ---------------------------------------------------------------

    private YamlConfiguration load() {
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void save(YamlConfiguration yaml) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save access.yml", e);
        }
    }
}
