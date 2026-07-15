package dev.stemcraft.service.firstjoin;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public final class FirstJoinListener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final STEMCraft plugin;
    private final STEMCraftAPI api;
    private final FirstJoinService service;

    public FirstJoinListener(STEMCraft plugin, STEMCraftAPI api, FirstJoinService service) {
        this.plugin = plugin;
        this.api = api;
        this.service = service;
    }

    public void register() {
        api.events().register(PlayerJoinEvent.class, event -> service.handleJoin(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class, event -> service.removeSession(event.getPlayer().getUniqueId()));
        api.events().register(PlayerKickEvent.class, event -> service.removeSession(event.getPlayer().getUniqueId()));

        api.events().register(AsyncChatEvent.class, event -> {
            Player player = event.getPlayer();
            if (!service.hasActiveSession(player.getUniqueId())) {
                return;
            }

            event.setCancelled(true);
            String input = PLAIN.serialize(event.message()).trim();
            player.getScheduler().run(plugin, task -> service.processChatResponse(player, input), () -> service.removeSession(player.getUniqueId()));
        }, EventPriority.LOWEST, false);

        api.events().register(PlayerMoveEvent.class, service::handleMove, EventPriority.HIGHEST, false);
        api.events().register(PlayerCommandPreprocessEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(BlockBreakEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(BlockPlaceEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(PlayerInteractEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(PlayerInteractEntityEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(PlayerInteractAtEntityEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(PlayerEditBookEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(InventoryOpenEvent.class, event -> {
            if (event.getPlayer() instanceof Player player) {
                service.cancelIfActive(player, event);
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                service.cancelIfActive(player, event);
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(InventoryDragEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                service.cancelIfActive(player, event);
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(PlayerDropItemEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                service.cancelIfActive(player, event);
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(EntityDamageEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                service.cancelIfActive(player, event);
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(EntityDamageByEntityEvent.class, event -> {
            if (event.getDamager() instanceof Player player) {
                service.cancelIfActive(player, event);
            } else {
                Player shooter = service.resolvePlayerDamager(event);
                if (shooter != null) {
                    service.cancelIfActive(shooter, event);
                }
            }
        }, EventPriority.HIGHEST, false);
        api.events().register(PlayerPortalEvent.class, event -> service.cancelIfActive(event.getPlayer(), event), EventPriority.HIGHEST, false);
        api.events().register(org.bukkit.event.player.PlayerTeleportEvent.class, event -> service.handleTeleport(event.getPlayer(), event.getTo()), EventPriority.MONITOR, false);
    }
}
