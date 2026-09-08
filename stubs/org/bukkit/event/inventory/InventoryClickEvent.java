package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class InventoryClickEvent extends Event implements Cancellable {
    private boolean cancelled;
    public HumanEntity getWhoClicked() { return null; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
