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
        private final UnderhallsGenerator generator = new UnderhallsGenerator();
        private final Map<String,org.mockbukkit.mockbukkit.world.ChunkMock> testChunks=new HashMap<>();
        @Override public org.mockbukkit.mockbukkit.world.ChunkMock getChunkAt(int x,int z) {
            return testChunks.computeIfAbsent(x + "," + z,key -> {
                var chunk=spy(super.getChunkAt(x,z));
                doReturn(new org.bukkit.block.BlockState[0]).when(chunk).getTileEntities();
                return chunk;
            });
        }
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
        @Override public ChunkGenerator getGenerator() { return maze ? generator : null; }
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
        assertEquals(68,player.getLocation().getBlockX());
        assertEquals(65,player.getLocation().getBlockZ());
        var broken=new BlockBreakEvent(origin,player); server.getPluginManager().callEvent(broken);
        assertFalse(broken.isCancelled());
        assertTrue(state().entrance(Pos.of(origin)).retired());
        // Even rebuilding the exact materials must not reactivate the record.
        assertFalse(entrance(origin));
        walk(player,origin.getLocation().add(.5,0,.5));
        assertEquals(source,player.getWorld());
    }
    @Test void pairedReturnSurvivesEntranceDestructionAndNeverMovesWhenBlocked() throws Exception {
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
        assertEquals(new Location(source,104.5,65,103.5),destination);
        assertEquals(0,state().entrance(Pos.of(entrance)).tileX());
        destination.getBlock().setType(Material.STONE);
        walk(player,room);
        assertEquals(maze,player.getWorld());
        assertEquals(new Location(source,104.5,65,103.5),destination);
        assertEquals(0,state().entrance(Pos.of(entrance)).tileX());
        destination.getBlock().setType(Material.AIR);
        walk(player,room);
        assertEquals(destination,player.getLocation());
    }

    @Test void steppingInsideDoorwayExitsWithoutWalkingToBackWall() throws Exception {
        var player=server.addPlayer();
        walk(player,new Location(maze,68.5,65,67.5));
        assertEquals(source,player.getWorld());
    }

    @Test void standingInExitAfterCooldownExpiresStillTeleports() throws Exception {
        var player=server.addPlayer();
        player.teleport(new Location(maze,68.5,65,67.5));
        var tick=UnderhallsFeature.class.getDeclaredMethod("tick"); tick.setAccessible(true);
        tick.invoke(feature);
        assertEquals(maze,player.getWorld());
        clearCooldowns();
        tick.invoke(feature);
        assertEquals(source,player.getWorld());
    }

    @Test void exitTeleportWaitsUntilMovementEventHasFinished() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        var tasks=api.tasks();
        doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(tasks).nextTick(any());
        var player=server.addPlayer();
        walk(player,new Location(maze,68.5,65,67.5));
        assertEquals(maze,player.getWorld());
        assertEquals(1,queued.size());
        while (!queued.isEmpty()) queued.removeFirst().run();
        assertEquals(source,player.getWorld());
    }

    @Test void doorwayOnlyNeedsItsFrameAndOneSupportingBlock() throws Exception {
        Block origin=source.getBlockAt(104,65,104); source.getChunkAt(6,6);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            if(x!=0 || z!=0) origin.getRelative(x,-1,z).setType(Material.AIR);
        }
        origin.getRelative(0,0,1).setType(Material.STONE);
        origin.getRelative(0,0,-1).setType(Material.SHORT_GRASS);
        var site=UnderhallsFeature.class.getDeclaredMethod("siteProblem",Block.class,boolean.class);site.setAccessible(true);
        assertNull(site.invoke(feature,origin,true));
        assertTrue(entrance(origin));
        assertEquals(Material.STONE,origin.getRelative(0,0,1).getType());
        assertEquals(Material.SHORT_GRASS,origin.getRelative(0,0,-1).getType());
        assertEquals(Material.AIR,origin.getRelative(1,-1,0).getType());
    }

    @Test void exitsAllowWaterAndKeepTheirPinnedLocationAboveADrop() throws Exception {
        source.getBlockAt(8,64,8).setType(Material.WATER);
        source.getBlockAt(8,64,9).setType(Material.WATER);
        var storeField=UnderhallsFeature.class.getDeclaredField("store");storeField.setAccessible(true);
        ((UnderhallsStore)storeField.get(feature)).pinExit(new Pos(maze.getUID(),68,65,69),new Pos(source.getUID(),8,65,8));
        var player=server.addPlayer();
        Location room=new Location(maze,68.5,65,67.5);
        walk(player,room);
        assertEquals(source,player.getWorld());
        assertEquals(8,player.getLocation().getBlockX());
        assertEquals(8,player.getLocation().getBlockZ());
        Location destination=player.getLocation().clone();
        destination.getBlock().getRelative(0,-1,0).setType(Material.AIR);
        walk(player,room);
        assertEquals(destination,player.getLocation());
        assertEquals(Pos.of(destination.getBlock()),state().exit(new Pos(maze.getUID(),68,65,69)));
    }

    @Test void underhallsTravelRecordsLocationWithoutGrantingDamageProtection() throws Exception {
        var captured=new java.util.concurrent.atomic.AtomicReference<dev.stemcraft.api.util.TeleportOptions>();
        var plugin=MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvent(org.bukkit.event.player.PlayerTeleportEvent.class,new Listener(){},
            EventPriority.MONITOR,(ignored,event)-> {
                var teleport=(org.bukkit.event.player.PlayerTeleportEvent)event;
                if(teleport.getTo().getWorld().equals(source)) captured.set(dev.stemcraft.api.util.TeleportContext.current(teleport.getPlayer().getUniqueId()));
            },plugin,true);
        walk(server.addPlayer(),new Location(maze,68.5,65,67.5));
        assertNotNull(captured.get());
        assertFalse(captured.get().grantDamageProtection());
        assertTrue(captured.get().updateWorldLastLocation());
    }

    List<net.kyori.adventure.text.Component> listing(int page) {
        var sender=mock(org.bukkit.command.CommandSender.class);
        feature.listRoutes(sender,page);
        var messages=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(sender,atLeastOnce()).sendMessage(messages.capture());
        return messages.getAllValues();
    }
    String listingText(List<net.kyori.adventure.text.Component> messages) {
        return messages.stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    void collectClicks(net.kyori.adventure.text.Component component,List<String> clicks) {
        if(component.clickEvent()!=null) clicks.add(((net.kyori.adventure.text.event.ClickEvent.Payload.Text)component.clickEvent().payload()).value());
        component.children().forEach(child->collectClicks(child,clicks));
    }
    @Test void routeListShowsRealDirectionsAndClickableEndpoints() throws Exception {
        Block origin=source.getBlockAt(104,65,104);source.getChunkAt(6,6);assertTrue(entrance(origin));
        var player=server.addPlayer();
        walk(player,new Location(maze,68.5,65,67.5));
        var messages=listing(1);
        String text=listingText(messages);
        assertTrue(text.contains("survival 104, 65, 104"));
        assertTrue(text.contains("survival_underhalls 68, 65, 65"));
        assertTrue(text.contains("survival 104, 65, 103"));
        assertTrue(text.contains("Paired doorways"));
        List<String> clicks=new ArrayList<>();messages.forEach(message->collectClicks(message,clicks));
        Pos id=Pos.of(origin);
        assertTrue(clicks.contains("/underhalls visit entrance " + id.encode()));
        assertTrue(clicks.contains("/underhalls visit entrance-destination " + id.encode()));
        feature.visitRoute(player,"entrance",id);
        assertEquals(new Location(source,104.5,65,103.5),player.getLocation());
        feature.visitRoute(player,"entrance-destination",id);
        assertEquals(new Location(maze,68.5,65,65.5),player.getLocation());
        feature.visitRoute(player,"exit-destination",new Pos(maze.getUID(),68,65,69));
        assertEquals(new Location(source,104.5,65,103.5),player.getLocation());
        feature.visitRoute(player,"entrance",new Pos(source.getUID(),999,65,999));
        assertEquals(new Location(source,104.5,65,103.5),player.getLocation());
    }
    @Test void pairedAndRetiredRoutesAreExplicitAndListingDoesNotPinExits() throws Exception {
        Block origin=source.getBlockAt(104,65,104);source.getChunkAt(6,6);assertTrue(entrance(origin));
        server.getPluginManager().callEvent(new BlockBreakEvent(origin,server.addPlayer()));
        String text=listingText(listing(1));
        assertTrue(text.contains("RETIRED"));
        assertTrue(text.contains("PAIRED RETURN"));
        assertTrue(state().exits().isEmpty());
        server.unloadWorld(maze,false);
        text=listingText(listing(1));
        assertTrue(text.contains("unloaded"));
        verify(api.worlds(),never()).loadWorld(anyString());
    }
    @Test void routePaginationIncludesEveryPairedDoorway() throws Exception {
        var field=UnderhallsFeature.class.getDeclaredField("store");field.setAccessible(true);
        var store=(UnderhallsStore)field.get(feature);
        for(int x=100;x<107;x++) store.save(new UnderhallsStore.Entrance(new Pos(source.getUID(),x,65,100),maze.getUID(),x-100,0,false));
        var first=listing(1);var second=listing(2);var third=listing(3);
        assertTrue(listingText(first).contains("14 known, page 1/3"));
        assertTrue(listingText(second).contains("14 known, page 2/3"));
        List<String> clicks=new ArrayList<>();first.forEach(message->collectClicks(message,clicks));
        assertTrue(clicks.contains("/underhalls list 2"));
        String text=listingText(first)+listingText(second)+listingText(third);
        for(int x=100;x<107;x++) assertTrue(text.contains("survival " + x + ", 65, 100"));
        assertEquals(7,text.split("EXIT ", -1).length-1);
    }

    @Test void entrancesInSameOverworldTileHaveDistinctPersistentReturnDoors() throws Exception {
        Block first=source.getBlockAt(104,65,104), second=source.getBlockAt(408,65,104);
        source.getChunkAt(6,6);source.getChunkAt(25,6);
        assertTrue(entrance(first));assertTrue(entrance(second));
        var a=state().entrance(Pos.of(first));var b=state().entrance(Pos.of(second));
        assertNotEquals(a.tileX(),b.tileX());
        feature.onDisable();feature.onEnable();
        var player=server.addPlayer();
        for(Block original:List.of(first,second)) {
            walk(player,Pos.of(original).location());
            assertEquals(maze,player.getWorld());
            Location doorInterior=player.getLocation().clone().add(0,0,2);
            walk(player,doorInterior);
            assertEquals(Pos.of(original).offset(0,0,-1).location(),player.getLocation());
        }
    }
    @Test void legacySharedRoomsAreSplitAndOldRandomExitCannotOverridePair() throws Exception {
        var field=UnderhallsFeature.class.getDeclaredField("store");field.setAccessible(true);
        var store=(UnderhallsStore)field.get(feature);
        Pos a=new Pos(source.getUID(),104,65,104), b=new Pos(source.getUID(),408,65,104);
        store.save(new UnderhallsStore.Entrance(a,maze.getUID(),0,0,false));
        store.save(new UnderhallsStore.Entrance(b,maze.getUID(),0,0,false));
        store.pinExit(new Pos(maze.getUID(),68,65,69),new Pos(source.getUID(),8,65,8));
        feature.migrateSharedRooms();
        int assigned=state().entrance(b).tileX();
        assertNotEquals(0,assigned);
        feature.migrateSharedRooms();
        assertEquals(assigned,state().entrance(b).tileX());
        var player=server.addPlayer();
        walk(player,new Location(maze,68.5,65,67.5));
        assertEquals(a.offset(0,0,-1).location(),player.getLocation());
        walk(player,new Location(maze,assigned*128+68.5,65,67.5));
        assertEquals(b.offset(0,0,-1).location(),player.getLocation());
        assertFalse(listingText(listing(1)).contains("survival 8, 65, 8"));
    }

    @Test void retrofitsDeadEndsAndLightsExistingRoomsWithoutReplacingPlayerBuilds() throws Exception {
        var storeField=UnderhallsFeature.class.getDeclaredField("store");storeField.setAccessible(true);
        var store=(UnderhallsStore)storeField.get(feature);
        var generator=(UnderhallsGenerator)maze.getGenerator();
        List<Pos> rooms=new ArrayList<>();
        for(int x=0;x<128;x+=8)for(int z=0;z<128;z+=8)if(generator.roomAt(maze,x,z)) rooms.add(new Pos(maze.getUID(),x+4,65,z+5));
        Pos preserved=rooms.getLast();
        var placed=preserved.offset(0,0,-1);placed.location().getBlock().setType(Material.COBBLESTONE);store.placed(placed);
        for(Pos room:rooms) dev.stemcraft.feature.underhalls.UnderhallsRooms.upgrade(room.location().getChunk(),store);
        for(Pos room:rooms) {
            if(room.equals(preserved))continue;
            assertEquals(Material.DARK_OAK_DOOR,room.offset(0,0,-3).location().getBlock().getType());
            assertEquals(Material.OCHRE_FROGLIGHT,room.offset(0,5,-1).location().getBlock().getType());
        }
        assertEquals(Material.COBBLESTONE,placed.location().getBlock().getType());
        assertNotEquals(Material.DARK_OAK_DOOR,preserved.offset(0,0,-3).location().getBlock().getType());
    }

    @Test void newDeadEndCreatesRandomPairedDoorAndPersistsExactRoom() throws Exception {
        var randomField=UnderhallsFeature.class.getDeclaredField("random");randomField.setAccessible(true);((Random)randomField.get(feature)).setSeed(12345);
        var storeField=UnderhallsFeature.class.getDeclaredField("store");storeField.setAccessible(true);
        var store=(UnderhallsStore)storeField.get(feature);
        var generator=(UnderhallsGenerator)maze.getGenerator();
        List<Pos> rooms=new ArrayList<>();
        for(int x=0;x<128 && rooms.size()<2;x+=8)for(int z=0;z<128 && rooms.size()<2;z+=8) {
            if(generator.roomAt(maze,x,z))rooms.add(new Pos(maze.getUID(),x+4,65,z+5));
        }
        var player=server.addPlayer();
        for(Pos room:rooms) {
            dev.stemcraft.feature.underhalls.UnderhallsRooms.upgrade(room.location().getChunk(),store);
            walk(player,room.offset(0,0,-2).location());
            assertEquals(source,player.getWorld());
            var pair=state().entrances().stream().filter(entry->room.equals(entry.room())).findFirst().orElseThrow();
            assertEquals(pair.origin().offset(0,0,-1).location(),player.getLocation());
            assertEquals(Material.DARK_OAK_DOOR,pair.origin().location().getBlock().getType());
            assertTrue(Math.abs(pair.origin().x()-source.getSpawnLocation().getBlockX())<=10016);
            assertNotEquals(8,pair.origin().x());
            walk(player,pair.origin().location());
            assertEquals(room.offset(0,0,-4).location(),player.getLocation());
        }
        assertEquals(2,state().entrances().size());
        feature.onDisable();feature.onEnable();
        for(Pos room:rooms) {
            var pair=state().entrances().stream().filter(entry->room.equals(entry.room())).findFirst().orElseThrow();
            walk(player,room.offset(0,0,-2).location());
            assertEquals(pair.origin().offset(0,0,-1).location(),player.getLocation());
        }
    }

    @Test void protectedRandomSitesDoNotCreatePortalsOrLeavePairingLocked() throws Exception {
        var plugin=MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvent(dev.stemcraft.api.event.world.SurvivalPortalActivateEvent.class,new Listener(){},
            EventPriority.HIGHEST,(listener,event)->((dev.stemcraft.api.event.world.SurvivalPortalActivateEvent)event).setCancelled(true),plugin);
        var player=server.addPlayer();walk(player,new Location(maze,68.5,65,67.5));
        assertEquals(maze,player.getWorld());assertTrue(state().entrances().isEmpty());
        assertTrue(source.testBlocks.values().stream().noneMatch(block->block.getType()==Material.DARK_OAK_DOOR));
        var field=UnderhallsFeature.class.getDeclaredField("pairing");field.setAccessible(true);
        assertTrue(((Set<?>)field.get(feature)).isEmpty());
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
