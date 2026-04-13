package dev.stemcraft.minigame.parkour;

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
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ParkourCommand {
    private static final long PREVIEW_TICKS = 100L;
    private final STEMCraftAPI api;
    private final ParkourMiniGame parkour;

    public ParkourCommand(STEMCraftAPI api, ParkourMiniGame parkour) {
        this.api = api;
        this.parkour = parkour;
    }

    public void onEnable() {
        api.tabComplete().register("parkour-arenas", (sender, args) -> parkour.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());

        api.commands().create("parkour")
            .permission("stemcraft.command.parkour")
            .usage("/parkour <list|info|create|delete|join|joinall|restart|leave|save|reload|validate|enable|disable|set|select|sel|show>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{parkour-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{parkour-arenas}")
            .tabCompletion("join", "{parkour-arenas}", "{player}")
            .tabCompletion("restart", "{player}")
            .tabCompletion("joinall", "{parkour-arenas}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("save", "{parkour-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{parkour-arenas}")
            .tabCompletion("enable", "{parkour-arenas}")
            .tabCompletion("disable", "{parkour-arenas}")
            .tabCompletion("set", "{parkour-arenas}", "lobby")
            .tabCompletion("set", "{parkour-arenas}", "arena")
            .tabCompletion("set", "{parkour-arenas}", "finish")
            .tabCompletion("set", "{parkour-arenas}", "name")
            .tabCompletion("select", "{parkour-arenas}", "lobby")
            .tabCompletion("select", "{parkour-arenas}", "arena")
            .tabCompletion("select", "{parkour-arenas}", "finish")
            .tabCompletion("sel", "{parkour-arenas}", "lobby")
            .tabCompletion("sel", "{parkour-arenas}", "arena")
            .tabCompletion("sel", "{parkour-arenas}", "finish")
            .tabCompletion("show", "{parkour-arenas}", "lobby")
            .executor((ignored, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);

                switch (ctx.getArgLower(0)) {
                    case "list" -> commandList(ctx);
                    case "info" -> commandInfo(ctx);
                    case "create" -> commandCreate(ctx);
                    case "delete" -> commandDelete(ctx);
                    case "join" -> commandJoin(ctx);
                    case "joinall", "join-all" -> commandJoinAll(ctx);
                    case "restart" -> commandRestart(ctx);
                    case "leave" -> commandLeave(ctx);
                    case "save" -> commandSave(ctx);
                    case "reload" -> commandReload(ctx);
                    case "validate" -> commandValidate(ctx);
                    case "enable" -> commandEnable(ctx);
                    case "disable" -> commandDisable(ctx);
                    case "set" -> commandSet(ctx);
                    case "select" -> commandSelect(ctx);
                    case "sel" -> commandSelect(ctx);
                    case "show" -> commandShow(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = parkour.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            api.locales().resolve(ctx.getSender(), "PARKOUR_LIST_TITLE"),
            "parkour list",
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
                        .append(Component.text(" " + arena.numPlayers() + " active", NamedTextColor.AQUA)));
                }
                return lines;
            },
            "PARKOUR_LIST_NONE"
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
        MiniGameArena arena = requireArena(ctx, 1);
        ArenaValidationResult validation = arena.validate();
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers());
        ctx.info(" - Lobby region: " + formatRegion(arena.get("lobbyRegion", SCRegion.class)));
        ctx.info(" - Start point: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.get("arenaRegion", SCRegion.class)));
        ctx.info(" - Finish region: " + formatRegion(arena.get("finishRegion", SCRegion.class)));
        String loadError = arena.get("loadError", String.class);
        if (loadError != null && !loadError.isBlank()) {
            ctx.warn(" - Load issue: " + loadError);
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
        if (parkour.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = parkour.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
        }
        ctx.success("Created Parkour arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        parkour.deleteArena(arena.id());
        ctx.success("Deleted Parkour arena '" + arena.id() + "'.");
    }

    private void commandJoin(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx, 1);
        Player targetPlayer = ctx.getArgAsPlayerOrSender(2);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }
        ensureNotInArena(ctx, targetPlayer);

        if (!arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
        }

        arena.addPlayer(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' joined arena '" + arena.id() + "'.");
    }

    private void commandJoinAll(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArena(ctx, 1);
        if (!arena.isJoinable()) {
            ctx.returnError("Arena '" + arena.id() + "' is not joinable right now.");
        }

        int joined = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = parkour.minigame().findPlayer(targetPlayer);
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
        }

        ctx.success("Arena '" + arena.id() + "': joined " + joined + ", skipped " + skipped + ".");
    }

    private void commandRestart(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena arena = parkour.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Parkour arena.");
        }

        parkour.resetRun(arena, targetPlayer, "");
        if(!ctx.equalsPlayer(targetPlayer)) {
            // TODO: Use a better method? Feels rather fishy.
            STEMCraftAPI.api().messages().success(targetPlayer, "Your run has been restarted.");
        }
        ctx.success("Restarted " + targetPlayer.getName() + "'s run.");
    }

    private void commandLeave(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena arena = parkour.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Parkour arena.");
        }

        arena.removePlayer(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' left arena '" + arena.id() + "'.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        try {
            arena.remove("loadError");
            parkour.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!parkour.reloadFromConfig()) {
            ctx.returnError("Parkour config could not be reloaded.");
        }
        ctx.success("Parkour config reloaded. Loaded " + parkour.minigame().arenas().size() + " arenas.");
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
            arena.remove("loadError");
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            parkour.persistArenaEnabled(arena, true);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now enabled.");
    }

    private void commandDisable(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx, 1);
        for (Player player : new ArrayList<>(arena.getPlayers())) {
            arena.removePlayer(player);
        }
        try {
            arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
            parkour.persistArenaEnabled(arena, false);
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
            case "lobby", "spawn" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.set("lobbyRegion", selection.copy());
                parkour.syncLobbyRegion(arena);
                arena.remove("loadError");
                showRegionPreview(player, "lobby", selection);
                ctx.success("Lobby region updated for arena '" + arena.id() + "'.");
            }
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                SCRegion regionCopy = selection.copy();
                arena.setRegion(regionCopy);
                arena.set("arenaRegion", regionCopy.copy());
                arena.remove("loadError");
                showRegionPreview(player, "arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
            }
            case "finish" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.set("finishRegion", selection.copy());
                arena.remove("loadError");
                showRegionPreview(player, "finish", selection);
                ctx.success("Finish region updated for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                arena.remove("loadError");
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Parkour set target '" + target + "'.");
        }
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        String target = ctx.getArgLower(2);
        SCRegion region = null;
        Location location = null;

        switch (target) {
            case "lobby", "spawn" -> region = arena.get("lobbyRegion", SCRegion.class);
            case "arena" -> region = arena.get("arenaRegion", SCRegion.class);
            case "finish" -> region = arena.get("finishRegion", SCRegion.class);
            default -> {
                ctx.returnError("Unknown Parkour select target '" + target + "'.");
                return;
            }
        }

        if (location != null) {
            if (!player.getWorld().equals(location.getWorld())) {
                ctx.returnError("Move to world '" + location.getWorld().getName() + "' to preview that location.");
            }

            api.selections().setWorldEditSelection(player, location);
            showLocationPreview(player, "select-" + target, location);
            ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
            return;
        }

        if (region == null) {
            ctx.returnError("No stored region is configured for '" + target + "' in arena '" + arena.id() + "'.");
        }
        if (!player.getWorld().equals(region.getWorld())) {
            ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
        }

        api.selections().setWorldEditSelection(player, region);
        showRegionPreview(player, "select-" + target, region);
        ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
    }

    private void commandShow(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx, 1);
        String target = ctx.getArgLower(2);
        SCRegion region;

        switch (target) {
            case "lobby", "spawn" -> region = arena.get("lobbyRegion", SCRegion.class);
            case "arena" -> region = arena.get("arenaRegion", SCRegion.class);
            case "finish" -> region = arena.get("finishRegion", SCRegion.class);
            default -> {
                ctx.returnError("Unknown Parkour show target '" + target + "'.");
                return;
            }
        }

        if (region == null) {
            ctx.returnError("No stored region is configured for '" + target + "' in arena '" + arena.id() + "'.");
        }
        if (!player.getWorld().equals(region.getWorld())) {
            ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
        }

        showRegionPreview(player, "show-" + target, region);
        ctx.success("Showing stored region for '" + target + "' in arena '" + arena.id() + "'.");
    }

    private MiniGameArena requireArena(CommandContext ctx, int index) {
        ctx.checkArgsSizeAtLeast(index + 1);
        String arenaId = ctx.getArg(index);
        MiniGameArena arena = parkour.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
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
        MiniGameArena existingArena = parkour.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "parkour-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "parkour-preview:" + player.getUniqueId() + ":" + key;
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
