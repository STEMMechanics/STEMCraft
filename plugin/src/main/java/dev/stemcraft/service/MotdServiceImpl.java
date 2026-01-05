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
import dev.stemcraft.api.util.TextUtil;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class MotdServiceImpl extends BaseService implements MotdService {

    private final Map<String, ResolvedMotd> motdMap = new LinkedHashMap<>();
    private String defaultMotdTitle;
    private String defaultMotdText;
    private String currentMotdId;

    /**
     * Constructor for MotdServiceImpl.
     */
    public MotdServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is being enabled.
     */
    public void onEnable() {
        setDefault(
            getConfigSection().getString("title", "&6&lSTEMCraft"),
            getConfigSection().getString("text", "")
        );

        api.events().register(ServerListPingEvent.class, event -> {
            if (api.isMaintenanceMode()) {
                event.motd(TextUtil.colourise("&c&lServer is under maintenance!\n&7Please check back later."));
                return;
            }

            ResolvedMotd motd = current();
            event.motd(TextUtil.colourise(motd.motdTitle() + "\n" + motd.motdText()));
        });
    }

    /**
     * Set the default MOTD used when no other MOTDs are set.
     */
    public void setDefault(String title, String text) {
        this.defaultMotdTitle = title;
        this.defaultMotdText = text;
    }

    /**
     * Get the current MOTD
     */
    public ResolvedMotd current() {
        if (motdMap.isEmpty() || currentMotdId == null) {
            return new ResolvedMotd(defaultMotdTitle, defaultMotdText, Priority.DEFAULT);
        }

        return motdMap.get(currentMotdId);
    }

    /**
     * Get the MOTD based on namespace ID.
     */
    public ResolvedMotd get(String namespaceId) {
        return motdMap.get(namespaceId);
    }

    /**
     * Push a new MOTD with given priority. Higher priority MOTDs override lower priority ones.
     */
    public void push(String namespaceId, Priority priority, String motdTitle, String motdText) {
        motdMap.put(namespaceId, new ResolvedMotd(motdTitle, motdText, priority));
        updateCurrentMotd();
    }

    /**
     * Remove the MOTD associated with the given namespace ID.
     */
    public void remove(String namespaceId) {
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
}
