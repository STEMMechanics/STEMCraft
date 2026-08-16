package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Keeps Slime in a Bucket visuals synchronized with the holder's slime chunk. */
public final class SlimeBuckets extends BaseFeature {
    static final String SLIME_BUCKET_ID = "slime-bucket";
    static final String EXCITED_STATE = "excited";

    public SlimeBuckets(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerMoveEvent.class, this::onMove, EventPriority.MONITOR, true);
        api.events().register(PlayerTeleportEvent.class, event -> refreshNextTick(event.getPlayer()), EventPriority.MONITOR, true);
        api.events().register(PlayerChangedWorldEvent.class, event -> refreshNextTick(event.getPlayer()));
        api.events().register(PlayerJoinEvent.class, event -> refreshNextTick(event.getPlayer()));
        api.events().register(PlayerRespawnEvent.class, event -> refreshNextTick(event.getPlayer()));
        api.events().register(PlayerItemHeldEvent.class, event -> refreshNextTick(event.getPlayer()));
        api.events().register(PlayerSwapHandItemsEvent.class, event -> refreshNextTick(event.getPlayer()));
        api.events().register(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
        });
        api.events().register(InventoryDragEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
        });
        api.events().register(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) refreshNextTick(player);
        }, EventPriority.MONITOR, true);
        api.events().register(PlayerDropItemEvent.class, event -> {
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (setExcited(dropped, false)) event.getItemDrop().setItemStack(dropped);
            refreshNextTick(event.getPlayer());
        }, EventPriority.MONITOR, true);
        api.events().register(PlayerDeathEvent.class, event -> {
            for (int slot = 0; slot < event.getDrops().size(); slot++) {
                ItemStack item = event.getDrops().get(slot);
                if (setExcited(item, false)) event.getDrops().set(slot, item);
            }
        }, EventPriority.MONITOR, false);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(this::normalizeInventory);
    }

    private void onMove(@NotNull PlayerMoveEvent event) {
        if (crossedChunk(event.getFrom(), event.getTo())) refresh(event.getPlayer());
    }

    static boolean crossedChunk(@NotNull Location from, Location to) {
        return to != null && (from.getWorld() != to.getWorld()
            || (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
            || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4));
    }

    private void refreshNextTick(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        api.tasks().nextTick(() -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) refresh(online);
        });
    }

    private void refresh(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        normalizeStorage(inventory);
        var top = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack item = top.getItem(slot);
            if (setExcited(item, false)) top.setItem(slot, item);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (setExcited(cursor, false)) player.setItemOnCursor(cursor);
        boolean excited = player.getChunk().isSlimeChunk();
        ItemStack mainHand = inventory.getItemInMainHand();
        if (setExcited(mainHand, excited)) inventory.setItemInMainHand(mainHand);
        ItemStack offHand = inventory.getItemInOffHand();
        if (setExcited(offHand, excited)) inventory.setItemInOffHand(offHand);
    }

    private void normalizeInventory(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        normalizeStorage(inventory);
        ItemStack offHand = inventory.getItemInOffHand();
        if (setExcited(offHand, false)) inventory.setItemInOffHand(offHand);
    }

    private void normalizeStorage(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (setExcited(item, false)) inventory.setItem(slot, item);
        }
    }

    private boolean setExcited(ItemStack item, boolean excited) {
        return item != null && api.items().isCustomItemId(SLIME_BUCKET_ID, item)
            && api.items().applyCustomItemVisualState(item, excited ? EXCITED_STATE : null);
    }
}
