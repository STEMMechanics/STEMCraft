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
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import dev.stemcraft.api.service.chatmenu.SCChatMenuService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
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

public final class GateKeeperServiceImpl extends BaseService implements GatekeeperService {
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

    public GateKeeperServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        plugin.saveResource(GATEKEEPER_FILENAME, false);
    }

    public void onEnable() {
        config = api.config().load(GATEKEEPER_FILENAME);

        enabled = config.getBoolean("enabled", true);

        String spawnLocationStr = config.getString("spawn");
        if(spawnLocationStr != null) {
            spawnLocation = LocationUtil.deserialize(spawnLocationStr);
            if (spawnLocation == null) {
                api.error("Invalid gatekeeper location in config: " + spawnLocationStr);
            }
        }

        if(spawnLocation == null) {
            spawnLocation = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }

        blacklist = new ArrayList<>(config.getStringList("blacklist").stream().map(UUID::fromString).toList());
        whitelist = new ArrayList<>(config.getStringList("whitelist").stream().map(UUID::fromString).toList());

        api.commands().create("invite")
                .description("Manage server invite codes")
                .usage("/invite add <code> <yyyy-MM-dd|never> | /invite remove <code> | /invite list")
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
                                ctx.returnError("Invite code '" + code + "' already exists.");
                            }

                            if (expires != null && !TimeUtil.validDate(expires)) {
                                ctx.returnError("Invalid date. Use yyyy-MM-dd or 'never'.");
                            }

                            config.set("invites." + code + ".expires", expires == null ? "never" : expires);
                            saveConfig();

                            ctx.returnInfo("Invite code added");
                        }
                        case "remove" -> {
                            ctx.checkArgsSizeAtLeast(2);
                            String code = ctx.getArg(2).toLowerCase(Locale.ROOT);

                            if (!config.contains("invites." + code)) {
                                ctx.returnError("Invite code '" + code + "' does not exist.");
                            }

                            config.set("invites." + code, null);
                            saveConfig();

                            ctx.returnInfo("Invite code removed");
                        }
                        case "list" -> {
                            ConfigSection section = config.getSection("invites");
                            if(section == null || section.getKeys(false).isEmpty()) {
                                ctx.returnInfo("No invite codes found.");
                            }

                            List<String> inviteCodes = section.getKeys(false).stream().toList();
                            int page = ctx.getArgAsInt(2, 1);

                            SCChatMenuService.render(ctx.getSenderAsPlayer(), "Invite Codes", "invite list", page, inviteCodes.size(), (start, count, isPlayer) -> {
                                List<Component> lines = new ArrayList<>();
                                for (int i = 0; i < count; i++) {
                                    String code = inviteCodes.get(i + start);
                                    String lineText = (i + 1) + ". " + code + " ";
                                    Component line = Component.text((i + 1) + ". ", NamedTextColor.WHITE)
                                            .append(Component.text(code, NamedTextColor.YELLOW))
                                            .append(Component.text(" "));

                                    if (isPlayer) {
                                        line = line.append(
                                                Component.text("[del]", NamedTextColor.RED)
                                                        .clickEvent(ClickEvent.runCommand("/invite remove " + code))
                                                        .hoverEvent(HoverEvent.showText(
                                                                Component.text(plugin.locale().get("BOOK_LIST_GET_HOVER", "name", code))
                                                        ))
                                        );
                                    }

                                    lines.add(line);
                                }

                                return lines;
                            }, "NO_INVITES_FOUND");
                        }
                        default -> {
                            ctx.returnError(cmd.getUsage());
                        }
                    }
                })
                .register(STEMCraft.getPlugin());

        // Event AsyncPlayerPreLoginEvent
        plugin.registerEvent(AsyncPlayerPreLoginEvent.class, event -> {
            UUID uuid = event.getUniqueId();

            if(!enabled) { return; }

            if(isBlacklisted(uuid)) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        MiniMessage.miniMessage().deserialize("You are blacklisted from this server.")
                );
            }
        }, EventPriority.HIGHEST, true);

        // Event PlayerJoinEvent
        plugin.registerEvent(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();

            if(!enabled) { return; }

            if(isBlacklisted(player.getUniqueId())) {
                player.kick(Component.text("You are blacklisted from this server."));
                return;
            }

            if(isWhitelisted(player)) { return; }

            event.joinMessage(null);
            gatePlayer(player);
        }, EventPriority.HIGHEST, true);

        // Event PlayerCommandPreprocessEvent
        plugin.registerEvent(PlayerCommandPreprocessEvent.class, event -> {
            Player player = event.getPlayer();

            if(!enabled) { return; }

            if (isWhitelisted(player)) return;
            event.setCancelled(true);
            plugin.messengerService().error(player, "Commands are disabled until you enter a valid invite code.");
        }, EventPriority.HIGHEST, true);

        // Event AsyncPlayerChatEvent
        plugin.registerEvent(AsyncPlayerChatEvent.class, event -> {
            Player player = event.getPlayer();

            if(!enabled) { return; }
            event.getRecipients().removeIf(p -> !isWhitelisted(p));

            if(isWhitelisted(player)) return;
            event.setCancelled(true);

            String raw = event.getMessage();
            String code = normalize(raw);

            resetInviteReminder(player, true);
            Bukkit.getScheduler().runTask(plugin, () -> checkInviteCode(player, code));
        }, EventPriority.HIGHEST, true);

        // Event PlayerMoveEvent
        plugin.registerEvent(PlayerMoveEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;

            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from);
            }
        }, EventPriority.HIGHEST, true);

        // Event EntityDamageEvent
        plugin.registerEvent(EntityDamageEvent.class, event -> {
            if(!enabled) { return; }
            if(event.getEntity() instanceof Player player && isWhitelisted(player)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event EntityDamageByEntityEvent
        plugin.registerEvent(EntityDamageByEntityEvent.class, event -> {
            if(!enabled) { return; }
            if (event.getDamager() instanceof Player player && isWhitelisted(player)) return;
            if (event.getEntity() instanceof Player p && isWhitelisted(p)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event PlayerInteractEvent
        plugin.registerEvent(PlayerInteractEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event InventoryClickEvent
        plugin.registerEvent(InventoryClickEvent.class, event -> {
            if(!enabled) { return; }
            if (event.getWhoClicked() instanceof Player player && isWhitelisted(player)) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        // Event PlayerDropItemEvent
        plugin.registerEvent(PlayerDropItemEvent.class, event -> {
            if(!enabled) { return; }
            if(isWhitelisted(event.getPlayer())) return;
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }

    public void onDisable() {
        config = null;
    }

    public boolean isBlacklisted(UUID uuid) {
        if(!enabled) { return false; }
        return blacklist.contains(uuid);
    }

    public void blacklist(UUID uuid, boolean value) {
        boolean dirty = false;

        if(value) {
            if(!blacklist.contains(uuid)) {
                blacklist.add(uuid);
                dirty = true;

                Player player = Bukkit.getPlayer(uuid);
                if(player != null && player.isOnline()) {
                    player.kick(Component.text("You have been blacklisted from this server."));
                }
            }
        } else {
            if(blacklist.remove(uuid)) {
                dirty = true;
            }
        }

        if(dirty) {
            config.set("blacklist", blacklist.stream().map(UUID::toString).toList());
            saveConfig();
        }
    }

    public void blacklist(Player player, boolean value) {
        blacklist(player.getUniqueId(), value);
    }

    public boolean isWhitelisted(UUID uuid) {
        if(!enabled) { return true; }
        return whitelist.contains(uuid);
    }

    public void whitelist(UUID uuid, boolean value) {
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
            saveConfig();
        }
    }


    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    private void saveConfig() {
        plugin.saveConfig(GATEKEEPER_FILENAME, config);
    }

    private void resetInviteReminder(Player player, boolean cancel) {
        String taskId = "gatekeeper-invite-" + player.getUniqueId();
        plugin.taskService().cancel(taskId);
        if (!cancel) {
            plugin.taskService().runOnceDelay(taskId, GATEKEEPER_REMINDER_DELAY * 20L, () -> {
                if (isWhitelisted(player)) return;

                reminders.put(player, reminders.getOrDefault(player, 0) + 1);
                if (reminders.get(player) >= GATEKEEPER_MAX_REMINDERS) {
                    player.kick(Component.text("No correct invite code was entered\n\nTry again later or contact STEMMechanics.", NamedTextColor.YELLOW));
                    return;
                }

                plugin.messengerService().info(player, "Please type your invite code in chat.");
                resetInviteReminder(player, false);
            });
        }
    }

    private void gatePlayer(Player player) {
        PlayerUtil.teleport(player, spawnLocation);
        api.players().hide(player);

        // Make them harmless
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);

        plugin.messengerService().send(player,
                "&e----------------------------------------------------\n" +
                        "Welcome to the STEMCraft server!\n" +
                        "This server is invite-only. Please type your invite\n" +
                        "code in chat to join.\n" +
                        "----------------------------------------------------");
        resetInviteReminder(player, false);
    }

    private void releasePlayer(Player player) {
        resetInviteReminder(player, true);
        api.players().show(player);

        player.setCollidable(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        String releaseLocationStr = config.getString("release.location");
        List<String> commandList = config.getStringList("release.commands");

        boolean hasLocation = false;
        if (releaseLocationStr != null) {
            Location releaseLocation = LocationUtil.deserialize(releaseLocationStr);
            if (releaseLocation != null) {
                PlayerUtil.teleport(player, releaseLocation);
                hasLocation = true;
            }
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

    private void checkInviteCode(Player player, String code) {
        if (!player.isOnline()) return;

        if (code.isEmpty()) {
            plugin.messengerService().warn(player, "Please type your invite code.");
            return;
        }

        if (!isCodeValid(code)) {
            wrongAttempt(player);
            return;
        }

        // Success: approve + release
        whitelist(player.getUniqueId(), true);
        plugin.messengerService().info(player, "Invite accepted.");
        releasePlayer(player);

        Bukkit.broadcast(Component.text(player.getName() + " joined the server.", NamedTextColor.YELLOW));
    }

    private void wrongAttempt(Player player) {
        attempts.put(player, attempts.getOrDefault(player, 0) + 1);
        reminders.put(player, 0);

        if (attempts.get(player) >= GATEKEEPER_MAX_ATTEMPTS) {
            player.kick(Component.text("Too many incorrect codes.\n\nTry again later or contact STEMMechanics.", NamedTextColor.YELLOW));
            return;
        }

        plugin.messengerService().warn(player, "Invalid or expired invite code. Please try again.");
    }

    private boolean isCodeValid(String code) {
        if (code == null || code.isBlank()) return false;

        ConfigSection invite = config.getConfigSection("invites." + code);
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
            if (today.isAfter(expiresDate)) return false;
        }

        return true;
    }

    private LocalDate parseDate(Object raw) {
        if (raw instanceof java.util.Date d) {
            return d.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        String s = raw.toString().trim();
        return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // common kid mistakes
        s = s.replace(" ", "");
        return s.toLowerCase(Locale.ROOT);
    }
}