package dev.stemcraft.service.minigame;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.service.ChatServiceImpl;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameServiceImplChatBridgeTest {
    @Test
    void emitsOneStartAndOneEndEventAcrossActivePhaseTransitions() {
        STEMCraft plugin = mock(STEMCraft.class);
        RecordingChatService chat = new RecordingChatService(plugin);
        when(plugin.chat()).thenReturn(chat);

        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.namespace()).thenReturn("testgame");
        when(arena.id()).thenReturn("arena");
        when(arena.getName()).thenReturn("Arena");
        when(arena.world()).thenReturn(mock(World.class));
        when(arena.getPlayers()).thenReturn(List.of());
        when(arena.getSpectators()).thenReturn(List.of());
        when(arena.getTeams()).thenReturn(List.of());

        MiniGameServiceImpl service = new MiniGameServiceImpl(plugin, mock(STEMCraftAPI.class));
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.STARTING, MiniGameArena.ArenaStatus.RUNNING);
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.PREPARATION, MiniGameArena.ArenaStatus.RUNNING);
        service.notifyArenaStatusChanged(arena, MiniGameArena.ArenaStatus.RUNNING, MiniGameArena.ArenaStatus.ENDING);

        assertEquals(List.of("minecraft.minigame_start", "minecraft.minigame_end"), chat.events);
    }

    private static final class RecordingChatService extends ChatServiceImpl {
        private final List<String> events = new java.util.ArrayList<>();

        private RecordingChatService(STEMCraft plugin) {
            super(plugin, mock(STEMCraftAPI.class));
        }

        @Override
        public void publishChatBridgeEvent(String eventType, Player player, Map<String, Object> data) {
            events.add(eventType);
        }
    }
}
