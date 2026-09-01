package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Hidden, session-scoped night vision for builders. */
public final class NightVisionFeature extends BaseFeature {
    private final Set<UUID> enabledPlayers = new HashSet<>();

    public NightVisionFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerChangedWorldEvent.class, event -> disable(event.getPlayer()), EventPriority.MONITOR, true);
        api.events().register(PlayerGameModeChangeEvent.class, event -> disable(event.getPlayer()), EventPriority.MONITOR, true);
        api.events().register(PlayerQuitEvent.class, event -> disable(event.getPlayer()));
        api.commands().create("nightvision")
            .aliases("nv")
            .description("Toggle hidden night vision until changing world or game mode.")
            .usage("/nightvision [on|off]")
            .tabCompletion("on")
            .tabCompletion("off")
            .permission("stemcraft.command.nightvision")
            .executor((unused, command, ctx) -> {
                ctx.checkNotConsole();
                Player player = ctx.asPlayer();
                if (ctx.args().size() > 1 || (!ctx.args().isEmpty()
                    && !Set.of("on", "off").contains(ctx.getArgLower(0)))) {
                    ctx.returnError("Use /nightvision [on|off].");
                    return;
                }
                boolean currentlyEnabled = enabledPlayers.contains(player.getUniqueId());
                boolean turnOn = ctx.args().isEmpty() ? !currentlyEnabled : ctx.getArgLower(0).equals("on");
                if (!turnOn) {
                    if (!currentlyEnabled) {
                        ctx.returnInfo("Night vision is already off.");
                        return;
                    }
                    disable(player);
                    ctx.returnSuccess("Night vision turned off.");
                    return;
                }
                if (currentlyEnabled) {
                    ctx.returnInfo("Night vision is already on.");
                    return;
                }
                if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    ctx.returnError("You already have night vision from another source.");
                    return;
                }
                enable(player);
                ctx.returnSuccess("Night vision enabled until you use /nv again or change world or game mode.");
            })
            .register(STEMCraft.getPlugin());
    }

    @Override
    public void onDisable() {
        for (UUID uuid : Set.copyOf(enabledPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) disable(player);
        }
        enabledPlayers.clear();
    }

    private void enable(Player player) {
        player.addPotionEffect(managedEffect());
        enabledPlayers.add(player.getUniqueId());
    }

    private void disable(Player player) {
        if (!enabledPlayers.remove(player.getUniqueId())) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (isManagedEffect(current)) player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    static PotionEffect managedEffect() {
        return new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false);
    }

    static boolean isManagedEffect(PotionEffect effect) {
        return effect != null && effect.getType().equals(PotionEffectType.NIGHT_VISION)
            && effect.isInfinite() && effect.getAmplifier() == 0 && !effect.isAmbient()
            && !effect.hasParticles() && !effect.hasIcon();
    }
}
