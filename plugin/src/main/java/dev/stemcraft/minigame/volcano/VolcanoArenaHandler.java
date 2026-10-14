package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

final class VolcanoArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final VolcanoMiniGame owner;
    private final Map<MiniGameArena, Round> rounds = new HashMap<>();

    static final class Round {
        final Map<UUID, Long> nextFlameDamage = new HashMap<>();
        final Map<UUID, Location> respawns = new HashMap<>();
        final Map<UUID, Long> survivalTicks = new HashMap<>();
        VolcanoEruption eruption;
        long tick;
        int initialPlayers;
        // Keep HUD text short; detailed instructions are sent to chat on join.
        String objective = "";
    }

    VolcanoArenaHandler(STEMCraftAPI api, VolcanoMiniGame owner) {
        this.api = api;
        this.owner = owner;
    }

    private VolcanoArenaSettings settings(MiniGameArena a) {
        return owner.settings(a);
    }

    private String task(MiniGameArena a) {
        return "minigame:" + VolcanoMiniGame.NAMESPACE + ":" + a.id();
    }

    String objective(MiniGameArena a) {
        Round r = rounds.get(a);
        return r == null ? "Waiting for players" : r.objective;
    }

    String progress(MiniGameArena a, Player player) {
        Round r = rounds.get(a);
        if (r == null) return "";
        return "Survived: " + survivalTime(a, player) + " | Survivors: " + a.numPlayers();
    }

    String survivalTime(MiniGameArena a, Player player) {
        Round r = rounds.get(a);
        long ticks = r == null || player == null ? 0 : r.survivalTicks.getOrDefault(player.getUniqueId(), 0L);
        long seconds = ticks / 20;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void validate(@NotNull MiniGameArena a, ArenaValidationResult result) {
        VolcanoArenaSettings d = settings(a);
        MiniGameConfigSupport.validateArenaSpawns(api, a, d.spawns, 64, true, result);

        if (d.roundSeconds < 1 || d.roundSeconds > 3600 || d.countdown < 1 || d.countdown > 60) {
            result.addError("Settings: roundseconds 1-3600, countdown 1-60.", "settings");
        }
        if (d.startDelaySeconds < 0 || d.startDelaySeconds > 60) {
            result.addError("Settings: startdelay 0-60.", "settings");
        }

        if (!contained(a, d.floor)) result.addError("Select the floor inside the arena.", "floor");
        else {
            Location min = d.floor.getMinimumLocation(), max = d.floor.getMaximumLocation();
            long area = (long) (max.getBlockX() - min.getBlockX() + 1) * (max.getBlockZ() - min.getBlockZ() + 1);
            if (min.getBlockY() != max.getBlockY() || area > 4096 || area < 6)
                result.addError("Floor must be one flat layer, 6-4096 blocks.", "floor");
            else {
                for (Block b : floorBlocks(d.floor))
                    if (!b.getType().isSolid() || b.getState() instanceof org.bukkit.block.TileState) {
                        result.addError("Use solid floor blocks without containers or block entities.", "floor");
                        break;
                    }
                for (Location l : d.spawns)
                    if (l != null && !d.floor.contains(l.clone().subtract(0, 1, 0))) {
                        result.addError("Survival spawns must stand directly on the selected floor.", "spawns");
                        break;
                    }
            }
        }
    }

    private boolean outside(MiniGameArena a, Location l) {
        return a.getRegion() == null || !a.getRegion().contains(l);
    }

    private boolean contained(MiniGameArena a, SCRegion r) {
        if (r == null || a.getRegion() == null || !a.world().equals(r.getWorld())) return false;
        // Check actual geometry, including concave parent regions, without scanning an unbounded volume.
        var min = r.getMinimumLocation();
        var max = r.getMaximumLocation();
        long volume = (long) (max.getBlockX() - min.getBlockX() + 1) * (max.getBlockY() - min.getBlockY() + 1) * (max.getBlockZ() - min.getBlockZ() + 1);
        if (volume > 100000) return false;
        for (var v : r.getRegion()) if (outside(a, new Location(a.world(), v.x(), v.y(), v.z()))) return false;
        return true;
    }

    private List<Block> floorBlocks(SCRegion region) {
        List<Block> blocks = new ArrayList<>();
        for (var v : region.getRegion()) blocks.add(region.getWorld().getBlockAt(v.x(), v.y(), v.z()));
        return blocks;
    }

    private Location assignSpawn(MiniGameArena a, Round r, Player p) {
        return r.respawns.computeIfAbsent(p.getUniqueId(), ignored -> {
            VolcanoArenaSettings d = settings(a);
            if (d.spawns.isEmpty()) {
                List<Block> blocks = floorBlocks(d.floor);
                return blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()))
                        .getLocation().clone().add(0.5, 1, 0.5);
            }
            return d.spawns.stream().filter(l -> !r.respawns.containsValue(l))
                    .findFirst().orElse(d.spawns.getFirst()).clone();
        });
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena a, Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        api.messages().info(p, VolcanoMiniGame.INSTRUCTIONS);
        api.tasks().runLater(1, () -> {
            if (a.getStatus() == WAITING && a.numPlayers() >= a.getMinPlayers())
                a.setStatus(STARTING, settings(a).countdown);
        });
        Round r = rounds.get(a);
        if (a.getStatus() == PREPARATION && r != null) {
            equip(p);
            return assignSpawn(a, r, p);
        }
        return a.getLobbySpawn();
    }

    @Override
    public Location onPlayerJoinSpectator(MiniGameArena a, Player p) {
        p.setFireTicks(0);
        p.setGameMode(GameMode.SPECTATOR);
        return a.getSpectatorSpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena a, Player p) {
        p.setFireTicks(0);
        Round r = rounds.get(a);
        if (r != null) {
            r.respawns.remove(p.getUniqueId());
        }
        if ((a.getStatus() == STARTING || a.getStatus() == PREPARATION) && a.numPlayers() == 0) {
            a.setStatus(WAITING, 0);
        }
        if (a.getStatus() == RUNNING) checkSurvivors(a);
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena a, Player p) {
        onPlayerLeaveArena(a, p);
    }

    @Override
    public void onArenaUnload(MiniGameArena a) {
        cleanup(a);
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena a, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus status) {
        if (status == STARTING) {
            a.setCountdown(settings(a).countdown);
            cleanup(a);
            Round r = new Round();
            rounds.put(a, r);
            a.set("winner", "-");
            r.objective = "Get ready!";
        } else if (status == PREPARATION) {
            Round r = rounds.get(a);
            if (r == null) {
                a.setStatus(DISABLED, 0);
                return;
            }
            r.objective = "Get ready!";
            for (Player p : a.getPlayers()) {
                p.teleport(assignSpawn(a, r, p));
                p.getInventory().clear();
                equip(p);
            }
        } else if (status == RUNNING) {
            Round r = rounds.get(a);
            if (r == null) {
                a.setStatus(DISABLED, 0);
                return;
            }
            for (Player p : a.getPlayers()) {
                Location spawn = assignSpawn(a, r, p);
                r.survivalTicks.put(p.getUniqueId(), 0L);
                a.getPlayer(p).setScore(0);
                a.getPlayer(p).setKills(0);
                a.getPlayer(p).setDeaths(0);
                if (oldStatus != PREPARATION || outside(a, p.getLocation())
                        || p.getLocation().getY() < settings(a).floor.getMinimumLocation().getBlockY() - 0.5) {
                    p.teleport(spawn);
                }
                p.getInventory().clear();
            }
            r.initialPlayers = a.numPlayers();
            r.objective = "Dodge lava & holes";
            for (Player p : a.getPlayers()) equip(p);
            r.eruption = new VolcanoEruption(a.world(), a.getRegion(), floorBlocks(settings(a).floor), new Random());

            api.tasks().repeating(task(a), 2L, () -> tick(a));
        } else if (status == ENDING) {
            api.tasks().cancel(task(a));

        } else if (status == RESETTING) {
            cleanup(a);
            a.removeAllOccupants();
            a.setStatus(WAITING, 0);
        } else if (status == DISABLED || status == SHUTDOWN) {
            cleanup(a);
        } else if (status == WAITING) {
            cleanup(a);
            for (Player p : a.getPlayers()) p.teleport(a.getLobbySpawn());
        }
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena a, int seconds) {
        if ((a.getStatus() == STARTING || a.getStatus() == PREPARATION) && seconds > 0 && seconds <= 5) {
            a.showStartingCountdownTitle(seconds);
        }
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena a) {
        if (a.getStatus() == STARTING && settings(a).startDelaySeconds > 0) {
            a.setStatus(PREPARATION, settings(a).startDelaySeconds);
        } else if (a.getStatus() == STARTING || a.getStatus() == PREPARATION) {
            a.setStatus(RUNNING, settings(a).roundSeconds);
        } else if (a.getStatus() == RUNNING) finish(a, null);
        else if (a.getStatus() == ENDING) a.setStatus(RESETTING, 0);
    }

    private void cleanup(MiniGameArena a) {
        api.tasks().cancel(task(a));
        Round r = rounds.remove(a);
        if (r == null) return;

        if (r.eruption != null) r.eruption.restore();
        for (Player player : a.getOccupants()) player.setFireTicks(0);
    }

    private void equip(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFireTicks(0);
        p.setHealth(PlayerUtil.getMaxHealth(p));
        p.setFoodLevel(20);
        p.setFallDistance(0);
    }

    private void tick(MiniGameArena a) {
        Round r = rounds.get(a);
        if (r == null || a.getStatus() != RUNNING) return;
        r.tick += 2;
        if (a.numPlayers() == 0) {
            finish(a, List.of());
            return;
        }
        // Evaluate everyone before selecting a winner, including simultaneous falls.
        for (Player p : new ArrayList<>(a.getPlayers())) {
            r.survivalTicks.put(p.getUniqueId(), r.tick);
            if (r.tick % 20 == 0) a.getPlayer(p).setScore((int) (r.tick / 20));
            if (outside(a, p.getLocation())
                    || p.getLocation().getY() < settings(a).floor.getMinimumLocation().getBlockY() - 0.5) {
                a.addSpectator(p);
            }
        }
        checkSurvivors(a);
        if (a.getStatus() != RUNNING) return;

        r.eruption.tick(r.tick);
        // Flame particles mark a hot floor tile; touching them burns once per second.
        for (Player p : new ArrayList<>(a.getPlayers())) {
            Block support = p.getLocation().subtract(0, 1, 0).getBlock();
            if (r.eruption.isWarning(support) && r.tick >= r.nextFlameDamage.getOrDefault(p.getUniqueId(), 0L)) {
                r.nextFlameDamage.put(p.getUniqueId(), r.tick + 20);
                p.damage(1.0, DamageSource.builder(DamageType.IN_FIRE).build());
                if (a.getStatus() != RUNNING) return;
            }
        }
    }

    private void eliminate(MiniGameArena a, Player p) {
        Round r = rounds.get(a);
        if (r == null || !a.hasPlayer(p) || a.getStatus() != RUNNING) return;
        r.survivalTicks.put(p.getUniqueId(), r.tick);
        a.addSpectator(p);
        checkSurvivors(a);
    }

    private void checkSurvivors(MiniGameArena a) {
        Round r = rounds.get(a);
        if (r == null || a.getStatus() != RUNNING) return;
        if (a.numPlayers() == 0) finish(a, List.of());
        else if (r.initialPlayers > 1 && a.numPlayers() == 1)
            finish(a, List.of(a.getPlayers().getFirst().getUniqueId()));
    }

    private void finish(MiniGameArena a, List<UUID> explicitWinners) {
        if (a.getStatus() != RUNNING) return;
        Round r = rounds.get(a);
        List<UUID> winners = explicitWinners;
        if (winners == null) {
            winners = a.getPlayers().stream().map(Player::getUniqueId).toList();
        }
        String names = winners.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).collect(java.util.stream.Collectors.joining(", "));
        a.set("winner", names.isEmpty() ? "No winner" : names);
        r.objective = "Round over";
        announce(a, names.isEmpty() ? "Round over — no winner." : "Winner" + (winners.size() > 1 ? "s: " : ": ") + names);
        for (Player player : a.getOccupants()) {
            if (r.survivalTicks.containsKey(player.getUniqueId()))
                api.messages().info(player, "You survived " + survivalTime(a, player) + ".");
        }
        // Solo practice does not grant rewards.
        if (r.initialPlayers > 1 && !winners.isEmpty()) owner.game.rewardWinners(a, winners);
        a.setStatus(ENDING, 20);
    }

    private void announce(MiniGameArena a, String message) {
        for (Player p : a.getOccupants()) api.messages().info(p, message);
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena a, Player p, Block b) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena a, Player p, Block b) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena a, Player p, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena a, EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !a.hasPlayer(p) || a.getStatus() != RUNNING
                || e.isCancelled()) return HandlerEventResult.DENY;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            eliminate(a, p);
            return HandlerEventResult.DENY;
        }
        DamageType damageType = e.getDamageSource().getDamageType();
        if (!Set.of(DamageType.HOT_FLOOR, DamageType.IN_FIRE, DamageType.ON_FIRE,
                DamageType.LAVA, DamageType.FALL).contains(damageType)) return HandlerEventResult.DENY;
        if (e.getFinalDamage() >= p.getHealth()) {
            // End participation without sending the player through the server respawn screen.
            a.getPlayer(p).setDeaths(a.getPlayer(p).getDeaths() + 1);
            eliminate(a, p);
            p.setFireTicks(0);
            return HandlerEventResult.DENY;
        }
        return HandlerEventResult.ALLOW;
    }

    void registerListeners() {
        // Vanilla fluid updates would escape the platform or fight the animated flow levels.
        api.events().register(BlockFromToEvent.class, this::onFluidFlow, EventPriority.HIGHEST, true);
        api.events().register(FluidLevelChangeEvent.class, this::onFluidLevelChange, EventPriority.HIGHEST, true);
        api.events().register(BlockFormEvent.class, this::onLavaForm, EventPriority.HIGHEST, true);
        api.events().register(BlockIgniteEvent.class, this::onLavaIgnite, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEvent.class, this::interact);
        api.events().register(PlayerInteractEntityEvent.class, e -> {
            MiniGameArena a = owner.game.findPlayer(e.getPlayer());
            if (a != null) e.setCancelled(true);
        });
        api.events().register(PlayerSwapHandItemsEvent.class, e -> {
            if (owner.game.findPlayer(e.getPlayer()) != null) e.setCancelled(true);
        });
        api.events().register(InventoryClickEvent.class, e -> {
            if (e.getWhoClicked() instanceof Player p && owner.game.findPlayer(p) != null) e.setCancelled(true);
        });
        api.events().register(InventoryDragEvent.class, e -> {
            if (e.getWhoClicked() instanceof Player p && owner.game.findPlayer(p) != null) e.setCancelled(true);
        });
        api.events().register(EntityPickupItemEvent.class, e -> {
            if (e.getEntity() instanceof Player p && owner.game.findPlayer(p) != null) e.setCancelled(true);
        });
    }

    void onFluidFlow(BlockFromToEvent event) {
        if (managedLava(event.getBlock()) || managedLava(event.getToBlock())) event.setCancelled(true);
    }

    void onFluidLevelChange(FluidLevelChangeEvent event) {
        if (managedLava(event.getBlock())) event.setCancelled(true);
    }

    void onLavaForm(BlockFormEvent event) {
        if (managedLava(event.getBlock())) event.setCancelled(true);
    }

    void onLavaIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA && managedLava(event.getIgnitingBlock())) event.setCancelled(true);
    }

    private boolean managedLava(Block block) {
        if (block == null) return false;
        return rounds.values().stream().anyMatch(r -> r.eruption != null && r.eruption.containsLava(block));
    }

    private void interact(PlayerInteractEvent event) {
        if (owner.game.findPlayer(event.getPlayer()) != null) event.setCancelled(true);
    }

}
