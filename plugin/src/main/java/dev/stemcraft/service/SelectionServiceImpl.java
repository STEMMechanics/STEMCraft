package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.integration.worldedit.WorldEditRegionSupport;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared selection/highlight service used by plugin features, minigames, and third parties.
 */
public class SelectionServiceImpl extends BaseService implements SelectionService {
    private static final String TASK_ID = "selection-service-render";
    private static final long DEFAULT_UPDATE_TICKS = 6L;
    private static final double DEFAULT_POINT_SPACING = 2.0D;
    private static final int DEFAULT_MAX_POINTS = 180;
    private static final int DEFAULT_MAJOR_MARKER_INTERVAL = 5;
    private static final double DEFAULT_MAX_VIEW_DISTANCE = 96.0D;
    private static final double REGION_EDGE_OFFSET = 0.03D;
    private static final Material DEFAULT_FLASH_MATERIAL = Material.GLOWSTONE;
    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.fromRGB(255, 72, 72), 1.0f);

    private final Map<String, Highlight> highlights = new LinkedHashMap<>();

    private boolean worldEditPreviewEnabled;
    private long updateTicks;
    private double pointSpacing;
    private int maxPoints;
    private int majorMarkerInterval;
    private double maxViewDistance;
    private Material flashMaterial;

    public SelectionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    protected List<String> getConfigPathCandidates() {
        return List.of(
            "features.selection_preview",
            "features.selection-preview",
            "selection_preview",
            "selection-preview"
        );
    }

    @Override
    public void onEnable() {
        reloadConfig();
        registerCommands();
        start();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadConfig();
        start();
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(TASK_ID);
        clearHighlights("");
    }

    @Override
    public @Nullable SCRegion getWorldEditSelection(@NotNull Player player) {
        return WorldEditRegionSupport.getWESelection(player);
    }

    @Override
    public @Nullable SCRegion getWorldEditPreviewSelection(@NotNull Player player) {
        return WorldEditRegionSupport.getWEPreviewSelection(player);
    }

    @Override
    public @Nullable Location getWorldEditPrimaryPosition(@NotNull Player player) {
        return WorldEditRegionSupport.getWEPrimaryPosition(player);
    }

    @Override
    public void setWorldEditSelection(@NotNull Player player, @NotNull SCRegion region) {
        WorldEditRegionSupport.setWESelection(player, region);
    }

    @Override
    public void setWorldEditSelection(@NotNull Player player, @NotNull Location location) {
        WorldEditRegionSupport.setWESelection(player, location);
    }

    @Override
    public void clearWorldEditSelection(@NotNull Player player) {
        WorldEditRegionSupport.clearWESelection(player);
    }

    @Override
    public void showRegion(@NotNull Player viewer, @NotNull SCRegion region) {
        if (!canRenderRegion(viewer, region)) {
            return;
        }
        renderRegion(viewer, region);
    }

    @Override
    public void showRegion(@NotNull World world, @NotNull SCRegion region) {
        if (region.getWorld() == null || !region.getWorld().equals(world)) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (!shouldRender(player) || !isNearSelection(player.getLocation(), region, maxViewDistance)) {
                continue;
            }
            renderRegion(player, region);
        }
    }

    @Override
    public void showLocation(@NotNull Player viewer, @NotNull Location location) {
        if (!canRenderLocation(viewer, location)) {
            return;
        }
        renderLocation(viewer, location);
    }

    @Override
    public void showLocation(@NotNull World world, @NotNull Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(world)) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (!shouldRender(player) || !isNearLocation(player.getLocation(), location, maxViewDistance)) {
                continue;
            }
            renderLocation(player, location);
        }
    }

    @Override
    public void highlightRegion(@NotNull String id, @NotNull Player viewer, @NotNull SCRegion region, long durationTicks) {
        registerHighlight(id, Highlight.forPlayerRegion(viewer, region, expiresAt(durationTicks)));
    }

    @Override
    public void highlightRegion(@NotNull String id, @NotNull World world, @NotNull SCRegion region, long durationTicks) {
        registerHighlight(id, Highlight.forWorldRegion(world, region, expiresAt(durationTicks)));
    }

    @Override
    public void highlightLocation(@NotNull String id, @NotNull Player viewer, @NotNull Location location, long durationTicks) {
        registerHighlight(id, Highlight.forPlayerLocation(viewer, location, expiresAt(durationTicks)));
    }

    @Override
    public void highlightLocation(@NotNull String id, @NotNull World world, @NotNull Location location, long durationTicks) {
        registerHighlight(id, Highlight.forWorldLocation(world, location, expiresAt(durationTicks)));
    }

    @Override
    public void flashBlock(@NotNull String id, @NotNull Player viewer, @NotNull Location location, long durationTicks) {
        registerHighlight(id, Highlight.forPlayerBlock(viewer, location, expiresAt(durationTicks)));
    }

    @Override
    public void clearHighlight(@NotNull String id) {
        Highlight removed = highlights.remove(id);
        if (removed != null) {
            restoreHighlight(removed);
        }
    }

    @Override
    public void clearHighlights(@NotNull String prefix) {
        List<String> ids = new ArrayList<>();
        for (String id : highlights.keySet()) {
            if (prefix.isEmpty() || id.startsWith(prefix)) {
                ids.add(id);
            }
        }

        for (String id : ids) {
            clearHighlight(id);
        }
    }

    private void reloadConfig() {
        ConfigSection section = getConfigSection();
        boolean changed = false;

        changed |= ensureDefault(section, "enabled", true);
        changed |= ensureDefault(section, "update_ticks", DEFAULT_UPDATE_TICKS);
        changed |= ensureDefault(section, "point_spacing", DEFAULT_POINT_SPACING);
        changed |= ensureDefault(section, "max_points", DEFAULT_MAX_POINTS);
        changed |= ensureDefault(section, "major_marker_interval", DEFAULT_MAJOR_MARKER_INTERVAL);
        changed |= ensureDefault(section, "max_view_distance", DEFAULT_MAX_VIEW_DISTANCE);
        changed |= ensureDefault(section, "flash_material", DEFAULT_FLASH_MATERIAL.name());

        if (changed) {
            section.save();
        }

        worldEditPreviewEnabled = section.getBoolean("enabled", true);
        updateTicks = Math.max(1L, section.getLong("update_ticks", DEFAULT_UPDATE_TICKS));
        pointSpacing = Math.max(1.0D, section.getDouble("point_spacing", DEFAULT_POINT_SPACING));
        maxPoints = Math.max(16, section.getInt("max_points", DEFAULT_MAX_POINTS));
        majorMarkerInterval = Math.max(2, section.getInt("major_marker_interval", DEFAULT_MAJOR_MARKER_INTERVAL));
        maxViewDistance = Math.max(8.0D, section.getDouble("max_view_distance", DEFAULT_MAX_VIEW_DISTANCE));
        flashMaterial = parseFlashMaterial(section.getString("flash_material", DEFAULT_FLASH_MATERIAL.name()));
    }

    private void registerCommands() {
        api.commands().create("clearsel")
            .permission("stemcraft.command.clearsel")
            .usage("/clearsel [player]")
            .tabCompletion("{player}")
            .executor((ignored, cmd, ctx) -> {
                Player target = ctx.getArgAsPlayerOrSender(0);
                if (target == null) {
                    ctx.returnError("Specify a player when using this command from console.");
                }

                clearWorldEditSelection(target);
                ctx.success("Cleared WorldEdit selection for '" + target.getName() + "'.");
            })
            .register(plugin);
    }

    private void start() {
        api.tasks().cancel(TASK_ID);
        api.tasks().repeating(TASK_ID, 1L, updateTicks, this::tick);
    }

    private void tick() {
        if (worldEditPreviewEnabled) {
            renderWorldEditSelections();
        }
        renderHighlights();
    }

    private void renderWorldEditSelections() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldRender(player)) {
                continue;
            }

            SCRegion selection = getWorldEditPreviewSelection(player);
            if (canRenderRegion(player, selection) && isNearSelection(player.getLocation(), selection, maxViewDistance)) {
                renderRegion(player, selection);
                continue;
            }

            Location primary = getWorldEditPrimaryPosition(player);
            if (!canRenderLocation(player, primary) || !isNearLocation(player.getLocation(), primary, maxViewDistance)) {
                continue;
            }
            renderLocation(player, primary);
        }
    }

    private void renderHighlights() {
        if (highlights.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<String> expiredIds = new ArrayList<>();

        for (Map.Entry<String, Highlight> entry : highlights.entrySet()) {
            Highlight highlight = entry.getValue();
            if (highlight.isExpired(now) || !renderHighlight(highlight)) {
                expiredIds.add(entry.getKey());
                continue;
            }
            highlight.phase++;
        }

        for (String id : expiredIds) {
            clearHighlight(id);
        }
    }

    private boolean renderHighlight(Highlight highlight) {
        return switch (highlight.audienceType) {
            case PLAYER -> renderHighlightForPlayer(highlight);
            case WORLD -> renderHighlightForWorld(highlight);
        };
    }

    private boolean renderHighlightForPlayer(Highlight highlight) {
        if (highlight.viewerId == null || highlight.region == null) {
            return false;
        }

        Player player = Bukkit.getPlayer(highlight.viewerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!shouldRender(player)) {
            return true;
        }

        switch (highlight.kind) {
            case REGION -> {
                if (canRenderRegion(player, highlight.region) && isNearSelection(player.getLocation(), highlight.region, maxViewDistance)) {
                    renderRegion(player, highlight.region);
                }
            }
            case LOCATION -> {
                if (canRenderLocation(player, highlight.location) && isNearLocation(player.getLocation(), highlight.location, maxViewDistance)) {
                    renderLocation(player, highlight.location);
                }
            }
            case BLOCK -> {
                if (canRenderLocation(player, highlight.location) && isNearLocation(player.getLocation(), highlight.location, maxViewDistance)) {
                    renderFlashingBlock(player, highlight.location, highlight.phase);
                }
            }
        }
        return true;
    }

    private boolean renderHighlightForWorld(Highlight highlight) {
        if (highlight.worldName == null) {
            return false;
        }

        World world = Bukkit.getWorld(highlight.worldName);
        if (world == null) {
            return false;
        }

        for (Player player : world.getPlayers()) {
            if (!shouldRender(player)) {
                continue;
            }

            switch (highlight.kind) {
                case REGION -> {
                    if (canRenderRegion(player, highlight.region) && isNearSelection(player.getLocation(), highlight.region, maxViewDistance)) {
                        renderRegion(player, highlight.region);
                    }
                }
                case LOCATION -> {
                    if (canRenderLocation(player, highlight.location) && isNearLocation(player.getLocation(), highlight.location, maxViewDistance)) {
                        renderLocation(player, highlight.location);
                    }
                }
                case BLOCK -> {
                    // World-visible block flashing is intentionally not supported because that would require mutating the world.
                }
            }
        }

        return true;
    }

    private void registerHighlight(@NotNull String id, @NotNull Highlight highlight) {
        if (id.isBlank()) {
            return;
        }

        clearHighlight(id);
        highlights.put(id, highlight);
    }

    private long expiresAt(long durationTicks) {
        if (durationTicks < 0L) {
            return -1L;
        }
        return System.currentTimeMillis() + (durationTicks * 50L);
    }

    private boolean shouldRender(Player player) {
        return player.isOnline() && !player.isDead();
    }

    private boolean canRenderRegion(Player player, SCRegion region) {
        return region != null && region.getWorld() != null && region.getWorld().equals(player.getWorld());
    }

    private boolean canRenderLocation(Player player, Location location) {
        return location != null && location.getWorld() != null && location.getWorld().equals(player.getWorld());
    }

    private boolean isNearSelection(Location playerLocation, SCRegion region, double maxDistance) {
        World world = region.getWorld();
        if (world == null || !world.equals(playerLocation.getWorld())) {
            return false;
        }

        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();

        double dx = axisDistance(playerLocation.getX(), min.getX(), max.getX() + 1.0D);
        double dy = axisDistance(playerLocation.getY(), min.getY(), max.getY() + 1.0D);
        double dz = axisDistance(playerLocation.getZ(), min.getZ(), max.getZ() + 1.0D);

        return ((dx * dx) + (dy * dy) + (dz * dz)) <= (maxDistance * maxDistance);
    }

    private boolean isNearLocation(Location playerLocation, Location location, double maxDistance) {
        if (location.getWorld() == null || !location.getWorld().equals(playerLocation.getWorld())) {
            return false;
        }
        return playerLocation.distanceSquared(location) <= (maxDistance * maxDistance);
    }

    private double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private void restoreHighlight(Highlight highlight) {
        if (highlight.kind != HighlightKind.BLOCK || highlight.viewerId == null || highlight.location == null) {
            return;
        }

        Player player = Bukkit.getPlayer(highlight.viewerId);
        if (player == null || !player.isOnline() || !canRenderLocation(player, highlight.location)) {
            return;
        }

        Location blockLocation = highlight.location.getBlock().getLocation();
        player.sendBlockChange(blockLocation, blockLocation.getBlock().getBlockData());
    }

    private Material parseFlashMaterial(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_FLASH_MATERIAL;
        }

        Material material = Material.matchMaterial(value.trim());
        if (material == null || !material.isBlock()) {
            return DEFAULT_FLASH_MATERIAL;
        }

        return material;
    }

    private boolean ensureDefault(ConfigSection section, String path, Object value) {
        if (section.contains(path)) {
            return false;
        }
        section.set(path, value);
        return true;
    }

    private void renderLocation(Player player, Location location) {
        if (!canRenderLocation(player, location)) {
            return;
        }

        Location center = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
        double[] yOffsets = {0.05D, 0.55D, 1.05D};
        double[][] corners = {
            {-0.35D, -0.35D},
            {-0.35D, 0.35D},
            {0.35D, -0.35D},
            {0.35D, 0.35D}
        };

        for (double yOffset : yOffsets) {
            for (double[] corner : corners) {
                spawnDust(player, center.clone().add(corner[0], yOffset, corner[1]));
            }
        }

        spawnFlame(player, center.clone().add(0.0D, 0.1D, 0.0D));
        spawnFlame(player, center.clone().add(0.0D, 1.1D, 0.0D));
    }

    private void renderFlashingBlock(Player player, Location location, int phase) {
        Location blockLocation = location.getBlock().getLocation();
        BlockData blockData = (phase % 2 == 0) ? flashMaterial.createBlockData() : blockLocation.getBlock().getBlockData();
        player.sendBlockChange(blockLocation, blockData);
        renderLocation(player, location);
    }

    private void renderRegion(Player player, SCRegion region) {
        if (!canRenderRegion(player, region)) {
            return;
        }

        renderCorners(player, region);

        if (region.isCuboid()) {
            renderCuboid(player, region);
            return;
        }

        if (region.isPolygon()) {
            renderPolygon(player, region);
        }
    }

    private void renderCorners(Player player, SCRegion region) {
        CuboidBounds bounds = outerBounds(region);
        Location min = bounds.min();
        Location max = bounds.max();

        List<Location> corners = List.of(
            point(min.getWorld(), min.getX(), min.getY(), min.getZ()),
            point(min.getWorld(), min.getX(), min.getY(), max.getZ()),
            point(min.getWorld(), min.getX(), max.getY(), min.getZ()),
            point(min.getWorld(), min.getX(), max.getY(), max.getZ()),
            point(min.getWorld(), max.getX(), min.getY(), min.getZ()),
            point(min.getWorld(), max.getX(), min.getY(), max.getZ()),
            point(min.getWorld(), max.getX(), max.getY(), min.getZ()),
            point(min.getWorld(), max.getX(), max.getY(), max.getZ())
        );

        for (Location corner : corners) {
            spawnFlame(player, corner);
        }
    }

    private void renderCuboid(Player player, SCRegion region) {
        CuboidBounds bounds = outerBounds(region);
        List<Line> lines = cuboidLines(bounds);
        renderLines(player, lines);
        renderMajorMarkers(player, lines);
    }

    private void renderPolygon(Player player, SCRegion region) {
        World world = region.getWorld();
        List<Location> points = region.getPolygonVertices();
        if (points.isEmpty()) {
            return;
        }

        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        double bottomY = min.getBlockY() - REGION_EDGE_OFFSET;
        double topY = max.getBlockY() + 1.0D + REGION_EDGE_OFFSET;
        List<Line> lines = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            Location current = points.get(i);
            Location next = points.get((i + 1) % points.size());

            Location bottomA = polygonVertexPoint(world, current, bottomY);
            Location bottomB = polygonVertexPoint(world, next, bottomY);
            Location topA = polygonVertexPoint(world, current, topY);
            Location topB = polygonVertexPoint(world, next, topY);

            lines.add(line(bottomA, bottomB));
            lines.add(line(topA, topB));
            lines.add(line(bottomA, topA));

            spawnFlame(player, bottomA);
            spawnFlame(player, topA);
        }

        renderLines(player, lines);
        renderMajorMarkers(player, lines);
    }

    private List<Line> cuboidLines(CuboidBounds bounds) {
        Location min = bounds.min();
        Location max = bounds.max();
        World world = min.getWorld();

        return List.of(
            line(point(world, min.getX(), min.getY(), min.getZ()), point(world, max.getX(), min.getY(), min.getZ())),
            line(point(world, min.getX(), min.getY(), min.getZ()), point(world, min.getX(), max.getY(), min.getZ())),
            line(point(world, min.getX(), min.getY(), min.getZ()), point(world, min.getX(), min.getY(), max.getZ())),
            line(point(world, max.getX(), max.getY(), max.getZ()), point(world, min.getX(), max.getY(), max.getZ())),
            line(point(world, max.getX(), max.getY(), max.getZ()), point(world, max.getX(), min.getY(), max.getZ())),
            line(point(world, max.getX(), max.getY(), max.getZ()), point(world, max.getX(), max.getY(), min.getZ())),
            line(point(world, max.getX(), min.getY(), min.getZ()), point(world, max.getX(), max.getY(), min.getZ())),
            line(point(world, max.getX(), min.getY(), min.getZ()), point(world, max.getX(), min.getY(), max.getZ())),
            line(point(world, min.getX(), max.getY(), min.getZ()), point(world, max.getX(), max.getY(), min.getZ())),
            line(point(world, min.getX(), max.getY(), min.getZ()), point(world, min.getX(), max.getY(), max.getZ())),
            line(point(world, min.getX(), min.getY(), max.getZ()), point(world, max.getX(), min.getY(), max.getZ())),
            line(point(world, min.getX(), min.getY(), max.getZ()), point(world, min.getX(), max.getY(), max.getZ()))
        );
    }

    private void renderLines(Player player, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }

        double totalLength = lines.stream().mapToDouble(Line::length).sum();
        if (totalLength <= 0.0D) {
            return;
        }

        int totalBudget = Math.max(1, maxPoints);
        for (Line line : lines) {
            int budget = Math.max(2, (int) Math.round((line.length() / totalLength) * totalBudget));
            renderLine(player, line, budget);
        }
    }

    private void renderLine(Player player, Line line, int lineMaxPoints) {
        double length = line.length();
        if (length <= 0.0D) {
            spawnDust(player, line.start());
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(length / pointSpacing));
        steps = Math.clamp(lineMaxPoints, 1, steps);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double x = line.start().getX() + ((line.end().getX() - line.start().getX()) * t);
            double y = line.start().getY() + ((line.end().getY() - line.start().getY()) * t);
            double z = line.start().getZ() + ((line.end().getZ() - line.start().getZ()) * t);
            spawnDust(player, point(line.start().getWorld(), x, y, z));
        }
    }

    private void renderMajorMarkers(Player player, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }

        Map<String, Location> markers = new LinkedHashMap<>();
        for (Line line : lines) {
            for (Location marker : lineMarkers(line, majorMarkerInterval)) {
                markers.put(locationKey(marker), marker);
            }
        }

        if (markers.isEmpty()) {
            return;
        }

        List<Location> ordered = new ArrayList<>(markers.values());
        int markerBudget = Math.max(64, maxPoints * 2);
        if (ordered.size() <= markerBudget) {
            for (Location location : ordered) {
                spawnFlame(player, location);
            }
            return;
        }

        double stride = (double) ordered.size() / (double) markerBudget;
        for (int i = 0; i < markerBudget; i++) {
            int index = Math.min(ordered.size() - 1, (int) Math.floor(i * stride));
            spawnFlame(player, ordered.get(index));
        }
    }

    private List<Location> lineMarkers(Line line, double spacing) {
        List<Location> markers = new ArrayList<>();
        double length = line.length();
        if (length <= 0.0D) {
            markers.add(line.start());
            return markers;
        }

        int steps = Math.max(1, (int) Math.floor(length / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double x = line.start().getX() + ((line.end().getX() - line.start().getX()) * t);
            double y = line.start().getY() + ((line.end().getY() - line.start().getY()) * t);
            double z = line.start().getZ() + ((line.end().getZ() - line.start().getZ()) * t);
            markers.add(point(line.start().getWorld(), x, y, z));
        }

        return markers;
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ();
    }

    private void spawnDust(Player player, Location location) {
        player.spawnParticle(Particle.DUST, location.getX(), location.getY(), location.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D, RED_DUST);
    }

    private void spawnFlame(Player player, Location location) {
        player.spawnParticle(Particle.FLAME, location.getX(), location.getY(), location.getZ(), 1, 0.02D, 0.02D, 0.02D, 0.0D);
    }

    private Location point(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private CuboidBounds outerBounds(SCRegion region) {
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        World world = min.getWorld();

        return new CuboidBounds(
            point(world,
                min.getBlockX() - REGION_EDGE_OFFSET,
                min.getBlockY() - REGION_EDGE_OFFSET,
                min.getBlockZ() - REGION_EDGE_OFFSET),
            point(world,
                max.getBlockX() + 1.0D + REGION_EDGE_OFFSET,
                max.getBlockY() + 1.0D + REGION_EDGE_OFFSET,
                max.getBlockZ() + 1.0D + REGION_EDGE_OFFSET)
        );
    }

    private Location polygonVertexPoint(World world, Location vertex, double y) {
        return point(world, vertex.getX() - 0.5D, y, vertex.getZ() - 0.5D);
    }

    private Line line(Location start, Location end) {
        return new Line(start, end);
    }

    private enum AudienceType {
        PLAYER,
        WORLD
    }

    private enum HighlightKind {
        REGION,
        LOCATION,
        BLOCK
    }

    private static final class Highlight {
        private final AudienceType audienceType;
        private final HighlightKind kind;
        private final UUID viewerId;
        private final String worldName;
        private final SCRegion region;
        private final Location location;
        private final long expiresAt;
        private int phase;

        private Highlight(AudienceType audienceType, HighlightKind kind, UUID viewerId, String worldName, SCRegion region, Location location, long expiresAt) {
            this.audienceType = audienceType;
            this.kind = kind;
            this.viewerId = viewerId;
            this.worldName = worldName;
            this.region = region;
            this.location = location;
            this.expiresAt = expiresAt;
        }

        private static Highlight forPlayerRegion(Player viewer, SCRegion region, long expiresAt) {
            return new Highlight(AudienceType.PLAYER, HighlightKind.REGION, viewer.getUniqueId(), null, region.copy(), null, expiresAt);
        }

        private static Highlight forWorldRegion(World world, SCRegion region, long expiresAt) {
            return new Highlight(AudienceType.WORLD, HighlightKind.REGION, null, world.getName(), region.copy(), null, expiresAt);
        }

        private static Highlight forPlayerLocation(Player viewer, Location location, long expiresAt) {
            return new Highlight(AudienceType.PLAYER, HighlightKind.LOCATION, viewer.getUniqueId(), null, null, location.clone(), expiresAt);
        }

        private static Highlight forWorldLocation(World world, Location location, long expiresAt) {
            return new Highlight(AudienceType.WORLD, HighlightKind.LOCATION, null, world.getName(), null, location.clone(), expiresAt);
        }

        private static Highlight forPlayerBlock(Player viewer, Location location, long expiresAt) {
            return new Highlight(AudienceType.PLAYER, HighlightKind.BLOCK, viewer.getUniqueId(), null, null, location.clone(), expiresAt);
        }

        private boolean isExpired(long now) {
            return expiresAt >= 0L && now >= expiresAt;
        }
    }

    private record Line(Location start, Location end) {
        private double length() {
            return start.distance(end);
        }
    }

    private record CuboidBounds(Location min, Location max) { }
}
