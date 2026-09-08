package org.bukkit;

import java.util.UUID;

public class SimpleOfflinePlayer implements OfflinePlayer {
    public UUID getUniqueId() { return UUID.randomUUID(); }
}
