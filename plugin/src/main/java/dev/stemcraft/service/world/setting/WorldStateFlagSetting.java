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

package dev.stemcraft.service.world.setting;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SculkBloomEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Generic allow/deny/unset world flag setting used for WorldGuard-style natural event toggles.
 */
public final class WorldStateFlagSetting implements WorldBaseSetting {
    private static final Set<Material> VINE_GROWTH_MATERIALS = EnumSet.of(
        Material.VINE,
        Material.KELP,
        Material.KELP_PLANT,
        Material.CAVE_VINES,
        Material.CAVE_VINES_PLANT,
        Material.TWISTING_VINES,
        Material.TWISTING_VINES_PLANT,
        Material.WEEPING_VINES,
        Material.WEEPING_VINES_PLANT
    );
    private static final Set<Material> ROCK_GROWTH_MATERIALS = EnumSet.of(
        Material.POINTED_DRIPSTONE,
        Material.SMALL_AMETHYST_BUD,
        Material.MEDIUM_AMETHYST_BUD,
        Material.LARGE_AMETHYST_BUD,
        Material.AMETHYST_CLUSTER
    );
    private static final Set<Material> EXTRA_CROP_GROWTH_MATERIALS = EnumSet.of(
        Material.BAMBOO,
        Material.CACTUS,
        Material.COCOA,
        Material.MELON,
        Material.NETHER_WART,
        Material.PITCHER_CROP,
        Material.PITCHER_PLANT,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER_CROP
    );

    @FunctionalInterface
    public interface FlagRegistrar {
        void register(@NotNull WorldStateFlagSetting setting, @NotNull STEMCraftAPI api, @NotNull WorldService service);
    }

    private final String key;
    private final FlagRegistrar registrar;

    public WorldStateFlagSetting(@NotNull String key, @NotNull FlagRegistrar registrar) {
        this.key = key;
        this.registrar = registrar;
    }

    public static @NotNull WorldStateFlagSetting combined(@NotNull String key, @NotNull WorldStateFlagSetting... settings) {
        return new WorldStateFlagSetting(key, (setting, api, service) -> {
            for (WorldStateFlagSetting child : settings) {
                child.registrar.register(setting, api, service);
            }
        });
    }

    public static @NotNull WorldStateFlagSetting blockFade(@NotNull String key, @NotNull Predicate<BlockFadeEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockFadeEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting blockForm(@NotNull String key, @NotNull Predicate<BlockFormEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockFormEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting entityBlockForm(@NotNull String key, @NotNull Predicate<EntityBlockFormEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(EntityBlockFormEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting blockSpread(@NotNull String key, @NotNull Predicate<BlockSpreadEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockSpreadEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting blockGrow(@NotNull String key, @NotNull Predicate<BlockGrowEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockGrowEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting blockFromTo(@NotNull String key, @NotNull Predicate<BlockFromToEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockFromToEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting blockIgnite(@NotNull String key, @NotNull Predicate<BlockIgniteEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(BlockIgniteEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting leavesDecay(@NotNull String key) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(LeavesDecayEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting lightning(@NotNull String key, @NotNull Predicate<LightningStrikeEvent> predicate) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(LightningStrikeEvent.class, event -> {
                if (setting.isDenied(event.getWorld(), service.getConfigSection(event.getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull WorldStateFlagSetting sculkBloom(@NotNull String key) {
        return new WorldStateFlagSetting(key, (setting, api, service) ->
            api.events().register(SculkBloomEvent.class, event -> {
                if (setting.isDenied(event.getBlock().getWorld(), service.getConfigSection(event.getBlock().getWorld()))) {
                    event.setCancelled(true);
                }
            })
        );
    }

    public static @NotNull FlagRegistrar structureGrow(@NotNull Predicate<StructureGrowEvent> predicate) {
        return (setting, api, service) ->
            api.events().register(StructureGrowEvent.class, event -> {
                if (setting.isDenied(event.getWorld(), service.getConfigSection(event.getWorld()))
                    && predicate.test(event)) {
                    event.setCancelled(true);
                }
            });
    }

    public static @NotNull Predicate<BlockFormEvent> formsMaterial(@NotNull Material material) {
        return event -> !(event instanceof EntityBlockFormEvent) && event.getNewState().getType() == material;
    }

    public static @NotNull Predicate<BlockFormEvent> copperWeathering() {
        return event -> isCopperWeathering(event.getBlock().getType(), event.getNewState().getType());
    }

    public static @NotNull Predicate<BlockFadeEvent> fadesMaterial(@NotNull Material material) {
        return event -> event.getBlock().getType() == material;
    }

    public static @NotNull Predicate<BlockFadeEvent> fadesTagged(@NotNull Tag<Material> tag) {
        return event -> tag.isTagged(event.getBlock().getType());
    }

    public static @NotNull Predicate<BlockFadeEvent> fadesLiveCoral() {
        return event -> isLiveCoral(event.getBlock().getType());
    }

    public static @NotNull Predicate<BlockSpreadEvent> spreadsMaterial(@NotNull Material material) {
        return event -> event.getNewState().getType() == material;
    }

    public static @NotNull Predicate<BlockSpreadEvent> spreadsMushroom() {
        return event -> event.getNewState().getType() == Material.BROWN_MUSHROOM
            || event.getNewState().getType() == Material.RED_MUSHROOM;
    }

    public static @NotNull Predicate<BlockSpreadEvent> spreadsVineLike() {
        return event -> VINE_GROWTH_MATERIALS.contains(event.getNewState().getType());
    }

    public static @NotNull Predicate<BlockGrowEvent> growsCropLike() {
        return event -> isCropGrowthMaterial(event.getNewState().getType());
    }

    public static @NotNull Predicate<BlockGrowEvent> growsVineLike() {
        return event -> VINE_GROWTH_MATERIALS.contains(event.getNewState().getType());
    }

    public static @NotNull Predicate<BlockGrowEvent> growsRockLike() {
        return event -> ROCK_GROWTH_MATERIALS.contains(event.getNewState().getType());
    }

    public static @NotNull Predicate<StructureGrowEvent> growsHugeMushroom() {
        return event -> event.getSpecies() == TreeType.BROWN_MUSHROOM || event.getSpecies() == TreeType.RED_MUSHROOM;
    }

    @Override
    public @NotNull String key() {
        return key;
    }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        registrar.register(this, api, service);
    }

    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
            new String[]{"unset"},
            new String[]{"allow"},
            new String[]{"deny"}
        );
    }

    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            ctx.returnInfo("WORLD_SETTING_STATE_FLAG_STATUS", "setting", key(), "world", world.getName(), "value", get(world, config));
            return;
        }

        String normalizedValue = normalizeStateValue(value);
        if (normalizedValue == null) {
            ctx.returnError("WORLD_SETTING_STATE_FLAG_INVALID", "setting", key(), "value", value);
            return;
        }

        set(world, config, normalizedValue);
        if (normalizedValue.equals("unset")) {
            ctx.returnSuccess("WORLD_SETTING_STATE_FLAG_RESET", "setting", key(), "world", world.getName());
        } else {
            ctx.returnSuccess("WORLD_SETTING_STATE_FLAG_SET", "setting", key(), "world", world.getName(), "value", normalizedValue);
        }
    }

    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        if (!config.contains(key())) {
            return "unset";
        }

        Object rawValue = config.get(key());
        if (rawValue instanceof Boolean boolValue) {
            return boolValue ? "allow" : "deny";
        }

        String normalized = String.valueOf(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "allow", "true" -> "allow";
            case "deny", "false" -> "deny";
            default -> "unset";
        };
    }

    @Override
    public void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value) {
        String normalized = normalizeStateValue(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported state flag value: " + value);
        }

        if (normalized.equals("unset")) {
            config.set(key(), null);
        } else {
            config.set(key(), normalized);
        }
        config.save();
    }

    private boolean isDenied(@NotNull World world, @NotNull ConfigSection config) {
        return get(world, config).equals("deny");
    }

    private static String normalizeStateValue(@NotNull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "allow", "true" -> "allow";
            case "deny", "false" -> "deny";
            case "unset" -> "unset";
            default -> null;
        };
    }

    private static boolean isCropGrowthMaterial(@NotNull Material material) {
        return Tag.CROPS.isTagged(material) || EXTRA_CROP_GROWTH_MATERIALS.contains(material);
    }

    private static boolean isLiveCoral(@NotNull Material material) {
        return Tag.CORAL_BLOCKS.isTagged(material)
            || Tag.CORALS.isTagged(material)
            || Tag.WALL_CORALS.isTagged(material)
            || Tag.CORAL_PLANTS.isTagged(material);
    }

    private static boolean isCopperWeathering(@NotNull Material oldType, @NotNull Material newType) {
        int oldStage = copperWeatheringStage(oldType);
        int newStage = copperWeatheringStage(newType);
        return oldStage >= 0
            && newStage > oldStage
            && normalizedCopperName(oldType).equals(normalizedCopperName(newType));
    }

    private static int copperWeatheringStage(@NotNull Material material) {
        String name = material.name();
        if (name.startsWith("WAXED_") || !name.contains("COPPER")) {
            return -1;
        }
        if (name.startsWith("OXIDIZED_")) {
            return 3;
        }
        if (name.startsWith("WEATHERED_")) {
            return 2;
        }
        if (name.startsWith("EXPOSED_")) {
            return 1;
        }
        return 0;
    }

    private static @NotNull String normalizedCopperName(@NotNull Material material) {
        String name = material.name();
        for (String prefix : List.of("WAXED_", "OXIDIZED_", "WEATHERED_", "EXPOSED_")) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }
}
