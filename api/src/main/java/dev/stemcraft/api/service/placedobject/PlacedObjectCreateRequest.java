package dev.stemcraft.api.service.placedobject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PlacedObjectCreateRequest(
    @NotNull String typeId,
    @Nullable UUID ownerUuid,
    @NotNull PlacedBlockRef primaryBlock,
    @NotNull List<PlacedObjectBlockLink> blockLinks,
    @NotNull List<PlacedObjectEntityLink> entityLinks,
    @NotNull Map<String, String> metadata
) {
    public PlacedObjectCreateRequest {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(primaryBlock, "primaryBlock");
        Objects.requireNonNull(blockLinks, "blockLinks");
        Objects.requireNonNull(entityLinks, "entityLinks");
        Objects.requireNonNull(metadata, "metadata");
        if (typeId.isBlank()) {
            throw new IllegalArgumentException("typeId must not be blank");
        }
        blockLinks = List.copyOf(blockLinks);
        entityLinks = List.copyOf(entityLinks);
        metadata = Map.copyOf(metadata);
    }
}
