package dev.stemcraft.feature;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SleepingBagsTest {
    private STEMCraftAPI api;
    private SleepingBags feature;
    private World world;
    private Block foot, head;
    private ItemStack item;

    @BeforeEach void setUp() {
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        feature = new SleepingBags(api);
        world = mock(World.class);
        item = mock(ItemStack.class);
        when(api.items().getCustomItemId(item)).thenReturn(SleepingBags.ITEM_ID);
        // The two halves straddle a chunk boundary, including a negative coordinate.
        foot = half(0, Bed.Part.FOOT);
        head = half(-1, Bed.Part.HEAD);
        when(foot.getRelative(BlockFace.NORTH)).thenReturn(head);
        when(head.getRelative(BlockFace.SOUTH)).thenReturn(foot);
    }

    private Block half(int z, Bed.Part part) {
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class); // Beds no longer implement TileState.
        Chunk chunk = mock(Chunk.class);
        when(chunk.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
        Bed data = mock(Bed.class);
        when(data.getPart()).thenReturn(part);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(block.getBlockData()).thenReturn(data);
        when(block.getState()).thenReturn(state);
        when(block.getChunk()).thenReturn(chunk);
        when(state.getChunk()).thenReturn(chunk);
        when(state.getBlock()).thenReturn(block);
        when(state.getBlockData()).thenReturn(data);
        when(state.getY()).thenReturn(64);
        when(state.getZ()).thenReturn(z);
        return block;
    }

    private void place(ItemStack placedItem) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getItemInHand()).thenReturn(placedItem);
        when(event.getBlockPlaced()).thenReturn(foot);
        feature.onPlace(event);
    }

    @Test void placementMarksBothBedHalvesAndBreakingReturnsTheReusableItem() {
        place(item);
        assertTrue(SleepingBags.isSleepingBag(foot.getState()));
        assertTrue(SleepingBags.isSleepingBag(head.getState()));
        var drop = mock(org.bukkit.event.block.BlockDropItemEvent.class);
        var entity = mock(org.bukkit.entity.Item.class);
        var vanilla = mock(ItemStack.class);
        when(vanilla.getType()).thenReturn(Material.WHITE_BED);
        when(vanilla.getAmount()).thenReturn(1);
        when(entity.getItemStack()).thenReturn(vanilla);
        doReturn(foot.getState()).when(drop).getBlockState();
        when(drop.getItems()).thenReturn(java.util.List.of(entity));
        when(api.items().createCustomItem(SleepingBags.ITEM_ID, 1)).thenReturn(item);
        feature.onDrop(drop);
        verify(entity).setItemStack(item);
        assertFalse(SleepingBags.isSleepingBag(foot.getState()));
        assertFalse(SleepingBags.isSleepingBag(head.getState()));
    }

    @Test void markersSurviveFeatureRecreationButOrdinaryReplacementClearsBothHalves() {
        place(item);
        feature = new SleepingBags(api);
        assertTrue(SleepingBags.isSleepingBag(head.getState()));
        place(mock(ItemStack.class));
        assertFalse(SleepingBags.isSleepingBag(foot.getState()));
        assertFalse(SleepingBags.isSleepingBag(head.getState()));
    }

    @Test void creativeAndNoDropBreaksClearBothHalves() {
        Player player = mock(Player.class);
        // Test fixture constructs the event normally created by the server.
        @SuppressWarnings("UnstableApiUsage")
        BlockBreakEvent event = new BlockBreakEvent(head, player);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        place(item);
        SleepingBags.onBreak(event);
        assertFalse(SleepingBags.isSleepingBag(foot.getState()));
        assertFalse(SleepingBags.isSleepingBag(head.getState()));
        place(item);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        event.setDropItems(false);
        SleepingBags.onBreak(event);
        assertFalse(SleepingBags.isSleepingBag(foot.getState()));
        assertFalse(SleepingBags.isSleepingBag(head.getState()));
    }

    @Test void sleepingBagCancelsOnlyBedSpawnChangesAndSuppressesTheSpawnMessage() {
        place(item);
        Player player = mock(Player.class);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(head);
        Location location = new Location(world, 0, 64, -1);
        // Test fixture constructs the event normally created by the server.
        @SuppressWarnings("UnstableApiUsage")
        PlayerSetSpawnEvent sleeping = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.BED,
            location, false, true, null);
        SleepingBags.onSetSpawn(sleeping);
        assertTrue(sleeping.isCancelled());
        assertFalse(sleeping.willNotifyPlayer());
        // Test fixture constructs the event normally created by the server.
        @SuppressWarnings("UnstableApiUsage")
        PlayerSetSpawnEvent command = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.COMMAND,
            location, true, true, null);
        SleepingBags.onSetSpawn(command);
        assertFalse(command.isCancelled());
        place(mock(ItemStack.class));
        // Test fixture constructs the event normally created by the server.
        @SuppressWarnings("UnstableApiUsage")
        PlayerSetSpawnEvent ordinaryBed = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.BED,
            location, false, true, null);
        SleepingBags.onSetSpawn(ordinaryBed);
        assertFalse(ordinaryBed.isCancelled());
        verifyNoInteractions(player);
    }
}
