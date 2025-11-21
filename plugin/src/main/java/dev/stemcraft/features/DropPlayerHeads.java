package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class DropPlayerHeads implements STEMCraftFeature {

    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            LivingEntity killer = player.getKiller();

            if (killer instanceof Player killerPlayer) {
                if (killerPlayer.getUniqueId().equals(player.getUniqueId())) return;
                if (killerPlayer.getGameMode() != GameMode.SURVIVAL) return;

                if (player.getGameMode() == GameMode.SURVIVAL) {
                    ItemStack playerHead = SCPlayer.getHead(player);
                    event.getDrops().add(playerHead);
                }
            }
        });
    }
}
