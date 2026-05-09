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

package dev.stemcraft.service.spellbook;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBookExtension;
import dev.stemcraft.api.service.spellbook.SpellBookExtensionContext;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import dev.stemcraft.api.service.spellbook.SpellBookService;
import dev.stemcraft.api.service.spellbook.SpellBookSource;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Config-driven world teleports keyed by spell phrases.
 */
public final class WorldTeleportSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_ENTRY_ID = "duan";

    @Override
    public @NotNull String id() {
        return "world-teleports";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(PlayerInteractEvent.class, event -> {
            ConfigSection config = context.config();
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }
            if (event.getHand() == null || !event.getHand().name().equals("HAND")) {
                return;
            }
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            Player player = event.getPlayer();
            ConfigSection targets = config.getSection("targets", false);
            for (String targetId : targets.getKeys()) {
                ConfigSection target = targets.getSection(targetId, false);
                if (!target.getBoolean("enabled", true)) {
                    continue;
                }

                String spell = target.getString("spell", defaultSpell(targetId));
                if (spell.isBlank()) {
                    continue;
                }

                SpellBookMatch match = spellBooks.findSpell(player, SpellBookSource.MAIN_HAND, spell);
                if (match == null) {
                    continue;
                }

                String worldName = target.getString("world", targetId);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    world = api.worlds().loadWorld(worldName);
                }
                if (world == null) {
                    player.sendMessage(Component.text("The world '" + worldName + "' is not available."));
                    event.setCancelled(true);
                    return;
                }

                Location destination = resolveDestination(world, target);
                player.teleport(destination);
                event.setCancelled(true);
                SpellBookNegativeEffect.applyConfigured(player, match, config);
                return;
            }
        }, EventPriority.HIGHEST, true);
    }

    private @NotNull Location resolveDestination(@NotNull World world, @NotNull ConfigSection target) {
        Location spawn = world.getSpawnLocation();
        if (!target.contains("x") || !target.contains("y") || !target.contains("z")) {
            return spawn;
        }

        double x = target.getDouble("x", spawn.getX());
        double y = target.getDouble("y", spawn.getY());
        double z = target.getDouble("z", spawn.getZ());
        float yaw = target.getFloat("yaw", spawn.getYaw());
        float pitch = target.getFloat("pitch", spawn.getPitch());
        return new Location(world, x, y, z, yaw, pitch);
    }

    private @NotNull String defaultSpell(@NotNull String targetId) {
        if (DEFAULT_ENTRY_ID.equals(targetId)) {
            return "yo go to bo zo du";
        }
        return "go to " + targetId.replace('-', ' ');
    }
}
