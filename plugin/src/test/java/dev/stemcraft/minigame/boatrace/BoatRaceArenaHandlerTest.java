package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        verify(player).playSound(playerLocation, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.45f);
        verify(arena).info(player, "<gold>Lap 1 complete.</gold> <aqua>Lap:</aqua> <yellow>2/3</yellow>");
        assertEquals(2, lapProgress.get(playerId));
        assertEquals(0, stageProgress.get(playerId));
        assertEquals(playerLocation, checkpointLocations.get(playerId));
    }
}
