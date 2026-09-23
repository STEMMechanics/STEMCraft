package dev.stemcraft.feature;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.GameMode;
import org.bukkit.event.EventPriority;

/** Allows advancement progress only while a player is in Survival mode. */
public class SurvivalAdvancements extends BaseFeature {
    public SurvivalAdvancements(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerAdvancementCriterionGrantEvent.class, event -> {
            if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) {
                event.setCancelled(true);
            }
        }, EventPriority.HIGHEST, true);
    }
}
