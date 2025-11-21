package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.concurrent.ThreadLocalRandom;

public final class RebalanceIronGolem implements STEMCraftFeature {
    private static final int DEFAULT_MIN_DROPS = 3;
    private static final int DEFAULT_MAX_DROPS = 5;

    @Override
    public void onEnable(STEMCraftAPI api) {
        String base = getConfigBase(); // e.g. features.rebalance_iron_golem

        int min = Math.max(0, api.config().getInt(base + ".min_drops", DEFAULT_MIN_DROPS));
        int max = Math.max(0, api.config().getInt(base + ".max_drops", DEFAULT_MAX_DROPS));

        final int minDrops = Math.min(min, max);
        final int maxDrops = Math.max(min, max);

        api.registerEvent(EntityDeathEvent.class, event -> {
            if (event.getEntityType() != EntityType.IRON_GOLEM) return;

            event.getDrops().clear();

            int amount = ThreadLocalRandom.current()
                    .nextInt(minDrops, maxDrops + 1); // inclusive range [min, max]

            event.getDrops().add(new ItemStack(Material.IRON_NUGGET, amount));
        });
    }
}
