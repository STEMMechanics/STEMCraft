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
 * API model for the built-in region title extension payload.
 */
public class RegionTitleData implements ConfigurationSerializable {
    public static final String KEY = "stemcraft:titles";
    public static final String COMMAND_KEY = "title";

    private String enterTitle = "";
    private String enterSubtitle = "";
    private String exitTitle = "";
    private String exitSubtitle = "";

    /**
     * Returns the title shown when a player enters the region.
     *
     * @return The enter title text.
     */
    public @NotNull String getEnterTitle() {
        return enterTitle;
    }

    /**
     * Updates the title shown when a player enters the region.
     *
     * @param enterTitle The enter title text.
     */
    public void setEnterTitle(@NotNull String enterTitle) {
        this.enterTitle = enterTitle;
    }

    /**
     * Returns the subtitle shown when a player enters the region.
     *
     * @return The enter subtitle text.
     */
    public @NotNull String getEnterSubtitle() {
        return enterSubtitle;
    }

    /**
     * Updates the subtitle shown when a player enters the region.
     *
     * @param enterSubtitle The enter subtitle text.
     */
    public void setEnterSubtitle(@NotNull String enterSubtitle) {
        this.enterSubtitle = enterSubtitle;
    }

    /**
     * Returns the title shown when a player exits the region.
     *
     * @return The exit title text.
     */
    public @NotNull String getExitTitle() {
        return exitTitle;
    }

    /**
     * Updates the title shown when a player exits the region.
     *
     * @param exitTitle The exit title text.
     */
    public void setExitTitle(@NotNull String exitTitle) {
        this.exitTitle = exitTitle;
    }

    /**
     * Returns the subtitle shown when a player exits the region.
     *
     * @return The exit subtitle text.
     */
    public @NotNull String getExitSubtitle() {
        return exitSubtitle;
    }

    /**
     * Updates the subtitle shown when a player exits the region.
     *
     * @param exitSubtitle The exit subtitle text.
     */
    public void setExitSubtitle(@NotNull String exitSubtitle) {
        this.exitSubtitle = exitSubtitle;
    }

    /**
     * Serializes this title data object for configuration storage.
     *
     * @return The serialized configuration map.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enterTitle", enterTitle);
        out.put("enterSubtitle", enterSubtitle);
        out.put("exitTitle", exitTitle);
        out.put("exitSubtitle", exitSubtitle);
        return out;
    }

    /**
     * Deserializes title data from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized title data.
     */
    public static @NotNull RegionTitleData deserialize(@NotNull Map<String, Object> map) {
        RegionTitleData data = new RegionTitleData();
        data.enterTitle = MapParse.string(map, "enterTitle", "regionTitles");
        data.enterSubtitle = MapParse.string(map, "enterSubtitle", "regionTitles");
        data.exitTitle = MapParse.string(map, "exitTitle", "regionTitles");
        data.exitSubtitle = MapParse.string(map, "exitSubtitle", "regionTitles");
        if (data.enterTitle == null) data.enterTitle = "";
        if (data.enterSubtitle == null) data.enterSubtitle = "";
        if (data.exitTitle == null) data.exitTitle = "";
        if (data.exitSubtitle == null) data.exitSubtitle = "";
        return data;
    }
}
