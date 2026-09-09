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
import org.bukkit.event.block.BlockPlaceEvent;
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
        api.events().register(PlayerSetSpawnEvent.class, SleepingBags::onSetSpawn, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
    }

    void onPlace(BlockPlaceEvent event) {
        if (!ITEM_ID.equals(api.items().getCustomItemId(event.getItemInHand()))) return;
        Block block = event.getBlockPlaced();
        mark(block);
        if (block.getBlockData() instanceof Bed bed) {
            mark(block.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace()));
        }
    }

    private static void mark(Block block) {
        if (block.getState() instanceof org.bukkit.block.Bed bed) {
            bed.getPersistentDataContainer().set(BAG_KEY, PersistentDataType.BYTE, (byte) 1);
            bed.update();
        }
    }

    static boolean isSleepingBag(BlockState state) {
        return state instanceof TileState tile
            && tile.getPersistentDataContainer().has(BAG_KEY, PersistentDataType.BYTE);
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
        for (var entity : event.getItems()) {
            if (entity.getItemStack().getType().name().endsWith("_BED")) {
                var bag = api.items().createCustomItem(ITEM_ID, entity.getItemStack().getAmount());
                if (bag != null) entity.setItemStack(bag);
            }
        }
    }
}
