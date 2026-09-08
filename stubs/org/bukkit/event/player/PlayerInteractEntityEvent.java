package org.bukkit.event.player;

import org.bukkit.event.Cancellable;

public class PlayerInteractEntityEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
