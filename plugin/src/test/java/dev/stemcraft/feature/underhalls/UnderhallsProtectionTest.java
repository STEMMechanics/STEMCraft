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
        world = server.addSimpleWorld("survival_underhalls");
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
    @Test void generatedBlocksAreProtectedButAnotherPlayerCanRemovePlacedBlocksAfterReload() {
        var builder = server.addPlayer(); var other = server.addPlayer();
        Block generated = world.getBlockAt(8,65,8); generated.setType(Material.END_STONE);
        var denied = new BlockBreakEvent(generated,other); server.getPluginManager().callEvent(denied);
        assertTrue(denied.isCancelled());
        Block placed = world.getBlockAt(12,65,12);
        var before = placed.getState(); placed.setType(Material.END_STONE);
        var placement = new BlockPlaceEvent(placed,before,placed.getRelative(0,-1,0),new ItemStack(Material.END_STONE),builder,true,EquipmentSlot.HAND);
        server.getPluginManager().callEvent(placement);
        assertFalse(placement.isCancelled());
        store.load();
        assertTrue(protection.canBreak(placed));
        var broken = new BlockBreakEvent(placed,other); server.getPluginManager().callEvent(broken);
        assertFalse(broken.isCancelled());
        assertFalse(store.isPlaced(Pos.of(placed)));
        assertFalse(protection.canBreak(generated));
    }
    @Test void cancelledPlacementsAndReservedRoomsNeverBecomePlayerOwned() {
        var player = server.addPlayer();
        for (int[] coords : new int[][]{{68,65,69},{4,65,4},{12,70,12}}) {
            Block block = world.getBlockAt(coords[0],coords[1],coords[2]);
            var before = block.getState(); block.setType(Material.COBBLESTONE);
            var event = new BlockPlaceEvent(block,before,block.getRelative(0,-1,0),new ItemStack(Material.COBBLESTONE),player,true,EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);
            assertTrue(event.isCancelled());
            assertFalse(store.isPlaced(Pos.of(block)));
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

    @Test void generatedDoorOpensAndClosesBothHalvesWithoutTerrainPhysics() {
        var bottom = world.getBlockAt(68,65,66);
        var top = bottom.getRelative(0,1,0);
        var door = (org.bukkit.block.data.type.Door) Material.DARK_OAK_DOOR.createBlockData();
        door.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
        bottom.setBlockData(door,false);
        door.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        top.setBlockData(door,false);
        var player=server.addPlayer();
        for (Block clicked : List.of(top,bottom)) {
            boolean opening = !((org.bukkit.block.data.type.Door)bottom.getBlockData()).isOpen();
            var event = new org.bukkit.event.player.PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,clicked,
                    org.bukkit.block.BlockFace.NORTH,EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);
            assertEquals(opening,((org.bukkit.block.data.type.Door)bottom.getBlockData()).isOpen());
            assertEquals(opening,((org.bukkit.block.data.type.Door)top.getBlockData()).isOpen());
            assertEquals(Event.Result.DENY,event.useInteractedBlock());
            var offhand = new org.bukkit.event.player.PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,clicked,
                    org.bukkit.block.BlockFace.NORTH,EquipmentSlot.OFF_HAND);
            server.getPluginManager().callEvent(offhand);
            assertEquals(opening,((org.bukkit.block.data.type.Door)bottom.getBlockData()).isOpen());
        }
        assertFalse(protection.canBreak(bottom));
        assertFalse(protection.canBreak(top));
    }

    @Test void explosionsAndPistonsCannotRemoveOrMoveTheMaze() {
        Block wall = world.getBlockAt(8,65,8); wall.setType(Material.END_STONE);
        var entity = mock(org.bukkit.entity.Entity.class);
        when(entity.getWorld()).thenReturn(world);
        var blocks = new ArrayList<>(List.of(wall));
        var explosion = new EntityExplodeEvent(entity,wall.getLocation(),blocks,0,ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertTrue(blocks.isEmpty());
        var piston = new BlockPistonExtendEvent(world.getBlockAt(7,65,8),List.of(wall),org.bukkit.block.BlockFace.EAST);
        server.getPluginManager().callEvent(piston);
        assertTrue(piston.isCancelled());
    }
}
