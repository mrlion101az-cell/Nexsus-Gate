package org.bukkit.event.player;

import org.bukkit.event.Cancellable;

public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;
    public String getMessage() { return ""; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
