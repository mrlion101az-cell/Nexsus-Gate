package org.bukkit.configuration;

import java.util.List;
import java.util.Set;

public interface ConfigurationSection {
    boolean getBoolean(String path, boolean def);
    int getInt(String path, int def);
    long getLong(String path, long def);
    String getString(String path, String def);
    List<String> getStringList(String path);
    List<?> getList(String path, List<?> def);
    ConfigurationSection getConfigurationSection(String path);
    Set<String> getKeys(boolean deep);
    boolean isSet(String path);
    void set(String path, Object value);
}
