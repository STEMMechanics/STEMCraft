package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BoatRaceCommand {
    private static final long PREVIEW_TICKS = 100L;

    private final STEMCraftAPI api;
    private final BoatRaceMiniGame boatRace;

    public BoatRaceCommand(STEMCraftAPI api, BoatRaceMiniGame boatRace) {
        this.api = api;
        this.boatRace = boatRace;
    }

    public void onEnable() {
        api.tabComplete().register("boatrace-arenas", (sender, args) -> boatRace.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());

        api.commands().create("boatrace")
            .permission("stemcraft.command.boatrace")
            .usage("/boatrace <list|info|create|delete|join|joinall|spectate|leave|start|stop|restart|save|reload|validate|enable|disable|set|addgrid|setgrid|removegrid|addcheckpoint|setcheckpoint|removecheckpoint|addstage|setstage|removestage|select|sel|show>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{boatrace-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{boatrace-arenas}")
            .tabCompletion("join", "{boatrace-arenas}", "{player}")
            .tabCompletion("joinall", "{boatrace-arenas}")
            .tabCompletion("spectate", "{boatrace-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{boatrace-arenas}")
            .tabCompletion("stop", "{boatrace-arenas}")
            .tabCompletion("restart", "{boatrace-arenas}")
            .tabCompletion("save", "{boatrace-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{boatrace-arenas}")
            .tabCompletion("enable", "{boatrace-arenas}")
            .tabCompletion("disable", "{boatrace-arenas}")
            .tabCompletion("set", "{boatrace-arenas}", "lobby")
            .tabCompletion("set", "{boatrace-arenas}", "spectator")
            .tabCompletion("set", "{boatrace-arenas}", "arena")
            .tabCompletion("set", "{boatrace-arenas}", "finish")
            .tabCompletion("set", "{boatrace-arenas}", "minplayers")
            .tabCompletion("set", "{boatrace-arenas}", "maxplayers")
            .tabCompletion("set", "{boatrace-arenas}", "name")
            .tabCompletion("addgrid", "{boatrace-arenas}")
            .tabCompletion("setgrid", "{boatrace-arenas}", "{int}")
            .tabCompletion("removegrid", "{boatrace-arenas}", "{int}")
            .tabCompletion("addstage", "{boatrace-arenas}")
            .tabCompletion("addcheckpoint", "{boatrace-arenas}")
            .tabCompletion("setstage", "{boatrace-arenas}", "{int}")
            .tabCompletion("setcheckpoint", "{boatrace-arenas}", "{int}")
            .tabCompletion("removestage", "{boatrace-arenas}", "{int}")
            .tabCompletion("removecheckpoint", "{boatrace-arenas}", "{int}")
            .tabCompletion("select", "{boatrace-arenas}", "arena")
            .tabCompletion("select", "{boatrace-arenas}", "finish")
            .tabCompletion("select", "{boatrace-arenas}", "stage", "{int}")
            .tabCompletion("select", "{boatrace-arenas}", "checkpoint", "{int}")
            .tabCompletion("select", "{boatrace-arenas}", "grid", "{int}")
            .tabCompletion("select", "{boatrace-arenas}", "lobby")
            .tabCompletion("select", "{boatrace-arenas}", "spectator")
            .tabCompletion("sel", "{boatrace-arenas}", "arena")
            .tabCompletion("sel", "{boatrace-arenas}", "finish")
            .tabCompletion("sel", "{boatrace-arenas}", "stage", "{int}")
            .tabCompletion("sel", "{boatrace-arenas}", "checkpoint", "{int}")
            .tabCompletion("sel", "{boatrace-arenas}", "grid", "{int}")
            .tabCompletion("sel", "{boatrace-arenas}", "lobby")
            .tabCompletion("sel", "{boatrace-arenas}", "spectator")
            .tabCompletion("show", "{boatrace-arenas}", "lobby")
            .tabCompletion("show", "{boatrace-arenas}", "spectator")
            .tabCompletion("show", "{boatrace-arenas}", "grid", "{int}")
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
                    case "addgrid" -> commandAddGrid(ctx);
                    case "setgrid" -> commandSetGrid(ctx);
                    case "removegrid" -> commandRemoveGrid(ctx);
                    case "addstage", "addcheckpoint" -> commandAddStage(ctx);
                    case "setstage", "setcheckpoint" -> commandSetStage(ctx);
                    case "removestage", "removecheckpoint" -> commandRemoveStage(ctx);
                    case "select", "sel" -> commandSelect(ctx);
                    case "show" -> commandShow(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = boatRace.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            api.locales().resolve(ctx.getSender(), "BOATRACE_LIST_TITLE"),
            "boatrace list",
            ctx.getArgAsInt(1, 1),
            arenas.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, arenas.size());
                for (int i = start; i < end; i++) {
                    MiniGameArena arena = arenas.get(i);
                    Component line = Component.text(arena.id(), NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                        .clickEvent(ClickEvent.runCommand("/boatrace info " + arena.id()))
                        .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena)))
                        .append(Component.text(arena.getName(), NamedTextColor.GRAY))
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA));

                    if (isPlayer) {
                        line = line
                            .append(Component.text(" "))
                            .append(Component.text("[Info]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/boatrace info " + arena.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Show arena details"))));

                        if (arena.isJoinable()) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Join]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/boatrace join " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Join this race"))));
                        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Spectate]", NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand("/boatrace spectate " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Spectate this race"))));
                        }
                    }

                    lines.add(line);
                }
                return lines;
            },
            "BOATRACE_LIST_NONE"
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
        ctx.info(" - Finish region: " + formatRegion(arena.get("finishRegion", SCRegion.class)));
        if (boatRace.arenaBestMillis(arena) > 0L) {
            ctx.info(" - Record: " + boatRace.formatMillis(boatRace.arenaBestMillis(arena)) + " by " + boatRace.arenaBestHolder(arena));
        } else {
            ctx.info(" - Record: none");
        }
        ctx.info(" - Starting grid slots: " + boatRace.startingGrid(arena).size());
        for (int i = 0; i < boatRace.startingGrid(arena).size(); i++) {
            ctx.info("   - Grid " + (i + 1) + ": " + formatLocation(boatRace.startingGrid(arena).get(i)));
        }
        ctx.info(" - Checkpoints: " + boatRace.stageRegions(arena).size());
        for (int i = 0; i < boatRace.stageRegions(arena).size(); i++) {
            ctx.info("   - Checkpoint " + (i + 1) + ": " + formatRegion(boatRace.stageRegions(arena).get(i)));
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
        if (boatRace.minigame().arena(arenaId) != null) {
            ctx.returnError("Arena '" + arenaId + "' already exists.");
        }

        World world = ctx.getArgAsWorld(2);
        if (world == null) {
            Player player = ctx.asPlayer();
            if (player == null) {
                ctx.returnError("Specify a world when creating an arena from console.");
            }
            world = player.getWorld();
        }

        MiniGameArena arena = boatRace.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
        }
        ctx.success("Created Boat Race arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        boatRace.deleteArena(arena.id());
        ctx.success("Deleted Boat Race arena '" + arena.id() + "'.");
    }

    private void commandJoin(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx, 1);
        Player targetPlayer = ctx.getArgAsPlayerOrSender(2);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }
        ensureNotInArena(ctx, targetPlayer);

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.addSpectator(targetPlayer);
            ctx.success("Player '" + targetPlayer.getName() + "' is now spectating arena '" + arena.id() + "'.");
            return;
        }

        if (!arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
        }

        arena.addPlayer(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' joined arena '" + arena.id() + "'.");
    }

    private void commandJoinAll(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx, 1);
        boolean spectateOnly = arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING;
        if (!spectateOnly && !arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
        }

        int joined = 0;
        int spectating = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = boatRace.minigame().findPlayer(targetPlayer);
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
        }

        ctx.success("Arena '" + arena.id() + "': joined " + joined + ", spectating " + spectating + ", skipped " + skipped + ".");
    }

    private void commandSpectate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx, 1);
        Player targetPlayer = ctx.getArgAsPlayerOrSender(2);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }
        ensureNotInArena(ctx, targetPlayer);

        arena.addSpectator(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' is now spectating arena '" + arena.id() + "'.");
    }

    private void commandLeave(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena arena = boatRace.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Boat Race arena.");
        }

        arena.removeOccupant(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' left arena '" + arena.id() + "'.");
    }

    private void commandStart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        if (arena.numPlayers() < arena.getMinPlayers()) {
            ctx.returnError("Arena '" + arena.id() + "' needs at least " + arena.getMinPlayers() + " players to start.");
        }

        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, 5);
        ctx.success("Arena '" + arena.id() + "' is starting.");
    }

    private void commandStop(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        ctx.success("Arena '" + arena.id() + "' has been stopped and reset.");
    }

    private void commandRestart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        try {
            boatRace.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!boatRace.reloadFromConfig()) {
            ctx.returnError("Boat Race config could not be reloaded.");
        }
        ctx.success("Boat Race config reloaded. Loaded " + boatRace.minigame().arenas().size() + " arenas.");
    }

    private void commandValidate(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
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
        MiniGameArena arena = requireArena(ctx, 1);
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
            boatRace.persistArenaEnabled(arena, true);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now enabled.");
    }

    private void commandDisable(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        for (Player player : new ArrayList<>(arena.getOccupants())) {
            arena.removeOccupant(player);
        }
        try {
            arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
            boatRace.persistArenaEnabled(arena, false);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now disabled.");
    }

    private void commandSet(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx, 1);
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
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection, "Arena region");
                ensureArenaRegionAcceptsExistingGeometry(ctx, arena, selection);
                SCRegion copy = selection.copy();
                arena.setRegion(copy);
                arena.set("arenaRegion", copy.copy());
                showRegionPreview(player, "arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
            }
            case "finish" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection, "Finish region");
                ensureRegionContained(ctx, selection, arena.get("arenaRegion", SCRegion.class), "Finish region", "arena region");
                arena.set("finishRegion", selection.copy());
                showRegionPreview(player, "finish", selection);
                ctx.success("Finish region updated for arena '" + arena.id() + "'.");
            }
            case "minplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int minPlayers = ctx.getArgAsInt(3, 1, 1, null);
                arena.setMinPlayers(minPlayers);
                ctx.success("Minimum players set to " + arena.getMinPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "maxplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxPlayers = ctx.getArgAsInt(3, 8, 1, null);
                if (!boatRace.startingGrid(arena).isEmpty() && maxPlayers > boatRace.startingGrid(arena).size()) {
                    ctx.returnError("Max players cannot exceed the configured starting grid slots (" + boatRace.startingGrid(arena).size() + ").");
                }
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Boat Race set target '" + target + "'.");
        }
    }

    private void commandAddGrid(CommandContext ctx) {
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        ensureArenaWorld(ctx, arena, player.getLocation(), "Starting grid slot");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class), "Starting grid slot", "arena region");
        boatRace.startingGrid(arena).add(player.getLocation().clone());
        if (arena.getMaxPlayers() < boatRace.startingGrid(arena).size()) {
            arena.setMaxPlayers(boatRace.startingGrid(arena).size());
        }
        showLocationPreview(player, "grid-add", player.getLocation());
        ctx.success("Added starting grid slot " + boatRace.startingGrid(arena).size() + " to arena '" + arena.id() + "'.");
    }

    private void commandSetGrid(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        int index = requireOneBasedIndex(ctx, 2, boatRace.startingGrid(arena).size(), "grid slot");
        ensureArenaWorld(ctx, arena, player.getLocation(), "Starting grid slot");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class), "Starting grid slot", "arena region");
        boatRace.startingGrid(arena).set(index, player.getLocation().clone());
        showLocationPreview(player, "grid-set-" + index, player.getLocation());
        ctx.success("Updated starting grid slot " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveGrid(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx, 1);
        int index = requireOneBasedIndex(ctx, 2, boatRace.startingGrid(arena).size(), "grid slot");
        boatRace.startingGrid(arena).remove(index);
        if (!boatRace.startingGrid(arena).isEmpty() && arena.getMaxPlayers() > boatRace.startingGrid(arena).size()) {
            arena.setMaxPlayers(boatRace.startingGrid(arena).size());
        }
        ctx.success("Removed starting grid slot " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private void commandAddStage(CommandContext ctx) {
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        SCRegion selection = requireSelection(ctx, player);
        ensureArenaWorld(ctx, arena, selection, "Checkpoint region");
        ensureRegionContained(ctx, selection, arena.get("arenaRegion", SCRegion.class), "Checkpoint region", "arena region");
        boatRace.stageRegions(arena).add(selection.copy());
        showRegionPreview(player, "stage-add", selection);
        ctx.success("Added checkpoint " + boatRace.stageRegions(arena).size() + " to arena '" + arena.id() + "'.");
    }

    private void commandSetStage(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        int index = requireOneBasedIndex(ctx, 2, boatRace.stageRegions(arena).size(), "checkpoint");
        SCRegion selection = requireSelection(ctx, player);
        ensureArenaWorld(ctx, arena, selection, "Checkpoint region");
        ensureRegionContained(ctx, selection, arena.get("arenaRegion", SCRegion.class), "Checkpoint region", "arena region");
        boatRace.stageRegions(arena).set(index, selection.copy());
        showRegionPreview(player, "stage-set-" + index, selection);
        ctx.success("Updated checkpoint " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveStage(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx, 1);
        int index = requireOneBasedIndex(ctx, 2, boatRace.stageRegions(arena).size(), "checkpoint");
        boatRace.stageRegions(arena).remove(index);
        ctx.success("Removed checkpoint " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        String target = ctx.getArgLower(2);
        SCRegion region;
        Location location;

        switch (target) {
            case "arena" -> {
                region = arena.get("arenaRegion", SCRegion.class);
                if (region == null) {
                    ctx.returnError("No stored region is configured for 'arena' in arena '" + arena.id() + "'.");
                }
                requireSameWorld(ctx, player, region.getWorld().getName());
                api.selections().setWorldEditSelection(player, region);
                showRegionPreview(player, "select-arena", region);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (arena).");
            }
            case "finish" -> {
                region = arena.get("finishRegion", SCRegion.class);
                if (region == null) {
                    ctx.returnError("No stored region is configured for 'finish' in arena '" + arena.id() + "'.");
                }
                requireSameWorld(ctx, player, region.getWorld().getName());
                api.selections().setWorldEditSelection(player, region);
                showRegionPreview(player, "select-finish", region);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (finish).");
            }
            case "stage", "checkpoint" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, boatRace.stageRegions(arena).size(), "checkpoint");
                region = boatRace.stageRegions(arena).get(index);
                requireSameWorld(ctx, player, region.getWorld().getName());
                api.selections().setWorldEditSelection(player, region);
                showRegionPreview(player, "select-stage-" + index, region);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (checkpoint " + (index + 1) + ").");
            }
            case "grid" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, boatRace.startingGrid(arena).size(), "grid slot");
                location = boatRace.startingGrid(arena).get(index);
                requireSameWorld(ctx, player, location.getWorld().getName());
                api.selections().setWorldEditSelection(player, location);
                showLocationPreview(player, "select-grid-" + index, location);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (grid " + (index + 1) + ").");
            }
            case "lobby" -> {
                location = arena.getLobbySpawn();
                requireSameWorld(ctx, player, location.getWorld().getName());
                api.selections().setWorldEditSelection(player, location);
                showLocationPreview(player, "select-lobby", location);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (lobby).");
            }
            case "spectator" -> {
                location = arena.getSpectatorSpawn();
                requireSameWorld(ctx, player, location.getWorld().getName());
                api.selections().setWorldEditSelection(player, location);
                showLocationPreview(player, "select-spectator", location);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (spectator).");
            }
            default -> ctx.returnError("Unknown Boat Race select target '" + target + "'.");
        }
    }

    private void commandShow(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        String target = ctx.getArgLower(2);
        Location location;

        switch (target) {
            case "lobby" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            case "grid" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, boatRace.startingGrid(arena).size(), "grid slot");
                location = boatRace.startingGrid(arena).get(index);
            }
            default -> {
                ctx.returnError("Unknown Boat Race show target '" + target + "'.");
                return;
            }
        }

        requireSameWorld(ctx, player, location.getWorld().getName());
        showLocationPreview(player, "show-" + target, location);
        ctx.success("Showing stored location for '" + target + "' in arena '" + arena.id() + "'.");
    }

    private MiniGameArena requireArena(CommandContext ctx, int index) {
        ctx.checkArgsSizeAtLeast(index + 1);
        String arenaId = ctx.getArg(index);
        MiniGameArena arena = boatRace.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
        }
        return arena;
    }

    private MiniGameArena requireArenaForInfo(CommandContext ctx) {
        if (ctx.numArgs() >= 2) {
            return requireArena(ctx, 1);
        }

        List<MiniGameArena> arenas = boatRace.minigame().arenas();
        if (arenas.isEmpty()) {
            ctx.returnError("No Boat Race arenas are loaded.");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }

        ctx.returnError("Specify an arena id. Use /boatrace list to choose one.");
        return null;
    }

    private Player requirePlayer(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("This subcommand must be run in-game.");
        }
        return player;
    }

    private SCRegion requireSelection(CommandContext ctx, Player player) {
        SCRegion selection = api.selections().getWorldEditSelection(player);
        if (selection == null) {
            ctx.returnError("No WorldEdit selection found. Make a selection first.");
        }
        return selection;
    }

    private int requireOneBasedIndex(CommandContext ctx, int argIndex, int size, String label) {
        if (size <= 0) {
            ctx.returnError("No " + label + "s are configured yet.");
        }
        int oneBased = ctx.getArgAsInt(argIndex, 1, 1, size);
        return oneBased - 1;
    }

    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, Location location, String label) {
        if (location == null || location.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
        }
        if (!arena.world().equals(location.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, SCRegion region, String label) {
        if (region == null || region.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
        }
        if (!arena.world().equals(region.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    private void ensureRegionContained(CommandContext ctx, SCRegion child, SCRegion parent, String childLabel, String parentLabel) {
        if (parent != null && !parent.contains(child)) {
            ctx.returnError(childLabel + " must be fully inside the " + parentLabel + ".");
        }
    }

    private void ensureLocationContained(CommandContext ctx, Location location, SCRegion parent, String childLabel, String parentLabel) {
        if (parent != null && !parent.contains(location)) {
            ctx.returnError(childLabel + " must be inside the " + parentLabel + ".");
        }
    }

    private void ensureArenaRegionAcceptsExistingGeometry(CommandContext ctx, MiniGameArena arena, SCRegion arenaRegion) {
        List<String> errors = new ArrayList<>();

        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);
        if (finishRegion != null && !arenaRegion.contains(finishRegion)) {
            errors.add("existing finish region");
        }
        for (int i = 0; i < boatRace.stageRegions(arena).size(); i++) {
            SCRegion stage = boatRace.stageRegions(arena).get(i);
            if (stage != null && !arenaRegion.contains(stage)) {
                errors.add("checkpoint " + (i + 1));
            }
        }
        for (int i = 0; i < boatRace.startingGrid(arena).size(); i++) {
            Location grid = boatRace.startingGrid(arena).get(i);
            if (grid != null && !arenaRegion.contains(grid)) {
                errors.add("starting grid slot " + (i + 1));
            }
        }

        if (!errors.isEmpty()) {
            ctx.warn("Cannot set arena region because it would exclude existing course geometry:");
            for (String error : errors) {
                ctx.warn(" - " + error);
            }
            ctx.returnError("Expand the selection or re-set the listed items first.");
        }
    }

    private void requireSameWorld(CommandContext ctx, Player player, String worldName) {
        if (!player.getWorld().getName().equals(worldName)) {
            ctx.returnError("Move to world '" + worldName + "' to preview that selection.");
        }
    }

    private void ensureNotInArena(CommandContext ctx, Player player) {
        MiniGameArena existingArena = boatRace.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "boatrace-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "boatrace-preview:" + player.getUniqueId() + ":" + key;
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
}
