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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prevents configured mobs from targeting players carrying the spell book unless provoked recently.
 */
public final class NoBiteSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "zo no bite";
    private final Map<String, Long> recentAggression = new HashMap<>();

    @Override
    public @NotNull String id() {
        return "no-bite";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(EntityDamageByEntityEvent.class, event -> {
            if (!(event.getDamager() instanceof Player player)) {
                return;
            }
            recentAggression.put(key(event.getEntity().getUniqueId(), player.getUniqueId()), System.currentTimeMillis());
        }, EventPriority.MONITOR, true);

        api.events().register(EntityTargetLivingEntityEvent.class, event -> {
            ConfigSection config = context.config();
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }
            if (!(event.getTarget() instanceof Player player)) {
                return;
            }

            Set<EntityType> blockedTypes = parseEntityTypes(config);
            if (!blockedTypes.contains(event.getEntityType())) {
                return;
            }

            String spell = config.getString("spell", DEFAULT_SPELL);
            SpellBookSource source = parseSource(config.getString("source", "inventory"));
            SpellBookMatch match = spellBooks.findSpell(player, source, spell);
            if (match == null) {
                return;
            }

            long recentWindow = Math.max(0L, config.getLong("forgive-after-seconds", 10L)) * 1000L;
            Long attackedAt = recentAggression.get(key(event.getEntity().getUniqueId(), player.getUniqueId()));
            if (attackedAt != null && recentWindow > 0L && (System.currentTimeMillis() - attackedAt) <= recentWindow) {
                return;
            }

            event.setCancelled(true);
            event.setTarget(null);
            SpellBookNegativeEffect.applyConfigured(player, match, config);
        }, EventPriority.HIGHEST, true);
    }

    private @NotNull Set<EntityType> parseEntityTypes(@NotNull ConfigSection config) {
        Set<EntityType> types = new HashSet<>();
        for (String raw : config.getStringList("mob-types")) {
            try {
                types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid entries
            }
        }

        if (types.isEmpty()) {
            types.add(EntityType.SPIDER);
            types.add(EntityType.CAVE_SPIDER);
            types.add(EntityType.WOLF);
        }

        return types;
    }

    private @NotNull SpellBookSource parseSource(@NotNull String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "main-hand" -> SpellBookSource.MAIN_HAND;
            case "off-hand" -> SpellBookSource.OFF_HAND;
            case "hands" -> SpellBookSource.HANDS;
            default -> SpellBookSource.INVENTORY;
        };
    }

    private @NotNull String key(@NotNull UUID entityId, @NotNull UUID playerId) {
        return entityId + ":" + playerId;
    }
}
