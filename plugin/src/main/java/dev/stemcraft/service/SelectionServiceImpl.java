package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.integration.worldedit.WorldEditRegionSupport;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.selection.SelectionService;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Region;
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
    private static final double DEFAULT_SURFACE_POINT_SPACING = 3.0D;
    private static final int DEFAULT_MAX_SURFACE_POINTS = 8000;
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
    private double surfacePointSpacing;
    private int maxSurfacePoints;
    private double maxViewDistance;
    private Material flashMaterial;
    private Particle selectionParticle = Particle.DUST;
    private Particle cornerParticle = Particle.FLAME;
    private boolean advancedGridEnabled;
    private double gridPointSpacing;
    private long maxSelectionSize;

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
        changed |= ensureDefault(section, "flash_material", DEFAULT_FLASH_MATERIAL.name());
        changed |= ensureDefault(section, "particle", "DUST");
        changed |= ensureDefault(section, "corner-particle", "FLAME");
        changed |= ensureDefault(section, "particles-per-block", 2.0D);
        changed |= ensureDefault(section, "major-marker-interval", DEFAULT_MAJOR_MARKER_INTERVAL);
        changed |= ensureDefault(section, "particle-send-interval", 6L);
        changed |= ensureDefault(section, "particle-viewdistance", 96.0D);
        changed |= ensureDefault(section, "max-selection-size-to-display", 10_000_000L);
        changed |= ensureDefault(section, "advanced-grid.enabled", true);
        changed |= ensureDefault(section, "advanced-grid.spacing", 3.0D);
        changed |= ensureDefault(section, "advanced-grid.max-points", DEFAULT_MAX_SURFACE_POINTS);

        if (changed) {
            section.save();
        }

        worldEditPreviewEnabled = section.getBoolean("enabled", true);
        updateTicks = Math.max(1L, section.getLong("particle-send-interval", section.getLong("update_ticks", DEFAULT_UPDATE_TICKS)));
        double particlesPerBlock = Math.clamp(section.getDouble("particles-per-block", 2.0D), 0.25D, 5.0D);
        pointSpacing = 1.0D / particlesPerBlock;
        maxPoints = Math.max(16, section.getInt("max_points", DEFAULT_MAX_POINTS));
        majorMarkerInterval = Math.max(2, section.getInt("major-marker-interval",
            section.getInt("major_marker_interval", DEFAULT_MAJOR_MARKER_INTERVAL)));
        advancedGridEnabled = section.getBoolean("advanced-grid.enabled", true);
        gridPointSpacing = Math.max(1.0D, section.getDouble("advanced-grid.spacing", DEFAULT_SURFACE_POINT_SPACING));
        surfacePointSpacing = gridPointSpacing;
        maxSurfacePoints = Math.max(128, section.getInt("advanced-grid.max-points", section.getInt("max_surface_points", DEFAULT_MAX_SURFACE_POINTS)));
        maxViewDistance = Math.max(8.0D, section.getDouble("particle-viewdistance", section.getDouble("max_view_distance", DEFAULT_MAX_VIEW_DISTANCE)));
        maxSelectionSize = Math.max(1L, section.getLong("max-selection-size-to-display", 10_000_000L));
        selectionParticle = parseParticle(section.getString("particle", "DUST"), Particle.DUST);
        cornerParticle = parseParticle(section.getString("corner-particle", "FLAME"), Particle.FLAME);
        flashMaterial = parseFlashMaterial(section.getString("flash_material", DEFAULT_FLASH_MATERIAL.name()));
    }

    private Particle parseParticle(String value, Particle fallback) {
        if (value == null) return fallback;
        try { return Particle.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace("REDSTONE", "DUST")); }
        catch (IllegalArgumentException ignored) { return fallback; }
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

        Region worldEditRegion = region.getRegion();
        if (worldEditRegion.getVolume() > maxSelectionSize) return;
        if (region.isCuboid()) {
            renderCorners(player, region);
            renderCuboid(player, region);
            return;
        }
        if (worldEditRegion instanceof EllipsoidRegion ellipsoid) {
            renderEllipsoid(player, region.getWorld(), ellipsoid);
            return;
        }
        if (worldEditRegion instanceof CylinderRegion cylinder) {
            renderCylinder(player, region.getWorld(), cylinder);
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
        if (advancedGridEnabled) renderCuboidExteriorGrid(player, bounds);
    }

    private void renderCuboidExteriorGrid(Player player, CuboidBounds bounds) {
        Location min = bounds.min();
        Location max = bounds.max();
        World world = min.getWorld();
        List<Double> xs = axisPoints(min.getX(), max.getX(), majorMarkerInterval);
        List<Double> ys = axisPoints(min.getY(), max.getY(), majorMarkerInterval);
        List<Double> zs = axisPoints(min.getZ(), max.getZ(), majorMarkerInterval);
        List<Line> gridLines = new ArrayList<>();

        for (double z : List.of(min.getZ(), max.getZ())) {
            for (double x : xs) gridLines.add(line(point(world, x, min.getY(), z), point(world, x, max.getY(), z)));
            for (double y : ys) gridLines.add(line(point(world, min.getX(), y, z), point(world, max.getX(), y, z)));
        }
        for (double y : List.of(min.getY(), max.getY())) {
            for (double x : xs) gridLines.add(line(point(world, x, y, min.getZ()), point(world, x, y, max.getZ())));
            for (double z : zs) gridLines.add(line(point(world, min.getX(), y, z), point(world, max.getX(), y, z)));
        }
        for (double x : List.of(min.getX(), max.getX())) {
            for (double y : ys) gridLines.add(line(point(world, x, y, min.getZ()), point(world, x, y, max.getZ())));
            for (double z : zs) gridLines.add(line(point(world, x, min.getY(), z), point(world, x, max.getY(), z)));
        }

        Map<String, Location> dust = new LinkedHashMap<>();
        Map<String, Location> flames = new LinkedHashMap<>();
        for (Line gridLine : gridLines) {
            addLinePoints(dust, gridLine, pointSpacing);
            for (Location marker : lineMarkers(gridLine, majorMarkerInterval)) {
                flames.putIfAbsent(locationKey(marker), marker);
            }
        }
        renderSurfacePoints(player, new ArrayList<>(dust.values()));
        renderFlamePoints(player, new ArrayList<>(flames.values()));
    }

    private void addLinePoints(Map<String, Location> points, Line line, double spacing) {
        int steps = Math.max(1, (int) Math.ceil(line.length() / spacing));
        for (int index = 0; index <= steps; index++) {
            if (isMajorMarkerStep(line, index, steps)) continue;
            double factor = index / (double) steps;
            Location location = point(line.start().getWorld(),
                line.start().getX() + (line.end().getX() - line.start().getX()) * factor,
                line.start().getY() + (line.end().getY() - line.start().getY()) * factor,
                line.start().getZ() + (line.end().getZ() - line.start().getZ()) * factor);
            points.putIfAbsent(locationKey(location), location);
        }
    }

    private void renderFlamePoints(Player player, List<Location> points) {
        if (points.size() <= maxSurfacePoints) {
            points.forEach(location -> spawnFlame(player, location));
            return;
        }
        double stride = (double) points.size() / maxSurfacePoints;
        for (int index = 0; index < maxSurfacePoints; index++) {
            spawnFlame(player, points.get(Math.min(points.size() - 1, (int) Math.floor(index * stride))));
        }
    }

    private void renderEllipsoid(Player player, World world, EllipsoidRegion region) {
        Vector3 center = region.getCenter();
        Vector3 radius = region.getRadius();
        List<Location> outline = new ArrayList<>();
        addEllipse(outline, world, center.x(), center.y(), center.z(), radius.x() + 0.5D, radius.z() + 0.5D, 0);
        addEllipse(outline, world, center.x(), center.y(), center.z(), radius.x() + 0.5D, radius.y() + 0.5D, 1);
        addEllipse(outline, world, center.x(), center.y(), center.z(), radius.z() + 0.5D, radius.y() + 0.5D, 2);
        List<Location> grid = new ArrayList<>();
        if (advancedGridEnabled) {
            int latitudeSteps = Math.max(4, (int) Math.ceil(Math.PI * Math.max(1.0D, radius.y()) / gridPointSpacing));
            int longitudeSteps = Math.max(8, (int) Math.ceil(2 * Math.PI * Math.max(radius.x(), radius.z()) / gridPointSpacing));
            for (int lat = 0; lat <= latitudeSteps; lat++) {
                double phi = Math.PI * lat / latitudeSteps;
                for (int lon = 0; lon < longitudeSteps; lon++) {
                    double theta = 2 * Math.PI * lon / longitudeSteps;
                    grid.add(point(world, center.x() + radius.x() * Math.sin(phi) * Math.cos(theta),
                        center.y() + radius.y() * Math.cos(phi), center.z() + radius.z() * Math.sin(phi) * Math.sin(theta)));
                }
            }
        }
        renderIntervalOutline(player, outline);
        if (advancedGridEnabled) renderSurfacePoints(player, grid);
    }

    private void renderCylinder(Player player, World world, CylinderRegion region) {
        Vector2 center = region.getCenter().toVector2();
        Vector2 radius = region.getRadius();
        double bottom = region.getMinimumY() - REGION_EDGE_OFFSET;
        double top = region.getMaximumY() + 1.0D + REGION_EDGE_OFFSET;
        List<Location> ringPoints = new ArrayList<>();
        List<Location> gridPoints = new ArrayList<>();
        int ringSteps = Math.max(12, (int) Math.ceil(2 * Math.PI * Math.max(radius.x(), radius.z()) / pointSpacing));
        for (int step = 0; step < ringSteps; step++) {
            double angle = 2 * Math.PI * step / ringSteps;
            double x = center.x() + (radius.x() + 0.5D) * Math.cos(angle);
            double z = center.z() + (radius.z() + 0.5D) * Math.sin(angle);
            ringPoints.add(point(world, x, bottom, z));
            ringPoints.add(point(world, x, top, z));
            if (advancedGridEnabled && step % Math.max(1, (int) Math.ceil(gridPointSpacing / pointSpacing)) == 0) {
                for (double y : axisPoints(bottom, top, gridPointSpacing)) gridPoints.add(point(world, x, y, z));
            }
        }
        renderCylinderOutline(player, ringPoints);
        if (advancedGridEnabled) renderSurfacePoints(player, gridPoints);
    }

    private void addEllipse(List<Location> points, World world, double cx, double cy, double cz,
                            double radiusA, double radiusB, int plane) {
        int steps = Math.max(12, (int) Math.ceil(2 * Math.PI * Math.max(radiusA, radiusB) / pointSpacing));
        for (int step = 0; step < steps; step++) {
            double angle = 2 * Math.PI * step / steps;
            double a = radiusA * Math.cos(angle);
            double b = radiusB * Math.sin(angle);
            points.add(switch (plane) {
                case 0 -> point(world, cx + a, cy, cz + b);
                case 1 -> point(world, cx + a, cy + b, cz);
                default -> point(world, cx, cy + b, cz + a);
            });
        }
    }

    private void renderCuboidSurfaces(Player player, CuboidBounds bounds) {
        Location min = bounds.min();
        Location max = bounds.max();
        World world = min.getWorld();
        List<Double> xs = axisPoints(min.getX(), max.getX(), surfacePointSpacing);
        List<Double> ys = axisPoints(min.getY(), max.getY(), surfacePointSpacing);
        List<Double> zs = axisPoints(min.getZ(), max.getZ(), surfacePointSpacing);
        Map<String, Location> points = new LinkedHashMap<>();

        for (double x : xs) for (double y : ys) {
            addSurfacePoint(points, point(world, x, y, min.getZ()));
            addSurfacePoint(points, point(world, x, y, max.getZ()));
        }
        for (double x : xs) for (double z : zs) {
            addSurfacePoint(points, point(world, x, min.getY(), z));
            addSurfacePoint(points, point(world, x, max.getY(), z));
        }
        for (double y : ys) for (double z : zs) {
            addSurfacePoint(points, point(world, min.getX(), y, z));
            addSurfacePoint(points, point(world, max.getX(), y, z));
        }

        renderSurfacePoints(player, new ArrayList<>(points.values()));
    }

    private List<Double> axisPoints(double start, double end, double spacing) {
        List<Double> points = new ArrayList<>();
        points.add(start);
        for (double value = start + spacing; value < end; value += spacing) points.add(value);
        if (end > start) points.add(end);
        return points;
    }

    private void addSurfacePoint(Map<String, Location> points, Location location) {
        points.putIfAbsent(locationKey(location), location);
    }

    private void renderSurfacePoints(Player player, List<Location> points) {
        if (points.size() <= maxSurfacePoints) {
            points.forEach(location -> spawnDust(player, location));
            return;
        }
        double stride = (double) points.size() / maxSurfacePoints;
        for (int index = 0; index < maxSurfacePoints; index++) {
            spawnDust(player, points.get(Math.min(points.size() - 1, (int) Math.floor(index * stride))));
        }
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

        for (Line line : lines) {
            renderLine(player, line, Integer.MAX_VALUE);
        }
    }

    private void renderIntervalOutline(Player player, List<Location> orderedPoints) {
        if (orderedPoints.isEmpty()) return;
        int interval = Math.max(1, (int) Math.round(majorMarkerInterval / pointSpacing));
        List<Location> dust = new ArrayList<>();
        List<Location> flames = new ArrayList<>();
        for (int index = 0; index < orderedPoints.size(); index++) {
            (index % interval == 0 ? flames : dust).add(orderedPoints.get(index));
        }
        renderSurfacePoints(player, dust);
        renderFlamePoints(player, flames);
    }

    private void renderCylinderOutline(Player player, List<Location> ringPairs) {
        int ringInterval = Math.max(1, (int) Math.round(majorMarkerInterval / pointSpacing));
        List<Location> dust = new ArrayList<>();
        List<Location> flames = new ArrayList<>();
        for (int pair = 0; pair + 1 < ringPairs.size(); pair += 2) {
            List<Location> target = (pair / 2) % ringInterval == 0 ? flames : dust;
            target.add(ringPairs.get(pair));
            target.add(ringPairs.get(pair + 1));
        }
        renderSurfacePoints(player, dust);
        renderFlamePoints(player, flames);
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
            if (isMajorMarkerStep(line, i, steps)) continue;
            double t = (double) i / (double) steps;
            double x = line.start().getX() + ((line.end().getX() - line.start().getX()) * t);
            double y = line.start().getY() + ((line.end().getY() - line.start().getY()) * t);
            double z = line.start().getZ() + ((line.end().getZ() - line.start().getZ()) * t);
            spawnDust(player, point(line.start().getWorld(), x, y, z));
        }
    }

    private boolean isMajorMarkerStep(Line line, int step, int totalSteps) {
        double length = line.length();
        if (length <= 0.0D || totalSteps <= 0) return true;
        int markerSteps = Math.max(1, (int) Math.floor(length / majorMarkerInterval));
        double markerSpacing = length / markerSteps;
        double distance = length * step / totalSteps;
        double nearestMarker = Math.rint(distance / markerSpacing) * markerSpacing;
        return Math.abs(distance - nearestMarker) <= Math.max(0.01D, pointSpacing * 0.3D);
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
                spawnMajorMarker(player, location);
            }
            return;
        }

        double stride = (double) ordered.size() / (double) markerBudget;
        for (int i = 0; i < markerBudget; i++) {
            int index = Math.min(ordered.size() - 1, (int) Math.floor(i * stride));
            spawnMajorMarker(player, ordered.get(index));
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
        if (selectionParticle == Particle.DUST) {
            player.spawnParticle(Particle.DUST, location.getX(), location.getY(), location.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D, RED_DUST, true);
        } else {
            player.spawnParticle(selectionParticle, location.getX(), location.getY(), location.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D, null, true);
        }
    }

    private void spawnFlame(Player player, Location location) {
        player.spawnParticle(cornerParticle, location.getX(), location.getY(), location.getZ(),
            1, 0.02D, 0.02D, 0.02D, 0.0D, null, true);
    }

    private void spawnMajorMarker(Player player, Location location) {
        player.spawnParticle(Particle.FLAME, location.getX(), location.getY(), location.getZ(),
            3, 0.08D, 0.08D, 0.08D, 0.0D, null, true);
        spawnDust(player, location);
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
