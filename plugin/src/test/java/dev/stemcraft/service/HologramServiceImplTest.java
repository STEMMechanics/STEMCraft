package dev.stemcraft.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HologramServiceImplTest {
    private WorldMock world;
    private WorldMock otherWorld;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("hologram-tests");
        otherWorld = server.addSimpleWorld("other-world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void matchesLocationsInSameChunkWithoutResolvingChunk() {
        Location location = new Location(world, 31.9, 80, 47.9);

        assertTrue(HologramServiceImpl.isLocationInChunk(location, world, 1, 2));
        assertFalse(HologramServiceImpl.isLocationInChunk(location, world, 2, 2));
    }

    @Test
    void handlesNegativeChunkCoordinates() {
        Location location = new Location(world, -1.2, 80, -16.0);

        assertTrue(HologramServiceImpl.isLocationInChunk(location, world, -1, -1));
        assertFalse(HologramServiceImpl.isLocationInChunk(location, world, 0, -1));
    }

    @Test
    void rejectsNullAndWrongWorldLocations() {
        assertFalse(HologramServiceImpl.isLocationInChunk(null, world, 0, 0));
        assertFalse(HologramServiceImpl.isLocationInChunk(new Location(otherWorld, 0, 64, 0), world, 0, 0));
    }

    @Test
    void recognisesCitizensArmorStandHelperHolograms() {
        ArmorStand stand = mock(ArmorStand.class);
        when(stand.hasMetadata("NPC")).thenReturn(true);
        when(stand.isMarker()).thenReturn(true);
        when(stand.isVisible()).thenReturn(false);
        when(stand.isCustomNameVisible()).thenReturn(true);
        when(stand.customName()).thenReturn(Component.text("Guide"));

        assertTrue(HologramServiceImpl.isLikelyCitizensHologram(stand));
    }

    @Test
    void rejectsRegularCitizensArmorStandNpcs() {
        ArmorStand stand = mock(ArmorStand.class);
        when(stand.hasMetadata("NPC")).thenReturn(true);
        when(stand.isMarker()).thenReturn(false);
        when(stand.isVisible()).thenReturn(false);
        when(stand.isCustomNameVisible()).thenReturn(true);
        when(stand.customName()).thenReturn(Component.text("Guide"));

        assertFalse(HologramServiceImpl.isLikelyCitizensHologram(stand));
    }

    @Test
    void recognisesCitizensTextDisplaysWithText() {
        TextDisplay display = mock(TextDisplay.class);
        when(display.hasMetadata("NPC")).thenReturn(true);
        when(display.text()).thenReturn(Component.text("Quest"));

        assertTrue(HologramServiceImpl.isLikelyCitizensHologram(display));
    }

    @Test
    void lineOfSightFailsWhenRayTraceHitsBlock() {
        Player player = mock(Player.class);
        World rayTraceWorld = mock(World.class);
        Location eye = new Location(rayTraceWorld, 0, 64, 0);
        Location target = new Location(rayTraceWorld, 0, 64, 5);

        when(player.getWorld()).thenReturn(rayTraceWorld);
        when(player.getEyeLocation()).thenReturn(eye);
        when(rayTraceWorld.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(), anyBoolean()))
            .thenReturn(new RayTraceResult(target.toVector()));

        assertFalse(HologramServiceImpl.hasClearLineOfSight(player, target));
    }

    @Test
    void lineOfSightSucceedsWhenNoBlockIsHit() {
        Player player = mock(Player.class);
        World rayTraceWorld = mock(World.class);
        Location eye = new Location(rayTraceWorld, 0, 64, 0);
        Location target = new Location(rayTraceWorld, 0, 64, 5);

        when(player.getWorld()).thenReturn(rayTraceWorld);
        when(player.getEyeLocation()).thenReturn(eye);
        when(rayTraceWorld.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(), anyBoolean()))
            .thenReturn(null);

        assertTrue(HologramServiceImpl.hasClearLineOfSight(player, target));
    }
}
