package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerGameMessages implements STEMCraftFeature {
    STEMCraftAPI api;

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random rng = new Random();

    private boolean joinEnabled;
    private boolean quitEnabled;
    private boolean deathEnabled;

    private final Map<UUID, Long> kickedUntil = new ConcurrentHashMap<>();

    private List<String> joinList = List.of();
    private List<String> quitList = List.of();

    private List<String> deathDefault = List.of();
    private final EnumMap<EntityDamageEvent.DamageCause, List<String>> deathByCause = new EnumMap<>(EntityDamageEvent.DamageCause.class);
    private List<String> deathByEntity = List.of();

    @Override
    public void onEnable(STEMCraftAPI api) {
        this.api = api;
        String base = getConfigBase(); // e.g. features.rebalance_iron_golem

        joinEnabled = api.config().getBoolean(base + ".join.enabled", true);
        quitEnabled = api.config().getBoolean(base + ".quit.enabled", true);
        deathEnabled = api.config().getBoolean(base + ".death.enabled", true);

        joinList = readList(base + ".join.list", null);
        quitList = readList(base + ".quit.list", null);

        deathDefault = readList(base + ".death.list", null);
        deathByEntity = readList(base + ".death.list_entity", null);

        deathByCause.clear();
        ConfigurationSection byCause = api.config().getConfigurationSection(base + ".death.list_cause");
        if (byCause != null) {
            for (String key : byCause.getKeys(false)) {
                EntityDamageEvent.DamageCause cause = parseCause(key);
                if (cause == null) continue;
                List<String> list = byCause.getStringList(key);
                if (list != null && !list.isEmpty()) deathByCause.put(cause, List.copyOf(list));
            }
        }

        api.registerEvent(PlayerKickEvent.class, event -> {
            kickedUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 10_000L);
        });

        api.registerEvent(PlayerJoinEvent.class,event -> {
            if (!joinEnabled) return;
            if (!SCPlayer.isWhitelisted(event.getPlayer())) return;

            String raw = pick(joinList);
            if (raw == null) return;

            event.joinMessage(render("<yellow>" + raw, event.getPlayer(), null, null));
        });


        api.registerEvent(PlayerQuitEvent.class, event -> {
            if (!quitEnabled) return;
            if (!SCPlayer.isWhitelisted(event.getPlayer())) return;

            String raw = pick(quitList);
            if (raw == null) return;

            event.quitMessage(render("<yellow>" + raw, event.getPlayer(), null, null));
        });

        api.registerEvent(PlayerDeathEvent.class, event -> {
            if (!deathEnabled) return;

            Player p = event.getEntity();
            String killer = null;

            Player pk = p.getKiller();
            if (pk != null) killer = pk.getName();

            EntityDamageEvent last = p.getLastDamageCause();
            EntityDamageEvent.DamageCause cause = (last != null ? last.getCause() : null);

            String causeText = (cause != null ? cause.name().toLowerCase(Locale.ROOT) : "unknown");

            List<String> pool = null;

            // If entity caused damage, prefer by_entity messages
            if (last instanceof EntityDamageByEntityEvent) {
                pool = deathByEntity;
                Entity damager = ((EntityDamageByEntityEvent) last).getDamager();
                if (killer == null && damager != null) killer = damager.getType().name().toLowerCase(Locale.ROOT);
            } else if (cause != null) {
                pool = deathByCause.get(cause);
            }

            if (pool == null || pool.isEmpty()) pool = deathDefault;

            String raw = pick(pool);
            if (raw == null) return;

            event.deathMessage(render("<yellow>" + raw, p, killer, causeText));
        });
    }

    private List<String> readList(String path, List<String> fallback) {
        List<String> list = api.config().getStringList(path);
        if (list == null || list.isEmpty()) return fallback;
        return List.copyOf(list);
    }

    private String pick(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(rng.nextInt(list.size()));
    }

    private Component render(String raw, Player player, String killer, String cause) {
        String world = player.getWorld().getName();

        String s = raw
                .replace("{player}", player.getName())
                .replace("{world}", world)
                .replace("{killer}", killer == null ? "" : killer)
                .replace("{cause}", cause == null ? "" : cause);

        // If you want glyph bindings here too:
        // s = plugin.localeService().processBindings(s);

        return mm.deserialize(s);
    }

    private EntityDamageEvent.DamageCause parseCause(String key) {
        if (key == null) return null;
        String k = key.trim().toUpperCase(Locale.ROOT);
        try {
            return EntityDamageEvent.DamageCause.valueOf(k);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean wasRecentlyKicked(UUID uuid) {
        Long until = kickedUntil.get(uuid);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            kickedUntil.remove(uuid);
            return false;
        }
        return true;
    }
}
