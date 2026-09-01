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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persistent, command-managed PNG displays backed by the image-map service. */
public final class ImageMapsFeature extends BaseFeature {
    private static final String PERMISSION = "stemcraft.command.imagemap.admin";
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+");
    private final Map<String, StoredDisplay> displays = new LinkedHashMap<>();
    private final Map<UUID, PendingPlacement> placements = new LinkedHashMap<>();
    private File imageDirectory;

    public ImageMapsFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        imageDirectory = new File(api.getDataFolder(), getConfigSection().getString("directory", "images"));
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
              columns INTEGER NOT NULL, rows INTEGER NOT NULL
            );
            """);
    }

    private void loadDisplays() {
        displays.clear();
        api.database().queryEach("SELECT * FROM image_map_displays ORDER BY id", null, rs -> {
            StoredDisplay display = new StoredDisplay(rs.getString("id"), rs.getString("image_path"),
                rs.getString("world_name"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                parseFace(rs.getString("facing")), rs.getInt("columns"), rs.getInt("rows"));
            displays.put(display.id, display); restore(display);
        });
    }

    private void registerCommand() {
        api.commands().create("imagemap")
            .description("Create and manage PNG map displays")
            .usage("/imagemap <create <id> <png-file>|delete <id>|list>")
            .permission(PERMISSION)
            .tabCompletion("create")
            .tabCompletion("delete")
            .tabCompletion("list")
            .executor((unused, command, ctx) -> execute(ctx))
            .register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty()) { ctx.returnInfo("Use /imagemap <create <id> <png-file>|delete <id>|list>."); return; }
        switch (ctx.getArgLower(0)) {
            case "create" -> prepareCreate(ctx);
            case "delete" -> delete(ctx);
            case "list" -> list(ctx);
            default -> ctx.returnError("Use /imagemap <create <id> <png-file>|delete <id>|list>.");
        }
    }

    private void prepareCreate(CommandContext ctx) {
        if (ctx.args().size() < 3) { ctx.returnError("Usage: /imagemap create <id> <png-file>"); return; }
        ctx.checkNotConsole();
        String id = ctx.getArgLower(1);
        if (!VALID_ID.matcher(id).matches()) { ctx.returnError("The ID may only contain letters, numbers, dots, dashes, and underscores."); return; }
        String path = String.join(" ", ctx.args().subList(2, ctx.args().size()));
        try {
            File file = resolveImage(path);
            BufferedImage image = ImageIO.read(file);
            if (image == null) { ctx.returnError("That file is not a readable PNG image."); return; }
            int columns = Math.max(1, (image.getWidth() + 127) / 128);
            int rows = Math.max(1, (image.getHeight() + 127) / 128);
            placements.put(ctx.asPlayer().getUniqueId(), new PendingPlacement(id, path, image, columns, rows));
            ctx.returnSuccess("Image is " + image.getWidth() + "×" + image.getHeight() + " pixels (" + columns + "×" + rows
                + " maps). Right-click its bottom-left backing block; sneak-right-click to cancel.");
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
            backing.getBlockX(), backing.getBlockY(), backing.getBlockZ(), face, pending.columns, pending.rows);
        api.imageMaps().create(managedId(display.id), backing, face, display.columns, display.rows);
        api.imageMaps().render(managedId(display.id), pending.image);
        displays.put(display.id, display); save(display); placements.remove(player.getUniqueId());
        api.messages().send(player, "/success/Image-map display placed.");
    }

    private void restore(StoredDisplay display) {
        World world = Bukkit.getWorld(display.world); if (world == null) return;
        try {
            BufferedImage image = ImageIO.read(resolveImage(display.imagePath)); if (image == null) return;
            api.imageMaps().create(managedId(display.id), new Location(world, display.x, display.y, display.z),
                display.facing, display.columns, display.rows);
            api.imageMaps().render(managedId(display.id), image);
        } catch (IOException ex) {
            STEMCraft.getPlugin().getLogger().warning("Could not restore image map " + display.id + ": " + ex.getMessage());
        }
    }

    private void save(StoredDisplay display) {
        api.database().update("INSERT OR REPLACE INTO image_map_displays(id,image_path,world_name,x,y,z,facing,columns,rows) VALUES(?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setString(1, display.id); ps.setString(2, display.imagePath); ps.setString(3, display.world);
            ps.setInt(4, display.x); ps.setInt(5, display.y); ps.setInt(6, display.z); ps.setString(7, display.facing.name());
            ps.setInt(8, display.columns); ps.setInt(9, display.rows);
        });
    }

    private File resolveImage(String path) throws IOException {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("Image-map files must use the .png extension.");
        File root = imageDirectory.getCanonicalFile(); File file = new File(root, path).getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath())) throw new IOException("The PNG must be inside " + root + ".");
        if (!file.isFile()) throw new IOException("PNG not found: " + file + ".");
        return file;
    }

    private String managedId(String id) { return "command:" + id; }
    private static BlockFace parseFace(String value) { try { return BlockFace.valueOf(value); } catch (Exception ignored) { return BlockFace.SOUTH; } }
    private static BlockFace facing(Player player) {
        int yaw = Math.floorMod(Math.round(player.getLocation().getYaw() / 90F), 4);
        return switch (yaw) { case 0 -> BlockFace.SOUTH; case 1 -> BlockFace.WEST; case 2 -> BlockFace.NORTH; default -> BlockFace.EAST; };
    }

    private record PendingPlacement(String id, String imagePath, BufferedImage image, int columns, int rows) {}
    private record StoredDisplay(String id, String imagePath, String world, int x, int y, int z,
                                 BlockFace facing, int columns, int rows) {}
}
