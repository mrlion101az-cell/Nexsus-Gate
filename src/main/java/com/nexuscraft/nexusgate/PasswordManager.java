package com.nexuscraft.nexusgate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Owns two independent shared passwords -- one for Java players, one for Bedrock/Xbox players
 * (added in v0.2.0; v0.1.0 only ever had the one, Java-only, password). Neither is ever stored
 * or logged in plaintext -- only a salted SHA-256 hash each, persisted to its own small
 * password.yml so a config.yml reload/edit can never touch either by accident.
 *
 * Upgrading from v0.1.0: that version stored a single password directly at the top level of
 * password.yml. The first time this loads on an upgraded server, that old entry is migrated
 * into the new "java" section automatically (see {@link #migrateLegacyFormatIfNeeded}) so an
 * existing Java password is never lost or reset by this update.
 */
public final class PasswordManager {

    public enum PasswordKind {
        JAVA("java"),
        BEDROCK("bedrock");

        private final String sectionKey;

        PasswordKind(String sectionKey) {
            this.sectionKey = sectionKey;
        }

        public String sectionKey() {
            return sectionKey;
        }
    }

    private static final class Slot {
        boolean passwordSet = false;
        byte[] salt = new byte[0];
        byte[] hash = new byte[0];
    }

    private final JavaPlugin plugin;
    private final File file;
    private final SecureRandom random = new SecureRandom();
    private final Map<PasswordKind, Slot> slots = new EnumMap<>(PasswordKind.class);

    public PasswordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "password.yml");
        for (PasswordKind kind : PasswordKind.values()) {
            slots.put(kind, new Slot());
        }
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        migrateLegacyFormatIfNeeded(yaml);

        for (PasswordKind kind : PasswordKind.values()) {
            Slot slot = slots.get(kind);
            String prefix = kind.sectionKey() + ".";
            boolean set = yaml.getBoolean(prefix + "password-set", false);
            String saltHex = yaml.getString(prefix + "salt", "");
            String hashHex = yaml.getString(prefix + "hash", "");
            if (set && !saltHex.isEmpty() && !hashHex.isEmpty()) {
                slot.passwordSet = true;
                slot.salt = hexToBytes(saltHex);
                slot.hash = hexToBytes(hashHex);
            }
        }
    }

    /** v0.1.0 stored one password at the top level (password-set/salt/hash). If those legacy
     * keys are present and the new "java" section isn't, move them over and rewrite the file --
     * a one-time, automatic, lossless upgrade so nobody's existing Java password gets reset. */
    private void migrateLegacyFormatIfNeeded(YamlConfiguration yaml) {
        boolean legacySet = yaml.getBoolean("password-set", false);
        boolean alreadyMigrated = yaml.getBoolean("java.password-set", false);
        if (!legacySet || alreadyMigrated) {
            return;
        }
        String saltHex = yaml.getString("salt", "");
        String hashHex = yaml.getString("hash", "");
        if (saltHex.isEmpty() || hashHex.isEmpty()) {
            return;
        }
        yaml.set("java.password-set", true);
        yaml.set("java.salt", saltHex);
        yaml.set("java.hash", hashHex);
        yaml.set("password-set", null);
        yaml.set("salt", null);
        yaml.set("hash", null);
        try {
            yaml.save(file);
            plugin.getLogger().info("[NexusGate] Migrated your existing password into the new Java-specific slot. "
                    + "Run /nexusgate setpassword bedrock <password> (from console) to also require one for Bedrock/Xbox players.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate password.yml to the new per-platform format", e);
        }
    }

    public boolean isSet(PasswordKind kind) {
        return slots.get(kind).passwordSet;
    }

    /**
     * Sets (or changes) the password for one platform immediately. Takes effect for every
     * subsequent /login attempt from that platform right away; does not affect players already
     * authenticated in the current session, and never touches the other platform's password.
     */
    public void setPassword(PasswordKind kind, String plainPassword) {
        byte[] newSalt = new byte[16];
        random.nextBytes(newSalt);
        byte[] newHash = digest(newSalt, plainPassword);

        Slot slot = slots.get(kind);
        slot.salt = newSalt;
        slot.hash = newHash;
        slot.passwordSet = true;

        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String prefix = kind.sectionKey() + ".";
        yaml.set(prefix + "password-set", true);
        yaml.set(prefix + "salt", bytesToHex(newSalt));
        yaml.set(prefix + "hash", bytesToHex(newHash));
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save password.yml", e);
        }
    }

    /**
     * Constant-time-ish verification of an attempted password against the stored salted hash
     * for one platform. Returns false if that platform's password hasn't been set yet.
     */
    public boolean verify(PasswordKind kind, String attempt) {
        Slot slot = slots.get(kind);
        if (!slot.passwordSet || attempt == null) {
            return false;
        }
        byte[] attemptHash = digest(slot.salt, attempt);
        return MessageDigest.isEqual(attemptHash, slot.hash);
    }

    private byte[] digest(byte[] salt, String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(plain.getBytes("UTF-8"));
            return md.digest();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            // SHA-256 and UTF-8 are guaranteed present on every JVM; this can't happen.
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
