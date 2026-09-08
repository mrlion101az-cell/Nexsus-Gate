package com.nexuscraft.nexusgate;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NexusGateCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PasswordManager passwordManager;
    private final PendingAuthManager pendingAuthManager;
    private final LockoutManager lockoutManager;
    private final GateListener gateListener;
    private final AccessManager accessManager;

    public NexusGateCommandExecutor(JavaPlugin plugin, PasswordManager passwordManager, PendingAuthManager pendingAuthManager,
                                     LockoutManager lockoutManager, GateListener gateListener, AccessManager accessManager) {
        this.plugin = plugin;
        this.passwordManager = passwordManager;
        this.pendingAuthManager = pendingAuthManager;
        this.lockoutManager = lockoutManager;
        this.gateListener = gateListener;
        this.accessManager = accessManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setpassword": {
                if (args.length < 2) {
                    sender.sendMessage(GateListener.colorize("&cUsage: /nexusgate setpassword [java|bedrock] <password>"));
                    return true;
                }

                // /nexusgate setpassword java|bedrock <password...> -- the (v0.2.0) form.
                PasswordManager.PasswordKind kind;
                int passwordStart;
                String maybeKind = args[1].toLowerCase();
                if (maybeKind.equals("java") || maybeKind.equals("bedrock")) {
                    if (args.length < 3) {
                        sender.sendMessage(GateListener.colorize("&cUsage: /nexusgate setpassword " + maybeKind + " <password>"));
                        return true;
                    }
                    kind = maybeKind.equals("bedrock") ? PasswordManager.PasswordKind.BEDROCK : PasswordManager.PasswordKind.JAVA;
                    passwordStart = 2;
                } else if (!passwordManager.isSet(PasswordManager.PasswordKind.JAVA)
                        && !passwordManager.isSet(PasswordManager.PasswordKind.BEDROCK)) {
                    // Bare old-style form (/nexusgate setpassword <password>), with NEITHER
                    // password configured yet -- a genuinely fresh install, so it's unambiguous
                    // to assume this is the first-time Java setup, same as v0.1.0 always did.
                    kind = PasswordManager.PasswordKind.JAVA;
                    passwordStart = 1;
                } else {
                    // At least one password already exists (an upgraded server, most likely),
                    // so the bare form is now REFUSED rather than silently guessing which
                    // platform you meant -- that guess used to default to Java no matter what,
                    // which is exactly the kind of silent surprise that can overwrite a
                    // password you didn't mean to touch. Spell it out from here on.
                    sender.sendMessage(GateListener.colorize("&cBoth Java and Bedrock passwords are supported now -- say which one:"));
                    sender.sendMessage(GateListener.colorize("&7/nexusgate setpassword java <password>"));
                    sender.sendMessage(GateListener.colorize("&7/nexusgate setpassword bedrock <password>"));
                    return true;
                }

                String newPassword = String.join(" ", Arrays.copyOfRange(args, passwordStart, args.length));
                passwordManager.setPassword(kind, newPassword);
                sender.sendMessage(GateListener.colorize("&aNexusGate " + kind.sectionKey() + " password updated."));
                if (sender instanceof Player) {
                    sender.sendMessage(GateListener.colorize("&7Tip: run this from the server console next time so the password doesn't sit in chat/player logs."));
                }
                plugin.getLogger().info("[NexusGate] " + kind.name() + " password changed by " + sender.getName() + ".");
                return true;
            }
            case "reload": {
                gateListener.loadSettingsFromConfig();
                sender.sendMessage(GateListener.colorize("&aNexusGate config reloaded."));
                return true;
            }
            case "ban": {
                if (args.length < 2) {
                    sender.sendMessage(GateListener.colorize("&cUsage: /nexusgate ban <player> [reason...]"));
                    return true;
                }
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
                accessManager.ban(target.getUniqueId(), targetName, reason, sender.getName());

                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("reason", reason == null || reason.isBlank() ? "(no reason given)" : reason);
                    online.kickPlayer(gateListener.format("banned-kick", ph));
                }

                sender.sendMessage(GateListener.colorize("&aBanned " + targetName
                        + " from NexusGate" + (online != null ? " and kicked them." : ".")));
                plugin.getLogger().info("[NexusGate] " + targetName + " banned by " + sender.getName()
                        + (reason != null ? " (" + reason + ")" : ""));
                return true;
            }
            case "unban": {
                if (args.length < 2) {
                    sender.sendMessage(GateListener.colorize("&cUsage: /nexusgate unban <player>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                boolean removed = accessManager.unban(target.getUniqueId());
                sender.sendMessage(GateListener.colorize(removed
                        ? "&aUnbanned " + args[1] + " from NexusGate."
                        : "&7" + args[1] + " wasn't NexusGate-banned."));
                return true;
            }
            case "banlist": {
                List<AccessManager.BanEntry> bans = accessManager.listBans();
                if (bans.isEmpty()) {
                    sender.sendMessage(GateListener.colorize("&7No NexusGate bans."));
                    return true;
                }
                sender.sendMessage(GateListener.colorize("&6NexusGate bans (&f" + bans.size() + "&6):"));
                for (AccessManager.BanEntry b : bans) {
                    sender.sendMessage(GateListener.colorize("&7- &f" + b.name + " &7- " + b.reason
                            + " &7(by " + b.bannedBy + ", " + AccessManager.formatTime(b.bannedAtMillis) + ")"));
                }
                return true;
            }
            case "knownusers": {
                List<AccessManager.KnownPlayer> known = accessManager.listKnownPlayers();
                if (known.isEmpty()) {
                    sender.sendMessage(GateListener.colorize("&7Nobody has typed a correct password yet."));
                    return true;
                }
                int shown = Math.min(known.size(), 50);
                sender.sendMessage(GateListener.colorize("&6Players who have used the password (&f" + known.size()
                        + "&6" + (known.size() > shown ? ", showing most recent " + shown : "") + "):"));
                for (int i = 0; i < shown; i++) {
                    AccessManager.KnownPlayer kp = known.get(i);
                    boolean banned = accessManager.isBanned(kp.uuid);
                    sender.sendMessage(GateListener.colorize("&7- &f" + kp.name + " &7[" + kp.kind + "] logins: &f" + kp.logins
                            + " &7last: &f" + AccessManager.formatTime(kp.lastSeenMillis)
                            + (banned ? " &c[BANNED]" : "")));
                }
                return true;
            }
            case "status": {
                sender.sendMessage(GateListener.colorize("&6NexusGate status:"));
                sender.sendMessage(GateListener.colorize("&7Java password: "
                        + (passwordManager.isSet(PasswordManager.PasswordKind.JAVA)
                                ? "&a" + passwordManager.currentValue(PasswordManager.PasswordKind.JAVA)
                                : "&c(not set)")));
                sender.sendMessage(GateListener.colorize("&7Bedrock password: "
                        + (passwordManager.isSet(PasswordManager.PasswordKind.BEDROCK)
                                ? "&a" + passwordManager.currentValue(PasswordManager.PasswordKind.BEDROCK)
                                : "&c(not set)")));
                sender.sendMessage(GateListener.colorize("&7(Both are also visible/editable directly in config.yml under 'passwords:'.)"));
                sender.sendMessage(GateListener.colorize("&7Timeout: &f" + gateListener.getTimeoutSeconds() + "s&7, Max attempts: &f" + gateListener.getMaxAttempts()));
                sender.sendMessage(GateListener.colorize("&7Players currently pending auth: &f" + pendingAuthManager.pendingCount()));
                sender.sendMessage(GateListener.colorize("&7Active IP lockouts: &f" + lockoutManager.activeLockoutCount()));
                return true;
            }
            default: {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(GateListener.colorize("&6NexusGate &7-- /nexusgate <setpassword [java|bedrock] <password>|reload|status"
                + "|ban <player> [reason]|unban <player>|banlist|knownusers>"));
    }
}
