package com.nexuscraft.nexusgate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public final class NexusGateCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PasswordManager passwordManager;
    private final PendingAuthManager pendingAuthManager;
    private final LockoutManager lockoutManager;
    private final GateListener gateListener;

    public NexusGateCommandExecutor(JavaPlugin plugin, PasswordManager passwordManager, PendingAuthManager pendingAuthManager,
                                     LockoutManager lockoutManager, GateListener gateListener) {
        this.plugin = plugin;
        this.passwordManager = passwordManager;
        this.pendingAuthManager = pendingAuthManager;
        this.lockoutManager = lockoutManager;
        this.gateListener = gateListener;
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
        sender.sendMessage(GateListener.colorize("&6NexusGate &7-- /nexusgate <setpassword [java|bedrock] <password>|reload|status>"));
    }
}
