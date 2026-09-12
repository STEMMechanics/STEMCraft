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

package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.api.STEMCraftAPI;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Class to register core tab completions.
 */
public class CoreTabCompletions {

    /**
     * Register all core tab completions.
     *
     * @param api The STEMCraft API instance.
     */
    public static void registerAll(STEMCraftAPI api) {
        registerEntityCompletion(api, "entity", type -> type != EntityType.UNKNOWN);
        registerEntityCompletion(api, "mob", CoreTabCompletions::isMob);
        registerEntityCompletion(api, "hostile", CoreTabCompletions::isHostile);
        registerEntityCompletion(api, "neutral", CoreTabCompletions::isNeutral);
        registerEntityCompletion(api, "passive", type -> isMob(type) && !isNeutral(type) && !isHostile(type));

        // Vanilla items; ItemService expands this provider with custom items.
        registerMaterialCompletion(api, "item", Material::isItem);
        registerMaterialCompletion(api, "material", material -> true);
        registerMaterialCompletion(api, "block", Material::isBlock);

        registerRegistryCompletion(api, "biome", RegistryKey.BIOME);
        registerRegistryCompletion(api, "effect", RegistryKey.MOB_EFFECT);
        registerRegistryCompletion(api, "enchantment", RegistryKey.ENCHANTMENT);
        registerRegistryCompletion(api, "sound", RegistryKey.SOUND_EVENT);
        registerRegistryCompletion(api, "particle", RegistryKey.PARTICLE_TYPE);

        api.tabComplete().register("boolean", (player, args) -> List.of("true", "false"));
        api.tabComplete().register("difficulty", (player, args) -> Arrays.stream(Difficulty.values())
                .map(difficulty -> difficulty.name().toLowerCase(Locale.ROOT)).toList());

        // Online players
        api.tabComplete().register("player", (player, args) ->
                Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player::canSee)
                        .map(Player::getName)
                        .toList()
        );

        // Common durations
        api.tabComplete().register("duration", (player, args) -> List.of(
                "1m", "2m", "5m", "10m", "15m", "30m",
                "1h", "2h", "4h",
                "1d", "1w"
        ));

        // Worlds
        api.tabComplete().register("world", (player, args) -> Bukkit.getWorlds().stream().map(World::getName).toList());

        // Game-modes
        api.tabComplete().register("gamemode", (player, args) -> List.of(
                "survival", "creative", "spectator", "adventure"
        ));

        api.tabComplete().register("int", (player, args) -> List.of(
                "1", "2", "5", "10", "15", "20", "25", "50", "100"
        ));
        // Resolve the alias dynamically so a later int override also applies to number.
        api.tabComplete().register("number", (player, args) -> api.tabComplete().getCompletionList("int", player, args));
    }

    private static void registerMaterialCompletion(STEMCraftAPI api, String name, Predicate<Material> filter) {
        List<String> names = Arrays.stream(Material.values()).filter(material -> !material.isLegacy())
                .filter(filter).map(material -> material.getKey().toString()).sorted().toList();
        api.tabComplete().register(name, (player, args) -> names);
    }

    private static <T extends Keyed> void registerRegistryCompletion(STEMCraftAPI api, String name, RegistryKey<T> key) {
        api.tabComplete().register(name, (player, args) -> RegistryAccess.registryAccess().getRegistry(key)
                .keyStream().map(Object::toString).sorted().toList());
    }

    private static void registerEntityCompletion(STEMCraftAPI api, String name, Predicate<EntityType> filter) {
        List<String> names = Arrays.stream(EntityType.values()).filter(filter).map(Enum::name).sorted().toList();
        api.tabComplete().register(name, (player, args) -> names);
    }

    private static boolean isMob(EntityType type) {
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && Mob.class.isAssignableFrom(entityClass);
    }

    // Behaviour towards players, independent of a particular mob's anger, taming, or rider.
    // Bukkit's Enemy marker also includes neutral mobs, so handle those explicitly.
    private static boolean isNeutral(EntityType type) {
        return switch (type) {
            case BEE, CAVE_SPIDER, DOLPHIN, ENDERMAN, GOAT, IRON_GOLEM, LLAMA,
                 NAUTILUS, PANDA, PIGLIN, POLAR_BEAR, SPIDER, TRADER_LLAMA, WOLF,
                 ZOMBIE_NAUTILUS, ZOMBIFIED_PIGLIN -> true;
            default -> false;
        };
    }

    private static boolean isHostile(EntityType type) {
        // Giants inherit Monster in Bukkit but have no attacking AI in vanilla Java.
        if (!isMob(type) || isNeutral(type) || type == EntityType.GIANT) return false;
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && Enemy.class.isAssignableFrom(entityClass);
    }
}
