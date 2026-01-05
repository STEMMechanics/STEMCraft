package dev.stemcraft.api.minigame;

import org.bukkit.entity.Player;

import java.util.Map;

@FunctionalInterface
public interface MiniGameHudProvider {
    Map<String,String> provide(MiniGameArena arena, Player player);
}
