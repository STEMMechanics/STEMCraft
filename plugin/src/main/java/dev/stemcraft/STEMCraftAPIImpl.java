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

package dev.stemcraft;

import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.comet.CometService;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarService;
import dev.stemcraft.api.service.audit.AuditService;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.dialog.DialogService;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.motd.MotdService;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.playerstats.PlayerStatsService;
import dev.stemcraft.api.service.placeholder.PlaceholderService;
import dev.stemcraft.api.service.placedobject.PlacedObjectService;
import dev.stemcraft.api.service.protection.ProtectionService;
import dev.stemcraft.api.service.profanity.ProfanityFilterService;
import dev.stemcraft.api.service.recipe.RecipeService;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.gift.GiftService;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.imagemap.ImageMapService;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.selection.SelectionService;
import dev.stemcraft.api.service.save.SaveService;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.service.minigame.MiniGameService;

import java.io.File;

/**
 * Implementation of the STEMCraftAPI interface.
 */
public class STEMCraftAPIImpl extends HasMessagesImpl implements STEMCraftAPI {
    private final STEMCraft plugin;

    public STEMCraftAPIImpl(STEMCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Get the current version of STEMCraft.
     */
    @Override
    public String getVersion() {
        return STEMCraft.getVersion();
    }

    /**
     * Get the data folder for STEMCraft.
     */
    @Override
    public File getDataFolder() { return plugin.getDataFolder(); }

    /**
     * Check if the server is in maintenance mode.
     */
    @Override
    public boolean isMaintenanceMode() { return plugin.isMaintenanceMode(); }

    /**
     * Get the command service.
     */
    @Override
    public CommandService commands() {
        return plugin.commands();
    }

    @Override
    public CoordinateBarService coordinateBar() { return plugin.coordinateBar(); }

    /** Get the comet event service. */
    @Override
    public CometService comets() { return plugin.comets(); }

    /**
     * Get the STEMCraft configuration file.
     */
    @Override
    public ConfigService config() {
        return plugin.config();
    }

    /**
     * Get the audit service.
     */
    @Override
    public AuditService audit() { return plugin.audit(); }

    /**
     * Get the database service.
     */
    @Override
    public DatabaseService database() {
        return plugin.database();
    }

    /**
     * Get the cross-platform dialog service.
     */
    @Override
    public DialogService dialogs() { return plugin.dialogs(); }

    /**
     * Get the event service.
     */
    @Override
    public EventService events() { return plugin.events(); }

    /**
     * Get the hologram service.
     */
    @Override
    public HologramService holograms() { return plugin.holograms(); }

    @Override
    public GiftService gifts() { return plugin.gifts(); }

    /**
     * Get the item service.
     */
    @Override
    public ItemService items() { return plugin.items(); }

    /** Get the image-map display service. */
    @Override
    public ImageMapService imageMaps() { return plugin.imageMaps(); }

    /**
     * Get the locale service.
     */
    @Override
    public LocaleService locales() { return plugin.locales(); }

    /**
     * Get the mailbox delivery service.
     */
    @Override
    public MailboxService mailboxes() { return plugin.mailboxes(); }

    /**
     * Get the messenger service.
     */
    @Override
    public MessageService messages() {
        return plugin.messages();
    }

    /**
     * Get the minigame service.
     */
    @Override
    public MiniGameService minigames() { return plugin.minigames(); }

    /**
     * Get the MOTD service.
     */
    @Override
    public MotdService motd() { return plugin.motd(); }

    /**
     * Get the placed object service.
     */
    @Override
    public PlacedObjectService placedObjects() { return plugin.placedObjects(); }

    /**
     * Get the player log service.
     */
    @Override
    public PlayerService players() { return plugin.players(); }

    /**
     * Get the placeholder service.
     */
    @Override
    public PlaceholderService placeholders() { return plugin.placeholders(); }

    /**
     * Get the protection service.
     */
    @Override
    public ProtectionService protections() { return plugin.protections(); }

    /**
     * Get the profanity filter service.
     */
    @Override
    public ProfanityFilterService profanityFilter() { return plugin.profanityFilter(); }

    /**
     * Get the punishment service.
     */
    @Override
    public PunishmentService punishments() { return plugin.punishments(); }

    /**
     * Get the player stats service.
     */
    @Override
    public PlayerStatsService playerStats() { return plugin.playerStats(); }

    /**
     * Get the resource pack service.
     */
    @Override
    public ResourcePackService resourcePacks() { return plugin.resourcePack(); }

    /**
     * Get the recipe service.
     */
    @Override
    public RecipeService recipes() { return plugin.recipes(); }

    /**
     * Get the selection/highlight service.
     */
    @Override
    public SelectionService selections() { return plugin.selections(); }

    @Override
    public SaveService saves() { return plugin.saves(); }

    /**
     * Get the region service.
     */
    @Override
    public RegionService regions() { return plugin.regions(); }

    /**
     * Get the tab complete service.
     */
    @Override
    public TabCompleteService tabComplete() { return plugin.tabComplete(); }

    /**
     * Get the task service.
     */
    @Override
    public TaskService tasks() { return plugin.tasks(); }

    /**
     * Get the web service.
     */
    @Override
    public WebService web() { return plugin.web(); }

    /**
     * Get the world service.
     */
    @Override
    public WorldService worlds() { return plugin.worlds(); }
}
