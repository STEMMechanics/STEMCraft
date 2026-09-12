package dev.stemcraft.feature.quest;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.event.guide.GuideActionRequestEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Optional, private practice quest owned by the quest feature. It demonstrates acceptance,
 * a location objective and book hand-in without rewards, real quest statistics or persistent
 * campaign state. The caller controls its lifetime through the public guide request event.
 */
public final class PracticeQuestGuide {
    private final STEMCraftAPI api;
    private final Plugin plugin;
    private final NamespacedKey bookKey;
    private final Map<UUID, Attempt> attempts = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * @param api plugin API
     * @param plugin owner of private entities and temporary books
     */
    public PracticeQuestGuide(STEMCraftAPI api, Plugin plugin) {
        this.api = api;
        this.plugin = plugin;
        bookKey = new NamespacedKey(plugin, "practice-quest-book");
    }

    /** Register optional practice requests and owner interaction/cleanup listeners. */
    public void enable() {
        listeners.add(api.events().register(GuideActionRequestEvent.class, this::begin));
        listeners.add(api.events().register(PlayerInteractEntityEvent.class, this::interact, EventPriority.HIGHEST, false));
        Bukkit.getOnlinePlayers().forEach(this::removeBooks);
        listeners.add(api.events().register(PlayerJoinEvent.class, event -> removeBooks(event.getPlayer())));
        listeners.add(api.events().register(PlayerQuitEvent.class, event -> cancel(event.getPlayer())));
        listeners.add(api.events().register(PlayerChangedWorldEvent.class, event -> cancel(event.getPlayer())));
        listeners.add(api.events().register(PlayerDeathEvent.class, event -> {
            event.getDrops().removeIf(this::isBook);
            cancel(event.getEntity());
        }));
        listeners.add(api.events().register(PlayerDropItemEvent.class, event -> {
            if (isBook(event.getItemDrop().getItemStack())) event.setCancelled(true);
        }));
        // Practice books stay with the owner and cannot be stored or traded.
        listeners.add(api.events().register(org.bukkit.event.inventory.InventoryClickEvent.class, event -> {
            boolean hotbarBook = event.getWhoClicked() instanceof Player player && event.getHotbarButton() >= 0
                && isBook(player.getInventory().getItem(event.getHotbarButton()));
            boolean offhandBook = event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && event.getWhoClicked() instanceof Player player && isBook(player.getInventory().getItemInOffHand());
            if (isBook(event.getCurrentItem()) || isBook(event.getCursor()) || hotbarBook || offhandBook) event.setCancelled(true);
        }));
        listeners.add(api.events().register(org.bukkit.event.inventory.InventoryDragEvent.class, event -> {
            if (isBook(event.getOldCursor())) event.setCancelled(true);
        }));
        api.tasks().repeating("quest-practice", 20L, 20L, this::tick);
    }

    private void begin(GuideActionRequestEvent request) {
        if (!request.action().equals("stemcraft:quest-practice")) return;
        Player player = request.player();
        if (!player.isOnline() || !player.getWorld().getName().toLowerCase(Locale.ROOT).startsWith("survival")) return;
        if (attempts.containsKey(player.getUniqueId())) return;
        if (!request.claim(() -> remove(player))) return;
        Location location = findSpot(player);
        if (location == null) { request.complete(false); return; }
        try {
            Villager npc = location.getWorld().spawn(location, Villager.class, villager -> {
                // Apply before spawning so other clients never receive the entity.
                villager.setVisibleByDefault(false);
                villager.setPersistent(false);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.customName(Component.text("Practice Guide [?]", NamedTextColor.YELLOW));
                villager.setCustomNameVisible(true);
            });
            Attempt attempt = new Attempt(request, npc, location.clone(), UUID.randomUUID().toString());
            attempts.put(player.getUniqueId(), attempt);
            player.showEntity(plugin, npc);
            tell(player, "Right-click your private Practice Guide to accept a short quest. Say skip to stop at any time.");
        } catch (RuntimeException failure) {
            request.complete(false);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not start private practice quest", failure);
        }
    }

    private void interact(PlayerInteractEntityEvent event) {
        Attempt attempt = attempts.values().stream().filter(value -> value.npc.equals(event.getRightClicked())).findFirst().orElse(null);
        if (attempt == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!attempt.request.player().getUniqueId().equals(player.getUniqueId()) || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!attempt.accepted) {
            if (player.getInventory().firstEmpty() < 0) { tell(player, "Make room for one practice quest book, then click me again."); return; }
            player.getInventory().addItem(book(attempt));
            attempt.accepted = true;
            attempt.objectiveStart = player.getLocation().clone();
            tell(player, "Quest accepted! Read your book, walk four blocks away from this guide, then come back.");
        } else if (!attempt.ready) {
            tell(player, "Walk at least four blocks away, then return to me with your practice book.");
        } else if (!hasBook(player, attempt.id)) {
            tell(player, "Bring your practice quest book to hand in. You can say skip and try again if it is missing.");
        } else {
            tell(player, "Practice quest handed in! Real quests work the same way: follow the book, then bring it to the right NPC.");
            attempt.request.complete(true);
        }
    }

    private void tick() {
        for (Attempt attempt : List.copyOf(attempts.values())) {
            Player player = attempt.request.player();
            if (!player.isOnline() || !attempt.npc.isValid() || !player.getWorld().equals(attempt.origin.getWorld())
                || System.currentTimeMillis() - attempt.started > 300_000) { attempt.request.complete(false); continue; }
            Location start = attempt.objectiveStart == null ? attempt.origin : attempt.objectiveStart;
            double dx = player.getLocation().getX() - start.getX();
            double dz = player.getLocation().getZ() - start.getZ();
            if (attempt.accepted && !attempt.ready && dx * dx + dz * dz >= 16) {
                attempt.ready = true;
                attempt.npc.customName(Component.text("Practice Guide [!]", NamedTextColor.YELLOW));
                tell(player, "Objective complete. Return to the Practice Guide and right-click with your book in your inventory.");
            }
        }
    }

    private ItemStack book(Attempt attempt) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle("A Small First Step");
        meta.setAuthor("Practice Guide");
        meta.pages(Component.text("A Small First Step\n\n1. Walk four blocks away from the Practice Guide.\n\n2. Return and right-click the guide with this book in your inventory.\n\nThis is optional practice. Say skip to stop. No items or XP are awarded."));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, attempt.id);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isBook(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.STRING);
    }
    private boolean hasBook(Player player, String id) {
        return Arrays.stream(player.getInventory().getContents()).anyMatch(item -> isBook(item)
            && id.equals(item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.STRING)));
    }
    private void cancel(Player player) {
        Attempt attempt = attempts.get(player.getUniqueId());
        if (attempt != null) attempt.request.complete(false);
    }
    private void remove(Player player) {
        Attempt attempt = attempts.remove(player.getUniqueId());
        if (attempt == null) return;
        attempt.npc.remove();
        removeBooks(player);
    }
    private void removeBooks(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isBook(item)) player.getInventory().setItem(slot, null);
        }
        if (isBook(player.getItemOnCursor())) player.setItemOnCursor(null);
    }
    private static void tell(Player player, String message) { player.sendMessage(Component.text(message, NamedTextColor.YELLOW)); }

    private static Location findSpot(Player player) {
        Location base = player.getLocation();
        for (int radius = 2; radius <= 5; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy = -1; dy <= 1; dy++) {
                Location feet = base.clone().add(dx, dy, dz).toBlockLocation().add(.5, 0, .5);
                World world = feet.getWorld();
                if (!world.isChunkLoaded(feet.getBlockX() >> 4, feet.getBlockZ() >> 4)
                    || feet.getY() <= world.getMinHeight() || feet.getY() + 2 >= world.getMaxHeight()
                    || !world.getWorldBorder().isInside(feet)) continue;
                Material floor = feet.clone().add(0, -1, 0).getBlock().getType();
                if (feet.getBlock().getType().isAir() && feet.clone().add(0, 1, 0).getBlock().getType().isAir()
                    && floor.isSolid() && floor.isOccluding() && floor != Material.MAGMA_BLOCK) return feet;
            }
        }
        return null;
    }

    /** Fail pending practice steps and remove their private entities and temporary books. */
    public void clear() {
        for (Attempt attempt : List.copyOf(attempts.values())) attempt.request.complete(false);
    }
    /** Release listeners, timer and active attempts on quest-feature disable. */
    public void disable() {
        api.tasks().cancel("quest-practice");
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        clear();
    }
    private static final class Attempt {
        final GuideActionRequestEvent request;
        final Villager npc;
        final Location origin;
        final String id;
        final long started = System.currentTimeMillis();
        Location objectiveStart;
        boolean accepted;
        boolean ready;
        Attempt(GuideActionRequestEvent request, Villager npc, Location origin, String id) {
            this.request = request; this.npc = npc; this.origin = origin; this.id = id;
        }
    }
}
