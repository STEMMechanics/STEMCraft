package dev.stemcraft.minigame.mobarena;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;

// TODO: [Overall] Make sure to get rid of the global "arena" region, using the "arena" zone instead.

public class MobArenaCommand {
    private static final long PREVIEW_TICKS = 100L;
    private final STEMCraftAPI api;
    private final MobArenaMiniGame mobArena;

    public MobArenaCommand(STEMCraftAPI api, MobArenaMiniGame mobArena) {
        this.api = api;
        this.mobArena = mobArena;
    }

    public void onEnable() {
        api.tabComplete().register("mobarena-arenas", (sender, args)->mobArena.minigame()
                .arenas()
                .stream()
                .map(MiniGameArena::id)
                .sorted()
                .toList());
        api.tabComplete().register("mobarena-mobs", (sender, args)-> Arrays.stream(EntityType.values())
                .map(Enum::toString)
                .sorted()
                .toList());
        api.tabComplete().register("mobarena-increment-type", (sender, args)-> Arrays.stream(MobArenaArenaRecord.SpawnerRecord.IncrementType.values())
                .map(Enum::toString)
                .sorted()
                .toList());

        // TODO: Add zones and spawner record adjustment here
        api.commands().create("mobarena")
                .permission("stemcraft.command.mobarena")
                .usage("/mobarena <list|info [arena]|create <arena> [world]|delete|join|joinall|spectate|leave|start|stop|restart|save|reload")
                .tabCompletion("list")
                .tabCompletion("list", "{int}")
                .tabCompletion("info")
                .tabCompletion("info", "{mobarena-arenas}")
                .tabCompletion("create")
                .tabCompletion("create", "")
                .tabCompletion("create", "", "{world}")
                .tabCompletion("delete", "{mobarena-arenas}")
                .tabCompletion("join", "{mobarena-arenas}", "{player}")
                .tabCompletion("joinall", "{mobarena-arenas}")
                .tabCompletion("spectate", "{mobarena-arenas}", "{player}")
                .tabCompletion("leave", "{player}")
                .tabCompletion("start", "{mobarena-arenas}")
                .tabCompletion("stop", "{mobarena-arenas}")
                .tabCompletion("restart", "{mobarena-arenas}")
                .tabCompletion("save", "{mobarena-arenas}")
                .tabCompletion("reload")
                .tabCompletion("validate", "{mobarena-arenas}")
                .tabCompletion("enable", "{mobarena-arenas}")
                .tabCompletion("disable", "{mobarena-arenas}")
                .tabCompletion("set", "{mobarena-arenas}", "arena")
                .tabCompletion("set", "{mobarena-arenas}", "lobby")
                .tabCompletion("set", "{mobarena-arenas}", "spectator")
                .tabCompletion("set", "{mobarena-arenas}", "minplayers")
                .tabCompletion("set", "{mobarena-arenas}", "maxplayers")
                .tabCompletion("set", "{mobarena-arenas}", "name")
                .tabCompletion("select", "{mobarena-arenas}", "arena")
                .tabCompletion("select", "{mobarena-arenas}", "lobby")
                .tabCompletion("select", "{mobarena-arenas}", "spectator")
                .tabCompletion("sel", "{mobarena-arenas}", "arena")
                .tabCompletion("sel", "{mobarena-arenas}", "lobby")
                .tabCompletion("sel", "{mobarena-arenas}", "spectator")
                .tabCompletion("show", "{mobarena-arenas}", "lobby")
                .tabCompletion("show", "{mobarena-arenas}", "spectator")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "add")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "remove", "")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "entityType", "{mobarena-mobs}")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "initialAmount", "")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "incrementAmount", "")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "incrementType", "{mobarena-increment-type}")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "initialWave", "")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "spawnZone", "")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "countTowardsMobCount", "true")
                .tabCompletion("spawnerconfig", "{mobarena-arenas}", "set", "", "countTowardsMobCount", "false")
                .tabCompletion("zone", "{mobarena-arenas}", "")
                .tabCompletion("zone", "{mobarena-arenas}", "delete", "")
                .executor((ignored, cmd, ctx) -> {
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
                        case "spawnerconfig" -> commandSpawnerConfig(ctx);
                        case "zone" -> commandZone(ctx);
                        default -> ctx.returnUsage();
                    }
                })
                .register(STEMCraft.getPlugin());
    }

    private void commandSpawnerConfig(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);
        switch (ctx.getArg(2)) {
            case "add" -> commandSpawnerConfigAdd(ctx);
            case "remove" -> commandSpawnerConfigRemove(ctx);
            case "set" -> commandSpawnerConfigSet(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void commandSpawnerConfigAdd(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);

        int newSpawnerConfigIndex = arena.get("spawner-configs.max", Integer.class);

        final String newSpawnerConfigPrefix = "spawner-configs." + newSpawnerConfigIndex + ".";

        arena.set(newSpawnerConfigPrefix + "entityType", EntityType.ZOMBIE);
        arena.set(newSpawnerConfigPrefix + "initialAmount", 1);
        arena.set(newSpawnerConfigPrefix + "incrementAmount", 1.0);
        arena.set(newSpawnerConfigPrefix + "incrementType", MobArenaArenaRecord.SpawnerRecord.IncrementType.Linear);
        arena.set(newSpawnerConfigPrefix + "initialWave", 1);
        arena.set(newSpawnerConfigPrefix + "spawnZone", "");
        arena.set(newSpawnerConfigPrefix + "countTowardsMobCount", true);

        arena.set("spawner-configs.max", newSpawnerConfigIndex + 1);

        ctx.returnSuccess("Created a new spawner config!");
    }

    private void commandSpawnerConfigRemove(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
    }

    private void commandSpawnerConfigSet(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);

        ctx.checkArgsSizeAtLeast(6);

        int spawnerIndex = ctx.getArgAsInt(3) - 1;
        String spawnerPropertyKey = ctx.getArg(4);
        String spawnerPropertyValue = ctx.getArg(5);

        if (spawnerIndex < 0 || spawnerIndex >= arena.get("spawner-configs.max", Integer.class)) {
            ctx.returnUsage();
        }

        switch (spawnerPropertyKey) {
            case "entityType":
                try {
                    EntityType newEntityType = EntityType.valueOf(spawnerPropertyValue);
                    arena.set("spawner-configs." + spawnerIndex + ".entityType", newEntityType);
                    ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s entity type to '" + spawnerPropertyValue + "'.");
                } catch (IllegalArgumentException e) {
                    ctx.returnError("'" + spawnerPropertyValue + "' is not a valid entity type.");
                }
                break;
            case "initialAmount":
                try {
                    int newInitialAmount = Integer.parseInt(spawnerPropertyValue);
                    arena.set("spawner-configs." + spawnerIndex + ".initialAmount", newInitialAmount);
                    ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s increment amount to '" + spawnerPropertyValue + "'.");
                } catch (NumberFormatException e) {
                    ctx.returnUsage();
                }
                break;
            case "incrementAmount":
                try {
                    double newIncrementAmount = Double.parseDouble(spawnerPropertyValue);
                    arena.set("spawner-configs." + spawnerIndex + ".incrementAmount", newIncrementAmount);
                    ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s increment amount to '" + spawnerPropertyValue + "'.");
                } catch (NumberFormatException e) {
                    ctx.returnUsage();
                }
                break;
            case "incrementType":
                try {
                    MobArenaArenaRecord.SpawnerRecord.IncrementType newIncrementType = MobArenaArenaRecord.SpawnerRecord.IncrementType.valueOf(spawnerPropertyValue);
                    arena.set("spawner-configs." + spawnerIndex + ".incrementType", newIncrementType);
                    ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s increment type to '" + spawnerPropertyValue + "'.");
                } catch (IllegalArgumentException e) {
                    ctx.returnError("'" + spawnerPropertyValue + "' is not a valid increment type.");
                }
                break;
            case "initialWave":
                try {
                    int newWaveAmount = Integer.parseInt(spawnerPropertyValue);
                    arena.set("spawner-configs." + spawnerIndex + ".initialWave", newWaveAmount);
                    ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s increment wave to '" + spawnerPropertyValue + "'.");
                } catch (NumberFormatException e) {
                    ctx.returnUsage();
                }
                break;
            case "spawnZone":
                arena.set("spawner-configs." + spawnerIndex + ".spawnZone", spawnerPropertyValue);
                ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s spawn zone to '" + spawnerPropertyValue + "'.");
                break;
            case "countTowardsMobCount":
                arena.set("spawner-configs." + spawnerIndex + ".countTowardsMobCount", Boolean.valueOf(spawnerPropertyValue));
                ctx.returnSuccess("Set spawner " + (spawnerIndex + 1) + "'s counting towards the mob count to '" + spawnerPropertyValue + "'.");
        }
    }

    private void commandZone(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3);

        String possibleOperation = ctx.getArg(2);
        if (Objects.equals(possibleOperation, "delete")) {
            commandZoneDelete(ctx);
        } else {
            commandZonePut(ctx);
        }
    }

    private void commandZoneDelete(CommandContext ctx) {

    }

    private void commandZonePut(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        String zoneName = ctx.getArg(2); // callee guarantees args size of 3.

        Player player = requirePlayer(ctx);
        SCRegion selection = requireSelection(ctx, player);
        Map<String, SCRegion> zones = arena.getMap("zones", String.class, SCRegion.class);

        SCRegion formerRegion = zones.put(zoneName, selection);
        if (formerRegion == null) {
            ctx.returnSuccess("'" + zoneName + "' has been added to the list of zones.");
        } else {
            ctx.returnSuccess("'" + zoneName + "' has been replaced with " + formatRegion(selection) + " (formerly " + formatRegion(formerRegion) + ").");
        }
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = mobArena.minigame().arenas().stream()
                .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                .toList();

        ChatMenuUtil.render(
                ctx.getSender(),
                api.locales().resolve(ctx.getSender(), "MOB_ARENA_LIST_TITLE"),
                "mobarena list",
                ctx.getArgAsInt(1, 1),
                arenas.size(),
                (start, count, isPlayer) -> {
                    List<Component> lines = new ArrayList<>();
                    int end = Math.min(start + count, arenas.size());
                    for (int i = start; i < end; i++) {
                        MiniGameArena arena = arenas.get(i);
                        Component line = Component.text(arena.id(), NamedTextColor.YELLOW)
                                .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                                .clickEvent(ClickEvent.runCommand("/mobarena info " + arena.id()))
                                .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena))
                                        .hoverEvent(HoverEvent.showText(Component.text("Show info for " + arena.id())))
                                        .clickEvent(ClickEvent.runCommand("/mobarena info " + arena.id())))
                                .append(Component.text(arena.getName(), NamedTextColor.GRAY))
                                .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA));

                        if (isPlayer) {
                            line = line
                                    .append(Component.text(" "))
                                    .append(Component.text("[Info]", NamedTextColor.GOLD)
                                            .clickEvent(ClickEvent.runCommand("/mobarena info " + arena.id()))
                                            .hoverEvent(HoverEvent.showText(Component.text("Show arena details"))));

                            if (arena.isJoinable()) {
                                line = line
                                        .append(Component.text(" "))
                                        .append(Component.text("[Join]", NamedTextColor.GREEN)
                                                .clickEvent(ClickEvent.runCommand("/mobarena join " + arena.id()))
                                                .hoverEvent(HoverEvent.showText(Component.text("Join this arena"))));
                            } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                                line = line
                                        .append(Component.text(" "))
                                        .append(Component.text("[Spectate]", NamedTextColor.AQUA)
                                                .clickEvent(ClickEvent.runCommand("/mobarena spectate " + arena.id()))
                                                .hoverEvent(HoverEvent.showText(Component.text("Spectate this arena"))));
                            } else {
                                line = line
                                        .append(Component.text(" "))
                                        .append(Component.text("[Validate]", NamedTextColor.BLUE)
                                                .clickEvent(ClickEvent.runCommand("/mobarena validate " + arena.id()))
                                                .hoverEvent(HoverEvent.showText(Component.text("Validate this arena"))));
                            }
                        }

                        lines.add(line);
                    }
                    return lines;
                },
                "MOBARENA_LIST_NONE"
        );
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
        ctx.info(" - Spawn Records:");
        printSpawnerConfigInfo(ctx, arena);
        ctx.info(" - Zones:");
        printZoneInfo(ctx, arena);
        if (validation.hasErrors()) {
            ctx.warn(" - Validation: failed");
            for (String error : validation.getErrors()) {
                ctx.warn("   - " + error);
            }
        } else {
            ctx.success(" - Validation: ok");
        }
    }

    private void printSpawnerConfigInfo(CommandContext ctx, MiniGameArena arena) {
        int maxSpawnerConfig = arena.get("spawner-configs.max", Integer.class);

        for (int i = 0; i < maxSpawnerConfig; i++) {
            final String spawnerConfigPrefix = "spawner-configs." + i + ".";
            ctx.info("   - " + (i + 1) + ":");
            ctx.info("     - Entity Type: " + arena.get(spawnerConfigPrefix + "entityType", EntityType.class).toString());
            ctx.info("     - Initial Amount: " + arena.get(spawnerConfigPrefix + "initialAmount", Integer.class).toString());
            ctx.info("     - Increment Amount: " + arena.get(spawnerConfigPrefix + "incrementAmount", Double.class).toString());
            ctx.info("     - Increment Type: " + arena.get(spawnerConfigPrefix + "incrementType", MobArenaArenaRecord.SpawnerRecord.IncrementType.class).toString());
            ctx.info("     - Spawn Zone: " + arena.get(spawnerConfigPrefix + "spawnZone", String.class));
            ctx.info("     - Count Towards Mob Count: " + arena.get(spawnerConfigPrefix + "countTowardsMobCount", Boolean.class).toString());
        }
    }

    private void printZoneInfo(CommandContext ctx, MiniGameArena arena) {
        Map<String, SCRegion> zones = arena.getMap("zones", String.class, SCRegion.class);
        if (zones == null) {
            return;
        }

        for (Map.Entry<String, SCRegion> entry : zones.entrySet()) {
            ctx.info("   - " + entry.getKey() + ": " + formatRegion(entry.getValue()));
        }
    }

    private void commandCreate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        String arenaId = ctx.getArg(1);
        if (mobArena.minigame().arena(arenaId) != null) {
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

        MiniGameArena arena = mobArena.createArena(arenaId, world);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' could not be created.");
            return;
        }
        ctx.success("Created Mob Arena arena '" + arenaId + "' in world '" + world.getName() + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        mobArena.deleteArena(arena.id());
        ctx.success("Deleted Mob Arena arena '" + arena.id() + "'.");
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
            MiniGameArena existingArena = mobArena.minigame().findPlayer(targetPlayer);
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

        MiniGameArena arena = mobArena.minigame().findPlayer(targetPlayer);
        if (arena == null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a Mob Arena arena.");
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

        arena.setStatus(MiniGameArena.ArenaStatus.STARTING, mobArena.startCountdownSeconds(arena));
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
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, mobArena.startCountdownSeconds(arena));
            ctx.success("Arena '" + arena.id() + "' has been restarted.");
            return;
        }
        ctx.success("Arena '" + arena.id() + "' has been reset.");
    }

    private void commandSave(CommandContext ctx) {
        MiniGameArena arena = requireArena(ctx);
        try {
            mobArena.saveArena(arena);
        } catch (MiniGameInvalidArenaConfigException exception) {
            ctx.returnError(exception.getMessage());
        }
        ctx.success("Arena '" + arena.id() + "' has been saved.");
    }

    private void commandReload(CommandContext ctx) {
        if (!mobArena.reloadFromConfig()) {
            ctx.returnError("Mob Arena config could not be reloaded.");
        }
        ctx.success("Mob Arena config reloaded. Loaded " + mobArena.minigame().arenas().size() + " arenas.");
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
            mobArena.persistArenaEnabled(arena, true);
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
            mobArena.persistArenaEnabled(arena, false);
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
                ensureArenaWorld(ctx, arena, selection, "Arena region");
                ensureArenaRegionAcceptsExistingGeometry(ctx, arena, selection);
                SCRegion regionCopy = selection.copy();
                arena.setRegion(regionCopy);
                arena.set("arenaRegion", regionCopy.copy());
                showRegionPreview(player, "set-arena", selection);
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
                arena.setMaxPlayers(maxPlayers);
                ctx.success("Maximum players set to " + arena.getMaxPlayers() + " for arena '" + arena.id() + "'.");
            }
            case "name" -> {
                ctx.checkArgsSizeAtLeast(4);
                arena.setName(ctx.getArgsAsString(4));
                ctx.success("Display name updated for arena '" + arena.id() + "'.");
            }
            default -> ctx.returnError("Unknown Mob Arena set target '" + target + "'.");
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
            case "arena" -> region = arena.getRegion();
            case "lobby" -> location = arena.getLobbySpawn();
            case "spectator" -> location = arena.getSpectatorSpawn();
            default -> {
                ctx.returnError("Unknown Mob Arena select target '" + target + "'.");
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
            default -> {
                ctx.returnError("Unknown Mob Arena show target '" + target + "'.");
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

    // TODO: Merge this into a static class, code taken from Bridge. - ProjectHSI
    private SCRegion requireSelection(CommandContext ctx, Player player) {
        SCRegion selection = api.selections().getWorldEditSelection(player);
        if (selection == null) {
            ctx.returnError("No WorldEdit selection found. Make a selection first.");
            throw new IllegalStateException("WorldEdit selection is required");
        }
        return selection;
    }

    // TODO: Merge this into a static class, code taken from Bridge. - ProjectHSI
    private Player requirePlayer(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("This subcommand must be run in-game.");
            throw new IllegalStateException("Player is required");
        }
        return player;
    }

    // TODO: Merge this into a static class, code taken from Bridge. - ProjectHSI
    private MiniGameArena requireArena(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(1 + 1);
        String arenaId = ctx.getArg(1);
        MiniGameArena arena = mobArena.minigame().arena(arenaId);
        if (arena == null) {
            ctx.returnError("Arena '" + arenaId + "' does not exist.");
            throw new IllegalStateException("Arena '" + arenaId + "' does not exist.");
        }
        return arena;
    }

    // TODO: Merge this into a static class, code taken from Bridge. - ProjectHSI
    private MiniGameArena requireArenaForInfo(CommandContext ctx) {
        if (ctx.numArgs() >= 2) {
            return requireArena(ctx);
        }

        List<MiniGameArena> arenas = mobArena.minigame().arenas();
        if (arenas.isEmpty()) {
            ctx.returnError("No Mob Arena arenas are loaded.");
            throw new IllegalStateException("No Mob Arena arenas are loaded.");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }

        ctx.returnError("Specify an arena id. Use /mobarena list to choose one.");
        throw new IllegalStateException("Specify an arena id.");
    }

    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private NamedTextColor statusColour(MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case IDLE, WAITING -> NamedTextColor.GREEN;
            case STARTING, PREPARATION, RUNNING, COOLDOWN, ENDING, RESETTING -> NamedTextColor.GOLD;
            case SETUP -> NamedTextColor.BLUE;
            case DISABLED, SHUTDOWN -> NamedTextColor.RED;
        };
    }

    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, Location location, String label) {
        if (location == null || location.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
            return;
        }
        if (!arena.world().equals(location.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private void ensureArenaWorld(CommandContext ctx, MiniGameArena arena, SCRegion region, String label) {
        if (region == null || region.getWorld() == null) {
            ctx.returnError(label + " is not set in a valid world.");
            return;
        }
        if (!arena.world().equals(region.getWorld())) {
            ctx.returnError(label + " must be in world '" + arena.world().getName() + "'.");
        }
    }

    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private void ensureRegionContained(CommandContext ctx, SCRegion child, SCRegion parent, String childLabel) {
        if (parent != null && !parent.contains(child)) {
            ctx.returnError(childLabel + " must be fully inside the " + "arena region" + ".");
        }
    }


    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private void ensureArenaRegionAcceptsExistingGeometry(CommandContext ctx, MiniGameArena arena, SCRegion arenaRegion) {
        List<String> errors = new ArrayList<>();

        // TODO: Add in arena reigon validation.

        if (!errors.isEmpty()) {
            ctx.warn("Cannot set arena region because it would exclude existing arena geometry:");
            for (String error : errors) {
                ctx.warn(" - " + error);
            }
            ctx.returnError("Expand the selection or re-set the listed items first.");
        }
    }

    // TODO: Merge this into a static class, code copied from Bridge impl. - ProjectHSI
    private void ensureNotInArena(CommandContext ctx, Player player) {
        MiniGameArena existingArena = mobArena.minigame().findPlayer(player);
        if (existingArena != null) {
            ctx.returnError("Player '" + player.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }
    }

    // TODO: Merge in this into a static class, code copied from Bridge impl. - ProjectHSI
    private void showRegionPreview(Player player, String key, SCRegion region) {
        String id = "bridge-preview:" + player.getUniqueId() + ":" + key + ":region";
        api.selections().highlightRegion(id, player, region, PREVIEW_TICKS);
    }

    // TODO: Merge in this into a static class, code copied from Bridge impl. - ProjectHSI
    private void showLocationPreview(Player player, String key, Location location) {
        String baseId = "bridge-preview:" + player.getUniqueId() + ":" + key;
        api.selections().highlightLocation(baseId + ":location", player, location, PREVIEW_TICKS);
        api.selections().flashBlock(baseId + ":block", player, location, PREVIEW_TICKS);
    }

    // TODO: Merge in this into a static class, code copied from Bridge impl. - ProjectHSI
    private String formatLocation(Location location) {
        if (location == null) {
            return "<unset>";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    // TODO: Merge in this into a static class, code copied from Bridge impl. - ProjectHSI
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
