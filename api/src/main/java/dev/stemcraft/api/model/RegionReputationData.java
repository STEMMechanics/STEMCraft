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

package dev.stemcraft.api.model;

import dev.stemcraft.api.util.MapParse;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API model for the built-in region reputation extension payload.
 */
@SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed"})
public class RegionReputationData implements ConfigurationSerializable {
    public static final String KEY = "stemcraft:reputation";
    public static final String COMMAND_KEY = "reputation";

    private String reputationKey = "";
    private double defaultValue = 0.0d;
    private double hostileThreshold = -100.0d;
    private double minimumValue = -1000.0d;
    private double maximumValue = 1000.0d;

    /**
     * Returns the storage key or faction key used by the reputation system.
     *
     * @return The reputation storage key.
     */
    public @NotNull String getReputationKey() {
        return reputationKey;
    }

    /**
     * Updates the storage key or faction key used by the reputation system.
     *
     * @param reputationKey The reputation storage key.
     */
    public void setReputationKey(@NotNull String reputationKey) {
        this.reputationKey = reputationKey;
    }

    /**
     * Returns the default reputation value used when a player has no stored entry.
     *
     * @return The default reputation value.
     */
    public double getDefaultValue() {
        return defaultValue;
    }

    /**
     * Updates the default reputation value used when a player has no stored entry.
     *
     * @param defaultValue The default reputation value.
     */
    public void setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the threshold at or below which the player is considered hostile.
     *
     * @return The hostile threshold.
     */
    public double getHostileThreshold() {
        return hostileThreshold;
    }

    /**
     * Updates the threshold at or below which the player is considered hostile.
     *
     * @param hostileThreshold The hostile threshold.
     */
    public void setHostileThreshold(double hostileThreshold) {
        this.hostileThreshold = hostileThreshold;
    }

    /**
     * Returns the minimum reputation value allowed for this region.
     *
     * @return The minimum reputation value.
     */
    public double getMinimumValue() {
        return minimumValue;
    }

    /**
     * Updates the minimum reputation value allowed for this region.
     *
     * @param minimumValue The minimum reputation value.
     */
    public void setMinimumValue(double minimumValue) {
        this.minimumValue = minimumValue;
    }

    /**
     * Returns the maximum reputation value allowed for this region.
     *
     * @return The maximum reputation value.
     */
    public double getMaximumValue() {
        return maximumValue;
    }

    /**
     * Updates the maximum reputation value allowed for this region.
     *
     * @param maximumValue The maximum reputation value.
     */
    public void setMaximumValue(double maximumValue) {
        this.maximumValue = maximumValue;
    }

    /**
     * Serializes this reputation data object for configuration storage.
     *
     * @return The serialized configuration map.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reputationKey", reputationKey);
        out.put("defaultValue", defaultValue);
        out.put("hostileThreshold", hostileThreshold);
        out.put("minimumValue", minimumValue);
        out.put("maximumValue", maximumValue);
        return out;
    }

    /**
     * Deserializes reputation data from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized reputation data.
     */
    public static @NotNull RegionReputationData deserialize(@NotNull Map<String, Object> map) {
        RegionReputationData data = new RegionReputationData();
        data.reputationKey = MapParse.string(map, "reputationKey", "regionReputation");
        data.defaultValue = valueOrDefault(MapParse.doubleValue(map, "defaultValue", "regionReputation"), 0.0d);
        data.hostileThreshold = valueOrDefault(MapParse.doubleValue(map, "hostileThreshold", "regionReputation"), -100.0d);
        data.minimumValue = valueOrDefault(MapParse.doubleValue(map, "minimumValue", "regionReputation"), -1000.0d);
        data.maximumValue = valueOrDefault(MapParse.doubleValue(map, "maximumValue", "regionReputation"), 1000.0d);
        if (data.reputationKey == null) {
            data.reputationKey = "";
        }
        return data;
    }

    private static double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }
}
