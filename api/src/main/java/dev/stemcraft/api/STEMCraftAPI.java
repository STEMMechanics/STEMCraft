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

package dev.stemcraft.api;

import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.comet.CometService;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarService;
import dev.stemcraft.api.service.audit.AuditService;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.dialog.DialogService;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.gift.GiftService;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.imagemap.ImageMapService;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.minigame.MiniGameService;
import dev.stemcraft.api.service.motd.MotdService;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.playerstats.PlayerStatsService;
import dev.stemcraft.api.service.playerreset.PlayerResetService;
import dev.stemcraft.api.service.placeholder.PlaceholderService;
import dev.stemcraft.api.service.placedobject.PlacedObjectService;
import dev.stemcraft.api.service.protection.ProtectionService;
import dev.stemcraft.api.service.profanity.ProfanityFilterService;
import dev.stemcraft.api.service.recipe.RecipeService;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.selection.SelectionService;
import dev.stemcraft.api.service.save.SaveService;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.world.WorldService;

import java.io.File;

public interface STEMCraftAPI {

    /**
     * Get the current version of STEMCraft.
     */
    String getVersion();

    /**
     * Get the data folder for STEMCraft.
     */
    File getDataFolder();

    /**
     * Check if the server is in maintenance mode.
     */
    boolean isMaintenanceMode();

    /**
     * Register a new command.
     */
    CommandService commands();

    /** Get the coordinate boss-bar extension service. */
    CoordinateBarService coordinateBar();

    /** Get the comet event service. */
    CometService comets();

    /**
     * Get the STEMCraft configuration file.
     */
    ConfigService config();

    /**
     * Get the audit service.
     */
    AuditService audit();

    /**
     * Get the database service.
     */
    DatabaseService database();

    /**
     * Get the cross-platform dialog service.
     */
    DialogService dialogs();

    /**
     * Get the event service.
     */
    EventService events();

    /**
     * Get the hologram service.
     */
    HologramService holograms();

    /** Get the portable gift service. */
    GiftService gifts();

    /**
     * Get the item service.
     */
    ItemService items();

    /** Get the image-map display service. */
    ImageMapService imageMaps();

    /**
     * Get the locale service.
     */
    LocaleService locales();

    /**
     * Get the mailbox delivery service.
     */
    MailboxService mailboxes();

    /**
     * Get the messenger service.
     */
    MessageService messages();

    /**
     * Get the minigame service.
     */
    MiniGameService minigames();

    /**
     * Get the MOTD service.
     */
    MotdService motd();

    /**
     * Get the placed object service.
     */
    PlacedObjectService placedObjects();

    /**
     * Get the player log service.
     */
    PlayerService players();

    /**
     * Get the placeholder service.
     */
    PlaceholderService placeholders();

    /**
     * Get the protection service.
     */
    ProtectionService protections();

    /**
     * Get the profanity filter service.
     */
    ProfanityFilterService profanityFilter();

    /**
     * Get the punishment service.
     */
    PunishmentService punishments();

    /**
     * Get the player stats service.
     */
    PlayerStatsService playerStats();

    /** Coordinates safe, previewed resets of player-owned data. */
    PlayerResetService playerResets();

    /**
     * Get the resource pack service.
     */
    ResourcePackService resourcePacks();

    /**
     * Get the recipe service.
     */
    RecipeService recipes();

    /**
     * Get the selection/highlight service.
     */
    SelectionService selections();

    /** Get the STEMCraft and extension save coordinator. */
    SaveService saves();

    /**
     * Get the region service.
     */
    RegionService regions();

    /**
     * Get the tab complete service.
     */
    TabCompleteService tabComplete();

    /**
     * Get the task service.
     */
    TaskService tasks();

    /**
     * Get the web service.
     */
    WebService web();

    /**
     * Get the world service.
     */
    WorldService worlds();

    /**
     * Static access to the STEMCraft API instance.
     *
     * <p>The instance becomes available after STEMCraft has enabled. Plugins should
     * declare STEMCraft as a dependency or soft dependency before accessing it.</p>
     *
     * @return the active API instance, or {@code null} before STEMCraft enables
     * @example Getting the API
     * {@code
     * STEMCraftAPI api = STEMCraftAPI.api();
     * }
     */
    static STEMCraftAPI api() {
        return InstanceHolder.api();
    }
}
