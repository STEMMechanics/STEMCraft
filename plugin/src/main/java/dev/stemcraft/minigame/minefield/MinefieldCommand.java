package dev.stemcraft.minigame.minefield;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MinefieldCommand {
    private static final long PREVIEW_TICKS = 100L;

    private final STEMCraftAPI api;
    private final MinefieldMiniGame minefield;

    public MinefieldCommand(STEMCraftAPI api, MinefieldMiniGame minefield) {
        this.api = api;
        this.minefield = minefield;
    }

    public void onEnable() {
        api.tabComplete().register("minefield-arenas", (sender, args) -> minefield.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());

        api.commands().create("minefield")
            .permission("stemcraft.command.minefield")
            .usage("/minefield <list|info|create|delete|join|joinall|spectate|leave|start|stop|restart|save|reload|validate|enable|disable|set|select|sel|show>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{minefield-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{minefield-arenas}")
            .tabCompletion("join", "{minefield-arenas}", "{player}")
            .tabCompletion("joinall", "{minefield-arenas}")
            .tabCompletion("spectate", "{minefield-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{minefield-arenas}")
            .tabCompletion("stop", "{minefield-arenas}")
            .tabCompletion("restart", "{minefield-arenas}")
            .tabCompletion("save", "{minefield-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{minefield-arenas}")
            .tabCompletion("enable", "{minefield-arenas}")
            .tabCompletion("disable", "{minefield-arenas}")
            .tabCompletion("set", "{minefield-arenas}", "spectator")
            .tabCompletion("set", "{minefield-arenas}", "arena")
            .tabCompletion("set", "{minefield-arenas}", "start")
            .tabCompletion("set", "{minefield-arenas}", "field")
            .tabCompletion("set", "{minefield-arenas}", "finish")
            .tabCompletion("set", "{minefield-arenas}", "minplayers")
            .tabCompletion("set", "{minefield-arenas}", "maxplayers")
            .tabCompletion("set", "{minefield-arenas}", "mine-ratio")
            .tabCompletion("set", "{minefield-arenas}", "hidden-block")
            .tabCompletion("set", "{minefield-arenas}", "clear-block")
            .tabCompletion("set", "{minefield-arenas}", "adjacent-block")
            .tabCompletion("set", "{minefield-arenas}", "mine-block")
            .tabCompletion("set", "{minefield-arenas}", "startcountdown")
            .tabCompletion("set", "{minefield-arenas}", "endingseconds")
            .tabCompletion("set", "{minefield-arenas}", "completionbonus")
            .tabCompletion("set", "{minefield-arenas}", "name")
            .tabCompletion("select", "{minefield-arenas}", "spectator")
            .tabCompletion("select", "{minefield-arenas}", "arena")
            .tabCompletion("select", "{minefield-arenas}", "start")
            .tabCompletion("select", "{minefield-arenas}", "field")
            .tabCompletion("select", "{minefield-arenas}", "finish")
            .tabCompletion("sel", "{minefield-arenas}", "spectator")
            .tabCompletion("sel", "{minefield-arenas}", "arena")
            .tabCompletion("sel", "{minefield-arenas}", "start")
            .tabCompletion("sel", "{minefield-arenas}", "field")
            .tabCompletion("sel", "{minefield-arenas}", "finish")
            .tabCompletion("show", "{minefield-arenas}", "spectator")
            .tabCompletion("show", "{minefield-arenas}", "arena")
            .tabCompletion("show", "{minefield-arenas}", "start")
            .tabCompletion("show", "{minefield-arenas}", "field")
            .tabCompletion("show", "{minefield-arenas}", "finish")
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
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = minefield.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            "Minefield Arenas",
            "minefield list",
            ctx.getArgAsInt(1, 1),
            arenas.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, arenas.size());
                for (int i = start; i < end; i++) {
                    MiniGameArena arena = arenas.get(i);
                    lines.add(Component.text(arena.id(), NamedTextColor.YELLOW)
                        .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena)))
                        .append(Component.text(arena.getName(), NamedTextColor.GRAY))
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA)));
                }
                return lines;
            },
            "No Minefield arenas are loaded."
        );
    }

    private void commandInfo(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        ArenaValidationResult validation = arena.validate();
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Min players: " + arena.getMinPlayers());
        ctx.info(" - Start countdown: " + minefield.startCountdownSeconds(arena) + " sec");
        ctx.info(" - Ending countdown: " + minefield.endingSeconds(arena) + " sec");
        ctx.info(" - Mine ratio: " + minefield.mineRatio(arena));
        ctx.info(" - Completion bonus: " + minefield.completionBonus(arena));
        ctx.info(" - Hidden block: " + minefield.hiddenBlock(arena));
        ctx.info(" - Clear block: " + minefield.clearBlock(arena));
        ctx.info(" - Adjacent block: " + minefield.adjacentBlock(arena));
        ctx.info(" - Mine block: " + minefield.triggeredMineBlock(arena));
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class)));
        ctx.info(" - Start region: " + formatRegion(arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class)));
        ctx.info(" - Field region: " + formatRegion(arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class)));
        ctx.info(" - Finish region: " + formatRegion(arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class)));
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
        if (minefield.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = minefield.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created Minefield arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        minefield.deleteArena(arena.id());
        ctx.success("Deleted Minefield arena '" + arena.id() + "'.");
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
        if (!arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
            return;
        }

        int joined = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = minefield.minigame().findPlayer(targetPlayer);
            if (existingArena != null) {
                skipped++;
                continue;
            }
            arena.addPlayer(targetPlayer);
            if (arena.hasPlayer(targetPlayer)) {
                joined++;
            } else {
                skipped++;
            }
        }

        if (joined == 0) {
            ctx.returnError("No online players could be added to arena '" + arena.id() + "'.");
            return;
        }

        ctx.success("Arena '" + arena.id() + "': joined " + joined + ", skipped " + skipped + ".");
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

        MiniGameArena arena = minefield.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Minefield arena.");
            return;
        }

        arena.removeOccupant(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' left arena '" + arena.id() + "'.");
    }

    private void commandStart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        ArenaValidationResult result = arena.validate();
        if (result.hasErrors()) {
            ctx.warn("Arena '" + arena.id() + "' cannot be started until it is valid:");
            for (String error : result.getErrors()) {
                ctx.warn(" - " + error);
            }
            return;
        }

        arena.set("suppressAutoStart", false);
        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, minefield.startCountdownSeconds(arena));
        ctx.success("Arena '" + arena.id() + "' is starting.");
    }

    private void commandStop(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        arena.set("suppressAutoStart", true);
        arena.setCountdown(0);
        arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
        ctx.success("Arena '" + arena.id() + "' has been stopped.");
    }

    private void commandRestart(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        arena.set("suppressAutoStart", false);
        arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        ctx.success("Arena '" + arena.id() + "' is restarting.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            minefield.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
            return;
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!minefield.reloadFromConfig()) {
            ctx.returnError("Minefield config could not be reloaded.");
            return;
        }
        ctx.success("Minefield config reloaded. Loaded " + minefield.minigame().arenas().size() + " arenas.");
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
            arena.set("suppressAutoStart", false);
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            minefield.persistArenaEnabled(arena, true);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
            return;
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
            minefield.persistArenaEnabled(arena, false);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
            return;
        }
        ctx.success("Arena '" + arena.id() + "' is now disabled.");
    }

    private void commandSet(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);

        switch (target) {
            case "spectator" -> {
                Player player = requirePlayer(ctx);
                arena.setSpectatorSpawn(player.getLocation().clone());
                showLocationPreview(player, "spectator", player.getLocation());
                ctx.success("Spectator spawn updated for arena '" + arena.id() + "'.");
            }
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.setRegion(selection.copy());
                arena.set(MinefieldMiniGame.ARENA_REGION_KEY, selection.copy());
                showRegionPreview(player, "arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
            }
            case "start" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.set(MinefieldMiniGame.START_REGION_KEY, selection.copy());
                minefield.syncStartRegion(arena);
                showRegionPreview(player, "start", selection);
                ctx.success("Start region updated for arena '" + arena.id() + "'.");
            }
            case "field" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.set(MinefieldMiniGame.FIELD_REGION_KEY, selection.copy());
                showRegionPreview(player, "field", selection);
                ctx.success("Field region updated for arena '" + arena.id() + "'.");
            }
            case "finish" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.set(MinefieldMiniGame.FINISH_REGION_KEY, selection.copy());
                showRegionPreview(player, "finish", selection);
                ctx.success("Finish region updated for arena '" + arena.id() + "'.");
            }
            case "minplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int minPlayers = ctx.getArgAsInt(3, 1, null, null);
                arena.setMinPlayers(minPlayers);
                if (arena.getMaxPlayers() < minPlayers) {
                    arena.setMaxPlayers(minPlayers);
                }
                ctx.success("Minimum players set to " + arena.getMinPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "maxplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxPlayers = ctx.getArgAsInt(3, 1, null, null);
                if (maxPlayers < arena.getMinPlayers()) {
                    ctx.returnError("Max players cannot be lower than min players.");
                    return;
                }
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "mine-ratio", "mineratio" -> {
                ctx.checkArgsSizeAtLeast(4);
                double ratio;
                try {
                    ratio = Double.parseDouble(ctx.getArg(3));
                } catch (NumberFormatException exception) {
                    ctx.returnError("Mine ratio must be a decimal value such as 0.18.");
                    return;
                }
                if (ratio <= 0.0d || ratio >= 0.95d) {
                    ctx.returnError("Mine ratio must be greater than 0 and lower than 0.95.");
                    return;
                }
                arena.set(MinefieldMiniGame.MINE_RATIO_KEY, ratio);
                ctx.success("Mine ratio set to " + ratio + " for arena '" + arena.id() + "'.");
            }
            case "hidden-block", "hiddenblock" -> setBlockMaterial(ctx, arena, MinefieldMiniGame.HIDDEN_BLOCK_KEY, "Hidden block");
            case "clear-block", "clearblock" -> setBlockMaterial(ctx, arena, MinefieldMiniGame.CLEAR_BLOCK_KEY, "Clear block");
            case "adjacent-block", "adjacentblock" -> setBlockMaterial(ctx, arena, MinefieldMiniGame.ADJACENT_BLOCK_KEY, "Adjacent block");
            case "mine-block", "mineblock" -> setBlockMaterial(ctx, arena, MinefieldMiniGame.TRIGGERED_MINE_BLOCK_KEY, "Mine block");
            case "startcountdown", "start-countdown" -> {
                ctx.checkArgsSizeAtLeast(4);
                int seconds = ctx.getArgAsInt(3, 1, null, null);
                arena.set(MinefieldMiniGame.START_COUNTDOWN_SECONDS_KEY, seconds);
                ctx.success("Start countdown set to " + seconds + " seconds for arena '" + arena.id() + "'.");
            }
            case "endingseconds", "ending-seconds" -> {
                ctx.checkArgsSizeAtLeast(4);
                int seconds = ctx.getArgAsInt(3, 1, null, null);
                arena.set(MinefieldMiniGame.ENDING_SECONDS_KEY, seconds);
                ctx.success("Ending countdown set to " + seconds + " seconds for arena '" + arena.id() + "'.");
            }
            case "completionbonus", "completion-bonus" -> {
                ctx.checkArgsSizeAtLeast(4);
                int bonus = ctx.getArgAsInt(3, 0, null, null);
                arena.set(MinefieldMiniGame.COMPLETION_BONUS_KEY, bonus);
                ctx.success("Completion bonus set to " + bonus + " for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(3));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Minefield set target '" + target + "'.");
        }
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);

        switch (target) {
            case "spectator" -> {
                Location location = arena.getSpectatorSpawn();
                if (location == null) {
                    ctx.returnError("No stored location is configured for 'spectator' in arena '" + arena.id() + "'.");
                    return;
                }
                api.selections().setWorldEditSelection(player, location);
                showLocationPreview(player, "spectator", location);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (spectator).");
            }
            case "arena", "start", "field", "finish" -> {
                SCRegion region = switch (target) {
                    case "arena" -> arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class);
                    case "start" -> arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
                    case "field" -> arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class);
                    default -> arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);
                };
                if (region == null) {
                    ctx.returnError("No stored region is configured for '" + target + "' in arena '" + arena.id() + "'.");
                    return;
                }
                if (!player.getWorld().equals(region.getWorld())) {
                    ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
                    return;
                }
                api.selections().setWorldEditSelection(player, region);
                showRegionPreview(player, target, region);
                ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
            }
            default -> ctx.returnError("Unknown Minefield select target '" + target + "'.");
        }
    }

    private void commandShow(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);

        switch (target) {
            case "spectator" -> {
                Location location = arena.getSpectatorSpawn();
                if (location == null) {
                    ctx.returnError("No stored location is configured for 'spectator' in arena '" + arena.id() + "'.");
                    return;
                }
                if (!player.getWorld().equals(location.getWorld())) {
                    ctx.returnError("Move to world '" + location.getWorld().getName() + "' to preview that location.");
                    return;
                }
                showLocationPreview(player, "spectator", location);
                ctx.success("Showing stored location for 'spectator' in arena '" + arena.id() + "'.");
            }
            case "arena", "start", "field", "finish" -> {
                SCRegion region = switch (target) {
                    case "arena" -> arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class);
                    case "start" -> arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
                    case "field" -> arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class);
                    default -> arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);
                };
                if (region == null) {
                    ctx.returnError("No stored region is configured for '" + target + "' in arena '" + arena.id() + "'.");
                    return;
                }
                if (!player.getWorld().equals(region.getWorld())) {
                    ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
                    return;
                }
                showRegionPreview(player, target, region);
                ctx.success("Showing stored region for '" + target + "' in arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Minefield show target '" + target + "'.");
        }
    }

    private void setBlockMaterial(CommandContext ctx, MiniGameArena arena, String key, String label) {
        ctx.checkArgsSizeAtLeast(4);
        Material material = parseMaterial(ctx.getArg(3));
        if (material == null || material.isAir()) {
            ctx.returnError("Invalid material '" + ctx.getArg(3) + "'.");
            return;
        }
        arena.set(key, material);
        ctx.success(label + " set to " + material + " for arena '" + arena.id() + "'.");
    }

    private Material parseMaterial(String value) {
        return Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
    }

    private MiniGameArena requireArena(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        String arenaId = ctx.getArg(1);
        MiniGameArena arena = minefield.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
            throw new IllegalStateException("Arena '" + arenaId + "' does not exist.");
        }
        return arena;
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

    private void ensureNotInArena(CommandContext ctx, Player player) {
        MiniGameArena existingArena = minefield.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        api.selections().highlightRegion("minefield-preview:" + player.getUniqueId() + ":" + key, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "minefield-preview:" + player.getUniqueId() + ":" + key;
        api.selections().highlightLocation(baseId + ":location", player, location, PREVIEW_TICKS);
        api.selections().flashBlock(baseId + ":block", player, location, PREVIEW_TICKS);
    }

    private NamedTextColor statusColour(MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case IDLE, WAITING -> NamedTextColor.GREEN;
            case STARTING, PREPARATION, RUNNING, COOLDOWN, ENDING, RESETTING -> NamedTextColor.GOLD;
            case SETUP -> NamedTextColor.BLUE;
            case DISABLED, SHUTDOWN -> NamedTextColor.RED;
        };
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
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
