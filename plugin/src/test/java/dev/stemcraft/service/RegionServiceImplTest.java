package dev.stemcraft.service;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.region.RegionListener;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked"})
class RegionServiceImplTest {
    private static final String PLAYER_GATE_ID = "test:gate";
    private static final String VEHICLE_GATE_ID = "test:vehicle_gate";

    private WorldMock world;
    private PlayerMock player;
    private RegionServiceImpl service;
    private Map<Class<?>, EventHandler<?>> handlers;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("region-service");
        player = server.addPlayer("Alex");

        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);

        EventService events = mock(EventService.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.events()).thenReturn(events);

        handlers = new HashMap<>();
        when(events.register(any(Class.class), any())).thenAnswer(invocation -> {
            Class<?> eventClass = invocation.getArgument(0);
            EventHandler<?> handler = invocation.getArgument(1);
            handlers.put(eventClass, handler);
            return mock(Listener.class);
        });

        service = new RegionServiceImpl(plugin, api);
        service.onEnable();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onEnableRegistersPlayerAndVehicleMovementHandlers() {
        assertTrue(handlers.containsKey(PlayerMoveEvent.class));
        assertTrue(handlers.containsKey(VehicleMoveEvent.class));
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

        EventHandler<PlayerMoveEvent> handler = (EventHandler<PlayerMoveEvent>) handlers.get(PlayerMoveEvent.class);
        assertNotNull(handler);
        //noinspection UnstableApiUsage
        handler.handle(new PlayerMoveEvent(player, from, to));

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

        EventHandler<VehicleMoveEvent> handler = (EventHandler<VehicleMoveEvent>) handlers.get(VehicleMoveEvent.class);
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

        EventHandler<VehicleMoveEvent> handler = (EventHandler<VehicleMoveEvent>) handlers.get(VehicleMoveEvent.class);
        assertNotNull(handler);
        handler.handle(event);

        assertEquals(1, enters.get());
    }
}
