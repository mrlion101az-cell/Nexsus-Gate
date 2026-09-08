package org.bukkit.entity;

import org.bukkit.Location;

import java.util.UUID;

public interface Entity {
    UUID getUniqueId();
    Location getLocation();
    void teleport(Location location);
    void setGravity(boolean gravity);
}
