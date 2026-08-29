package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.PatternUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** World-scoped survival profession progression backed by persistent player stats. */
public final class ProfessionsFeature extends BaseFeature {
    static final int MAX_LEVEL = 100;
    private static final List<SkillDefinition> SKILLS = List.of(
        new SkillDefinition("mining", "Mining", "profession_mining"),
        new SkillDefinition("herbalism", "Herbalism", "profession_herbalism"),
        new SkillDefinition("farming", "Farming", "profession_farming"),
        new SkillDefinition("fishing", "Fishing", "profession_fishing"),
        new SkillDefinition("cooking", "Cooking", "profession_cooking"),
        new SkillDefinition("engineering", "Engineering", "profession_engineering"),
        new SkillDefinition("melee_combat", "Melee Combat", "profession_melee"),
        new SkillDefinition("ranged_combat", "Ranged Combat", "profession_ranged")
    );
    private static final Set<Material> MINING = EnumSet.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);
    private static final Set<Material> HERBS = EnumSet.of(
        Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET,
        Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY,
        Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SUNFLOWER, Material.LILAC,
        Material.ROSE_BUSH, Material.PEONY, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.NETHER_WART,
        Material.SWEET_BERRY_BUSH, Material.CAVE_VINES, Material.CAVE_VINES_PLANT);
    private static final Set<Material> CROPS = EnumSet.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
        Material.COCOA, Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
        Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);
    private static final Set<Material> ENGINEERING = EnumSet.of(
        Material.REDSTONE, Material.REDSTONE_TORCH, Material.REPEATER, Material.COMPARATOR, Material.OBSERVER,
        Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER, Material.DROPPER, Material.HOPPER,
        Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL, Material.MINECART,
        Material.CHEST_MINECART, Material.HOPPER_MINECART, Material.FURNACE_MINECART, Material.TNT_MINECART);

    private List<Pattern> worldPatterns = List.of();
    private String levelMessage;

    public ProfessionsFeature(STEMCraftAPI api) { super(api); }

    @Override
    public void onEnable() {
        loadConfig();
        api.database().execute("CREATE TABLE IF NOT EXISTS profession_placed_source (" +
            "world_name TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL," +
            "PRIMARY KEY(world_name,x,y,z));");
        SKILLS.forEach(skill -> api.playerStats().register(new PlayerStatDefinition(
            statKey(skill.key()), skill.title() + " XP", "Lifetime experience earned in " + skill.title() + ".")));
        registerSkillsCommand();
        api.events().register(BlockPlaceEvent.class, this::onPlace, EventPriority.MONITOR, true);
        api.events().register(BlockBreakEvent.class, this::onBreak, EventPriority.MONITOR, true);
        api.events().register(PlayerFishEvent.class, this::onFish, EventPriority.MONITOR, true);
        api.events().register(FurnaceExtractEvent.class, this::onExtract, EventPriority.MONITOR, true);
        api.events().register(CraftItemEvent.class, this::onCraft, EventPriority.MONITOR, true);
        api.events().register(EntityDeathEvent.class, this::onDeath, EventPriority.MONITOR, true);
    }

    @Override
    public void onReload() { super.onReload(); loadConfig(); }

    private void loadConfig() {
        ConfigSection config = getConfigSection();
        List<String> worlds = config.getStringList("worlds");
        if (worlds.isEmpty()) worlds = List.of("survival*");
        worldPatterns = worlds.stream().filter(value -> value != null && !value.isBlank())
            .map(value -> PatternUtil.globToRegex(value.toLowerCase(Locale.ROOT))).toList();
        levelMessage = config.getString("level-up-message", "&eYou have reached {skill} Level {level}");
    }

    private void onPlace(BlockPlaceEvent event) {
        if (!matchesWorld(event.getPlayer())) return;
        Material type = event.getBlockPlaced().getType();
        if (MINING.contains(type) || HERBS.contains(type)) markPlaced(event.getBlockPlaced());
    }

    private void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        boolean placed = (MINING.contains(type) || HERBS.contains(type)) && unmarkPlaced(block);
        if (!eligible(player)) return;
        if (MINING.contains(type) && !placed) award(player, "mining", miningXp(type));
        if (HERBS.contains(type) && !placed) award(player, "herbalism", 4);
        if (CROPS.contains(type) && mature(block)) award(player, "farming", 5);
    }

    private void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && eligible(event.getPlayer()))
            award(event.getPlayer(), "fishing", 10);
    }

    private void onExtract(FurnaceExtractEvent event) {
        if (eligible(event.getPlayer()) && event.getItemType().isEdible())
            award(event.getPlayer(), "cooking", Math.max(1, event.getItemAmount() * 3));
    }

    private void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !eligible(player)) return;
        Material result = event.getRecipe().getResult().getType();
        int amount = Math.max(1, event.getRecipe().getResult().getAmount());
        if (result.isEdible()) award(player, "cooking", amount * 2);
        if (ENGINEERING.contains(result)) award(player, "engineering", engineeringXp(result) * amount);
    }

    private void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy)) return;
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) return;
        Player player = meleePlayer(damage);
        if (player != null && eligible(player)) award(player, "melee_combat", 10);
        player = rangedPlayer(damage);
        if (player != null && eligible(player)) award(player, "ranged_combat", 10);
    }

    private static Player meleePlayer(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player player ? player : null;
    }

    private static Player rangedPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return null;
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player && (projectile instanceof AbstractArrow || projectile.getType().name().contains("TRIDENT"))
            ? player : null;
    }

    private boolean eligible(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        return matchesWorld(player);
    }

    private boolean matchesWorld(Player player) {
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return worldPatterns.stream().anyMatch(pattern -> pattern.matcher(world).matches());
    }

    private void award(Player player, String skill, int amount) {
        String key = statKey(skill);
        double before = api.playerStats().total(player.getUniqueId(), key);
        int oldLevel = levelForXp(before);
        api.playerStats().increment(player.getUniqueId(), player.getName(), key, amount);
        int newLevel = levelForXp(before + amount);
        if (newLevel > oldLevel) api.messages().send(player, levelMessage,
            "skill", skill(skill).title(), "level", newLevel);
    }

    private void registerSkillsCommand() {
        api.commands().create("skills")
            .aliases("professions")
            .description("View your profession levels and progress.")
            .usage("/skills [skill]")
            .tabCompletion(SKILLS.stream().map(SkillDefinition::commandName).toArray(String[]::new))
            .executor((unused, command, context) -> {
                context.checkNotConsole();
                Player player = context.asPlayer();
                if (context.args().isEmpty()) {
                    showSkills(player);
                    return;
                }
                SkillDefinition selected = findSkill(context.getArg(0));
                if (selected == null) {
                    command.error(player, "Unknown skill '{skill}'. Try /skills to see your skills.",
                        "skill", context.getArg(0));
                    return;
                }
                showSkill(player, selected);
            }).register(STEMCraft.getPlugin());
    }

    private void showSkills(Player player) {
        api.messages().send(player, "&6:star: Your Skills");
        for (SkillDefinition skill : SKILLS) {
            double xp = api.playerStats().total(player.getUniqueId(), statKey(skill.key()));
            api.messages().send(player, "&f:" + skill.icon() + ": &e{skill} &7— &fLevel {level} &8({xp} XP)",
                "skill", skill.title(), "level", levelForXp(xp), "xp", formatXp(xp));
        }
        api.messages().send(player, "&7Use &f/skills <skill> &7for progress to the next level.");
    }

    private void showSkill(Player player, SkillDefinition skill) {
        double xp = api.playerStats().total(player.getUniqueId(), statKey(skill.key()));
        int level = levelForXp(xp);
        api.messages().send(player, "&6:" + skill.icon() + ": {skill} &7— &eLevel {level}",
            "skill", skill.title(), "level", level);
        if (level >= MAX_LEVEL) {
            api.messages().send(player, "&aMaximum level reached! &8({xp} lifetime XP)", "xp", formatXp(xp));
            return;
        }
        long currentThreshold = xpForLevel(level);
        long nextThreshold = xpForLevel(level + 1);
        long earned = Math.max(0L, (long) Math.floor(xp) - currentThreshold);
        long needed = nextThreshold - currentThreshold;
        int percent = (int) Math.clamp(Math.floor(earned * 100.0d / needed), 0, 100);
        api.messages().send(player, "&e{earned} &7/ &e{needed} XP &7toward Level {next} &8({percent}%)",
            "earned", formatXp(earned), "needed", formatXp(needed), "next", level + 1, "percent", percent);
        api.messages().send(player, progressBar(percent));
        api.messages().send(player, "&8Lifetime XP: {xp}", "xp", formatXp(xp));
    }

    static String progressBar(int percent) {
        int filled = Math.clamp(percent, 0, 100) / 10;
        return "&a" + "■".repeat(filled) + "&8" + "■".repeat(10 - filled);
    }

    private static String formatXp(double xp) {
        return String.format(Locale.ROOT, "%,.0f", xp);
    }

    private static SkillDefinition findSkill(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("melee")) normalized = "melee_combat";
        if (normalized.equals("ranged")) normalized = "ranged_combat";
        for (SkillDefinition skill : SKILLS) if (skill.key().equals(normalized)) return skill;
        return null;
    }

    private static SkillDefinition skill(String key) {
        for (SkillDefinition skill : SKILLS) if (skill.key().equals(key)) return skill;
        throw new IllegalArgumentException("Unknown profession: " + key);
    }

    static String statKey(String skill) { return "skill_" + skill + "_xp"; }

    public static int levelForXp(double xp) {
        double completedLevels = Math.floor(Math.sqrt(Math.max(0, xp) / 100.0));
        return 1 + (int) Math.min(MAX_LEVEL - 1, completedLevels);
    }

    static long xpForLevel(int level) {
        int normalized = Math.max(1, Math.min(MAX_LEVEL, level));
        long completed = normalized - 1L;
        return 100L * completed * completed;
    }

    private static boolean mature(Block block) {
        return !(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge();
    }

    private static int miningXp(Material material) {
        String name = material.name();
        if (name.contains("ANCIENT_DEBRIS")) return 30;
        if (name.contains("DIAMOND") || name.contains("EMERALD")) return 20;
        if (name.contains("GOLD") || name.contains("LAPIS") || name.contains("REDSTONE")) return 10;
        return 6;
    }

    private static int engineeringXp(Material material) {
        String name = material.name();
        if (name.contains("MINECART") || material == Material.HOPPER) return 10;
        if (name.contains("RAIL") || material == Material.PISTON || material == Material.STICKY_PISTON) return 5;
        return 3;
    }

    private void markPlaced(Block block) {
        api.database().update("INSERT OR REPLACE INTO profession_placed_source(world_name,x,y,z) VALUES(?,?,?,?)", statement -> {
            statement.setString(1, block.getWorld().getName()); statement.setInt(2, block.getX());
            statement.setInt(3, block.getY()); statement.setInt(4, block.getZ());
        });
    }

    private boolean unmarkPlaced(Block block) {
        Integer found = api.database().querySingleMapped(
            "SELECT 1 FROM profession_placed_source WHERE world_name=? AND x=? AND y=? AND z=?", statement -> {
                statement.setString(1, block.getWorld().getName()); statement.setInt(2, block.getX());
                statement.setInt(3, block.getY()); statement.setInt(4, block.getZ());
            }, result -> result.getInt(1));
        if (found == null) return false;
        api.database().update("DELETE FROM profession_placed_source WHERE world_name=? AND x=? AND y=? AND z=?", statement -> {
            statement.setString(1, block.getWorld().getName()); statement.setInt(2, block.getX());
            statement.setInt(3, block.getY()); statement.setInt(4, block.getZ());
        });
        return true;
    }

    private record SkillDefinition(String key, String title, String icon) {
        String commandName() {
            return key.endsWith("_combat") ? key.substring(0, key.length() - "_combat".length()) : key;
        }
    }
}
