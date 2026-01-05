/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */
package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Feature to customize player join, quit, and death messages.
 */
public class PlayerGameMessages extends BaseFeature {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random rng = new Random();

    private boolean joinEnabled;
    private boolean quitEnabled;
    private boolean deathEnabled;

    private List<String> joinList = List.of();
    private List<String> quitList = List.of();

    private List<String> deathDefault = List.of();
    private final EnumMap<EntityDamageEvent.DamageCause, List<String>> deathByCause = new EnumMap<>(EntityDamageEvent.DamageCause.class);
    private List<String> deathByEntity = List.of();

    /**
     * Constructor
     */
    public PlayerGameMessages(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        joinEnabled = getConfigSection().getBoolean("join.enabled", true);
        quitEnabled = getConfigSection().getBoolean("quit.enabled", true);
        deathEnabled = getConfigSection().getBoolean("death.enabled", true);

        joinList = readList("join.list");
        quitList = readList("quit.list");

        deathDefault = readList("death.list");
        deathByEntity = readList("death.list_entity");

        deathByCause.clear();
        ConfigSection byCause = getConfigSection().getSection("death.list_cause");
        if (byCause != null) {
            for (String key : byCause.getKeys(false)) {
                EntityDamageEvent.DamageCause cause = parseCause(key);
                if (cause == null) continue;
                List<String> list = byCause.getStringList(key);
                if (list != null && !list.isEmpty()) deathByCause.put(cause, List.copyOf(list));
            }
        }

        api.events().register(PlayerJoinEvent.class,event -> {
            if (!joinEnabled) return;
            if (!api.gatekeeper().isWhitelisted(event.getPlayer())) return;

            String raw = pick(joinList);
            if (raw == null) return;

            event.joinMessage(render("<yellow>" + raw, event.getPlayer(), null, null));
        });


        api.events().register(PlayerQuitEvent.class, event -> {
            if (!quitEnabled) return;
            if (!api.gatekeeper().isWhitelisted(event.getPlayer())) return;

            String raw = pick(quitList);
            if (raw == null) return;

            event.quitMessage(render("<yellow>" + raw, event.getPlayer(), null, null));
        });

        api.events().register(PlayerDeathEvent.class, event -> {
            if (!deathEnabled) return;

            Player p = event.getEntity();
            String killer = null;

            Player pk = p.getKiller();
            if (pk != null) killer = pk.getName();

            EntityDamageEvent last = p.getLastDamageCause();
            EntityDamageEvent.DamageCause cause = (last != null ? last.getCause() : null);

            String causeText = (cause != null ? cause.name().toLowerCase(Locale.ROOT) : "unknown");

            List<String> pool = null;

            // If entity caused damage, prefer by_entity messages
            if (last instanceof EntityDamageByEntityEvent) {
                pool = deathByEntity;
                Entity damager = ((EntityDamageByEntityEvent) last).getDamager();
                if (killer == null) killer = damager.getType().name().toLowerCase(Locale.ROOT);
            } else if (cause != null) {
                pool = deathByCause.get(cause);
            }

            if (pool == null || pool.isEmpty()) pool = deathDefault;

            String raw = pick(pool);
            if (raw == null) return;

            event.deathMessage(render("<yellow>" + raw, p, killer, causeText));
        });
    }

    private List<String> readList(String path) {
        List<String> list = getConfigSection().getStringList(path);
        if (list == null || list.isEmpty()) return null;
        return List.copyOf(list);
    }

    private String pick(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(rng.nextInt(list.size()));
    }

    private Component render(String raw, Player player, String killer, String cause) {
        String world = player.getWorld().getName();

        String s = raw
                .replace("{player}", player.getName())
                .replace("{world}", world)
                .replace("{killer}", killer == null ? "" : killer)
                .replace("{cause}", cause == null ? "" : cause);

        return mm.deserialize(s);
    }

    private EntityDamageEvent.DamageCause parseCause(String key) {
        if (key == null) return null;
        String k = key.trim().toUpperCase(Locale.ROOT);
        try {
            return EntityDamageEvent.DamageCause.valueOf(k);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

}
