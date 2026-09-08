package org.bukkit.configuration.file;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class YamlConfiguration implements ConfigurationSection {

    public static YamlConfiguration loadConfiguration(File file) {
        return new YamlConfiguration();
    }

    public void save(File file) throws IOException {
    }

    @Override public boolean getBoolean(String path, boolean def) { return def; }
    @Override public int getInt(String path, int def) { return def; }
    @Override public long getLong(String path, long def) { return def; }
    @Override public String getString(String path, String def) { return def; }
    @Override public List<String> getStringList(String path) { return new ArrayList<>(); }
    @Override public List<?> getList(String path, List<?> def) { return def; }
    @Override public List<Map<?, ?>> getMapList(String path) { return new ArrayList<>(); }
    @Override public ConfigurationSection getConfigurationSection(String path) { return null; }
    @Override public Set<String> getKeys(boolean deep) { return new HashSet<>(); }
    @Override public boolean isSet(String path) { return false; }
    @Override public void set(String path, Object value) { }
}
