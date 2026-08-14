package dev.stemcraft.api.service.placedobject;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record PlacedObjectEntityLink(
    @NotNull UUID entityUuid,
    @NotNull String role
) {
    public PlacedObjectEntityLink {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
