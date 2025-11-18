package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.Random;

public final class RebalanceIronGolem implements Listener {
    private int minDrops = 3;
    private int maxDrops = 5;

    public void onEnable(STEMCraft plugin) {
        minDrops = plugin.getConfig().getInt("features.rebalance_iron_golem.min_drops", 3);
        maxDrops = plugin.getConfig().getInt("features.rebalance_iron_golem.max_drops", 5);
    }

    @EventHandler
    public void onIronGolemDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.IRON_GOLEM) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.IRON_NUGGET, minDrops + new Random().nextInt(maxDrops)));
        }
    }
}
