/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
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

/**
 * Feature that implements Naughty Mode functionality.
 */
public class NaughtyMode extends BaseFeature {
    private static final String PERSISTENT_TIMER_TYPE = "NAUGHTY";
    private final Set<UUID> naughtyPlayers = ConcurrentHashMap.newKeySet();
    private List<String> allowedCommands = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     */
    public NaughtyMode(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Enable the feature.
     */
    @Override
    public void onEnable() {
        reloadAllowedCommands();

        api.punishments().registerAlert("naughty", (type, player, record) -> {
            String durationString = TimeUtil.formatDuration(record.durationSeconds());

            api.messages().info(player, "NAUGHTY_SET", "reason", record.reason(), "duration", durationString);
            return true;
        });

        api.tasks().listPersistentTimers(PERSISTENT_TIMER_TYPE).forEach(data -> {
            try {
                naughtyPlayers.add(UUID.fromString(data));
            } catch(Exception ignored) {
                // ignored
            }
        });

        api.tasks().registerPersistentCallback(PERSISTENT_TIMER_TYPE, (type, id, data) -> {
            UUID uuid;

            try {
                uuid = UUID.fromString(id);
                naughtyPlayers.remove(uuid);
            } catch (IllegalArgumentException e) {
                return;
            }

            String playerName = PlayerUtil.name(uuid);
            if(playerName != null) {
                api.messages().broadcast("NAUGHTY_UNSET_ALL", "player", playerName);
            }
        });

        api.commands().create("naughty")
            .tabCompletion("{player}", "{duration}")
            .tabCompletion("{player}", "clear")
            .usage("NAUGHTY_USAGE")
            .permission("stemcraft.command.naughty")
            .description("NAUGHTY_DESCRIPTION")
            .executor((plugin, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    cmd.error(ctx.getSender(), cmd.getUsage());
                    return;
                }

                // naughty <player> <- player status
                // naughty <player> <duration> (reason) <- player naughty for this duration
                // naughty <player> clear <- player naughty cleared

                OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
                if (target == null) {
                    cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                    return;
                }

                if(ctx.args().size() >= 2) {
                    // check if the second argument is clear
                    if (ctx.getArg(1).equalsIgnoreCase("clear")) {
                        UUID uuid = target.getUniqueId();
                        if (isNaughty(uuid)) {
                            clearNaughty(uuid);
                        } else {
                            cmd.error(ctx.getSender(), "NAUGHTY_NOT_SET", "player", target.getName());
                        }

                    } else {
                        Duration duration = ctx.getArgAsDuration(1);
                        if (duration == null) {
                            cmd.error(ctx.getSender(), "INVALID_DURATION");
                        }

                        setNaughty(target.getUniqueId(), duration, ctx.asPlayer(), ctx.getArgsAsString(2));
                    }
                } else {
                    long remaining = remainingNaughty(target.getUniqueId());
                    if (remaining == 0) {
                        cmd.info(ctx.getSender(), "NAUGHTY_NOT_SET", "player", target.getName());
                    } else {
                        cmd.info(ctx.getSender(), "NAUGHTY_REMAINING", "player", target.getName(), "duration", TimeUtil.formatDuration(remaining));
                    }

                }
            })
            .register(STEMCraft.getPlugin());

        api.events().register(AsyncChatEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_CHAT");
            }
        });

        api.events().register(PlayerCommandPreprocessEvent.class, event -> {
            Player player = event.getPlayer();
            if (!isNaughty(player.getUniqueId())) return;

            String msg = event.getMessage();
            if (!msg.startsWith("/")) return;

            String[] parts = msg.substring(1).split(" ");
            if (parts.length == 0) return;

            String root = parts[0].toLowerCase(Locale.ROOT);
            if (!allowedCommands.contains(root)) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_COMMAND");
            }
        });

        api.events().register(SignChangeEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_SIGN_WRITE");
            }
        });

        api.events().register(PlayerEditBookEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_BOOK_WRITE");
            }
        });

        api.events().register(BlockBreakEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_BLOCK_BREAK");
            }
        });

        api.events().register(BlockPlaceEvent.class, event -> {
            if (isNaughty(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                api.messages().error(event.getPlayer(), "NAUGHTY_NO_BLOCK_PLACE");
            }
        });

        api.events().register(EntityDamageByEntityEvent.class, event -> {
            if (!(event.getEntity() instanceof Player)) return;

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
                api.messages().error(damagerPlayer, "NAUGHTY_NO_PVP");
            }
        });

        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            if (!isNaughty(player.getUniqueId())) return;

            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        });

        // Using blocks/items: chests, doors, buttons, eating, etc
        api.events().register(PlayerInteractEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            Action action = event.getAction();
            switch (action) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK, PHYSICAL -> {
                    event.setCancelled(true);
                    api.messages().error(event.getPlayer(), "NAUGHTY_NO_USE");
                }
                default -> {
                    // LEFT_CLICK_* still allowed so they can punch mobs/air if you want
                }
            }
        });

        // Using entities: right-click on villagers, minecarts, item frames, etc
        api.events().register(PlayerInteractEntityEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            event.setCancelled(true);
            api.messages().error(event.getPlayer(), "NAUGHTY_NO_USE");
        });

        api.events().register(PlayerInteractAtEntityEvent.class, event -> {
            if (!isNaughty(event.getPlayer().getUniqueId())) return;

            event.setCancelled(true);
            api.messages().error(event.getPlayer(), "NAUGHTY_NO_USE");
        });

        api.events().register(PlayerJoinEvent.class, event -> {
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

            String durationString = TimeUtil.formatDuration(remaining);
            api.messages().info(player, "NAUGHTY_REMAINING_SELF", "duration", durationString);
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadAllowedCommands();
    }

    @Override
    protected List<String> getConfigPathCandidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add("naughty");
        candidates.add("features.naughty");
        candidates.addAll(super.getConfigPathCandidates());
        return candidates;
    }

    private void reloadAllowedCommands() {
        allowedCommands = new ArrayList<>();
        for (String cmd : getConfigSection().getStringList("allowed-commands")) {
            allowedCommands.add(cmd.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Checks if a player is in Naughty Mode.
     *
     * @param uuid The UUID of the player.
     * @return True if the player is naughty, false otherwise.
     */
    public boolean isNaughty(UUID uuid) {
        return naughtyPlayers.contains(uuid);
    }

    /**
     * Sets or clears Naughty Mode for a player.
     *
     * @param uuid The UUID of the player.
     * @param duration The duration of Naughty Mode. If null or non-positive, Naughty Mode is cleared.
     * @param actor The player who is setting the Naughty Mode (can be null).
     * @param reason The reason for setting Naughty Mode (can be null).
     */
    private void setNaughty(UUID uuid, Duration duration, Player actor, String reason) {
        String id = uuid.toString();
        if(duration == null || duration.isNegative() || duration.isZero()) {
            api.tasks().cancel(id);
            naughtyPlayers.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);
            String playerName = PlayerUtil.name(uuid);
            if (player != null && player.isOnline()) {
                api.messages().info(player, "NAUGHTY_UNSET");
            }

            api.messages().broadcast("NAUGHTY_UNSET_ALL", player, playerName);
        } else {
            api.tasks().runLaterPersistent(PERSISTENT_TIMER_TYPE, id, "", TimeUtil.durationToRunAtMillis(duration));
            naughtyPlayers.add(uuid);

            Player player = Bukkit.getPlayer(uuid);
            String playerName = PlayerUtil.name(uuid);
            String durationString = TimeUtil.formatDuration(duration.toSeconds());

            api.messages().broadcast("NAUGHTY_SET_ALL", player, "player", playerName, "duration", durationString);
            api.punishments().record(uuid, actor, duration, "naughty", false, reason);
        }
    }

    /**
     * Clears Naughty Mode for a player.
     *
     * @param uuid The UUID of the player.
     */
    private void clearNaughty(UUID uuid) {
        setNaughty(uuid, null, null, null);
    }

    /**
     * Gets the remaining duration of Naughty Mode for a player.
     *
     * @param uuid The UUID of the player.
     * @return The remaining duration in seconds, or 0 if not naughty.
     */
    private long remainingNaughty(UUID uuid) {
        return api.tasks().remaining(uuid.toString());
    }
}
