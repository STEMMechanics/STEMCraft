/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature.portal;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.event.world.SurvivalPortalActivateEvent;
import dev.stemcraft.api.service.world.portal.WorldPortalService;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.*;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.entity.LightningStrike;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.BiConsumer;
import static dev.stemcraft.feature.portal.PortalPattern.Offset;

/** Gameplay extension of CustomPortals. All state and world access stay on the server thread.
 * Pattern scans and effects never load neighboring chunks. No terrain code depends on this class.
 */
public final class SurvivalPortals implements WorldPortalService {
    private record BlockKey(UUID world, Offset position) {}
    private static final class Instance {
        final UUID id, world;
        final SurvivalPortalType type;
        final Offset origin;
        final int rotation;
        final Set<Integer> charges = new HashSet<>();
        boolean active;
        boolean generated;
        UUID partner;
        String worldName = "";
        final PortalExitBuilder.Bounds padBounds;
        Instance(UUID id, UUID world, SurvivalPortalType type, Offset origin, int rotation) {
            this.id = id; this.world = world; this.type = type; this.origin = origin; this.rotation = rotation;
            this.padBounds = PortalExitBuilder.bounds(type.pattern(), rotation);
        }
        Offset at(Offset offset) { return origin.add(offset.rotate(rotation)); }
    }
    private final STEMCraftAPI api;
    private final BiConsumer<Player, Location> teleport;
    private final java.util.function.Predicate<Location> reserved;
    private final Map<NamespacedKey, SurvivalPortalType> types = new LinkedHashMap<>();
    private final Map<UUID, Instance> instances = new LinkedHashMap<>();
    private final Map<BlockKey, Instance> frameIndex = new HashMap<>(), interiorIndex = new HashMap<>();
    private final Map<UUID, UUID> pendingTravel = new HashMap<>();
    private final Map<UUID, UUID> preparingExits = new HashMap<>();
    private final Map<UUID, UUID> attemptedPortal = new HashMap<>();
    private final Map<Chunk, Integer> travelTickets = new HashMap<>();
    private final org.bukkit.plugin.Plugin owner;
    private long ticketEpoch;
    private final Set<Chunk> ownedTickets = new HashSet<>();
    private final PortalTravelEffects travelEffects = new PortalTravelEffects();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private ConfigFile state;
    private BukkitTask ticker;
    private boolean enabled;
    private boolean dirty;
    private int ticks;
    private int maxPortals = 512;

    public SurvivalPortals(STEMCraftAPI api, BiConsumer<Player, Location> teleport, org.bukkit.plugin.Plugin owner) { this(api, teleport, location -> false, owner); }
    public SurvivalPortals(STEMCraftAPI api, BiConsumer<Player, Location> teleport,
                           java.util.function.Predicate<Location> reserved, org.bukkit.plugin.Plugin owner) {
        this.api = api; this.teleport = teleport; this.reserved = reserved; this.owner = owner;
    }
    public void enable(ConfigSection config) {
        reload(config);
        api.events().register(org.bukkit.event.player.PlayerTeleportEvent.class, this::arrived, EventPriority.MONITOR, true);
        api.events().register(org.bukkit.event.player.PlayerJoinEvent.class, event -> markArrival(event.getPlayer(), event.getPlayer().getLocation()));
        api.events().register(PlayerInteractEvent.class, this::interact, EventPriority.HIGHEST, true);
        api.events().register(BlockPhysicsEvent.class, this::physics, EventPriority.HIGHEST, true);
        api.events().register(EntityAddToWorldEvent.class, this::entityAdded, EventPriority.MONITOR, true);
        api.events().register(EntityPortalEnterEvent.class, this::itemEntered);
        api.events().register(BlockBreakEvent.class, e -> invalidate(e.getBlock()), EventPriority.MONITOR, true);
        api.events().register(BlockPlaceEvent.class, e -> invalidate(e.getBlock()), EventPriority.MONITOR, true);
        api.events().register(BlockBurnEvent.class, e -> invalidate(e.getBlock()), EventPriority.MONITOR, true);
        api.events().register(BlockExplodeEvent.class, e -> e.blockList().forEach(this::invalidate), EventPriority.MONITOR, true);
        api.events().register(EntityExplodeEvent.class, e -> e.blockList().forEach(this::invalidate), EventPriority.MONITOR, true);
        api.events().register(BlockPistonExtendEvent.class, e -> e.getBlocks().forEach(this::invalidate), EventPriority.MONITOR, true);
        api.events().register(BlockPistonRetractEvent.class, e -> e.getBlocks().forEach(this::invalidate), EventPriority.MONITOR, true);
        api.events().register(PlayerQuitEvent.class, e -> { cooldowns.remove(e.getPlayer().getUniqueId()); cancelTravel(e.getPlayer()); attemptedPortal.remove(e.getPlayer().getUniqueId()); });
        ticker = Bukkit.getScheduler().runTaskTimer(STEMCraft.getPlugin(), this::tick, 20, 20);
    }
    public void reload(ConfigSection config) {
        save();
        pendingTravel.clear();
        preparingExits.clear();
        travelEffects.stopAll();
        ticketEpoch++;
        attemptedPortal.clear();
        for (Chunk chunk : ownedTickets) removeTicket(chunk);
        ownedTickets.clear();
        travelTickets.clear();
        types.clear(); instances.clear(); frameIndex.clear(); interiorIndex.clear();
        enabled = config.getBoolean("survival.enabled", true);
        maxPortals = Math.clamp(config.getInt("survival.max-portals", 512), 1, 4096);
        java.io.File definitionsFile = new java.io.File(api.getDataFolder(), "worlds/portals.yml");
        if (!definitionsFile.exists()) STEMCraft.getPlugin().saveResource("worlds/portals.yml", false);
        ConfigFile definitions = api.config().load(definitionsFile);
        if (definitions != null) {
            // Add the new default once without overwriting an administrator's chosen sound or materials.
            ConfigSection faraway = definitions.getSection("types.faraway", false);
            if (faraway != null && !faraway.contains("effects.active-frame")) {
                faraway.set("effects.active-frame.OBSIDIAN", "CRYING_OBSIDIAN");
                if (faraway.getString("effects.activate-sound", "").equals("minecraft:block.portal.trigger"))
                    faraway.set("effects.activate-sound", "minecraft:entity.generic.explode");
                definitions.save();
            }
            // Update the former Skylands defaults once; custom placement configurations remain explicit.
            ConfigSection skylands = definitions.getSection("types.skylands", false);
            if (skylands != null && !skylands.contains("destination.placement")) {
                skylands.set("destination.placement", "AIR_DROP");
                skylands.set("effects.warmup-ticks", 0);
                skylands.set("effects.screen-distortion", false);
                definitions.save();
            }
            boolean upgraded = false;
            ConfigSection section = definitions.getSection("types", false);
            if (section != null) for (String id : section.getKeys(false)) {
                ConfigSection entry = section.getSection(id, false);
                if (entry == null || !entry.getBoolean("enabled", true)) continue;
                for (var setting : Map.<String, Object>of("destination.coordinate-scale", 8.0,
                        "destination.search-radius", 32, "destination.link-radius", 16, "effects.light-level", 14, "effects.warmup-ticks", 60, "effects.screen-distortion", true).entrySet()) {
                    if (!entry.contains(setting.getKey())) { entry.set(setting.getKey(), setting.getValue()); upgraded = true; }
                }
                try {
                    SurvivalPortalType type = SurvivalPortalType.read(id, entry);
                    types.put(type.key(), type);
                } catch (RuntimeException error) { warn("Invalid portal type " + id + ": " + error.getMessage()); }
            }
            if (upgraded) definitions.save();
        }
        state = api.config().load(new java.io.File(api.getDataFolder(), "worlds/portal-state.yml"));
        if (enabled && state != null) loadState();
    }
    public void disable() {
        enabled = false;
        pendingTravel.clear();
        preparingExits.clear();
        travelEffects.stopAll();
        ticketEpoch++;
        attemptedPortal.clear();
        for (Chunk chunk : ownedTickets) removeTicket(chunk);
        ownedTickets.clear();
        travelTickets.clear();
        if (ticker != null) { ticker.cancel(); ticker = null; }
        save();
        instances.clear(); frameIndex.clear(); interiorIndex.clear(); cooldowns.clear();
    }
    private static void serverThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Portal API requires the server thread");
    }
    @Override public Collection<PortalType> getTypes() {
        serverThread();
        return types.values().stream().map(t -> new PortalType(t.key(), t.generator(), t.activation().name())).toList();
    }
    @Override public Collection<Portal> getPortals() {
        serverThread();
        return instances.values().stream().map(SurvivalPortals::snapshot).toList();
    }
    private static Portal snapshot(Instance portal) {
        return new Portal(portal.id, portal.type.key(), portal.world, portal.origin.x(), portal.origin.y(), portal.origin.z(), portal.active);
    }
    @Override public Optional<Portal> getLinkedPortal(UUID id) {
        serverThread();
        Instance portal = instances.get(id);
        Instance linked = portal == null || portal.partner == null ? null : instances.get(portal.partner);
        return linked == null || !id.equals(linked.partner) ? Optional.empty() : Optional.of(snapshot(linked));
    }
    @Override public boolean deactivate(UUID id) {
        serverThread();
        Instance existing = instances.get(id);
        if (existing != null && Bukkit.getWorld(existing.world) == null) return false;
        Instance removed = instances.remove(id);
        if (removed == null) return false;
        Instance other = removed.partner == null ? null : instances.get(removed.partner);
        if (other != null && removed.id.equals(other.partner)) other.partner = null;
        frameIndex.values().removeIf(p -> p == removed);
        interiorIndex.values().removeIf(p -> p == removed);
        World world = Bukkit.getWorld(removed.world);
        if (world != null && removed.active) frameEffect(removed, false);
        if (world != null && loaded(world, removed.origin))
            world.playSound(location(world, removed.origin), removed.type.deactivateSound(), .6F, 1F);
        dirty = true;
        return true;
    }
    private void invalidate(Block block) {
        if (!enabled) return;
        BlockKey key = key(block);
        Instance portal = frameIndex.get(key);
        if (portal == null) portal = interiorIndex.get(key);
        if (portal != null) deactivate(portal.id);
    }
    private boolean available(SurvivalPortalType type) {
        return api.worlds().generator().getGenerator(type.generator()).isPresent();
    }
    private boolean matchesItem(String id, ItemStack stack) {
        if (stack == null || stack.isEmpty() || id.isBlank()) return false;
        if (id.startsWith("stemcraft:")) return api.items().isCustomItemId(id.substring(10), stack);
        Material material = Material.matchMaterial(id);
        return material != null && stack.getType() == material && api.items().getCustomItemId(stack) == null;
    }
    void interact(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Instance existing = frameIndex.get(key(block));
        if (existing != null && event.getPlayer().isSneaking() && matchesItem(existing.type.deactivateItem(), event.getItem())) {
            deactivate(existing.id); event.setCancelled(true); return;
        }
        for (SurvivalPortalType type : types.values()) {
            if ((type.activation() != SurvivalPortalType.Activation.INTERACT && type.activation() != SurvivalPortalType.Activation.CHARGE_BLOCKS)
                    || !type.allows(block.getWorld()) || !available(type) || !matchesItem(type.item(), event.getItem())) continue;
            if (type.activation() == SurvivalPortalType.Activation.CHARGE_BLOCKS && block.getType() != type.chargeBlock()) continue;
            Instance portal = find(type, block, false);
            if (portal == null || portal.active) continue;
            int charge = -1;
            if (type.activation() == SurvivalPortalType.Activation.CHARGE_BLOCKS) {
                for (int i = 0; i < type.pattern().cells().size(); i++) {
                    var cell = type.pattern().cells().get(i);
                    if (cell.material() == type.chargeBlock() && portal.at(cell.offset()).equals(offset(block))) charge = i;
                }
                if (charge < 0 || portal.charges.contains(charge)) continue;
            }
            if (!approve(portal, event.getPlayer())) return;
            if (charge >= 0) portal.charges.add(charge);
            portal.active = charge < 0 || portal.charges.size() == type.pattern().cells().stream().filter(c -> c.material() == type.chargeBlock()).count();
            if (!remember(portal)) return;
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                ItemStack remaining = event.getItem().clone();
                remaining.subtract(1);
                event.getPlayer().getInventory().setItemInMainHand(remaining);
            }
            event.setCancelled(true);
            if (!portal.active) block.getWorld().playSound(block.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, .6F, 1F);
            effect(portal, portal.active);
            return;
        }
    }
    void physics(BlockPhysicsEvent event) {
        if (!enabled) return;
        Instance portal = interiorIndex.get(key(event.getBlock()));
        if (portal != null && portal.active && !portal.type.activeFrame().isEmpty()
                && portal.type.pattern().interiorMaterial() == Material.NETHER_PORTAL
                && available(portal.type) && valid(portal)) event.setCancelled(true);
    }
    void entityAdded(EntityAddToWorldEvent event) {
        if (!enabled || !(event.getEntity() instanceof LightningStrike lightning) || lightning.isEffect()) return;
        // Observe actual admission, including /summon, after any cancellable strike/spawn event.
        Location hit = lightning.getLocation();
        for (SurvivalPortalType type : types.values()) {
            if (type.activation() != SurvivalPortalType.Activation.LIGHTNING || !type.allows(event.getWorld()) || !available(type)) continue;
            for (int dy = -2; dy <= 1; dy++) {
                Offset point = new Offset(hit.getBlockX(), hit.getBlockY() + dy, hit.getBlockZ());
                if (!loaded(event.getWorld(), point)) continue;
                Block block = block(event.getWorld(), point);
                if (!matchesFrame(type.lightningBlock(), block.getType())) continue;
                Instance portal = find(type, block, false);
                Player player = lightning.getCausingEntity() instanceof Player p ? p : null;
                if (portal != null && !portal.active && approve(portal, player)) {
                    portal.active = true;
                    if (remember(portal)) effect(portal, true);
                    return;
                }
            }
        }
    }
    void itemEntered(EntityPortalEnterEvent event) {
        if (!enabled || !(event.getEntity() instanceof Item item) || item.getThrower() == null) return;
        Player player = Bukkit.getPlayer(item.getThrower());
        if (player == null) return;
        Block block = event.getLocation().getBlock();
        for (SurvivalPortalType type : types.values()) {
            if (type.activation() != SurvivalPortalType.Activation.THROW_ITEM || !type.allows(block.getWorld())
                    || !available(type) || !matchesItem(type.item(), item.getItemStack())) continue;
            Instance portal = find(type, block, true);
            if (portal == null || portal.active || !approve(portal, player)) continue;
            portal.active = true;
            if (!remember(portal)) return;
            ItemStack stack = item.getItemStack();
            if (stack.getAmount() == 1) item.remove(); else { stack.subtract(1); item.setItemStack(stack); }
            effect(portal, true);
            return;
        }
    }
    private boolean approve(Instance portal, Player player) {
        World world = Bukkit.getWorld(portal.world);
        if (world == null || !available(portal.type)) return false;
        SurvivalPortalActivateEvent event = new SurvivalPortalActivateEvent(portal.type.key(), location(world, portal.origin), player);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
    private Instance find(SurvivalPortalType type, Block anchor, boolean interior) {
        List<Offset> anchors = interior ? type.pattern().interior()
                : type.pattern().cells().stream().filter(c -> c.material() == anchor.getType()).map(PortalPattern.Cell::offset).toList();
        for (int rotation = 0; rotation < 4; rotation++) for (Offset cell : anchors) {
            Offset origin = offset(anchor).subtract(cell.rotate(rotation));
            Instance candidate = new Instance(UUID.randomUUID(), anchor.getWorld().getUID(), type, origin, rotation);
            if (!valid(candidate)) continue;
            Instance existing = frameIndex.get(new BlockKey(candidate.world, candidate.at(type.pattern().cells().getFirst().offset())));
            if (existing != null) return existing.type.key().equals(type.key()) ? existing : null;
            return candidate;
        }
        return null;
    }
    private boolean valid(Instance portal) {
        World world = Bukkit.getWorld(portal.world);
        if (world == null) return false;
        for (var cell : portal.type.pattern().cells()) {
            Offset point = portal.at(cell.offset());
            if (!loaded(world, point)) return false;
            Material actual = block(world, point).getType();
            Material active = portal.type.activeFrame().getOrDefault(cell.material(), cell.material());
            if (!matchesFrame(cell.material(), actual) && (!portal.active || !matchesFrame(active, actual))) return false;
        }
        for (Offset cell : portal.type.pattern().interior()) {
            Offset point = portal.at(cell);
            if (!loaded(world, point)) return false;
            if (reserved.test(location(world, point))) return false;
            Material actual = block(world, point).getType();
            Material expected = portal.type.pattern().interiorMaterial();
            if (expected == Material.AIR ? !(actual.isAir() || portal.active && (actual == Material.LIGHT || reactorFire(portal, actual))) : actual != expected) return false;
        }
        return true;
    }
    private static boolean matchesFrame(Material expected, Material actual) {
        if (expected == actual) return true;
        if (expected != Material.COPPER_BLOCK && expected != Material.LIGHTNING_ROD) return false;
        return switch (expected) {
            case COPPER_BLOCK -> switch (actual) {
                case EXPOSED_COPPER, WEATHERED_COPPER, OXIDIZED_COPPER, WAXED_COPPER_BLOCK,
                        WAXED_EXPOSED_COPPER, WAXED_WEATHERED_COPPER, WAXED_OXIDIZED_COPPER -> true;
                default -> false;
            };
            case LIGHTNING_ROD -> switch (actual) {
                case EXPOSED_LIGHTNING_ROD, WEATHERED_LIGHTNING_ROD, OXIDIZED_LIGHTNING_ROD, WAXED_LIGHTNING_ROD,
                        WAXED_EXPOSED_LIGHTNING_ROD, WAXED_WEATHERED_LIGHTNING_ROD, WAXED_OXIDIZED_LIGHTNING_ROD -> true;
                default -> false;
            };
            default -> false;
        };
    }
    private static boolean reactorFire(Instance portal, Material material) {
        return portal.type.activation() == SurvivalPortalType.Activation.LIGHTNING && material == Material.FIRE;
    }
    private boolean allLoaded(Instance portal) {
        World world = Bukkit.getWorld(portal.world);
        if (world == null) return false;
        for (var c : portal.type.pattern().cells()) if (!loaded(world, portal.at(c.offset()))) return false;
        for (var c : portal.type.pattern().interior()) if (!loaded(world, portal.at(c))) return false;
        return true;
    }
    private boolean remember(Instance portal) {
        World world = Bukkit.getWorld(portal.world);
        if (world != null) portal.worldName = world.getName();
        if (!instances.containsKey(portal.id) && instances.size() >= maxPortals) return false;
        for (var c : portal.type.pattern().cells()) {
            BlockKey key = new BlockKey(portal.world, portal.at(c.offset()));
            Instance other = frameIndex.get(key);
            if (other != null && other != portal || interiorIndex.containsKey(key)) return false;
        }
        for (var c : portal.type.pattern().interior()) {
            BlockKey key = new BlockKey(portal.world, portal.at(c));
            Instance other = interiorIndex.get(key);
            if (other != null && other != portal || frameIndex.containsKey(key)) return false;
        }
        instances.put(portal.id, portal);
        for (var c : portal.type.pattern().cells()) frameIndex.put(new BlockKey(portal.world, portal.at(c.offset())), portal);
        for (var c : portal.type.pattern().interior()) interiorIndex.put(new BlockKey(portal.world, portal.at(c)), portal);
        dirty = true;
        return true;
    }
    void arrived(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (!enabled || event.isCancelled() || event.getTo() == null) return;
        cancelTravel(event.getPlayer());
        markArrival(event.getPlayer(), event.getTo());
    }
    private void markArrival(Player player, Location at) {
        attemptedPortal.remove(player.getUniqueId());
        for (Instance portal : instances.values()) {
            if (portal.active && onPad(portal, at)) {
                attemptedPortal.put(player.getUniqueId(), portal.id);
                break;
            }
        }
    }
    private static boolean onPad(Instance portal, Location at) {
        if (at.getWorld() == null || !portal.world.equals(at.getWorld().getUID())) return false;
        var bounds = portal.padBounds;
        int inset = 2;
        int x = at.getBlockX() - portal.origin.x(), y = at.getBlockY() - portal.origin.y(), z = at.getBlockZ() - portal.origin.z();
        return x >= bounds.minX() + inset && x <= bounds.maxX() - inset
                && z >= bounds.minZ() + inset && z <= bounds.maxZ() - inset
                && y >= bounds.minY() - (portal.generated && portal.type.exitPlacement() == SurvivalPortalType.ExitPlacement.AIR_DROP ? 6 : 0)
                && y <= bounds.maxY() + 3;
    }
    private void cancelTravel(Player player) {
        pendingTravel.remove(player.getUniqueId());
        stopDistortion(player);
    }
    void startDistortion(Player player) { travelEffects.start(player); }
    void stopDistortion(Player player) { travelEffects.stop(player); }
    private void failTravel(Player player, String message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, .5F, .7F);
    }
    /** Called ahead of the existing admin portal handlers. */
    public boolean move(PlayerMoveEvent event) {
        if (!enabled || event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return false;
        Instance portal = interiorIndex.get(key(event.getTo().getBlock()));
        Instance attempted = instances.get(attemptedPortal.get(event.getPlayer().getUniqueId()));
        if (attempted != null && !onPad(attempted, event.getTo())
                && (attempted.type.warmupTicks() > 0 || !pendingTravel.containsKey(event.getPlayer().getUniqueId()))) {
            attemptedPortal.remove(event.getPlayer().getUniqueId());
            cancelTravel(event.getPlayer());
        }
        if (portal != null && portal.active) { travel(event.getPlayer(), portal, event.getFrom()); return true; }
        return false;
    }
    public boolean portal(PlayerPortalEvent event) {
        if (!enabled) return false;
        Instance portal = interiorIndex.get(key(event.getFrom().getBlock()));
        if (portal == null || !portal.active) return false;
        event.setCancelled(true);
        travel(event.getPlayer(), portal, event.getFrom());
        return true;
    }
    private void travel(Player player, Instance portal, Location from) {
        if (!allLoaded(portal)) return;
        if (!valid(portal)) { deactivate(portal.id); return; }
        if (cooling(player) || (!portal.generated && !portal.type.allows(player.getWorld()))) return;
        if (portal.id.equals(attemptedPortal.get(player.getUniqueId()))) return;
        attemptedPortal.put(player.getUniqueId(), portal.id);
        if (!available(portal.type)) { failTravel(player, "The portal's energy has faded. Its distant realm is silent."); return; }
        World target;
        if (portal.generated) {
            Instance partner = portal.partner == null ? null : instances.get(portal.partner);
            if (partner == null || !portal.id.equals(partner.partner)) {
                failTravel(player, "The portal flickers, but nothing answers from the other side. Its twin must be rekindled."); return;
            }
            target = Bukkit.getWorld(partner.world);
            if (target == null && !partner.worldName.isBlank()) target = api.worlds().loadWorld(partner.worldName);
            if (target == null || !target.getUID().equals(partner.world)) {
                failTravel(player, "The portal reaches into the mist, but its distant twin does not answer. Try again later."); return;
            }
        } else {
            String name = portal.type.destinationName(player.getWorld().getName());
            target = Bukkit.getWorld(name);
            if (target == null) {
                if (api.worlds().worldExists(name)) target = api.worlds().loadWorld(name);
                else if (portal.type.createWorld()) target = api.worlds().createWorld(name, portal.type.generator().toString(), "", player.getWorld().getSeed());
            }
            if (target == null) { failTravel(player, "The portal shudders. The realm beyond will not open just yet."); return; }
            var generated = api.worlds().generator().getGeneratedWorld(target);
            if (generated.isEmpty() || !generated.get().generator().key().equals(portal.type.generator())) {
                failTravel(player, "The portal recoils from an unfamiliar realm. Its connection has been disturbed."); return;
            }
        }
        World destinationWorld = target;
        Instance linked = portal.partner == null ? null : instances.get(portal.partner);
        if (linked != null && !linked.world.equals(target.getUID())) {
            // A regenerated world has a different UUID: do not reuse stale structures at the same name.
            unlink(portal);
            if (Bukkit.getWorld(linked.world) == null) forget(linked);
            linked = null;
        }
        int x = linked != null ? linked.origin.x() : PortalExitBuilder.scaled(portal.origin.x(), portal.type.coordinateScale());
        int z = linked != null ? linked.origin.z() : PortalExitBuilder.scaled(portal.origin.z(), portal.type.coordinateScale());
        int radius = linked != null ? 22 : portal.type.searchRadius() + 22;
        if (!target.getWorldBorder().isInside(new Location(target,x+.5,target.getMinHeight()+1,z+.5))) {
            failTravel(player, "The portal reaches beyond the edge of the known world. Try building it elsewhere."); return;
        }
        UUID request = UUID.randomUUID();
        pendingTravel.put(player.getUniqueId(), request);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + portal.type.cooldownTicks() * 50L);
        travelEffect(player, true);
        if (portal.type.screenDistortion()) startDistortion(player);
        var warmed = new java.util.concurrent.CompletableFuture<Void>();
        if (portal.type.warmupTicks() == 0) warmed.complete(null);
        else api.tasks().runLater(portal.type.warmupTicks(), () -> warmed.complete(null));
        long epoch = ticketEpoch;
        List<Chunk> held = new ArrayList<>();
        java.util.concurrent.CompletableFuture.allOf(warmed, prepareTravelArea(target, x, z, radius, request, held)).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).whenComplete((unused, failure) -> api.tasks().nextTick(() -> {
            try {
                if (!pendingTravel.remove(player.getUniqueId(), request) || !enabled || !player.isOnline()
                        || instances.get(portal.id) != portal || !portal.active || !available(portal.type)
                        || !player.getWorld().equals(from.getWorld()) || player.getLocation().distanceSquared(from) > 64) return;
                if (failure != null || Bukkit.getWorld(destinationWorld.getUID()) != destinationWorld) {
                    if (failure != null) owner.getLogger().log(java.util.logging.Level.WARNING, "Could not prepare portal destination", failure);
                    failTravel(player, "The portal sputters and falls quiet. Step away and try again."); return;
                }
                if (!allLoaded(portal)) { failTravel(player, "The portal flickers. Step away and let its energy settle."); return; }
                if (!valid(portal)) { deactivate(portal.id); return; }
                Instance exit = portal.partner == null ? null : instances.get(portal.partner);
                if (exit != null && (!exit.world.equals(destinationWorld.getUID()) || !allLoaded(exit))) {
                    // Missing chunk data is not evidence that a player's portal has been broken.
                    failTravel(player, "The distant portal is still stirring. Step away and try again."); return;
                }
                if (exit != null && (!exit.active || !valid(exit))) {
                    if (exit.active) deactivate(exit.id);
                    exit = null;
                }
                if (exit == null && !portal.generated) exit = findOrBuildExit(player, portal, destinationWorld, x, z);
                if (exit == null) {
                    failTravel(player, "The portal cannot find a foothold in the realm beyond. Try building your entrance elsewhere, or clear a path around its twin."); return;
                }
                Location entrance = location(destinationWorld, exit.at(exit.type.pattern().interior().getFirst()));
                boolean drop = exit.generated && exit.type.exitPlacement() == SurvivalPortalType.ExitPlacement.AIR_DROP;
                Location landing = drop ? AirDropExit.landing(destinationWorld,exit.type,exit.origin,exit.rotation,this::outsideActivePortal)
                        : safe(entrance, this::outsideActivePortal, 2);
                if (landing == null) { failTravel(player, "Something blocks the far side. Clear a path around the other portal."); return; }
                PortalExitBuilder.faceAway(landing, exit.type.pattern(), exit.origin, exit.rotation);
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + portal.type.cooldownTicks() * 50L);
                attemptedPortal.put(player.getUniqueId(), exit.id);
                if (drop) {
                    player.setFallDistance(0);
                    player.setVelocity(new org.bukkit.util.Vector());
                }
                teleport.accept(player, landing);
            } finally {
                if (!pendingTravel.containsKey(player.getUniqueId())) stopDistortion(player);
                releaseTickets(held,epoch);
            }
        }));
    }
    /** Prepare a twin on activation; travel can still retry if terrain or world loading fails. */
    private void prepareExit(Instance source) {
        if (!enabled || instances.get(source.id)!=source || !source.active || source.generated
                || source.partner!=null || preparingExits.containsKey(source.id) || !available(source.type)) return;
        World origin=Bukkit.getWorld(source.world);
        if (origin==null) return;
        UUID request=UUID.randomUUID();
        preparingExits.put(source.id,request);
        long epoch=ticketEpoch;
        List<Chunk> held=new ArrayList<>();
        try {
            String name=source.type.destinationName(origin.getName());
            World target=Bukkit.getWorld(name);
            if(target==null) {
                if(api.worlds().worldExists(name)) target=api.worlds().loadWorld(name);
                else if(source.type.createWorld()) target=api.worlds().createWorld(name,source.type.generator().toString(),"",origin.getSeed());
            }
            if(target==null || api.worlds().generator().getGeneratedWorld(target)
                    .filter(w->w.generator().key().equals(source.type.generator())).isEmpty()) {
                preparingExits.remove(source.id,request); return;
            }
            World destination=target;
            int x=PortalExitBuilder.scaled(source.origin.x(),source.type.coordinateScale());
            int z=PortalExitBuilder.scaled(source.origin.z(),source.type.coordinateScale());
            if (!target.getWorldBorder().isInside(new Location(target,x+.5,target.getMinHeight()+1,z+.5))) {
                preparingExits.remove(source.id,request); return;
            }
            prepareTravelArea(target,x,z,source.type.searchRadius()+22,request,held)
                    .orTimeout(60,java.util.concurrent.TimeUnit.SECONDS).whenComplete((unused,failure)->api.tasks().nextTick(()->{
                try {
                    if(!preparingExits.remove(source.id,request) || !enabled || instances.get(source.id)!=source
                            || !source.active || source.partner!=null || !available(source.type)) return;
                    if(failure!=null) {
                        owner.getLogger().log(java.util.logging.Level.WARNING,"Could not prepare matching portal",failure); return;
                    }
                    if(Bukkit.getWorld(destination.getUID())==destination && allLoaded(source) && valid(source))
                        findOrBuildExit(null,source,destination,x,z);
                } finally { releaseTickets(held,epoch); }
            }));
        } catch(RuntimeException failure) {
            preparingExits.remove(source.id,request);
            releaseTickets(held,epoch);
            owner.getLogger().log(java.util.logging.Level.WARNING,"Could not prepare matching portal",failure);
        }
    }
    private void releaseTickets(List<Chunk> held,long epoch) {
        if(epoch!=ticketEpoch) return;
        for(Chunk chunk:held) {
            Integer count=travelTickets.get(chunk);
            if(count==null) continue;
            if(count==1) { travelTickets.remove(chunk); if(ownedTickets.remove(chunk)) removeTicket(chunk); }
            else travelTickets.put(chunk,count-1);
        }
    }
    private Instance findOrBuildExit(Player player, Instance source, World world, int x, int z) {
        // Reattach an intact orphan left after rebuilding its entrance, but never steal another pair's exit.
        Instance nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Instance candidate : instances.values()) {
            if (!candidate.generated || candidate.partner != null || !candidate.active || !candidate.world.equals(world.getUID())
                    || !candidate.type.key().equals(source.type.key())) continue;
            double d = Math.pow((double) candidate.origin.x() - x, 2) + Math.pow((double) candidate.origin.z() - z, 2);
            if (d <= (double) source.type.linkRadius() * source.type.linkRadius() && d < distance && valid(candidate)) {
                nearest = candidate; distance = d;
            }
        }
        if (nearest != null) { link(source, nearest); return nearest; }
        if (instances.size() >= maxPortals) return null;
        var blocked = (java.util.function.Predicate<Location>) at -> reserved.test(at)
                || frameIndex.containsKey(key(at.getBlock())) || interiorIndex.containsKey(key(at.getBlock()));
        PortalExitBuilder.Plan plan = PortalExitBuilder.find(world, source.type, x, z, source.rotation,
                world.getSpawnLocation().getBlockY(), blocked);
        if (plan == null) return null;
        Instance exit = new Instance(UUID.randomUUID(), world.getUID(), source.type, plan.origin(), source.rotation);
        exit.generated = true;
        if (!approve(exit, player)) return null;
        // Recheck after protection listeners, which may have changed blocks or reserved this footprint.
        plan = PortalExitBuilder.at(world, source.type, plan.origin(), source.rotation,
                PortalExitBuilder.bounds(source.type.pattern(), source.rotation), blocked);
        if (plan == null || !remember(exit)) return null;
        try { plan.build(world); }
        catch (RuntimeException failure) { forget(exit); throw failure; }
        exit.active = true;
        for (int i = 0; i < exit.type.pattern().cells().size(); i++)
            if (exit.type.pattern().cells().get(i).material() == exit.type.chargeBlock()) exit.charges.add(i);
        link(source, exit);
        effect(exit, true);
        return exit;
    }
    private void link(Instance first, Instance second) {
        first.partner = second.id; second.partner = first.id;
        dirty = true; save();
    }
    private void unlink(Instance portal) {
        Instance other = portal.partner == null ? null : instances.get(portal.partner);
        if (other != null && portal.id.equals(other.partner)) other.partner = null;
        portal.partner = null; dirty = true;
    }
    private void forget(Instance portal) {
        unlink(portal); instances.remove(portal.id);
        frameIndex.values().removeIf(p -> p == portal); interiorIndex.values().removeIf(p -> p == portal);
        dirty = true;
    }
    static java.util.concurrent.CompletableFuture<Void> prepare(Location location) {
        return prepareArea(location.getWorld(), location.getBlockX(), location.getBlockZ(), 4);
    }
    private static java.util.concurrent.CompletableFuture<Void> prepareArea(World world, int x, int z, int radius) {
        List<java.util.concurrent.CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = Math.floorDiv(x-radius,16); cx <= Math.floorDiv(x+radius,16); cx++)
            for (int cz = Math.floorDiv(z-radius,16); cz <= Math.floorDiv(z+radius,16); cz++)
                if (!world.isChunkLoaded(cx,cz)) chunks.add(world.getChunkAtAsync(cx,cz,true));
        return java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new));
    }
    private java.util.concurrent.CompletableFuture<Void> prepareTravelArea(World world, int x, int z, int radius,
            UUID request, List<Chunk> held) {
        List<java.util.concurrent.CompletableFuture<Void>> ready = new ArrayList<>();
        for (int cx = Math.floorDiv(x-radius,16); cx <= Math.floorDiv(x+radius,16); cx++)
            for (int cz = Math.floorDiv(z-radius,16); cz <= Math.floorDiv(z+radius,16); cz++) {
                var loaded = world.isChunkLoaded(cx,cz)
                        ? java.util.concurrent.CompletableFuture.completedFuture(world.getChunkAt(cx,cz))
                        : world.getChunkAtAsync(cx,cz,true);
                var pinned = new java.util.concurrent.CompletableFuture<Void>();
                ready.add(pinned);
                loaded.whenComplete((chunk, failure) -> api.tasks().nextTick(() -> {
                    if (failure != null) { pinned.completeExceptionally(failure); return; }
                    try {
                        if (enabled && (pendingTravel.containsValue(request) || preparingExits.containsValue(request))) {
                            if (!travelTickets.containsKey(chunk) && addTicket(chunk)) ownedTickets.add(chunk);
                            travelTickets.merge(chunk, 1, Integer::sum);
                            held.add(chunk);
                        }
                        pinned.complete(null);
                    } catch (RuntimeException error) { pinned.completeExceptionally(error); }
                }));
            }
        return java.util.concurrent.CompletableFuture.allOf(ready.toArray(java.util.concurrent.CompletableFuture[]::new));
    }
    boolean addTicket(Chunk chunk) { return chunk.addPluginChunkTicket(owner); }
    void removeTicket(Chunk chunk) { chunk.removePluginChunkTicket(owner); }
    private boolean cooling(Player player) {
        return pendingTravel.containsKey(player.getUniqueId()) || cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }
    private boolean outsideActivePortal(Location feet) {
        for (int dy = 0; dy <= 1; dy++) {
            Instance portal = interiorIndex.get(new BlockKey(feet.getWorld().getUID(),
                    new Offset(feet.getBlockX(), feet.getBlockY() + dy, feet.getBlockZ())));
            if (portal != null && portal.active) return false;
        }
        return true;
    }
    static Location safe(Location preferred) { return safe(preferred, location -> true, 16); }
    private static Location safe(Location preferred, java.util.function.Predicate<Location> allowed, int verticalRange) {
        World world = preferred.getWorld();
        for (int radius = 0; radius <= 4; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = preferred.getBlockX() + dx, z = preferred.getBlockZ() + dz;
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            for (int step = 0; step <= verticalRange * 2; step++) {
                int dy = step == 0 ? 0 : (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
                int y = preferred.getBlockY() + dy;
                if (y < world.getMinHeight() + 1 || y >= world.getMaxHeight() - 1) continue;
                Material floor = world.getBlockAt(x, y - 1, z).getType();
                if (floor.isSolid() && floor != Material.MAGMA_BLOCK && floor != Material.CACTUS
                        && floor != Material.CAMPFIRE && floor != Material.SOUL_CAMPFIRE
                        && world.getBlockAt(x, y, z).getType().isAir() && world.getBlockAt(x, y + 1, z).getType().isAir()) {
                    Location landing = new Location(world, x + .5, y, z + .5, preferred.getYaw(), preferred.getPitch());
                    if (allowed.test(landing)) return landing;
                }
            }
        }
        return null;
    }
    void tick() {
        if (!enabled) return;
        for (Instance portal : List.copyOf(instances.values())) {
            if (!allLoaded(portal)) continue;
            if (!valid(portal)) { deactivate(portal.id); continue; }
            if (portal.active && available(portal.type)) effect(portal, false);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (pendingTravel.containsKey(player.getUniqueId())) travelEffect(player, false);

        }
        if (++ticks % 5 == 0) save();
    }
    /** Client-only travel cue. Reuses the portal ticker and ends when its pending request is removed. */
    private static void travelEffect(Player player, boolean starting) {
        Location center = player.getLocation().add(0, 1, 0);
        player.spawnParticle(Particle.PORTAL, center, starting ? 70 : 35, .45, .65, .45, .12);
        if (starting) player.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, .55F, 1.2F);
    }
    private void effect(Instance portal, boolean activation) {
        World world = Bukkit.getWorld(portal.world);
        if (world == null) return;
        Location center = location(world, portal.at(portal.type.pattern().interior().getFirst())).add(.5, .5, .5);
        if (activation && (!portal.type.activeFrame().isEmpty()
                || portal.type.pattern().interiorMaterial() == Material.AIR && portal.type.lightLevel() > 0)) save();
        if (portal.active) frameEffect(portal, true);
        if (activation) world.playSound(center, portal.type.activateSound(), 1F, 1F);
        if (activation && portal.active && !portal.generated) api.tasks().nextTick(() -> prepareExit(portal));
        if (!activation && world.getNearbyPlayers(center, 48).isEmpty()) return;
        for (Offset cell : portal.type.pattern().interior())
            world.spawnParticle(portal.type.particle(), location(world, portal.at(cell)).add(.5, .5, .5), activation ? 8 : 2, .35, .35, .35, .01);
    }
    private void frameEffect(Instance portal, boolean activating) {
        World world = Bukkit.getWorld(portal.world);
        if (world == null) return;
        if (portal.type.pattern().interiorMaterial() == Material.AIR) {
            for (Offset cell : portal.type.pattern().interior()) {
                Block target = block(world, portal.at(cell));
                if (activating && portal.type.lightLevel() > 0 && (target.getType().isAir() || target.getType() == Material.LIGHT || reactorFire(portal, target.getType()))) {
                    var light = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
                    light.setLevel(portal.type.lightLevel());
                    if (!target.getBlockData().equals(light)) target.setBlockData(light, false);
                }
                else if ((!activating || portal.type.lightLevel() == 0)
                        && (target.getType() == Material.LIGHT || reactorFire(portal, target.getType()))) target.setType(Material.AIR, false);
            }
        }
        for (var cell : portal.type.pattern().cells()) {
            Material replacement = portal.type.activeFrame().get(cell.material());
            if (replacement == null || replacement == cell.material()) continue;
            // Activation was validated in loaded chunks. Deactivation restores surviving frame blocks,
            // including an adjacent frame chunk that may since have unloaded; it never rebuilds a broken block.
            Block target = block(world, portal.at(cell.offset()));
            if (target.getType() == (activating ? cell.material() : replacement))
                target.setType(activating ? replacement : cell.material(), false);
        }
    }
    public void save() {
        if (state == null || !dirty) return;
        state.set("instances", null);
        for (Instance portal : instances.values()) {
            String path = "instances." + portal.id;
            state.set(path + ".schema", schema(portal.type));
            state.set(path + ".type", portal.type.key().toString()); state.set(path + ".world", portal.world.toString());
            state.set(path + ".origin", portal.origin.x() + "," + portal.origin.y() + "," + portal.origin.z());
            state.set(path + ".rotation", portal.rotation); state.set(path + ".active", portal.active);
            state.set(path + ".charges", new ArrayList<>(portal.charges));
            state.set(path + ".world-name", portal.worldName);
            state.set(path + ".generated", portal.generated);
            state.set(path + ".partner", portal.partner == null ? null : portal.partner.toString());
        }
        state.set("returns", null); // Remove the obsolete per-player spawn-pad routing.
        state.set("format", 2);
        state.save(); dirty = false;
    }
    private void loadState() {
        ConfigSection section = state.getSection("instances", false);
        if (section != null) for (String id : section.getKeys(false)) {
            try {
                ConfigSection entry = section.getSection(id);
                SurvivalPortalType type = types.get(NamespacedKey.fromString(entry.getString("type")));
                if (type == null || !schema(type).equals(entry.getString("schema", ""))) continue;
                Instance portal = new Instance(UUID.fromString(id), UUID.fromString(entry.getString("world")), type,
                        PortalPattern.offset(entry.getString("origin")), Math.floorMod(entry.getInt("rotation"), 4));
                for (int charge : entry.getIntegerList("charges")) {
                    if (charge >= 0 && charge < type.pattern().cells().size()
                            && type.pattern().cells().get(charge).material() == type.chargeBlock()) portal.charges.add(charge);
                }
                portal.worldName = entry.getString("world-name", "");
                portal.generated = entry.getBoolean("generated", false);
                String partner = entry.getString("partner", "");
                if (!partner.isBlank()) portal.partner = UUID.fromString(partner);
                portal.active = entry.getBoolean("active", false);
                if (type.activation() == SurvivalPortalType.Activation.CHARGE_BLOCKS)
                    portal.active = portal.active && portal.charges.size() == type.pattern().cells().stream().filter(c -> c.material() == type.chargeBlock()).count();
                remember(portal);
            } catch (RuntimeException error) { warn("Ignoring invalid saved portal " + id); }
        }
        for (Instance portal : instances.values()) {
            Instance other = portal.partner == null ? null : instances.get(portal.partner);
            if (portal.partner != null && (other == null || !portal.id.equals(other.partner)
                    || !portal.type.key().equals(other.type.key()) || portal.generated == other.generated)) {
                portal.partner = null; dirty = true;
            }
        }
    }

    private static String schema(SurvivalPortalType type) {
        return type.pattern() + "/" + type.activation() + "/" + type.item() + "/" + type.generator();
    }
    private static boolean loaded(World world, Offset point) {
        return point.y() >= world.getMinHeight() && point.y() < world.getMaxHeight()
                && world.isChunkLoaded(Math.floorDiv(point.x(), 16), Math.floorDiv(point.z(), 16));
    }
    private static Offset offset(Block block) { return new Offset(block.getX(), block.getY(), block.getZ()); }
    private static BlockKey key(Block block) { return new BlockKey(block.getWorld().getUID(), offset(block)); }
    private static Block block(World world, Offset point) { return world.getBlockAt(point.x(), point.y(), point.z()); }
    private static Location location(World world, Offset point) { return new Location(world, point.x(), point.y(), point.z()); }
    private static void warn(String message) { STEMCraft.getPlugin().getLogger().warning(message); }
}
