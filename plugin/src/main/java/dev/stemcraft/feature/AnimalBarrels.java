package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Craftable crates used to safely carry small passive animals. */
public final class AnimalBarrels extends BaseFeature {
    private static final String ITEM_ID = "animal-barrel";
    private static final String TYPE = "animal-barrel-type";
    private static final String NAME = "animal-barrel-name";
    private static final String BABY = "animal-barrel-baby";
    private static final String OWNER = "animal-barrel-owner";
    private static final String VARIANT = "animal-barrel-variant";
    private static final Set<EntityType> ALLOWED = EnumSet.of(EntityType.CHICKEN, EntityType.RABBIT,
        EntityType.FROG, EntityType.CAT);

    public AnimalBarrels(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        registerItem();
        api.events().register(PlayerInteractEntityEvent.class, this::capture, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEvent.class, this::release, EventPriority.HIGHEST, true);
        api.events().register(org.bukkit.event.block.BlockPlaceEvent.class, event -> {
            if (api.items().isCustomItemId(ITEM_ID, event.getItemInHand())) event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }

    @Override
    public void onDisable() {
        api.recipes().remove("stemcraft:animal-barrel");
    }

    private void registerItem() {
        ItemStack crate = new ItemStack(Material.BARREL);
        ItemMeta meta = crate.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(getConfigSection().getString("item-name", "Animal Barrel")));
        meta.setMaxStackSize(1);
        crate.setItemMeta(meta);
        api.items().registerCustomItem(ITEM_ID, crate);
        api.recipes().remove("stemcraft:animal-barrel");
        ItemStack recipeResult = api.items().createCustomItem(ITEM_ID);
        if (recipeResult != null) api.recipes().addShapeless("animal-barrel", recipeResult,
            Material.BARREL, Material.LEAD);
    }

    private void capture(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getHand() != EquipmentSlot.HAND || !ALLOWED.contains(event.getRightClicked().getType())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!api.items().isCustomItemId(ITEM_ID, held) || api.items().hasAttrib(held, TYPE)) return;
        Entity animal = event.getRightClicked();
        if (animal instanceof Tameable tameable && tameable.isTamed() && tameable.getOwnerUniqueId() != null
            && !tameable.getOwnerUniqueId().equals(player.getUniqueId()) && !player.hasPermission("stemcraft.animalbarrel.others")) {
            api.messages().send(player, getConfigSection().getString("messages.not-owner", "/error/That animal belongs to another player."));
            return;
        }
        event.setCancelled(true);
        ItemStack filled = held.clone();
        api.items().addAttrib(filled, TYPE, animal.getType().name());
        api.items().addAttrib(filled, BABY, animal instanceof Ageable ageable && !ageable.isAdult());
        if (animal.customName() != null) api.items().addAttrib(filled, NAME,
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(animal.customName()));
        if (animal instanceof Tameable tameable && tameable.getOwnerUniqueId() != null)
            api.items().addAttrib(filled, OWNER, tameable.getOwnerUniqueId().toString());
        if (animal instanceof Rabbit rabbit) api.items().addAttrib(filled, VARIANT, rabbit.getRabbitType().name());
        if (animal instanceof Cat cat) api.items().addAttrib(filled, VARIANT, cat.getCatType().getKey().toString());
        if (animal instanceof Frog frog) api.items().addAttrib(filled, VARIANT, frog.getVariant().getKey().toString());
        ItemMeta meta = filled.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(getConfigSection().getString("filled-name", "Animal Barrel ({animal})")
            .replace("{animal}", friendly(animal.getType().name()))));
        filled.setItemMeta(meta);
        player.getInventory().setItemInMainHand(filled);
        animal.remove();
        api.messages().send(player, getConfigSection().getString("messages.captured", "/success/Picked up {animal}."),
            "animal", friendly(animal.getType().name()));
    }

    private void release(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!api.items().isCustomItemId(ITEM_ID, held) || !api.items().hasAttrib(held, TYPE)) return;
        EntityType type;
        try { type = EntityType.valueOf(api.items().getAttrib(held, TYPE, String.class, "")); }
        catch (IllegalArgumentException exception) { return; }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if (!location.getBlock().isPassable()) {
            api.messages().send(event.getPlayer(), getConfigSection().getString("messages.no-space", "/error/There is not enough room to release that animal."));
            return;
        }
        event.setCancelled(true);
        Entity spawned = location.getWorld().spawnEntity(location, type);
        restore(spawned, held);
        ItemStack empty = api.items().createCustomItem(ITEM_ID);
        event.getPlayer().getInventory().setItemInMainHand(empty);
        api.messages().send(event.getPlayer(), getConfigSection().getString("messages.released", "/success/Released {animal}."),
            "animal", friendly(type.name()));
    }

    private void restore(Entity entity, ItemStack crate) {
        String name = api.items().getAttrib(crate, NAME, String.class, "");
        if (!name.isBlank()) entity.customName(net.kyori.adventure.text.Component.text(name));
        if (entity instanceof Ageable ageable && api.items().getAttrib(crate, BABY, Boolean.class, false)) ageable.setBaby();
        String owner = api.items().getAttrib(crate, OWNER, String.class, "");
        if (entity instanceof Tameable tameable && !owner.isBlank()) {
            try {
                tameable.setOwner(Bukkit.getOfflinePlayer(UUID.fromString(owner)));
                tameable.setTamed(true);
            } catch (IllegalArgumentException ignored) { }
        }
        String variant = api.items().getAttrib(crate, VARIANT, String.class, "");
        try {
            if (entity instanceof Rabbit rabbit) rabbit.setRabbitType(Rabbit.Type.valueOf(variant));
            else if (entity instanceof Cat cat) {
                var key = org.bukkit.NamespacedKey.fromString(variant);
                if (key != null && org.bukkit.Registry.CAT_VARIANT.get(key) != null) cat.setCatType(org.bukkit.Registry.CAT_VARIANT.get(key));
            } else if (entity instanceof Frog frog) {
                var key = org.bukkit.NamespacedKey.fromString(variant);
                if (key != null && org.bukkit.Registry.FROG_VARIANT.get(key) != null) frog.setVariant(org.bukkit.Registry.FROG_VARIANT.get(key));
            }
        } catch (IllegalArgumentException ignored) { }
    }

    private static String friendly(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
