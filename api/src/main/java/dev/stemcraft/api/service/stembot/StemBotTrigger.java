package dev.stemcraft.api.service.stembot;

import org.bukkit.entity.Player;

/** A registered event trigger that can request a scripted STEMBot action. */
@FunctionalInterface
public interface StemBotTrigger extends AutoCloseable {
    StemBotTrigger UNAVAILABLE = player -> false;

    /** Queue this trigger for the player. False means STEMBot cannot accept it. */
    boolean fire(Player player);

    /** Release this trigger registration. */
    @Override
    default void close() { }
}
