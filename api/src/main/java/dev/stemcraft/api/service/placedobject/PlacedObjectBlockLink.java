package dev.stemcraft.api.service.placedobject;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record PlacedObjectBlockLink(
    @NotNull PlacedBlockRef block,
    @NotNull String role
) {
    public PlacedObjectBlockLink {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
