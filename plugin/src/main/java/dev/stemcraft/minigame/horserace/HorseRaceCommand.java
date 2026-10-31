package dev.stemcraft.minigame.horserace;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.minigame.MiniGameArenaInfoSupport;
import dev.stemcraft.minigame.MiniGameCommandSupport;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

final class HorseRaceCommand {
    private final STEMCraftAPI api;
    private final HorseRaceMiniGame owner;

    HorseRaceCommand(STEMCraftAPI api, HorseRaceMiniGame owner) {
        this.api = api;
        this.owner = owner;
    }

    void register() {
        String id = HorseRaceMiniGame.NAMESPACE;
        api.tabComplete().register(id + "-arenas", (_, _, _) -> owner.game.arenas().stream().map(MiniGameArena::id).sorted().toList());
        var command = api.commands().create(id).usage("/" + id + " <command> [arena]");
        for (String action : List.of("list", "leave", "create", "info", "join", "joinall", "spectate", "start", "stop", "save",
                "validate", "enable", "disable", "delete", "addspawn", "removespawn", "addcheckpoint", "removecheckpoint")) {
            command.tabCompletion(action, "{" + id + "-arenas}");
        }
        command.tabCompletion("join", "{" + id + "-arenas}", "{players}");
        for (String key : List.of("arena", "lobby", "spectator", "minplayers", "maxplayers", "roundseconds", "countdown", "laps")) {
            command.tabCompletion("set", "{" + id + "-arenas}", key);
        }
        command.executor((_, _, ctx) -> execute(ctx)).register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        String action = ctx.numArgs() == 0 ? "list" : ctx.getArgLower(0);
        if (action.equals("list")) {
            List<MiniGameArena> arenas = owner.game.arenas();
            if (arenas.isEmpty()) {
                ctx.info("No Horse Racing arenas are loaded.");
                return;
            }
            for (MiniGameArena a : arenas)
                ctx.info(a.id() + " — " + a.getStatus() + " (" + a.numPlayers() + "/" + a.getMaxPlayers() + ")");
            return;
        }
        if (action.equals("leave")) {
            Player p = player(ctx);
            MiniGameArena a = owner.game.findPlayer(p);
            if (a != null) a.removeOccupant(p);
            return;
        }
        boolean publicAction = action.equals("join") || action.equals("spectate") || action.equals("info");
        if (!publicAction && !ctx.hasPermission("stemcraft.command." + HorseRaceMiniGame.NAMESPACE) && !ctx.getSender().isOp()) {
            ctx.returnError("Arena setup requires stemcraft.command." + HorseRaceMiniGame.NAMESPACE);
            return;
        }
        ctx.checkArgsSizeAtLeast(2);
        String id = ctx.getArg(1);
        if (action.equals("create")) {
            if (!id.matches("[a-z0-9_-]+")) {
                ctx.returnError("Use lowercase letters, numbers, underscores or hyphens for arena ids.");
                return;
            }
            if (owner.game.arena(id) != null) {
                ctx.returnError("Arena already exists.");
                return;
            }
            MiniGameArena a = owner.create(id, player(ctx).getWorld());
            owner.save(a);
            ctx.success("Created " + id + ".");
            return;
        }
        MiniGameArena a = owner.game.arena(id);
        if (a == null) {
            ctx.returnError("Unknown arena: " + id);
            return;
        }
        switch (action) {
            case "info" -> {
                showInfo(ctx, a);
                return;
            }
            case "validate" -> {
                var result = a.validate();
                if (!result.hasErrors()) ctx.success("Arena setup is valid.");
                else result.getErrors().forEach(ctx::warn);
                return;
            }
            case "join" -> {
                List<Player> targets = MiniGameCommandSupport.targets(ctx, 2);
                if (targets.isEmpty()) {
                    ctx.returnError("Player is required.");
                    return;
                }
                if (a.getStatus() == MiniGameArena.ArenaStatus.DISABLED || a.getStatus() == MiniGameArena.ArenaStatus.SETUP) {
                    ctx.returnError("Arena is not enabled.");
                    return;
                }
                if (!a.isJoinable()) {
                    ctx.returnError("Arena is not joinable. Use spectate.");
                    return;
                }
                MiniGameCommandSupport.JoinResult result = MiniGameCommandSupport.addPlayers(owner.game, a, targets, false);
                if (result.added() == 0) {
                    ctx.returnError("No players could be added to arena '" + a.id() + "'.");
                    return;
                }
                ctx.success("Arena '" + a.id() + "': joined " + result.joined() + ", skipped " + result.skipped() + ".");
                return;
            }
            case "joinall" -> {
                if (a.getStatus() == MiniGameArena.ArenaStatus.DISABLED || a.getStatus() == MiniGameArena.ArenaStatus.SETUP || !a.isJoinable()) {
                    ctx.returnError("Arena is not joinable right now.");
                    return;
                }
                MiniGameCommandSupport.JoinResult result = MiniGameCommandSupport.addOnlinePlayers(owner.game, a, false);
                if (result.added() == 0) {
                    ctx.returnError("No online players could be added to arena '" + a.id() + "'.");
                    return;
                }
                ctx.success("Arena '" + a.id() + "': joined " + result.joined() + ", skipped " + result.skipped() + ".");
                return;
            }
            case "spectate" -> {
                Player p = player(ctx);
                if (owner.game.findPlayer(p) != null) {
                    ctx.returnError("Leave your current arena first.");
                    return;
                }
                if (a.getStatus() == MiniGameArena.ArenaStatus.DISABLED || a.getStatus() == MiniGameArena.ArenaStatus.SETUP) {
                    ctx.returnError("Arena is not enabled.");
                    return;
                }
                if (action.equals("spectate")) a.addSpectator(p);
                else if (a.isJoinable()) a.addPlayer(p);
                else {
                    ctx.returnError("Arena is not joinable. Use spectate.");
                    return;
                }
                return;
            }
            case "stop", "disable" -> {
                a.setStatus(MiniGameArena.ArenaStatus.DISABLED, 0);
                a.removeAllOccupants();
                owner.save(a);
                ctx.success("Stopped and disabled arena; use enable when ready.");
                return;
            }
            case "save" -> {
                owner.save(a);
                ctx.success("Saved arena.");
                return;
            }
            case "start" -> {
                if (a.getStatus() != MiniGameArena.ArenaStatus.WAITING || a.numPlayers() == 0) {
                    ctx.returnError("Enable the arena and join before starting.");
                    return;
                }
                if (a.validate().hasErrors()) {
                    a.validate().getErrors().forEach(ctx::warn);
                    return;
                }
                // Explicit start permits a single tester, regardless of automatic minimum.
                a.setStatus(MiniGameArena.ArenaStatus.STARTING, owner.settings(a).countdown);
                return;
            }
            default -> {
            }
        }
        if (!a.getOccupants().isEmpty() || (a.getStatus() != MiniGameArena.ArenaStatus.SETUP && a.getStatus() != MiniGameArena.ArenaStatus.DISABLED)) {
            ctx.returnError("Stop the arena before editing setup.");
            return;
        }
        if (action.equals("enable")) {
            var result = a.validate();
            if (result.hasErrors()) {
                result.getErrors().forEach(ctx::warn);
                return;
            }
            a.setStatus(MiniGameArena.ArenaStatus.WAITING, 0);
            owner.save(a);
            ctx.success("Arena enabled.");
            return;
        }
        if (action.equals("delete")) {
            owner.delete(a);
            ctx.success("Arena definition deleted.");
            return;
        }
        HorseRaceArenaSettings d = owner.settings(a);
        switch (action) {
            case "addspawn" -> d.spawns.add(location(ctx, a));
            case "addcheckpoint" -> d.checkpoints.add(selection(ctx, a));
            case "removespawn", "removecheckpoint" -> {
                List<?> entries = action.equals("removespawn") ? d.spawns : d.checkpoints;
                ctx.checkArgsSizeAtLeast(3);
                if (entries.isEmpty()) {
                    ctx.returnError("No entries to remove.");
                    return;
                }
                entries.remove(ctx.getArgAsInt(2, 1, 1, entries.size()) - 1);
            }
            case "set" -> {
                ctx.checkArgsSizeAtLeast(3);
                switch (ctx.getArgLower(2)) {
                    case "arena" -> a.setRegion(selection(ctx, a));
                    case "lobby" -> a.setLobbySpawn(location(ctx, a));
                    case "spectator" -> a.setSpectatorSpawn(location(ctx, a));
                    default -> {
                        ctx.checkArgsSizeAtLeast(4);
                        int n = ctx.getArgAsInt(3, 1, 1, 3600);
                        switch (ctx.getArgLower(2)) {
                            case "minplayers" -> a.setMinPlayers(n);
                            case "maxplayers" -> a.setMaxPlayers(n);
                            case "roundseconds" -> d.roundSeconds = n;
                            case "countdown" -> d.countdown = n;
                            case "laps" -> d.laps = n;
                            default -> {
                                ctx.returnError("Unknown setting.");
                                return;
                            }
                        }
                    }
                }
            }
            default -> {
                ctx.returnError("Unknown command.");
                return;
            }
        }
        owner.save(a);
        ctx.success("Setup saved. Use validate to check remaining steps.");
    }

    private void showInfo(CommandContext ctx, MiniGameArena a) {
        HorseRaceArenaSettings d = owner.settings(a);
        MiniGameArenaInfoSupport.common(ctx, a, d.countdown, d.roundSeconds);
        MiniGameArenaInfoSupport.locations(ctx, "Starting spawns", d.spawns);
        ctx.info(" - Laps: " + d.laps);
        MiniGameArenaInfoSupport.regions(ctx, "Checkpoints (last is the finish)", d.checkpoints);
    }

    private Player player(CommandContext ctx) {
        Player p = ctx.asPlayer();
        if (p == null) {
            ctx.returnError("Run this command in-game.");
            throw new IllegalArgumentException("Player required");
        }
        return p;
    }

    private Location location(CommandContext ctx, MiniGameArena a) {
        Location l = player(ctx).getLocation().clone();
        if (!a.world().equals(l.getWorld())) {
            ctx.returnError("Move to the arena world.");
            throw new IllegalArgumentException("Wrong world");
        }
        return l;
    }

    private SCRegion selection(CommandContext ctx, MiniGameArena a) {
        SCRegion r = api.selections().getWorldEditSelection(player(ctx));
        if (r == null || !a.world().equals(r.getWorld())) {
            ctx.returnError("Make a WorldEdit selection in the arena world first.");
            throw new IllegalArgumentException("Selection required");
        }
        return r;
    }
}
