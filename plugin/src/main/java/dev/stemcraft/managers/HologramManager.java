package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import dev.stemcraft.api.services.hologram.HologramService;
import dev.stemcraft.api.services.hologram.HologramTypeHandler;
import dev.stemcraft.api.utils.SCString;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.events.WorldDeleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HologramManager implements HologramService {
    private static final double LINE_SPACING = 0.25;
    private final STEMCraft plugin;
    private final LocaleService locale;

    private final Map<Integer, HologramData> holograms = new HashMap<>();
    private final Map<Integer, List<UUID>> entitiesById = new HashMap<>();
    private final Map<String, HologramTypeHandler> handlers = new HashMap<>();

    private final File storageFile;
    private final YamlConfiguration config;

    public HologramManager(STEMCraft plugin) {
        this.plugin = plugin;
        this.locale = plugin.localeService();
        this.storageFile = new File(plugin.getDataFolder(), "holograms.yml");
        this.config = YamlConfiguration.loadConfiguration(storageFile);
    }

    /**
     * Initialize the hologram manager.
     */
    public void onEnable() {
        registerType("", new HologramTypeHandler() {
            @Override
            public List<String> list(String type) {
                return null;
            }

            @Override
            public List<String> lines(String type, String context, int id, List<String> data) {
                return data;
            }
        });

        Bukkit.getWorlds().forEach(world -> {
            loadHolograms(null, world.getName());
        });

        plugin.registerEvent(WorldLoadEvent.class, event -> {
            World world = event.getWorld();
            loadHolograms(null, world.getName());
        });

        plugin.registerEvent(WorldUnloadEvent.class, event -> {
            String worldName = event.getWorld().getName();

            for (HologramData data : holograms.values()) {
                if (!data.location.getWorld().getName().equals(worldName)) {
                    continue;
                }

                despawn(data.id);
            }
        });

        plugin.registerEvent(WorldDeleteEvent.class, event -> {
            String worldName = event.getWorldName();

            for (HologramData data : holograms.values()) {
                if (!data.location.getWorld().getName().equals(worldName)) {
                    continue;
                }

                delete(data.id);
            }
        });

        plugin.registerEvent(ChunkLoadEvent.class, event -> {
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

        plugin.registerEvent(ChunkUnloadEvent.class, event -> {
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


        plugin.tabCompleteService().register("hologram_type", (player, args) ->
                new ArrayList<>(handlers.keySet())
        );

        plugin.tabCompleteService().register("hologram_context", (player, args) -> {
            if (args.length < 2) {
                return Collections.emptyList();
            }
            String type = SCString.slugify(args[args.length - 1]);
            HologramTypeHandler handler = handlers.get(type);
            if (handler == null) {
                return Collections.emptyList();
            }
            List<String> contexts = handler.list(type);
            return Objects.requireNonNullElse(contexts, Collections.emptyList());
        });

        plugin.registerCommand("hologram")
                .setDescription("HOLOGRAM_DESCRIPTION")
                .setUsage("HOLOGRAM_USAGE")
                .setPermission("stemcraft.command.hologram")

                .addTabCompletion("create", "{hologram_type}", "{hologram_context}")
                .addTabCompletion("closest", "{range}")
                .addTabCompletion("delete", "{id}")
                .addTabCompletion("deleteall", "{hologram_type}", "{hologram_context}")

                .setExecutor((api, cmd, ctx) -> {

                    var args = ctx.args();
                    if (args.isEmpty()) {
                        cmd.error(ctx.getSender(), "HOLOGRAM_USAGE");
                        return;
                    }

                    var action = args.getFirst().toLowerCase();

                    switch (action) {

                        case "create" -> {
                            subCommandCreate(api, cmd, ctx);
                        }

                        case "closest" -> {
                            subCommandClosest(api, cmd, ctx);
                        }

                        case "delete" -> {
                            subCommandDelete(api, cmd, ctx);
                        }

                        case "deleteall" -> {
                            subCommandDeleteAll(api, cmd, ctx);
                        }

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

    // ===== Sub Commands =====
    private void subCommandCreate(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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

    private void subCommandClosest(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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

    private void subCommandDelete(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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

    private void subCommandDeleteAll(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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

    // ===== API =====

    @Override
    public void registerType(String type, HologramTypeHandler handler) {
        if (type == null) {
            type = "";
        }

        String slug = SCString.slugify(type);
        handlers.put(slug, handler);

        Bukkit.getWorlds().forEach(world -> {
            loadHolograms(slug, world.getName());
        });
    }

    @Override
    public int create(String type, String context, Location location, List<String> data) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException(plugin.localeService().get("HOLOGRAM_LOCATION_INVALID"));
        }

        if(type == null) { type = ""; }
        String slug = SCString.slugify(type);

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

    @Override
    public void update(int id, List<String> data) {
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

    @Override
    public void update(String type, String context) {
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

    @Override
    public void move(int id, Location newLocation) {
        if (newLocation == null || newLocation.getWorld() == null) {
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

    @Override
    public void delete(int id) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(storageFile);

        HologramData data = holograms.remove(id);
        if (data == null) {
            return;
        }

        despawn(id);

        cfg.set("holograms." + data.location.getWorld().getName() + "." + id, null);
        try {
            cfg.save(storageFile);
        } catch (IOException e) {
            plugin.warn("HOLOGRAM_DELETE_FAILED", "error", e.getMessage());
        }
    }

    @Override
    public void delete(String type, String context) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(storageFile);

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
            cfg.set("holograms." + data.location.getWorld().getName() + "." + data.id, null);
            iterator.remove();
        }

        try {
            cfg.save(storageFile);
        } catch (IOException e) {
            plugin.warn("HOLOGRAM_DELETE_FAILED", "error", e.getMessage());
        }
    }

    @Override
    public int closest(Location loc, int range) {
        if (loc == null || loc.getWorld() == null) {
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

    @Override
    public void save(Integer id) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(storageFile);

        if(id == null) {
            for (HologramData data : holograms.values()) {
                String path = "holograms." + data.id;
                cfg.set(path + ".type", data.type);
                cfg.set(path + ".location", data.location);
                cfg.set(path + ".data", data.data);
            }
        } else {
            HologramData data = holograms.get(id);
            if (data == null) {
                return;
            }

            String path = "holograms." + data.id;
            cfg.set(path + ".type", data.type);
            cfg.set(path + ".location", data.location);
            cfg.set(path + ".data", data.data);
        }

        try {
            cfg.save(storageFile);
        } catch (IOException e) {
            plugin.warn("HOLOGRAM_SAVE_FAILED", "error", e.getMessage());
        }
    }

    // ===== internals =====

    private void spawnAll() {
        for (HologramData data : holograms.values()) {
            spawn(data);
        }
    }

    private void respawn(HologramData data) {
        despawn(data.id);
        spawn(data);
    }

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
                entity.customName(SCString.colourise(line));
                entity.setPersistent(false);
            });
            uuids.add(stand.getUniqueId());
            yOffset -= LINE_SPACING;
        }

        entitiesById.put(hologram.id, uuids);
    }

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

    private Entity findEntity(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity e = world.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static class HologramData {
        final int id;
        final String type;
        String context;
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

    private void loadHolograms(String limitType, String worldName) {
        World world = Bukkit.getWorld(worldName);

        if(world == null) {
            return;
        }

        if (!this.config.isConfigurationSection("holograms")) {
            return;
        }

        ConfigurationSection section = this.config.getConfigurationSection("holograms." + worldName);
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

                if(locationStr == null || locationStr.isEmpty()) {
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
                continue;
            }
        }
    }
}