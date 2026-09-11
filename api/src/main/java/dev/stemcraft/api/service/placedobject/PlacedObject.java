package dev.stemcraft.api.service.placedobject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlacedObject {
    private final @NotNull UUID id;
    private final @NotNull String typeId;
    private @Nullable UUID ownerUuid;
    private @NotNull PlacedBlockRef primaryBlock;
    private final @NotNull List<PlacedObjectBlockLink> blockLinks = new ArrayList<>();
    private final @NotNull List<PlacedObjectEntityLink> entityLinks = new ArrayList<>();
    private final @NotNull Map<String, String> metadata = new LinkedHashMap<>();
    private final long createdAt;
    private long updatedAt;

    public PlacedObject(@NotNull UUID id,
                        @NotNull String typeId,
                        @Nullable UUID ownerUuid,
                        @NotNull PlacedBlockRef primaryBlock,
                        long createdAt,
                        long updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.ownerUuid = ownerUuid;
        this.primaryBlock = Objects.requireNonNull(primaryBlock, "primaryBlock");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public @NotNull UUID id() {
        return id;
    }

    public @NotNull String typeId() {
        return typeId;
    }

    public @Nullable UUID ownerUuid() {
        return ownerUuid;
    }

    public void ownerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public @NotNull PlacedBlockRef primaryBlock() {
        return primaryBlock;
    }

    public void primaryBlock(@NotNull PlacedBlockRef primaryBlock) {
        this.primaryBlock = Objects.requireNonNull(primaryBlock, "primaryBlock");
    }

    public @NotNull List<PlacedObjectBlockLink> blockLinks() {
        return blockLinks;
    }

    public @NotNull List<PlacedObjectEntityLink> entityLinks() {
        return entityLinks;
    }

    public @NotNull Map<String, String> metadata() {
        return metadata;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
