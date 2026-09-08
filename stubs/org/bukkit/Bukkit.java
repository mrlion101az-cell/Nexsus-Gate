package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;

public final class Bukkit {
    private Bukkit() {}
    public static PluginManager getPluginManager() { return new PluginManager(); }
    public static BukkitScheduler getScheduler() { return new BukkitScheduler(); }
    public static Player getPlayer(UUID uuid) { return null; }
    public static OfflinePlayer getOfflinePlayer(String name) { return new SimpleOfflinePlayer(); }
}
