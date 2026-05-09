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
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Breaks connected log blocks as a simple tree-felling spell.
 */
public final class TreeFallSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "tree fall now";
    private final Set<String> processing = new HashSet<>();

    @Override
    public @NotNull String id() {
        return "tree-fall";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(BlockBreakEvent.class, event -> {
            ConfigSection config = context.config();
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }

            Block origin = event.getBlock();
            if (!Tag.LOGS.isTagged(origin.getType())) {
                return;
            }

            String key = blockKey(origin);
            if (!processing.add(key)) {
                return;
            }

            try {
                Player player = event.getPlayer();
                String spell = config.getString("spell", DEFAULT_SPELL);
                SpellBookSource source = parseSource(config.getString("source", "inventory"));
                SpellBookMatch match = spellBooks.findSpell(player, source, spell);
                if (match == null) {
                    return;
                }

                ItemStack tool = player.getInventory().getItemInMainHand();
                if (config.getBoolean("require-axe", true) && !tool.getType().name().endsWith("_AXE")) {
                    return;
                }

                int maxBlocks = Math.max(1, config.getInt("max-blocks", 64));
                fellTree(origin, tool, maxBlocks);
                SpellBookNegativeEffect.applyConfigured(player, match, config);
            } finally {
                processing.remove(key);
            }
        }, EventPriority.MONITOR, true);
    }

    private void fellTree(@NotNull Block origin, @NotNull ItemStack tool, int maxBlocks) {
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block block = queue.removeFirst();
            String key = blockKey(block);
            if (!visited.add(key)) {
                continue;
            }
            if (!Tag.LOGS.isTagged(block.getType())) {
                continue;
            }

            if (block.getType() != Material.AIR) {
                block.breakNaturally(tool);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block relative = block.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(relative.getType())) {
                            queue.add(relative);
                        }
                    }
                }
            }
        }
    }

    private @NotNull SpellBookSource parseSource(@NotNull String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "main-hand" -> SpellBookSource.MAIN_HAND;
            case "off-hand" -> SpellBookSource.OFF_HAND;
            case "hands" -> SpellBookSource.HANDS;
            default -> SpellBookSource.INVENTORY;
        };
    }

    private @NotNull String blockKey(@NotNull Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
