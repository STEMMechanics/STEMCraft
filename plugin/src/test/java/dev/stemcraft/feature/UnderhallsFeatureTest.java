package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.config.ConfigSectionImpl;
import dev.stemcraft.feature.underhalls.UnderhallsStore;
import dev.stemcraft.service.DatabaseServiceImpl;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.stemcraft.feature.underhalls.UnderhallsStore.Pos;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnderhallsFeatureTest {
    static class TestWorld extends WorldMock {
        private final boolean maze;
        private final Map<String, org.mockbukkit.mockbukkit.block.BlockMock> testBlocks = new HashMap<>();
        TestWorld(String name, boolean maze) { super(Material.DIRT,-64,320,64); setName(name); this.maze=maze; }
        @Override public org.mockbukkit.mockbukkit.block.BlockMock getBlockAt(int x, int y, int z) {
            // MockBukkit has no bounding-box implementation. These fixtures use full-cube floors.
            return testBlocks.computeIfAbsent(x + "," + y + "," + z, key -> {
                var block = spy(super.getBlockAt(x, y, z));
                doReturn(new org.bukkit.util.BoundingBox(x, y, z, x + 1, y + 1, z + 1)).when(block).getBoundingBox();
                doAnswer(ignored -> !block.getType().isSolid()).when(block).isPassable();
                return block;
            });
        }
        @Override public ChunkGenerator getGenerator() { return maze ? new UnderhallsGenerator() : null; }
        @Override public CompletableFuture<Chunk> getChunkAtAsync(int x,int z,boolean gen,boolean urgent) {
            return CompletableFuture.completedFuture(getChunkAt(x,z));
        }
    }
    ServerMock server;
    TestWorld source, maze;
    STEMCraftAPI api;
    UnderhallsFeature feature;
    DatabaseServiceImpl database;
    Connection connection;
    MockedStatic<STEMCraft> pluginAccess;
    @BeforeEach @SuppressWarnings({"unchecked","rawtypes"}) void setup() throws Exception {
        server=MockBukkit.mock();
        source=new TestWorld("survival",false); maze=new TestWorld("survival_underhalls",true);
        server.addWorld(source); server.addWorld(maze);
        api=mock(STEMCraftAPI.class,RETURNS_DEEP_STUBS);
        var plugin=MockBukkit.createMockPlugin();
        pluginAccess=mockStatic(STEMCraft.class);
        pluginAccess.when(STEMCraft::getPlugin).thenReturn(mock(STEMCraft.class));
        var events=api.events();
        doAnswer(call -> {
            Class<? extends Event> type=call.getArgument(0); EventHandler handler=call.getArgument(1);
            Listener listener=new Listener(){};
            server.getPluginManager().registerEvent(type,listener,call.getArgument(2),(unused,e)->handler.handle(e),plugin,call.getArgument(3));
            return listener;
        }).when(events).register(any(),any(),any(),anyBoolean());
        var tasks=api.tasks();
        doAnswer(call->{ ((Runnable)call.getArgument(0)).run(); return null; }).when(tasks).nextTick(any());
        doAnswer(call->{ ((Runnable)call.getArgument(0)).run(); return null; }).when(tasks).runSync(any());
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        database=new DatabaseServiceImpl(mock(STEMCraft.class),api);
        var field=DatabaseServiceImpl.class.getDeclaredField("connection"); field.setAccessible(true); field.set(database,connection);
        when(api.database()).thenReturn(database);
        var yaml=new YamlConfiguration(); yaml.set("entrances.enabled",false);
        ConfigSection config=new ConfigSectionImpl(null,yaml);
        feature=new UnderhallsFeature(api) { @Override protected ConfigSection getConfigSection(){ return config; } };
        feature.onEnable();
    }
    @AfterEach void cleanup() throws Exception {
        if(feature!=null)feature.onDisable();
        if(connection!=null)connection.close();
        if(pluginAccess!=null)pluginAccess.close();
        MockBukkit.unmock();
    }
    boolean entrance(Block block) throws Exception {
        var method=UnderhallsFeature.class.getDeclaredMethod("createEntrance",Block.class,Player.class,boolean.class);
        method.setAccessible(true); return ((UnderhallsFeature.EntranceResult)method.invoke(feature,block,null,false)).created();
    }
    UnderhallsStore state() { var store=new UnderhallsStore(database); store.load(); return store; }
    @SuppressWarnings("unchecked") void clearCooldowns() throws Exception {
        var field=UnderhallsFeature.class.getDeclaredField("cooldowns");field.setAccessible(true);((Map<UUID,Long>)field.get(feature)).clear();
    }
    void walk(Player player, Location to) throws Exception {
        Location from=to.clone().add(0,0,-1);
        player.teleport(to); clearCooldowns();
        server.getPluginManager().callEvent(new PlayerMoveEvent(player,from,to));
    }
    @Test void doorwaysTransferThenStayRetiredAfterBreakAndReload() throws Exception {
        Block origin=source.getBlockAt(104,65,104);
        source.getChunkAt(6,6);
        assertTrue(entrance(origin));
        var player=server.addPlayer();
        walk(player,origin.getLocation().add(.5,0,.5));
        assertEquals(maze,player.getWorld());
        assertEquals(4,player.getLocation().getBlockX());
        var broken=new BlockBreakEvent(origin,player); server.getPluginManager().callEvent(broken);
        assertFalse(broken.isCancelled());
        assertTrue(state().entrance(Pos.of(origin)).retired());
        // Even rebuilding the exact materials must not reactivate the record.
        assertFalse(entrance(origin));
        walk(player,origin.getLocation().add(.5,0,.5));
        assertEquals(source,player.getWorld());
    }
    @Test void fixedRoomExitSurvivesEntranceDestructionAndNeverMovesWhenBlocked() throws Exception {
        var player=server.addPlayer();
        Block entrance=source.getBlockAt(104,65,104);
        source.getChunkAt(6,6);
        assertTrue(entrance(entrance));
        server.getPluginManager().callEvent(new BlockBreakEvent(entrance,player));
        assertTrue(state().entrance(Pos.of(entrance)).retired());
        Location room=new Location(maze,68.5,65,69.5);
        walk(player,room);
        assertEquals(source,player.getWorld());
        Location destination=player.getLocation().clone();
        Pos roomKey=Pos.of(room.getBlock());
        assertEquals(Pos.of(destination.getBlock()),state().exit(roomKey));
        destination.getBlock().setType(Material.STONE);
        walk(player,room);
        assertEquals(maze,player.getWorld());
        assertEquals(Pos.of(destination.getBlock()),state().exit(roomKey));
        destination.getBlock().setType(Material.AIR);
        walk(player,room);
        assertEquals(destination,player.getLocation());
    }

    @Test void portalProtectionCanVetoEntranceWithoutSavingOrBuildingAnything() throws Exception {
        var plugin=MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvent(dev.stemcraft.api.event.world.SurvivalPortalActivateEvent.class,
            new Listener(){},EventPriority.HIGHEST,(listener,event) ->
                ((dev.stemcraft.api.event.world.SurvivalPortalActivateEvent)event).setCancelled(true),plugin);
        Block origin=source.getBlockAt(104,65,104);source.getChunkAt(6,6);
        assertFalse(entrance(origin));
        assertEquals(Material.AIR,origin.getType());
        assertTrue(state().entrances().isEmpty());
    }

    @Test void removingDoorSupportImmediatelyRetiresEntrance() throws Exception {
        Block origin=source.getBlockAt(104,65,104);source.getChunkAt(6,6);
        assertTrue(entrance(origin));
        server.getPluginManager().callEvent(new BlockBreakEvent(origin.getRelative(0,-1,0),server.addPlayer()));
        assertTrue(state().entrance(Pos.of(origin)).retired());
    }

    @Test void grassOnOtherwiseClearGroundDoesNotPreventManualDoorway() throws Exception {
        Block origin=source.getBlockAt(104,65,104);source.getChunkAt(6,6);
        origin.setType(Material.SHORT_GRASS);
        boolean created = entrance(origin);
        assertTrue(created);
    }
    @Test void missingMazeMustBeCreatedWithItsGeneratorBeforeTryingToLoadIt() throws Exception {
        String name = "survival_new_underhalls";
        var field = UnderhallsFeature.class.getDeclaredField("mazeName");
        field.setAccessible(true); field.set(feature, name);
        var normal = mock(World.class);
        when(normal.getName()).thenReturn(name);
        when(api.worlds().worldExists(name)).thenReturn(false);
        // WorldService.loadWorld creates a normal world when no generator metadata exists.
        when(api.worlds().loadWorld(name)).thenReturn(normal);
        when(api.worlds().createWorld(name, "underhalls", "", source.getSeed())).thenReturn(maze);
        var method = UnderhallsFeature.class.getDeclaredMethod("ensureMaze");
        method.setAccessible(true);
        World actual = (World) method.invoke(feature);
        assertSame(maze, actual);
        verify(api.worlds(), never()).loadWorld(name);
    }

    @Test void existingNormalWorldIsRejectedWithoutBeingLoadedOrRecreated() throws Exception {
        var field = UnderhallsFeature.class.getDeclaredField("mazeName");
        field.setAccessible(true); field.set(feature, "existing_normal");
        when(api.worlds().worldExists("existing_normal")).thenReturn(true);
        when(api.worlds().getConfigSection("existing_normal").getString("generator.key", "")).thenReturn("normal");
        var method = UnderhallsFeature.class.getDeclaredMethod("ensureMaze"); method.setAccessible(true);
        assertNull(method.invoke(feature));
        verify(api.worlds(), never()).loadWorld(anyString());
        verify(api.worlds(), never()).createWorld(anyString(), anyString(), anyString(), any());
        var error = UnderhallsFeature.class.getDeclaredField("mazeFailure"); error.setAccessible(true);
        assertTrue(((String)error.get(feature)).contains("new unused name"));
    }

    @Test void existingUnderhallsUsesSavedGeneratorAndIsNotRecreated() throws Exception {
        var field = UnderhallsFeature.class.getDeclaredField("mazeName");
        field.setAccessible(true); field.set(feature, "saved_underhalls");
        when(api.worlds().worldExists("saved_underhalls")).thenReturn(true);
        when(api.worlds().getConfigSection("saved_underhalls").getString("generator.key", "")).thenReturn("stemcraft:underhalls");
        when(api.worlds().loadWorld("saved_underhalls")).thenReturn(maze);
        var method = UnderhallsFeature.class.getDeclaredMethod("ensureMaze"); method.setAccessible(true);
        assertSame(maze, method.invoke(feature));
        verify(api.worlds(), never()).createWorld(anyString(), anyString(), anyString(), any());
    }

    @Test void manualDoorwayWorksOnBuiltFloorAndReportsSolidObstructions() throws Exception {
        Block origin=source.getBlockAt(104,65,104); source.getChunkAt(6,6);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)origin.getRelative(x,-1,z).setType(Material.STONE_BRICKS);
        origin.getRelative(1,1,0).setType(Material.GLASS);
        var method=UnderhallsFeature.class.getDeclaredMethod("createEntrance",Block.class,Player.class,boolean.class);
        method.setAccessible(true);
        var rejected=(UnderhallsFeature.EntranceResult)method.invoke(feature,origin,null,false);
        assertFalse(rejected.created());
        assertTrue(rejected.reason().contains("glass at 105, 66, 104"));
        assertTrue(state().entrances().isEmpty());
        origin.getRelative(1,1,0).setType(Material.AIR);
        assertTrue(entrance(origin));
    }

}
