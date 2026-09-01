package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent, command-managed PNG displays backed by the image-map service. */
public final class ImageMapsFeature extends BaseFeature {
    private static final String PERMISSION = "stemcraft.command.imagemap.admin";
    private final Map<String, StoredDisplay> displays = new LinkedHashMap<>();
    private final Map<UUID, PendingPlacement> placements = new LinkedHashMap<>();
    private File imageDirectory;

    public ImageMapsFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        imageDirectory = new File(api.getDataFolder(), getConfigSection().getString("directory", "imagemaps"));
        if (!imageDirectory.exists() && !imageDirectory.mkdirs())
            STEMCraft.getPlugin().getLogger().warning("Could not create image-map directory " + imageDirectory);
        ensureStorage(); loadDisplays(); registerCommand();
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
    }

    @Override public void onDisable() {
        placements.clear();
        displays.keySet().forEach(id -> api.imageMaps().delete(managedId(id)));
        displays.clear();
    }

    private void ensureStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS image_map_displays (
              id TEXT PRIMARY KEY, image_path TEXT NOT NULL, world_name TEXT NOT NULL,
              x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, facing TEXT NOT NULL,
              columns INTEGER NOT NULL, rows INTEGER NOT NULL, scaled INTEGER NOT NULL DEFAULT 0
            );
            """);
    }

    private void loadDisplays() {
        displays.clear();
        api.database().queryEach("SELECT * FROM image_map_displays ORDER BY id", null, rs -> {
            StoredDisplay display = new StoredDisplay(rs.getString("id"), rs.getString("image_path"),
                rs.getString("world_name"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                parseFace(rs.getString("facing")), rs.getInt("columns"), rs.getInt("rows"), rs.getInt("scaled") != 0);
            displays.put(display.id, display); restore(display);
        });
    }

    private void registerCommand() {
        api.tabComplete().register("image-map-file", (player, args) -> availableImages());
        api.tabComplete().register("image-map-id", (player, args) -> displays.keySet().stream().sorted().toList());
        api.commands().create("imagemap")
            .description("Create and manage PNG map displays")
            .usage("/imagemap <place <png-file> [scale]|delete <id>|list>")
            .permission(PERMISSION)
            .tabCompletion("place", "{image-map-file}")
            .tabCompletion("place", "{image-map-file}", "scale")
            .tabCompletion("delete", "{image-map-id}")
            .tabCompletion("list")
            .executor((unused, command, ctx) -> execute(ctx))
            .register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty()) { ctx.returnInfo("Use /imagemap <place <png-file> [scale]|delete <id>|list>."); return; }
        switch (ctx.getArgLower(0)) {
            case "place", "create" -> preparePlace(ctx);
            case "delete" -> delete(ctx);
            case "list" -> list(ctx);
            default -> ctx.returnError("Use /imagemap <place <png-file> [scale]|delete <id>|list>.");
        }
    }

    private void preparePlace(CommandContext ctx) {
        if (ctx.args().size() < 2 || ctx.args().size() > 3
            || ctx.args().size() == 3 && !ctx.getArgLower(2).equals("scale")) {
            ctx.returnError("Usage: /imagemap place <png-file> [scale]. Quote filenames containing spaces."); return;
        }
        ctx.checkNotConsole();
        String path = ctx.getArg(1); boolean scale = ctx.args().size() == 3;
        try {
            File file = resolveImage(path);
            BufferedImage image = ImageIO.read(file);
            if (image == null) { ctx.returnError("That file is not a readable PNG image."); return; }
            String id = nextId(path);
            int columns = Math.max(1, (image.getWidth() + 127) / 128);
            int rows = Math.max(1, (image.getHeight() + 127) / 128);
            BufferedImage rendered = scale ? image : centered(image, columns * 128, rows * 128);
            placements.put(ctx.asPlayer().getUniqueId(), new PendingPlacement(id, path, rendered, columns, rows, scale));
            ctx.returnSuccess("Preparing " + id + ": " + image.getWidth() + "×" + image.getHeight() + " pixels (" + columns + "×" + rows
                + " maps, " + (scale ? "scaled to fill" : "centred at original size")
                + "). Right-click its bottom-left backing block; sneak-right-click to cancel.");
        } catch (IOException ex) { ctx.returnError(ex.getMessage()); }
    }

    private void delete(CommandContext ctx) {
        if (ctx.args().size() < 2) { ctx.returnError("Usage: /imagemap delete <id>"); return; }
        String id = ctx.getArgLower(1);
        StoredDisplay removed = displays.remove(id);
        if (removed == null) { ctx.returnError("Unknown image-map display."); return; }
        api.imageMaps().delete(managedId(id));
        api.database().update("DELETE FROM image_map_displays WHERE id=?", ps -> ps.setString(1, id));
        ctx.returnSuccess("Deleted image-map display " + id + ".");
    }

    private void list(CommandContext ctx) {
        if (displays.isEmpty()) { ctx.returnInfo("No image-map displays exist."); return; }
        displays.values().forEach(display -> ctx.info(display.id + " — " + display.imagePath + " — "
            + display.columns + "×" + display.rows + " maps — " + display.world + " " + display.x + "," + display.y + "," + display.z));
    }

    private void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer(); PendingPlacement pending = placements.get(player.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        if (player.isSneaking()) { placements.remove(player.getUniqueId()); api.messages().send(player, "/info/Image-map placement cancelled."); return; }
        Location backing = event.getClickedBlock().getLocation();
        BlockFace face = facing(player).getOppositeFace();
        StoredDisplay display = new StoredDisplay(pending.id, pending.imagePath, backing.getWorld().getName(),
            backing.getBlockX(), backing.getBlockY(), backing.getBlockZ(), face, pending.columns, pending.rows, pending.scale);
        api.imageMaps().create(managedId(display.id), backing, face, display.columns, display.rows);
        api.imageMaps().render(managedId(display.id), pending.image);
        displays.put(display.id, display); save(display); placements.remove(player.getUniqueId());
        api.messages().send(player, "/success/Image-map display placed.");
    }

    private void restore(StoredDisplay display) {
        World world = Bukkit.getWorld(display.world); if (world == null) return;
        try {
            BufferedImage source = ImageIO.read(resolveImage(display.imagePath)); if (source == null) return;
            BufferedImage image = display.scaled ? source : centered(source, display.columns * 128, display.rows * 128);
            api.imageMaps().create(managedId(display.id), new Location(world, display.x, display.y, display.z),
                display.facing, display.columns, display.rows);
            api.imageMaps().render(managedId(display.id), image);
        } catch (IOException ex) {
            STEMCraft.getPlugin().getLogger().warning("Could not restore image map " + display.id + ": " + ex.getMessage());
        }
    }

    private void save(StoredDisplay display) {
        api.database().update("INSERT OR REPLACE INTO image_map_displays(id,image_path,world_name,x,y,z,facing,columns,rows,scaled) VALUES(?,?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setString(1, display.id); ps.setString(2, display.imagePath); ps.setString(3, display.world);
            ps.setInt(4, display.x); ps.setInt(5, display.y); ps.setInt(6, display.z); ps.setString(7, display.facing.name());
            ps.setInt(8, display.columns); ps.setInt(9, display.rows); ps.setInt(10, display.scaled ? 1 : 0);
        });
    }

    private File resolveImage(String path) throws IOException {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("Image-map files must use the .png extension.");
        File root = imageDirectory.getCanonicalFile(); File file = new File(root, path).getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath())) throw new IOException("The PNG must be inside " + root + ".");
        if (!file.isFile()) throw new IOException("PNG not found: " + file + ".");
        return file;
    }

    private List<String> availableImages() {
        File[] files = imageDirectory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files).map(File::getName).sorted(String.CASE_INSENSITIVE_ORDER)
            .map(ImageMapsFeature::quoteCompletion).toList();
    }

    private String nextId(String imagePath) {
        String filename = new File(imagePath).getName();
        int extension = filename.lastIndexOf('.'); if (extension > 0) filename = filename.substring(0, extension);
        String base = filename.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "image";
        Set<String> reserved = new HashSet<>(displays.keySet());
        placements.values().forEach(pending -> reserved.add(pending.id));
        if (!reserved.contains(base)) return base;
        int suffix = 2; while (reserved.contains(base + "-" + suffix)) suffix++;
        return base + "-" + suffix;
    }

    private static BufferedImage centered(BufferedImage source, int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(source, (width - source.getWidth()) / 2, (height - source.getHeight()) / 2, null);
        graphics.dispose(); return canvas;
    }

    private static String quoteCompletion(String filename) {
        if (!filename.contains(" ") && !filename.contains("\t") && !filename.contains("\"")) return filename;
        return "\"" + filename.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String managedId(String id) { return "command:" + id; }
    private static BlockFace parseFace(String value) { try { return BlockFace.valueOf(value); } catch (Exception ignored) { return BlockFace.SOUTH; } }
    private static BlockFace facing(Player player) {
        int yaw = Math.floorMod(Math.round(player.getLocation().getYaw() / 90F), 4);
        return switch (yaw) { case 0 -> BlockFace.SOUTH; case 1 -> BlockFace.WEST; case 2 -> BlockFace.NORTH; default -> BlockFace.EAST; };
    }

    private record PendingPlacement(String id, String imagePath, BufferedImage image, int columns, int rows, boolean scale) {}
    private record StoredDisplay(String id, String imagePath, String world, int x, int y, int z,
                                 BlockFace facing, int columns, int rows, boolean scaled) {}
}
