package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.imagemap.ImageMapDisplay;
import dev.stemcraft.api.service.imagemap.ImageMapClick;
import dev.stemcraft.api.service.imagemap.ImageMapService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Runtime implementation of wall-mounted image-map mosaics. */
public final class ImageMapServiceImpl extends BaseService implements ImageMapService {
    private static final String ENTITY_TAG = "stemcraft:image-map";
    private final Map<String, ManagedDisplay> displays = new HashMap<>();
    private final Map<String, Consumer<ImageMapClick>> clickHandlers = new HashMap<>();

    public ImageMapServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEntityEvent.class, event -> {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            UUID clickedId = event.getRightClicked().getUniqueId();
            for (ManagedDisplay display : displays.values()) {
                int index = display.frameIds.indexOf(clickedId);
                if (index < 0) {
                    continue;
                }
                event.setCancelled(true);
                Consumer<ImageMapClick> callback = clickHandlers.get(display.definition.id());
                if (callback != null) {
                    int column = index % display.definition.columns();
                    int row = index / display.definition.columns();
                    callback.accept(new ImageMapClick(display.definition.id(), event.getPlayer(), column, row));
                }
                return;
            }
        });
    }

    @Override
    public void onDisable() {
        displays.values().forEach(this::removeFrames);
        displays.clear();
        clickHandlers.clear();
    }

    @Override
    public @NotNull ImageMapDisplay create(@NotNull String id,
                                            @NotNull Location backingBlock,
                                            @NotNull BlockFace facing,
                                            int columns,
                                            int rows) {
        if (!List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST).contains(facing)) {
            throw new IllegalArgumentException("Image maps must face north, south, east, or west");
        }
        if (backingBlock.getWorld() == null) {
            throw new IllegalArgumentException("The backing block must have a world");
        }
        delete(id);
        ImageMapDisplay definition = new ImageMapDisplay(id, backingBlock.toBlockLocation(), facing, columns, rows);
        ManagedDisplay managed = new ManagedDisplay(definition, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        displays.put(id, managed);
        spawnFrames(managed);
        return definition;
    }

    @Override
    public boolean render(@NotNull String id, @NotNull BufferedImage image) {
        ManagedDisplay display = displays.get(id);
        if (display == null) {
            return false;
        }
        ensureFrames(display);
        int width = display.definition.columns() * 128;
        int height = display.definition.rows() * 128;
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();

        World world = display.definition.backingBlock().getWorld();
        if (world == null) {
            return false;
        }
        for (int row = 0; row < display.definition.rows(); row++) {
            for (int column = 0; column < display.definition.columns(); column++) {
                int sourceY = (display.definition.rows() - row - 1) * 128;
                BufferedImage tile = scaled.getSubimage(column * 128, sourceY, 128, 128);
                int index = row * display.definition.columns() + column;
                if (display.views.size() <= index) {
                    MapView view = Bukkit.createMap(world);
                    view.getRenderers().forEach(view::removeRenderer);
                    ImageRenderer renderer = new ImageRenderer(tile);
                    view.addRenderer(renderer);
                    view.setTrackingPosition(false);
                    view.setUnlimitedTracking(false);
                    display.views.add(view);
                    display.renderers.add(renderer);
                    ItemStack map = new ItemStack(Material.FILLED_MAP);
                    MapMeta meta = (MapMeta) map.getItemMeta();
                    meta.setMapView(view);
                    map.setItemMeta(meta);
                    Entity entity = Bukkit.getEntity(display.frameIds.get(index));
                    if (entity instanceof GlowItemFrame frame) {
                        frame.setItem(map, false);
                    }
                } else {
                    display.renderers.get(index).setImage(tile);
                    for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMap(display.views.get(index));
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean delete(@NotNull String id) {
        ManagedDisplay removed = displays.remove(id);
        if (removed == null) {
            return false;
        }
        removeFrames(removed);
        clickHandlers.remove(id);
        return true;
    }

    @Override
    public boolean exists(@NotNull String id) {
        return displays.containsKey(id);
    }

    @Override
    public void onClick(@NotNull String id, @NotNull Consumer<ImageMapClick> callback) {
        if (!displays.containsKey(id)) {
            throw new IllegalArgumentException("Unknown image-map display: " + id);
        }
        clickHandlers.put(id, callback);
    }

    @Override
    public void clearClickHandler(@NotNull String id) {
        clickHandlers.remove(id);
    }

    private void ensureFrames(ManagedDisplay display) {
        if (display.frameIds.size() != display.definition.columns() * display.definition.rows()
            || display.frameIds.stream().anyMatch(id -> Bukkit.getEntity(id) == null)) {
            removeFrames(display);
            display.frameIds.clear();
            spawnFrames(display);
        }
    }

    private void spawnFrames(ManagedDisplay display) {
        ImageMapDisplay definition = display.definition;
        World world = definition.backingBlock().getWorld();
        if (world == null) {
            return;
        }
        definition.backingBlock().getChunk().load();
        removeTaggedFramesNear(definition);
        int rightX = definition.facing().getModZ();
        int rightZ = -definition.facing().getModX();
        for (int row = 0; row < definition.rows(); row++) {
            for (int column = 0; column < definition.columns(); column++) {
                Location backing = definition.backingBlock().clone().add(rightX * column, row, rightZ * column);
                Location spawn = backing.clone().add(0.5, 0.5, 0.5)
                    .add(definition.facing().getDirection().multiply(0.501));
                GlowItemFrame frame = world.spawn(spawn, GlowItemFrame.class, entity -> {
                    entity.setFacingDirection(definition.facing(), true);
                    entity.setFixed(true);
                    entity.setPersistent(true);
                    entity.setInvulnerable(true);
                    entity.addScoreboardTag(ENTITY_TAG);
                    entity.addScoreboardTag("stemcraft:image-map:" + safeTag(definition.id()));
                });
                display.frameIds.add(frame.getUniqueId());
            }
        }
    }

    private void removeFrames(ManagedDisplay display) {
        for (UUID id : display.frameIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void removeTaggedFramesNear(ImageMapDisplay definition) {
        int search = Math.max(definition.columns(), definition.rows()) + 2;
        for (Entity entity : definition.backingBlock().getWorld().getNearbyEntities(
            definition.backingBlock().clone().add(0.5, definition.rows() / 2.0, 0.5), search, search, search)) {
            if (entity.getScoreboardTags().contains(ENTITY_TAG)
                && entity.getScoreboardTags().contains("stemcraft:image-map:" + safeTag(definition.id()))) {
                entity.remove();
            }
        }
    }

    private static String safeTag(String id) {
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static final class ImageRenderer extends MapRenderer {
        private BufferedImage image;
        private boolean rendered;

        private ImageRenderer(BufferedImage image) {
            super(false);
            this.image = image;
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            this.rendered = false;
        }

        @Override
        public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull org.bukkit.entity.Player player) {
            if (!rendered) {
                canvas.drawImage(0, 0, image);
                rendered = true;
            }
        }
    }

    private record ManagedDisplay(ImageMapDisplay definition,
                                  List<UUID> frameIds,
                                  List<MapView> views,
                                  List<ImageRenderer> renderers) { }
}
