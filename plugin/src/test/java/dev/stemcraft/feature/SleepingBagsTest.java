package dev.stemcraft.feature;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Bed;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SleepingBagsTest {
    @Test void placementMarksBothBedHalvesAndBreakingReturnsTheReusableItem() {
        var api = mock(dev.stemcraft.api.STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var feature = new SleepingBags(api);
        var item = mock(org.bukkit.inventory.ItemStack.class);
        when(api.items().getCustomItemId(item)).thenReturn(SleepingBags.ITEM_ID);
        var event = mock(org.bukkit.event.block.BlockPlaceEvent.class);
        var foot = mock(Block.class);
        var head = mock(Block.class);
        var footState = mock(Bed.class);
        var headState = mock(Bed.class);
        var footData = mock(PersistentDataContainer.class);
        var headData = mock(PersistentDataContainer.class);
        when(footState.getPersistentDataContainer()).thenReturn(footData);
        when(headState.getPersistentDataContainer()).thenReturn(headData);
        when(foot.getState()).thenReturn(footState);
        when(head.getState()).thenReturn(headState);
        var direction = mock(org.bukkit.block.data.type.Bed.class);
        when(direction.getPart()).thenReturn(org.bukkit.block.data.type.Bed.Part.FOOT);
        when(direction.getFacing()).thenReturn(org.bukkit.block.BlockFace.NORTH);
        when(foot.getBlockData()).thenReturn(direction);
        when(foot.getRelative(org.bukkit.block.BlockFace.NORTH)).thenReturn(head);
        when(event.getItemInHand()).thenReturn(item);
        when(event.getBlockPlaced()).thenReturn(foot);
        feature.onPlace(event);
        var key = new NamespacedKey("stemcraft", "sleeping-bag");
        verify(footData).set(key, PersistentDataType.BYTE, (byte) 1);
        verify(headData).set(key, PersistentDataType.BYTE, (byte) 1);
        verify(footState).update();
        verify(headState).update();

        when(footData.has(key, PersistentDataType.BYTE)).thenReturn(true);
        var drop = mock(org.bukkit.event.block.BlockDropItemEvent.class);
        var entity = mock(org.bukkit.entity.Item.class);
        var vanilla = mock(org.bukkit.inventory.ItemStack.class);
        when(vanilla.getType()).thenReturn(org.bukkit.Material.WHITE_BED);
        when(vanilla.getAmount()).thenReturn(1);
        when(entity.getItemStack()).thenReturn(vanilla);
        when(drop.getBlockState()).thenReturn(footState);
        when(drop.getItems()).thenReturn(java.util.List.of(entity));
        when(api.items().createCustomItem(SleepingBags.ITEM_ID, 1)).thenReturn(item);
        feature.onDrop(drop);
        verify(entity).setItemStack(item);
    }

    @Test void sleepingBagCancelsOnlyBedSpawnChangesAndSuppressesTheSpawnMessage() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Block block = mock(Block.class);
        Bed bed = mock(Bed.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        when(block.getState()).thenReturn(bed);
        when(bed.getPersistentDataContainer()).thenReturn(data);
        when(data.has(new NamespacedKey("stemcraft", "sleeping-bag"), PersistentDataType.BYTE)).thenReturn(true);
        Location location = new Location(world, 1, 64, 2);
        PlayerSetSpawnEvent sleeping = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.BED,
            location, false, true, null);
        SleepingBags.onSetSpawn(sleeping);
        assertTrue(sleeping.isCancelled());
        assertFalse(sleeping.willNotifyPlayer());
        PlayerSetSpawnEvent command = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.COMMAND,
            location, true, true, null);
        SleepingBags.onSetSpawn(command);
        assertFalse(command.isCancelled());
        when(data.has(new NamespacedKey("stemcraft", "sleeping-bag"), PersistentDataType.BYTE)).thenReturn(false);
        PlayerSetSpawnEvent ordinaryBed = new PlayerSetSpawnEvent(player, PlayerSetSpawnEvent.Cause.BED,
            location, false, true, null);
        SleepingBags.onSetSpawn(ordinaryBed);
        assertFalse(ordinaryBed.isCancelled());
        verifyNoInteractions(player);
    }
}
