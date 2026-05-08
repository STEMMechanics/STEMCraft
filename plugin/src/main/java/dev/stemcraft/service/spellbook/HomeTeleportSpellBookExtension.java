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
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Teleports the player to their respawn location or world spawn.
 */
public final class HomeTeleportSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "zo me home";

    @Override
    public @NotNull String id() {
        return "home-teleport";
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

            String spell = config.getString("spell", DEFAULT_SPELL);
            Player player = event.getPlayer();
            if (spellBooks.findSpell(player, SpellBookSource.MAIN_HAND, spell) == null) {
                return;
            }

            Location destination = player.getRespawnLocation();
            if (destination == null && config.getBoolean("fallback-to-world-spawn", true)) {
                destination = player.getWorld().getSpawnLocation();
            }
            if (destination == null) {
                player.sendMessage(Component.text("No home location is available."));
                return;
            }

            player.teleport(destination);
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }
}
