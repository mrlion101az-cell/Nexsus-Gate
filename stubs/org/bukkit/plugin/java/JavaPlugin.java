package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.logging.Logger;

public abstract class JavaPlugin implements Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public ConfigurationSection getConfig() { return null; }
    public File getDataFolder() { return null; }
    public Logger getLogger() { return Logger.getLogger("stub"); }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
}
