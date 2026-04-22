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

package dev.stemcraft.minigame.bridge;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BridgeCommand {
    private static final long PREVIEW_TICKS = 100L;
    private final STEMCraftAPI api;
    private final BridgeMiniGame bridge;

    public BridgeCommand(STEMCraftAPI api, BridgeMiniGame bridge) {
        this.api = api;
        this.bridge = bridge;
    }

    public void onEnable() {
        api.tabComplete().register("bridge-arenas", (sender, args) -> bridge.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());
        api.tabComplete().register("bridge-teams", (sender, args) -> List.of("red", "blue"));

        api.commands().create("bridge")
            .permission("stemcraft.command.bridge")
            .usage("/bridge <list|info [arena]|create <arena> [world]|delete|join|joinall|spectate|leave|start|stop|restart|save|reload|validate|enable|disable|set|select|sel|show|dropitems|adddropitem|removedropitem>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info")
            .tabCompletion("info", "{bridge-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{bridge-arenas}")
            .tabCompletion("join", "{bridge-arenas}", "{player}")
            .tabCompletion("joinall", "{bridge-arenas}")
            .tabCompletion("spectate", "{bridge-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{bridge-arenas}")
            .tabCompletion("stop", "{bridge-arenas}")
            .tabCompletion("restart", "{bridge-arenas}")
            .tabCompletion("save", "{bridge-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{bridge-arenas}")
            .tabCompletion("enable", "{bridge-arenas}")
            .tabCompletion("disable", "{bridge-arenas}")
            .tabCompletion("set", "{bridge-arenas}", "lobby")
            .tabCompletion("set", "{bridge-arenas}", "spectator")
            .tabCompletion("set", "{bridge-arenas}", "bridge")
            .tabCompletion("set", "{bridge-arenas}", "arena")
            .tabCompletion("set", "{bridge-arenas}", "teamspawn", "{bridge-teams}")
            .tabCompletion("set", "{bridge-arenas}", "teamportal", "{bridge-teams}")
            .tabCompletion("set", "{bridge-arenas}", "minplayers")
            .tabCompletion("set", "{bridge-arenas}", "maxplayers")
            .tabCompletion("set", "{bridge-arenas}", "name")
            .tabCompletion("select", "{bridge-arenas}", "arena")
            .tabCompletion("select", "{bridge-arenas}", "bridge")
            .tabCompletion("select", "{bridge-arenas}", "lobby")
            .tabCompletion("select", "{bridge-arenas}", "spectator")
            .tabCompletion("select", "{bridge-arenas}", "teamspawn", "{bridge-teams}")
            .tabCompletion("select", "{bridge-arenas}", "teamportal", "{bridge-teams}")
            .tabCompletion("sel", "{bridge-arenas}", "arena")
            .tabCompletion("sel", "{bridge-arenas}", "bridge")
            .tabCompletion("sel", "{bridge-arenas}", "lobby")
            .tabCompletion("sel", "{bridge-arenas}", "spectator")
            .tabCompletion("sel", "{bridge-arenas}", "teamspawn", "{bridge-teams}")
            .tabCompletion("sel", "{bridge-arenas}", "teamportal", "{bridge-teams}")
            .tabCompletion("show", "{bridge-arenas}", "lobby")
            .tabCompletion("show", "{bridge-arenas}", "spectator")
            .tabCompletion("show", "{bridge-arenas}", "teamspawn", "{bridge-teams}")
            .tabCompletion("dropitems", "{bridge-arenas}")
            .tabCompletion("dropitems", "{bridge-arenas}", "{int}")
            .tabCompletion("adddropitem", "{bridge-arenas}")
            .tabCompletion("removedropitem", "{bridge-arenas}")
            .tabCompletion("removedropitem", "{bridge-arenas}", "{int}")
            .executor((ignored, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);

                switch (ctx.getArgLower(0)) {
                    case "list" -> commandList(ctx);
                    case "info" -> commandInfo(ctx);
                    case "create" -> commandCreate(ctx);
                    case "delete" -> commandDelete(ctx);
                    case "join" -> commandJoin(ctx);
                    case "joinall", "join-all" -> commandJoinAll(ctx);
                    case "spectate" -> commandSpectate(ctx);
                    case "leave" -> commandLeave(ctx);
                    case "start" -> commandStart(ctx);
                    case "stop" -> commandStop(ctx);
                    case "restart" -> commandRestart(ctx);
                    case "save" -> commandSave(ctx);
                    case "reload" -> commandReload(ctx);
                    case "validate" -> commandValidate(ctx);
                    case "enable" -> commandEnable(ctx);
                    case "disable" -> commandDisable(ctx);
                    case "set" -> commandSet(ctx);
                    case "select", "sel" -> commandSelect(ctx);
                    case "show" -> commandShow(ctx);
                    case "dropitems" -> commandDropItems(ctx);
                    case "adddropitem" -> commandAddDropItem(ctx);
                    case "removedropitem" -> commandRemoveDropItem(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = bridge.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            api.locales().resolve(ctx.getSender(), "BRIDGE_LIST_TITLE"),
            "bridge list",
            ctx.getArgAsInt(1, 1),
            arenas.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, arenas.size());
                for (int i = start; i < end; i++) {
                    MiniGameArena arena = arenas.get(i);
                    Component line = Component.text(arena.id(), NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                        .clickEvent(ClickEvent.runCommand("/bridge info " + arena.id()))
                        .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena))
                            .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                            .clickEvent(ClickEvent.runCommand("/bridge info " + arena.id())))
                        .append(Component.text(arena.getName(), NamedTextColor.GRAY))
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA));

                    if (isPlayer) {
                        line = line
                            .append(Component.text(" "))
                            .append(Component.text("[Info]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/bridge info " + arena.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Show arena details"))));

                        if (arena.isJoinable()) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Join]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/bridge join " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Join this arena"))));
                        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Spectate]", NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand("/bridge spectate " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Spectate this arena"))));
                        } else {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Validate]", NamedTextColor.BLUE)
                                    .clickEvent(ClickEvent.runCommand("/bridge validate " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Validate this arena"))));
                        }
                    }

                    lines.add(line);
                }
                return lines;
            },
            "BRIDGE_LIST_NONE"
        );
    }

    private NamedTextColor statusColour(MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case IDLE, WAITING -> NamedTextColor.GREEN;
            case STARTING, PREPARATION, RUNNING, COOLDOWN, ENDING, RESETTING -> NamedTextColor.GOLD;
            case SETUP -> NamedTextColor.BLUE;
            case DISABLED, SHUTDOWN -> NamedTextColor.RED;
        };
    }

    private void commandInfo(CommandContext ctx) {
        MiniGameArena arena = requireArenaForInfo(ctx);
        @SuppressWarnings("DataFlowIssue") ArenaValidationResult validation = arena.validate();
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Min players: " + arena.getMinPlayers());
        ctx.info(" - Lobby: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.get("arenaRegion", SCRegion.class)));
        ctx.info(" - Bridge region: " + formatRegion(arena.get("bridgeRegion", SCRegion.class)));
        ctx.info(" - Drop items: " + bridge.dropItems(arena).size() + " configured");
        for (String teamId : List.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }
            ctx.info(" - Team " + teamId + " spawn: " + formatLocation(team.getSpawn()));
            ctx.info(" - Team " + teamId + " portal: " + formatRegion(team.get("portalRegion", SCRegion.class)));
        }
        if (validation.hasErrors()) {
            ctx.warn(" - Validation: failed");
            for (String error : validation.getErrors()) {
                ctx.warn("   - " + error);
            }
        } else {
            ctx.success(" - Validation: ok");
        }
    }

    private void commandCreate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        String arenaId = ctx.getArg(1);
        if (bridge.minigame().arena(arenaId) != null) {
            ctx.returnError("Arena '" + arenaId + "' already exists.");
            return;
        }

        World world = ctx.getArgAsWorld(2);
        if (world == null) {
            Player player = ctx.asPlayer();
            if (player == null) {
                ctx.returnError("Specify a world when creating an arena from console.");
                return;
            }
            world = player.getWorld();
        }

        MiniGameArena arena = bridge.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created Bridge arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        bridge.deleteArena(arena.id());
        ctx.success("Deleted Bridge arena '" + arena.id() + "'.");
    }

    private void commandJoin(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx);
        Player targetPlayer = ctx.getArgAsPlayerOrSender(2);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
            return;
        }
        ensureNotInArena(ctx, targetPlayer);

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.addSpectator(targetPlayer);
            ctx.success("Player '" + targetPlayer.getName() + "' is now spectating arena '" + arena.id() + "'.");
            return;
        }

        if (!arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
            return;
        }

        arena.addPlayer(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' joined arena '" + arena.id() + "'.");
    }

    private void commandJoinAll(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx);
        boolean spectateOnly = arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING;
        if (!spectateOnly && !arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
            return;
        }

        int joined = 0;
        int spectating = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = bridge.minigame().findPlayer(targetPlayer);
            if (existingArena != null) {
                skipped++;
                continue;
            }

            if (spectateOnly) {
                arena.addSpectator(targetPlayer);
                if (arena.hasSpectator(targetPlayer)) {
                    spectating++;
                } else {
                    skipped++;
                }
                continue;
            }

            arena.addPlayer(targetPlayer);
            if (arena.hasPlayer(targetPlayer)) {
                joined++;
            } else if (arena.hasSpectator(targetPlayer)) {
                spectating++;
            } else {
                skipped++;
            }
        }

        if (joined == 0 && spectating == 0) {
            ctx.returnError("No online players could be added to arena '" + arena.id() + "'.");
            return;
        }

        ctx.success("Arena '" + arena.id() + "': joined " + joined + ", spectating " + spectating + ", skipped " + skipped + ".");
    }

    private void commandSpectate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx);
        Player targetPlayer = ctx.getArgAsPlayerOrSender(2);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
            return;
        }
        ensureNotInArena(ctx, targetPlayer);

        arena.addSpectator(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' is now spectating arena '" + arena.id() + "'.");
    }

    private void commandLeave(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
            return;
        }

        MiniGameArena arena = bridge.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Bridge arena.");
            return;
        }

        arena.removeOccupant(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' left arena '" + arena.id() + "'.");
    }

    private void commandStart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        if (arena.numPlayers() < arena.getMinPlayers()) {
            ctx.returnError("Arena '" + arena.id() + "' needs at least " + arena.getMinPlayers() + " players to start.");
        }

        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, 5);
        ctx.success("Arena '" + arena.id() + "' is starting.");
    }

    private void commandStop(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        ctx.success("Arena '" + arena.id() + "' has been stopped and reset.");
    }

    private void commandRestart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        if (arena.numPlayers() >= arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, 5);
            ctx.success("Arena '" + arena.id() + "' has been restarted.");
            return;
        }
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            bridge.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!bridge.reloadFromConfig()) {
            ctx.returnError("Bridge config could not be reloaded.");
        }
        ctx.success("Bridge config reloaded. Loaded " + bridge.minigame().arenas().size() + " arenas.");
    }

    private void commandValidate(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        ArenaValidationResult result = arena.validate();
        if (!result.hasErrors()) {
            ctx.returnSuccess("Arena '" + arena.id() + "' is valid.");
        }

        ctx.warn("Arena '" + arena.id() + "' has validation errors:");
        for (String error : result.getErrors()) {
            ctx.warn(" - " + error);
        }
    }

    private void commandEnable(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        ArenaValidationResult result = arena.validate();
        if (result.hasErrors()) {
            ctx.warn("Arena '" + arena.id() + "' cannot be enabled until it is valid:");
            for (String error : result.getErrors()) {
                ctx.warn(" - " + error);
            }
            return;
        }

        try {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            bridge.persistArenaEnabled(arena, true);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now enabled.");
    }

    private void commandDisable(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        for (Player player : new ArrayList<>(arena.getPlayers())) {
            arena.removePlayer(player);
        }
        for (Player player : new ArrayList<>(arena.getSpectators())) {
            arena.removeSpectator(player);
        }
        try {
            arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
            bridge.persistArenaEnabled(arena, false);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now disabled.");
    }

    private void commandSet(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);

        switch (target) {
            case "lobby" -> {
                Player player = requirePlayer(ctx);
                ensureArenaWorld(ctx, arena, player.getLocation(), "Lobby spawn");
                arena.setLobbySpawn(player.getLocation());
                showLocationPreview(player, "lobby", player.getLocation());
                ctx.success("Lobby spawn updated for arena '" + arena.id() + "'.");
            }
            case "spectator" -> {
                Player player = requirePlayer(ctx);
                ensureArenaWorld(ctx, arena, player.getLocation(), "Spectator spawn");
                arena.setSpectatorSpawn(player.getLocation());
                showLocationPreview(player, "spectator", player.getLocation());
                ctx.success("Spectator spawn updated for arena '" + arena.id() + "'.");
            }
            case "bridge" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection, "Bridge region");
                ensureRegionContained(ctx, selection, arena.get("arenaRegion", SCRegion.class), "Bridge region");
                arena.set("bridgeRegion", selection.copy());
                showRegionPreview(player, "set-bridge", selection);
                ctx.success("Bridge region updated for arena '" + arena.id() + "'.");
            }
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection, "Arena region");
                ensureArenaRegionAcceptsExistingGeometry(ctx, arena, selection);
                SCRegion regionCopy = selection.copy();
                arena.setRegion(regionCopy);
                arena.set("arenaRegion", regionCopy.copy());
                showRegionPreview(player, "set-arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
            }
            case "teamspawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                Player player = requirePlayer(ctx);
                MiniGameTeam team = requireTeam(ctx, arena);
                ensureArenaWorld(ctx, arena, player.getLocation(), "Team spawn");
                ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class));
                team.setSpawn(player.getLocation());
                showLocationPreview(player, "teamspawn-" + team.getName(), player.getLocation());
                ctx.success("Spawn updated for team '" + team.getName() + "' in arena '" + arena.id() + "'.");
            }
            case "teamportal" -> {
                ctx.checkArgsSizeAtLeast(4);
                Player player = requirePlayer(ctx);
                MiniGameTeam team = requireTeam(ctx, arena);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection, "Team portal");
                ensureRegionContained(ctx, selection, arena.get("arenaRegion", SCRegion.class), "Team portal");
                team.set("portalRegion", selection.copy());
                showRegionPreview(player, "teamportal-" + team.getName(), selection);
                ctx.success("Portal region updated for team '" + team.getName() + "' in arena '" + arena.id() + "'.");
            }
            case "minplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int minPlayers = ctx.getArgAsInt(3, 2, 2, null);
                arena.setMinPlayers(minPlayers);
                ctx.success("Minimum players set to " + arena.getMinPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "maxplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxPlayers = ctx.getArgAsInt(3, 16, 2, null);
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Bridge set target '" + target + "'.");
        }
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);
        SCRegion region = null;
        Location location = null;

        switch (target) {
            case "arena" -> region = arena.get("arenaRegion", SCRegion.class);
            case "bridge" -> region = arena.get("bridgeRegion", SCRegion.class);
            case "lobby" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            case "teamspawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                MiniGameTeam team = requireTeam(ctx, arena);
                location = team.getSpawn();
            }
            case "teamportal" -> {
                ctx.checkArgsSizeAtLeast(4);
                MiniGameTeam team = requireTeam(ctx, arena);
                region = team.get("portalRegion", SCRegion.class);
            }
            default -> {
                ctx.returnError("Unknown Bridge select target '" + target + "'.");
                return;
            }
        }

        if (region != null) {
            if (!player.getWorld().equals(region.getWorld())) {
                ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
            }

            api.selections().setWorldEditSelection(player, region);
            showRegionPreview(player, "select-" + target, region);
            ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
            return;
        }

        if (location == null) {
            ctx.returnError("No stored location is configured for '" + target + "' in arena '" + arena.id() + "'.");
            return;
        }
        if (!player.getWorld().equals(location.getWorld())) {
            ctx.returnError("Move to world '" + location.getWorld().getName() + "' to preview that location.");
            return;
        }

        api.selections().setWorldEditSelection(player, location);
        showLocationPreview(player, "select-" + target, location);
        ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
    }

    private void commandShow(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);
        Location location;

        switch (target) {
            case "lobby" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            case "teamspawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                MiniGameTeam team = requireTeam(ctx, arena);
                location = team.getSpawn();
            }
            default -> {
                ctx.returnError("Unknown Bridge show target '" + target + "'.");
                return;
            }
        }

        if (location == null) {
            ctx.returnError("No stored location is configured for '" + target + "' in arena '" + arena.id() + "'.");
            return;
        }
        if (!player.getWorld().equals(location.getWorld())) {
            ctx.returnError("Move to world '" + location.getWorld().getName() + "' to preview that location.");
            return;
        }

        showLocationPreview(player, "show-" + target, location);
        ctx.success("Showing stored location for '" + target + "' in arena '" + arena.id() + "'.");
    }

    private void commandDropItems(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        List<Material> dropItems = bridge.dropItems(arena);

        ChatMenuUtil.render(
            ctx.getSender(),
            "Bridge Drop Items: " + arena.id(),
            "bridge dropitems " + arena.id(),
            ctx.getArgAsInt(2, 1),
            dropItems.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, dropItems.size());
                for (int i = start; i < end; i++) {
                    Material item = dropItems.get(i);
                    int index = i + 1;
                    Component line = Component.text("#" + index + " ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(describeDropItem(item), NamedTextColor.GOLD));
                    if (isPlayer) {
                        line = line.append(Component.text(" "))
                            .append(Component.text("[Remove]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/bridge removedropitem " + arena.id() + " " + index))
                                .hoverEvent(HoverEvent.showText(Component.text("Remove this drop item"))));
                    }
                    lines.add(line);
                }
                return lines;
            },
            "No Bridge drop items are configured for that arena."
        );
    }

    private void commandAddDropItem(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        Player player = requirePlayer(ctx);
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType().isAir()) {
            ctx.returnError("Hold the item you want to add as a drop in your main hand.");
            return;
        }

        bridge.dropItems(arena).add(heldItem.getType());
        ctx.success("Added drop item '" + describeDropItem(heldItem.getType()) + "' to arena '" + arena.id() + "'.");
    }

    private void commandRemoveDropItem(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        List<Material> dropItems = bridge.dropItems(arena);
        if (dropItems.isEmpty()) {
            ctx.returnError("Arena '" + arena.id() + "' has no configured drop items.");
        }

        int index = ctx.getArgAsInt(2, 1, 1, dropItems.size()) - 1;
        Material removed = dropItems.remove(index);
        ctx.success("Removed drop item '" + describeDropItem(removed) + "' from arena '" + arena.id() + "'.");
    }

    private MiniGameArena requireArena(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(1 + 1);
        String arenaId = ctx.getArg(1);
        MiniGameArena arena = bridge.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
            throw new IllegalStateException("Arena '" + arenaId + "' does not exist.");
        }
        return arena;
    }

    private MiniGameArena requireArenaForInfo(CommandContext ctx) {
        if (ctx.numArgs() >= 2) {
            return requireArena(ctx);
        }

        List<MiniGameArena> arenas = bridge.minigame().arenas();
        if (arenas.isEmpty()) {
            ctx.returnError("No Bridge arenas are loaded.");
            throw new IllegalStateException("No Bridge arenas are loaded.");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }

        ctx.returnError("Specify an arena id. Use /bridge list to choose one.");
        throw new IllegalStateException("Specify an arena id.");
    }

    private MiniGameTeam requireTeam(CommandContext ctx, MiniGameArena arena) {
        String teamId = ctx.getArgLower(3);
        MiniGameTeam team = arena.getTeam(teamId);
        if (team == null) {
            ctx.returnError("Arena '" + arena.id() + "' does not have team '" + teamId + "'.");
            throw new IllegalStateException("Missing team '" + teamId + "'.");
        }
        return team;
    }

    private Player requirePlayer(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("This subcommand must be run in-game.");
            throw new IllegalStateException("Player is required");
        }
        return player;
    }

    private SCRegion requireSelection(CommandContext ctx, Player player) {
        SCRegion selection = api.selections().getWorldEditSelection(player);
        if (selection == null) {
            ctx.returnError("No WorldEdit selection found. Make a selection first.");
            throw new IllegalStateException("WorldEdit selection is required");
        }
        return selection;
    }

    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, Location location, String label) {
        if (location == null || location.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
            return;
        }
        if (!arena.world().equals(location.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, SCRegion region, String label) {
        if (region == null || region.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
            return;
        }
        if (!arena.world().equals(region.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    private void ensureRegionContained(CommandContext ctx, SCRegion child, SCRegion parent, String childLabel) {
        if (parent != null && !parent.contains(child)) {
            ctx.returnError(childLabel + " must be fully inside the " + "arena region" + ".");
        }
    }

    private void ensureLocationContained(CommandContext ctx, Location location, SCRegion parent) {
        if (parent != null && !parent.contains(location)) {
            ctx.returnError("Team spawn" + " must be inside the " + "arena region" + ".");
        }
    }

    private void ensureArenaRegionAcceptsExistingGeometry(CommandContext ctx, MiniGameArena arena, SCRegion arenaRegion) {
        List<String> errors = new ArrayList<>();

        SCRegion bridgeRegion = arena.get("bridgeRegion", SCRegion.class);
        if (bridgeRegion != null && !arenaRegion.contains(bridgeRegion)) {
            errors.add("existing bridge region");
        }

        for (String teamId : List.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }

            if (team.getSpawn() != null && !arenaRegion.contains(team.getSpawn())) {
                errors.add("team '" + teamId + "' spawn");
            }

            SCRegion portalRegion = team.get("portalRegion", SCRegion.class);
            if (portalRegion != null && !arenaRegion.contains(portalRegion)) {
                errors.add("team '" + teamId + "' portal region");
            }
        }

        if (!errors.isEmpty()) {
            ctx.warn("Cannot set arena region because it would exclude existing arena geometry:");
            for (String error : errors) {
                ctx.warn(" - " + error);
            }
            ctx.returnError("Expand the selection or re-set the listed items first.");
        }
    }

    private void ensureNotInArena(CommandContext ctx, Player player) {
        MiniGameArena existingArena = bridge.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "bridge-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "bridge-preview:" + player.getUniqueId() + ":" + key;
        api.selections().highlightLocation(baseId + ":location", player, location, PREVIEW_TICKS);
        api.selections().flashBlock(baseId + ":block", player, location, PREVIEW_TICKS);
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "<unset>";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private String formatRegion(SCRegion region) {
        if (region == null) {
            return "<unset>";
        }
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        return region.getWorld().getName() + " "
            + min.getBlockX() + "," + min.getBlockY() + "," + min.getBlockZ()
            + " -> "
            + max.getBlockX() + "," + max.getBlockY() + "," + max.getBlockZ();
    }

    private String describeDropItem(Material item) {
        return StringUtil.beautify(item.name());
    }
}
