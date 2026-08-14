package dev.stemcraft.api.service.placedobject;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface PlacedObjectService {

    void registerType(@NotNull String typeId);

    boolean isRegisteredType(@NotNull String typeId);

    @NotNull PlacedObject create(@NotNull PlacedObjectCreateRequest request);

    void save(@NotNull PlacedObject object);

    boolean delete(@NotNull UUID objectId);

    @Nullable PlacedObject find(@NotNull UUID objectId);

    @Nullable PlacedObject findByBlock(@NotNull Location location);

    @Nullable PlacedObject findByEntity(@NotNull UUID entityUuid);

    @NotNull List<PlacedObject> findByOwner(@NotNull UUID ownerUuid);

    @NotNull List<PlacedObject> findByType(@NotNull String typeId);
}
