package dev.stemcraft.feature.underhalls;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.service.DatabaseServiceImpl;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.sql.*;
import java.util.*;
import static dev.stemcraft.feature.underhalls.UnderhallsStore.Pos;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnderhallsProtectionTest {
    ServerMock server;
    World world;
    STEMCraftAPI api;
    Connection connection;
    UnderhallsStore store;
    UnderhallsProtection protection;
    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"}) void setup() throws Exception {
        server = MockBukkit.mock();
        var fixture = new org.mockbukkit.mockbukkit.world.WorldMock() {
            @Override public org.bukkit.generator.ChunkGenerator getGenerator() { return null; }
        };
        fixture.setName("survival_underhalls");server.addWorld(fixture);world=fixture;
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var plugin = MockBukkit.createMockPlugin();
        var events = api.events();
        doAnswer(call -> {
            Class<? extends Event> type = call.getArgument(0);
            EventHandler handler = call.getArgument(1);
            Listener listener = new Listener() { };
            server.getPluginManager().registerEvent(type, listener, call.getArgument(2), (ignored,e) -> handler.handle(e), plugin, call.getArgument(3));
            return listener;
        }).when(events).register(any(),any(),any(),anyBoolean());
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        var database = new DatabaseServiceImpl(mock(STEMCraft.class),api);
        var field = DatabaseServiceImpl.class.getDeclaredField("connection");
        field.setAccessible(true); field.set(database,connection);
        store = new UnderhallsStore(database); store.load();
        protection = new UnderhallsProtection(api,store,w -> w.getUID().equals(world.getUID()));
        protection.enable();
    }
    @AfterEach void cleanup() throws Exception {
        if (protection != null) protection.disable();
        if (connection != null) connection.close();
        MockBukkit.unmock();
    }
    @Test void generatedAndPlacedBlocksCanBeMinedAndEditsPersist() {
        var builder = server.addPlayer(); var other = server.addPlayer();
        Block generated = world.getBlockAt(8,65,8); generated.setType(Material.END_STONE);
        var denied = new BlockBreakEvent(generated,other); server.getPluginManager().callEvent(denied);
        assertFalse(denied.isCancelled());
        Block placed = world.getBlockAt(12,65,12);
        var before = placed.getState(); placed.setType(Material.END_STONE);
        var placement = new BlockPlaceEvent(placed,before,placed.getRelative(0,-1,0),new ItemStack(Material.END_STONE),builder,true,EquipmentSlot.HAND);
        server.getPluginManager().callEvent(placement);
        assertFalse(placement.isCancelled());
        store.load();
        assertTrue(protection.canBreak(placed));
        var broken = new BlockBreakEvent(placed,other); server.getPluginManager().callEvent(broken);
        assertFalse(broken.isCancelled());
        assertTrue(store.isPlaced(Pos.of(placed)));
        assertTrue(protection.canBreak(generated));
    }
    @Test void RoomsAndCeilingAllowBuildingButCancelledPlacementsAreNotRecorded() {
        var player = server.addPlayer();
        for (int[] coords : new int[][]{{68,65,69},{4,65,4},{12,70,12}}) {
            Block block = world.getBlockAt(coords[0],coords[1],coords[2]);
            var before = block.getState(); block.setType(Material.COBBLESTONE);
            var event = new BlockPlaceEvent(block,before,block.getRelative(0,-1,0),new ItemStack(Material.COBBLESTONE),player,true,EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
            assertTrue(store.isPlaced(Pos.of(block)));
        }
        Block block = world.getBlockAt(14,65,14);
        var before = block.getState(); block.setType(Material.STONE);
        var event = new BlockPlaceEvent(block,before,block.getRelative(0,-1,0),new ItemStack(Material.STONE),player,true,EquipmentSlot.HAND);
        event.setCancelled(true); server.getPluginManager().callEvent(event);
        assertFalse(store.isPlaced(Pos.of(block)));
    }
    @Test void temporaryHeldLightDoesNotPreventPlayerPlacement() {
        var block = world.getBlockAt(12, 66, 12);
        block.setType(Material.LIGHT);
        block.getChunk().getPersistentDataContainer().set(new NamespacedKey("stemcraft", "held_light_12_66_12"),
                org.bukkit.persistence.PersistentDataType.STRING, "AIR");
        var before = block.getState();
        block.setType(Material.COBBLESTONE);
        var event = new BlockPlaceEvent(block, before, block.getRelative(0, -1, 0),
                new ItemStack(Material.COBBLESTONE), server.addPlayer(), true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertTrue(store.isPlaced(Pos.of(block)));
    }

    @Test void generatedDoorUsesNormalInteractionAndPhysics() {
        var block = world.getBlockAt(68,65,66);
        block.setType(Material.DARK_OAK_DOOR);
        var event = new org.bukkit.event.player.PlayerInteractEvent(server.addPlayer(), Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.AIR), block, org.bukkit.block.BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertNotEquals(Event.Result.DENY, event.useInteractedBlock());
        var physics = new BlockPhysicsEvent(block, block.getBlockData());
        server.getPluginManager().callEvent(physics);
        assertFalse(physics.isCancelled());
        assertTrue(protection.canBreak(block));
    }

    @Test void explosionsAndPistonsCanChangeTheMaze() {
        Block wall = world.getBlockAt(8,65,8); wall.setType(Material.END_STONE);
        var entity = mock(org.bukkit.entity.Entity.class);
        when(entity.getWorld()).thenReturn(world);
        var blocks = new ArrayList<>(List.of(wall));
        var explosion = new EntityExplodeEvent(entity,wall.getLocation(),blocks,0,ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertEquals(List.of(wall), blocks);
        var piston = new BlockPistonExtendEvent(world.getBlockAt(7,65,8),List.of(wall),org.bukkit.block.BlockFace.EAST);
        server.getPluginManager().callEvent(piston);
        assertFalse(piston.isCancelled());
    }
}
