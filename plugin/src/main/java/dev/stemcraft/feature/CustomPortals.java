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

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Defines custom nether portals that route players to explicit destinations.
 */
public class CustomPortals extends BaseFeature {
    private static final String COMMAND_LABEL = "portal";
    private static final String COMMAND_PERMISSION = "stemcraft.command.portal";
    private static final String PORTALS_PATH = "portals";
    private static final String DESTINATION_PATH = "destination";
    private static final String BLOCKS_PATH = "blocks";
    private static final int PORTAL_SCAN_RADIUS = 1;
    private static final int TARGET_BLOCK_DISTANCE = 8;
    private static final int MAX_PORTAL_BLOCKS = 4096;
    private static final long INSTANT_TELEPORT_GUARD_MILLIS = 1000L;
    private static final Pattern PORTAL_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");

    private final Map<String, PortalDefinition> portals = new LinkedHashMap<>();
    private final Map<PortalBlockKey, PortalDefinition> portalBlockIndex = new LinkedHashMap<>();
    private final Map<UUID, Long> instantTeleportGuards = new LinkedHashMap<>();
    private boolean instantTeleport;
    private Command command;

    record PortalBlockKey(String worldName, int x, int y, int z) {
        static @Nullable PortalBlockKey fromBlock(@Nullable Block block) {
            if (block == null || block.getWorld() == null) {
                return null;
            }
            return new PortalBlockKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
            );
        }

        static @Nullable PortalBlockKey deserialize(@Nullable String serialized) {
            if (serialized == null || serialized.isBlank()) {
                return null;
            }

            String[] parts = serialized.split(",", 4);
            if (parts.length != 4) {
                return null;
            }

            try {
                return new PortalBlockKey(
                    parts[0].trim(),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim())
                );
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        String serialize() {
            return worldName + "," + x + "," + y + "," + z;
        }
    }

    enum PortalDestinationMode {
        WORLD_DEFAULT("default"),
        WORLD_SPAWN("spawn"),
        EXACT("exact");

        private final String serializedValue;

        PortalDestinationMode(String serializedValue) {
            this.serializedValue = serializedValue;
        }

        String serializedValue() {
            return serializedValue;
        }

        static @Nullable PortalDestinationMode fromSerializedValue(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return null;
            }

            for (PortalDestinationMode mode : values()) {
                if (mode.serializedValue.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }

            return null;
        }
    }

    record PortalDestination(String worldName, PortalDestinationMode mode, double x, double y, double z, float yaw, float pitch) {
        static PortalDestination fromLocation(@NotNull Location location) {
            World world = Objects.requireNonNull(location.getWorld(), "Destination world cannot be null.");
            return new PortalDestination(
                world.getName(),
                PortalDestinationMode.EXACT,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            );
        }

        static PortalDestination worldDefault(@NotNull World world) {
            return new PortalDestination(world.getName(), PortalDestinationMode.WORLD_DEFAULT, 0.0d, 0.0d, 0.0d, 0.0f, 0.0f);
        }

        static PortalDestination worldSpawn(@NotNull World world) {
            return new PortalDestination(world.getName(), PortalDestinationMode.WORLD_SPAWN, 0.0d, 0.0d, 0.0d, 0.0f, 0.0f);
        }

        static @Nullable PortalDestination deserialize(@Nullable String serialized) {
            if (serialized == null || serialized.isBlank()) {
                return null;
            }

            String[] parts = serialized.split(",");
            if (parts.length == 2) {
                PortalDestinationMode mode = PortalDestinationMode.fromSerializedValue(parts[1].trim());
                if (mode == null || mode == PortalDestinationMode.EXACT) {
                    return null;
                }

                return new PortalDestination(parts[0].trim(), mode, 0.0d, 0.0d, 0.0d, 0.0f, 0.0f);
            }

            if (parts.length == 6) {
                try {
                    return new PortalDestination(
                        parts[0].trim(),
                        PortalDestinationMode.EXACT,
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim()),
                        Float.parseFloat(parts[4].trim()),
                        Float.parseFloat(parts[5].trim())
                    );
                } catch (NumberFormatException exception) {
                    return null;
                }
            }

            if (parts.length != 7) {
                return null;
            }

            try {
                PortalDestinationMode mode = PortalDestinationMode.fromSerializedValue(parts[1].trim());
                if (mode == null) {
                    return null;
                }

                return new PortalDestination(
                    parts[0].trim(),
                    mode,
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()),
                    Double.parseDouble(parts[4].trim()),
                    Float.parseFloat(parts[5].trim()),
                    Float.parseFloat(parts[6].trim())
                );
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        String serialize() {
            if (mode != PortalDestinationMode.EXACT) {
                return worldName + "," + mode.serializedValue();
            }

            return worldName + "," + mode.serializedValue() + "," + x + "," + y + "," + z + "," + yaw + "," + pitch;
        }
    }

    record PortalDefinition(String id, PortalDestination destination, Set<PortalBlockKey> blocks) {
        PortalDefinition {
            destination = Objects.requireNonNull(destination, "destination");
            blocks = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(blocks, "blocks")));
        }
    }

    public CustomPortals(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        instantTeleport = getConfigSection().getBoolean("instant-teleport", true);
        registerTabCompletions();
        registerCommand();
        loadPortalsFromConfig();

        api.events().register(PlayerPortalEvent.class, this::handlePortalEvent, EventPriority.HIGHEST, true);
        api.events().register(PlayerMoveEvent.class, this::handlePlayerMove, EventPriority.HIGHEST, true);
    }

    @Override
    public void onReload() {
        super.onReload();
        instantTeleport = getConfigSection().getBoolean("instant-teleport", true);
        loadPortalsFromConfig();
    }

    @Override
    public void onDisable() {
        if (command != null) {
            command.unregister();
            command = null;
        }

        portals.clear();
        portalBlockIndex.clear();
        instantTeleportGuards.clear();
    }

    private void registerTabCompletions() {
        api.tabComplete().register("custom-portal-id", (player, args) -> portalIds());
    }

    private void registerCommand() {
        if (command != null) {
            return;
        }

        command = api.commands().create(COMMAND_LABEL)
            .description("Define custom nether portal destinations.")
            .usage("Usage: /portal <list|info|set|repair|delete> ...")
            .permission(COMMAND_PERMISSION)
            .tabCompletion("list")
            .tabCompletion("info", "{custom-portal-id}")
            .tabCompletion("set", "{custom-portal-id}")
            .tabCompletion("set", "{custom-portal-id}", "here")
            .tabCompletion("set", "{custom-portal-id}", "{world}")
            .tabCompletion("set", "{custom-portal-id}", "{world}", "spawn")
            .tabCompletion("repair", "{custom-portal-id}")
            .tabCompletion("delete", "{custom-portal-id}")
            .executor((unused, cmd, ctx) -> handleCommand(ctx))
            .register(STEMCraft.getPlugin());
    }

    private void handleCommand(@NotNull CommandContext ctx) {
        String subCommand = ctx.getArgLower(0);
        if (subCommand == null || subCommand.equals("list")) {
            handleList(ctx);
            return;
        }

        switch (subCommand) {
            case "info" -> handleInfo(ctx);
            case "set" -> handleSet(ctx);
            case "repair" -> handleRepair(ctx);
            case "delete", "remove" -> handleDelete(ctx);
            default -> ctx.returnError("Usage: /portal <list|info|set|repair|delete> ...");
        }
    }

    private void handleList(@NotNull CommandContext ctx) {
        if (portals.isEmpty()) {
            ctx.returnInfo("No custom portals are configured.");
        }

        ctx.info("Custom portals:");
        portals.values().stream()
            .sorted(Comparator.comparing(PortalDefinition::id))
            .forEach(definition -> ctx.info(
                " - " + definition.id()
                    + " -> " + formatDestination(definition.destination())
                    + " (" + definition.blocks().size() + " portal blocks)"
            ));
        ctx.returnInfo("Total custom portals: " + portals.size());
    }

    private void handleInfo(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "Usage: /portal info <id>");

        PortalDefinition definition = findPortal(normalizePortalId(ctx.getArg(1)));
        if (definition == null) {
            ctx.returnError("Custom portal not found: " + ctx.getArg(1));
            return;
        }

        PortalBlockKey firstBlock = definition.blocks().stream().findFirst().orElse(null);
        ctx.info("Portal '" + definition.id() + "':");
        ctx.info(" - Destination: " + formatDestination(definition.destination()));
        ctx.info(" - Blocks: " + definition.blocks().size());
        if (firstBlock != null) {
            ctx.info(" - Source world: " + firstBlock.worldName());
            ctx.info(" - Sample block: " + firstBlock.x() + ", " + firstBlock.y() + ", " + firstBlock.z());
        }
        ctx.returnInfo("Use /portal delete " + definition.id() + " to remove it.");
    }

    private void handleSet(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "Usage: /portal set <id> <world|here> [spawn|x y z [yaw pitch]]");
        ctx.checkNotConsole("This command must be run in-game.");

        String portalId = normalizePortalId(ctx.getArg(1));
        if (!PORTAL_ID_PATTERN.matcher(portalId).matches()) {
            ctx.returnError("Portal IDs must match [a-z0-9][a-z0-9_-]*.");
        }

        Player player = ctx.asPlayer();
        Block seedBlock = findPortalSeed(Objects.requireNonNull(player));
        if (seedBlock == null) {
            ctx.returnError("Stand inside the lit nether portal, or look directly at one, before running this command.");
            return;
        }

        Set<PortalBlockKey> blocks = collectPortalBlocks(seedBlock);
        if (blocks.isEmpty()) {
            ctx.returnError("Could not resolve the connected nether portal blocks.");
        }

        PortalDefinition overlap = findOverlappingPortal(blocks, portalId);
        if (overlap != null) {
            ctx.returnError("This portal overlaps custom portal '" + overlap.id() + "'. Delete or update that one first.");
        }

        PortalDestination destination = parseDestination(ctx, 2);
        PortalDefinition definition = new PortalDefinition(portalId, destination, blocks);

        portals.put(portalId, definition);
        rebuildPortalBlockIndex();
        savePortal(definition);

        ctx.returnSuccess(
            "Portal '" + portalId + "' now routes to "
                + formatDestination(destination)
                + " using " + blocks.size() + " portal blocks."
        );
    }

    private void handleDelete(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "Usage: /portal delete <id>");

        String portalId = normalizePortalId(ctx.getArg(1));
        PortalDefinition removed = portals.remove(portalId);
        if (removed == null) {
            ctx.returnError("Custom portal not found: " + ctx.getArg(1));
        }

        rebuildPortalBlockIndex();
        deletePortal(portalId);
        ctx.returnSuccess("Deleted custom portal '" + portalId + "'.");
    }

    private void handleRepair(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "Usage: /portal repair <id>");
        ctx.checkNotConsole("This command must be run in-game.");

        String portalId = normalizePortalId(ctx.getArg(1));
        PortalDefinition existing = findPortal(portalId);
        if (existing == null) {
            ctx.returnError("Custom portal not found: " + ctx.getArg(1));
            return;
        }

        Player player = Objects.requireNonNull(ctx.asPlayer());
        Block seedBlock = findPortalSeed(player);
        if (seedBlock == null) {
            ctx.returnError("Stand inside the rebuilt lit nether portal, or look directly at it, before running this command.");
            return;
        }

        Set<PortalBlockKey> blocks = collectPortalBlocks(seedBlock);
        if (blocks.isEmpty()) {
            ctx.returnError("Could not resolve the connected nether portal blocks.");
            return;
        }

        PortalDefinition overlap = findOverlappingPortal(blocks, portalId);
        if (overlap != null) {
            ctx.returnError("This portal overlaps custom portal '" + overlap.id() + "'. Delete or update that one first.");
            return;
        }

        PortalDefinition repaired = repairDefinition(existing, blocks);
        portals.put(portalId, repaired);
        rebuildPortalBlockIndex();
        savePortal(repaired);
        ctx.returnSuccess("Repaired portal '" + portalId + "' using " + blocks.size()
            + " portal blocks; destination remains " + formatDestination(existing.destination()) + ".");
    }

    static @NotNull PortalDefinition repairDefinition(@NotNull PortalDefinition existing,
                                                       @NotNull Set<PortalBlockKey> blocks) {
        return new PortalDefinition(existing.id(), existing.destination(), blocks);
    }

    private void handlePlayerMove(@NotNull PlayerMoveEvent event) {
        if (!instantTeleport || event.getTo() == null) {
            return;
        }

        PortalBlockKey destinationBlock = PortalBlockKey.fromBlock(event.getTo().getBlock());
        PortalBlockKey previousBlock = PortalBlockKey.fromBlock(event.getFrom().getBlock());
        if (destinationBlock == null || destinationBlock.equals(previousBlock)) {
            return;
        }

        PortalDefinition definition = portalBlockIndex.get(destinationBlock);
        if (definition != null) {
            teleportThroughPortal(event.getPlayer(), definition, true);
        }
    }

    private void handlePortalEvent(@NotNull PlayerPortalEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        PortalDefinition definition = findPortalAt(event.getFrom());
        if (definition == null) {
            return;
        }

        if (instantTeleport) {
            event.setCancelled(true);
            teleportThroughPortal(event.getPlayer(), definition, true);
            return;
        }

        event.setCancelled(true);
        teleportThroughPortal(event.getPlayer(), definition, false);
    }

    private void teleportThroughPortal(@NotNull Player player,
                                       @NotNull PortalDefinition definition,
                                       boolean guardRepeatedTeleport) {
        long now = System.currentTimeMillis();
        if (guardRepeatedTeleport && instantTeleportGuards.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }

        Location destination = resolveDestination(player, definition.destination());
        if (destination == null) {
            STEMCraft.getPlugin().getLogger().warning(
                "Skipping custom portal '" + definition.id() + "' because destination world '"
                    + definition.destination().worldName() + "' is unavailable."
            );
            return;
        }

        if (guardRepeatedTeleport) {
            instantTeleportGuards.put(player.getUniqueId(), now + INSTANT_TELEPORT_GUARD_MILLIS);
        }
        rememberForcedDestination(player, definition.destination(), destination);
        PlayerUtil.teleport(player, destination);
    }

    private void loadPortalsFromConfig() {
        portals.clear();
        portalBlockIndex.clear();

        ConfigSection portalsSection = getConfigSection().getSection(PORTALS_PATH, true);
        for (String rawId : portalsSection.getKeys(false)) {
            ConfigSection portalSection = portalsSection.getSection(rawId, false);
            if (portalSection == null) {
                continue;
            }

            PortalDefinition definition = readPortal(rawId, portalSection);
            if (definition == null) {
                STEMCraft.getPlugin().getLogger().warning("Ignoring invalid custom portal definition '" + rawId + "'.");
                continue;
            }

            PortalDefinition overlap = findOverlappingPortal(definition.blocks(), definition.id());
            if (overlap != null) {
                STEMCraft.getPlugin().getLogger().warning(
                    "Ignoring custom portal '" + definition.id() + "' because it overlaps '" + overlap.id() + "'."
                );
                continue;
            }

            portals.put(definition.id(), definition);
        }

        rebuildPortalBlockIndex();
    }

    static @Nullable PortalDefinition readPortal(@NotNull String rawId, @NotNull ConfigSection portalSection) {
        String portalId = normalizePortalId(rawId);
        PortalDestination destination = PortalDestination.deserialize(portalSection.getString(DESTINATION_PATH));
        if (destination == null) {
            return null;
        }

        LinkedHashSet<PortalBlockKey> blocks = new LinkedHashSet<>();
        for (String rawBlock : portalSection.getStringList(BLOCKS_PATH)) {
            PortalBlockKey block = PortalBlockKey.deserialize(rawBlock);
            if (block != null) {
                blocks.add(block);
            }
        }

        if (blocks.isEmpty()) {
            return null;
        }

        return new PortalDefinition(portalId, destination, blocks);
    }

    static void writePortal(@NotNull ConfigSection portalsSection, @NotNull PortalDefinition definition) {
        ConfigSection portalSection = portalsSection.createSection(definition.id(), true);
        portalSection.set(DESTINATION_PATH, definition.destination().serialize());
        portalSection.set(BLOCKS_PATH, definition.blocks().stream()
            .map(PortalBlockKey::serialize)
            .toList());
    }

    static @NotNull Set<PortalBlockKey> collectPortalBlocks(@NotNull Block seedBlock) {
        if (seedBlock.getType() != Material.NETHER_PORTAL) {
            return Set.of();
        }

        ArrayDeque<Block> queue = new ArrayDeque<>();
        LinkedHashSet<PortalBlockKey> visited = new LinkedHashSet<>();

        queue.add(seedBlock);
        while (!queue.isEmpty()) {
            Block block = queue.removeFirst();
            PortalBlockKey key = PortalBlockKey.fromBlock(block);
            if (key == null || !visited.add(key)) {
                continue;
            }

            if (visited.size() > MAX_PORTAL_BLOCKS) {
                throw new IllegalStateException("Portal exceeds the maximum supported size of " + MAX_PORTAL_BLOCKS + " blocks.");
            }

            enqueueIfPortal(queue, block.getRelative(1, 0, 0));
            enqueueIfPortal(queue, block.getRelative(-1, 0, 0));
            enqueueIfPortal(queue, block.getRelative(0, 1, 0));
            enqueueIfPortal(queue, block.getRelative(0, -1, 0));
            enqueueIfPortal(queue, block.getRelative(0, 0, 1));
            enqueueIfPortal(queue, block.getRelative(0, 0, -1));
        }

        return visited;
    }

    private static void enqueueIfPortal(@NotNull ArrayDeque<Block> queue, @NotNull Block candidate) {
        if (candidate.getType() == Material.NETHER_PORTAL) {
            queue.add(candidate);
        }
    }

    private @Nullable PortalDefinition findPortalAt(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Block origin = location.getBlock();
        for (int dx = -PORTAL_SCAN_RADIUS; dx <= PORTAL_SCAN_RADIUS; dx++) {
            for (int dy = -PORTAL_SCAN_RADIUS; dy <= PORTAL_SCAN_RADIUS; dy++) {
                for (int dz = -PORTAL_SCAN_RADIUS; dz <= PORTAL_SCAN_RADIUS; dz++) {
                    PortalDefinition definition = portalBlockIndex.get(new PortalBlockKey(
                        origin.getWorld().getName(),
                        origin.getX() + dx,
                        origin.getY() + dy,
                        origin.getZ() + dz
                    ));
                    if (definition != null) {
                        return definition;
                    }
                }
            }
        }

        return null;
    }

    private @Nullable Block findPortalSeed(@NotNull Player player) {
        Block nearby = findNearbyPortalBlock(player.getLocation().getBlock(), PORTAL_SCAN_RADIUS);
        if (nearby != null) {
            return nearby;
        }

        Block target = player.getTargetBlockExact(TARGET_BLOCK_DISTANCE);
        if (target != null) {
            return findNearbyPortalBlock(target, PORTAL_SCAN_RADIUS);
        }

        return null;
    }

    private static @Nullable Block findNearbyPortalBlock(@NotNull Block origin, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block candidate = origin.getRelative(dx, dy, dz);
                    if (candidate.getType() == Material.NETHER_PORTAL) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private PortalDestination parseDestination(@NotNull CommandContext ctx, int startIndex) {
        String firstArg = ctx.getArg(startIndex, "");
        if (firstArg.equalsIgnoreCase("here")) {
            if (ctx.numArgs() != startIndex + 1) {
                ctx.returnError("Usage: /portal set <id> here");
            }
            return PortalDestination.fromLocation(Objects.requireNonNull(ctx.asPlayer()).getLocation());
        }

        World world = resolveWorld(firstArg);
        if (world == null) {
            ctx.returnError("World not found: " + firstArg);
            throw new IllegalStateException("Command execution should stop after reporting a missing world.");
        }

        int remainingArgs = ctx.numArgs() - startIndex;
        if (remainingArgs == 1) {
            return PortalDestination.worldDefault(world);
        }
        if (remainingArgs == 2 && "spawn".equalsIgnoreCase(ctx.getArg(startIndex + 1))) {
            return PortalDestination.worldSpawn(world);
        }
        if (remainingArgs != 4 && remainingArgs != 6) {
            ctx.returnError("Usage: /portal set <id> <world> [spawn|x y z [yaw pitch]]");
        }

        ctx.checkArgIsDouble(startIndex + 1, "Invalid X coordinate: " + ctx.getArg(startIndex + 1));
        ctx.checkArgIsDouble(startIndex + 2, "Invalid Y coordinate: " + ctx.getArg(startIndex + 2));
        ctx.checkArgIsDouble(startIndex + 3, "Invalid Z coordinate: " + ctx.getArg(startIndex + 3));

        double x = ctx.getArgAsDouble(startIndex + 1);
        double y = ctx.getArgAsDouble(startIndex + 2);
        double z = ctx.getArgAsDouble(startIndex + 3);

        float yaw = 0.0f;
        float pitch = 0.0f;
        if (remainingArgs == 6) {
            ctx.checkArgIsFloat(startIndex + 4, "Invalid yaw: " + ctx.getArg(startIndex + 4));
            ctx.checkArgIsFloat(startIndex + 5, "Invalid pitch: " + ctx.getArg(startIndex + 5));
            yaw = ctx.getArgAsFloat(startIndex + 4);
            pitch = ctx.getArgAsFloat(startIndex + 5);
        }

        return new PortalDestination(world.getName(), PortalDestinationMode.EXACT, x, y, z, yaw, pitch);
    }

    private @Nullable World resolveWorld(@Nullable String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }

        if (!api.worlds().worldExists(worldName)) {
            return null;
        }

        return api.worlds().loadWorld(worldName);
    }

    private @NotNull Location resolveWorldDefaultDestination(@Nullable UUID playerId, @NotNull World world) {
        TeleportUtils teleportUtils = STEMCraft.getPlugin().feature(TeleportUtils.class);
        if (teleportUtils == null) {
            return world.getSpawnLocation();
        }

        Location destination = teleportUtils.resolveWorldDestination(playerId, world);
        return destination != null ? destination : world.getSpawnLocation();
    }

    private void rememberForcedDestination(@NotNull Player player,
                                           @NotNull PortalDestination destination,
                                           @NotNull Location resolvedDestination) {
        if (destination.mode() == PortalDestinationMode.WORLD_DEFAULT || resolvedDestination.getWorld() == null) {
            return;
        }

        RandomFirstSpawn randomFirstSpawn = STEMCraft.getPlugin().feature(RandomFirstSpawn.class);
        if (randomFirstSpawn == null) {
            return;
        }

        randomFirstSpawn.recordSeenWorldEntry(player.getUniqueId(), resolvedDestination.getWorld(), resolvedDestination);
    }

    private @Nullable Location resolveDestination(@NotNull Player player, @NotNull PortalDestination destination) {
        World world = resolveWorld(destination.worldName());
        if (world == null) {
            return null;
        }

        return switch (destination.mode()) {
            case WORLD_DEFAULT -> resolveWorldDefaultDestination(player.getUniqueId(), world);
            case WORLD_SPAWN -> world.getSpawnLocation();
            case EXACT -> new Location(
                world,
                destination.x(),
                destination.y(),
                destination.z(),
                destination.yaw(),
                destination.pitch()
            );
        };
    }

    private void savePortal(@NotNull PortalDefinition definition) {
        ConfigSection portalsSection = getConfigSection().getSection(PORTALS_PATH, true);
        writePortal(portalsSection, definition);
        portalsSection.save();
    }

    private void deletePortal(@NotNull String portalId) {
        ConfigSection portalsSection = getConfigSection().getSection(PORTALS_PATH, true);
        portalsSection.remove(portalId);
        portalsSection.save();
    }

    private void rebuildPortalBlockIndex() {
        portalBlockIndex.clear();
        for (PortalDefinition definition : portals.values()) {
            for (PortalBlockKey block : definition.blocks()) {
                portalBlockIndex.put(block, definition);
            }
        }
    }

    private @Nullable PortalDefinition findPortal(@Nullable String portalId) {
        if (portalId == null || portalId.isBlank()) {
            return null;
        }
        return portals.get(portalId);
    }

    private @Nullable PortalDefinition findOverlappingPortal(@NotNull Set<PortalBlockKey> blocks, @Nullable String ignoredPortalId) {
        for (PortalBlockKey block : blocks) {
            PortalDefinition definition = portalBlockIndex.get(block);
            if (definition != null && !definition.id().equalsIgnoreCase(ignoredPortalId)) {
                return definition;
            }
        }
        return null;
    }

    private static @NotNull String normalizePortalId(@Nullable String portalId) {
        if (portalId == null) {
            return "";
        }

        String normalized = portalId.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private @NotNull List<String> portalIds() {
        return portals.keySet().stream().sorted().toList();
    }

    private static @NotNull String formatDestination(@NotNull PortalDestination destination) {
        return switch (destination.mode()) {
            case WORLD_DEFAULT -> destination.worldName() + " (world default)";
            case WORLD_SPAWN -> destination.worldName() + " (spawn)";
            case EXACT -> destination.worldName()
                + " @ "
                + formatCoordinate(destination.x())
                + ", "
                + formatCoordinate(destination.y())
                + ", "
                + formatCoordinate(destination.z());
        };
    }

    private static @NotNull String formatCoordinate(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
