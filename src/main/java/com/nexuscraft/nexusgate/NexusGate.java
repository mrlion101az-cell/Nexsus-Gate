package com.nexuscraft.nexusgate;

import org.bukkit.plugin.java.JavaPlugin;

public final class NexusGate extends JavaPlugin {

    private PasswordManager passwordManager;
    private PendingAuthManager pendingAuthManager;
    private LockoutManager lockoutManager;
    private AuditLogger auditLogger;
    private GateListener gateListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.passwordManager = new PasswordManager(this);
        this.pendingAuthManager = new PendingAuthManager();
        this.lockoutManager = new LockoutManager();
        this.auditLogger = new AuditLogger(this);

        this.gateListener = new GateListener(this, passwordManager, pendingAuthManager, lockoutManager, auditLogger);
        gateListener.loadSettingsFromConfig();
        getServer().getPluginManager().registerEvents(gateListener, this);

        LoginCommandExecutor loginExecutor = new LoginCommandExecutor(this, passwordManager, pendingAuthManager, lockoutManager, auditLogger, gateListener);
        getCommand("login").setExecutor(loginExecutor);

        NexusGateCommandExecutor adminExecutor = new NexusGateCommandExecutor(this, passwordManager, pendingAuthManager, lockoutManager, gateListener);
        getCommand("nexusgate").setExecutor(adminExecutor);

        if (!passwordManager.isSet()) {
            getLogger().warning("No password is set yet! Players are joining WITHOUT the gate. Run: /nexusgate setpassword <password> (from console recommended).");
        }

        getLogger().info("NexusGate enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("NexusGate disabled.");
    }
}
