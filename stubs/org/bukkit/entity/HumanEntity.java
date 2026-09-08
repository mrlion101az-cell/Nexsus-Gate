package org.bukkit.entity;

import org.bukkit.command.CommandSender;

public interface HumanEntity extends LivingEntity, CommandSender {
    boolean isOp();
}
