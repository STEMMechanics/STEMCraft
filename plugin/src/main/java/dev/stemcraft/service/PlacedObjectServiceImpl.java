package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.placedobject.PlacedBlockRef;
import dev.stemcraft.api.service.placedobject.PlacedObject;
import dev.stemcraft.api.service.placedobject.PlacedObjectBlockLink;
import dev.stemcraft.api.service.placedobject.PlacedObjectCreateRequest;
import dev.stemcraft.api.service.placedobject.PlacedObjectEntityLink;
import dev.stemcraft.api.service.placedobject.PlacedObjectService;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlacedObjectServiceImpl extends BaseService implements PlacedObjectService {
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private final Gson gson = new Gson();
    private final Map<UUID, PlacedObject> objectsById = new LinkedHashMap<>();
    private final Map<String, UUID> objectIdByBlockKey = new HashMap<>();
    private final Map<UUID, UUID> objectIdByEntityId = new HashMap<>();
    private final Set<String> registeredTypes = new LinkedHashSet<>();

    public PlacedObjectServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        ensureSchema();
        loadAll();
    }

    @Override
    public void registerType(@NotNull String typeId) {
        if (typeId.isBlank()) {
            throw new IllegalArgumentException("typeId must not be blank");
        }
        registeredTypes.add(typeId.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isRegisteredType(@NotNull String typeId) {
        return registeredTypes.contains(typeId.toLowerCase(Locale.ROOT));
    }

    @Override
    public @NotNull PlacedObject create(@NotNull PlacedObjectCreateRequest request) {
        String normalizedType = normalizeType(request.typeId());
        if (!isRegisteredType(normalizedType)) {
            throw new IllegalArgumentException("Unregistered placed object type: " + request.typeId());
        }
        long now = System.currentTimeMillis();
        PlacedObject object = new PlacedObject(UUID.randomUUID(), normalizedType, request.ownerUuid(), request.primaryBlock(), now, now);
        object.blockLinks().addAll(request.blockLinks());
        object.entityLinks().addAll(request.entityLinks());
        object.metadata().putAll(request.metadata());
        save(object);
        return object;
    }

    @Override
    public void save(@NotNull PlacedObject object) {
        object.updatedAt(System.currentTimeMillis());

        String metadataJson = gson.toJson(object.metadata(), STRING_MAP_TYPE);
        api.database().update(
            "INSERT INTO placed_objects (id, type_id, owner_uuid, world, x, y, z, metadata_json, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET "
                + "type_id = excluded.type_id, owner_uuid = excluded.owner_uuid, world = excluded.world, "
                + "x = excluded.x, y = excluded.y, z = excluded.z, metadata_json = excluded.metadata_json, updated_at = excluded.updated_at",
            ps -> {
                ps.setString(1, object.id().toString());
                ps.setString(2, normalizeType(object.typeId()));
                if (object.ownerUuid() == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, object.ownerUuid().toString());
                }
                ps.setString(4, object.primaryBlock().worldName());
                ps.setInt(5, object.primaryBlock().x());
                ps.setInt(6, object.primaryBlock().y());
                ps.setInt(7, object.primaryBlock().z());
                ps.setString(8, metadataJson);
                ps.setLong(9, object.createdAt());
                ps.setLong(10, object.updatedAt());
            }
        );

        api.database().update("DELETE FROM placed_object_blocks WHERE object_id = ?", ps -> ps.setString(1, object.id().toString()));
        for (PlacedObjectBlockLink link : object.blockLinks()) {
            api.database().update(
                "INSERT INTO placed_object_blocks (object_id, world, x, y, z, role) VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, object.id().toString());
                    ps.setString(2, link.block().worldName());
                    ps.setInt(3, link.block().x());
                    ps.setInt(4, link.block().y());
                    ps.setInt(5, link.block().z());
                    ps.setString(6, link.role());
                }
            );
        }

        api.database().update("DELETE FROM placed_object_entities WHERE object_id = ?", ps -> ps.setString(1, object.id().toString()));
        for (PlacedObjectEntityLink link : object.entityLinks()) {
            api.database().update(
                "INSERT INTO placed_object_entities (object_id, entity_uuid, role) VALUES (?, ?, ?)",
                ps -> {
                    ps.setString(1, object.id().toString());
                    ps.setString(2, link.entityUuid().toString());
                    ps.setString(3, link.role());
                }
            );
        }

        index(object);
    }

    @Override
    public boolean delete(@NotNull UUID objectId) {
        PlacedObject removed = objectsById.remove(objectId);
        if (removed == null) {
            return false;
        }

        deindex(removed);
        api.database().update("DELETE FROM placed_object_entities WHERE object_id = ?", ps -> ps.setString(1, objectId.toString()));
        api.database().update("DELETE FROM placed_object_blocks WHERE object_id = ?", ps -> ps.setString(1, objectId.toString()));
        api.database().update("DELETE FROM placed_objects WHERE id = ?", ps -> ps.setString(1, objectId.toString()));
        return true;
    }

    @Override
    public @Nullable PlacedObject find(@NotNull UUID objectId) {
        return objectsById.get(objectId);
    }

    @Override
    public @Nullable PlacedObject findByBlock(@NotNull Location location) {
        UUID objectId = objectIdByBlockKey.get(blockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        return objectId == null ? null : objectsById.get(objectId);
    }

    @Override
    public @Nullable PlacedObject findByEntity(@NotNull UUID entityUuid) {
        UUID objectId = objectIdByEntityId.get(entityUuid);
        return objectId == null ? null : objectsById.get(objectId);
    }

    @Override
    public @NotNull List<PlacedObject> findByOwner(@NotNull UUID ownerUuid) {
        List<PlacedObject> matches = new ArrayList<>();
        for (PlacedObject object : objectsById.values()) {
            if (ownerUuid.equals(object.ownerUuid())) {
                matches.add(object);
            }
        }
        matches.sort(Comparator.comparingLong(PlacedObject::createdAt));
        return matches;
    }

    @Override
    public @NotNull List<PlacedObject> findByType(@NotNull String typeId) {
        String normalizedType = normalizeType(typeId);
        List<PlacedObject> matches = new ArrayList<>();
        for (PlacedObject object : objectsById.values()) {
            if (normalizeType(object.typeId()).equals(normalizedType)) {
                matches.add(object);
            }
        }
        matches.sort(Comparator.comparingLong(PlacedObject::createdAt));
        return matches;
    }

    private void ensureSchema() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS placed_objects (
              id TEXT PRIMARY KEY,
              type_id TEXT NOT NULL,
              owner_uuid TEXT,
              world TEXT NOT NULL,
              x INTEGER NOT NULL,
              y INTEGER NOT NULL,
              z INTEGER NOT NULL,
              metadata_json TEXT NOT NULL DEFAULT '{}',
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            );
            """);
        api.database().execute("CREATE INDEX IF NOT EXISTS placed_objects_owner_idx ON placed_objects (owner_uuid);");
        api.database().execute("CREATE INDEX IF NOT EXISTS placed_objects_type_idx ON placed_objects (type_id);");
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS placed_object_blocks (
              object_id TEXT NOT NULL,
              world TEXT NOT NULL,
              x INTEGER NOT NULL,
              y INTEGER NOT NULL,
              z INTEGER NOT NULL,
              role TEXT NOT NULL,
              PRIMARY KEY (world, x, y, z),
              FOREIGN KEY (object_id) REFERENCES placed_objects(id) ON DELETE CASCADE
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS placed_object_entities (
              object_id TEXT NOT NULL,
              entity_uuid TEXT PRIMARY KEY,
              role TEXT NOT NULL,
              FOREIGN KEY (object_id) REFERENCES placed_objects(id) ON DELETE CASCADE
            );
            """);
    }

    private void loadAll() {
        objectsById.clear();
        objectIdByBlockKey.clear();
        objectIdByEntityId.clear();

        api.database().queryEach(
            "SELECT id, type_id, owner_uuid, world, x, y, z, metadata_json, created_at, updated_at FROM placed_objects",
            null,
            rs -> {
                PlacedObject object = new PlacedObject(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("type_id"),
                    parseUuid(rs.getString("owner_uuid")),
                    new PlacedBlockRef(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                );
                String metadataJson = rs.getString("metadata_json");
                if (metadataJson != null && !metadataJson.isBlank()) {
                    Map<String, String> metadata = gson.fromJson(metadataJson, STRING_MAP_TYPE);
                    if (metadata != null) {
                        object.metadata().putAll(metadata);
                    }
                }
                objectsById.put(object.id(), object);
                registeredTypes.add(normalizeType(object.typeId()));
            }
        );

        api.database().queryEach(
            "SELECT object_id, world, x, y, z, role FROM placed_object_blocks",
            null,
            rs -> {
                PlacedObject object = objectsById.get(UUID.fromString(rs.getString("object_id")));
                if (object == null) {
                    return;
                }
                object.blockLinks().add(new PlacedObjectBlockLink(
                    new PlacedBlockRef(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                    rs.getString("role")
                ));
            }
        );

        api.database().queryEach(
            "SELECT object_id, entity_uuid, role FROM placed_object_entities",
            null,
            rs -> {
                PlacedObject object = objectsById.get(UUID.fromString(rs.getString("object_id")));
                if (object == null) {
                    return;
                }
                object.entityLinks().add(new PlacedObjectEntityLink(UUID.fromString(rs.getString("entity_uuid")), rs.getString("role")));
            }
        );

        for (PlacedObject object : objectsById.values()) {
            index(object);
        }
    }

    private void index(@NotNull PlacedObject object) {
        PlacedObject previous = objectsById.put(object.id(), object);
        if (previous != null) {
            deindex(previous);
        }
        objectIdByBlockKey.put(blockKey(object.primaryBlock()), object.id());
        for (PlacedObjectBlockLink link : object.blockLinks()) {
            objectIdByBlockKey.put(blockKey(link.block()), object.id());
        }
        for (PlacedObjectEntityLink link : object.entityLinks()) {
            objectIdByEntityId.put(link.entityUuid(), object.id());
        }
    }

    private void deindex(@NotNull PlacedObject object) {
        objectIdByBlockKey.remove(blockKey(object.primaryBlock()));
        for (PlacedObjectBlockLink link : object.blockLinks()) {
            objectIdByBlockKey.remove(blockKey(link.block()));
        }
        for (PlacedObjectEntityLink link : object.entityLinks()) {
            objectIdByEntityId.remove(link.entityUuid());
        }
    }

    private @NotNull String normalizeType(@NotNull String typeId) {
        return typeId.toLowerCase(Locale.ROOT);
    }

    private @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @NotNull String blockKey(@NotNull PlacedBlockRef block) {
        return blockKey(block.worldName(), block.x(), block.y(), block.z());
    }

    private @NotNull String blockKey(@NotNull String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }
}
