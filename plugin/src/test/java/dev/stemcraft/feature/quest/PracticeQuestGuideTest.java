package dev.stemcraft.feature.quest;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.event.guide.GuideActionRequestEvent;
import dev.stemcraft.api.service.event.EventHandler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeQuestGuideTest {
    private final Map<Class<?>, EventHandler<?>> handlers = new HashMap<>();
    private STEMCraftAPI api;
    private PracticeQuestGuide guide;
    private Player player;
    private Villager npc;
    private Runnable tick;
    private Location position;
    private final List<Boolean> results = new ArrayList<>();

    @BeforeEach void setUp() {
        var server = MockBukkit.mock();
        var inventoryOwner = server.addPlayer();
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var events = api.events();
        var tasks = api.tasks();
        doAnswer(call -> {
            handlers.put(call.getArgument(0), call.getArgument(1));
            return new Listener() {};
        }).when(events).register(any(), any());
        doAnswer(call -> {
            handlers.put(call.getArgument(0), call.getArgument(1));
            return new Listener() {};
        }).when(events).register(any(), any(), any(), anyBoolean());
        doAnswer(call -> { tick = call.getArgument(3); return null; })
            .when(tasks).repeating(eq("quest-practice"), anyLong(), anyLong(), any(Runnable.class));
        Plugin plugin = MockBukkit.createMockPlugin();
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getName()).thenReturn("survival");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn((int) call.getArgument(1) < 64 ? Material.STONE : Material.AIR);
            return block;
        });
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        });
        npc = mock(Villager.class);
        when(npc.isValid()).thenReturn(true);
        doAnswer(call -> {
            Consumer<Villager> initializer = call.getArgument(2);
            initializer.accept(npc);
            return npc;
        }).when(world).spawn(any(Location.class), eq(Villager.class), org.mockito.ArgumentMatchers.<Consumer<Villager>>any());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        position = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenAnswer(call -> position.clone());
        when(player.getInventory()).thenReturn(inventoryOwner.getInventory());
        guide = new PracticeQuestGuide(api, plugin);
        guide.enable();
    }
    @AfterEach void tearDown() { try { if (guide != null) guide.disable(); } finally { MockBukkit.unmock(); } }

    @SuppressWarnings("unchecked")
    private <T extends Event> void fire(T event) { ((EventHandler<T>) handlers.get(event.getClass())).handle(event); }
    private GuideActionRequestEvent begin() {
        var request = new GuideActionRequestEvent(player, "stemcraft:quest-practice", results::add);
        fire(request);
        assertTrue(request.claimed());
        assertFalse(request.finished());
        return request;
    }
    private void click(Player actor) { fire(new PlayerInteractEntityEvent(actor, npc, EquipmentSlot.HAND)); }

    @Test void privateQuestRequiresAcceptanceMovementAndHandInAndGivesNoRewards() {
        var request = begin();
        verify(npc).setVisibleByDefault(false);
        verify(player).showEntity(any(), eq(npc));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        click(other);
        assertTrue(player.getInventory().isEmpty());
        click(player);
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).count());
        click(player);
        assertFalse(request.finished());
        position.add(4, 0, 0);
        tick.run();
        assertFalse(request.finished());
        position.subtract(4, 0, 0);
        click(player);
        assertEquals(List.of(true), results);
        assertTrue(player.getInventory().isEmpty());
        verify(npc).remove();
        verify(api, never()).playerStats();
        verify(player, never()).giveExp(anyInt());
    }

    @Test void cancellationRemovesBookAndNpcAndIgnoresLateClicks() {
        var request = begin();
        click(player);
        request.cancel();
        click(player);
        tick.run();
        assertTrue(results.isEmpty());
        assertTrue(player.getInventory().isEmpty());
        verify(npc, times(1)).remove();
    }

    @Test void staleBookIsRemovedOnJoinAndWorldChangesFailPractice() {
        begin();
        click(player);
        var stale = player.getInventory().getItem(0).clone();
        when(player.getWorld()).thenReturn(mock(World.class));
        tick.run();
        assertEquals(List.of(false), results);
        player.getInventory().addItem(stale);
        fire(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
        assertTrue(player.getInventory().isEmpty());
    }
}
