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

package dev.stemcraft.feature.portal;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.*;
import java.util.*;

/** Small declarative portal schema. Runtime state lives separately from these definitions. */
public record SurvivalPortalType(NamespacedKey key, NamespacedKey generator, PortalPattern pattern,
        Activation activation, String item, Material chargeBlock, Material lightningBlock,
        Set<World.Environment> environments, Set<String> worlds, String destination, boolean createWorld,
        String deactivateItem, Particle particle, Sound activateSound, Sound deactivateSound, int cooldownTicks, Map<Material, Material> activeFrame, double coordinateScale, int searchRadius, int linkRadius, int lightLevel, int warmupTicks, boolean screenDistortion, ExitPlacement exitPlacement) {
    public enum ExitPlacement { GROUND, AIR_DROP }
    public enum Activation { INTERACT, CHARGE_BLOCKS, LIGHTNING, THROW_ITEM }
    public SurvivalPortalType {
        if (exitPlacement == ExitPlacement.AIR_DROP && (pattern.interiorMaterial() != Material.AIR
                || pattern.interior().stream().mapToInt(PortalPattern.Offset::y).distinct().count() != 1))
            throw new IllegalArgumentException("Air-drop exits require a horizontal air opening");
        if (warmupTicks < 0 || warmupTicks > 200) throw new IllegalArgumentException("Portal warmup must be 0-200 ticks");
        if (lightLevel < 0 || lightLevel > 15) throw new IllegalArgumentException("Portal light must be 0-15");
        if (!Double.isFinite(coordinateScale) || coordinateScale < .125 || coordinateScale > 128)
            throw new IllegalArgumentException("Coordinate scale must be between 0.125 and 128");
        if (searchRadius < 0 || searchRadius > 64 || linkRadius < 0 || linkRadius > searchRadius)
            throw new IllegalArgumentException("Portal search radius must be 0-64, with link radius no larger than search radius");
        activeFrame = Map.copyOf(activeFrame);
        for (var entry : activeFrame.entrySet()) {
            if (!entry.getKey().isSolid() || !entry.getValue().isSolid())
                throw new IllegalArgumentException("Active frame replacements must be solid blocks");
        }
        environments = Set.copyOf(environments); worlds = Set.copyOf(worlds);
        if (!destination.matches("[a-zA-Z0-9_{}-]+") || !destination.replace("{world}", "world").matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Destination must be a safe world name, optionally containing {world}");
        if (activation == Activation.CHARGE_BLOCKS && pattern.cells().stream().noneMatch(c -> c.material() == chargeBlock))
            throw new IllegalArgumentException("Charge material is absent from the frame");
        if (activation == Activation.LIGHTNING && pattern.cells().stream().noneMatch(c -> c.material() == lightningBlock))
            throw new IllegalArgumentException("Lightning anchor is absent from the frame");
        if (cooldownTicks < 20 || cooldownTicks > 1200) throw new IllegalArgumentException("Cooldown must be 20-1200 ticks");
        if (particle.getDataType() != Void.class) throw new IllegalArgumentException("Particle must not require extra data");
    }
    public boolean allows(World world) {
        return environments.contains(world.getEnvironment()) && (worlds.contains("*") || worlds.contains(world.getName()));
    }
    public String destinationName(String source) { return destination.replace("{world}", source); }
    private static Sound sound(String id) {
        NamespacedKey key = NamespacedKey.fromString(id);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) throw new IllegalArgumentException("Unknown sound: " + id);
        return sound;
    }
    public static SurvivalPortalType read(String id, ConfigSection config) {
        NamespacedKey key = Objects.requireNonNull(NamespacedKey.fromString(id.contains(":") ? id : "stemcraft:" + id));
        NamespacedKey generator = Objects.requireNonNull(NamespacedKey.fromString(config.getString("generator")));
        PortalPattern pattern = PortalPattern.parse(config.getStringList("frame"), config.getStringList("interior"),
                Material.valueOf(config.getString("interior-material", "AIR")));
        Set<World.Environment> environments = new HashSet<>();
        for (String env : config.getStringList("source-environments")) environments.add(World.Environment.valueOf(env));
        Map<Material, Material> activeFrame = new EnumMap<>(Material.class);
        ConfigSection replacements = config.getSection("effects.active-frame", false);
        if (replacements != null) for (String material : replacements.getKeys(false))
            activeFrame.put(Material.valueOf(material), Material.valueOf(replacements.getString(material)));
        return new SurvivalPortalType(key, generator, pattern,
                Activation.valueOf(config.getString("activation.type", "INTERACT")),
                config.getString("activation.item", ""),
                Material.valueOf(config.getString("activation.charge-block", "PURPUR_BLOCK")),
                Material.valueOf(config.getString("activation.lightning-block", "LIGHTNING_ROD")),
                environments, new HashSet<>(config.getStringList("source-worlds")),
                config.getString("destination.world", "{world}_" + key.getKey()),
                config.getBoolean("destination.create", true), config.getString("deactivation.item", "SHEARS"),
                Particle.valueOf(config.getString("effects.particle", "ENCHANT")),
                sound(config.getString("effects.activate-sound", "minecraft:block.beacon.activate")),
                sound(config.getString("effects.deactivate-sound", "minecraft:block.beacon.deactivate")),
                config.getInt("cooldown-ticks", 100), activeFrame, config.getDouble("destination.coordinate-scale", 8),
                config.getInt("destination.search-radius", 32), config.getInt("destination.link-radius", 16), config.getInt("effects.light-level", 14), config.getInt("effects.warmup-ticks", 60),
                config.getBoolean("effects.screen-distortion", true),
                ExitPlacement.valueOf(config.getString("destination.placement", "GROUND")));
    }
}
