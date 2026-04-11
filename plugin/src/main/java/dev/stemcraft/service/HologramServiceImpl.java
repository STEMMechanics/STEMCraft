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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.service.playerstats.PlayerStatValue;
import dev.stemcraft.api.service.playerstats.PlayerStatsRecord;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.hologram.HologramTypeHandler;
import dev.stemcraft.api.event.world.WorldDeleteEvent;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Implementation of the HologramService for managing holograms in the game.
 */
public class HologramServiceImpl extends BaseService implements HologramService {
    private static final double LINE_SPACING = 0.25;
    private static final String STAT_LEADERBOARD_TYPE = "stat_leaderboard";
    private static final int DEFAULT_STAT_LEADERBOARD_LIMIT = 10;
    private static final DecimalFormat STAT_VALUE_FORMAT = new DecimalFormat("0.##");

    private final Map<Integer, HologramData> holograms = new HashMap<>();
    private final Map<Integer, List<UUID>> entitiesById = new HashMap<>();
    private final Map<String, HologramTypeHandler> handlers = new HashMap<>();

    /**
     * Constructor for HologramServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public HologramServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initialize the hologram manager.
     */
    public void onEnable() {
        ensureStorage();

        registerType("", new HologramTypeHandler() {
            /** {@inheritDoc} */
            @Override
            public List<String> list(@NonNull String type) {
                return null;
            }

            /** {@inheritDoc} */
            @Override
            public @NonNull List<String> lines(@NonNull String type, @NonNull String context, int id, @NonNull List<String> data) {
                return data;
            }
        });

        registerType(STAT_LEADERBOARD_TYPE, new HologramTypeHandler() {
            @Override
            public List<String> list(@NonNull String type) {
                return api.playerStats().getDefinitions().stream()
                    .map(PlayerStatDefinition::key)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            }

            @Override
            public @NonNull List<String> lines(@NonNull String type, @NonNull String context, int id, @NonNull List<String> data) {
                return renderStatLeaderboard(context, data);
            }
        });

        Bukkit.getWorlds().forEach(world -> loadHolograms(null, world.getName()));

        api.events().register(WorldLoadEvent.class, event -> {
            World world = event.getWorld();
            loadHolograms(null, world.getName());
        });

        api.events().register(WorldUnloadEvent.class, event -> {
            String worldName = event.getWorld().getName();

            for (HologramData data : holograms.values()) {
                if (!data.location.getWorld().getName().equals(worldName)) {
                    continue;
                }

                despawn(data.id);
            }
        });

        api.events().register(WorldDeleteEvent.class, event -> {
            String worldName = event.getWorldName();

            for (HologramData data : holograms.values()) {
                if (!data.location.getWorld().getName().equals(worldName)) {
                    continue;
                }

                delete(data.id);
            }
        });

        api.events().register(ChunkLoadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            World world = chunk.getWorld();

            for (HologramData data : holograms.values()) {
                Location loc = data.location;
                if (loc == null || loc.getWorld() == null) continue;
                if (!loc.getWorld().equals(world)) continue;
                if (!chunk.equals(loc.getChunk())) continue;

                // Only respawn if not already spawned
                if (!entitiesById.containsKey(data.id)) {
                    spawn(data);
                }
            }
        });

        api.events().register(ChunkUnloadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            World world = chunk.getWorld();

            for (HologramData data : holograms.values()) {
                Location loc = data.location;
                if (loc == null || loc.getWorld() == null) continue;
                if (!loc.getWorld().equals(world)) continue;
                if (!chunk.equals(loc.getChunk())) continue;

                despawn(data.id); // clears entitiesById
            }
        });


        api.tabComplete().register("hologram_type", (player, args) ->
                new ArrayList<>(handlers.keySet())
        );

        api.tabComplete().register("hologram_context", (player, args) -> {
            if (args.length < 2) {
                return Collections.emptyList();
            }
            String type = StringUtil.slugify(args[args.length - 1]);
            HologramTypeHandler handler = handlers.get(type);
            if (handler == null) {
                return Collections.emptyList();
            }
            List<String> contexts = handler.list(type);
            return Objects.requireNonNullElse(contexts, Collections.emptyList());
        });

        api.commands().create("hologram")
                .description("HOLOGRAM_DESCRIPTION")
                .usage("HOLOGRAM_USAGE")
                .permission("stemcraft.command.hologram")

                .tabCompletion("create", "{hologram_type}", "{hologram_context}")
                .tabCompletion("closest", "{range}")
                .tabCompletion("delete", "{id}")
                .tabCompletion("deleteall", "{hologram_type}", "{hologram_context}")

                .executor((api, cmd, ctx) -> {

                    var args = ctx.args();
                    if (args.isEmpty()) {
                        cmd.error(ctx.getSender(), "HOLOGRAM_USAGE");
                        return;
                    }

                    var action = args.getFirst().toLowerCase();

                    switch (action) {

                        case "create" -> subCommandCreate(api, cmd, ctx);
                        case "closest" -> subCommandClosest(api, cmd, ctx);
                        case "delete" -> subCommandDelete(api, cmd, ctx);
                        case "deleteall" -> subCommandDeleteAll(api, cmd, ctx);
                        default -> cmd.error(ctx.getSender(), "HOLOGRAM_USAGE");
                    }

                })
                .register(plugin);
    }

    /**
     * Shut down the hologram manager.
     */
    public void onDisable() {
        despawnAll();
        saveAll();
    }

    /**
     * Subcommand handler for "hologram create".
     *
     * @param api The STEMCraft API instance.
     * @param cmd The command being executed.
     * @param ctx The command context.
     */
    private void subCommandCreate(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        // hologram create <type> <text...>
        ctx.checkNotConsole();
        ctx.checkArgsSizeAtLeast(2, "HOLOGRAM_USAGE_CREATE");

        String type = StringUtil.slugify(ctx.getArg(1));
        if(!handlers.containsKey(type)) {
            ctx.returnError("HOLOGRAM_TYPE_INVALID", "type", type);
        }

        List<String> data;
        String context = null;

        List<String> contexts = handlers.get(type).list(type);
        if(contexts == null || contexts.isEmpty()) {
            data = parseLines(ctx.getArgsAsString(2));
        } else {
            ctx.checkArgsSizeAtLeast(3, "HOLOGRAM_USAGE_CREATE");
            context = ctx.getArg(2);
            if(!contexts.contains(context)) {
                ctx.returnError("HOLOGRAM_CONTEXT_INVALID", "context", context);
            }

            data = parseLines(ctx.getArgsAsString(3));
        }

        Location location = ctx.asPlayer().getLocation().add(0, 2.0, 0);
        int id = create(type, context, location, data);

        ctx.returnSuccess("HOLOGRAM_CREATE_SUCCESS", "id", id);
    }

    /**
     * Subcommand handler for "hologram closest".
     *
     * @param api The STEMCraft API instance.
     * @param cmd The command being executed.
     * @param ctx The command context.
     */
    private void subCommandClosest(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        // hologram closest [range]
        ctx.checkNotConsole();
        Player player = ctx.asPlayer();

        int range = 20;
        if(ctx.numArgs() >= 2) {
            ctx.checkArgIsInt(1, "HOLOGRAM_RANGE_INVALID", "range", ctx.getArg(1));
            range = Math.clamp(ctx.getArgAsInt(1), 1, 50);
        }

        int closestId = closest(player.getLocation(), range);
        if (closestId == -1) {
            ctx.returnError("HOLOGRAM_CLOSEST_NONE", "range", String.valueOf(range));
        } else {
            ctx.returnSuccess("HOLOGRAM_CLOSEST_FOUND", "id", String.valueOf(closestId));
        }
    }

    /**
     * Subcommand handler for "hologram delete".
     *
     * @param api The STEMCraft API instance.
     * @param cmd The command being executed.
     * @param ctx The command context.
     */
    private void subCommandDelete(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        // hologram delete <id>
        ctx.checkArgsSizeAtLeast(2, "HOLOGRAM_USAGE_DELETE");
        ctx.checkArgIsInt(1, "HOLOGRAM_ID_INVALID", "id", ctx.getArg(1));

        int id = ctx.getArgAsInt(1);
        HologramData data = holograms.get(id);
        if(data == null) {
            ctx.returnError("HOLOGRAM_ID_INVALID", "id", String.valueOf(id));
        }

        delete(id);
        ctx.returnSuccess("HOLOGRAM_DELETE_SUCCESS", "id", String.valueOf(id));
    }

    /**
     * Subcommand handler for "hologram deleteall".
     *
     * @param api The STEMCraft API instance.
     * @param cmd The command being executed.
     * @param ctx The command context.
     */
    private void subCommandDeleteAll(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        // hologram deleteall <type> [context]
        ctx.checkArgsSizeAtLeast(2, "HOLOGRAM_USAGE_DELETEALL");

        String type = StringUtil.slugify(ctx.getArg(1));
        if(!handlers.containsKey(type)) {
            ctx.returnError("HOLOGRAM_TYPE_INVALID", "type", type);
        }

        String context = null;
        List<String> contexts = handlers.get(type).list(type);
        if(contexts != null && !contexts.isEmpty()) {
            ctx.checkArgsSizeAtLeast(3, "HOLOGRAM_USAGE_DELETEALL");
            context = ctx.getArg(2);
            if(!contexts.contains(context)) {
                ctx.returnError("HOLOGRAM_CONTEXT_INVALID", "context", context);
            }
        }

        delete(type, context);
        ctx.returnSuccess("HOLOGRAM_DELETEALL_SUCCESS", "type", type);
    }

    /**
     * Register a new hologram type handler.
     *
     * @param type The hologram type.
     * @param handler The handler for the hologram type.
     */
    @Override
    public void registerType(@NotNull String type, @NotNull HologramTypeHandler handler) {

        String slug = StringUtil.slugify(type);
        handlers.put(slug, handler);

        Bukkit.getWorlds().forEach(world -> loadHolograms(slug, world.getName()));
    }

    /**
     * Create a new hologram.
     *
     * @param type The hologram type.
     * @param context The hologram context.
     * @param location The location of the hologram.
     * @param data The data for the hologram.
     * @return The ID of the created hologram.
     */
    @Override
    public int create(@NotNull String type, @Nullable String context, @NotNull Location location, @NotNull List<String> data) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException(api.locales().resolve("HOLOGRAM_LOCATION_INVALID"));
        }

        String slug = StringUtil.slugify(type);

        int nextId = holograms.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
        HologramData hologram = new HologramData(
                nextId,
                slug,
                context,
                location,
                new ArrayList<>(data)
        );

        holograms.put(nextId, hologram);
        save(nextId);
        spawn(hologram);

        return nextId;
    }

    /**
     * Update an existing hologram.
     *
     * @param id The ID of the hologram to update.
     * @param data The new data for the hologram.
     */
    @Override
    public void update(int id, @NotNull List<String> data) {
        HologramData hologram = holograms.get(id);
        if (hologram == null) {
            return;
        }

        hologram.data = new ArrayList<>(data);
        HologramTypeHandler handler = handlers.get(hologram.type);
        if (handler != null) {
            List<String> resolvedLines = handler.lines(hologram.type, hologram.context, hologram.id, data);
            hologram.data = new ArrayList<>(resolvedLines);
        }

        save(id);
        respawn(hologram);
    }

    /**
     * Update holograms by type and context.
     *
     * @param type The hologram type to update.
     * @param context The hologram context to update (or null for all contexts).
     */
    @Override
    public void update(@NotNull String type, @Nullable String context) {
        for(HologramData data : holograms.values()) {
            if(!data.type.equals(type)) {
                continue;
            }
            if(context != null && !context.equals(data.context)) {
                continue;
            }

            HologramTypeHandler handler = handlers.get(data.type);
            if(handler == null) {
                continue;
            }

            List<String> resolvedLines = handler.lines(data.type, data.context, data.id, data.data);
            data.data = new ArrayList<>(resolvedLines);

            save(data.id);
            respawn(data);
        }
    }

    /**
     * Move an existing hologram to a new location.
     *
     * @param id The ID of the hologram to move.
     * @param newLocation The new location for the hologram.
     */
    @Override
    public void move(int id, @NotNull Location newLocation) {
        if (newLocation.getWorld() == null) {
            return;
        }

        HologramData data = holograms.get(id);
        if (data == null) {
            return;
        }

        data.location = newLocation;
        save(id);
        respawn(data);
    }

    /**
     * Delete a hologram by ID.
     *
     * @param id The ID of the hologram to delete.
     */
    @Override
    public void delete(int id) {
        HologramData data = holograms.remove(id);
        if (data == null) {
            return;
        }

        despawn(id);
        api.database().update("DELETE FROM holograms WHERE id = ?", ps -> ps.setInt(1, id));
    }

    /**
     * Delete holograms by type and context.
     *
     * @param type The hologram type to delete.
     * @param context The hologram context to delete (or null for all contexts).
     */
    @Override
    public void delete(@NotNull String type, @Nullable String context) {
        List<Integer> toDelete = new ArrayList<>();
        Iterator<Map.Entry<Integer, HologramData>> iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, HologramData> entry = iterator.next();
            HologramData data = entry.getValue();
            if (!data.type.equals(type)) {
                continue;
            }
            if (context != null && !context.equals(data.context)) {
                continue;
            }
            despawn(data.id);
            toDelete.add(data.id);
            iterator.remove();
        }
        for (Integer id : toDelete) {
            api.database().update("DELETE FROM holograms WHERE id = ?", ps -> ps.setInt(1, id));
        }
    }

    /**
     * Save all holograms to the configuration.
     */
    @Override
    public int closest(@NotNull Location loc, int range) {
        if (loc.getWorld() == null) {
            return -1;
        }

        String worldName = loc.getWorld().getName();
        double maxDistSq = range * (double) range;

        int closestId = -1;
        double closestDist = Double.MAX_VALUE;

        for (HologramData data : holograms.values()) {
            Location hLoc = data.location;
            if (hLoc == null || hLoc.getWorld() == null) {
                continue;
            }
            if (!hLoc.getWorld().getName().equals(worldName)) {
                continue;
            }

            double distSq = hLoc.distanceSquared(loc);

            if (distSq <= maxDistSq && distSq < closestDist) {
                closestDist = distSq;
                closestId = data.id;
            }
        }

        return closestId;
    }

    /**
     * Despawn all holograms.
     */
    @Override
    public void despawnAll() {
        for (List<UUID> uuids : entitiesById.values()) {
            for (UUID uuid : uuids) {
                Entity e = findEntity(uuid);
                if (e != null && e.getType() == EntityType.ARMOR_STAND) {
                    e.remove();
                }
            }
        }
        entitiesById.clear();
    }

    /**
     * Save a hologram or all holograms to the configuration.
     */
    @Override
    public void save(@Nullable Integer id) {
        if(id == null) {
            for (HologramData data : holograms.values()) {
                upsertHologram(data);
            }
        } else {
            HologramData data = holograms.get(id);
            if (data == null) {
                return;
            }
            upsertHologram(data);
        }
    }

    /**
     * Spawn all holograms.
     */
    private void spawnAll() {
        for (HologramData data : holograms.values()) {
            spawn(data);
        }
    }

    /**
     * Respawn a hologram by first despawning and then spawning it again.
     *
     * @param data The hologram data to respawn.
     */
    private void respawn(HologramData data) {
        despawn(data.id);
        spawn(data);
    }

    /**
     * Spawn a hologram in the game world.
     *
     * @param hologram The hologram data to spawn.
     */
    private void spawn(HologramData hologram) {
        HologramTypeHandler handler = handlers.get(hologram.type);
        if (handler == null) {
            return;
        }

        List<String> lines = handler.lines(hologram.type, hologram.context, hologram.id, hologram.data);
        if (lines.isEmpty()) {
            return;
        }

        World world = hologram.location.getWorld();
        if (world == null) {
            return;
        }

        List<UUID> uuids = new ArrayList<>();
        double yOffset = 0;

        for (String line : lines) {
            Location lineLoc = hologram.location.clone().add(0, yOffset, 0);
            String renderedLine = api.placeholders().apply(line);
            ArmorStand stand = world.spawn(lineLoc, ArmorStand.class, entity -> {
                entity.setMarker(true);
                entity.setInvisible(true);
                entity.setGravity(false);
                entity.setSmall(true);
                entity.setCustomNameVisible(true);
                entity.customName(TextUtil.colourise(renderedLine));
                entity.setPersistent(false);
            });
            uuids.add(stand.getUniqueId());
            yOffset -= LINE_SPACING;
        }

        entitiesById.put(hologram.id, uuids);
    }

    /**
     * Despawn a hologram from the game world.
     *
     * @param id The ID of the hologram to despawn.
     */
    private void despawn(int id) {
        List<UUID> uuids = entitiesById.remove(id);
        if (uuids == null) {
            return;
        }
        for (UUID uuid : uuids) {
            Entity e = findEntity(uuid);
            if (e != null && e.getType() == EntityType.ARMOR_STAND) {
                e.remove();
            }
        }
    }

    /**
     * Find an entity by its UUID across all worlds.
     *
     * @param uuid The UUID of the entity to find.
     * @return The found entity, or null if not found.
     */
    private Entity findEntity(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity e = world.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    /**
     * Internal class to hold hologram data.
     */
    private static class HologramData {
        final int id;
        final String type;
        final String context;
        Location location;
        List<String> data;

        HologramData(int id, String type, String context, Location location, List<String> data) {
            this.id = id;
            this.type = type;
            this.context = context;
            this.location = location;
            this.data = data;
        }
    }

    /**
     * Parse lines from a string separated by '|'.
     *
     * @param str The input string.
     * @return A list of parsed lines.
     */
    private List<String> parseLines(String str) {
        String[] split = str.split("\\|", -1);

        List<String> lines = new ArrayList<>();
        for (String s : split) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private @NotNull List<String> renderStatLeaderboard(@NotNull String context, @NotNull List<String> data) {
        String statKey = context.trim();
        if (statKey.isBlank()) {
            return List.of("<red>Missing stat key</red>");
        }

        PlayerStatDefinition definition = api.playerStats().getDefinition(statKey);
        if (definition == null) {
            return List.of("<red>Unknown stat:</red> <yellow>" + statKey + "</yellow>");
        }

        StatLeaderboardOptions options = parseStatLeaderboardOptions(definition, data);
        List<PlayerStatsRecord> records = api.playerStats().top(definition.key(), options.limit(), options.period());

        List<String> lines = new ArrayList<>();
        lines.add(options.title());
        if (records.isEmpty()) {
            lines.add("<gray>No recorded values yet.</gray>");
            return lines;
        }

        int rank = 1;
        for (PlayerStatsRecord record : records) {
            PlayerStatValue value = record.stats().isEmpty() ? null : record.stats().getFirst();
            if (value == null) {
                continue;
            }

            String playerName = record.username() == null || record.username().isBlank()
                ? record.uuid().toString()
                : record.username();

            String template = rank == 1 && options.firstLine() != null
                ? options.firstLine()
                : options.line();

            lines.add(template
                .replace("{rank}", Integer.toString(rank))
                .replace("{player}", playerName)
                .replace("{value}", formatStatValue(value.value()))
                .replace("{stat}", definition.title())
                .replace("{period}", options.periodLabel()));
            rank++;
        }
        return lines;
    }

    private @NotNull StatLeaderboardOptions parseStatLeaderboardOptions(@NotNull PlayerStatDefinition definition, @NotNull List<String> data) {
        String title = "<gold>" + definition.title() + "</gold>";
        int limit = DEFAULT_STAT_LEADERBOARD_LIMIT;
        String period = "all";
        String line = "<yellow>{rank}.</yellow> <aqua>{player}</aqua> <dark_gray>-</dark_gray> <gold>{value}</gold>";
        String firstLine = "<gold>{rank}.</gold> <yellow>{player}</yellow> <dark_gray>-</dark_gray> <gold>{value}</gold>";

        for (String rawLine : data) {
            int separator = rawLine.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = rawLine.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = rawLine.substring(separator + 1).trim();
            if (value.isBlank()) {
                continue;
            }

            switch (key) {
                case "title" -> title = value;
                case "period" -> period = value;
                case "line" -> line = value;
                case "first", "first-line", "top-line" -> firstLine = value;
                case "limit" -> {
                    try {
                        limit = Math.max(1, Math.min(Integer.parseInt(value), 100));
                    } catch (NumberFormatException ignored) {
                        // Ignore invalid custom limit and keep default.
                    }
                }
                default -> {
                }
            }
        }

        String normalizedPeriod = normalizeLeaderboardPeriod(period);
        return new StatLeaderboardOptions(title, line, firstLine, limit, normalizedPeriod, periodLabel(normalizedPeriod));
    }

    private @NotNull String normalizeLeaderboardPeriod(@Nullable String period) {
        if (period == null || period.isBlank()) {
            return "all";
        }
        String normalized = period.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "day", "week", "month", "year", "all" -> normalized;
            default -> "all";
        };
    }

    private @NotNull String periodLabel(@NotNull String period) {
        return switch (period) {
            case "day" -> "Today";
            case "week" -> "This Week";
            case "month" -> "This Month";
            case "year" -> "This Year";
            default -> "All Time";
        };
    }

    private @NotNull String formatStatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001d) {
            return Long.toString(Math.round(value));
        }
        return STAT_VALUE_FORMAT.format(value);
    }

    private record StatLeaderboardOptions(
        @NotNull String title,
        @NotNull String line,
        @Nullable String firstLine,
        int limit,
        @NotNull String period,
        @NotNull String periodLabel
    ) {}

    // ===== internals =====

    private void loadHolograms(String limitType, String worldName) {
        World world = Bukkit.getWorld(worldName);

        if(world == null) {
            return;
        }

        String sql = "SELECT id, type, context, x, y, z, data_yaml FROM holograms WHERE world = ?";
        if (limitType != null) {
            sql += " AND type = ?";
        }

        api.database().queryEach(
            sql,
            ps -> {
                ps.setString(1, worldName);
                if (limitType != null) {
                    ps.setString(2, limitType);
                }
            },
            rs -> {
                int id = rs.getInt("id");
                String type = Objects.requireNonNullElse(rs.getString("type"), "").toLowerCase(Locale.ROOT);
                if (!handlers.containsKey(type)) {
                    return;
                }

                Location loc = new Location(
                    world,
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z")
                );
                List<String> data = dataFromYaml(rs.getString("data_yaml"));

                HologramData dataObj = new HologramData(
                    id,
                    type,
                    rs.getString("context"),
                    loc,
                    new ArrayList<>(data)
                );
                holograms.put(id, dataObj);
            }
        );
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS holograms (" +
            "id INTEGER PRIMARY KEY," +
            "world TEXT NOT NULL," +
            "type TEXT NOT NULL," +
            "context TEXT," +
            "x REAL NOT NULL," +
            "y REAL NOT NULL," +
            "z REAL NOT NULL," +
            "data_yaml TEXT NOT NULL," +
            "updated_at INTEGER NOT NULL" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS holograms_world ON holograms(world);");
        api.database().execute("CREATE INDEX IF NOT EXISTS holograms_type ON holograms(type);");
    }

    private void upsertHologram(HologramData data) {
        World world = data.location.getWorld();
        if (world == null) {
            return;
        }
        String dataYaml = dataToYaml(data.data);
        api.database().update(
            "INSERT INTO holograms (id, world, type, context, x, y, z, data_yaml, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(id) DO UPDATE SET world = excluded.world, type = excluded.type, context = excluded.context, " +
            "x = excluded.x, y = excluded.y, z = excluded.z, data_yaml = excluded.data_yaml, updated_at = excluded.updated_at",
            ps -> {
                ps.setInt(1, data.id);
                ps.setString(2, world.getName());
                ps.setString(3, data.type);
                ps.setString(4, data.context);
                ps.setDouble(5, data.location.getX());
                ps.setDouble(6, data.location.getY());
                ps.setDouble(7, data.location.getZ());
                ps.setString(8, dataYaml);
                ps.setLong(9, System.currentTimeMillis());
            }
        );
    }

    private String dataToYaml(List<String> lines) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("lines", lines);
        return cfg.saveToString();
    }

    private List<String> dataFromYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return new ArrayList<>();
        }

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
            return new ArrayList<>(cfg.getStringList("lines"));
        } catch (Exception ignored) {
            try {
                return new ArrayList<>(Arrays.asList(yaml.split("\\R")));
            } catch (Exception ignoredToo) {
                return new ArrayList<>();
            }
        }
    }
}
