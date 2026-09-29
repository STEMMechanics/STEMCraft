package dev.stemcraft.minigame.horserace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

final class HorseRaceArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final HorseRaceMiniGame owner;
    private final Map<MiniGameArena, Round> rounds = new HashMap<>();

    static final class Round {
        final Map<UUID, Integer> scores = new LinkedHashMap<>();
        final Map<UUID, Integer> checkpoints = new HashMap<>();
        final Map<UUID, Location> previous = new HashMap<>();
        final Map<UUID, Location> respawns = new HashMap<>();
        final Map<UUID, Entity> mounts = new HashMap<>();
        final Set<Entity> entities = new HashSet<>();
        long tick;
        int initialPlayers;
        String objective = "";
    }

    HorseRaceArenaHandler(STEMCraftAPI api, HorseRaceMiniGame owner) {
        this.api = api;
        this.owner = owner;
    }

    private HorseRaceArenaSettings settings(MiniGameArena a) {
        return owner.settings(a);
    }

    private String task(MiniGameArena a) {
        return "minigame:" + HorseRaceMiniGame.NAMESPACE + ":" + a.id();
    }

    String objective(MiniGameArena a) {
        Round r = rounds.get(a);
        return r == null ? "Waiting for the round to start" : r.objective;
    }

    String progress(MiniGameArena a, Player p) {
        Round r = rounds.get(a);
        if (r == null) return "";
        return "Gates: " + r.checkpoints.getOrDefault(p.getUniqueId(), 0) + "/" + (settings(a).laps * settings(a).checkpoints.size());
    }

    @Override
    public void validate(@NotNull MiniGameArena a, ArenaValidationResult result) {
        HorseRaceArenaSettings d = settings(a);
        MiniGameConfigSupport.validateArenaSpawns(api, a, d.spawns, 64, result);

        if (d.roundSeconds < 1 || d.roundSeconds > 3600 || d.countdown < 1 || d.countdown > 60 || d.laps < 1 || d.laps > 100) {
            result.addError("Settings: roundseconds 1-3600, countdown 1-60, laps 1-100.", "settings");
        }
        if (d.checkpoints.size() < 2)
            result.addError("Add at least two ordered checkpoint regions; the last is the finish line.", "checkpoints");
        for (SCRegion gate : d.checkpoints)
            if (!contained(a, gate)) result.addError("Every checkpoint must be inside the arena.", "checkpoints");
        for (int i = 1; i < d.checkpoints.size(); i++) {
            if (d.checkpoints.get(i) != null && d.checkpoints.get(i - 1) != null && d.checkpoints.get(i).intersects(d.checkpoints.get(i - 1)))
                result.addError("Consecutive checkpoint gates must not overlap.", "checkpoints");
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

    @Override
    public Location onPlayerJoinArena(MiniGameArena a, Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        api.messages().info(p, HorseRaceMiniGame.INSTRUCTIONS);
        api.tasks().runLater(1, () -> {
            if (a.getStatus() == WAITING && a.numPlayers() >= a.getMinPlayers())
                a.setStatus(STARTING, settings(a).countdown);
        });
        if (a.getStatus() == STARTING) {
            Round r = rounds.get(a);
            if (r != null) {
                Location spawn = r.respawns.computeIfAbsent(p.getUniqueId(), ignored -> settings(a).spawns.stream()
                        .filter(l -> !r.respawns.containsValue(l)).findFirst().orElse(settings(a).spawns.getFirst()).clone());
                r.scores.put(p.getUniqueId(), 0);
                return spawn;
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
            removeMount(r, p);
            r.scores.remove(p.getUniqueId());
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
            r.objective = HorseRaceMiniGame.INSTRUCTIONS;
            int i = 0;
            for (Player p : a.getPlayers()) {
                Location spawn = settings(a).spawns.get(i++).clone();
                r.respawns.put(p.getUniqueId(), spawn);
                r.scores.put(p.getUniqueId(), 0);
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
            for (Player p : a.getPlayers()) equip(a, p, r);

            api.tasks().repeating(task(a), 2L, () -> tick(a));
        } else if (status == ENDING) {
            api.tasks().cancel(task(a));
            Round r = rounds.get(a);
            if (r != null) {
                r.entities.forEach(Entity::remove);
                r.entities.clear();
                r.mounts.clear();
            }
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
        r.entities.forEach(Entity::remove);
    }

    private void equip(MiniGameArena a, Player p, Round r) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFireTicks(0);
        spawnMount(a, p, r);
    }

    private void spawnMount(MiniGameArena a, Player p, Round r) {
        removeMount(r, p);
        Location l = r.respawns.get(p.getUniqueId());
        Entity mount;
        Horse horse = a.world().spawn(l, Horse.class);
        horse.setTamed(true);
        horse.setOwner(p);
        horse.setAdult();
        horse.setAgeLock(true);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        var movementSpeed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) movementSpeed.setBaseValue(0.25);
        horse.setJumpStrength(0.7);
        mount = horse;
        mount.setPersistent(false);
        mount.setInvulnerable(true);
        r.mounts.put(p.getUniqueId(), mount);
        r.entities.add(mount);
        mount.addPassenger(p);
        r.previous.put(p.getUniqueId(), l.clone());
    }

    private void removeMount(Round r, Player p) {
        Entity mount = r.mounts.remove(p.getUniqueId());
        if (mount != null) {
            r.entities.remove(mount);
            mount.remove();
        }
    }

    private void tick(MiniGameArena a) {
        Round r = rounds.get(a);
        if (r == null || a.getStatus() != RUNNING) return;
        r.tick += 2;
        if (a.numPlayers() == 0) {
            finish(a, List.of());
            return;
        }
        for (Player p : new ArrayList<>(a.getPlayers())) {
            if (outside(a, p.getLocation())) {
                recover(a, p, r);
                continue;
            }
            raceTick(a, p, r);
            if (a.getStatus() != RUNNING) break;
        }
        if (a.getStatus() != RUNNING) return;

        r.entities.removeIf(e -> !e.isValid());
    }

    private void recover(MiniGameArena a, Player p, Round r) {

        Location location = r.respawns.get(p.getUniqueId());
        p.leaveVehicle();
        p.teleport(location);
        p.setFallDistance(0);
        p.setFireTicks(0);
        spawnMount(a, p, r);
    }

    private void raceTick(MiniGameArena a, Player p, Round r) {
        Entity mount = r.mounts.get(p.getUniqueId());
        if (mount == null || !mount.isValid() || p.getVehicle() != mount) {
            recover(a, p, r);
            return;
        }

        Location now = mount.getLocation();
        Location before = r.previous.put(p.getUniqueId(), now.clone());
        int progress = r.checkpoints.getOrDefault(p.getUniqueId(), 0);
        List<SCRegion> gates = settings(a).checkpoints;
        SCRegion gate = gates.get(progress % gates.size());
        if (before != null && !gate.contains(before) && gate.intersectsPath(before, now)) {
            progress++;
            r.checkpoints.put(p.getUniqueId(), progress);
            score(a, p);
            r.respawns.put(p.getUniqueId(), now.clone());
            p.playSound(now, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
            if (progress >= gates.size() * settings(a).laps) finish(a, List.of(p.getUniqueId()));
        }
    }

    private void checkSurvivors(MiniGameArena a) {
        Round r = rounds.get(a);
        if (r == null || a.getStatus() != RUNNING) return;
        if (a.numPlayers() == 0) finish(a, List.of());
    }

    private void score(MiniGameArena a, Player p) {
        Round r = rounds.get(a);
        int score = r.scores.merge(p.getUniqueId(), 1, Integer::sum);
        if (a.getPlayer(p) != null) a.getPlayer(p).setScore(score);
    }

    private void finish(MiniGameArena a, List<UUID> explicitWinners) {
        if (a.getStatus() != RUNNING) return;
        Round r = rounds.get(a);
        List<UUID> winners = explicitWinners;
        if (winners == null) {
            int best = a.getPlayers().stream().mapToInt(p -> r.scores.getOrDefault(p.getUniqueId(), 0)).max().orElse(0);
            winners = best == 0 ? List.of() : a.getPlayers().stream().map(Player::getUniqueId).filter(id -> r.scores.getOrDefault(id, 0) == best).toList();
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
                if (r != null) recover(a, p, r);
            }
        }
        return HandlerEventResult.DENY;
    }

    void registerListeners() {
        api.events().register(EntityDamageEvent.class, e -> {
            if (owned(e.getEntity())) e.setCancelled(true);
        });
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
        api.events().register(VehicleDamageEvent.class, e -> {
            if (owned(e.getVehicle())) e.setCancelled(true);
        });
        api.events().register(VehicleEnterEvent.class, e -> {
            for (Round r : rounds.values())
                if (r.entities.contains(e.getVehicle()) && r.mounts.get(e.getEntered().getUniqueId()) != e.getVehicle())
                    e.setCancelled(true);
        });
    }

    private boolean owned(Entity entity) {
        return rounds.values().stream().anyMatch(r -> r.entities.contains(entity));
    }

    private void interact(PlayerInteractEvent event) {
        if (owner.game.findPlayer(event.getPlayer()) != null) event.setCancelled(true);
    }

}
