package com.nexuscraft.nexusgate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LoginCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PasswordManager passwordManager;
    private final PendingAuthManager pendingAuthManager;
    private final LockoutManager lockoutManager;
    private final AuditLogger auditLogger;
    private final GateListener gateListener;
    private final AccessManager accessManager;

    public LoginCommandExecutor(JavaPlugin plugin, PasswordManager passwordManager, PendingAuthManager pendingAuthManager,
                                 LockoutManager lockoutManager, AuditLogger auditLogger, GateListener gateListener,
                                 AccessManager accessManager) {
        this.plugin = plugin;
        this.passwordManager = passwordManager;
        this.pendingAuthManager = pendingAuthManager;
        this.lockoutManager = lockoutManager;
        this.auditLogger = auditLogger;
        this.gateListener = gateListener;
        this.accessManager = accessManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /login.");
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (!pendingAuthManager.isPending(uuid)) {
            player.sendMessage(gateListener.format("not-pending", null));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(gateListener.format("usage-login", null));
            return true;
        }

        String ip = GateListener.ipOf(player);
        long lockedRemaining = lockoutManager.remainingLockoutSeconds(ip);
        if (lockedRemaining > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("remaining", GateListener.formatDuration(lockedRemaining));
            player.kickPlayer(gateListener.format("locked-out-kick", ph));
            return true;
        }

        PasswordManager.PasswordKind kind = BedrockUtil.kindOf(player, plugin.getLogger());
        if (passwordManager.verify(kind, args[0])) {
            pendingAuthManager.removeAndCancel(uuid);
            lockoutManager.clear(ip);
            gateListener.completeAuth(player);
            accessManager.recordSuccess(uuid, player.getName(), kind.sectionKey());
            player.sendMessage(gateListener.format("success", null));
            auditLogger.log("SUCCESS  player=" + player.getName() + " uuid=" + uuid + " ip=" + ip + " kind=" + kind);
            return true;
        }

        int attempts = pendingAuthManager.incrementAttempt(uuid);
        int maxAttempts = gateListener.getMaxAttempts();
        auditLogger.log("FAIL     player=" + player.getName() + " uuid=" + uuid + " ip=" + ip
                + " attempt=" + attempts + "/" + maxAttempts + " kind=" + kind);

        if (attempts >= maxAttempts) {
            pendingAuthManager.removeAndCancel(uuid);
            lockoutManager.registerFailure(ip);
            player.kickPlayer(gateListener.format("too-many-attempts-kick", null));
            return true;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("remaining", String.valueOf(maxAttempts - attempts));
        player.sendMessage(gateListener.format("wrong-password", ph));
        return true;
    }
}
