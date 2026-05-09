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
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Prevents lethal fall damage for players carrying the spell book.
 */
public final class NoFallDeathSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "fall no die";

    @Override
    public @NotNull String id() {
        return "no-fall-death";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(EntityDamageEvent.class, event -> {
            ConfigSection config = context.config();
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }
            if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
                return;
            }

            String spell = config.getString("spell", DEFAULT_SPELL);
            SpellBookSource source = parseSource(config.getString("source", "inventory"));
            SpellBookMatch match = spellBooks.findSpell(player, source, spell);
            if (match == null) {
                return;
            }

            double health = player.getHealth();
            if (event.getFinalDamage() < health) {
                return;
            }

            event.setDamage(Math.max(0.0d, health - 1.0d));
            SpellBookNegativeEffect.applyConfigured(player, match, config);
        }, EventPriority.HIGHEST, true);
    }

    private @NotNull SpellBookSource parseSource(@NotNull String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "main-hand" -> SpellBookSource.MAIN_HAND;
            case "off-hand" -> SpellBookSource.OFF_HAND;
            case "hands" -> SpellBookSource.HANDS;
            default -> SpellBookSource.INVENTORY;
        };
    }
}
