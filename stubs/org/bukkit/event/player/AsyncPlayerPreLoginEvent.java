package org.bukkit.event.player;

import org.bukkit.event.Event;

import java.net.InetAddress;
import java.util.UUID;

public class AsyncPlayerPreLoginEvent extends Event {
    public enum Result { ALLOWED, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }
    public UUID getUniqueId() { return null; }
    public String getName() { return null; }
    public InetAddress getAddress() { return null; }
    public void disallow(Result result, String message) {}
}
