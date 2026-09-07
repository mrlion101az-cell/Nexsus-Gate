package com.nexuscraft.nexusgate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.logging.Level;

/**
 * Owns the single shared server password. Never stores or logs it in
 * plaintext -- only a salted SHA-256 hash, persisted to its own small
 * password.yml so a config.yml reload/edit can never touch it by accident.
 */
public final class PasswordManager {

    private final JavaPlugin plugin;
    private final File file;
    private final SecureRandom random = new SecureRandom();

    private boolean passwordSet = false;
    private byte[] salt = new byte[0];
    private byte[] hash = new byte[0];

    public PasswordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "password.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            passwordSet = false;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        passwordSet = yaml.getBoolean("password-set", false);
        String saltHex = yaml.getString("salt", "");
        String hashHex = yaml.getString("hash", "");
        if (passwordSet && !saltHex.isEmpty() && !hashHex.isEmpty()) {
            salt = hexToBytes(saltHex);
            hash = hexToBytes(hashHex);
        } else {
            passwordSet = false;
        }
    }

    public boolean isSet() {
        return passwordSet;
    }

    /**
     * Sets (or changes) the server password immediately. Takes effect for
     * every subsequent /login attempt right away; does not affect players
     * already authenticated in the current session.
     */
    public void setPassword(String plainPassword) {
        byte[] newSalt = new byte[16];
        random.nextBytes(newSalt);
        byte[] newHash = digest(newSalt, plainPassword);

        this.salt = newSalt;
        this.hash = newHash;
        this.passwordSet = true;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("password-set", true);
        yaml.set("salt", bytesToHex(newSalt));
        yaml.set("hash", bytesToHex(newHash));
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
     * Constant-time-ish verification of an attempted password against the
     * stored salted hash. Returns false if no password has been set yet.
     */
    public boolean verify(String attempt) {
        if (!passwordSet || attempt == null) {
            return false;
        }
        byte[] attemptHash = digest(salt, attempt);
        return MessageDigest.isEqual(attemptHash, hash);
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
