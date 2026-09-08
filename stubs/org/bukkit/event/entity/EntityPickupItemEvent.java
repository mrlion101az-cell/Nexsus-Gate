package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;

public class EntityPickupItemEvent extends EntityEvent implements Cancellable {
    private boolean cancelled;
    @Override public LivingEntity getEntity() { return null; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
