package dev.stemcraft.api.service.placedobject;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record PlacedBlockRef(
    @NotNull String worldName,
    int x,
    int y,
    int z
) {
    public PlacedBlockRef {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
    }

    public static @NotNull PlacedBlockRef of(@NotNull Block block) {
        return new PlacedBlockRef(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static @NotNull PlacedBlockRef of(@NotNull Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new PlacedBlockRef(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean matches(@NotNull Block block) {
        return worldName.equals(block.getWorld().getName()) && x == block.getX() && y == block.getY() && z == block.getZ();
    }

    public @Nullable Location resolve() {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }
}
