package dev.stemcraft.minigame.boatrace;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.region.RegionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoatRaceArenaHandlerTest {
    @Test
    @SuppressWarnings("unchecked")
    void finishRaceAdvanceShowsTitleForNextLap() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        Player player = mock(Player.class);
        Listener listenerRegistration = mock(Listener.class);
        UUID playerId = UUID.randomUUID();
        Location playerLocation = new Location(null, 12.0d, 65.0d, 24.0d);
        Map<UUID, Integer> lapProgress = new LinkedHashMap<>();
        Map<UUID, Integer> stageProgress = new LinkedHashMap<>();
        Map<UUID, Location> checkpointLocations = new LinkedHashMap<>();

        when(api.events()).thenReturn(events);
        when(events.register(any(Class.class), any())).thenReturn(listenerRegistration);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(playerLocation);

        when(boatRace.stageRegions(arena)).thenReturn(List.of());
        when(boatRace.hasFinished(arena, playerId)).thenReturn(false);
        when(boatRace.currentLap(arena, playerId)).thenReturn(1);
        when(boatRace.laps(arena)).thenReturn(3);
        when(boatRace.lapProgress(arena)).thenReturn(lapProgress);
        when(boatRace.stageProgress(arena)).thenReturn(stageProgress);
        when(boatRace.checkpointLocations(arena)).thenReturn(checkpointLocations);

        BoatRaceArenaHandler handler = new BoatRaceArenaHandler(api, boatRace);
        Method finishRace = BoatRaceArenaHandler.class.getDeclaredMethod("finishRace", MiniGameArena.class, Player.class);
        finishRace.setAccessible(true);
        finishRace.invoke(handler, arena, player);

        verify(player).showTitle(any(net.kyori.adventure.title.Title.class));
        verify(player).playSound(playerLocation, Sound.UI_TOAST_IN, 0.8f, 1.1f);
        verify(arena).info(player, "<gold>Lap 1 complete.</gold> <aqua>Lap:</aqua> <yellow>2/3</yellow>");
        assertEquals(2, lapProgress.get(playerId));
        assertEquals(0, stageProgress.get(playerId));
        assertEquals(playerLocation, checkpointLocations.get(playerId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTntBounceContactUsesFullBoatPath() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("boatrace-tnt");
            world.getBlockAt(5, 64, 5).setType(Material.TNT);

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            EventService events = mock(EventService.class);
            BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
            Listener listenerRegistration = mock(Listener.class);

            when(api.events()).thenReturn(events);
            when(events.register(any(Class.class), any())).thenReturn(listenerRegistration);

            BoatRaceArenaHandler handler = new BoatRaceArenaHandler(api, boatRace);
            Method findTntBounceContact = BoatRaceArenaHandler.class.getDeclaredMethod(
                "findTntBounceContact",
                Location.class,
                Location.class
            );
            findTntBounceContact.setAccessible(true);

            Location from = new Location(world, 0.5d, 64.75d, 5.5d);
            Location to = new Location(world, 10.5d, 64.75d, 5.5d);
            Location contact = (Location) findTntBounceContact.invoke(handler, from, to);

            assertNotNull(contact);
            assertEquals(5, contact.getBlockX());
            assertEquals(64, contact.getBlockY());
            assertEquals(5, contact.getBlockZ());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void endingBoundaryExitKeepsRacersInArena() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("boatrace-ending");

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            EventService events = mock(EventService.class);
            RegionService regions = mock(RegionService.class);
            BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
            MiniGameArena arena = mock(MiniGameArena.class);
            Player player = mock(Player.class);
            Listener listenerRegistration = mock(Listener.class);
            SCRegion arenaRegion = new SCRegion(
                new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(10, 70, 10)),
                world
            );

            when(api.events()).thenReturn(events);
            when(api.regions()).thenReturn(regions);
            when(events.register(any(Class.class), any())).thenReturn(listenerRegistration);

            when(arena.id()).thenReturn("test");
            when(arena.get("arenaRegion", SCRegion.class)).thenReturn(arenaRegion);
            when(arena.get("finishRegion", SCRegion.class)).thenReturn(null);
            when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.ENDING);
            when(arena.hasPlayer(player)).thenReturn(true);
            when(arena.hasOccupant(player)).thenReturn(true);
            when(boatRace.boatAssignments(arena)).thenReturn(new LinkedHashMap<>());
            when(boatRace.finishOrder(arena)).thenReturn(new java.util.ArrayList<>());
            when(boatRace.lapProgress(arena)).thenReturn(new LinkedHashMap<>());
            when(boatRace.stageProgress(arena)).thenReturn(new LinkedHashMap<>());
            when(boatRace.assignedGridSlots(arena)).thenReturn(new LinkedHashMap<>());
            when(boatRace.stageRegions(arena)).thenReturn(List.of());
            when(boatRace.checkpointLocations(arena)).thenReturn(new LinkedHashMap<>());
            when(arena.getOrCreate(org.mockito.ArgumentMatchers.eq("tntBounceCooldowns"), org.mockito.ArgumentMatchers.eq(Map.class), any()))
                .thenReturn(new LinkedHashMap<UUID, Long>());
            when(arena.getOrCreate(org.mockito.ArgumentMatchers.eq("tntBounceKeys"), org.mockito.ArgumentMatchers.eq(Map.class), any()))
                .thenReturn(new LinkedHashMap<UUID, String>());
            when(arena.getLobbySpawn()).thenReturn(null);

            BoatRaceArenaHandler handler = new BoatRaceArenaHandler(api, boatRace);
            handler.onArenaLoad(arena);

            ArgumentCaptor<RegionListener> listenerCaptor = ArgumentCaptor.forClass(RegionListener.class);
            verify(regions).addListener(any(String.class), any(SCRegion.class), listenerCaptor.capture());

            listenerCaptor.getValue().onExit(
                player,
                arenaRegion,
                new Location(world, 5.0d, 65.0d, 5.0d),
                new Location(world, 12.0d, 65.0d, 5.0d)
            );

            verify(arena, never()).removeOccupant(player);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshRegionListenersRebindsWithoutClearingRaceState() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("boatrace-refresh");

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            EventService events = mock(EventService.class);
            RegionService regions = mock(RegionService.class);
            BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
            MiniGameArena arena = mock(MiniGameArena.class);
            Listener listenerRegistration = mock(Listener.class);
            SCRegion arenaRegion = new SCRegion(
                new CuboidRegion(BlockVector3.at(0, 60, 0), BlockVector3.at(10, 70, 10)),
                world
            );
            Map<UUID, UUID> boatAssignments = new LinkedHashMap<>();
            List<UUID> finishOrder = new ArrayList<>();
            Map<UUID, Integer> lapProgress = new LinkedHashMap<>();
            Map<UUID, Integer> stageProgress = new LinkedHashMap<>();
            Map<UUID, Integer> assignedGridSlots = new LinkedHashMap<>();
            Map<UUID, Location> checkpointLocations = new LinkedHashMap<>();
            Map<UUID, Long> cooldowns = new LinkedHashMap<>();
            Map<UUID, String> bounceKeys = new LinkedHashMap<>();
            UUID playerId = UUID.randomUUID();
            UUID boatId = UUID.randomUUID();

            boatAssignments.put(playerId, boatId);
            finishOrder.add(playerId);
            lapProgress.put(playerId, 2);
            stageProgress.put(playerId, 1);
            assignedGridSlots.put(playerId, 0);
            checkpointLocations.put(playerId, new Location(world, 4.0d, 64.0d, 4.0d));
            cooldowns.put(playerId, 123L);
            bounceKeys.put(playerId, "key");

            when(api.events()).thenReturn(events);
            when(api.regions()).thenReturn(regions);
            when(events.register(any(Class.class), any())).thenReturn(listenerRegistration);

            when(arena.id()).thenReturn("test");
            when(arena.get("arenaRegion", SCRegion.class)).thenReturn(arenaRegion);
            when(arena.get("finishRegion", SCRegion.class)).thenReturn(null);
            when(boatRace.stageRegions(arena)).thenReturn(List.of());
            when(boatRace.boatAssignments(arena)).thenReturn(boatAssignments);
            when(boatRace.finishOrder(arena)).thenReturn(finishOrder);
            when(boatRace.lapProgress(arena)).thenReturn(lapProgress);
            when(boatRace.stageProgress(arena)).thenReturn(stageProgress);
            when(boatRace.assignedGridSlots(arena)).thenReturn(assignedGridSlots);
            when(boatRace.checkpointLocations(arena)).thenReturn(checkpointLocations);
            when(arena.getOrCreate(org.mockito.ArgumentMatchers.eq("tntBounceCooldowns"), org.mockito.ArgumentMatchers.eq(Map.class), any()))
                .thenReturn(cooldowns);
            when(arena.getOrCreate(org.mockito.ArgumentMatchers.eq("tntBounceKeys"), org.mockito.ArgumentMatchers.eq(Map.class), any()))
                .thenReturn(bounceKeys);

            BoatRaceArenaHandler handler = new BoatRaceArenaHandler(api, boatRace);
            handler.refreshRegionListeners(arena);

            verify(regions).removeListener("boatrace:test_*");
            verify(regions).addListener(any(String.class), any(SCRegion.class), any(RegionListener.class));
            assertEquals(1, boatAssignments.size());
            assertEquals(1, finishOrder.size());
            assertEquals(2, lapProgress.get(playerId));
            assertEquals(1, stageProgress.get(playerId));
            assertEquals(1, checkpointLocations.size());
            assertEquals(1, assignedGridSlots.size());
            assertEquals(1, cooldowns.size());
            assertEquals(1, bounceKeys.size());
        } finally {
            MockBukkit.unmock();
        }
    }
}
