package com.nexuscraft.nexusgate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The core of NexusGate: freezes every not-yet-authenticated Java player on
 * join and blocks movement/interaction/commands/chat/damage until they type
 * /login <password> correctly, or kicks them on timeout / too many wrong
 * attempts. Bedrock players and configured exemptions skip all of this.
 */
public final class GateListener implements Listener {

    private final JavaPlugin plugin;
    private final PasswordManager passwordManager;
    private final PendingAuthManager pendingAuthManager;
    private final LockoutManager lockoutManager;
    private final AuditLogger auditLogger;

    private int timeoutSeconds = 60;
    private int maxAttempts = 3;
    private boolean exemptOperators = false;
    private boolean exemptBedrock = true;
    private Set<String> trustedUuids = new HashSet<>();
    private Map<String, String> messages = new HashMap<>();

    public GateListener(JavaPlugin plugin, PasswordManager passwordManager, PendingAuthManager pendingAuthManager,
                         LockoutManager lockoutManager, AuditLogger auditLogger) {
        this.plugin = plugin;
        this.passwordManager = passwordManager;
        this.pendingAuthManager = pendingAuthManager;
        this.lockoutManager = lockoutManager;
        this.auditLogger = auditLogger;
    }

    // ---------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------

    public void loadSettingsFromConfig() {
        plugin.reloadConfig();
        ConfigurationSection settings = plugin.getConfig().getConfigurationSection("settings");
        if (settings != null) {
            timeoutSeconds = settings.getInt("timeout-seconds", 60);
            maxAttempts = settings.getInt("max-attempts", 3);
            exemptOperators = settings.getBoolean("exempt-operators", false);
            exemptBedrock = settings.getBoolean("exempt-bedrock", true);
            List<String> uuids = settings.getStringList("trusted-uuids");
            trustedUuids = new HashSet<>(uuids);
        }

        List<Long> durations = new ArrayList<>();
        for (Object o : plugin.getConfig().getList("lockout.durations-seconds", new ArrayList<>())) {
            if (o instanceof Number) {
                durations.add(((Number) o).longValue());
            }
        }
        if (!durations.isEmpty()) {
            long[] arr = new long[durations.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = durations.get(i);
            }
            lockoutManager.setDurationsSeconds(arr);
        }

        messages = new HashMap<>();
        ConfigurationSection msgSection = plugin.getConfig().getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, msgSection.getString(key, ""));
            }
        }

        boolean loggingEnabled = plugin.getConfig().getBoolean("logging.enabled", true);
        String logFile = plugin.getConfig().getString("logging.file", "auth-log.txt");
        auditLogger.configure(loggingEnabled, logFile);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getMessage(String key) {
        String raw = messages.get(key);
        return raw != null ? raw : "";
    }

    public String format(String key, Map<String, String> placeholders) {
        String raw = getMessage(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return colorize(raw);
    }

    public static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    // ---------------------------------------------------------------
    // Exemptions
    // ---------------------------------------------------------------

    public boolean isExempt(Player player) {
        if (player.hasPermission("nexusgate.bypass")) {
            return true;
        }
        if (exemptOperators && player.isOp()) {
            return true;
        }
        if (trustedUuids.contains(player.getUniqueId().toString())) {
            return true;
        }
        boolean isBedrock = BedrockUtil.isBedrockPlayer(player, plugin.getLogger());
        if (exemptBedrock && isBedrock) {
            // Admin has explicitly chosen to keep Bedrock ungated even though a Bedrock
            // password can now be configured (v0.2.0) -- same opt-out as v0.1.0 had.
            return true;
        }
        // If this player's platform doesn't have a password configured yet, exempting them
        // is the only sane option -- gating with no password would just lock everyone out.
        PasswordManager.PasswordKind kind = isBedrock ? PasswordManager.PasswordKind.BEDROCK : PasswordManager.PasswordKind.JAVA;
        return !passwordManager.isSet(kind);
    }

    public static String ipOf(Player player) {
        try {
            InetAddress addr = player.getAddress().getAddress();
            return addr != null ? addr.getHostAddress() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ---------------------------------------------------------------
    // Pre-login lockout check (earliest possible point to reject)
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        boolean anyPasswordConfigured = passwordManager.isSet(PasswordManager.PasswordKind.JAVA)
                || passwordManager.isSet(PasswordManager.PasswordKind.BEDROCK);
        if (!anyPasswordConfigured) {
            return;
        }
        InetAddress addr = event.getAddress();
        String ip = addr != null ? addr.getHostAddress() : null;
        if (ip == null) {
            return;
        }
        long remaining = lockoutManager.remainingLockoutSeconds(ip);
        if (remaining > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("remaining", formatDuration(remaining));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, format("locked-out-kick", ph));
        }
    }

    // ---------------------------------------------------------------
    // Join -> begin the gate
    // ---------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PasswordManager.PasswordKind kind = BedrockUtil.kindOf(player, plugin.getLogger());

        if (!passwordManager.isSet(kind)) {
            plugin.getLogger().warning(getMessage("no-password-configured-warning").replace("{kind}", kind.sectionKey()));
            return;
        }

        if (isExempt(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String ip = ipOf(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("timeout", String.valueOf(timeoutSeconds));
        player.sendMessage(format("gate-chat", ph));
        try {
            player.sendTitle(colorize(getMessage("gate-title")), colorize(getMessage("gate-subtitle")), 10, 100, 20);
        } catch (Throwable ignored) {
            // Cosmetic only -- never let a title API mismatch break the gate itself.
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingAuthManager.isPending(uuid)) {
                pendingAuthManager.removeAndCancel(uuid);
                lockoutManager.registerFailure(ip);
                auditLogger.log("TIMEOUT  player=" + player.getName() + " uuid=" + uuid + " ip=" + ip);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.kickPlayer(colorize(getMessage("timeout-kick")));
                }
            }
        }, timeoutSeconds * 20L);

        pendingAuthManager.addPending(uuid, task);
        auditLogger.log("PENDING  player=" + player.getName() + " uuid=" + uuid + " ip=" + ip);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingAuthManager.removeAndCancel(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        pendingAuthManager.removeAndCancel(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------
    // Freeze: movement, damage, interaction, commands, chat
    // ---------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!pendingAuthManager.isPending(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() == null || event.getFrom() == null) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (pendingAuthManager.isPending(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player p = (Player) event.getDamager();
            if (pendingAuthManager.isPending(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (pendingAuthManager.isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (pendingAuthManager.isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (pendingAuthManager.isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (pendingAuthManager.isPending(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            if (pendingAuthManager.isPending(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player p = (Player) event.getPlayer();
            if (pendingAuthManager.isPending(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!pendingAuthManager.isPending(player.getUniqueId())) {
            return;
        }
        String message = event.getMessage().toLowerCase();
        if (message.startsWith("/login")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(format("must-authenticate-first", null));
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingAuthManager.isPending(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(format("must-authenticate-first", null));
        }
    }

    // ---------------------------------------------------------------
    // Helpers shared with the command executors
    // ---------------------------------------------------------------

    public static String formatDuration(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }
}
