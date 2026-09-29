package dev.stemcraft.feature;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

/** Reusable beds that participate in normal sleeping without changing respawn locations. */
public final class SleepingBags extends BaseFeature {
    static final String ITEM_ID = "stemcraft:sleeping_bag";
    private static final NamespacedKey BAG_KEY = new NamespacedKey("stemcraft", "sleeping-bag");

    public SleepingBags(STEMCraftAPI api) { super(api); }

    @Override
    public void onEnable() {
        api.events().register(BlockPlaceEvent.class, this::onPlace, EventPriority.MONITOR, true);
        api.events().register(BlockDropItemEvent.class, this::onDrop, EventPriority.HIGHEST, true);
        api.events().register(BlockBreakEvent.class, SleepingBags::onBreak, EventPriority.MONITOR, true);
        api.events().register(BlockExplodeEvent.class, event -> event.blockList().forEach(block -> unmark(block.getState())), EventPriority.MONITOR, true);
        api.events().register(EntityExplodeEvent.class, event -> event.blockList().forEach(block -> unmark(block.getState())), EventPriority.MONITOR, true);
        api.events().register(PlayerSetSpawnEvent.class, SleepingBags::onSetSpawn, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
    }

    void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        // Remove stale coordinate markers when any new block replaces a sleeping bag.
        unmark(block.getState());
        if (!ITEM_ID.equals(api.items().getCustomItemId(event.getItemInHand()))) return;
        mark(block);
        if (block.getBlockData() instanceof Bed bed) {
            mark(block.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace()));
        }
    }

    private static void mark(Block block) {
        block.getChunk().getPersistentDataContainer().set(positionKey(block.getState()), PersistentDataType.BYTE, (byte) 1);
    }

    private static NamespacedKey positionKey(BlockState state) {
        return new NamespacedKey("stemcraft", "sleeping-bag/" + (state.getX() & 15) + "/" + state.getY() + "/" + (state.getZ() & 15));
    }

    private static void unmark(BlockState state) {
        state.getChunk().getPersistentDataContainer().remove(positionKey(state));
        if (state.getBlockData() instanceof Bed bed) {
            Block other = state.getBlock().getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
            other.getChunk().getPersistentDataContainer().remove(positionKey(other.getState()));
        }
    }

    static void onBreak(BlockBreakEvent event) {
        // Survival drops use the marker in onDrop; creative/no-drop breaks have no drop event.
        if (!event.isDropItems() || event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            unmark(event.getBlock().getState());
        }
    }

    static boolean isSleepingBag(BlockState state) {
        if (!(state.getBlockData() instanceof Bed)) return false;
        return state.getChunk().getPersistentDataContainer().has(positionKey(state), PersistentDataType.BYTE)
            // Read existing markers where an older server still exposes the former tile state.
            || state instanceof TileState tile && tile.getPersistentDataContainer().has(BAG_KEY, PersistentDataType.BYTE);
    }

    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getCause() == PlayerSetSpawnEvent.Cause.BED && event.getLocation() != null
            && isSleepingBag(event.getLocation().getBlock().getState())) {
            event.setCancelled(true);
            event.setNotifyPlayer(false);
        }
    }

    private void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
            || !isSleepingBag(event.getClickedBlock().getState())) return;
        if (event.getClickedBlock().getWorld().getEnvironment() != World.Environment.NORMAL) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            api.messages().send(event.getPlayer(), "/warn/Sleeping bags can only be used in the Overworld.");
        }
    }

    void onDrop(BlockDropItemEvent event) {
        if (!isSleepingBag(event.getBlockState())) return;
        unmark(event.getBlockState());
        for (var entity : event.getItems()) {
            if (entity.getItemStack().getType().name().endsWith("_BED")) {
                var bag = api.items().createCustomItem(ITEM_ID, entity.getItemStack().getAmount());
                if (bag != null) entity.setItemStack(bag);
            }
        }
    }
}
