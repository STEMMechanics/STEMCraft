package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.comet.CometLoot;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NightfallCommand {
    private static final long PREVIEW_TICKS = 100L;

    private final STEMCraftAPI api;
    private final NightfallMiniGame nightfall;

    public NightfallCommand(STEMCraftAPI api, NightfallMiniGame nightfall) {
        this.api = api;
        this.nightfall = nightfall;
    }

    public void onEnable() {
        api.tabComplete().register("nightfall-arenas", (sender, args) -> nightfall.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());

        api.commands().create("nightfall")
            .permission("stemcraft.command.nightfall")
            .usage("/nightfall <list|info|create|delete|join|joinall|spectate|leave|start|stop|restart|cycle|respawn|save|reload|validate|enable|disable|set|select|sel|show|comets|lobbies|addlobby|setlobby|removelobby|generators|addgenerator|setgenerator|removegenerator|dropblocks|adddropblock|setdropweight|removedropblock>")
            .tabCompletion("list")
            .tabCompletion("info")
            .tabCompletion("info", "{nightfall-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{nightfall-arenas}")
            .tabCompletion("join", "{nightfall-arenas}", "{player}")
            .tabCompletion("joinall", "{nightfall-arenas}")
            .tabCompletion("spectate", "{nightfall-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{nightfall-arenas}")
            .tabCompletion("stop", "{nightfall-arenas}")
            .tabCompletion("restart", "{nightfall-arenas}")
            .tabCompletion("cycle", "{nightfall-arenas}")
            .tabCompletion("respawn", "{nightfall-arenas}")
            .tabCompletion("save", "{nightfall-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{nightfall-arenas}")
            .tabCompletion("enable", "{nightfall-arenas}")
            .tabCompletion("disable", "{nightfall-arenas}")
            .tabCompletion("set", "{nightfall-arenas}", "lobby")
            .tabCompletion("set", "{nightfall-arenas}", "spectator")
            .tabCompletion("set", "{nightfall-arenas}", "spawn")
            .tabCompletion("set", "{nightfall-arenas}", "arena")
            .tabCompletion("set", "{nightfall-arenas}", "minplayers")
            .tabCompletion("set", "{nightfall-arenas}", "maxplayers")
            .tabCompletion("set", "{nightfall-arenas}", "lives")
            .tabCompletion("set", "{nightfall-arenas}", "prepseconds")
            .tabCompletion("set", "{nightfall-arenas}", "latejoin")
            .tabCompletion("set", "{nightfall-arenas}", "dropmindelay")
            .tabCompletion("set", "{nightfall-arenas}", "dropmaxdelay")
            .tabCompletion("set", "{nightfall-arenas}", "dropmaxactive")
            .tabCompletion("set", "{nightfall-arenas}", "dropgroupdistance")
            .tabCompletion("set", "{nightfall-arenas}", "droplootmin")
            .tabCompletion("set", "{nightfall-arenas}", "droplootmax")
            .tabCompletion("set", "{nightfall-arenas}", "zombiebasenightly")
            .tabCompletion("set", "{nightfall-arenas}", "zombienightlyincrease")
            .tabCompletion("set", "{nightfall-arenas}", "zombiehealthmultiplier")
            .tabCompletion("set", "{nightfall-arenas}", "zombiewavesize")
            .tabCompletion("set", "{nightfall-arenas}", "zombiewaveinterval")
            .tabCompletion("set", "{nightfall-arenas}", "zombiespawnradiusmin")
            .tabCompletion("set", "{nightfall-arenas}", "zombiespawnradiusmax")
            .tabCompletion("set", "{nightfall-arenas}", "bloodmoonchance")
            .tabCompletion("set", "{nightfall-arenas}", "bloodmoonmultiplier")
            .tabCompletion("set", "{nightfall-arenas}", "bloodmoonbabychance")
            .tabCompletion("set", "{nightfall-arenas}", "bloodmoonbuildersourceremovalchance")
            .tabCompletion("set", "{nightfall-arenas}", "timespeed")
            .tabCompletion("set", "{nightfall-arenas}", "daytimespeed")
            .tabCompletion("set", "{nightfall-arenas}", "nighttimespeed")
            .tabCompletion("set", "{nightfall-arenas}", "name")
            .tabCompletion("select", "{nightfall-arenas}", "arena")
            .tabCompletion("select", "{nightfall-arenas}", "lobby")
            .tabCompletion("select", "{nightfall-arenas}", "spectator")
            .tabCompletion("select", "{nightfall-arenas}", "spawn")
            .tabCompletion("select", "{nightfall-arenas}", "generator")
            .tabCompletion("sel", "{nightfall-arenas}", "arena")
            .tabCompletion("sel", "{nightfall-arenas}", "lobby")
            .tabCompletion("sel", "{nightfall-arenas}", "spectator")
            .tabCompletion("sel", "{nightfall-arenas}", "spawn")
            .tabCompletion("sel", "{nightfall-arenas}", "generator")
            .tabCompletion("show", "{nightfall-arenas}", "lobby")
            .tabCompletion("show", "{nightfall-arenas}", "spectator")
            .tabCompletion("show", "{nightfall-arenas}", "spawn")
            .tabCompletion("show", "{nightfall-arenas}", "generator")
            .tabCompletion("comets", "{nightfall-arenas}")
            .tabCompletion("comets", "{nightfall-arenas}", "set")
            .tabCompletion("comets", "{nightfall-arenas}", "loot")
            .tabCompletion("comets", "{nightfall-arenas}", "addloot")
            .tabCompletion("comets", "{nightfall-arenas}", "setloot")
            .tabCompletion("comets", "{nightfall-arenas}", "removeloot")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "enabled")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "startnight")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "chance")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "chanceincrease")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "maximumchance")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "maximumpernight")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "minimumdistance")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "maximumdistance")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "edgebuffer")
            .tabCompletion("comets", "{nightfall-arenas}", "set", "pathsafetylength")
            .tabCompletion("lobbies", "{nightfall-arenas}")
            .tabCompletion("addlobby", "{nightfall-arenas}")
            .tabCompletion("setlobby", "{nightfall-arenas}")
            .tabCompletion("removelobby", "{nightfall-arenas}")
            .tabCompletion("generators", "{nightfall-arenas}")
            .tabCompletion("addgenerator", "{nightfall-arenas}")
            .tabCompletion("setgenerator", "{nightfall-arenas}")
            .tabCompletion("removegenerator", "{nightfall-arenas}")
            .tabCompletion("dropblocks", "{nightfall-arenas}")
            .tabCompletion("adddropblock", "{nightfall-arenas}")
            .tabCompletion("setdropweight", "{nightfall-arenas}")
            .tabCompletion("removedropblock", "{nightfall-arenas}")
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
                    case "cycle" -> commandCycle(ctx);
                    case "respawn" -> commandRespawn(ctx);
                    case "save" -> commandSave(ctx);
                    case "reload" -> commandReload(ctx);
                    case "validate" -> commandValidate(ctx);
                    case "enable" -> commandEnable(ctx);
                    case "disable" -> commandDisable(ctx);
                    case "set" -> commandSet(ctx);
                    case "select", "sel" -> commandSelect(ctx);
                    case "show" -> commandShow(ctx);
                    case "comets" -> commandComets(ctx);
                    case "lobbies" -> commandLobbies(ctx);
                    case "addlobby" -> commandAddLobby(ctx);
                    case "setlobby" -> commandSetLobby(ctx);
                    case "removelobby" -> commandRemoveLobby(ctx);
                    case "generators" -> commandGenerators(ctx);
                    case "addgenerator" -> commandAddGenerator(ctx);
                    case "setgenerator" -> commandSetGenerator(ctx);
                    case "removegenerator" -> commandRemoveGenerator(ctx);
                    case "dropblocks" -> commandDropBlocks(ctx);
                    case "adddropblock" -> commandAddDropBlock(ctx);
                    case "setdropweight" -> commandSetDropWeight(ctx);
                    case "removedropblock" -> commandRemoveDropBlock(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = nightfall.minigame().arenas().stream()
            .sorted(Comparator.comparing(MiniGameArena::id, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (arenas.isEmpty()) {
            ctx.info("No Nightfall arenas are loaded.");
            return;
        }

        ctx.info("Nightfall arenas:");
        for (MiniGameArena arena : arenas) {
            ctx.info(" - " + arena.id() + " [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] "
                + arena.numPlayers() + "/" + arena.getMaxPlayers() + " :: " + arena.getName());
        }
    }

    private void commandInfo(CommandContext ctx) {
        MiniGameArena arena = requireArenaForInfo(ctx);
        ArenaValidationResult validation = arena.validate();

        ctx.info("Nightfall arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Min players: " + arena.getMinPlayers());
        ctx.info(" - Start countdown: " + nightfall.startCountdownSeconds(arena) + " sec");
        ctx.info(" - Reset countdown: " + nightfall.endingSeconds(arena) + " sec");
        ctx.info(" - Lobbies: " + nightfall.lobbyLocations(arena).size()
            + " (fallback " + formatLocation(arena.getLobbySpawn()) + ")");
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Spawn: " + formatLocation(nightfall.playSpawn(arena)));
        ctx.info(" - Arena region: " + formatRegion(arena.get("arenaRegion", SCRegion.class)));
        ctx.info(" - Deaths: creative ghost until sunrise, then respawn at play spawn");
        ctx.info(" - Prep seconds: " + nightfall.prepSeconds(arena));
        ctx.info(" - Late join: " + (nightfall.allowLateJoin(arena) ? "enabled" : "disabled"));
        ctx.info(" - Day time speed: " + String.format(Locale.ROOT, "%.2fx", nightfall.dayTimeSpeedMultiplier(arena)));
        ctx.info(" - Night time speed: " + String.format(Locale.ROOT, "%.2fx", nightfall.nightTimeSpeedMultiplier(arena)));
        ctx.info(" - Drop delay: " + (nightfall.dropsEnabled(arena)
            ? nightfall.dropMinSeconds(arena) + "-" + nightfall.dropMaxSeconds(arena) + " sec"
            : "disabled"));
        ctx.info(" - Drop max active items: " + nightfall.dropMaxActiveItems(arena));
        ctx.info(" - Drop group distance: " + nightfall.dropGroupDistance(arena) + " blocks");
        ctx.info(" - Drop loot stacks: " + nightfall.dropLootMinStacks(arena) + "-" + nightfall.dropLootMaxStacks(arena));
        ctx.info(" - Drop radius: 5-20 blocks around each player with open sky");
        ctx.info(" - Zombie nightly base: " + nightfall.zombieBaseNightlySpawns(arena));
        ctx.info(" - Zombie nightly increase: " + nightfall.zombieNightlySpawnIncrease(arena));
        ctx.info(" - Zombie nightly health multiplier: " + String.format(Locale.ROOT, "%.2fx", nightfall.zombieNightlyHealthMultiplier(arena)));
        ctx.info(" - Zombie wave size: " + nightfall.zombieWaveSize(arena));
        ctx.info(" - Zombie wave interval: " + nightfall.zombieWaveIntervalSeconds(arena) + " sec");
        ctx.info(" - Zombie spawn radius: " + nightfall.zombieSpawnRadiusMin(arena) + "-" + nightfall.zombieSpawnRadiusMax(arena));
        ctx.info(" - Blood moon chance: " + nightfall.bloodMoonChancePercent(arena) + "%");
        ctx.info(" - Blood moon spawn multiplier: " + String.format(Locale.ROOT, "%.2fx", nightfall.bloodMoonZombieSpawnMultiplier(arena)));
        ctx.info(" - Blood moon baby zombie chance: " + nightfall.bloodMoonBabyZombieChancePercent(arena) + "%");
        ctx.info(" - Blood moon builder source removal chance: "
            + nightfall.bloodMoonBuilderSourceRemovalChancePercent(arena) + "%");
        ctx.info(" - Drop tiers: " + nightfall.dropItems(arena).size() + " (" + nightfall.dropItemCount(arena) + " items)");
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
        if (nightfall.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = nightfall.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created Nightfall arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        nightfall.deleteArena(arena.id());
        ctx.success("Deleted Nightfall arena '" + arena.id() + "'.");
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

        if ((!nightfall.allowLateJoin(arena)
            && (arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION
            || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING))
            || arena.getStatus() == MiniGameArena.ArenaStatus.COOLDOWN
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
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
        boolean spectateOnly = ((!nightfall.allowLateJoin(arena)
            && (arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION
            || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING))
            || arena.getStatus() == MiniGameArena.ArenaStatus.COOLDOWN
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING);
        if (!spectateOnly && !arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
            return;
        }

        int joined = 0;
        int spectating = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = nightfall.minigame().findPlayer(targetPlayer);
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

        MiniGameArena arena = nightfall.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Nightfall arena.");
            return;
        }

        boolean wasLastPlayer = arena.hasPlayer(targetPlayer) && arena.numPlayers() == 1;
        MiniGameArena.ArenaStatus previousStatus = arena.getStatus();
        arena.removeOccupant(targetPlayer);
        if (wasLastPlayer
            && (previousStatus == MiniGameArena.ArenaStatus.PREPARATION
                || previousStatus == MiniGameArena.ArenaStatus.RUNNING
                || previousStatus == MiniGameArena.ArenaStatus.COOLDOWN
                || previousStatus == MiniGameArena.ArenaStatus.ENDING)
            && api.worlds().changes(arena.world()).isRecording()) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
        ctx.success("Player '" + targetPlayer.getName() + "' left arena '" + arena.id() + "'.");
    }

    private void commandStart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        if (arena.numPlayers() < arena.getMinPlayers()) {
            ctx.returnError("Arena '" + arena.id() + "' needs at least " + arena.getMinPlayers() + " players to start.");
            return;
        }

        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, nightfall.startCountdownSeconds(arena));
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
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, nightfall.startCountdownSeconds(arena));
            ctx.success("Arena '" + arena.id() + "' has been restarted.");
            return;
        }
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandCycle(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        if (!(nightfall.minigame().handler() instanceof NightfallArenaHandler handler)) {
            ctx.returnError("Nightfall cycle control is unavailable.");
            return;
        }

        String target = handler.advanceToNextCycle(arena);
        if (target == null) {
            ctx.returnError("Arena '" + arena.id() + "' must be in preparation or running to advance its cycle.");
            return;
        }

        ctx.success("Arena '" + arena.id() + "' advanced to " + target + ".");
    }

    private void commandRespawn(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        if (!(nightfall.minigame().handler() instanceof NightfallArenaHandler handler)) {
            ctx.returnError("Nightfall respawn control is unavailable.");
            return;
        }

        int revived = handler.respawnDownedPlayers(arena);
        if (revived < 0) {
            ctx.returnError("Arena '" + arena.id() + "' must be in preparation or running to respawn dead players.");
        }
        if (revived == 0) {
            ctx.info("Arena '" + arena.id() + "' has no dead players to respawn.");
            return;
        }

        ctx.success("Respawned " + revived + " dead player" + (revived == 1 ? "" : "s") + " in arena '" + arena.id() + "'.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            nightfall.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!nightfall.reloadFromConfig()) {
            ctx.returnError("Nightfall config could not be reloaded.");
        }
        ctx.success("Nightfall config reloaded. Loaded " + nightfall.minigame().arenas().size() + " arenas.");
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
            nightfall.persistArenaEnabled(arena, true);
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
            nightfall.persistArenaEnabled(arena, false);
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
                List<Location> lobbies = nightfall.lobbyLocations(arena);
                if (lobbies.isEmpty()) lobbies.add(player.getLocation().clone());
                else lobbies.set(0, player.getLocation().clone());
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
            case "spawn" -> {
                Player player = requirePlayer(ctx);
                ensureArenaWorld(ctx, arena, player.getLocation(), "Play spawn");
                ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class), "Play spawn");
                arena.set("playSpawn", player.getLocation());
                showLocationPreview(player, "spawn", player.getLocation());
                ctx.success("Play spawn updated for arena '" + arena.id() + "'.");
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
                int minPlayers = ctx.getArgAsInt(3, 1, 1, null);
                arena.setMinPlayers(minPlayers);
                ctx.success("Minimum players set to " + arena.getMinPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "maxplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxPlayers = ctx.getArgAsInt(3, 8, 1, null);
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "lives" -> {
                ctx.checkArgsSizeAtLeast(4);
                int lives = ctx.getArgAsInt(3, 3, 1, null);
                arena.set("lives", lives);
                ctx.success("Lives set to " + lives + " for arena '" + arena.id() + "'.");
            }
            case "prepseconds" -> {
                ctx.checkArgsSizeAtLeast(4);
                int prepSeconds = ctx.getArgAsInt(3, 300, 0, null);
                arena.set("prepSeconds", prepSeconds);
                ctx.success("Prep seconds set to " + prepSeconds + " for arena '" + arena.id() + "'.");
            }
            case "latejoin" -> {
                ctx.checkArgsSizeAtLeast(4);
                String value = ctx.getArgLower(3);
                Boolean enabled = switch (value) {
                    case "true", "on", "yes", "enabled" -> true;
                    case "false", "off", "no", "disabled" -> false;
                    default -> null;
                };
                if (enabled == null) {
                    ctx.returnError("Late join must be one of: true, false, on, off, yes, no.");
                    return;
                }
                arena.set("allowLateJoin", enabled);
                ctx.success("Late join " + (enabled ? "enabled" : "disabled") + " for arena '" + arena.id() + "'.");
            }
            case "timespeed" -> {
                ctx.checkArgsSizeAtLeast(4);
                double multiplier = ctx.getArgAsDouble(3, 2.0d, 1.0d, null);
                arena.set("dayTimeSpeedMultiplier", multiplier);
                arena.set("nightTimeSpeedMultiplier", multiplier);
                ctx.success("Day and night time speed multipliers set to " + String.format(Locale.ROOT, "%.2fx", multiplier)
                    + " for arena '" + arena.id() + "'.");
            }
            case "daytimespeed" -> {
                ctx.checkArgsSizeAtLeast(4);
                double multiplier = ctx.getArgAsDouble(3, 2.0d, 1.0d, null);
                arena.set("dayTimeSpeedMultiplier", multiplier);
                ctx.success("Day time speed multiplier set to " + String.format(Locale.ROOT, "%.2fx", multiplier)
                    + " for arena '" + arena.id() + "'.");
            }
            case "nighttimespeed" -> {
                ctx.checkArgsSizeAtLeast(4);
                double multiplier = ctx.getArgAsDouble(3, 2.0d, 1.0d, null);
                arena.set("nightTimeSpeedMultiplier", multiplier);
                ctx.success("Night time speed multiplier set to " + String.format(Locale.ROOT, "%.2fx", multiplier)
                    + " for arena '" + arena.id() + "'.");
            }
            case "dropmindelay" -> {
                ctx.checkArgsSizeAtLeast(4);
                int minDelay = ctx.getArgAsInt(3, 1, 0, null);
                if (minDelay == 0) {
                    arena.set("dropMinSeconds", 0);
                    arena.set("dropMaxSeconds", 0);
                    ctx.success("Drops disabled for arena '" + arena.id() + "'.");
                    return;
                }
                arena.set("dropMinSeconds", minDelay);
                if (!nightfall.dropsEnabled(arena) || nightfall.dropMaxSeconds(arena) < minDelay) {
                    arena.set("dropMaxSeconds", minDelay);
                }
                ctx.success("Minimum generator delay set to " + minDelay + " seconds for arena '" + arena.id() + "'.");
            }
            case "dropmaxdelay" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxDelay = ctx.getArgAsInt(3, 5, 0, null);
                if (maxDelay == 0) {
                    arena.set("dropMinSeconds", 0);
                    arena.set("dropMaxSeconds", 0);
                    ctx.success("Drops disabled for arena '" + arena.id() + "'.");
                    return;
                }
                arena.set("dropMaxSeconds", maxDelay);
                if (!nightfall.dropsEnabled(arena) || nightfall.dropMinSeconds(arena) > maxDelay) {
                    arena.set("dropMinSeconds", maxDelay);
                }
                ctx.success("Maximum generator delay set to " + maxDelay + " seconds for arena '" + arena.id() + "'.");
            }
            case "dropmaxactive" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 10, 0, null);
                arena.set("dropMaxActiveItems", value);
                ctx.success("Maximum active drops set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "dropgroupdistance" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 100, 0, null);
                arena.set("dropGroupDistance", value);
                ctx.success("Drop group distance set to " + value + " blocks for arena '" + arena.id() + "'.");
            }
            case "droplootmin" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 2, 1, null);
                arena.set("dropLootMinStacks", value);
                if (nightfall.dropLootMaxStacks(arena) < value) {
                    arena.set("dropLootMaxStacks", value);
                }
                ctx.success("Minimum drop loot stacks set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "droplootmax" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 4, 1, null);
                arena.set("dropLootMaxStacks", value);
                if (nightfall.dropLootMinStacks(arena) > value) {
                    arena.set("dropLootMinStacks", value);
                }
                ctx.success("Maximum drop loot stacks set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "zombiebasenightly" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 4, 1, null);
                arena.set("zombieBaseNightlySpawns", value);
                ctx.success("Base nightly zombie spawns set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "zombienightlyincrease" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 3, 0, null);
                arena.set("zombieNightlySpawnIncrease", value);
                ctx.success("Nightly zombie spawn increase set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "zombiehealthmultiplier" -> {
                ctx.checkArgsSizeAtLeast(4);
                double value = ctx.getArgAsDouble(3, 1.05d, 1.0d, null);
                arena.set("zombieNightlyHealthMultiplier", value);
                ctx.success("Zombie nightly health multiplier set to " + String.format(Locale.ROOT, "%.2fx", value)
                    + " for arena '" + arena.id() + "'.");
            }
            case "zombiewavesize" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 2, 1, null);
                arena.set("zombieWaveSize", value);
                ctx.success("Zombie wave size set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "zombiewaveinterval" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 8, 1, null);
                arena.set("zombieWaveIntervalSeconds", value);
                ctx.success("Zombie wave interval set to " + value + " seconds for arena '" + arena.id() + "'.");
            }
            case "zombiespawnradiusmin" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 10, 1, null);
                arena.set("zombieSpawnRadiusMin", value);
                if (nightfall.zombieSpawnRadiusMax(arena) < value) {
                    arena.set("zombieSpawnRadiusMax", value);
                }
                ctx.success("Minimum zombie spawn radius set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "zombiespawnradiusmax" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 24, 1, null);
                arena.set("zombieSpawnRadiusMax", value);
                if (nightfall.zombieSpawnRadiusMin(arena) > value) {
                    arena.set("zombieSpawnRadiusMin", value);
                }
                ctx.success("Maximum zombie spawn radius set to " + value + " for arena '" + arena.id() + "'.");
            }
            case "bloodmoonchance" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 0, 0, 100);
                arena.set("bloodMoonChancePercent", value);
                ctx.success("Blood moon chance set to " + value + "% for arena '" + arena.id() + "'.");
            }
            case "bloodmoonmultiplier" -> {
                ctx.checkArgsSizeAtLeast(4);
                double value = ctx.getArgAsDouble(3, 2.0d, 1.0d, null);
                arena.set("bloodMoonZombieSpawnMultiplier", value);
                ctx.success("Blood moon zombie spawn multiplier set to " + String.format(Locale.ROOT, "%.2fx", value)
                    + " for arena '" + arena.id() + "'.");
            }
            case "bloodmoonbabychance" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 20, 0, 100);
                arena.set("bloodMoonBabyZombieChancePercent", value);
                ctx.success("Blood moon baby zombie chance set to " + value + "% for arena '" + arena.id() + "'.");
            }
            case "bloodmoonbuildersourceremovalchance" -> {
                ctx.checkArgsSizeAtLeast(4);
                int value = ctx.getArgAsInt(3, 70, 0, 100);
                arena.set("bloodMoonBuilderSourceRemovalChancePercent", value);
                ctx.success("Blood moon builder source removal chance set to " + value
                    + "% for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Nightfall set target '" + target + "'.");
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
            case "lobby" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            case "spawn" -> location = nightfall.playSpawn(arena);
            case "generator" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, nightfall.generatorLocations(arena).size(), "generator");
                location = nightfall.generatorLocations(arena).get(index);
            }
            default -> {
                ctx.returnError("Unknown Nightfall select target '" + target + "'.");
                return;
            }
        }

        if (region != null) {
            requireSameWorld(ctx, player, region.getWorld().getName());
            api.selections().setWorldEditSelection(player, region);
            showRegionPreview(player, "select-" + target, region);
            ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
            return;
        }

        if (location == null) {
            ctx.returnError("No stored location is configured for '" + target + "' in arena '" + arena.id() + "'.");
            return;
        }
        requireSameWorld(ctx, player, location.getWorld().getName());
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
            case "spawn" -> location = nightfall.playSpawn(arena);
            case "generator" -> {
                ctx.checkArgsSizeAtLeast(4);
                int index = requireOneBasedIndex(ctx, 3, nightfall.generatorLocations(arena).size(), "generator");
                location = nightfall.generatorLocations(arena).get(index);
            }
            default -> {
                ctx.returnError("Unknown Nightfall show target '" + target + "'.");
                return;
            }
        }

        if (location == null) {
            ctx.returnError("No stored location is configured for '" + target + "' in arena '" + arena.id() + "'.");
            return;
        }
        requireSameWorld(ctx, player, location.getWorld().getName());
        showLocationPreview(player, "show-" + target, location);
        ctx.success("Showing stored location for '" + target + "' in arena '" + arena.id() + "'.");
    }

    private void commandGenerators(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        List<Location> generators = nightfall.generatorLocations(arena);
        if (generators.isEmpty()) {
            ctx.info("Arena '" + arena.id() + "' has no generator locations.");
            return;
        }

        ctx.info("Generator locations for arena '" + arena.id() + "':");
        for (int i = 0; i < generators.size(); i++) {
            ctx.info(" - #" + (i + 1) + " " + formatLocation(generators.get(i)));
        }
    }

    private void commandLobbies(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        List<Location> lobbies = nightfall.lobbyLocations(arena);
        ctx.info("Lobby locations for arena '" + arena.id() + "':");
        for (int i = 0; i < lobbies.size(); i++) {
            ctx.info(" - #" + (i + 1) + " " + formatLocation(lobbies.get(i)));
        }
    }

    private void commandComets(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        if (ctx.numArgs() < 3) {
            showCometSettings(ctx, arena);
            return;
        }

        switch (ctx.getArgLower(2)) {
            case "set" -> commandSetCometSetting(ctx, arena);
            case "loot" -> showCometLoot(ctx, arena);
            case "addloot" -> commandAddCometLoot(ctx, arena);
            case "setloot" -> commandSetCometLoot(ctx, arena);
            case "removeloot" -> commandRemoveCometLoot(ctx, arena);
            default -> ctx.returnError("Unknown comet action. Use set, loot, addloot, setloot, or removeloot.");
        }
    }

    private void showCometSettings(CommandContext ctx, MiniGameArena arena) {
        BloodMoonCometSettings settings = nightfall.bloodMoonComets(arena);
        ctx.info("Blood Moon comet settings for arena '" + arena.id() + "':");
        ctx.info(" - Enabled: " + settings.enabled());
        ctx.info(" - Start night: " + settings.startNight());
        ctx.info(" - Chance: " + settings.chancePercent() + "% + " + settings.chanceIncreasePerNight()
            + "% per night, maximum " + settings.maximumChancePercent() + "%");
        ctx.info(" - Maximum per night: " + settings.maximumPerNight());
        ctx.info(" - Player distance: " + settings.minimumPlayerDistance() + "-"
            + settings.maximumPlayerDistance() + " blocks");
        ctx.info(" - Arena edge buffer: " + settings.arenaEdgeBuffer() + " blocks");
        ctx.info(" - Path safety length: " + settings.pathSafetyLength() + " blocks");
        ctx.info(" - Loot entries: " + settings.loot().size());
    }

    private void commandSetCometSetting(CommandContext ctx, MiniGameArena arena) {
        ctx.checkArgsSizeAtLeast(5);
        BloodMoonCometSettings old = nightfall.bloodMoonComets(arena);
        String key = ctx.getArgLower(3);

        boolean enabled = old.enabled();
        int startNight = old.startNight();
        int chance = old.chancePercent();
        int increase = old.chanceIncreasePerNight();
        int maximumChance = old.maximumChancePercent();
        int maximumPerNight = old.maximumPerNight();
        int minimumDistance = old.minimumPlayerDistance();
        int maximumDistance = old.maximumPlayerDistance();
        int edgeBuffer = old.arenaEdgeBuffer();
        int pathSafetyLength = old.pathSafetyLength();

        switch (key) {
            case "enabled" -> enabled = parseBoolean(ctx, 4);
            case "startnight" -> startNight = ctx.getArgAsInt(4, startNight, 1, null);
            case "chance" -> chance = ctx.getArgAsInt(4, chance, 0, 100);
            case "chanceincrease" -> increase = ctx.getArgAsInt(4, increase, 0, 100);
            case "maximumchance" -> maximumChance = ctx.getArgAsInt(4, maximumChance, 0, 100);
            case "maximumpernight" -> maximumPerNight = ctx.getArgAsInt(4, maximumPerNight, 0, 20);
            case "minimumdistance" -> {
                minimumDistance = ctx.getArgAsInt(4, minimumDistance, 0, null);
                maximumDistance = Math.max(maximumDistance, minimumDistance);
            }
            case "maximumdistance" -> maximumDistance = ctx.getArgAsInt(4, maximumDistance, minimumDistance, null);
            case "edgebuffer" -> edgeBuffer = ctx.getArgAsInt(4, edgeBuffer, 0, null);
            case "pathsafetylength" -> pathSafetyLength = ctx.getArgAsInt(4, pathSafetyLength, 1, null);
            default -> {
                ctx.returnError("Unknown comet setting '" + key + "'.");
                return;
            }
        }

        arena.set("bloodMoonComets", new BloodMoonCometSettings(enabled, startNight, chance, increase,
            maximumChance, maximumPerNight, minimumDistance, maximumDistance, edgeBuffer, pathSafetyLength,
            old.loot()));
        ctx.success("Comet setting '" + key + "' updated for arena '" + arena.id() + "'.");
    }

    private void showCometLoot(CommandContext ctx, MiniGameArena arena) {
        List<CometLoot> loot = nightfall.bloodMoonComets(arena).loot();
        if (loot.isEmpty()) {
            ctx.info("Arena '" + arena.id() + "' has no comet loot configured.");
            return;
        }
        ctx.info("Comet loot for arena '" + arena.id() + "':");
        for (int i = 0; i < loot.size(); i++) {
            CometLoot entry = loot.get(i);
            ctx.info(" - #" + (i + 1) + " " + entry.material().name() + " "
                + entry.minimum() + "-" + entry.maximum());
        }
    }

    private void commandAddCometLoot(CommandContext ctx, MiniGameArena arena) {
        ctx.checkArgsSizeAtLeast(6);
        List<CometLoot> loot = new ArrayList<>(nightfall.bloodMoonComets(arena).loot());
        loot.add(parseCometLoot(ctx, 3, 4, 5));
        setCometLoot(arena, loot);
        ctx.success("Added comet loot entry " + loot.size() + " to arena '" + arena.id() + "'.");
    }

    private void commandSetCometLoot(CommandContext ctx, MiniGameArena arena) {
        ctx.checkArgsSizeAtLeast(7);
        List<CometLoot> loot = new ArrayList<>(nightfall.bloodMoonComets(arena).loot());
        int index = requireOneBasedIndex(ctx, 3, loot.size(), "comet loot item");
        loot.set(index, parseCometLoot(ctx, 4, 5, 6));
        setCometLoot(arena, loot);
        ctx.success("Updated comet loot entry " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveCometLoot(CommandContext ctx, MiniGameArena arena) {
        ctx.checkArgsSizeAtLeast(4);
        List<CometLoot> loot = new ArrayList<>(nightfall.bloodMoonComets(arena).loot());
        int index = requireOneBasedIndex(ctx, 3, loot.size(), "comet loot item");
        loot.remove(index);
        setCometLoot(arena, loot);
        ctx.success("Removed comet loot entry " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private CometLoot parseCometLoot(CommandContext ctx, int materialIndex, int minimumIndex, int maximumIndex) {
        Material material = Material.matchMaterial(ctx.getArg(materialIndex));
        if (material == null || material.isAir() || !material.isBlock()) {
            ctx.returnError("Comet loot material must be a valid block.");
            throw new IllegalArgumentException("Invalid comet loot material");
        }
        int minimum = ctx.getArgAsInt(minimumIndex, 0, 0, 4096);
        int maximum = ctx.getArgAsInt(maximumIndex, minimum, minimum, 4096);
        return new CometLoot(material, minimum, maximum);
    }

    private void setCometLoot(MiniGameArena arena, List<CometLoot> loot) {
        BloodMoonCometSettings old = nightfall.bloodMoonComets(arena);
        arena.set("bloodMoonComets", new BloodMoonCometSettings(old.enabled(), old.startNight(),
            old.chancePercent(), old.chanceIncreasePerNight(), old.maximumChancePercent(), old.maximumPerNight(),
            old.minimumPlayerDistance(), old.maximumPlayerDistance(), old.arenaEdgeBuffer(), old.pathSafetyLength(), loot));
    }

    private boolean parseBoolean(CommandContext ctx, int index) {
        return switch (ctx.getArgLower(index)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> {
                ctx.returnError("Expected true/false, on/off, or enabled/disabled.");
                throw new IllegalArgumentException("Invalid boolean value");
            }
        };
    }

    private void commandAddLobby(CommandContext ctx) {
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        ensureArenaWorld(ctx, arena, player.getLocation(), "Lobby location");
        nightfall.lobbyLocations(arena).add(player.getLocation().clone());
        showLocationPreview(player, "lobby-add", player.getLocation());
        ctx.success("Added lobby location " + nightfall.lobbyLocations(arena).size()
            + " to arena '" + arena.id() + "'.");
    }

    private void commandSetLobby(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        int index = requireOneBasedIndex(ctx, 2, nightfall.lobbyLocations(arena).size(), "lobby");
        ensureArenaWorld(ctx, arena, player.getLocation(), "Lobby location");
        nightfall.lobbyLocations(arena).set(index, player.getLocation().clone());
        if (index == 0) arena.setLobbySpawn(player.getLocation().clone());
        showLocationPreview(player, "lobby-set-" + index, player.getLocation());
        ctx.success("Updated lobby location " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveLobby(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        List<Location> lobbies = nightfall.lobbyLocations(arena);
        if (lobbies.size() <= 1) {
            ctx.returnError("Arena '" + arena.id() + "' must keep at least one lobby location.");
            return;
        }
        int index = requireOneBasedIndex(ctx, 2, lobbies.size(), "lobby");
        lobbies.remove(index);
        arena.setLobbySpawn(lobbies.getFirst().clone());
        ctx.success("Removed lobby location " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private void commandAddGenerator(CommandContext ctx) {
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        ensureArenaWorld(ctx, arena, player.getLocation(), "Generator location");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class), "Generator location");
        nightfall.generatorLocations(arena).add(player.getLocation().clone());
        showLocationPreview(player, "generator-add", player.getLocation());
        ctx.success("Added generator location " + nightfall.generatorLocations(arena).size() + " to arena '" + arena.id() + "'.");
    }

    private void commandSetGenerator(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        int index = requireOneBasedIndex(ctx, 2, nightfall.generatorLocations(arena).size(), "generator");
        ensureArenaWorld(ctx, arena, player.getLocation(), "Generator location");
        ensureLocationContained(ctx, player.getLocation(), arena.get("arenaRegion", SCRegion.class), "Generator location");
        nightfall.generatorLocations(arena).set(index, player.getLocation().clone());
        showLocationPreview(player, "generator-set-" + index, player.getLocation());
        ctx.success("Updated generator location " + (index + 1) + " for arena '" + arena.id() + "'.");
    }

    private void commandRemoveGenerator(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        int index = requireOneBasedIndex(ctx, 2, nightfall.generatorLocations(arena).size(), "generator");
        nightfall.generatorLocations(arena).remove(index);
        ctx.success("Removed generator location " + (index + 1) + " from arena '" + arena.id() + "'.");
    }

    private void commandDropBlocks(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        Map<Integer, List<Material>> dropItems = nightfall.dropItems(arena);
        if (dropItems.isEmpty()) {
            ctx.info("Arena '" + arena.id() + "' has no configured drop item tiers.");
            return;
        }

        ctx.info("Drop item tiers for arena '" + arena.id() + "':");
        for (Map.Entry<Integer, List<Material>> entry : dropItems.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            List<String> names = entry.getValue().stream()
                .map(this::describeMaterial)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            ctx.info(" - <= " + entry.getKey() + ": " + String.join(", ", names));
        }
    }

    private void commandAddDropBlock(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        Player player = requirePlayer(ctx);
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType().isAir()) {
            ctx.returnError("Hold the block you want to add in your main hand.");
            return;
        }

        int threshold = ctx.getArgAsInt(2, 100, 1, 100);
        List<Material> tierItems = nightfall.dropItems(arena).computeIfAbsent(threshold, ignored -> new ArrayList<>());
        if (!tierItems.contains(heldItem.getType())) {
            tierItems.add(heldItem.getType());
        }
        ctx.success("Added drop item '" + describeMaterial(heldItem.getType()) + "' to tier <= " + threshold
            + " in arena '" + arena.id() + "'.");
    }

    private void commandSetDropWeight(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(4);
        MiniGameArena arena = requireArena(ctx);
        List<DropItemEntry> dropItems = flattenedDropItems(arena);
        int index = requireOneBasedIndex(ctx, 2, dropItems.size(), "drop item");
        int threshold = ctx.getArgAsInt(3, 100, 1, 100);
        DropItemEntry entry = dropItems.get(index);

        List<Material> oldTier = nightfall.dropItems(arena).get(entry.threshold());
        if (oldTier != null) {
            oldTier.remove(entry.material());
            if (oldTier.isEmpty()) {
                nightfall.dropItems(arena).remove(entry.threshold());
            }
        }
        nightfall.dropItems(arena).computeIfAbsent(threshold, ignored -> new ArrayList<>()).add(entry.material());
        ctx.success("Moved drop item '" + describeMaterial(entry.material()) + "' to tier <= " + threshold
            + " in arena '" + arena.id() + "'.");
    }

    private void commandRemoveDropBlock(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        List<DropItemEntry> dropItems = flattenedDropItems(arena);
        int index = requireOneBasedIndex(ctx, 2, dropItems.size(), "drop item");
        DropItemEntry entry = dropItems.get(index);

        List<Material> tierItems = nightfall.dropItems(arena).get(entry.threshold());
        if (tierItems != null) {
            tierItems.remove(entry.material());
            if (tierItems.isEmpty()) {
                nightfall.dropItems(arena).remove(entry.threshold());
            }
        }
        ctx.success("Removed drop item '" + describeMaterial(entry.material()) + "' from tier <= " + entry.threshold()
            + " in arena '" + arena.id() + "'.");
    }

    private MiniGameArena requireArena(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(1 + 1);
        String arenaId = ctx.getArg(1);
        MiniGameArena arena = nightfall.minigame().arena(arenaId);
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

        List<MiniGameArena> arenas = nightfall.minigame().arenas();
        if (arenas.isEmpty()) {
            ctx.returnError("No Nightfall arenas are loaded.");
            throw new IllegalStateException("No Nightfall arenas are loaded.");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }

        ctx.returnError("Specify an arena id. Use /nightfall list to choose one.");
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

    private int requireOneBasedIndex(CommandContext ctx, int argIndex, int size, String label) {
        if (size <= 0) {
            ctx.returnError("No " + label + "s are configured yet.");
            throw new IllegalStateException("No " + label + "s are configured.");
        }
        return ctx.getArgAsInt(argIndex, 1, 1, size) - 1;
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

    private void ensureLocationContained(CommandContext ctx, Location location, SCRegion parent, String childLabel) {
        if (parent != null && !parent.contains(location)) {
            ctx.returnError(childLabel + " must be inside the " + "arena region" + ".");
        }
    }

    private void ensureArenaRegionAcceptsExistingGeometry(CommandContext ctx, MiniGameArena arena, SCRegion arenaRegion) {
        List<String> errors = new ArrayList<>();

        Location playSpawn = nightfall.playSpawn(arena);
        if (playSpawn != null && !arenaRegion.contains(playSpawn)) {
            errors.add("play spawn");
        }

        if (!errors.isEmpty()) {
            ctx.warn("Cannot set arena region because it would exclude existing arena geometry:");
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
        MiniGameArena existingArena = nightfall.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "nightfall-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "nightfall-preview:" + player.getUniqueId() + ":" + key;
        api.selections().highlightLocation(baseId + ":location", player, location, PREVIEW_TICKS);
        api.selections().flashBlock(baseId + ":block", player, location, PREVIEW_TICKS);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "<unset>";
        }
        return location.getWorld().getName() + " "
            + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
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

    private String describeMaterial(Material material) {
        if (material == null) {
            return "<unset>";
        }
        return StringUtil.beautify(material.name());
    }

    private @NotNull List<DropItemEntry> flattenedDropItems(@NotNull MiniGameArena arena) {
        List<DropItemEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, List<Material>> entry : nightfall.dropItems(arena).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList()) {
            for (Material material : entry.getValue()) {
                entries.add(new DropItemEntry(entry.getKey(), material));
            }
        }
        return entries;
    }

    private record DropItemEntry(int threshold, @NotNull Material material) {}
}
