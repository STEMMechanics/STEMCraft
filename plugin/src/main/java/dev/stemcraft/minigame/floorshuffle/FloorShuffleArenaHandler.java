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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

final class FloorShuffleArenaHandler implements MiniGameArenaHandler {
    private static final int FLASH_INTERVAL_TICKS = 6;
    private static final int FLASH_STEPS = 4;
    private static final List<Material> COLOURS = List.of(Material.RED_CONCRETE, Material.BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE);
    private final STEMCraftAPI api;
    private final FloorShuffleMiniGame owner;
    private final Map<MiniGameArena, Round> rounds = new HashMap<>();
    private final Map<MiniGameArena, Map<Block, BlockState>> lobbyFloors = new HashMap<>();

    static final class Round {
        final Map<UUID, Location> respawns = new HashMap<>();
        final Map<Block, BlockState> original = new LinkedHashMap<>();
        final Map<Block, Material> tiles = new LinkedHashMap<>();
        long tick;
        long nextPhase;
        int phase;
        int initialPlayers;
        int flashStep;
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
        MiniGameConfigSupport.validateArenaSpawns(api, a, d.spawns, 64, true, result);

        if (d.roundSeconds < 1 || d.roundSeconds > 3600 || d.countdown < 1 || d.countdown > 60) {
            result.addError("Settings: roundseconds 1-3600, countdown 1-60.", "settings");
        }
        if (d.startDelaySeconds < 0 || d.startDelaySeconds > 60) {
            result.addError("Settings: startdelay 0-60.", "settings");
        }
        if (d.lobbyShuffleSeconds < 0 || d.lobbyShuffleSeconds > 3600) {
            result.addError("Lobby shuffle interval must be 0-3600 seconds (0 disables it).", "settings");
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
            FloorShuffleArenaSettings d = settings(a);
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
        api.messages().info(p, FloorShuffleMiniGame.INSTRUCTIONS);
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
        p.setGameMode(GameMode.SPECTATOR);
        return a.getSpectatorSpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena a, Player p) {
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
            r.objective = FloorShuffleMiniGame.INSTRUCTIONS;
            startLobbyShuffle(a);
        } else if (status == PREPARATION) {
            stopLobbyShuffle(a);
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
            stopLobbyShuffle(a);
            Round r = rounds.get(a);
            if (r == null) {
                a.setStatus(DISABLED, 0);
                return;
            }
            for (Player p : a.getPlayers()) {
                Location spawn = assignSpawn(a, r, p);
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
            startLobbyShuffle(a);
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
        stopLobbyShuffle(a);
        Round r = rounds.remove(a);
        if (r == null) return;

        for (BlockState state : r.original.values()) state.update(true, false);
    }

    private void startLobbyShuffle(MiniGameArena a) {
        FloorShuffleArenaSettings d = settings(a);
        if (d.floor == null || d.lobbyShuffleSeconds <= 0) return;
        Map<Block, BlockState> original = new LinkedHashMap<>();
        for (Block block : floorBlocks(d.floor)) original.put(block, block.getState());
        lobbyFloors.put(a, original);
        randomizeFloor(original.keySet());
        api.tasks().repeating(task(a) + ":lobby", d.lobbyShuffleSeconds * 20L, () -> {
            if (lobbyFloors.get(a) == original && (a.getStatus() == WAITING || a.getStatus() == STARTING)) {
                randomizeFloor(original.keySet());
            }
        });
    }

    private void stopLobbyShuffle(MiniGameArena a) {
        api.tasks().cancel(task(a) + ":lobby");
        Map<Block, BlockState> original = lobbyFloors.remove(a);
        if (original != null) {
            for (BlockState state : original.values()) state.update(true, false);
        }
    }

    private Map<Block, Material> randomizeFloor(Collection<Block> floor) {
        List<Block> blocks = new ArrayList<>(floor);
        Collections.shuffle(blocks);
        Map<Block, Material> tiles = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            Material colour = COLOURS.get(i % COLOURS.size());
            Block block = blocks.get(i);
            block.setType(colour, false);
            tiles.put(block, colour);
        }
        return tiles;
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
            if (outside(a, p.getLocation()))
                a.addSpectator(p);
        }
        checkSurvivors(a);
        if (a.getStatus() != RUNNING) return;

        if (!r.dropping && r.flashStep < FLASH_STEPS
                && r.tick >= r.nextPhase - (long) FLASH_INTERVAL_TICKS * (FLASH_STEPS - r.flashStep)) {
            boolean flash = r.flashStep % 2 == 0;
            r.tiles.forEach((block, colour) -> {
                if (colour != r.colour) block.setType(flash ? Material.WHITE_CONCRETE : colour, false);
            });
            r.flashStep++;
        }
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
        r.tiles.putAll(randomizeFloor(r.original.keySet()));
        r.flashStep = 0;
        r.colour = COLOURS.get(ThreadLocalRandom.current().nextInt(COLOURS.size()));
        FloorShuffleTextFormat text = owner.textFormat;
        r.objective = FloorShuffleTextFormat.render(text.objective(), r.colour);
        r.dropping = false;
        r.nextPhase = r.tick + Math.max(25, 120 - r.phase * 5);
        announce(a, FloorShuffleTextFormat.render(text.chat(), r.colour));
        a.showTitle(FloorShuffleTextFormat.render(text.title(), r.colour),
                FloorShuffleTextFormat.render(text.subtitle(), r.colour),
                Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);
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
        return HandlerEventResult.DENY;
    }

    void registerListeners() {
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
