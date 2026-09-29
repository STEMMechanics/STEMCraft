package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Server-side held lights. Only air is borrowed; chunk metadata allows restart recovery. */
public final class HeldLightFeature extends BaseFeature {
    private static final String TASK = "held-light";
    private static final String PREFIX = "held_light_";
    private final Set<Block> active = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();

    public HeldLightFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) restoreChunk(chunk);
        }
        listeners.add(api.events().register(ChunkLoadEvent.class, e -> restoreChunk(e.getChunk())));
        listeners.add(api.events().register(ChunkUnloadEvent.class, e -> restoreChunk(e.getChunk()), EventPriority.MONITOR, true));
        listeners.add(api.events().register(PlayerQuitEvent.class, e -> tick(e.getPlayer().getUniqueId())));
        listeners.add(api.events().register(BlockPlaceEvent.class, e -> {
            var states = e instanceof BlockMultiPlaceEvent multi ? multi.getReplacedBlockStates() : List.of(e.getBlockReplacedState());
            for (var state : states) {
                Block block = state.getBlock();
                // The new block belongs to the player, even when they place a LIGHT block.
                if (active.remove(block)) block.getChunk().getPersistentDataContainer().remove(key(block));
            }
        }, EventPriority.MONITOR, true));
        api.tasks().repeating(TASK, 1L, 5L, () -> tick(null));
    }

    @Override public void onReload() {
        super.onReload();
        if (!isEnabled()) clear();
    }

    @Override public void onDisable() {
        api.tasks().cancel(TASK);
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        clear();
    }

    private void clear() {
        for (Block block : Set.copyOf(active)) releaseTemporaryLight(block);
        active.clear();
    }

    void tick(UUID excludedPlayer) {
        Map<Block, Integer> desired = new HashMap<>();
        if (isEnabled()) for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(excludedPlayer) || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
            int level = Math.max(level(player.getInventory().getItemInMainHand().getType()),
                    level(player.getInventory().getItemInOffHand().getType()));
            if (level == 0) continue;
            Location eye = player.getEyeLocation();
            if (!eye.isChunkLoaded()) continue;
            Block block = eye.getBlock();
            if (!available(block)) block = player.getLocation().getBlock();
            if (available(block)) desired.merge(block, level, Math::max);
        }
        for (Block block : Set.copyOf(active)) {
            if (!desired.containsKey(block)) { releaseTemporaryLight(block); active.remove(block); }
        }
        desired.forEach(this::illuminate);
    }

    static int level(Material type) {
        return switch (type) {
            case TORCH -> 14;
            case LANTERN -> 15;
            case SOUL_TORCH, SOUL_LANTERN -> 10;
            default -> 0;
        };
    }

    private boolean available(Block block) {
        return block.getY() >= block.getWorld().getMinHeight() && block.getY() < block.getWorld().getMaxHeight()
                && (block.getType().isAir() || (active.contains(block) && block.getType() == Material.LIGHT && isTemporaryLight(block)));
    }

    void illuminate(Block block, int level) {
        if (!available(block)) return;
        if (block.getType().isAir()) {
            block.getChunk().getPersistentDataContainer().set(key(block), PersistentDataType.STRING, block.getType().name());
        }
        active.add(block);
        if (block.getBlockData() instanceof Light existing && existing.getLevel() == level) return;
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        block.setBlockData(light, false);
    }

    /** Also works during placement, when the temporary light has already been replaced. */
    public static boolean isTemporaryLight(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && block.getChunk().getPersistentDataContainer().has(key(block), PersistentDataType.STRING);
    }

    private static NamespacedKey key(Block block) {
        return new NamespacedKey("stemcraft", PREFIX + (block.getX() & 15) + "_" + block.getY() + "_" + (block.getZ() & 15));
    }

    /** Release borrowed air before another feature takes ownership of this position. */
    public static void releaseTemporaryLight(Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
        var data = block.getChunk().getPersistentDataContainer();
        String original = data.get(key(block), PersistentDataType.STRING);
        if (original != null && block.getType() == Material.LIGHT) {
            Material material = Material.matchMaterial(original);
            if (material != null && material.isAir()) block.setType(material, false);
        }
        data.remove(key(block));
    }

    void restoreChunk(Chunk chunk) {
        var data = chunk.getPersistentDataContainer();
        for (NamespacedKey key : Set.copyOf(data.getKeys())) {
            if (!key.getNamespace().equals("stemcraft") || !key.getKey().startsWith(PREFIX)) continue;
            String[] coordinates = key.getKey().substring(PREFIX.length()).split("_");
            if (coordinates.length != 3) continue;
            try {
                int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]), z = Integer.parseInt(coordinates[2]);
                if (x < 0 || x > 15 || z < 0 || z > 15 || y < chunk.getWorld().getMinHeight() || y >= chunk.getWorld().getMaxHeight()) continue;
                Block block = chunk.getBlock(x, y, z);
                releaseTemporaryLight(block);
                active.remove(block);
            } catch (NumberFormatException ignored) {
                // Ignore foreign/malformed metadata without touching terrain.
            }
        }
    }
}
