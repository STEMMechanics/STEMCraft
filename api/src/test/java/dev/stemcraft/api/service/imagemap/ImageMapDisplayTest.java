package dev.stemcraft.api.service.imagemap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ImageMapDisplayTest {
    @Test
    void validatesDimensionsAndDefensivelyCopiesLocation() {
        Location location = new Location(mock(World.class), 1, 2, 3);
        ImageMapDisplay display = new ImageMapDisplay("board", location, BlockFace.NORTH, 4, 3);
        location.setX(99);
        assertEquals(1, display.backingBlock().getX());
        Location returned = display.backingBlock();
        returned.setX(42);
        assertEquals(1, display.backingBlock().getX());
        assertThrows(IllegalArgumentException.class,
            () -> new ImageMapDisplay("bad", location, BlockFace.NORTH, 0, 3));
    }
}
