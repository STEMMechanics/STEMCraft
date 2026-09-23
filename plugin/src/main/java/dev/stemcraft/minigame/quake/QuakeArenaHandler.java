package dev.stemcraft.minigame.quake;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

final class QuakeArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final QuakeMiniGame owner;
    private final Map<MiniGameArena, Round> rounds = new HashMap<>();

    static final class Round {
        final Map<UUID, Integer> scores = new LinkedHashMap<>();
        final Map<UUID, Location> respawns = new HashMap<>();
        final Map<UUID, Long> ready = new HashMap<>();
        final Map<UUID, Long> protectedUntil = new HashMap<>();
        long tick;
        int initialPlayers;
        String objective = "";
    }

    QuakeArenaHandler(STEMCraftAPI api, QuakeMiniGame owner) {
        this.api = api;
        this.owner = owner;
    }

    private QuakeArenaSettings settings(MiniGameArena a) {
        return owner.settings(a);
    }

    private String task(MiniGameArena a) {
        return "minigame:" + QuakeMiniGame.NAMESPACE + ":" + a.id();
    }

    String objective(MiniGameArena a) {
        Round r = rounds.get(a);
        return r == null ? "Waiting for the round to start" : r.objective;
    }

    String progress(MiniGameArena a, Player p) {
        Round r = rounds.get(a);
        if (r == null) return "";
        return "Score: " + r.scores.getOrDefault(p.getUniqueId(), 0);
    }

    @Override
    public void validate(@NotNull MiniGameArena a, ArenaValidationResult result) {
        QuakeArenaSettings d = settings(a);
        MiniGameConfigSupport.validateArenaSpawns(api, a, d.spawns, 64, result);

        if (d.roundSeconds < 1 || d.roundSeconds > 3600 || d.countdown < 1 || d.countdown > 60 || d.killTarget < 1 || d.killTarget > 1000 || d.reloadTicks < 5 || d.reloadTicks > 200) {
            result.addError("Settings: roundseconds 1-3600, countdown 1-60, killtarget 1-1000, reloadticks 5-200.", "settings");
        }
    }

    private boolean inside(MiniGameArena a, Location l) {
        return a.getRegion() != null && a.getRegion().contains(l);
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena a, Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        api.messages().info(p, QuakeMiniGame.INSTRUCTIONS);
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
            r.objective = QuakeMiniGame.INSTRUCTIONS;
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
            for (Player p : a.getPlayers()) equip(p);

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
        rounds.remove(a);
    }

    private void equip(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFireTicks(0);

        p.getInventory().setItem(0, railgun());
    }

    private ItemStack railgun() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Railgun (right-click)"));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
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
            if (!inside(a, p.getLocation())) {
                recover(p, r);
                continue;
            }

            if (a.getStatus() != RUNNING) break;
        }
    }

    private void recover(Player p, Round r) {

        Location location = r.respawns.get(p.getUniqueId());
        p.leaveVehicle();
        p.teleport(location);
        p.setFallDistance(0);
        p.setFireTicks(0);
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
                if (r != null) recover(p, r);
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
        Player p = event.getPlayer();
        MiniGameArena a = owner.game.findPlayer(p);
        if (a == null) return;
        // Restrict interactions to this game's equipment and arena controls.

        event.setCancelled(true);
        if (!a.hasPlayer(p) || a.getStatus() != RUNNING || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !event.getAction().isRightClick())
            return;
        Round r = rounds.get(a);
        if (r == null || r.tick < r.ready.getOrDefault(p.getUniqueId(), 0L)) return;

        if (p.getInventory().getItemInMainHand().getType() == Material.IRON_HOE) fire(a, p, r);
    }

    private void fire(MiniGameArena a, Player shooter, Round r) {
        if (r.tick < r.protectedUntil.getOrDefault(shooter.getUniqueId(), 0L)) return;
        r.ready.put(shooter.getUniqueId(), r.tick + settings(a).reloadTicks);
        shooter.setCooldown(Material.IRON_HOE, settings(a).reloadTicks);
        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = a.world().rayTrace(eye, direction, 80, FluidCollisionMode.NEVER, true, 0.2,
                e -> e instanceof Player p && p != shooter && a.hasPlayer(p) && r.tick >= r.protectedUntil.getOrDefault(p.getUniqueId(), 0L));
        double distance = hit == null ? 80 : hit.getHitPosition().distance(eye.toVector());
        for (double d = 0; d <= distance; d += 0.5) {
            Location l = eye.clone().add(direction.clone().multiply(d));
            if (!inside(a, l)) break;
            a.world().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
        }
        shooter.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f);
        if (hit != null && hit.getHitEntity() instanceof Player victim && inside(a, victim.getLocation())) {
            score(a, shooter);
            a.getPlayer(shooter).setKills(a.getPlayer(shooter).getKills() + 1);
            a.getPlayer(victim).setDeaths(a.getPlayer(victim).getDeaths() + 1);
            List<Location> spawns = settings(a).spawns;
            Location respawn = spawns.stream().max(Comparator.comparingDouble(l -> a.getPlayers().stream().filter(p -> p != victim)
                    .mapToDouble(p -> p.getLocation().distanceSquared(l)).min().orElse(0))).orElse(spawns.getFirst());
            victim.teleport(respawn);
            victim.setFallDistance(0);
            r.respawns.put(victim.getUniqueId(), respawn);
            r.protectedUntil.put(victim.getUniqueId(), r.tick + 30);
            announce(a, shooter.getName() + " tagged " + victim.getName());
            if (r.scores.get(shooter.getUniqueId()) >= settings(a).killTarget)
                finish(a, List.of(shooter.getUniqueId()));
        }
    }
}
