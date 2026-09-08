package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;

public class PlayerMoveEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;
    public Location getFrom() { return null; }
    public Location getTo() { return null; }
    public void setTo(Location location) {}
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
