package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Session-only AFK tracking; all state and player updates run on the server thread. */
public final class Afk extends BaseFeature {
    private static final String TASK = "afk-check";
    private final Map<UUID, State> players = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Permission> permissions = new ArrayList<>();
    private final LongSupplier clock;
    private Command command;
    private boolean enabled;
    private long idleMillis;
    private long kickMillis;

    public Afk(STEMCraftAPI api) { this(api, () -> System.nanoTime() / 1_000_000); }
    Afk(STEMCraftAPI api, LongSupplier clock) { super(api); this.clock = clock; }

    @Override public void onEnable() {
        loadSettings();
        permission("stemcraft.command.afk", "Toggle your own AFK status", PermissionDefault.TRUE);
        permission("stemcraft.afk.kick-exempt", "Exempt from STEMCraft AFK kicks", PermissionDefault.FALSE);
        command = api.commands().create("afk").permission("stemcraft.command.afk")
            .executor((unused, cmd, ctx) -> {
                if (ctx.asPlayer() == null) { ctx.returnError("This command must be run in-game."); return; }
                if (!enabled) { ctx.returnError("AFK tracking is disabled."); return; }
                toggle(ctx.asPlayer());
            }).register(STEMCraft.getPlugin());
        listen(PlayerJoinEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerQuitEvent.class, event -> players.remove(event.getPlayer().getUniqueId()));
        listen(PlayerMoveEvent.class, event -> {
            if (event.hasChangedPosition() || event.hasChangedOrientation()) activity(event.getPlayer());
        });
        listen(PlayerInteractEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerInteractEntityEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerAnimationEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerItemHeldEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerDropItemEvent.class, event -> activity(event.getPlayer()));
        listen(PlayerSwapHandItemsEvent.class, event -> activity(event.getPlayer()));
        listen(InventoryClickEvent.class, event -> { if (event.getWhoClicked() instanceof Player p) activity(p); });
        listen(InventoryDragEvent.class, event -> { if (event.getWhoClicked() instanceof Player p) activity(p); });
        listen(PlayerCommandPreprocessEvent.class, event -> {
            String label = event.getMessage().split("\\s+", 2)[0];
            if (!label.equalsIgnoreCase("/afk") && !label.equalsIgnoreCase("/stemcraft:afk")) activity(event.getPlayer());
        });
        listen(AsyncChatEvent.class, event -> {
            Player player = event.getPlayer();
            api.tasks().nextTick(() -> activity(player));
        });
        Bukkit.getOnlinePlayers().forEach(this::activity);
        api.tasks().repeating(TASK, 20L, 20L, this::tick);
    }

    private <T extends Event> void listen(Class<T> type, Consumer<T> callback) {
        listeners.add(api.events().register(type, callback::accept, EventPriority.MONITOR, false));
    }

    private void permission(String name, String description, PermissionDefault value) {
        if (Bukkit.getPluginManager().getPermission(name) == null) {
            Permission permission = new Permission(name, description, value);
            Bukkit.getPluginManager().addPermission(permission);
            permissions.add(permission);
        }
    }

    private void loadSettings() {
        var config = getConfigSection();
        enabled = config.getBoolean("enabled", true);
        idleMillis = Math.max(1, config.getInt("idle-minutes", 5)) * 60_000L;
        kickMillis = Math.max(0, config.getInt("kick-after-afk-minutes", 30)) * 60_000L;
    }

    @Override public void onReload() {
        super.onReload();
        loadSettings();
        if (!enabled) clear();
    }

    @Override public void onDisable() {
        enabled = false;
        api.tasks().cancel(TASK);
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (command != null) { command.unregister(); command = null; }
        permissions.forEach(Bukkit.getPluginManager()::removePermission);
        permissions.clear();
        clear();
    }

    private void clear() {
        players.clear();
        Bukkit.getOnlinePlayers().forEach(this::refreshName);
    }

    public boolean isAfk(Player player) {
        State state = players.get(player.getUniqueId());
        return enabled && state != null && state.afk;
    }

    void activity(Player player) {
        if (!enabled || !player.isOnline()) return;
        long now = clock.getAsLong();
        State state = players.computeIfAbsent(player.getUniqueId(), id -> new State(now));
        state.lastActivity = now;
        if (state.afk) change(player, state, false, now);
    }

    void toggle(Player player) {
        long now = clock.getAsLong();
        State state = players.computeIfAbsent(player.getUniqueId(), id -> new State(now));
        state.lastActivity = now;
        change(player, state, !state.afk, now);
    }

    void tick() {
        if (!enabled) return;
        long now = clock.getAsLong();
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            State state = players.computeIfAbsent(player.getUniqueId(), id -> new State(now));
            if (!state.afk && now - state.lastActivity >= idleMillis) {
                change(player, state, true, state.lastActivity + idleMillis);
            }
            if (state.afk && kickMillis > 0 && now - state.afkSince >= kickMillis
                && now >= state.nextKick && !player.hasPermission("stemcraft.afk.kick-exempt")) {
                // A cancelled kick must not cause an attempt every second.
                state.nextKick = now + 60_000;
                player.kick(Component.text("You were kicked for being AFK for " + kickMillis / 60_000 + " minutes.",
                    NamedTextColor.YELLOW), org.bukkit.event.player.PlayerKickEvent.Cause.IDLING);
            }
        }
    }

    private void change(Player player, State state, boolean afk, long since) {
        state.afk = afk;
        state.afkSince = since;
        state.nextKick = since;
        Component message = Component.text(player.getName() + (afk ? " is now AFK" : " is no longer AFK"),
            NamedTextColor.YELLOW);
        Bukkit.getOnlinePlayers().forEach(recipient -> recipient.sendMessage(message));
        refreshName(player);
    }

    private void refreshName(Player player) {
        STEMCraft plugin = STEMCraft.getPlugin();
        PlayerTabList tab = plugin.feature(PlayerTabList.class);
        if (tab != null) tab.refreshName(player);
    }

    private static final class State {
        long lastActivity;
        boolean afk;
        long afkSince;
        long nextKick;
        State(long now) { lastActivity = now; }
    }
}
