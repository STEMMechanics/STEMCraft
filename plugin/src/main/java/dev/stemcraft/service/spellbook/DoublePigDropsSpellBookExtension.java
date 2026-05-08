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
import org.bukkit.Material;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in spell that doubles pig drops when the killer carries the spell book.
 */
public final class DoublePigDropsSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "do zo ham";

    @Override
    public @NotNull String id() {
        return "pig-drops";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(EntityDeathEvent.class, event -> {
            ConfigSection config = context.config();
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true) || !(event.getEntity() instanceof Pig pig)) {
                return;
            }

            Player killer = pig.getKiller();
            String spell = config.getString("spell", DEFAULT_SPELL);
            SpellBookSource source = parseSource(config.getString("source", "inventory"));
            if (killer == null || spellBooks.findSpell(killer, source, spell) == null) {
                return;
            }

            double multiplier = Math.max(1.0d, config.getDouble("drop-multiplier", 2.0d));
            List<ItemStack> bonusDrops = new ArrayList<>(event.getDrops().size());
            for (ItemStack drop : event.getDrops()) {
                if (drop == null || drop.getType() == Material.AIR) {
                    continue;
                }
                int extraAmount = Math.max(0, (int) Math.round(drop.getAmount() * (multiplier - 1.0d)));
                if (extraAmount <= 0) {
                    continue;
                }
                ItemStack extra = drop.clone();
                extra.setAmount(extraAmount);
                bonusDrops.add(extra);
            }
            event.getDrops().addAll(bonusDrops);
        }, EventPriority.NORMAL, true);
    }

    private @NotNull SpellBookSource parseSource(@NotNull String value) {
        return switch (value.trim().toLowerCase()) {
            case "main-hand" -> SpellBookSource.MAIN_HAND;
            case "off-hand" -> SpellBookSource.OFF_HAND;
            case "hands" -> SpellBookSource.HANDS;
            default -> SpellBookSource.INVENTORY;
        };
    }
}
