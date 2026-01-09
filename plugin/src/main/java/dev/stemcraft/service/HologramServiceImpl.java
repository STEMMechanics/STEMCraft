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
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
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
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Implementation of the HologramService for managing holograms in the game.
 */
public class HologramServiceImpl extends BaseService implements HologramService {
    private static final double LINE_SPACING = 0.25;

    private final Map<Integer, HologramData> holograms = new HashMap<>();
    private final Map<Integer, List<UUID>> entitiesById = new HashMap<>();
    private final Map<String, HologramTypeHandler> handlers = new HashMap<>();

    private ConfigFile config;

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
        this.config = api.config().load("holograms.yml");

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
        ctx.checkArgsSizeAtLeast(3, "HOLOGRAM_USAGE_CREATE");

        String type = ctx.getArg(2);
        if(!handlers.containsKey(type)) {
            ctx.returnError("HOLOGRAM_TYPE_INVALID", "type", type);
        }

        List<String> data;
        String context = null;

        List<String> contexts = handlers.get(type).list(type);
        if(contexts == null || contexts.isEmpty()) {
            data = parseLines(ctx.getArgsAsString(3));
        } else {
            context = ctx.getArg(3);
            if(!contexts.contains(context)) {
                ctx.returnError("HOLOGRAM_CONTEXT_INVALID", "context", context);
            }

            data = parseLines(ctx.getArgsAsString(4));
        }

        Location location = ctx.getSenderAsPlayer().getLocation().add(0, 2.0, 0);
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
        Player player = ctx.getSenderAsPlayer();

        int range = 20;
        if(ctx.numArgs() >= 2) {
            ctx.checkArgIsInt(2, "HOLOGRAM_RANGE_INVALID", "range", ctx.getArg(2));
            range = Math.clamp(ctx.getArgAsInt(2), 1, 50);
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
        ctx.checkArgIsInt(2, "HOLOGRAM_ID_INVALID", "id", ctx.getArg(2));

        int id = ctx.getArgAsInt(2);
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

        String type = ctx.getArg(2);
        if(!handlers.containsKey(type)) {
            ctx.returnError("HOLOGRAM_TYPE_INVALID", "type", type);
        }

        String context = null;
        List<String> contexts = handlers.get(type).list(type);
        if(contexts != null && !contexts.isEmpty()) {
            ctx.checkArgsSizeAtLeast(3, "HOLOGRAM_USAGE_DELETEALL");
            context = ctx.getArg(3);
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

        config.set("holograms." + data.location.getWorld().getName() + "." + id, null);
        config.save();
    }

    /**
     * Delete holograms by type and context.
     *
     * @param type The hologram type to delete.
     * @param context The hologram context to delete (or null for all contexts).
     */
    @Override
    public void delete(@NotNull String type, @Nullable String context) {
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
            config.set("holograms." + data.location.getWorld().getName() + "." + data.id, null);
            iterator.remove();
        }

        config.save();
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
                String path = "holograms." + data.id;
                config.set(path + ".type", data.type);
                config.set(path + ".location", data.location);
                config.set(path + ".data", data.data);
            }
        } else {
            HologramData data = holograms.get(id);
            if (data == null) {
                return;
            }

            String path = "holograms." + data.id;
            config.set(path + ".type", data.type);
            config.set(path + ".location", data.location);
            config.set(path + ".data", data.data);
        }

        config.save();
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
            ArmorStand stand = world.spawn(lineLoc, ArmorStand.class, entity -> {
                entity.setMarker(true);
                entity.setInvisible(true);
                entity.setGravity(false);
                entity.setSmall(true);
                entity.setCustomNameVisible(true);
                entity.customName(TextUtil.colourise(line));
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

    // ===== internals =====

    /**
     * holograms:
     *   1:
     *     world: world
     *     location: "100,64,200"
     *     ...
     *   2:
     *     world: world_nether
     *     location: "50,80,10"
     *     ...
     */

    /**
     * Load holograms from the configuration for a specific world and optional type filter.
     *
     * @param limitType The hologram type to filter by (or null for all types).
     * @param worldName The name of the world to load holograms from.
     */
    private void loadHolograms(String limitType, String worldName) {
        World world = Bukkit.getWorld(worldName);

        if(world == null) {
            return;
        }

        if (!this.config.isSection("holograms")) {
            return;
        }

        ConfigSection section = this.config.getSection("holograms." + worldName);
        if(section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String base = "holograms." + worldName + "." + key;
                String type = this.config.getString(base + ".type", "").toLowerCase(Locale.ROOT);
                if(limitType != null && !limitType.equals(type)) {
                    continue;
                }

                String context = this.config.getString(base + ".context", null);
                String locationStr = this.config.getString(base + ".location");
                List<String> data = this.config.getStringList(base + ".data");

                if(!handlers.containsKey(type)) {
                    continue;
                }

                if(locationStr.isEmpty()) {
                    continue;
                }

                String[] locationParts = locationStr.split(",");
                double x = Double.parseDouble(locationParts[0]);
                double y = Double.parseDouble(locationParts[1]);
                double z = Double.parseDouble(locationParts[2]);
                Location loc = new Location(world, x, y, z);

                HologramData dataObj = new HologramData(id, type, context, loc, new ArrayList<>(data));
                holograms.put(id, dataObj);
            } catch (NumberFormatException e) {
                // ignored
            }
        }
    }
}
