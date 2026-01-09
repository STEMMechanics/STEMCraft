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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.gatekeeper.GatekeeperService;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the GatekeeperService for managing player access via invite codes.
 */
public final class GatekeeperServiceImpl extends BaseService implements GatekeeperService {
    private static final String GATEKEEPER_FILENAME = "gatekeeper.yml";
    private static final long GATEKEEPER_REMINDER_DELAY = 25;
    private static final int GATEKEEPER_MAX_ATTEMPTS = 5;
    private static final int GATEKEEPER_MAX_REMINDERS = 10;

    private boolean enabled;
    private ConfigFile config;
    private Location spawnLocation;

    private List<UUID> blacklist = new ArrayList<>();
    private List<UUID> whitelist = new ArrayList<>();

    private final Map<Player, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<Player, Integer> reminders = new ConcurrentHashMap<>();

    /**
     * Constructor for GateKeeperServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public GatekeeperServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        plugin.saveResource(GATEKEEPER_FILENAME, false);
    }

    /**
     * Initializes the gatekeeper service.
     */
    public void onEnable() {
        config = api.config().load(GATEKEEPER_FILENAME);
        if(config == null) {
            api.messages().error("Failed to load gatekeeper config.");
            return;
        }

        enabled = config.getBoolean("enabled", true);

        String spawnLocationStr = config.getString("spawn");
        spawnLocation = LocationUtil.deserialize(spawnLocationStr);
        if (spawnLocation == null) {
            api.messages().error("Invalid gatekeeper location in config: " + spawnLocationStr);
        }

        if(spawnLocation == null) {
            spawnLocation = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }

        blacklist = new ArrayList<>(config.getStringList("blacklist").stream().map(UUID::fromString).toList());
        whitelist = new ArrayList<>(config.getStringList("whitelist").stream().map(UUID::fromString).toList());

        api.commands().create("invite")
                .description("INVITE_DESCRIPTION")
                .usage("INVITE_USAGE")
                .tabCompletion("add", "", "never")
                .tabCompletion("remove", "{invite-codes}")
                .tabCompletion("list")
                .executor((unused, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);
                    String sub = ctx.getArg(1).toLowerCase(Locale.ROOT);

                    switch(sub) {
                        case "add" -> {
                            ctx.checkArgsSizeAtLeast(2);

                            String code = ctx.getArg(2).toLowerCase(Locale.ROOT);
                            String expires = ctx.getArg(3, "never").toLowerCase(Locale.ROOT);

                            if (expires.equals("never") || expires.isBlank()) {
                                expires = null;
                            }

                            if (config.contains("invites." + code)) {
                                ctx.returnError("INVITE_CODE_EXISTS", "code", code);
                            }

                            if (expires != null && !TimeUtil.validDate(expires)) {
                                ctx.returnError("INVITE_DATE_INVALID");
                            }

                            config.set("invites." + code + ".expires", expires == null ? "never" : expires);
                            config.save();

                            ctx.returnInfo("INVITE_CODE_ADDED");
                        }
                        case "remove" -> {
                            ctx.checkArgsSizeAtLeast(2);
                            String code = ctx.getArg(2).toLowerCase(Locale.ROOT);

                            if (!config.contains("invites." + code)) {
                                ctx.returnError("INVITE_CODE_NOT_FOUND", "code", code);
                            }

                            config.set("invites." + code, null);
                            config.save();

                            ctx.returnInfo("INVITE_CODE_REMOVED");
                        }
                        case "list" -> {
                            ConfigSection section = config.getSection("invites");
                            if(section == null || section.getKeys(false).isEmpty()) {
                                ctx.returnInfo("INVITE_CODE_NONE");
                            }

                            List<String> inviteCodes = section.getKeys(false).stream().toList();
                            int page = ctx.getArgAsInt(2, 1);

                            ChatMenuUtil.render(ctx.getSenderAsPlayer(), api.locales().resolve(ctx.getSenderAsPlayer(), "INVITE_LIST_TITLE"), "invite list", page, inviteCodes.size(), (start, count, isPlayer) -> {
                                List<Component> lines = new ArrayList<>();
                                for (int i = 0; i < count; i++) {
                                    String code = inviteCodes.get(i + start);
                                    Component line = Component.text((i + 1) + ". ", NamedTextColor.WHITE)
                                            .append(Component.text(code, NamedTextColor.YELLOW))
                                            .append(Component.text(" "));

                                    if (isPlayer) {
                                        line = line.append(
                                                Component.text("[del]", NamedTextColor.RED)
                                                        .clickEvent(ClickEvent.runCommand("/invite remove " + code))
                                                        .hoverEvent(HoverEvent.showText(
                                                                Component.text(
                                                                        PlaceholderUtil.apply(
                                                                            api.locales().resolve("BOOK_LIST_GET_HOVER"),
                                                                                "name", code)
                                                                )
                                                        ))
                                        );
                                    }

                                    lines.add(line);
                                }

                                return lines;
                            }, "INVITE_CODE_NONE");
                        }
                        default -> ctx.returnUsage();
                    }
                })
                .register(STEMCraft.getPlugin());

        // Event AsyncPlayerPreLoginEvent
        api.events().register(AsyncPlayerPreLoginEvent.class, event -> {
            UUID uuid = event.getUniqueId();

            if(!enabled) { return; }

            if(isBlacklisted(uuid)) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        MiniMessage.miniMessage().deserialize(api.messages().text(null, "GATEKEEPER_BLACKLISTED"))
                );
            }
        }, EventPriority.HIGHEST, true);

        // Event PlayerJoinEvent
        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();

            if(!enabled) { return; }

            if(isBlacklisted(player.getUniqueId())) {
                player.kick(MiniMessage.miniMessage().deserialize(api.messages().text(player, "GATEKEEPER_BLACKLISTED")));
                return;
            }

            if(isWhitelisted(player)) { return; }

            event.joinMessage(null);
            gatePlayer(player);
        }, EventPriority.HIGHEST, true);

        // Event PlayerCommandPreprocessEvent
        api.events().register(PlayerCommandPreprocessEvent.class, event -> {
            Player player = event.getPlayer();

            if(!enabled) { return; }

            if (isWhitelisted(player)) return;
            event.setCancelled(true);
            api.messages().error(player, "GATEKEEPER_COMMANDS_DISABLED");
        }, EventPriority.HIGHEST, true);

        // Event AsyncPlayerChatEvent
        api.events().register(AsyncChatEvent.class, event -> {
            if (!enabled) return;

            Player player = event.getPlayer();

            // Filter recipients
            event.viewers().removeIf(audience ->
                    !(audience instanceof Player p) || !isWhitelisted(p)
            );

            if (isWhitelisted(player)) return;

            event.setCancelled(true);

            // Extract plain text from Component
            String raw = PlainTextComponentSerializer.plainText()
                    .serialize(event.message());

            String code = normalize(raw);

            resetInviteReminder(player, true);
            api.tasks().nextTick(() -> checkInviteCode(player, code));
        }, EventPriority.HIGHEST, true);

        // Event PlayerMoveEvent
        api.events().register(PlayerMoveEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;

            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from);
            }
        }, EventPriority.HIGHEST, true);

        // Event EntityDamageEvent
        api.events().register(EntityDamageEvent.class, event -> {
            if(!enabled) { return; }
            if(event.getEntity() instanceof Player player && isWhitelisted(player)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event EntityDamageByEntityEvent
        api.events().register(EntityDamageByEntityEvent.class, event -> {
            if(!enabled) { return; }
            if (event.getDamager() instanceof Player player && isWhitelisted(player)) return;
            if (event.getEntity() instanceof Player p && isWhitelisted(p)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event PlayerInteractEvent
        api.events().register(PlayerInteractEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event InventoryClickEvent
        api.events().register(InventoryClickEvent.class, event -> {
            if(!enabled) { return; }
            if (event.getWhoClicked() instanceof Player player && isWhitelisted(player)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event PlayerDropItemEvent
        api.events().register(PlayerDropItemEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }

    /**
     * Disables the gatekeeper service.
     */
    public void onDisable() {
        config = null;
    }

    /**
     * Check if a UUID is blacklisted.
     *
     * @param uuid The UUID to check.
     * @return True if blacklisted, false otherwise.
     */
    public boolean isBlacklisted(@NotNull UUID uuid) {
        if(!enabled) { return false; }
        return blacklist.contains(uuid);
    }

    /**
     * Blacklist or unblacklist a UUID.
     *
     * @param uuid The UUID to modify.
     * @param value True to blacklist, false to unblacklist.
     */
    public void blacklist(@NotNull UUID uuid, boolean value) {
        boolean dirty = false;

        if(value) {
            if(!blacklist.contains(uuid)) {
                blacklist.add(uuid);
                dirty = true;

                Player player = Bukkit.getPlayer(uuid);
                if(player != null && player.isOnline()) {
                    player.kick(MiniMessage.miniMessage().deserialize(api.messages().text(player, "GATEKEEPER_BLACKLISTED")));
                }
            }
        } else {
            if(blacklist.remove(uuid)) {
                dirty = true;
            }
        }

        if(dirty) {
            config.set("blacklist", blacklist.stream().map(UUID::toString).toList());
            config.save();
        }
    }

    public void blacklist(@NotNull Player player, boolean value) {
        blacklist(player.getUniqueId(), value);
    }

    /**
     * Check if a UUID is whitelisted.
     *
     * @param uuid The UUID to check.
     * @return True if whitelisted, false otherwise.
     */
    public boolean isWhitelisted(@NotNull UUID uuid) {
        if(!enabled) { return true; }
        return whitelist.contains(uuid);
    }

    /**
     * Approve or unapprove a UUID.
     *
     * @param uuid The UUID to modify.
     * @param value True to approve, false to unapprove.
     */
    public void whitelist(@NotNull UUID uuid, boolean value) {
        boolean dirty = false;

        if(value) {
            if(!whitelist.contains(uuid)) {
                whitelist.add(uuid);
                dirty = true;
            }
        } else {
            if(whitelist.remove(uuid)) {
                dirty = true;
            }
        }

        if(dirty) {
            config.set("whitelist", whitelist.stream().map(UUID::toString).toList());
            config.save();
        }
    }

    /**
     * Reset or cancel the invite reminder for a player.
     *
     * @param player The player to reset the reminder for.
     * @param cancel True to cancel the reminder, false to reset and start it.
     */
    private void resetInviteReminder(Player player, boolean cancel) {
        String taskId = "gatekeeper-invite-" + player.getUniqueId();
        api.tasks().cancel(taskId);
        if (!cancel) {
            api.tasks().runOnceDelay(taskId, GATEKEEPER_REMINDER_DELAY * 20L, () -> {
                if (isWhitelisted(player)) return;

                reminders.put(player, reminders.getOrDefault(player, 0) + 1);
                if (reminders.get(player) >= GATEKEEPER_MAX_REMINDERS) {
                    player.kick(MiniMessage.miniMessage().deserialize(api.messages().text(player, "GATEKEEPER_TOO_MANY_REMINDERS")));
                    return;
                }

                api.messages().info(player, "GATEKEEPER_INVITE_PROMPT_CHAT");
                resetInviteReminder(player, false);
            });
        }
    }

    /**
     * Gate a player to the server.
     *
     * @param player The player to gate.
     */
    private void gatePlayer(Player player) {
        PlayerUtil.teleport(player, spawnLocation);
        api.players().hide(player);

        // Make them harmless
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);

        api.messages().send(player, "GATEKEEPER_WELCOME");
        resetInviteReminder(player, false);
    }

    /**
     * Release a player from the gatekeeper.
     *
     * @param player The player to release.
     */
    private void releasePlayer(Player player) {
        resetInviteReminder(player, true);
        api.players().show(player);

        player.setCollidable(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        String releaseLocationStr = config.getString("release.location");
        List<String> commandList = config.getStringList("release.commands");

        boolean hasLocation = false;
        Location releaseLocation = LocationUtil.deserialize(releaseLocationStr);
        if (releaseLocation != null) {
            PlayerUtil.teleport(player, releaseLocation);
            hasLocation = true;
        }

        for (String cmd : commandList) {
            CommandSender sender = Bukkit.getConsoleSender();
            String cmdLower = cmd.toLowerCase(Locale.ROOT);

            if (cmdLower.startsWith("player:")) {
                cmd = cmd.substring("player:".length()).trim();
                sender = player;
            }

            Bukkit.dispatchCommand(sender, cmd.replace("{player}", player.getName()));
        }

        if (!hasLocation && commandList.isEmpty()) {
            PlayerUtil.teleport(player, Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
    }

    /**
     * Check an invite code for a player.
     *
     * @param player The player to check the code for.
     * @param code The invite code to check.
     */
    private void checkInviteCode(Player player, String code) {
        if (!player.isOnline()) return;

        if (code.isEmpty()) {
            api.messages().warn(player, "GATEKEEPER_INVITE_PROMPT");
            return;
        }

        if (!isCodeValid(code)) {
            wrongAttempt(player);
            return;
        }

        // Success: approve + release
        whitelist(player.getUniqueId(), true);
        api.messages().info(player, "GATEKEEPER_INVITE_ACCEPTED");
        releasePlayer(player);

        api.messages().broadcast("GATEKEEPER_JOIN_BROADCAST", "player", player.getName());
    }

    /**
     * Handle a wrong invite code attempt.
     *
     * @param player The player who made the attempt.
     */
    private void wrongAttempt(Player player) {
        attempts.put(player, attempts.getOrDefault(player, 0) + 1);
        reminders.put(player, 0);

        if (attempts.get(player) >= GATEKEEPER_MAX_ATTEMPTS) {
            player.kick(MiniMessage.miniMessage().deserialize(api.messages().text(player, "GATEKEEPER_TOO_MANY_ATTEMPTS")));
            return;
        }

        api.messages().warn(player, "GATEKEEPER_INVITE_INVALID");
    }

    /**
     * Check if an invite code is valid.
     *
     * @param code The invite code to check.
     * @return True if valid, false otherwise.
     */
    private boolean isCodeValid(String code) {
        if (code == null || code.isBlank()) return false;

        ConfigSection invite = config.getSection("invites." + code);
        if (invite == null) return false;

        LocalDate today = LocalDate.now();

        // starts
        Object startsRaw = invite.get("starts");
        if (startsRaw != null) {
            LocalDate startsDate = parseDate(startsRaw);
            if (today.isBefore(startsDate)) return false;
        }

        // expires
        Object expiresRaw = invite.get("expires");
        if (expiresRaw != null) {
            if (expiresRaw instanceof String s && s.trim().equalsIgnoreCase("never")) {
                return true;
            }

            LocalDate expiresDate = parseDate(expiresRaw);
            return !today.isAfter(expiresDate);
        }

        return true;
    }

    /**
     * Parse a date from an object.
     *
     * @param raw The raw date object.
     * @return The parsed LocalDate.
     */
    private LocalDate parseDate(Object raw) {
        if (raw instanceof java.util.Date d) {
            return d.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        String s = raw.toString().trim();
        return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Normalize a raw invite code string.
     *
     * @param raw The raw invite code.
     * @return The normalized invite code.
     */
    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // common kid mistakes
        s = s.replace(" ", "");
        return s.toLowerCase(Locale.ROOT);
    }
}
