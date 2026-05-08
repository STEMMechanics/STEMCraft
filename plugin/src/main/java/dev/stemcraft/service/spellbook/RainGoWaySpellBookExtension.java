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
import dev.stemcraft.api.service.spellbook.SpellBookService;
import dev.stemcraft.api.service.spellbook.SpellBookSource;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Clears rain and thunder in the current world.
 */
public final class RainGoWaySpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "rain go way";

    @Override
    public @NotNull String id() {
        return "rain-go-way";
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
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (spellBooks.findSpell(player, SpellBookSource.MAIN_HAND, spell) == null) {
                return;
            }

            World world = player.getWorld();
            world.setStorm(false);
            world.setThundering(false);
            if (config.getBoolean("reset-weather-duration", true)) {
                world.setWeatherDuration(0);
                world.setThunderDuration(0);
            }
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }
}
