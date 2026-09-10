package dev.stemcraft.feature.portal;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.world.WorldGeneration;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.service.world.generation.*;
import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SurvivalPortalsTest {
    @TempDir Path folder;
    ServerMock server;
    World world;
    STEMCraftAPI api;
    WorldGeneration generation;
    ConfigFileImpl definitions, state, config;
    SurvivalPortals portals;
    PlayerMock player;
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("survival");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x,z);
        player = server.addPlayer(); player.teleport(new Location(world, 8,65,8));
        Path directory = Files.createDirectories(folder.resolve("worlds"));
        try (var resource = getClass().getClassLoader().getResourceAsStream("worlds/portals.yml")) {
            Files.copy(Objects.requireNonNull(resource), directory.resolve("portals.yml"));
        }
        definitions = file(directory.resolve("portals.yml")); state = file(directory.resolve("portal-state.yml"));
        config = file(folder.resolve("config.yml"));
        api = mock(STEMCraftAPI.class);
        var tasks = mock(dev.stemcraft.api.service.task.TaskService.class);
        when(api.tasks()).thenReturn(tasks);
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(tasks).nextTick(any());
        doAnswer(call -> { ((Runnable)call.getArgument(1)).run(); return null; }).when(tasks).runLater(anyLong(),any());
        when(api.getDataFolder()).thenReturn(folder.toFile());
        ConfigService configs = mock(ConfigService.class); when(api.config()).thenReturn(configs);
        when(configs.load(any(java.io.File.class))).thenAnswer(invocation -> {
            java.io.File f = invocation.getArgument(0); return f.getName().equals("portals.yml") ? definitions : state;
        });
        WorldService worlds = mock(WorldService.class); when(api.worlds()).thenReturn(worlds);
        generation = mock(WorldGeneration.class); when(worlds.generator()).thenReturn(generation);
        when(generation.getGenerator(any())).thenAnswer(invocation -> Optional.of(new GeneratorDefinition(invocation.getArgument(0),
                "Test", "Test", GeneratorCategory.NORMAL, true, 1)));
        ItemService items = mock(ItemService.class); when(api.items()).thenReturn(items);
        when(items.isCustomItemId(eq("echo_core"), any())).thenAnswer(i -> ((ItemStack)i.getArgument(1)).getType() == Material.ECHO_SHARD);
        when(items.isCustomItemId(eq("sky_eye"), any())).thenAnswer(i -> ((ItemStack)i.getArgument(1)).getType() == Material.ENDER_EYE);
        portals = portalService(api, (p,l) -> fail("Unexpected teleport"), MockBukkit.createMockPlugin());
        portals.reload(config);
    }
    private ConfigFileImpl file(Path path) {
        ConfigFileImpl file = new ConfigFileImpl(); assertTrue(file.load(path.toFile(), true)); return file;
    }
    @AfterEach void teardown() { MockBukkit.unmock(); }
    private SurvivalPortalType type(String id) { return SurvivalPortalType.read(id, definitions.getSection("types."+id)); }
    private void build(SurvivalPortalType type, int rotation) {
        for (var cell : type.pattern().cells()) {
            var p = cell.offset().rotate(rotation); world.getBlockAt(p.x(),64+p.y(),p.z()).setType(cell.material());
        }
        for (var cell : type.pattern().interior()) {
            var p = cell.rotate(rotation); world.getBlockAt(p.x(),64+p.y(),p.z()).setType(type.pattern().interiorMaterial());
        }
    }
    private PlayerInteractEvent click(Block block, Material item) {
        ItemStack stack = new ItemStack(item, 2); player.getInventory().setItemInMainHand(stack);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stack, block,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        portals.interact(event); return event;
    }
    @Test void bundledTypesValidateAndContainExpectedSurvivalRequirements() {
        assertEquals(4, portals.getTypes().size());
        assertEquals("survival_nether_faraway", type("faraway").destinationName("survival_nether"));
        assertEquals(12, type("skylands").pattern().cells().stream().filter(c -> c.material() == Material.PURPUR_BLOCK).count());
        assertEquals(4, type("faraway").pattern().cells().stream().filter(c -> c.material() == Material.CRYING_OBSIDIAN).count());
        assertFalse(type("deep").allows(server.addSimpleWorld("creative")));
    }
    @Test void deepActivationConsumesOnceSurvivesReloadAndResetsWhenFrameChanges() {
        build(type("deep"),1);
        assertEquals(org.bukkit.event.Event.Result.DENY, click(world.getBlockAt(0,64,0),Material.ECHO_SHARD).useItemInHand());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, portals.getPortals().size()); assertTrue(portals.getPortals().iterator().next().active());
        var interior = type("deep").pattern().interior().getFirst().rotate(1);
        Block light = world.getBlockAt(interior.x(),64+interior.y(),interior.z());
        assertEquals(Material.LIGHT, light.getType());
        assertEquals(14, ((org.bukkit.block.data.type.Light) light.getBlockData()).getLevel());
        portals.save(); portals.reload(config);
        assertTrue(portals.getPortals().iterator().next().active());
        world.getBlockAt(0,64,0).setType(Material.AIR);
        portals.tick(); assertTrue(portals.getPortals().isEmpty());
        assertEquals(Material.AIR, light.getType());
    }
    @Test void disabledGeneratorAndWrongSourceDoNotConsumeItems() {
        build(type("deep"),0);
        doReturn(Optional.empty()).when(generation).getGenerator(any());
        click(world.getBlockAt(0,64,0),Material.ECHO_SHARD);
        assertTrue(portals.getPortals().isEmpty()); assertEquals(2,player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void skylandsRequiresAllTwelveDistinctChargesAndPersistsPartialProgress() {
        var type = type("skylands"); build(type,0);
        var cells = type.pattern().cells().stream().filter(c -> c.material() == Material.PURPUR_BLOCK).toList();
        var first = cells.getFirst().offset();
        click(world.getBlockAt(first.x(),64+first.y(),first.z()),Material.ENDER_EYE);
        click(world.getBlockAt(first.x(),64+first.y(),first.z()),Material.ENDER_EYE);
        assertEquals(2,player.getInventory().getItemInMainHand().getAmount(), "Repeated charges must not consume an eye");
        portals.save(); portals.reload(config);
        assertFalse(portals.getPortals().iterator().next().active());
        for (int i=1;i<cells.size();i++) {
            var p=cells.get(i).offset(); click(world.getBlockAt(p.x(),64+p.y(),p.z()),Material.ENDER_EYE);
        }
        assertTrue(portals.getPortals().iterator().next().active());
    }
    @Test void corruptedPortalConsumesOneEchoShardAndDeactivationLeavesVanillaBlocks() {
        build(type("faraway"),0);
        Item item = mock(Item.class); when(item.getThrower()).thenReturn(player.getUniqueId());
        when(item.getItemStack()).thenReturn(new ItemStack(Material.ECHO_SHARD));
        EntityPortalEnterEvent event = mock(EntityPortalEnterEvent.class);
        when(event.getEntity()).thenReturn(item); when(event.getLocation()).thenReturn(new Location(world,1,65,0));
        portals.itemEntered(event);
        verify(item).remove(); assertTrue(portals.getPortals().iterator().next().active());
        for (var cell : type("faraway").pattern().cells()) {
            var p = cell.offset();
            assertEquals(Material.CRYING_OBSIDIAN, world.getBlockAt(p.x(), 64 + p.y(), p.z()).getType());
        }
        portals.save(); portals.reload(config); portals.tick();
        assertTrue(portals.getPortals().iterator().next().active());
        var physics = new org.bukkit.event.block.BlockPhysicsEvent(world.getBlockAt(1,65,0), Material.NETHER_PORTAL.createBlockData());
        portals.physics(physics); assertTrue(physics.isCancelled());
        assertTrue(portals.deactivate(portals.getPortals().iterator().next().id()));
        assertEquals(Material.OBSIDIAN, world.getBlockAt(0,65,0).getType());
        assertEquals(Material.CRYING_OBSIDIAN, world.getBlockAt(0,64,0).getType());
        assertEquals(Material.NETHER_PORTAL,world.getBlockAt(1,65,0).getType());
    }
    @Test void safetySearchFindsGroundBelowAnElevatedSpawnAndRejectsHazards() {
        World target = mock(World.class);
        when(target.getMinHeight()).thenReturn(-64); when(target.getMaxHeight()).thenReturn(320);
        when(target.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = mock(Block.class), stone = mock(Block.class), magma = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR); when(stone.getType()).thenReturn(Material.STONE);
        when(magma.getType()).thenReturn(Material.MAGMA_BLOCK);
        when(target.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int)call.getArgument(1) == 64 ? stone : air);
        Location found = SurvivalPortals.safe(new Location(target, 0, 72, 0));
        assertNotNull(found); assertEquals(65, found.getBlockY());
        when(target.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int)call.getArgument(1) == 64 ? magma : air);
        assertNull(SurvivalPortals.safe(new Location(target, 0, 72, 0)));
        when(target.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertNull(SurvivalPortals.safe(new Location(target, 0, 72, 0)));
    }
    @Test void arrivalPreparationLoadsEverySearchChunkIncludingAcrossZero() {
        World target = mock(World.class);
        List<java.util.concurrent.CompletableFuture<Chunk>> loads = new ArrayList<>();
        when(target.getChunkAtAsync(anyInt(), anyInt(), eq(true))).thenAnswer(call -> {
            var future = new java.util.concurrent.CompletableFuture<Chunk>(); loads.add(future); return future;
        });
        var ready = SurvivalPortals.prepare(new Location(target, 0, 70, 0));
        assertEquals(4, loads.size()); assertFalse(ready.isDone());
        verify(target).getChunkAtAsync(-1, -1, true); verify(target).getChunkAtAsync(0, 0, true);
        for (var future : loads) future.complete(mock(Chunk.class));
        assertTrue(ready.isDone()); assertFalse(ready.isCompletedExceptionally());
    }
    @Test void existingFarawayDefaultsGainTheNewEffectWithoutOverwritingCustomSounds() {
        definitions.set("types.faraway.effects.active-frame", null);
        definitions.set("types.faraway.effects.activate-sound", "minecraft:block.portal.trigger");
        portals.reload(config);
        assertEquals(Material.CRYING_OBSIDIAN, type("faraway").activeFrame().get(Material.OBSIDIAN));
        assertEquals(Sound.ENTITY_GENERIC_EXPLODE, type("faraway").activateSound());
        definitions.set("types.faraway.effects.active-frame", null);
        definitions.set("types.faraway.effects.activate-sound", "minecraft:block.beacon.activate");
        portals.reload(config);
        assertEquals(Sound.BLOCK_BEACON_ACTIVATE, type("faraway").activateSound());
    }
    @Test void realLightningActivatesACompletedReactor() {
        build(type("wasteland"), 0);
        var lightning = mock(org.bukkit.entity.LightningStrike.class);
        when(lightning.getLocation()).thenReturn(new Location(world,2,68,2));
        var event = mock(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent.class);
        when(event.getWorld()).thenReturn(world); when(event.getEntity()).thenReturn(lightning);
        when(lightning.isEffect()).thenReturn(true);
        portals.entityAdded(event); assertTrue(portals.getPortals().isEmpty());
        when(lightning.isEffect()).thenReturn(false);
        portals.entityAdded(event); assertTrue(portals.getPortals().iterator().next().active());
    }
    @Test void protectionEventCanRejectActivationBeforeConsumption() {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void activate(dev.stemcraft.api.event.world.SurvivalPortalActivateEvent event) {
                event.setCancelled(true);
            }
        }, MockBukkit.createMockPlugin());
        build(type("deep"), 0);
        click(world.getBlockAt(0,64,0), Material.ECHO_SHARD);
        assertTrue(portals.getPortals().isEmpty());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"deep", "wasteland", "skylands", "faraway"})
    void scaledMatchingPairsSurviveReloadAndWorkForAnotherPlayer(String id) {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api, (p,l) -> { landings.add(l); p.teleport(l); }, MockBukkit.createMockPlugin()); portals.reload(config);
        int ox = -800, oz = -1600;
        activateAt(id, ox, oz);
        World target = target(id, -100, -200);
        enter(player, id, world, new PortalPattern.Offset(ox,64,oz));
        assertEquals(1, landings.size()); assertEquals(target, landings.getFirst().getWorld());
        assertNull(player.nextMessage(), "Successful travel must not report an exit failure");
        var pinned = org.mockito.ArgumentCaptor.forClass(Chunk.class);
        verify(portals, atLeastOnce()).addTicket(pinned.capture());
        for (Chunk chunk : pinned.getAllValues()) verify(portals).removeTicket(chunk);
        assertEquals(2, portals.getPortals().size());
        portals.save();
        var endpoints = state.getSection("instances");
        String exitId = endpoints.getKeys(false).stream().filter(key -> endpoints.getBoolean(key + ".generated", false)).findFirst().orElseThrow();
        var exit = endpoints.getSection(exitId);
        var origin = PortalPattern.offset(exit.getString("origin"));
        assertTrue(Math.abs(origin.x() + 100) <= 32); assertTrue(Math.abs(origin.z() + 200) <= 32);
        assertTrue(Math.abs(landings.getFirst().getX()) > 60, "Must use scaled coordinates, not world spawn");
        for (var cell : type(id).pattern().cells()) {
            var p = origin.add(cell.offset());
            assertEquals(type(id).activeFrame().getOrDefault(cell.material(),cell.material()), target.getBlockAt(p.x(),p.y(),p.z()).getType());
        }
        var bounds = PortalExitBuilder.bounds(type(id).pattern(),0);
        double cx=origin.x()+(bounds.minX()+bounds.maxX())/2.0+.5,cz=origin.z()+(bounds.minZ()+bounds.maxZ())/2.0+.5;
        Location landing=landings.getFirst();
        if (id.equals("skylands")) assertEquals(origin.y()-2,landing.getBlockY());
        else assertTrue(landing.getDirection().dot(new org.bukkit.util.Vector(landing.getX()-cx,0,landing.getZ()-cz))>0);
        assertEquals(0,landing.getPitch());
        assertFalse(exit.getString("partner", "").isBlank());
        portals.reload(config);
        PlayerMock visitor = server.addPlayer();
        enter(visitor, id, target, origin);
        assertEquals(2, landings.size()); assertEquals(world, landings.getLast().getWorld());
        assertTrue(Math.abs(landings.getLast().getX() - ox) < 8); assertTrue(Math.abs(landings.getLast().getZ() - oz) < 8);
        // A fresh visitor at the source reuses the pair instead of building another exit.
        PlayerMock third = server.addPlayer(); enter(third, id, world, new PortalPattern.Offset(ox,64,oz));
        assertEquals(3,landings.size()); assertEquals(2,portals.getPortals().size());
        assertEquals(landings.getFirst(), landings.getLast());
        assertFalse(state.contains("returns"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"deep","wasteland","skylands","faraway"})
    void activationBuildsLinkedExitBeforeAnyoneTravels(String id) {
        List<Location> landings=new ArrayList<>();
        portals=portalService(api,(p,l)->landings.add(l),MockBukkit.createMockPlugin());
        portals.reload(config);
        target(id,100,200);
        activateAt(id,800,1600);
        assertEquals(2,portals.getPortals().size());
        assertTrue(landings.isEmpty());
        var source=portals.getPortals().stream().filter(p->p.sourceWorld().equals(world.getUID())).findFirst().orElseThrow();
        assertTrue(portals.getLinkedPortal(source.id()).isPresent());
        clearInvocations(api.tasks());
        enter(player,id,world,new PortalPattern.Offset(800,64,1600));
        assertEquals(1,landings.size());
        assertEquals(2,portals.getPortals().size());
        if(id.equals("skylands")) verify(api.tasks(),never()).runLater(anyLong(),any(Runnable.class));
    }
    @Test void deactivationDuringExitPreparationPreventsConstructionAndReleasesTickets() {
        target("deep",100,200);
        java.util.ArrayDeque<Runnable> queued=new java.util.ArrayDeque<>();
        var tasks=api.tasks();
        doAnswer(call->{ queued.add(call.getArgument(0)); return null; }).when(tasks).nextTick(any());
        activateAt("deep",800,1600);
        assertEquals(1,portals.getPortals().size());
        queued.removeFirst().run(); // Starts preparation and queues chunk ticket acquisition.
        queued.removeFirst().run(); // Acquire at least one ticket before deactivation.
        var source=portals.getPortals().iterator().next();
        assertTrue(portals.deactivate(source.id()));
        while(!queued.isEmpty()) queued.removeFirst().run();
        assertTrue(portals.getPortals().isEmpty());
        var pinned=org.mockito.ArgumentCaptor.forClass(Chunk.class);
        verify(portals,atLeastOnce()).addTicket(pinned.capture());
        for(Chunk chunk:pinned.getAllValues()) verify(portals).removeTicket(chunk);
    }
    @Test void sourceProtectionCancellationPreventsAutomaticExitConstruction() {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api, (p,l) -> landings.add(l), MockBukkit.createMockPlugin()); portals.reload(config);
        activateAt("deep",800,1600); World target = target("deep",100,200);
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void activate(dev.stemcraft.api.event.world.SurvivalPortalActivateEvent event) {
                if (event.getOrigin().getWorld().equals(target)) event.setCancelled(true);
            }
        }, MockBukkit.createMockPlugin());
        enter(player,"deep",world,new PortalPattern.Offset(800,64,1600));
        assertTrue(landings.isEmpty()); assertEquals(1,portals.getPortals().size());
    }
    @Test void failedExitDoesNotRetryUntilPlayerLeavesTheOpening() throws Exception {
        activateAt("deep",800,1600); World target = target("deep",100,200);
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void activate(dev.stemcraft.api.event.world.SurvivalPortalActivateEvent event) {
                if (event.getOrigin().getWorld().equals(target)) event.setCancelled(true);
            }
        }, MockBukkit.createMockPlugin());
        var origin = new PortalPattern.Offset(800,64,1600);
        enter(player,"deep",world,origin);
        assertTrue(player.nextMessage().contains("cannot find a foothold"));
        var cooldowns = SurvivalPortals.class.getDeclaredField("cooldowns");
        cooldowns.setAccessible(true);
        ((Map<?,?>) cooldowns.get(portals)).clear();
        enter(player,"deep",world,origin);
        assertNull(player.nextMessage());
        Location outside = new Location(world,790,65,1590);
        portals.move(new org.bukkit.event.player.PlayerMoveEvent(player,player.getLocation(),outside));
        enter(player,"deep",world,origin);
        assertTrue(player.nextMessage().contains("cannot find a foothold"));
    }
    @Test void preparationDoesNotRemoveATicketAlreadyHeldByAnotherSubsystem() {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api, (p,l) -> landings.add(l), MockBukkit.createMockPlugin());
        portals.reload(config);
        doReturn(false).when(portals).addTicket(any());
        activateAt("deep",800,1600); target("deep",100,200);
        enter(player,"deep",world,new PortalPattern.Offset(800,64,1600));
        assertEquals(1,landings.size());
        verify(portals, atLeastOnce()).addTicket(any());
        verify(portals, never()).removeTicket(any());
    }
    @Test void arrivalLocksTheEntireReactorPadUntilThePlayerStepsOff() throws Exception {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api,(p,l) -> { landings.add(l); p.teleport(l); }, MockBukkit.createMockPlugin()); portals.reload(config);
        activateAt("wasteland",800,1600); World target = target("wasteland",100,200);
        enter(player,"wasteland",world,new PortalPattern.Offset(800,64,1600));
        assertEquals(1,landings.size());
        var exit = portals.getPortals().stream().filter(p -> p.sourceWorld().equals(target.getUID())).findFirst().orElseThrow();
        var origin = new PortalPattern.Offset(exit.x(),exit.y(),exit.z());
        var cooldowns = SurvivalPortals.class.getDeclaredField("cooldowns"); cooldowns.setAccessible(true);
        ((Map<?,?>)cooldowns.get(portals)).clear();
        // Crossing the non-triggering raised centre must not rearm the pad.
        Location rim = new Location(target,origin.x()+2,origin.y()+4,origin.z()+2);
        portals.move(new org.bukkit.event.player.PlayerMoveEvent(player,player.getLocation(),rim));
        enter(player,"wasteland",target,origin);
        assertEquals(1,landings.size());
        Location outside = new Location(target,origin.x()-4,origin.y()+2,origin.z());
        portals.move(new org.bukkit.event.player.PlayerMoveEvent(player,player.getLocation(),outside));
        enter(player,"wasteland",target,origin);
        assertEquals(2,landings.size()); assertEquals(world,landings.getLast().getWorld());
    }
    @Test void manualTeleportOntoAPadAlsoRequiresSteppingOff() {
        activateAt("wasteland",800,1600); target("wasteland",100,200);
        Location onto = new Location(world,801.5,66,1601.5);
        portals.arrived(new org.bukkit.event.player.PlayerTeleportEvent(player,player.getLocation(),onto));
        enter(player,"wasteland",world,new PortalPattern.Offset(800,64,1600));
        verify(portals,never()).addTicket(any());
    }
    @Test void unloadedReturnChunksDoNotDeactivateTheOriginalPortal() throws Exception {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api,(p,l) -> { landings.add(l); p.teleport(l); }, MockBukkit.createMockPlugin()); portals.reload(config);
        activateAt("wasteland",800,1600); World target = target("wasteland",100,200);
        enter(player,"wasteland",world,new PortalPattern.Offset(800,64,1600));
        var exit = portals.getPortals().stream().filter(p -> p.sourceWorld().equals(target.getUID())).findFirst().orElseThrow();
        List<Runnable> warmups = new ArrayList<>();
        var tasks = api.tasks();
        doAnswer(call -> { warmups.add(call.getArgument(1)); return null; }).when(tasks).runLater(anyLong(),any());
        PlayerMock visitor = server.addPlayer();
        enter(visitor,"wasteland",target,new PortalPattern.Offset(exit.x(),exit.y(),exit.z()));
        assertEquals(1,warmups.size());
        assertTrue(world.unloadChunk(50,100)); // Simulates destination data becoming unavailable before validation.
        warmups.getFirst().run();
        assertEquals(1,landings.size());
        assertEquals(2,portals.getPortals().size());
        assertTrue(portals.getPortals().stream().allMatch(p -> p.active()));
        assertTrue(portals.getLinkedPortal(exit.id()).isPresent());
    }
    @Test void steppingOffDuringWarmupCancelsTravel() {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api,(p,l) -> landings.add(l), MockBukkit.createMockPlugin()); portals.reload(config);
        activateAt("wasteland",800,1600); target("wasteland",100,200);
        List<Runnable> warmups = new ArrayList<>(); var tasks = api.tasks();
        doAnswer(call -> { warmups.add(call.getArgument(1)); return null; }).when(tasks).runLater(anyLong(),any());
        var origin = new PortalPattern.Offset(800,64,1600);
        enter(player,"wasteland",world,origin);
        Location outside = new Location(world,799,66,1602);
        portals.move(new org.bukkit.event.player.PlayerMoveEvent(player,player.getLocation(),outside));
        warmups.getFirst().run();
        assertTrue(landings.isEmpty());
        verify(portals,atLeastOnce()).stopDistortion(player);
    }
    @Test void reactorSurvivesWeatheringAndExtinguishesLightningFire() {
        activateAt("wasteland",800,1600);
        world.getBlockAt(800,65,1600).setType(Material.WEATHERED_COPPER);
        world.getBlockAt(802,67,1602).setType(Material.EXPOSED_LIGHTNING_ROD);
        world.getBlockAt(800,66,1600).setType(Material.FIRE);
        portals.tick();
        assertEquals(1,portals.getPortals().size());
        assertTrue(portals.getPortals().iterator().next().active());
        assertEquals(Material.LIGHT,world.getBlockAt(800,66,1600).getType());
    }
    @Test void breakingTheOriginalPortalUnlinksItsExitInsteadOfRoutingToSpawn() {
        List<Location> landings = new ArrayList<>();
        portals = portalService(api,(p,l) -> { landings.add(l); p.teleport(l); }, MockBukkit.createMockPlugin()); portals.reload(config);
        activateAt("deep",800,1600); World target = target("deep",100,200);
        enter(player,"deep",world,new PortalPattern.Offset(800,64,1600));
        portals.save(); var entries = state.getSection("instances");
        String exit = entries.getKeys(false).stream().filter(key -> entries.getBoolean(key+".generated",false)).findFirst().orElseThrow();
        var origin = PortalPattern.offset(entries.getString(exit+".origin"));
        world.getBlockAt(800,64,1600).setType(Material.AIR); portals.tick();
        PlayerMock visitor = server.addPlayer(); enter(visitor,"deep",target,origin);
        assertEquals(1,landings.size()); assertTrue(visitor.nextMessage().contains("twin must be rekindled"));
    }
    private SurvivalPortals portalService(STEMCraftAPI api, java.util.function.BiConsumer<org.bukkit.entity.Player, Location> teleport,
                                         org.bukkit.plugin.Plugin owner) {
        var service = spy(new SurvivalPortals(api, teleport, owner));
        // MockBukkit lacks chunk tickets; retain real preparation and reference-counting logic.
        doReturn(true).when(service).addTicket(any());
        doNothing().when(service).removeTicket(any());
        doNothing().when(service).startDistortion(any());
        doNothing().when(service).stopDistortion(any());
        return service;
    }
    private World target(String id, int x, int z) {
        World target = server.addSimpleWorld("survival_"+id);
        target.setSpawnLocation(0,65,0);
        for (int cx = Math.floorDiv(x,16)-5; cx <= Math.floorDiv(x,16)+5; cx++)
            for (int cz = Math.floorDiv(z,16)-5; cz <= Math.floorDiv(z,16)+5; cz++) target.loadChunk(cx,cz);
        when(generation.getGeneratedWorld(target)).thenReturn(Optional.of(new GeneratedWorld(target.getUID(),
                new GeneratorDefinition(new NamespacedKey("stemcraft",id),id,"Test",GeneratorCategory.NORMAL,true,3),0)));
        return target;
    }
    private void activateAt(String id, int x, int z) {
        for (int cx = Math.floorDiv(x,16)-3; cx <= Math.floorDiv(x,16)+3; cx++)
            for (int cz = Math.floorDiv(z,16)-3; cz <= Math.floorDiv(z,16)+3; cz++) world.loadChunk(cx,cz);
        player.teleport(new Location(world,x-1,65,z-1));
        for (int dx = -4; dx <= 8; dx++) for (int dz = -4; dz <= 8; dz++) world.getBlockAt(x+dx,63,z+dz).setType(Material.STONE);
        var type = type(id);
        for (var c : type.pattern().cells()) { var p=c.offset(); world.getBlockAt(x+p.x(),64+p.y(),z+p.z()).setType(c.material()); }
        for (var p : type.pattern().interior()) world.getBlockAt(x+p.x(),64+p.y(),z+p.z()).setType(type.pattern().interiorMaterial());
        switch (id) {
            case "deep" -> click(world.getBlockAt(x,64,z),Material.ECHO_SHARD);
            case "skylands" -> {
                for (var c : type.pattern().cells()) if(c.material()==Material.PURPUR_BLOCK) {
                    var p=c.offset(); click(world.getBlockAt(x+p.x(),64+p.y(),z+p.z()),Material.ENDER_EYE);
                }
            }
            case "wasteland" -> {
                var bolt=mock(org.bukkit.entity.LightningStrike.class); when(bolt.getLocation()).thenReturn(new Location(world,x+2,68,z+2));
                var event=mock(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent.class);
                when(event.getWorld()).thenReturn(world); when(event.getEntity()).thenReturn(bolt); portals.entityAdded(event);
            }
            case "faraway" -> {
                Item item=mock(Item.class); when(item.getThrower()).thenReturn(player.getUniqueId()); when(item.getItemStack()).thenReturn(new ItemStack(Material.ECHO_SHARD));
                var event=mock(EntityPortalEnterEvent.class); when(event.getEntity()).thenReturn(item); when(event.getLocation()).thenReturn(new Location(world,x+1,65,z));
                portals.itemEntered(event);
            }
            default -> throw new AssertionError(id);
        }
        assertTrue(portals.getPortals().stream().anyMatch(p -> p.active()));
    }
    private void enter(PlayerMock traveller, String id, World inWorld, PortalPattern.Offset origin) {
        var p=origin.add(type(id).pattern().interior().getFirst());
        Location inside=new Location(inWorld,p.x()+.5,p.y(),p.z()+.5);
        Location before=inside.clone().add(-1,0,-1); traveller.teleport(before);
        assertTrue(portals.move(new org.bukkit.event.player.PlayerMoveEvent(traveller,before,inside)));
    }
    @Test void patternRotationsAndInvalidDefinitionsAreChecked() {
        var origin = new PortalPattern.Offset(-11,4,7);
        var point = origin;
        for (int i=0;i<4;i++) point=point.rotate(1);
        assertEquals(origin,point);
        assertThrows(IllegalArgumentException.class, () -> PortalPattern.parse(List.of("0,0,0:STONE"),List.of("0,0,0"),Material.AIR));
        assertThrows(IllegalArgumentException.class, () -> PortalPattern.parse(List.of("17,0,0:STONE"),List.of("1,0,0"),Material.AIR));
    }
}
