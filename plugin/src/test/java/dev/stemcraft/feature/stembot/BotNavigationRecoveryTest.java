package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

class BotNavigationRecoveryTest {
    @AfterEach
    void tearDown() {
        if(MockBukkit.isMocked()) MockBukkit.unmock();
    }

    @Test
    void scansElevatedLandingsAndExcludesHazardsAndBlockedHeadroom() {
        World world=MockBukkit.mock().addSimpleWorld("world");
        world.loadChunk(-810 >> 4, 305 >> 4);
        Location from=new Location(world,-806,70,305);
        world.getBlockAt(-810,74,305).setType(Material.STONE);
        world.getBlockAt(-802,69,305).setType(Material.MAGMA_BLOCK);
        world.getBlockAt(-806,69,309).setType(Material.STONE);
        world.getBlockAt(-806,71,309).setType(Material.STONE);
        var points=BotNavigationRecovery.candidates(from,new Location(world,-811,75,293));
        assertTrue(points.contains(new Location(world,-809.5,75,305.5)));
        assertFalse(points.contains(new Location(world,-801.5,70,305.5)));
        assertFalse(points.contains(new Location(world,-805.5,70,309.5)));
        assertTrue(points.size()<=64);
    }

    @Test
    void doesNotReadBlocksInUnloadedChunks() {
        World world=mock(World.class);
        assertTrue(BotNavigationRecovery.candidates(new Location(world,0,64,0),new Location(world,10,70,0)).isEmpty());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }

    @Test
    void doesNotSearchAcrossWorlds() {
        assertTrue(BotNavigationRecovery.candidates(new Location(mock(World.class),0,64,0),
            new Location(mock(World.class),10,70,0)).isEmpty());
    }
}
