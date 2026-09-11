package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.gift.GiftService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Default Gift API implementation. */
public final class GiftServiceImpl extends BaseService implements GiftService {
    public static final String ITEM_ID = "gift";
    private static final String CONTENTS_ATTRIBUTE = "gift-contents";
    private final Map<UUID, GiftEditorHolder> editors = new HashMap<>();
    private dev.stemcraft.api.command.Command giftCommand;

    public GiftServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "gifts");
    }

    @Override
    public void onEnable() {
        if (api.items().createCustomItem(ITEM_ID) == null) {
            Material material = Material.STICK;
            ItemStack template = new ItemStack(material);
            ItemMeta meta = template.getItemMeta();
            meta.displayName(dev.stemcraft.api.util.TextUtil.colourise(getConfigSection().getString("item.name", "<gold>Gift"))
                .decoration(TextDecoration.ITALIC, false));
            meta.setMaxStackSize(1);
            template.setItemMeta(meta);
            api.items().registerCustomItem(ITEM_ID, template);
        }
        api.events().register(PlayerInteractEvent.class, this::openGift, EventPriority.HIGHEST, true);
        api.events().register(InventoryClickEvent.class, this::handleEditorClick, EventPriority.HIGHEST, false);
        api.events().register(InventoryDragEvent.class, this::handleEditorDrag, EventPriority.HIGHEST, false);
        api.events().register(InventoryCloseEvent.class, this::handleEditorClose, EventPriority.MONITOR, false);
        giftCommand = api.commands().create("gift")
            .description("Create, inspect, or edit Gifts")
            .usage("/gift <create|inspect|edit>")
            .permission("stemcraft.command.gift")
            .tabCompletion("create")
            .tabCompletion("inspect")
            .tabCompletion("edit")
            .executor((unusedApi, unusedCommand, context) -> handleCommand(context))
            .register(STEMCraft.getPlugin());
    }

    @Override
    public void onDisable() {
        if (giftCommand != null) giftCommand.unregister();
        giftCommand = null;
        editors.clear();
    }

    @Override
    public @NotNull ItemStack createGift(@NotNull List<ItemStack> contents) {
        List<ItemStack> safe = contents.stream().filter(item -> item != null && !item.getType().isAir())
            .map(ItemStack::clone).toList();
        if (safe.isEmpty()) throw new IllegalArgumentException("A gift must contain at least one item");
        ItemStack gift = api.items().createCustomItem(ITEM_ID);
        if (gift == null) throw new IllegalStateException("The Gift custom item is not registered");
        api.items().addAttrib(gift, CONTENTS_ATTRIBUTE, serialize(safe));
        return gift;
    }

    @Override
    public @NotNull ItemStack createGiftFromSpecs(@NotNull List<String> specifications) {
        return createGift(specifications.stream().map(this::createItem).filter(Objects::nonNull).toList());
    }

    @Override
    public @Nullable ItemStack createItem(@NotNull String specification) {
        String value = specification.trim();
        if (value.isEmpty()) return null;
        String itemId = value;
        int minimum = 1;
        int maximum = 1;
        int separator = value.lastIndexOf(',');
        if (separator >= 0) {
            itemId = value.substring(0, separator).trim();
            String quantity = value.substring(separator + 1).trim();
            if (!quantity.isEmpty()) {
                String[] range = quantity.split("-", 2);
                try {
                    minimum = Math.max(1, Integer.parseInt(range[0].trim()));
                    maximum = range.length == 1 ? minimum : Math.max(minimum, Integer.parseInt(range[1].trim()));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid gift quantity range: " + quantity);
                }
            }
        }
        int amount = ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
        ItemStack item = api.items().createCustomItem(itemId, amount);
        if (item != null) return item;
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown gift item: " + itemId);
        return new ItemStack(material, amount);
    }

    @Override
    public @NotNull List<ItemStack> contents(@Nullable ItemStack gift) {
        if (!isGift(gift)) return List.of();
        return deserialize(api.items().getAttrib(gift, CONTENTS_ATTRIBUTE, byte[].class, new byte[0]));
    }

    @Override
    public boolean isGift(@Nullable ItemStack item) {
        return item != null && api.items().isCustomItemId(ITEM_ID, item) && api.items().hasAttrib(item, CONTENTS_ATTRIBUTE);
    }

    private void openGift(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) return;
        ItemStack held = event.getHand() == EquipmentSlot.HAND
            ? event.getPlayer().getInventory().getItemInMainHand() : event.getPlayer().getInventory().getItemInOffHand();
        List<ItemStack> giftContents = contents(held);
        if (giftContents.isEmpty()) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        held.subtract(1);
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        var location = target.getLocation().add(0.5, 0.25, 0.5);
        giftContents.forEach(item -> player.getWorld().dropItemNaturally(location, item));
        String opened = getConfigSection().getString("messages.opened", "");
        if (!opened.isBlank()) api.messages().send(player, opened);
    }

    private void handleCommand(dev.stemcraft.api.command.CommandContext context) {
        if (!(context.getSender() instanceof Player player)) {
            context.error(getConfigSection().getString("messages.player-only", "Only players can edit Gifts."));
            return;
        }
        if (context.args().isEmpty()) {
            context.error("Usage: /gift <create|inspect|edit>");
            return;
        }
        switch (context.args().getFirst().toLowerCase(Locale.ROOT)) {
            case "create" -> openEditor(player, EditorMode.CREATE, null);
            case "inspect" -> {
                ItemStack gift = player.getInventory().getItemInMainHand();
                if (!isGift(gift)) {
                    api.messages().send(player, getConfigSection().getString("messages.hold-gift", "/error/Hold a Gift in your main hand."));
                    return;
                }
                openEditor(player, EditorMode.INSPECT, gift);
            }
            case "edit" -> {
                ItemStack gift = player.getInventory().getItemInMainHand();
                if (!isGift(gift)) {
                    api.messages().send(player, getConfigSection().getString("messages.hold-gift", "/error/Hold a Gift in your main hand."));
                    return;
                }
                player.getInventory().setItemInMainHand(null);
                openEditor(player, EditorMode.EDIT, gift);
            }
            default -> context.error("Usage: /gift <create|inspect|edit>");
        }
    }

    private void openEditor(Player player, EditorMode mode, @Nullable ItemStack original) {
        if (editors.containsKey(player.getUniqueId())) return;
        String titlePath = switch (mode) {
            case CREATE -> "ui.create-title";
            case EDIT -> "ui.edit-title";
            case INSPECT -> "ui.inspect-title";
        };
        String defaultTitle = switch (mode) {
            case CREATE -> "Create Gift";
            case EDIT -> "Edit Gift";
            case INSPECT -> "Gift Contents";
        };
        GiftEditorHolder holder = new GiftEditorHolder(player.getUniqueId(), mode, original);
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, 54,
            dev.stemcraft.api.util.TextUtil.colourise(getConfigSection().getString(titlePath, defaultTitle)));
        holder.inventory = inventory;
        if (original != null) inventory.setContents(contents(original).toArray(ItemStack[]::new));
        editors.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    private void handleEditorClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof GiftEditorHolder holder)) return;
        if (holder.mode == EditorMode.INSPECT) event.setCancelled(true);
    }

    private void handleEditorDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof GiftEditorHolder holder)) return;
        if (holder.mode == EditorMode.INSPECT) event.setCancelled(true);
    }

    private void handleEditorClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof GiftEditorHolder holder)) return;
        editors.remove(holder.ownerUuid, holder);
        if (holder.mode == EditorMode.INSPECT || !(event.getPlayer() instanceof Player player)) return;
        List<ItemStack> editedContents = Arrays.stream(event.getInventory().getContents())
            .filter(item -> item != null && !item.getType().isAir()).map(ItemStack::clone).toList();
        ItemStack result;
        if (editedContents.isEmpty()) {
            result = holder.original;
            api.messages().send(player, getConfigSection().getString("messages.empty", "/error/A Gift must contain at least one item."));
        } else {
            result = createGift(editedContents);
            String messagePath = holder.mode == EditorMode.CREATE ? "messages.created" : "messages.updated";
            api.messages().send(player, getConfigSection().getString(messagePath,
                holder.mode == EditorMode.CREATE ? "/success/Gift created." : "/success/Gift updated."));
        }
        if (result != null) giveOrDrop(player, result);
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
    }

    private enum EditorMode { CREATE, EDIT, INSPECT }

    private static final class GiftEditorHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final EditorMode mode;
        private final ItemStack original;
        private Inventory inventory;

        private GiftEditorHolder(UUID ownerUuid, EditorMode mode, @Nullable ItemStack original) {
            this.ownerUuid = ownerUuid;
            this.mode = mode;
            this.original = original == null ? null : original.clone();
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static byte[] serialize(List<ItemStack> items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(items.size());
            for (ItemStack item : items) {
                byte[] itemBytes = item.serializeAsBytes();
                output.writeInt(itemBytes.length);
                output.write(itemBytes);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize gift contents", exception);
        }
    }

    private static List<ItemStack> deserialize(byte[] bytes) {
        if (bytes.length == 0) return List.of();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = input.readInt();
            if (count < 0 || count > 256) return List.of();
            List<ItemStack> items = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length <= 0 || length > 16_777_216) return List.of();
                byte[] itemBytes = input.readNBytes(length);
                if (itemBytes.length != length) return List.of();
                items.add(ItemStack.deserializeBytes(itemBytes));
            }
            return items;
        } catch (IOException | IllegalArgumentException exception) {
            return List.of();
        }
    }
}
