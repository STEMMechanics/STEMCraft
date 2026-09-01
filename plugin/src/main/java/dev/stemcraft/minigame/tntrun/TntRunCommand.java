package dev.stemcraft.minigame.tntrun;

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

public class TntRunCommand {
    private static final long PREVIEW_TICKS = 100L;

    private final STEMCraftAPI api;
    private final TntRunMiniGame tntRun;

    public TntRunCommand(STEMCraftAPI api, TntRunMiniGame tntRun) {
        this.api = api;
        this.tntRun = tntRun;
    }

    public void onEnable() {
        api.tabComplete().register("tntrun-arenas", (sender, args) -> tntRun.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());

        api.commands().create("tntrun")
            .permission("stemcraft.command.tntrun")
            .usage("/tntrun <list|info|create|delete|join|joinall|spectate|leave|start|stop|restart|save|reload|validate|enable|disable|set|addspawn|setspawn|removespawn|select|sel|show>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{tntrun-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{tntrun-arenas}")
            .tabCompletion("join", "{tntrun-arenas}", "{player}")
            .tabCompletion("joinall", "{tntrun-arenas}")
            .tabCompletion("spectate", "{tntrun-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{tntrun-arenas}")
            .tabCompletion("stop", "{tntrun-arenas}")
            .tabCompletion("restart", "{tntrun-arenas}")
            .tabCompletion("save", "{tntrun-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{tntrun-arenas}")
            .tabCompletion("enable", "{tntrun-arenas}")
            .tabCompletion("disable", "{tntrun-arenas}")
            .tabCompletion("set", "{tntrun-arenas}", "lobby")
            .tabCompletion("set", "{tntrun-arenas}", "spectator")
            .tabCompletion("set", "{tntrun-arenas}", "arena")
            .tabCompletion("set", "{tntrun-arenas}", "minplayers")
            .tabCompletion("set", "{tntrun-arenas}", "maxplayers")
            .tabCompletion("set", "{tntrun-arenas}", "voidy")
            .tabCompletion("set", "{tntrun-arenas}", "fadedelay")
            .tabCompletion("set", "{tntrun-arenas}", "roundseconds")
            .tabCompletion("set", "{tntrun-arenas}", "startcountdown")
            .tabCompletion("set", "{tntrun-arenas}", "endingseconds")
            .tabCompletion("set", "{tntrun-arenas}", "name")
            .tabCompletion("addspawn", "{tntrun-arenas}")
            .tabCompletion("setspawn", "{tntrun-arenas}", "{int}")
            .tabCompletion("removespawn", "{tntrun-arenas}", "{int}")
            .tabCompletion("select", "{tntrun-arenas}", "arena")
            .tabCompletion("select", "{tntrun-arenas}", "spawn", "{int}")
            .tabCompletion("select", "{tntrun-arenas}", "lobby")
            .tabCompletion("select", "{tntrun-arenas}", "spectator")
            .tabCompletion("sel", "{tntrun-arenas}", "arena")
            .tabCompletion("sel", "{tntrun-arenas}", "spawn", "{int}")
            .tabCompletion("sel", "{tntrun-arenas}", "lobby")
            .tabCompletion("sel", "{tntrun-arenas}", "spectator")
            .tabCompletion("show", "{tntrun-arenas}", "lobby")
            .tabCompletion("show", "{tntrun-arenas}", "spectator")
            .tabCompletion("show", "{tntrun-arenas}", "spawn", "{int}")
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
                    case "addspawn" -> commandAddSpawn(ctx);
                    case "setspawn" -> commandSetSpawn(ctx);
                    case "removespawn" -> commandRemoveSpawn(ctx);
                    case "select", "sel" -> commandSelect(ctx);
                    case "show" -> commandShow(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = tntRun.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            "TNT Run Arenas",
            "tntrun list",
            ctx.getArgAsInt(1, 1),
            arenas.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, arenas.size());
                for (int i = start; i < end; i++) {
                    MiniGameArena arena = arenas.get(i);
                    Component line = Component.text(arena.id(), NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                        .clickEvent(ClickEvent.runCommand("/tntrun info " + arena.id()))
                        .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena)))
                        .append(Component.text(arena.getName(), NamedTextColor.GRAY))
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA));

                    if (isPlayer) {
                        line = line
                            .append(Component.text(" "))
                            .append(Component.text("[Info]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/tntrun info " + arena.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Show arena details"))));

                        if (arena.isJoinable()) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Join]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/tntrun join " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Join this arena"))));
                        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                            line = line
                                .append(Component.text(" "))
                                .append(Component.text("[Spectate]", NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand("/tntrun spectate " + arena.id()))
                                    .hoverEvent(HoverEvent.showText(Component.text("Spectate this arena"))));
                        }
                    }

                    lines.add(line);
                }
                return lines;
            },
            "No TNT Run arenas are loaded."
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
        ArenaValidationResult validation = arena.validate();
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Min players: " + arena.getMinPlayers());
        ctx.info(" - Lobby: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.get("arenaRegion", SCRegion.class)));
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        int bottomY = arenaRegion == null ? 0 : arenaRegion.getMinimumLocation().getBlockY();
        ctx.info(" - Bottom Y: " + bottomY);
        ctx.info(" - Starting grid slots: " + tntRun.startingGrid(arena).size());
        for (int i = 0; i < tntRun.startingGrid(arena).size(); i++) {
            ctx.info("   - Spawn " + (i + 1) + ": " + formatLocation(tntRun.startingGrid(arena).get(i)));
        }
        ctx.info(" - Void Y: " + arena.get("voidY", Integer.class, arena.world().getMinHeight()));
        ctx.info(" - Fade delay ticks: " + arena.get("fadeDelayTicks", Integer.class, 8));
        ctx.info(" - Round seconds: " + arena.get("roundSeconds", Integer.class, 180));
        ctx.info(" - Start countdown: " + tntRun.startCountdownSeconds(arena) + " sec");
        ctx.info(" - Reset countdown: " + tntRun.endingSeconds(arena) + " sec");
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
        if (tntRun.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = tntRun.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created TNT Run arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        tntRun.deleteArena(arena.id());
        ctx.success("Deleted TNT Run arena '" + arena.id() + "'.");
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
        boolean suppressAutoStart = !spectateOnly && arena.getStatus() == MiniGameArena.ArenaStatus.WAITING;
        if (suppressAutoStart) {
            arena.set("suppressAutoStart", true);
        }
        try {
            for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
                MiniGameArena existingArena = tntRun.minigame().findPlayer(targetPlayer);
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
        } finally {
            if (suppressAutoStart) {
                arena.set("suppressAutoStart", false);
            }
        }

        if (!spectateOnly && tntRun.minigame().handler() instanceof TntRunArenaHandler handler) {
            if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING) {
                api.tasks().runLater(2L, () -> handler.queueAutoStartIfReady(arena));
            } else if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                handler.scheduleStartingGridRefresh(arena, 2L);
            }
        }

        if (joined == 0 && spectating == 0) {
            ctx.returnError("No online players could be added to arena '" + arena.id() + "'.");
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

        MiniGameArena arena = tntRun.minigame().findPlayer(targetPlayer);
        if (arena == null || !TntRunMiniGame.namespace().equals(arena.namespace())) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a TNT Run arena.");
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

        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, tntRun.startCountdownSeconds(arena));
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
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            tntRun.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!tntRun.reloadFromConfig()) {
            ctx.returnError("TNT Run config could not be reloaded.");
        }
        ctx.success("TNT Run config reloaded. Loaded " + tntRun.minigame().arenas().size() + " arenas.");
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
            tntRun.persistArenaEnabled(arena, true);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now enabled.");
    }

    private void commandDisable(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        for (Player player : new ArrayList<>(arena.getOccupants())) {
            arena.removeOccupant(player);
        }
        try {
            arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
            tntRun.persistArenaEnabled(arena, false);
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
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                ensureArenaWorld(ctx, arena, selection);
                ensureArenaRegionAcceptsExistingGeometry(ctx, arena, selection);
                SCRegion copy = selection.copy();
                arena.setRegion(copy);
                arena.set("arenaRegion", copy.copy());
                showRegionPreview(player, "arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
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
                if (!tntRun.startingGrid(arena).isEmpty() && maxPlayers > tntRun.startingGrid(arena).size()) {
                    ctx.returnError("Max players cannot exceed the configured starting grid slots (" + tntRun.startingGrid(arena).size() + ").");
                }
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "voidy" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, arena.world().getMinHeight(), null, null);
                arena.set("voidY", value);
                ctx.success("Void Y set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "fadedelay" -> {
                ctx.checkArgsSizeAtLeast(4);
                int ticks = ctx.getArgAsInt(3, 8, 0, null);
                arena.set("fadeDelayTicks", ticks);
                ctx.success("Fade delay set to " + ticks + " ticks for arena '" + arena.id() + "'.");
            }
            case "roundseconds" -> {
                ctx.checkArgsSizeAtLeast(4);
                int seconds = ctx.getArgAsInt(3, 180, 10, null);
                arena.set("roundSeconds", seconds);
                ctx.success("Round duration set to " + seconds + " seconds for arena '" + arena.id() + "'.");
            }
            case "startcountdown" -> {
                ctx.checkArgsSizeAtLeast(4);
                int seconds = ctx.getArgAsInt(3, TntRunConfig.DEFAULT_START_COUNTDOWN_SECONDS, 1, null);
                arena.set("startCountdownSeconds", seconds);
                ctx.success("Start countdown set to " + seconds + " seconds for arena '" + arena.id() + "'.");
            }
            case "endingseconds" -> {
                ctx.checkArgsSizeAtLeast(4);
                int seconds = ctx.getArgAsInt(3, TntRunConfig.DEFAULT_ENDING_SECONDS, 1, null);
                arena.set("endingSeconds", seconds);
                ctx.success("Ending countdown set to " + seconds + " seconds for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(3));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown TNT Run set target '" + target + "'.");
        }
    }

    private void commandAddSpawn(CommandContext ctx) {
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        ensureArenaWorld(ctx, arena, player.getLocation(), "Starting grid slot");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class));
        tntRun.startingGrid(arena).add(player.getLocation().clone());
        if (arena.getMaxPlayers() < tntRun.startingGrid(arena).size()) {
            arena.setMaxPlayers(tntRun.startingGrid(arena).size());
        }
        showLocationPreview(player, "spawn-add", player.getLocation());
        ctx.success("Added spawn " + tntRun.startingGrid(arena).size() + " to arena '" + arena.id() + "'.");
    }

    private void commandSetSpawn(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        int index = requireOneBasedIndex(ctx, 2, tntRun.startingGrid(arena).size());
        ensureArenaWorld(ctx, arena, player.getLocation(), "Starting grid slot");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class));
        tntRun.startingGrid(arena).set(index, player.getLocation().clone());
        showLocationPreview(player, "spawn-set-" + index, player.getLocation());
        ctx.success("Updated spawn " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveSpawn(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        int index = requireOneBasedIndex(ctx, 2, tntRun.startingGrid(arena).size());
        tntRun.startingGrid(arena).remove(index);
        if (!tntRun.startingGrid(arena).isEmpty() && arena.getMaxPlayers() > tntRun.startingGrid(arena).size()) {
            arena.setMaxPlayers(tntRun.startingGrid(arena).size());
        }
        ctx.success("Removed spawn " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);
        SCRegion region;
        Location location;

        switch (target) {
            case "arena" -> {
                region = arena.get("arenaRegion", SCRegion.class);
                if (region == null) {
                    ctx.returnError("No stored region is configured for 'arena' in arena '" + arena.id() + "'.");
                    return;
                }
                requireSameWorld(ctx, player, region.getWorld().getName());
                api.selections().setWorldEditSelection(player, region);
                showRegionPreview(player, "select-arena", region);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (arena).");
            }
            case "spawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, tntRun.startingGrid(arena).size());
                location = tntRun.startingGrid(arena).get(index);
                requireSameWorld(ctx, player, location.getWorld().getName());
                api.selections().setWorldEditSelection(player, location);
                showLocationPreview(player, "select-spawn-" + index, location);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (spawn " + (index + 1) + ").");
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
            default -> ctx.returnError("Unknown TNT Run select target '" + target + "'.");
        }
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
            case "spawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, tntRun.startingGrid(arena).size());
                location = tntRun.startingGrid(arena).get(index);
            }
            default -> {
                ctx.returnError("Unknown TNT Run show target '" + target + "'.");
                return;
            }
        }

        requireSameWorld(ctx, player, location.getWorld().getName());
        showLocationPreview(player, "show-" + target, location);
        ctx.success("Showing stored location for '" + target + "' in arena '" + arena.id() + "'.");
    }

    private MiniGameArena requireArena(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(1 + 1);
        String arenaId = ctx.getArg(1);
        MiniGameArena arena = tntRun.minigame().arena(arenaId);
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

        List<MiniGameArena> arenas = tntRun.minigame().arenas();
        if (arenas.isEmpty()) {
            ctx.returnError("No TNT Run arenas are loaded.");
            throw new IllegalStateException("No TNT Run arenas are loaded.");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }

        ctx.returnError("Specify an arena id. Use /tntrun list to choose one.");
        throw new IllegalStateException("Specify an arena id.");
    }

    private Player requirePlayer(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("This subcommand must be run in-game.");
            throw new IllegalStateException("Player is required.");
        }
        return player;
    }

    private SCRegion requireSelection(CommandContext ctx, Player player) {
        SCRegion selection = api.selections().getWorldEditSelection(player);
        if (selection == null) {
            ctx.returnError("No WorldEdit selection found. Make a selection first.");
            throw new IllegalStateException("WorldEdit selection is required.");
        }
        return selection;
    }

    private int requireOneBasedIndex(CommandContext ctx, int argIndex, int size) {
        if (size <= 0) {
            ctx.returnError("No " + "spawn" + "s are configured yet.");
            throw new IllegalStateException("No " + "spawn" + "s are configured.");
        }
        int oneBased = ctx.getArgAsInt(argIndex, 1, 1, size);
        return oneBased - 1;
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

    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, SCRegion region) {
        if (region == null || region.getWorld() == null) {
            ctx.returnError("Arena region" + " is not set in a valid world.");
            return;
        }
        if (!arena.world().equals(region.getWorld())) {
            ctx.returnError("Arena region" + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    private void ensureLocationContained(CommandContext ctx, Location location, SCRegion parent) {
        if (parent != null && !parent.contains(location)) {
            ctx.returnError("Starting grid slot" + " must be inside the " + "arena region" + ".");
        }
    }

    private void ensureArenaRegionAcceptsExistingGeometry(CommandContext ctx, MiniGameArena arena, SCRegion arenaRegion) {
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < tntRun.startingGrid(arena).size(); i++) {
            Location spawn = tntRun.startingGrid(arena).get(i);
            if (spawn != null && !arenaRegion.contains(spawn)) {
                errors.add("spawn " + (i + 1));
            }
        }

        if (!errors.isEmpty()) {
            ctx.warn("Cannot set arena region because it would exclude existing stored geometry:");
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
        MiniGameArena existingArena = tntRun.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "tntrun-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "tntrun-preview:" + player.getUniqueId() + ":" + key;
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
