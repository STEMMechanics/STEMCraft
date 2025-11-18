package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.services.LogService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class DenySpawnEggs implements Listener {
    private final static String PERMISSION = "stemcraft.allow_spawn_eggs";
    private LocaleService localeService;
    private LogService logService;

    public void onEnable(STEMCraft plugin) {
        localeService = STEMCraft.getLocaleService();
        logService = STEMCraft.getLogService();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType().toString().endsWith("_SPAWN_EGG")) {
            Player player = event.getPlayer();

            if(!player.hasPermission(DenySpawnEggs.PERMISSION)) {
                event.setCancelled(true);
                logService.error(player, localeService.get(player, "DENY_SPAWN_EGG"));
            }
        }
    }
}
