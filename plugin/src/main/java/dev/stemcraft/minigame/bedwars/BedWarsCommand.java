package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionInput;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BedWarsCommand {
    private static final long PREVIEW_TICKS = 100L;
    private final STEMCraftAPI api;
    private final BedWarsMiniGame bedWars;

    public BedWarsCommand(STEMCraftAPI api, BedWarsMiniGame bedWars) {
        this.api = api;
        this.bedWars = bedWars;
    }

    public void onEnable() {
        api.tabComplete().register("bedwars-arenas", (sender, args) -> bedWars.minigame().arenas().stream()
            .map(MiniGameArena::id)
            .sorted()
            .toList());
        api.tabComplete().register("bedwars-team-ids", (sender, args) -> bedWars.supportedTeamIds());
        api.tabComplete().register("bedwars-arena-teams", (sender, args) -> {
            if (args.length < 1) {
                return List.of();
            }
            MiniGameArena arena = bedWars.minigame().arena(args[0]);
            if (arena == null) {
                return List.of();
            }
            return arena.getTeams().stream().map(MiniGameTeam::getName).sorted().toList();
        });

        api.commands().create("bedwars")
            .permission("stemcraft.command.bedwars")
            .usage("/bedwars <list|info|create|delete|join|joinall|spectate|leave|start|stop|restart|save|reload|validate|enable|disable|addteam|removeteam|set|select|sel|show|dropitems|adddropitem|removedropitem>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{bedwars-arenas}")
            .tabCompletion("create")
            .tabCompletion("create", "")
            .tabCompletion("create", "", "{world}")
            .tabCompletion("delete", "{bedwars-arenas}")
            .tabCompletion("join", "{bedwars-arenas}", "{player}")
            .tabCompletion("joinall", "{bedwars-arenas}")
            .tabCompletion("spectate", "{bedwars-arenas}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("start", "{bedwars-arenas}")
            .tabCompletion("stop", "{bedwars-arenas}")
            .tabCompletion("restart", "{bedwars-arenas}")
            .tabCompletion("save", "{bedwars-arenas}")
            .tabCompletion("reload")
            .tabCompletion("validate", "{bedwars-arenas}")
            .tabCompletion("enable", "{bedwars-arenas}")
            .tabCompletion("disable", "{bedwars-arenas}")
            .tabCompletion("addteam", "{bedwars-arenas}", "{bedwars-team-ids}")
            .tabCompletion("removeteam", "{bedwars-arenas}", "{bedwars-arena-teams:$1}")
            .tabCompletion("set", "{bedwars-arenas}", "lobby")
            .tabCompletion("set", "{bedwars-arenas}", "lobbyspawn")
            .tabCompletion("set", "{bedwars-arenas}", "spectator")
            .tabCompletion("set", "{bedwars-arenas}", "arena")
            .tabCompletion("set", "{bedwars-arenas}", "lobbyregion")
            .tabCompletion("set", "{bedwars-arenas}", "teamspawn", "{bedwars-arena-teams:$1}")
            .tabCompletion("set", "{bedwars-arenas}", "teambed", "{bedwars-arena-teams:$1}")
            .tabCompletion("set", "{bedwars-arenas}", "teamsize")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection", "none")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection", "floor")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection", "hotbar")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection", "floor")
            .tabCompletion("set", "{bedwars-arenas}", "teamselection", "hotbar")
            .tabCompletion("set", "{bedwars-arenas}", "minplayers")
            .tabCompletion("set", "{bedwars-arenas}", "maxplayers")
            .tabCompletion("set", "{bedwars-arenas}", "name")
            .tabCompletion("set", "{bedwars-arenas}", "teamname", "{bedwars-arena-teams:$1}")
            .tabCompletion("select", "{bedwars-arenas}", "arena")
            .tabCompletion("select", "{bedwars-arenas}", "lobbyregion")
            .tabCompletion("select", "{bedwars-arenas}", "teambed", "{bedwars-arena-teams:$1}")
            .tabCompletion("sel", "{bedwars-arenas}", "arena")
            .tabCompletion("sel", "{bedwars-arenas}", "lobbyregion")
            .tabCompletion("sel", "{bedwars-arenas}", "teambed", "{bedwars-arena-teams:$1}")
            .tabCompletion("show", "{bedwars-arenas}", "lobby")
            .tabCompletion("show", "{bedwars-arenas}", "lobbyspawn")
            .tabCompletion("show", "{bedwars-arenas}", "spectator")
            .tabCompletion("show", "{bedwars-arenas}", "teamspawn", "{bedwars-arena-teams:$1}")
            .tabCompletion("dropitems", "{bedwars-arenas}")
            .tabCompletion("dropitems", "{bedwars-arenas}", "{int}")
            .tabCompletion("adddropitem", "{bedwars-arenas}")
            .tabCompletion("removedropitem", "{bedwars-arenas}")
            .tabCompletion("removedropitem", "{bedwars-arenas}", "{int}")
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
                    case "addteam" -> commandAddTeam(ctx);
                    case "removeteam" -> commandRemoveTeam(ctx);
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
        List<MiniGameArena> arenas = bedWars.minigame().arenas().stream()
            .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            api.locales().resolve(ctx.getSender(), "BEDWARS_LIST_TITLE"),
            "bedwars list",
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
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA))
                        .append(Component.text(" teams=" + arena.getTeams().size(), NamedTextColor.BLUE)));
                }
                return lines;
            },
            "BEDWARS_LIST_NONE"
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
        MiniGameArena arena = requireArena(ctx);
        ArenaValidationResult validation = arena.validate();
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Teams: " + arena.getTeams().size());
        ctx.info(" - Start countdown: " + bedWars.startCountdownSeconds(arena) + " sec");
        ctx.info(" - Reset countdown: " + bedWars.endingSeconds(arena) + " sec");
        ctx.info(" - Team size: " + arena.get("teamSize", Integer.class, 1));
        ctx.info(" - Team selection: " + formatTeamSelectionInput(arena));
        ctx.info(" - Lobby region: " + formatRegion(arena.getLobbyRegion()));
        ctx.info(" - Drop items: " + bedWars.dropItems(arena).size() + " configured");
        ctx.info(" - Drop surfaces: " + bedWars.dropSurfaceMaterials(arena).size() + " configured");
        ctx.info(" - Lobby: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.get("arenaRegion", SCRegion.class)));
        for (MiniGameTeam team : arena.getTeams()) {
            ctx.info("   - Team " + team.getName() + ": spawn=" + formatLocation(team.getSpawn()) + ", bed=" + formatRegion(team.get("bedRegion", SCRegion.class)));
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
        if (bedWars.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = bedWars.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created BedWars arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        bedWars.deleteArena(arena.id());
        ctx.success("Deleted BedWars arena '" + arena.id() + "'.");
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
        }

        int joined = 0;
        int spectating = 0;
        int skipped = 0;
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            MiniGameArena existingArena = bedWars.minigame().findPlayer(targetPlayer);
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

        MiniGameArena arena = bedWars.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a BedWars arena.");
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
        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, bedWars.startCountdownSeconds(arena));
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
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, bedWars.startCountdownSeconds(arena));
            ctx.success("Arena '" + arena.id() + "' has been restarted.");
            return;
        }
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            bedWars.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!bedWars.reloadFromConfig()) {
            ctx.returnError("BedWars config could not be reloaded.");
        }
        ctx.success("BedWars config reloaded. Loaded " + bedWars.minigame().arenas().size() + " arenas.");
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
            bedWars.persistArenaEnabled(arena, true);
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
            bedWars.persistArenaEnabled(arena, false);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' is now disabled.");
    }

    private void commandAddTeam(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        String teamId = ctx.getArgLower(2);
        if (arena.numPlayers() > 0 || arena.numSpectators() > 0) {
            ctx.returnError("Arena '" + arena.id() + "' must be empty before editing teams.");
        }
        if (arena.getTeams().size() >= 8) {
            ctx.returnError("Arena '" + arena.id() + "' already has the maximum number of teams.");
        }
        if (arena.getTeam(teamId) != null) {
            ctx.returnError("Arena '" + arena.id() + "' already has team '" + teamId + "'.");
        }
        if (!bedWars.supportedTeamIds().contains(teamId)) {
            ctx.returnError("Unsupported BedWars team id '" + teamId + "'.");
        }

        arena.addTeam(teamId, StringUtil.beautify(teamId), arena.getLobbySpawn());
        bedWars.refreshArenaKits(arena);
        ctx.success("Added team '" + teamId + "' to arena '" + arena.id() + "'.");
    }

    private void commandRemoveTeam(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        MiniGameTeam team = requireTeam(ctx, arena, 2);
        if (arena.numPlayers() > 0 || arena.numSpectators() > 0) {
            ctx.returnError("Arena '" + arena.id() + "' must be empty before editing teams.");
        }
        if (arena.getTeams().size() <= 2) {
            ctx.returnError("Arena '" + arena.id() + "' must keep at least two teams.");
        }
        if (arena.getMaxPlayers() > (arena.getTeams().size() - 1) * arena.get("teamSize", Integer.class, 1)) {
            ctx.returnError("Reduce max players or team size before removing this team.");
        }

        arena.removeTeam(team.getName());
        arena.removeKit(team.getName());
        ctx.success("Removed team '" + team.getName() + "' from arena '" + arena.id() + "'.");
    }

    private void commandSet(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);

        switch (target) {
            case "lobby", "lobbyspawn", "lobby-spawn" -> {
                Player player = requirePlayer(ctx);
                arena.setLobbySpawn(player.getLocation());
                showLocationPreview(player, "lobbyspawn", player.getLocation());
                ctx.success("Lobby spawn updated for arena '" + arena.id() + "'.");
            }
            case "spectator" -> {
                Player player = requirePlayer(ctx);
                arena.setSpectatorSpawn(player.getLocation());
                showLocationPreview(player, "spectator", player.getLocation());
                ctx.success("Spectator spawn updated for arena '" + arena.id() + "'.");
            }
            case "arena" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                SCRegion regionCopy = selection.copy();
                arena.setRegion(regionCopy);
                arena.set("arenaRegion", regionCopy.copy());
                showRegionPreview(player, "arena", selection);
                ctx.success("Arena region updated for arena '" + arena.id() + "'.");
            }
            case "lobbyregion", "lobby-region" -> {
                Player player = requirePlayer(ctx);
                SCRegion selection = requireSelection(ctx, player);
                arena.setLobbyRegion(selection.copy());
                showRegionPreview(player, "lobbyregion", selection);
                ctx.success("Lobby region updated for arena '" + arena.id() + "'.");
            }
            case "teamspawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                Player player = requirePlayer(ctx);
                MiniGameTeam team = requireTeam(ctx, arena, 3);
                team.setSpawn(player.getLocation());
                showLocationPreview(player, "teamspawn-" + team.getName(), player.getLocation());
                ctx.success("Spawn updated for team '" + team.getName() + "' in arena '" + arena.id() + "'.");
            }
            case "teambed" -> {
                ctx.checkArgsSizeAtLeast(4);
                Player player = requirePlayer(ctx);
                MiniGameTeam team = requireTeam(ctx, arena, 3);
                SCRegion selection = requireSelection(ctx, player);
                team.set("bedRegion", selection.copy());
                showRegionPreview(player, "teambed-" + team.getName(), selection);
                ctx.success("Bed region updated for team '" + team.getName() + "' in arena '" + arena.id() + "'.");
            }
            case "teamsize" -> {
                ctx.checkArgsSizeAtLeast(4);
                if (arena.numPlayers() > 0) {
                    ctx.returnError("Arena '" + arena.id() + "' must be empty before changing team size.");
                }
                int teamSize = ctx.getArgAsInt(3, 1, 1, 8);
                arena.set("teamSize", teamSize);
                if (arena.getMaxPlayers() > arena.getTeams().size() * teamSize) {
                    arena.setMaxPlayers(arena.getTeams().size() * teamSize);
                }
                ctx.success("Team size set to " + teamSize + " for arena '" + arena.id() + "'.");
            }
            case "teamselection", "team-selection" -> {
                arena.setTeamSelectionInput(parseTeamSelectionInput(ctx, 3));
                ctx.success("Team selection input set to " + formatTeamSelectionInput(arena) + " for arena '" + arena.id() + "'.");
            }
            case "minplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int minPlayers = ctx.getArgAsInt(3, 2, 2, null);
                arena.setMinPlayers(minPlayers);
                ctx.success("Minimum players set to " + arena.getMinPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "maxplayers" -> {
                ctx.checkArgsSizeAtLeast(4);
                int maxCapacity = Math.max(2, arena.getTeams().size() * arena.get("teamSize", Integer.class, 1));
                int maxPlayers = ctx.getArgAsInt(3, arena.getMaxPlayers(), 2, maxCapacity);
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            case "teamname" -> {
                ctx.checkArgsSizeAtLeast(5);
                MiniGameTeam team = requireTeam(ctx, arena, 3);
                team.set("displayName", ctx.getArgsAsString(5));
                bedWars.refreshArenaKits(arena);
                ctx.success("Display name updated for team '" + team.getName() + "' in arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown BedWars set target '" + target + "'.");
        }
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);
        SCRegion region;

        switch (target) {
            case "arena" -> region = arena.get("arenaRegion", SCRegion.class);
            case "lobbyregion", "lobby-region" -> region = arena.getLobbyRegion();
            case "teambed" -> {
                ctx.checkArgsSizeAtLeast(4);
                MiniGameTeam team = requireTeam(ctx, arena, 3);
                region = team.get("bedRegion", SCRegion.class);
            }
            default -> {
                ctx.returnError("Unknown BedWars select target '" + target + "'.");
                return;
            }
        }

        if (region == null) {
            ctx.returnError("No stored region is configured for '" + target + "' in arena '" + arena.id() + "'.");
            return;
        }
        if (!player.getWorld().equals(region.getWorld())) {
            ctx.returnError("Move to world '" + region.getWorld().getName() + "' to preview that region.");
            return;
        }

        api.selections().setWorldEditSelection(player, region);
        showRegionPreview(player, "select-" + target, region);
        ctx.success("WorldEdit selection updated from arena '" + arena.id() + "' (" + target + ").");
    }

    private void commandShow(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        Player player = requirePlayer(ctx);
        MiniGameArena arena = requireArena(ctx);
        String target = ctx.getArgLower(2);
        Location location;

        switch (target) {
            case "lobby", "lobbyspawn", "lobby-spawn" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            case "teamspawn" -> {
                ctx.checkArgsSizeAtLeast(4);
                MiniGameTeam team = requireTeam(ctx, arena, 3);
                location = team.getSpawn();
            }
            default -> {
                ctx.returnError("Unknown BedWars show target '" + target + "'.");
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
        List<Material> dropItems = bedWars.dropItems(arena);

        ChatMenuUtil.render(
            ctx.getSender(),
            "BedWars Drop Items: " + arena.id(),
            "bedwars dropitems " + arena.id(),
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
                                .clickEvent(ClickEvent.runCommand("/bedwars removedropitem " + arena.id() + " " + index))
                                .hoverEvent(HoverEvent.showText(Component.text("Remove this drop item"))));
                    }
                    lines.add(line);
                }
                return lines;
            },
            "No BedWars drop items are configured for that arena."
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

        bedWars.dropItems(arena).add(heldItem.getType());
        ctx.success("Added drop item '" + describeDropItem(heldItem.getType()) + "' to arena '" + arena.id() + "'.");
    }

    private void commandRemoveDropItem(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        MiniGameArena arena = requireArena(ctx);
        List<Material> dropItems = bedWars.dropItems(arena);
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
        MiniGameArena arena = bedWars.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
            throw new IllegalStateException("Arena '" + arenaId + "' does not exist.");
        }
        return arena;
    }

    private MiniGameTeam requireTeam(CommandContext ctx, MiniGameArena arena, int index) {
        String teamId = ctx.getArgLower(index);
        MiniGameTeam team = arena.getTeam(teamId);
        if (team == null) {
            ctx.returnError("Arena '" + arena.id() + "' does not have team '" + teamId + "'.");
        }
        return team;
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
        MiniGameArena existingArena = bedWars.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    private MiniGameTeamSelectionInput parseTeamSelectionInput(@NotNull CommandContext ctx, int index) {
        if (ctx.numArgs() <= index) {
            ctx.returnError("Specify a team selection input: none, floor, or hotbar.");
        }
        if (ctx.numArgs() > index + 1) {
            ctx.returnError("Only one team selection input can be configured per arena.");
        }

        String token = ctx.getArgLower(index);
        if ("none".equals(token)) {
            return null;
        }

        MiniGameTeamSelectionInput input = MiniGameTeamSelectionInput.fromToken(token);
        if (input == null) {
            ctx.returnError("Unknown team selection input '" + token + "'. Supported inputs: none, floor, hotbar.");
        }
        return input;
    }

    private @NotNull String formatTeamSelectionInput(@NotNull MiniGameArena arena) {
        MiniGameTeamSelectionInput input = arena.getTeamSelectionInput();
        if (input == null) {
            return "auto";
        }
        return input.configToken();
    }

    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "bedwars-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "bedwars-preview:" + player.getUniqueId() + ":" + key;
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
