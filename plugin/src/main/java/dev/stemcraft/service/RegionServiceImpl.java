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
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.model.RegionScopedData;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.region.RegionExtension;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.MapParse;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.service.region.RegionCommand;
import dev.stemcraft.service.region.RegionFlagExtension;
import dev.stemcraft.service.region.RegionMemberExtension;
import dev.stemcraft.service.region.RegionReputationExtension;
import dev.stemcraft.service.region.RegionTitleExtension;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Implementation of the RegionService for managing region and world listeners.
 */
public class RegionServiceImpl extends BaseService implements RegionService {
    private static final String MANAGED_REGIONS_CONFIG_KEY = "managed-regions";
    private static final String MANAGED_REGION_ID_PATTERN = "[a-z0-9_./-]+";

    /**
     * Entry for a region or world listener.
     */
    record RegionListenerEntry(SCRegion region, World world, RegionListener listener) { }

    /**
     * Map of registered region/world listeners.
     */
    private final Map<String, RegionListenerEntry> listeners = new HashMap<>();
    private final Map<String, SCManagedRegion> managedRegions = new HashMap<>();
    private final Map<String, RegionExtension<?>> extensions = new LinkedHashMap<>();
    private final Map<String, RegionExtension<?>> commandExtensions = new LinkedHashMap<>();

    /**
     * Map of players to their currently active regions/worlds.
     */
    private final Map<UUID, List<String>> entityRegions = new HashMap<>();

    /**
     * List of tracked entities.
     */
    private final List<UUID> trackedEntities = new ArrayList<>();
    private RegionCommand regionCommand;

    /**
     * Constructor for RegionServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public RegionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "regions");
    }

    /**
     * Initializes the region service.
     */
    @Override
    public void onEnable() {
        loadManagedRegions();

        regionCommand = new RegionCommand(plugin, api, this);
        regionCommand.onEnable();

        registerExtension(new RegionTitleExtension());
        registerExtension(new RegionMemberExtension());
        registerExtension(new RegionReputationExtension());
        registerExtension(new RegionFlagExtension());

        api.events().register(PlayerMoveEvent.class, event -> handleMovement(event.getPlayer(), event.getFrom(), event.getTo(), true));

        api.events().register(EntityMoveEvent.class, event -> {
            LivingEntity entity = event.getEntity();

            if (entity instanceof Player player) return;
            if (!trackedEntities.contains(entity.getUniqueId())) return;

            handleMovement(event.getEntity(), event.getFrom(), event.getTo(), false);
        });

        api.events().register(VehicleMoveEvent.class, event -> {
            Player rider = firstPassenger(event.getVehicle());
            if (rider == null) {
                return;
            }

            handleMovement(rider, event.getFrom(), event.getTo(), false);
        });

        api.events().register(EntityRemoveEvent.class, event -> {
            if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
                entityRegions.remove(event.getEntity().getUniqueId());
                trackedEntities.remove(event.getEntity().getUniqueId());
            }
        });

        api.events().register(PlayerQuitEvent.class, event -> entityRegions.remove(event.getPlayer().getUniqueId()));
        api.events().register(PlayerKickEvent.class, event -> entityRegions.remove(event.getPlayer().getUniqueId()));

        // 5 minutes in ticks
        long CLEANUP_ENTITY_INTERVAL = 6000;
        api.tasks().repeating(CLEANUP_ENTITY_INTERVAL, this::cleanupEntities);
    }

    /**
     * Cleans up region-service owned command helpers.
     */
    @Override
    public void onDisable() {
        getExtensions().forEach(RegionExtension::onDisable);
        if (regionCommand != null) {
            regionCommand.onDisable();
        }
    }

    private void cleanupEntities() {
        entityRegions.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null || !entity.isValid();
        });
    }

    /**
     * Loads managed regions from the region service configuration section.
     */
    private void loadManagedRegions() {
        managedRegions.clear();

        List<?> entries = getConfigSection().getList(MANAGED_REGIONS_CONFIG_KEY);
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?>)) {
                continue;
            }

            SCManagedRegion region = SCManagedRegion.deserialize(MapParse.map(entry, "regions.managed"));
            managedRegions.put(managedRegionKey(region.getWorldName(), region.getId()), region);
        }
    }

    /**
     * Persists all managed regions to the region service configuration section.
     */
    private void saveManagedRegions() {
        List<Map<String, Object>> serialized = managedRegions.values().stream()
            .map(this::serializeManagedRegion)
            .toList();
        getConfigSection().set(MANAGED_REGIONS_CONFIG_KEY, serialized);
        getConfigSection().save();
    }

    private void handleMovement(@NotNull LivingEntity livingEntity,
                                @Nullable Location from,
                                @Nullable Location requestedTo,
                                boolean preferActualLocation) {
        if (requestedTo == null || requestedTo.getWorld() == null) {
            return;
        }

        Location effectiveTo = preferActualLocation
            ? resolveEffectiveEnterLocation(livingEntity, from, requestedTo)
            : requestedTo;
        if (effectiveTo.getWorld() == null) {
            return;
        }

        UUID entityId = livingEntity.getUniqueId();
        World effectiveWorld = effectiveTo.getWorld();
        Set<String> previousIds = new HashSet<>(entityRegions.getOrDefault(entityId, List.of()));
        List<String> currentIds = new ArrayList<>();

        listeners.forEach((id, entry) -> {
            SCRegion region = entry.region();
            World world = entry.world();
            RegionListener listener = entry.listener();
            boolean wasInside = previousIds.contains(id);

            if (region != null) {
                boolean containsTo = region.contains(effectiveTo);
                boolean crossedRegion = from != null && region.intersectsPath(from, effectiveTo);

                if (wasInside) {
                    if (containsTo) {
                        currentIds.add(id);
                    } else if (livingEntity instanceof Player player) {
                        listener.onExit(player, region, from, effectiveTo);
                    } else {
                        listener.onExit(livingEntity, region, from, effectiveTo);
                    }
                    return;
                }

                if (!containsTo && !crossedRegion) {
                    return;
                }

                if (livingEntity instanceof Player player) {
                    listener.onEnter(player, region, from, effectiveTo);
                } else {
                    listener.onEnter(livingEntity, region, from, effectiveTo);
                }
                if (containsTo) {
                    currentIds.add(id);
                } else if (livingEntity instanceof Player player) {
                    listener.onExit(player, region, from, effectiveTo);
                } else {
                    listener.onExit(livingEntity, region, from, effectiveTo);
                }
                return;
            }

            if (world == null) {
                return;
            }

            if (wasInside) {
                if (world.equals(effectiveWorld)) {
                    currentIds.add(id);
                } else if(livingEntity instanceof Player player){
                    listener.onExitWorld(player, world, from, effectiveTo);
                }
                return;
            }

            if (world.equals(effectiveWorld) && (livingEntity instanceof Player player)) {
                listener.onEnterWorld(player, world, from, effectiveTo);
                currentIds.add(id);
            }
        });

        entityRegions.put(entityId, currentIds);
    }

    private @Nullable Player firstPassenger(@Nullable Entity vehicle) {
        if (vehicle == null) {
            return null;
        }

        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    private Location resolveEffectiveEnterLocation(LivingEntity livingEntity, Location from, Location requestedTo) {
        Location actual = livingEntity.getLocation();
        if (differentBlockLocation(actual, from) && differentBlockLocation(actual, requestedTo)) {
            return actual;
        }

        return requestedTo;
    }

    private boolean differentBlockLocation(Location left, Location right) {
        if (left == null || right == null) {
            return true;
        }
        if (left.getWorld() == null || right.getWorld() == null) {
            return true;
        }

        return !left.getWorld().equals(right.getWorld())
            || left.getBlockX() != right.getBlockX()
            || left.getBlockY() != right.getBlockY()
            || left.getBlockZ() != right.getBlockZ();
    }

    /**
     * Adds a region listener for a specific region.
     *
     * @param namespaceId The namespace ID of the region listener.
     * @param region The SCRegion to listen to.
     * @param listener The RegionListener to notify on enter/exit events.
     */
    public void addListener(@NotNull String namespaceId, @NotNull SCRegion region, @NotNull RegionListener listener) {
        NamespaceId.checkValid(namespaceId);

        listeners.put(namespaceId, new RegionListenerEntry(region, null, listener));
    }

    /**
     * Adds a region listener for a managed region.
     *
     * @param namespaceId The namespace ID of the region listener.
     * @param managedRegionId The managed region identifier.
     * @param listener The RegionListener to notify on enter/exit events.
     */
    @Override
    public void addListener(@NotNull String namespaceId, @NotNull String managedRegionId, @NotNull RegionListener listener) {
        SCManagedRegion managedRegion = resolveManagedRegionReference(managedRegionId);
        if (managedRegion == null) {
            throw new IllegalArgumentException("Unknown managed region: " + managedRegionId);
        }

        SCRegion region = managedRegion.getRegion();
        if (region != null) {
            addListener(namespaceId, region, listener);
            return;
        }

        World world = managedRegion.getWorld();
        if (world == null) {
            throw new IllegalStateException("Managed region world is not loaded: " + managedRegion.getWorldName());
        }

        addListener(namespaceId, world, listener);
    }

    /**
     * Adds a region listener for a specific world.
     *
     * @param namespaceId The namespace ID of the world listener.
     * @param world The World to listen to.
     * @param listener The RegionListener to notify on enter/exit world events.
     */
    public void addListener(@NotNull String namespaceId, @NotNull World world, @NotNull RegionListener listener) {
        listeners.put(namespaceId, new RegionListenerEntry(null, world, listener));
    }

    /**
     * Removes a region listener by its ID. Support asterisk wildcards at the end of the ID.
     *
     * @param namespaceId The namespace ID of the listener to remove.
     */
    @Override
    public void removeListener(@NotNull String namespaceId) {
        Set<String> idList = new HashSet<>();

        if(namespaceId.indexOf('*') != -1) {
            String prefix = namespaceId.substring(0, namespaceId.indexOf('*'));
            String suffix = namespaceId.substring(namespaceId.indexOf('*') + 1);

            for (String item : listeners.keySet()) {
                if (item.startsWith(prefix) && item.endsWith(suffix)) {
                    idList.add(item);
                }
            }
        } else {
            idList.add(namespaceId);
        }

        for(String item : idList) {
            listeners.remove(item);
        }
    }

    /**
     * Tracks a living entity for region and world listeners.
     *
     * @param livingEntity The entity to track.
     */
    public void trackLivingEntity(@NotNull LivingEntity livingEntity) {
        trackedEntities.add(livingEntity.getUniqueId());
    }

    /**
     * Untracks a living entity from region and world listeners.
     *
     * @param livingEntity The entity to untrack.
     */
    public void untrackLivingEntity(@NotNull LivingEntity livingEntity) {
        trackedEntities.remove(livingEntity.getUniqueId());
    }

    /**
     * Checks if a player is currently within a region or world listener.
     *
     * @param livingEntity The entity to check.
     * @return True if the entity is within a region or world listener, false otherwise.
     */
    public boolean isTracked(@NotNull LivingEntity livingEntity) {
        return trackedEntities.contains(livingEntity.getUniqueId());
    }

    /**
     * Checks if a player is currently within a region or world listener by its ID.
     *
     * @param uuid The entity UUID to check.
     * @param namespaceId The namespace ID of the region or world listener.
     * @return True if the player is within the region or world, false otherwise.
     */
    @Override
    public boolean contains(@NotNull UUID uuid, @NotNull String namespaceId) {
        List<String> regions = entityRegions.get(uuid);
        return regions != null && regions.contains(namespaceId);
    }

    /**
     * Gets the set of region and world listener IDs that a player is currently within.
     *
     * @param uuid The entity UUID to check.
     * @return A set of region and world listener IDs.
     */
    @Override
    public @NotNull Set<String> getRegions(@NotNull UUID uuid) {
        List<String> regions = entityRegions.get(uuid);
        if (regions == null) {
            return Set.of();
        }
        return new HashSet<>(regions);
    }

    /**
     * Gets a region by its ID.
     *
     * @param id The ID of the region.
     * @return The SCRegion associated with the ID, or null if not found.
     */
    @Override
    @Nullable
    public SCRegion getRegion(@NonNull String worldName, @NonNull String id) {
        RegionListenerEntry entry = listeners.get(id);
        if (entry != null) {
            return entry.region();
        }
        SCManagedRegion managedRegion = managedRegions.get(managedRegionKey(worldName, id));
        if (managedRegion != null) {
            return managedRegion.getRegion();
        }
        return null;
    }

    /**
     * Stores or updates a managed region definition.
     *
     * @param region The managed region definition to store.
     */
    @Override
    public void saveManagedRegion(@NotNull SCManagedRegion region) {
        checkManagedRegionId(region.getId());
        managedRegions.put(managedRegionKey(region.getWorldName(), region.getId()), region);
        saveManagedRegions();
    }

    /**
     * Retrieves a managed region definition by identifier.
     *
     * @param id The managed region identifier.
     * @return The managed region definition, or null if not found.
     */
    @Override
    public @Nullable SCManagedRegion getManagedRegion(@NotNull String worldName, @NotNull String id) {
        return managedRegions.get(managedRegionKey(worldName, id));
    }

    /**
     * Checks whether a managed region exists.
     *
     * @param id The managed region identifier.
     * @return True if the region exists, false otherwise.
     */
    @Override
    public boolean hasManagedRegion(@NotNull String worldName, @NotNull String id) {
        return managedRegions.containsKey(managedRegionKey(worldName, id));
    }

    /**
     * Removes a managed region definition.
     *
     * @param id The managed region identifier.
     * @return True if a managed region was removed, false otherwise.
     */
    @Override
    public boolean removeManagedRegion(@NotNull String worldName, @NotNull String id) {
        boolean removed = managedRegions.remove(managedRegionKey(worldName, id)) != null;
        if (removed) {
            saveManagedRegions();
        }
        return removed;
    }

    /**
     * Returns all managed region definitions known to the region service.
     *
     * @return The managed region definitions.
     */
    @Override
    public @NotNull Collection<SCManagedRegion> getManagedRegions(@NotNull String worldName) {
        return managedRegions.values().stream()
            .filter(region -> region.getWorldName().equalsIgnoreCase(worldName))
            .sorted(Comparator.comparing(SCManagedRegion::getId, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Returns managed regions that match the given location, ordered by priority.
     *
     * @param location The location to resolve.
     * @return The matching managed regions ordered by priority.
     */
    @Override
    public @NotNull Collection<SCManagedRegion> getManagedRegionsAt(@NotNull Location location) {
        if (location.getWorld() == null) {
            return List.of();
        }

        return managedRegions.values().stream()
            .filter(region -> matches(region, location))
            .sorted(Comparator
                .comparingInt(SCManagedRegion::getPriority).reversed()
                .thenComparing(SCManagedRegion::getId, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Returns the highest-priority managed region that matches the given location.
     *
     * @param location The location to resolve.
     * @return The highest-priority matching managed region, or null if none match.
     */
    @Override
    public @Nullable SCManagedRegion getManagedRegionAt(@NotNull Location location) {
        return getManagedRegionsAt(location).stream().findFirst().orElse(null);
    }

    /**
     * Registers a pluggable managed-region extension.
     *
     * @param extension The extension to register.
     */
    @Override
    public void registerExtension(@NotNull RegionExtension<?> extension) {
        NamespaceId.checkValid(extension.key());
        if (extensions.containsKey(extension.key())) {
            throw new IllegalArgumentException("A region extension with the key '" + extension.key() + "' is already registered.");
        }

        extension.onEnable(api, this);
        extensions.put(extension.key(), extension);
        hydrateManagedRegionData(extension);

        String commandKey = extension.commandKey();
        if (commandKey == null || commandKey.isBlank()) {
            return;
        }

        String normalizedCommandKey = commandKey.toLowerCase(Locale.ROOT);
        if (commandExtensions.containsKey(normalizedCommandKey)) {
            throw new IllegalArgumentException("A region extension command with the key '" + normalizedCommandKey + "' is already registered.");
        }

        commandExtensions.put(normalizedCommandKey, extension);
        addExtensionTabCompletions("set", normalizedCommandKey, extension.setTabCompletions());
        addExtensionTabCompletions("get", normalizedCommandKey, extension.getTabCompletions());
        addExtensionTabCompletions("clear", normalizedCommandKey, extension.clearTabCompletions());
    }

    /**
     * Retrieves a registered managed-region extension by its key.
     *
     * @param key The extension key.
     * @return The extension, or null if not found.
     */
    @Override
    public @Nullable RegionExtension<?> getExtension(@NotNull String key) {
        return extensions.get(key);
    }

    /**
     * Returns all registered managed-region extensions.
     *
     * @return The registered extensions.
     */
    @Override
    public @NotNull Collection<RegionExtension<?>> getExtensions() {
        return List.copyOf(extensions.values());
    }

    /**
     * Returns all managed region definitions across every world.
     *
     * @return All managed region definitions.
     */
    public @NotNull Collection<SCManagedRegion> getAllManagedRegions() {
        return List.copyOf(managedRegions.values());
    }

    /**
     * Resolves a managed region reference.
     * <p>
     * A reference may be the canonical {@code world:id} form, or a bare local ID when that ID
     * exists in exactly one world.
     *
     * @param reference The managed region reference.
     * @return The managed region, or null if no unique match exists.
     */
    public @Nullable SCManagedRegion resolveManagedRegionReference(@NotNull String reference) {
        int delimiter = reference.indexOf(':');
        if (delimiter > 0 && delimiter < reference.length() - 1) {
            return getManagedRegion(reference.substring(0, delimiter), reference.substring(delimiter + 1));
        }

        SCManagedRegion match = null;
        for (SCManagedRegion region : managedRegions.values()) {
            if (!region.getId().equalsIgnoreCase(reference)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = region;
        }
        return match;
    }

    /**
     * Returns a canonical region reference in {@code world:id} form.
     *
     * @param region The managed region.
     * @return The canonical managed region reference.
     */
    public @NotNull String getManagedRegionReference(@NotNull SCManagedRegion region) {
        return region.getWorldName() + ":" + region.getId();
    }

    /**
     * Retrieves a registered region extension by its command subcommand label.
     *
     * @param subCommand The `/region` subcommand label.
     * @return The region extension, or null if not found.
     */
    public @Nullable RegionExtension<?> getCommandExtension(@NotNull String subCommand) {
        return commandExtensions.get(subCommand.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the registered region extension command keys.
     *
     * @return The region extension command keys.
     */
    public @NotNull List<String> getCommandExtensionKeys() {
        return List.copyOf(commandExtensions.keySet());
    }

    private @NotNull Map<String, Object> serializeManagedRegion(@NotNull SCManagedRegion region) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", region.getId());
        out.put("world", region.getWorldName());
        out.put("priority", region.getPriority());
        if (region.getRegion() != null) {
            out.put("region", region.getRegion().serialize());
        }

        Map<String, Object> serializedData = new LinkedHashMap<>();
        region.data().forEach((key, value) -> {
            Object serialized = serializeExtensionDataValue(key, value);
            if (serialized != null) {
                serializedData.put(key, serialized);
            }
        });
        out.put("data", serializedData);
        return out;
    }

    @SuppressWarnings("unchecked")
    private @Nullable Object serializeExtensionDataValue(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            return null;
        }

        RegionExtension<?> extension = extensions.get(key);
        if (extension != null) {
            if (value instanceof RegionScopedData scopedData) {
                return serializeScopedExtensionData(extension, scopedData);
            }
            if (extension.type().isInstance(value)) {
                return ((RegionExtension<Object>) extension).serializeValue(value);
            }

            Object hydrated = ((RegionExtension<Object>) extension).deserializeValue(value);
            if (hydrated != null) {
                return ((RegionExtension<Object>) extension).serializeValue(hydrated);
            }
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private void hydrateManagedRegionData(@NotNull RegionExtension<?> extension) {
        for (SCManagedRegion region : managedRegions.values()) {
            Object raw = region.data().get(extension.key());
            if (raw == null || extension.type().isInstance(raw)) {
                continue;
            }

            if (raw instanceof RegionScopedData scopedData) {
                hydrateScopedExtensionData(region, extension, scopedData);
                continue;
            }

            if (RegionScopedData.isSerializedScopedData(raw)) {
                RegionScopedData scopedData = RegionScopedData.deserialize(MapParse.map(raw, "managedRegion.data." + extension.key()));
                hydrateScopedExtensionData(region, extension, scopedData);
                continue;
            }

            Object hydrated = ((RegionExtension<Object>) extension).deserializeValue(raw);
            if (hydrated != null) {
                region.setData(extension.key(), hydrated);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private @NotNull Object serializeScopedExtensionData(@NotNull RegionExtension<?> extension,
                                                         @NotNull RegionScopedData scopedData) {
        RegionScopedData serialized = new RegionScopedData();
        for (Map.Entry<String, Object> entry : scopedData.scopes().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            if (extension.type().isInstance(value)) {
                serialized.set(entry.getKey(), ((RegionExtension<Object>) extension).serializeValue(value));
                continue;
            }

            Object hydrated = ((RegionExtension<Object>) extension).deserializeValue(value);
            if (hydrated != null) {
                serialized.set(entry.getKey(), ((RegionExtension<Object>) extension).serializeValue(hydrated));
            }
        }
        return serialized.serialize();
    }

    @SuppressWarnings("unchecked")
    private void hydrateScopedExtensionData(@NotNull SCManagedRegion region,
                                            @NotNull RegionExtension<?> extension,
                                            @NotNull RegionScopedData scopedData) {
        RegionScopedData hydratedScopedData = new RegionScopedData();
        for (Map.Entry<String, Object> entry : scopedData.scopes().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (extension.type().isInstance(value)) {
                hydratedScopedData.set(entry.getKey(), value);
                continue;
            }

            Object hydrated = ((RegionExtension<Object>) extension).deserializeValue(value);
            if (hydrated != null) {
                hydratedScopedData.set(entry.getKey(), hydrated);
            }
        }

        region.setData(extension.key(), hydratedScopedData.isEmpty() ? null : hydratedScopedData);
    }

    private void addExtensionTabCompletions(@NotNull String action,
                                            @NotNull String commandKey,
                                            @NotNull Collection<String[]> completionsList) {
        regionCommand.getCommand().addTabCompletion(action, "{managed-regions}", commandKey);
        regionCommand.getCommand().addTabCompletion(action, "{managed-regions}", commandKey, "g:{managed-region-scopes}");
        completionsList.forEach(completions -> {
            String[] out = new String[completions.length + 3];
            out[0] = action;
            out[1] = "{managed-regions}";
            out[2] = commandKey;
            System.arraycopy(completions, 0, out, 3, completions.length);
            regionCommand.getCommand().addTabCompletion(out);

            String[] scoped = new String[completions.length + 4];
            scoped[0] = action;
            scoped[1] = "{managed-regions}";
            scoped[2] = commandKey;
            scoped[3] = "g:{managed-region-scopes}";
            System.arraycopy(completions, 0, scoped, 4, completions.length);
            regionCommand.getCommand().addTabCompletion(scoped);
        });
    }

    /**
     * Checks whether a managed region applies to the given location.
     *
     * @param region The managed region definition.
     * @param location The location to test.
     * @return True if the region applies, false otherwise.
     */
    private boolean matches(@NotNull SCManagedRegion region, @NotNull Location location) {
        if (location.getWorld() == null || !region.getWorldName().equals(location.getWorld().getName())) {
            return false;
        }

        SCRegion shape = region.getRegion();
        if (shape == null) {
            return true;
        }

        return shape.contains(location);
    }

    private @NotNull String managedRegionKey(@NotNull String worldName, @NotNull String id) {
        return worldName.toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT);
    }

    private void checkManagedRegionId(@NotNull String id) {
        if (!id.matches(MANAGED_REGION_ID_PATTERN)) {
            throw new IllegalArgumentException("Invalid managed region ID: " + id);
        }
    }
}
