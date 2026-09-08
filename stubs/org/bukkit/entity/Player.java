package org.bukkit.entity;

import java.net.InetSocketAddress;

public interface Player extends HumanEntity {
    InetSocketAddress getAddress();
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);
    void kickPlayer(String message);
}
