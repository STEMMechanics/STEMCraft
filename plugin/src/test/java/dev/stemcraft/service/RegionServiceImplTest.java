package dev.stemcraft.service;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.model.RegionFlagData;
import dev.stemcraft.api.model.RegionMemberData;
import dev.stemcraft.api.model.RegionScopedData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.region.RegionExtension;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.service.region.RegionFlagExtension;
import dev.stemcraft.service.region.RegionMemberExtension;
import dev.stemcraft.service.region.RegionReputationExtension;
import dev.stemcraft.service.region.RegionTitleExtension;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionServiceImplTest {
    private static final String PLAYER_GATE_ID = "test:gate";
    private static final String VEHICLE_GATE_ID = "test:vehicle_gate";

    private WorldMock world;
    private WorldMock otherWorld;
    private PlayerMock player;
    private RegionServiceImpl service;
    private Map<Class<?>, List<EventHandler<?>>> handlers;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("region-service");
        otherWorld = server.addSimpleWorld("region-service-2");
        player = server.addPlayer("Alex");

        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);

        EventService events = mock(EventService.class);
        TaskService tasks = mock(TaskService.class);
        CommandService commands = mock(CommandService.class);
        TabCompleteService tabComplete = mock(TabCompleteService.class);
        CommandBuilder commandBuilder = mock(CommandBuilder.class);
        ConfigSection configSection = mock(ConfigSection.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(api.commands()).thenReturn(commands);
        when(api.tabComplete()).thenReturn(tabComplete);

        when(commands.create(anyString())).thenReturn(commandBuilder);
        when(commandBuilder.description(anyString())).thenReturn(commandBuilder);
        when(commandBuilder.permission(anyString())).thenReturn(commandBuilder);
        when(commandBuilder.usage(anyString())).thenReturn(commandBuilder);
        when(commandBuilder.tabCompletion(any(String[].class))).thenReturn(commandBuilder);
        when(commandBuilder.executor(any())).thenReturn(commandBuilder);
        when(commandBuilder.register(any())).thenReturn(mock(dev.stemcraft.api.command.Command.class));
        when(configSection.getList(anyString())).thenReturn(List.of());
        doNothing().when(configSection).set(anyString(), any());
        doNothing().when(configSection).save();

        handlers = new HashMap<>();
        when(events.register(any(Class.class), any())).thenAnswer(invocation -> {
            Class<?> eventClass = invocation.getArgument(0);
            EventHandler<?> handler = invocation.getArgument(1);
            handlers.computeIfAbsent(eventClass, ignored -> new java.util.ArrayList<>()).add(handler);
            return mock(Listener.class);
        });
        when(events.register(any(Class.class), any(), any(EventPriority.class), anyBoolean())).thenAnswer(invocation -> {
            Class<?> eventClass = invocation.getArgument(0);
            EventHandler<?> handler = invocation.getArgument(1);
            handlers.computeIfAbsent(eventClass, ignored -> new java.util.ArrayList<>()).add(handler);
            return mock(Listener.class);
        });

        service = new RegionServiceImpl(plugin, api) {
            @Override
            protected ConfigSection getConfigSection() {
                return configSection;
            }
        };
        service.onEnable();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onEnableRegistersMovementAndCleanupHandlers() {
        assertTrue(handlers.containsKey(PlayerMoveEvent.class));
        assertTrue(handlers.containsKey(EntityMoveEvent.class));
        assertTrue(handlers.containsKey(VehicleMoveEvent.class));
        assertTrue(handlers.containsKey(EntityRemoveEvent.class));
        assertTrue(handlers.containsKey(PlayerQuitEvent.class));
        assertTrue(handlers.containsKey(PlayerKickEvent.class));
    }

    @Test
    void onEnableRegistersBuiltInManagedRegionExtensions() {
        Map<String, RegionExtension<?>> extensions = new HashMap<>();
        for (RegionExtension<?> extension : service.getExtensions()) {
            extensions.put(extension.key(), extension);
        }

        assertInstanceOf(RegionTitleExtension.class, extensions.get(RegionTitleExtension.KEY));
        assertInstanceOf(RegionMemberExtension.class, extensions.get(RegionMemberExtension.KEY));
        assertInstanceOf(RegionReputationExtension.class, extensions.get(RegionReputationExtension.KEY));
        assertInstanceOf(RegionFlagExtension.class, extensions.get(RegionFlagExtension.KEY));
    }

    @Test
    void managedRegionCanBeStoredAndResolvedByListenerId() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(5, 60, 0), BlockVector3.at(7, 70, 10)),
            world
        );
        SCManagedRegion managedRegion = new SCManagedRegion("test_gate", world.getName(), region, 10);
        service.saveManagedRegion(managedRegion);

        AtomicInteger enters = new AtomicInteger();
        service.addListener("test:managed_gate", world.getName() + ":" + managedRegion.getId(), new RegionListener() {
            @Override
            public void onEnter(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                enters.incrementAndGet();
            }
        });

        Location from = new Location(world, 0.0d, 65.0d, 5.0d);
        Location to = new Location(world, 10.0d, 65.0d, 5.0d);
        player.teleport(from);
        player.teleport(to);

        firePlayerMove(from, to);

        assertEquals(1, enters.get());
        SCRegion storedRegion = service.getRegion(world.getName(), managedRegion.getId());
        assertNotNull(storedRegion);
        assertEquals(region.toString(), storedRegion.toString());
        assertTrue(service.hasManagedRegion(world.getName(), managedRegion.getId()));
        SCManagedRegion resolvedRegion = service.getManagedRegionAt(new Location(world, 6.0d, 65.0d, 5.0d));
        assertNotNull(resolvedRegion);
        assertEquals(managedRegion.getId(), resolvedRegion.getId());
    }

    @Test
    void managedRegionsAllowDuplicateLocalIdsAcrossWorlds() {
        SCManagedRegion first = new SCManagedRegion(
            "spawn",
            world.getName(),
            new SCRegion(new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(2, 62, 2)), world),
            0
        );
        SCManagedRegion second = new SCManagedRegion(
            "spawn",
            otherWorld.getName(),
            new SCRegion(new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(2, 62, 2)), otherWorld),
            0
        );

        service.saveManagedRegion(first);
        service.saveManagedRegion(second);

        assertTrue(service.hasManagedRegion(world.getName(), "spawn"));
        assertTrue(service.hasManagedRegion(otherWorld.getName(), "spawn"));
        SCManagedRegion firstResolved = service.getManagedRegion(world.getName(), "spawn");
        SCManagedRegion secondResolved = service.getManagedRegion(otherWorld.getName(), "spawn");
        assertNotNull(firstResolved);
        assertNotNull(secondResolved);
        assertEquals(world.getName(), firstResolved.getWorldName());
        assertEquals(otherWorld.getName(), secondResolved.getWorldName());
        assertEquals(1, service.getManagedRegions(world.getName()).stream().filter(region -> region.getId().equals("spawn")).count());
        assertEquals(1, service.getManagedRegions(otherWorld.getName()).stream().filter(region -> region.getId().equals("spawn")).count());
    }

    @Test
    void breakBlockFlagCancelsBlockBreakInsideManagedRegion() {
        SCManagedRegion region = new SCManagedRegion(
            "spawn",
            world.getName(),
            new SCRegion(new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(4, 70, 4)), world),
            0
        );
        service.saveManagedRegion(region);

        RegionFlagExtension extension = (RegionFlagExtension) service.getExtension(RegionFlagExtension.KEY);
        assertNotNull(extension);
        extension.getOrCreate(region, null).setFlag("break-block", false);
        service.saveManagedRegion(region);

        player.teleport(new Location(world, 1.0d, 65.0d, 1.0d));
        BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(1, 65, 1), player);

        EventHandler<BlockBreakEvent> handler = firstHandler(BlockBreakEvent.class);
        assertNotNull(handler);
        handler.handle(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void memberAudienceOverridesAllAudienceForMemberPlayer() {
        SCManagedRegion region = new SCManagedRegion(
            "spawn",
            world.getName(),
            new SCRegion(new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(4, 70, 4)), world),
            0
        );
        RegionFlagData allFlags = new RegionFlagData();
        allFlags.setFlag("break-block", false);
        extensionScoped(region, RegionScopedData.ALL, allFlags);

        RegionFlagData memberFlags = new RegionFlagData();
        memberFlags.setFlag("break-block", true);
        extensionScoped(region, RegionScopedData.MEMBERS, memberFlags);

        RegionMemberData memberData = new RegionMemberData();
        memberData.addPlayer(player.getUniqueId());
        region.setData(RegionMemberData.KEY, memberData);
        service.saveManagedRegion(region);

        player.teleport(new Location(world, 1.0d, 65.0d, 1.0d));
        BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(1, 65, 1), player);

        EventHandler<BlockBreakEvent> handler = firstHandler(BlockBreakEvent.class);
        assertNotNull(handler);
        handler.handle(event);

        assertFalse(event.isCancelled());
    }

    private void extensionScoped(@NotNull SCManagedRegion region, @NotNull String scope, @NotNull RegionFlagData data) {
        RegionScopedData scoped = region.getData(RegionFlagData.KEY, RegionScopedData.class);
        if (scoped == null) {
            scoped = new RegionScopedData();
            region.setData(RegionFlagData.KEY, scoped);
        }
        scoped.set(scope, data);
    }

    @Test
    void playerMovePathIntersectionTriggersTransientRegionEnterAndExit() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(5, 60, 0), BlockVector3.at(5, 70, 10)),
            world
        );
        AtomicInteger enters = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();
        service.addListener(PLAYER_GATE_ID, region, new RegionListener() {
            @Override
            public void onEnter(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                enters.incrementAndGet();
            }

            @Override
            public void onExit(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                exits.incrementAndGet();
            }
        });

        Location from = new Location(world, 0.0d, 65.0d, 5.0d);
        Location to = new Location(world, 10.0d, 65.0d, 5.0d);
        player.teleport(from);
        player.teleport(to);

        firePlayerMove(from, to);

        assertEquals(1, enters.get());
        assertEquals(1, exits.get());
        assertFalse(service.contains(player, PLAYER_GATE_ID));
    }

    @Test
    void vehicleMovePathIntersectionTriggersTransientRegionEnterAndExitForRider() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(5, 60, 0), BlockVector3.at(5, 70, 10)),
            world
        );
        AtomicInteger enters = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();
        service.addListener(VEHICLE_GATE_ID, region, new RegionListener() {
            @Override
            public void onEnter(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                enters.incrementAndGet();
            }

            @Override
            public void onExit(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                exits.incrementAndGet();
            }
        });

        Location from = new Location(world, 0.0d, 65.0d, 5.0d);
        Location to = new Location(world, 10.0d, 65.0d, 5.0d);
        player.teleport(from);
        player.teleport(to);

        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getPassengers()).thenReturn(List.of(player));

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        EventHandler<VehicleMoveEvent> handler = firstHandler(VehicleMoveEvent.class);
        assertNotNull(handler);
        handler.handle(event);

        assertEquals(1, enters.get());
        assertEquals(1, exits.get());
        assertFalse(service.contains(player, VEHICLE_GATE_ID));
    }

    @Test
    void vehicleMoveUsesVehicleDestinationEvenWhenPlayerLocationHasNotCaughtUp() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(5, 60, 0), BlockVector3.at(7, 70, 10)),
            world
        );
        AtomicInteger enters = new AtomicInteger();
        service.addListener(VEHICLE_GATE_ID, region, new RegionListener() {
            @Override
            public void onEnter(@org.jetbrains.annotations.NotNull Player player, @org.jetbrains.annotations.NotNull SCRegion region, Location from, Location to) {
                enters.incrementAndGet();
            }
        });

        Location from = new Location(world, 0.0d, 65.0d, 5.0d);
        Location to = new Location(world, 10.0d, 65.0d, 5.0d);
        player.teleport(from);

        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getPassengers()).thenReturn(List.of(player));

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        EventHandler<VehicleMoveEvent> handler = firstHandler(VehicleMoveEvent.class);
        assertNotNull(handler);
        handler.handle(event);

        assertEquals(1, enters.get());
    }

    private void firePlayerMove(Location from, Location to) {
        List<EventHandler<?>> moveHandlers = handlers.get(PlayerMoveEvent.class);
        assertNotNull(moveHandlers);
        @SuppressWarnings("UnstableApiUsage")
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        for (EventHandler<?> rawHandler : moveHandlers) {
            EventHandler<PlayerMoveEvent> handler = (EventHandler<PlayerMoveEvent>) rawHandler;
            handler.handle(event);
        }
    }

    private <T extends Event> EventHandler<T> firstHandler(Class<?> eventClass) {
        List<EventHandler<?>> eventHandlers = handlers.get(eventClass);
        assertNotNull(eventHandlers);
        return (EventHandler<T>) eventHandlers.getFirst();
    }
}
