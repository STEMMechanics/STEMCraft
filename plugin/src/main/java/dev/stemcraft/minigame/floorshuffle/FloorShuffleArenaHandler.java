package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

final class FloorShuffleArenaHandler implements MiniGameArenaHandler {
    private static final List<Material> COLOURS = List.of(Material.RED_CONCRETE, Material.BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE);
    private final STEMCraftAPI api;
    private final FloorShuffleMiniGame owner;
    private final Map<MiniGameArena, Round> rounds = new HashMap<>();

    static final class Round {
        final Map<UUID, Location> respawns = new HashMap<>();
        final Map<Block, BlockState> original = new LinkedHashMap<>();
        final Map<Block, Material> tiles = new LinkedHashMap<>();
        long tick;
        long nextPhase;
        int phase;
        int initialPlayers;
        boolean dropping;
        Material colour;
        String objective = "";
    }

    FloorShuffleArenaHandler(STEMCraftAPI api, FloorShuffleMiniGame owner) {
        this.api = api;
        this.owner = owner;
    }

    private FloorShuffleArenaSettings settings(MiniGameArena a) {
        return owner.settings(a);
    }

    private String task(MiniGameArena a) {
        return "minigame:" + FloorShuffleMiniGame.NAMESPACE + ":" + a.id();
    }

    String objective(MiniGameArena a) {
        Round r = rounds.get(a);
        return r == null ? "Waiting for the round to start" : r.objective;
    }

    String progress(MiniGameArena a) {
        Round r = rounds.get(a);
        if (r == null) return "";
        return "Survivors: " + a.numPlayers();
    }

    @Override
    public void validate(@NotNull MiniGameArena a, ArenaValidationResult result) {
        FloorShuffleArenaSettings d = settings(a);
        MiniGameConfigSupport.validateArenaSpawns(api, a, d.spawns, 64, result);

        if (d.roundSeconds < 1 || d.roundSeconds > 3600 || d.countdown < 1 || d.countdown > 60) {
            result.addError("Settings: roundseconds 1-3600, countdown 1-60.", "settings");
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

    @Override
    public Location onPlayerJoinArena(MiniGameArena a, Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        api.messages().info(p, FloorShuffleMiniGame.INSTRUCTIONS);
        api.tasks().runLater(1, () -> {
            if (a.getStatus() == WAITING && a.numPlayers() >= a.getMinPlayers())
                a.setStatus(STARTING, settings(a).countdown);
        });
        if (a.getStatus() == STARTING) {
            Round r = rounds.get(a);
            if (r != null) {
                return r.respawns.computeIfAbsent(p.getUniqueId(), ignored -> settings(a).spawns.stream()
                        .filter(l -> !r.respawns.containsValue(l)).findFirst().orElse(settings(a).spawns.getFirst()).clone());
            }
        }
        return a.getLobbySpawn();
    }

    @Override
    public Location onPlayerJoinSpectator(MiniGameArena a, Player p) {
        p.setGameMode(GameMode.SPECTATOR);
        return a.getSpectatorSpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena a, Player p) {
        Round r = rounds.get(a);
        if (r != null) {
            r.respawns.remove(p.getUniqueId());
        }
        if (a.getStatus() == STARTING && a.numPlayers() == 0) a.setStatus(WAITING, 0);
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
            r.objective = FloorShuffleMiniGame.INSTRUCTIONS;
            int i = 0;
            for (Player p : a.getPlayers()) {
                Location spawn = settings(a).spawns.get(i++).clone();
                r.respawns.put(p.getUniqueId(), spawn);
                a.getPlayer(p).setScore(0);
                a.getPlayer(p).setKills(0);
                a.getPlayer(p).setDeaths(0);
                p.teleport(spawn);
                p.getInventory().clear();
            }
        } else if (status == RUNNING) {
            Round r = rounds.get(a);
            if (r == null) {
                a.setStatus(DISABLED, 0);
                return;
            }
            r.initialPlayers = a.numPlayers();
            for (Player p : a.getPlayers()) equip(p);
            for (Block b : floorBlocks(settings(a).floor)) r.original.put(b, b.getState());
            shuffle(a, r);
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
        if (a.getStatus() == STARTING && seconds > 0 && seconds <= 5) a.showStartingCountdownTitle(seconds);
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena a) {
        if (a.getStatus() == STARTING) a.setStatus(RUNNING, settings(a).roundSeconds);
        else if (a.getStatus() == RUNNING) finish(a, null);
        else if (a.getStatus() == ENDING) a.setStatus(RESETTING, 0);
    }

    private void cleanup(MiniGameArena a) {
        api.tasks().cancel(task(a));
        Round r = rounds.remove(a);
        if (r == null) return;

        for (BlockState state : r.original.values()) state.update(true, false);
    }

    private void equip(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFireTicks(0);
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
            if (outside(a, p.getLocation())
                    || p.getLocation().getY() < settings(a).floor.getMinimumLocation().getBlockY() - 0.5)
                a.addSpectator(p);
        }
        checkSurvivors(a);
        if (a.getStatus() != RUNNING) return;

        if (r.tick >= r.nextPhase) {
            if (!r.dropping) {
                r.tiles.forEach((b, m) -> {
                    if (m != r.colour) b.setType(Material.AIR, false);
                });
                r.dropping = true;
                r.nextPhase = r.tick + 50;
            } else shuffle(a, r);
        }
    }

    private void shuffle(MiniGameArena a, Round r) {
        r.phase++;
        r.tiles.clear();
        List<Block> blocks = new ArrayList<>(r.original.keySet());
        Collections.shuffle(blocks);
        for (int i = 0; i < blocks.size(); i++) {
            Material colour = COLOURS.get(i % COLOURS.size());
            Block b = blocks.get(i);
            b.setType(colour, false);
            r.tiles.put(b, colour);
        }
        r.colour = COLOURS.get(ThreadLocalRandom.current().nextInt(COLOURS.size()));
        r.objective = "Stand on " + r.colour.name().replace("_CONCRETE", "") + "!";
        r.dropping = false;
        r.nextPhase = r.tick + Math.max(25, 120 - r.phase * 5);
        announce(a, r.objective);
    }

    private void eliminate(MiniGameArena a, Player p) {
        if (!a.hasPlayer(p) || a.getStatus() != RUNNING) return;
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
        r.objective = "Round complete";
        announce(a, names.isEmpty() ? "Round over — no winner." : "Winner" + (winners.size() > 1 ? "s: " : ": ") + names);
        // Solo practice does not grant rewards.
        if (r.initialPlayers > 1 && !winners.isEmpty()) owner.game.rewardWinners(a, winners);
        a.setStatus(ENDING, 8);
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
        if (e.getEntity() instanceof Player p && a.hasPlayer(p) && a.getStatus() == RUNNING) {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.LAVA) {
                Round r = rounds.get(a);
                if (r != null) eliminate(a, p);
            }
        }
        return HandlerEventResult.DENY;
    }

    void registerListeners() {
        api.events().register(PlayerMoveEvent.class, e -> {
            MiniGameArena a = owner.game.findPlayer(e.getPlayer());
            if (a != null && a.hasPlayer(e.getPlayer()) && a.getStatus() == STARTING) {
                Location to = e.getFrom().clone();
                to.setYaw(e.getTo().getYaw());
                to.setPitch(e.getTo().getPitch());
                e.setTo(to);
            }
        });
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

    private void interact(PlayerInteractEvent event) {
        if (owner.game.findPlayer(event.getPlayer()) != null) event.setCancelled(true);
    }

}
