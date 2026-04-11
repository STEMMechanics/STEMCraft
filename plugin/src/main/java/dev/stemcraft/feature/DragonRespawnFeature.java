/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
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

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.Locale;

public class DragonRespawnFeature extends BaseFeature {
    private static final long TICKS_PER_DAY = 1_728_000L;
    private static final String PERSISTENT_TYPE = "DRAGON_RESPAWN";
    private static final String TIMER_ID_PREFIX = "dragon-respawn:";

    public DragonRespawnFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        if (!getConfigSection().getBoolean("enabled", false)) {
            return;
        }

        api.tasks().registerPersistentCallback(PERSISTENT_TYPE, (type, id, data) -> {
            String worldName = data == null ? "" : data.trim();
            if (worldName.isEmpty()) {
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                world = api.worlds().loadWorld(worldName);
            }
            if (world == null || !isWorldEligible(world)) {
                return;
            }

            respawnDragonIfNeeded(world);
        });

        api.events().register(EntityDeathEvent.class, event -> {
            if (!(event.getEntity() instanceof EnderDragon dragon)) {
                return;
            }

            World world = dragon.getWorld();
            if (!isWorldEligible(world)) {
                return;
            }

            String id = TIMER_ID_PREFIX + world.getUID();
            api.tasks().runLaterPersistent(PERSISTENT_TYPE, id, world.getName(), respawnDelayTicks());
        });
    }

    private long respawnDelayTicks() {
        long days = Math.max(1L, getConfigSection().getLong("days", 3L));
        return Math.max(1L, days * TICKS_PER_DAY);
    }

    private boolean isWorldEligible(World world) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }

        List<String> configuredWorlds = getConfigSection().getStringList("worlds");
        if (configuredWorlds.isEmpty()) {
            return true;
        }

        String worldName = world.getName().trim().toLowerCase(Locale.ROOT);
        for (String configured : configuredWorlds) {
            if (configured != null && worldName.equals(configured.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void respawnDragonIfNeeded(World world) {
        if (!world.getEntitiesByClass(EnderDragon.class).isEmpty()) {
            return;
        }

        var battle = world.getEnderDragonBattle();
        if (battle == null) {
            return;
        }

        try {
            battle.initiateRespawn();
        } catch (IllegalStateException ignored) {
            // Requires valid respawn setup in The End.
        }
    }
}
