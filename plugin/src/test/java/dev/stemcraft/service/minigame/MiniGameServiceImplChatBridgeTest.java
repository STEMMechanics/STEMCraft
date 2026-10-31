package dev.stemcraft.service.minigame;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.web.WebServiceEndpointHandler;
import org.jetbrains.annotations.NotNull;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameServiceImplChatBridgeTest {
    @Test
    void emitsOneStartAndOneEndEventAcrossActivePhaseTransitions() {
        STEMCraft plugin = mock(STEMCraft.class);
        RecordingWebService web = new RecordingWebService();
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.web()).thenReturn(web);

        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.namespace()).thenReturn("testgame");
        when(arena.id()).thenReturn("arena");
        when(arena.getName()).thenReturn("Arena");
        when(arena.world()).thenReturn(mock(World.class));
        when(arena.getPlayers()).thenReturn(List.of());
        when(arena.getSpectators()).thenReturn(List.of());
        when(arena.getTeams()).thenReturn(List.of());

        MiniGameServiceImpl service = new MiniGameServiceImpl(plugin, api);
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.STARTING, MiniGameArena.ArenaStatus.RUNNING);
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.PREPARATION, MiniGameArena.ArenaStatus.RUNNING);
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.RUNNING, MiniGameArena.ArenaStatus.ENDING);

        assertEquals(List.of("minecraft.minigame_start", "minecraft.minigame_end"), web.events);
    }

    private static final class RecordingWebService implements WebService {
        private final List<String> events = new java.util.ArrayList<>();

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void registerEndpointHandler(@NotNull String path, @NotNull WebServiceEndpointHandler handler) {}

        @Override
        public void publishEvent(@NotNull String eventType, UUID playerUuid, String playerName,
                                 @NotNull Map<String, Object> data) {
            events.add(eventType);
        }

        @Override
        public @NotNull String getPublicUrl() { return ""; }
    }
}
