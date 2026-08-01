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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.motd.MotdService;
import dev.stemcraft.api.util.FontUtil;
import dev.stemcraft.api.util.TextUtil;
import org.bukkit.event.server.ServerListPingEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of the MotdService for managing the server's Message of the Day (MOTD).
 */
public class MotdServiceImpl extends BaseService implements MotdService {
    private static final String DEFAULT_TITLE = "<gold><bold>STEMCraft</bold></gold>";
    private static final String DEFAULT_TEXT = "";
    private static final int CENTERED_MOTD_LINE_WIDTH = 266;
    private static final int SPACE_WIDTH = 4;

    private final Map<String, ResolvedMotd> motdMap = new LinkedHashMap<>();
    private String defaultMotdTitle;
    private String defaultMotdText;
    private boolean defaultTitleCentered;
    private boolean defaultTextCentered;
    private String currentMotdId;

    /**
     * Constructor for MotdServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public MotdServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "motd");
    }

    /**
     * Called when the service is being enabled.
     */
    public void onEnable() {
        loadDefaultFromConfig();

        api.events().register(ServerListPingEvent.class, event -> {
            if (api.isMaintenanceMode()) {
                applyLegacyMotd(event,
                    "<red><bold>Server is under maintenance!</bold>",
                    "<gray>Please check back later.</gray>"
                );
                return;
            }

            ResolvedMotd motd = current();
            boolean centered = currentMotdId == null;
            applyLegacyMotd(event,
                renderLine(motd.motdTitle(), centered && defaultTitleCentered),
                renderLine(motd.motdText(), centered && defaultTextCentered));
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        getRootConfigSection().reload();
        loadDefaultFromConfig();
    }

    /**
     * Set the default MOTD used when no other MOTDs are set.
     *
     * @param title The default MOTD title.
     * @param text The default MOTD text.
     */
    public void setDefault(@NotNull String title, @NotNull String text) {
        this.defaultMotdTitle = title;
        this.defaultMotdText = text;
    }

    public @NotNull ResolvedMotd defaultMotd() {
        return new ResolvedMotd(defaultMotdTitle, defaultMotdText, Priority.DEFAULT);
    }

    public boolean isDefaultTitleCentered() {
        return defaultTitleCentered;
    }

    public boolean isDefaultTextCentered() {
        return defaultTextCentered;
    }

    public void updateDefaultTitle(@NotNull String title, boolean centered) {
        setDefault(title, defaultMotdText);
        defaultTitleCentered = centered;
        getConfigSection().set("title", title);
        getConfigSection().set("center-title", centered);
        saveConfig();
    }

    public void updateDefaultText(@NotNull String text, boolean centered) {
        setDefault(defaultMotdTitle, text);
        defaultTextCentered = centered;
        getConfigSection().set("text", text);
        getConfigSection().set("center-text", centered);
        saveConfig();
    }

    /**
     * Get the current MOTD.
     *
     * @return The current MOTD.
     */
    public @NotNull ResolvedMotd current() {
        if (motdMap.isEmpty() || currentMotdId == null) {
            return new ResolvedMotd(defaultMotdTitle, defaultMotdText, Priority.DEFAULT);
        }

        return motdMap.get(currentMotdId);
    }

    /**
     * Get the MOTD based on namespace ID.
     *
     * @param namespaceId The namespace ID of the MOTD.
     * @return The MOTD associated with the namespace ID, or null if not found.
     */
    public @Nullable ResolvedMotd get(@NonNull String namespaceId) {
        return motdMap.get(namespaceId);
    }

    /**
     * Push a new MOTD with given priority. Higher priority MOTDs override lower priority ones.
     *
     * @param namespaceId The namespace ID for the MOTD.
     * @param priority The priority of the MOTD.
     * @param motdTitle The MOTD title.
     * @param motdText The MOTD text.
     */
    public void push(@NotNull String namespaceId, @NotNull Priority priority, @NotNull String motdTitle, @NotNull String motdText) {
        motdMap.put(namespaceId, new ResolvedMotd(motdTitle, motdText, priority));
        updateCurrentMotd();
    }

    /**
     * Remove the MOTD associated with the given namespace ID.
     *
     * @param namespaceId The namespace ID of the MOTD to remove.
     */
    public void remove(@NotNull String namespaceId) {
        motdMap.remove(namespaceId);
        updateCurrentMotd();
    }

    /**
     * Update the current MOTD based on highest priority.
     */
    private void updateCurrentMotd() {
        String highestPriorityId = null;
        Priority highestPriority = Priority.DEFAULT;

        for (Map.Entry<String, ResolvedMotd> entry : motdMap.entrySet()) {
            Priority priority = entry.getValue().priority();
            if (priority.ordinal() > highestPriority.ordinal()) {
                highestPriority = priority;
                highestPriorityId = entry.getKey();
            }
        }

        this.currentMotdId = highestPriorityId;
    }

    private void loadDefaultFromConfig() {
        setDefault(
            getConfigSection().getString("title", DEFAULT_TITLE),
            getConfigSection().getString("text", DEFAULT_TEXT)
        );
        defaultTitleCentered = getConfigSection().getBoolean("center-title", false);
        defaultTextCentered = getConfigSection().getBoolean("center-text", false);
    }

    static @NotNull String formatLegacyMotd(@NotNull String title, @NotNull String text) {
        return TextUtil.colouriseToSection(title) + "\n" + TextUtil.colouriseToSection(text);
    }

    static @NotNull String renderLine(@NotNull String text, boolean centered) {
        if (!centered) {
            return text;
        }

        int width = FontUtil.calculatePixelWidth(TextUtil.colouriseToSection(text));
        if (width >= CENTERED_MOTD_LINE_WIDTH) {
            return text;
        }

        int paddingWidth = (CENTERED_MOTD_LINE_WIDTH - width) / 2;
        int spaces = Math.max(0, paddingWidth / SPACE_WIDTH);
        return " ".repeat(spaces) + text;
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyMotd(@NotNull ServerListPingEvent event, @NotNull String title, @NotNull String text) {
        event.setMotd(formatLegacyMotd(title, text));
    }
}
