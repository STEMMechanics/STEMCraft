package dev.stemcraft.feature.underhalls;

import dev.stemcraft.chunkgen.UnderhallsGenerator;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnderhallsAtmosphereTest {
    @BeforeAll static void start() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }

    @Test void detailsAreDeterministicConfinedAndAvoidDoorRooms() {
        World world = mock(World.class);
        var generator = new UnderhallsGenerator();
        when(world.getGenerator()).thenReturn(generator);
        when(world.getSeed()).thenReturn(901L);
        int low = 0, high = 0, skinny = 0;
        for (int cx = -8; cx < 8; cx++) for (int cz = -8; cz < 8; cz++) {
            var plans = UnderhallsPassages.plan(world, cx, cz, 1);
            assertEquals(plans, UnderhallsPassages.plan(world, cx, cz, 1));
            assertTrue(UnderhallsPassages.plan(world, cx, cz, 0).isEmpty());
            for (var plan : plans) {
                boolean raised = plan.stream().anyMatch(c -> c.material() == Material.AIR && c.y() == 4);
                if (raised) {
                    high++;
                    assertEquals(2, plan.stream().filter(c -> c.material() == Material.AIR).count());
                    // Both sides have a three-step approach, with two blocks of headroom at the sill.
                    assertEquals(12, plan.stream().filter(c -> c.material() == Material.SMOOTH_SANDSTONE).count());
                } else if (plan.stream().anyMatch(c -> c.material() == Material.AIR)) low++;
                else skinny++;
                for (var c : plan) {
                    assertTrue(c.x() >= 0 && c.x() < 16 && c.z() >= 0 && c.z() < 16);
                    assertTrue(c.y() >= 1 && c.y() <= 5);
                    assertFalse(generator.roomAt(world, cx * 16 + c.x(), cz * 16 + c.z()));
                }
            }
        }
        assertTrue(low > 0 && high > 0 && skinny > 0);
    }
    @Test void retrofitPreservesPlayerBlocksAndRunsOnlyOnce() {
        var world = new org.mockbukkit.mockbukkit.world.WorldMock(Material.AIR, 0, 256, 64) {
            private final UnderhallsGenerator generator = new UnderhallsGenerator();
            @Override public org.bukkit.generator.ChunkGenerator getGenerator() { return generator; }
            @Override public long getSeed() { return 901L; }
        };
        var store = mock(UnderhallsStore.class);
        int cx = 0;
        while (UnderhallsPassages.plan(world, cx, 0, 1).size() < 2) cx++;
        var chunk = world.getChunkAt(cx, 0);
        var plans = UnderhallsPassages.plan(world, cx, 0, 1);
        var protectedChange = plans.getFirst().getFirst();
        var protectedBlock = chunk.getBlock(protectedChange.x(), 64 + protectedChange.y(), protectedChange.z());
        protectedBlock.setType(Material.DIAMOND_BLOCK);
        when(store.isPlaced(UnderhallsStore.Pos.of(protectedBlock))).thenReturn(true);
        UnderhallsPassages.upgrade(chunk, store, 1);
        assertEquals(Material.DIAMOND_BLOCK, protectedBlock.getType());
        for (var change : plans.get(1)) assertEquals(change.material(),
            chunk.getBlock(change.x(), 64 + change.y(), change.z()).getType());
        var changed = plans.get(1).getFirst();
        var block = chunk.getBlock(changed.x(), 64 + changed.y(), changed.z());
        block.setType(Material.GOLD_BLOCK);
        UnderhallsPassages.upgrade(chunk, store, 1);
        assertEquals(Material.GOLD_BLOCK, block.getType());
    }
    @Test void populationCapDisabledAndPeacefulPreventSpawning() {
        World world = mock(World.class);
        when(world.getGenerator()).thenReturn(new UnderhallsGenerator());
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(world.getPlayers()).thenReturn(java.util.List.of(player));
        var stalker = mock(Skeleton.class, RETURNS_DEEP_STUBS);
        when(stalker.getPersistentDataContainer().has(any(NamespacedKey.class),
            eq(org.bukkit.persistence.PersistentDataType.BYTE))).thenReturn(true);
        when(world.getLivingEntities()).thenReturn(java.util.List.of(stalker));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(java.util.List.of(world));
            new UnderhallsMobs().tick(true, 120, 1);
            verify(world).getLivingEntities();
            verify(player, never()).getLocation();
            clearInvocations(world);
            new UnderhallsMobs().tick(false, 120, 3);
            verifyNoInteractions(world);
            when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
            new UnderhallsMobs().tick(true, 120, 3);
            verify(world, never()).getLivingEntities();
        }
    }
    @Test void oldBedrockShellBecomesVoidAndMineableRoofWithoutRemovingBuilds() {
        var world = new org.mockbukkit.mockbukkit.world.WorldMock(Material.AIR, 0, 128, 64);
        var chunk = world.getChunkAt(0,0);
        var store = mock(UnderhallsStore.class);
        for (int y=0;y<64;y++) chunk.getBlock(4,y,4).setType(Material.BEDROCK);
        chunk.getBlock(4,64,4).setType(Material.SMOOTH_SANDSTONE);
        chunk.getBlock(4,71,4).setType(Material.BEDROCK);
        chunk.getBlock(5,12,4).setType(Material.DIAMOND_BLOCK);
        var placed = chunk.getBlock(6,12,4); placed.setType(Material.BEDROCK);
        when(store.isPlaced(UnderhallsStore.Pos.of(placed))).thenReturn(true);
        int passes=1;
        while (!UnderhallsTerrain.upgrade(chunk,store)) assertTrue(++passes<=8);
        assertEquals(8,passes);
        for (int y=0;y<64;y++) assertEquals(Material.AIR,chunk.getBlock(4,y,4).getType());
        assertEquals(Material.SMOOTH_SANDSTONE,chunk.getBlock(4,64,4).getType());
        assertEquals(Material.SMOOTH_SANDSTONE,chunk.getBlock(4,71,4).getType());
        assertEquals(Material.DIAMOND_BLOCK,chunk.getBlock(5,12,4).getType());
        assertEquals(Material.BEDROCK,placed.getType());
        assertTrue(UnderhallsTerrain.upgrade(chunk,store));
    }
    @Test void stalkerHasSwordDurabilityAndNoEquipmentPickup() {
        Skeleton skeleton = mock(Skeleton.class, RETURNS_DEEP_STUBS);
        UnderhallsMobs.configure(skeleton);
        var equipment = skeleton.getEquipment();
        var sword = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(equipment).setItemInMainHand(sword.capture());
        assertEquals(Material.IRON_SWORD, sword.getValue().getType());
        verify(equipment).setItemInMainHandDropChance(0);
        verify(skeleton).setCanPickupItems(false);
        verify(skeleton).setPersistent(false);
        verify(skeleton).setRemoveWhenFarAway(true);
        verify(skeleton).setSilent(false);
        verify(skeleton.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(60);
        verify(skeleton.getAttribute(Attribute.ARMOR)).setBaseValue(12);
        verify(skeleton).setHealth(60);
    }
}
