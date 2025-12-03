package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
import dev.stemcraft.api.utils.SCTime;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NaughtyMode implements STEMCraftFeature {
    STEMCraft plugin;
    STEMCraftAPI api;
    private static final String PERSISTENT_TIMER_TYPE = "NAUGHTY";
    private final Set<UUID> naughtyPlayers = ConcurrentHashMap.newKeySet();
    private List<String> allowedCommands = new ArrayList<>();


    public void onEnable(STEMCraftAPI api) {
        plugin = STEMCraft.getInstance();
        this.api = api;

        allowedCommands = new ArrayList<>();
        for (String cmd : api.config().getStringList(getConfigBase("allowed-commands"))) {
            allowedCommands.add(cmd.toLowerCase(Locale.ROOT));
        }

        api.punishment().registerAlert("naughty", (type, player, record) -> {
            String durationString = SCTime.formatDuration(record.durationSeconds());

            api.info(player, "NAUGHTY_SET", "reason", record.reason(), "duration", durationString);
            return true;
        });

        api.persistentTimer().list(PERSISTENT_TIMER_TYPE).forEach(data -> {
            try {
                naughtyPlayers.add(UUID.fromString(data));
            } catch(Exception ignored) {
                // ignored
            }
        });

        api.persistentTimer().registerType(PERSISTENT_TIMER_TYPE, (type, id, data) -> {
            UUID uuid;

            try {
                uuid = UUID.fromString(id);
                naughtyPlayers.remove(uuid);
            } catch (IllegalArgumentException e) {
                return;
            }

            String playerName = SCPlayer.name(uuid);
            if(playerName != null) {
                api.broadcast("NAUGHTY_UNSET_ALL", "player", playerName);
            }
        });

        api.registerCommand("naughty")
                .addTabCompletion("{player}", "{duration}")
                .addTabCompletion("{player}", "clear")
                .setUsage("naughty <player> <duration|clear> (reason)")
                .setPermission("stemcraft.command.naughty")
                .setDescription("NAUGHTY_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    // naughty <player> <- player status
                    // naughty <player> <duration> (reason) <- player naughty for this duration
                    // naughty <player> clear <- player naughty cleared

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(1);
                    if (target == null) {
                        cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    if(ctx.args().size() >= 2) {
                        // check if index 2 is clear
                        if (ctx.getArg(2).equalsIgnoreCase("clear")) {
                            UUID uuid = target.getUniqueId();
                            if (isNaughty(uuid)) {
                                clearNaughty(uuid);
                            } else {
                                cmd.error(ctx.getSender(), "NAUGHTY_NOT_SET", "player", target.getName());
                            }

                        } else {
                            Duration duration = ctx.getArgAsDuration(2);
                            if (duration == null) {
                                cmd.error(ctx.getSender(), "INVALID_DURATION");
                            }

                            setNaughty(target.getUniqueId(), duration, ctx.getSenderAsPlayer(), ctx.getArgsAsString(3));
                        }
                    } else {
                        long remaining = remainingNaughty(target.getUniqueId());
                        if (remaining == 0) {
                            cmd.info(ctx.getSender(), "NAUGHTY_NOT_SET", "player", target.getName());
                        } else {
                            cmd.info(ctx.getSender(), "NAUGHTY_REMAINING", "player", target.getName(), "duration", SCTime.formatDuration(remaining));
                        }

                    }
                })
                .register(STEMCraft.getInstance());

        api.registerEvent(AsyncPlayerChatEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_CHAT");
            }
        });


        api.registerEvent(PlayerCommandPreprocessEvent.class, event -> {
            Player player = event.getPlayer();
            if (!isNaughty(player.getUniqueId())) return;

            String msg = event.getMessage();
            if (!msg.startsWith("/")) return;

            String[] parts = msg.substring(1).split(" ");
            if (parts.length == 0) return;

            String root = parts[0].toLowerCase(Locale.ROOT);
            if (!allowedCommands.contains(root)) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_COMMAND");
            }
        });

        api.registerEvent(SignChangeEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_SIGN_WRITE");
            }
        });

        api.registerEvent(PlayerEditBookEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_BOOK_WRITE");
            }
        });

        api.registerEvent(BlockBreakEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_BLOCK_BREAK");
            }
        });

        api.registerEvent(BlockPlaceEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.error(event.getPlayer(), "NAUGHTY_NO_BLOCK_PLACE");
            }
        });

        api.registerEvent(EntityDamageByEntityEvent.class, event -> {
            if (!(event.getEntity() instanceof Player victim)) return;

            Entity damager = event.getDamager();
            Player damagerPlayer = null;

            if (damager instanceof Player p) {
                damagerPlayer = p;
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                damagerPlayer = p;
            }

            // Naughty player trying to damage a player
            if (damagerPlayer != null && isNaughty(damagerPlayer.getUniqueId())) {
                event.setCancelled(true);
                api.error(damagerPlayer, "NAUGHTY_NO_PVP");
            }
        });

        api.registerEvent(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            if (!isNaughty(player.getUniqueId())) return;

            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        });

        // Using blocks/items: chests, doors, buttons, eating, etc
        api.registerEvent(PlayerInteractEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            Action action = event.getAction();
            switch (action) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK, PHYSICAL -> {
                    event.setCancelled(true);
                    api.error(event.getPlayer(), "NAUGHTY_NO_USE");
                }
                default -> {
                    // LEFT_CLICK_* still allowed so they can punch mobs/air if you want
                }
            }
        });

        // Using entities: right-click on villagers, minecarts, item frames, etc
        api.registerEvent(PlayerInteractEntityEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            event.setCancelled(true);
            api.error(event.getPlayer(), "NAUGHTY_NO_USE");
        });

        api.registerEvent(PlayerInteractAtEntityEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            event.setCancelled(true);
            api.error(event.getPlayer(), "NAUGHTY_NO_USE");
        });

        api.registerEvent(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!isNaughty(uuid)) {
                return;
            }

            long remaining = remainingNaughty(uuid);

            // If the timer has expired but the flag is still set, clean it up
            if (remaining <= 0) {
                clearNaughty(uuid);
                return;
            }

            String durationString = SCTime.formatDuration(remaining);
            api.info(player, "NAUGHTY_REMAINING_SELF", "duration", durationString);
        });
    }

    public boolean isNaughty(UUID uuid) {
        return naughtyPlayers.contains(uuid);
    }

    private void setNaughty(UUID uuid, Duration duration, Player actor, String reason) {
        String id = uuid.toString();
        if(duration == null || duration.isNegative() || duration.isZero()) {
            api.persistentTimer().cancel(PERSISTENT_TIMER_TYPE, id);
            naughtyPlayers.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);
            String playerName = SCPlayer.name(uuid);
            if (player != null && player.isOnline()) {
                api.info(player, "NAUGHTY_UNSET");
            }

            api.broadcast("NAUGHTY_UNSET_ALL", player, playerName);
        } else {
            api.persistentTimer().schedule(PERSISTENT_TIMER_TYPE, id, null, duration);
            naughtyPlayers.add(uuid);

            Player player = Bukkit.getPlayer(uuid);
            String playerName = SCPlayer.name(uuid);
            String durationString = SCTime.formatDuration(duration.toSeconds());

            api.broadcast("NAUGHTY_SET_ALL", player, "player", playerName, "duration", durationString);
            api.punishment().record(uuid, actor, duration, "naughty", false, reason);
        }
    }

    private void clearNaughty(UUID uuid) {
        setNaughty(uuid, null, null, null);
    }

    private long remainingNaughty(UUID uuid) {
        return api.persistentTimer().remaining(PERSISTENT_TIMER_TYPE, uuid.toString());
    }
}
