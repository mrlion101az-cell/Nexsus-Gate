package org.bukkit.event.entity;

import org.bukkit.event.Cancellable;

public class EntityDamageEvent extends EntityEvent implements Cancellable {
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
