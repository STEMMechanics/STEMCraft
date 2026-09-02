package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Generic data-pack-driven crops and biome-aware forage drops. */
public final class AgricultureFeature extends BaseFeature {
    private static final String TASK_ID = "feature:agriculture-growth";
    private final Map<String, CropDefinition> crops = new HashMap<>();
    private final List<ForageDefinition> forage = new ArrayList<>();
    private final Map<BlockKey, CropState> plantedCrops = new HashMap<>();
    private final Set<BlockKey> placedForageSources = new HashSet<>();

    public AgricultureFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        ensureStorage();
        loadDefinitions();
        loadState();
        api.events().register(PlayerInteractEvent.class, this::onPlant, EventPriority.HIGHEST, true);
        api.events().register(BlockBreakEvent.class, this::onBreak, EventPriority.HIGHEST, true);
        api.events().register(BlockPlaceEvent.class, this::onPlace, EventPriority.MONITOR, true);
        api.events().register(BlockExplodeEvent.class, event -> event.blockList().removeIf(this::isCrop));
        api.events().register(EntityExplodeEvent.class, event -> event.blockList().removeIf(this::isCrop));
        api.events().register(BlockFertilizeEvent.class, event -> {
            if (isCrop(event.getBlock())) event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
        api.events().register(BlockPistonExtendEvent.class, event -> {
            if (event.getBlocks().stream().anyMatch(this::isCrop)) event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
        api.events().register(BlockPistonRetractEvent.class, event -> {
            if (event.getBlocks().stream().anyMatch(this::isCrop)) event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
        api.tasks().repeating(TASK_ID, 100L, this::growLoadedCrops);
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(TASK_ID);
        crops.clear();
        forage.clear();
        plantedCrops.clear();
        placedForageSources.clear();
    }

    private void loadDefinitions() {
        crops.clear();
        forage.clear();
        File packs = new File(STEMCraft.getPlugin().getDataFolder(), "data-packs");
        File[] directories = packs.listFiles(File::isDirectory);
        if (directories == null) return;
        for (File directory : directories) {
            File root = new File(directory, "config.yml");
            ConfigSection rootConfig = root.isFile() ? api.config().load(root) : null;
            if (rootConfig == null || !rootConfig.getBoolean("pack.enabled", true)) continue;
            loadAgriculture(rootConfig.getSection("agriculture", false));
            File configs = new File(directory, "configs");
            File[] files = configs.listFiles((ignored, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files != null) for (File file : files) {
                ConfigSection config = api.config().load(file);
                if (config != null) loadAgriculture(config.getSection("agriculture", false));
            }
        }
    }

    private void loadAgriculture(ConfigSection section) {
        if (section == null) return;
        ConfigSection cropSection = section.getSection("crops", false);
        if (cropSection != null) for (String id : cropSection.getKeys(false)) {
            ConfigSection value = cropSection.getSection(id, false);
            if (value == null) continue;
            Material soil = material(value.getString("soil"));
            List<Material> stages = value.getStringList("stages").stream().map(this::material).filter(java.util.Objects::nonNull).toList();
            if (soil == null || stages.isEmpty()) continue;
            crops.put(id, new CropDefinition(id, value.getString("seed"), soil,
                value.getBoolean("water-above", false), Math.max(1, value.getInt("light-min", 9)), stages,
                Math.max(1L, value.getLong("seconds-per-stage", 180L)), value.getString("mature-drop"),
                Math.max(1, value.getInt("mature-min", 2)), Math.max(1, value.getInt("mature-max", 4)),
                value.getString("seed-drop"), Math.max(1, value.getInt("seed-min", 1)),
                Math.max(1, value.getInt("seed-max", 2)), value.getBoolean("fortune", true)));
        }
        ConfigSection forageSection = section.getSection("foraging", false);
        if (forageSection != null) for (String id : forageSection.getKeys(false)) {
            ConfigSection value = forageSection.getSection(id, false);
            if (value == null) continue;
            Set<Material> blocks = new HashSet<>();
            value.getStringList("blocks").stream().map(this::material).filter(java.util.Objects::nonNull).forEach(blocks::add);
            Set<Biome> biomes = new HashSet<>();
            for (String raw : value.getStringList("biomes")) {
                NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
                Biome biome = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
                if (biome != null) biomes.add(biome);
            }
            if (!blocks.isEmpty() && !value.getString("item").isBlank()) forage.add(new ForageDefinition(
                id, blocks, biomes, value.getDouble("chance", 0D), value.getString("item"),
                Math.max(1, value.getInt("min", 1)), Math.max(1, value.getInt("max", 1)),
                value.getBoolean("fortune", false), value.getBoolean("natural-only", true),
                value.getStringList("tools").stream().map(this::material).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet())));
        }
    }

    private void onPlant(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
            || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getItem() == null) return;
        String itemId = api.items().getCustomItemId(event.getItem());
        if (itemId == null) return;
        CropDefinition crop = crops.values().stream().filter(value -> matchesId(value.seed(), itemId)).findFirst().orElse(null);
        if (crop == null || event.getClickedBlock().getType() != crop.soil()) return;
        Block target = event.getClickedBlock().getRelative(0, 1, 0);
        if (crop.waterAbove() ? target.getType() != Material.WATER : !target.getType().isAir()) return;
        if (target.getLightLevel() < crop.lightMin()) return;
        event.setCancelled(true);
        target.setType(crop.stages().getFirst(), false);
        if (target.getBlockData() instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(crop.waterAbove());
            target.setBlockData(waterlogged, false);
        }
        consumeOne(event.getPlayer(), event.getItem());
        CropState state = new CropState(crop.id(), 0, System.currentTimeMillis());
        BlockKey key = BlockKey.of(target);
        plantedCrops.put(key, state);
        saveCrop(key, state);
    }

    private void onPlace(BlockPlaceEvent event) {
        if (forage.stream().noneMatch(value -> value.naturalOnly() && value.blocks().contains(event.getBlockPlaced().getType()))) return;
        BlockKey key = BlockKey.of(event.getBlockPlaced());
        placedForageSources.add(key);
        api.database().update("INSERT OR REPLACE INTO agriculture_placed_source(world_name,x,y,z) VALUES(?,?,?,?)", statement -> bind(statement, key));
    }

    private void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockKey key = BlockKey.of(block);
        CropState cropState = plantedCrops.remove(key);
        if (cropState != null) {
            event.setDropItems(false);
            api.database().update("DELETE FROM agriculture_crop WHERE world_name=? AND x=? AND y=? AND z=?", statement -> bind(statement, key));
            CropDefinition crop = crops.get(cropState.cropId());
            if (crop != null) dropCrop(block, event.getPlayer().getInventory().getItemInMainHand(), crop, cropState.stage());
            return;
        }
        boolean playerPlaced = placedForageSources.remove(key);
        if (playerPlaced) api.database().update("DELETE FROM agriculture_placed_source WHERE world_name=? AND x=? AND y=? AND z=?", statement -> bind(statement, key));
        for (ForageDefinition definition : forage) {
            if (!definition.blocks().contains(block.getType()) || (!definition.biomes().isEmpty() && !definition.biomes().contains(block.getBiome()))
                || (definition.naturalOnly() && playerPlaced) || !definition.tools().isEmpty()
                && !definition.tools().contains(event.getPlayer().getInventory().getItemInMainHand().getType())) continue;
            int fortune = definition.fortune() ? event.getPlayer().getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE) : 0;
            if (ThreadLocalRandom.current().nextDouble() <= definition.chance() * (1D + fortune * 0.25D))
                drop(block.getLocation(), definition.item(), random(definition.min(), definition.max() + fortune));
        }
    }

    private void growLoadedCrops() {
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockKey, CropState> entry : new ArrayList<>(plantedCrops.entrySet())) {
            BlockKey key = entry.getKey();
            var world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            CropDefinition crop = crops.get(entry.getValue().cropId());
            if (crop == null) continue;
            int desired = Math.min(crop.stages().size() - 1,
                (int) ((now - entry.getValue().plantedAt()) / (crop.secondsPerStage() * 1000L)));
            if (desired <= entry.getValue().stage()) continue;
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            block.setType(crop.stages().get(desired), false);
            var blockData = block.getBlockData();
            if (blockData instanceof Waterlogged waterlogged) waterlogged.setWaterlogged(crop.waterAbove());
            if (blockData instanceof Ageable ageable) {
                ageable.setAge(Math.min(ageable.getMaximumAge(), desired));
            }
            block.setBlockData(blockData, false);
            CropState updated = new CropState(crop.id(), desired, entry.getValue().plantedAt());
            plantedCrops.put(key, updated);
            saveCrop(key, updated);
        }
    }

    private boolean isCrop(Block block) {
        return plantedCrops.containsKey(BlockKey.of(block));
    }

    private void dropCrop(Block block, ItemStack tool, CropDefinition crop, int stage) {
        boolean mature = stage >= crop.stages().size() - 1;
        int fortune = crop.fortune() ? tool.getEnchantmentLevel(Enchantment.FORTUNE) : 0;
        if (mature) drop(block.getLocation(), crop.matureDrop(), random(crop.matureMin(), crop.matureMax() + fortune));
        drop(block.getLocation(), crop.seedDrop(), mature ? random(crop.seedMin(), crop.seedMax() + fortune) : 1);
    }

    private void drop(Location location, String id, int amount) {
        ItemStack item = api.items().createCustomItem(id, Math.max(1, amount));
        if (item != null) location.getWorld().dropItemNaturally(location, item);
    }

    private void ensureStorage() {
        api.database().execute("CREATE TABLE IF NOT EXISTS agriculture_crop(world_name TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,crop_id TEXT NOT NULL,stage INTEGER NOT NULL,planted_at INTEGER NOT NULL,PRIMARY KEY(world_name,x,y,z))");
        api.database().execute("CREATE TABLE IF NOT EXISTS agriculture_placed_source(world_name TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,PRIMARY KEY(world_name,x,y,z))");
    }

    private void loadState() {
        api.database().queryEach("SELECT world_name,x,y,z,crop_id,stage,planted_at FROM agriculture_crop", null, result ->
            plantedCrops.put(new BlockKey(result.getString("world_name"), result.getInt("x"), result.getInt("y"), result.getInt("z")),
                new CropState(result.getString("crop_id"), result.getInt("stage"), result.getLong("planted_at"))));
        api.database().queryEach("SELECT world_name,x,y,z FROM agriculture_placed_source", null, result ->
            placedForageSources.add(new BlockKey(result.getString("world_name"), result.getInt("x"), result.getInt("y"), result.getInt("z"))));
    }

    private void saveCrop(BlockKey key, CropState state) {
        api.database().update("INSERT OR REPLACE INTO agriculture_crop(world_name,x,y,z,crop_id,stage,planted_at) VALUES(?,?,?,?,?,?,?)", statement -> {
            bind(statement, key); statement.setString(5, state.cropId()); statement.setInt(6, state.stage()); statement.setLong(7, state.plantedAt());
        });
    }

    private static void bind(java.sql.PreparedStatement statement, BlockKey key) throws java.sql.SQLException {
        statement.setString(1, key.world()); statement.setInt(2, key.x()); statement.setInt(3, key.y()); statement.setInt(4, key.z());
    }

    private Material material(String value) { return value == null ? null : Material.matchMaterial(value.toUpperCase(Locale.ROOT)); }
    private static boolean matchesId(String configured, String actual) { return configured.replace('_', '-').substring(configured.contains(":") ? configured.indexOf(':') + 1 : 0).equalsIgnoreCase(actual); }
    private static int random(int min, int max) { return ThreadLocalRandom.current().nextInt(min, Math.max(min, max) + 1); }
    private static void consumeOne(org.bukkit.entity.Player player, ItemStack item) { if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) item.subtract(1); }

    record CropDefinition(String id, String seed, Material soil, boolean waterAbove, int lightMin, List<Material> stages,
                          long secondsPerStage, String matureDrop, int matureMin, int matureMax, String seedDrop,
                          int seedMin, int seedMax, boolean fortune) { }
    record ForageDefinition(String id, Set<Material> blocks, Set<Biome> biomes, double chance, String item,
                            int min, int max, boolean fortune, boolean naturalOnly, Set<Material> tools) { }
    record CropState(String cropId, int stage, long plantedAt) { }
    record BlockKey(String world, int x, int y, int z) { static BlockKey of(Block block) { return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()); } }
}
