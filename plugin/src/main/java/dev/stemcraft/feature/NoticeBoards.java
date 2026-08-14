package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.dialog.DialogResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Player-created notices rendered on physical image-map boards. */
public final class NoticeBoards extends BaseFeature {
    private static final String EXPIRY_TASK = "feature:notice-boards-expiry";
    private static final String ROTATION_TASK = "feature:notice-boards-rotation";
    private static final long DEFAULT_RETENTION_MILLIS = Duration.ofDays(14).toMillis();
    private static final int DEFAULT_COLUMNS = 4;
    private static final int DEFAULT_ROWS = 3;
    private static final int MAX_HEADER_LENGTH = 36;
    private static final int MAX_MESSAGE_LENGTH = 160;
    private Command command;
    private long retentionMillis = DEFAULT_RETENTION_MILLIS;
    private int displayPage;

    public NoticeBoards(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        retentionMillis = Duration.ofDays(Math.max(1, getConfigSection().getInt("retention-days", 14))).toMillis();
        ensureStorage();
        purgeExpired();
        restoreBoards();
        registerCommand();
        api.tasks().repeating(EXPIRY_TASK, 1200L, 1200L, this::purgeExpired);
        api.tasks().repeating(ROTATION_TASK, 600L, 600L, () -> {
            displayPage++;
            refreshBoards();
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        retentionMillis = Duration.ofDays(Math.max(1, getConfigSection().getInt("retention-days", 14))).toMillis();
        refreshBoards();
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(EXPIRY_TASK);
        api.tasks().cancel(ROTATION_TASK);
        if (command != null) {
            command.unregister();
        }
        loadBoards().forEach(board -> api.imageMaps().delete(displayId(board.id())));
    }

    private void ensureStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS notice_boards (
              id TEXT PRIMARY KEY,
              world TEXT NOT NULL,
              x INTEGER NOT NULL,
              y INTEGER NOT NULL,
              z INTEGER NOT NULL,
              facing TEXT NOT NULL,
              columns INTEGER NOT NULL,
              rows INTEGER NOT NULL
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS notice_board_posts (
              id TEXT PRIMARY KEY,
              author_uuid TEXT NOT NULL,
              author_name TEXT NOT NULL,
              header TEXT NOT NULL,
              message TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL
            );
            """);
        api.database().execute("CREATE INDEX IF NOT EXISTS notice_board_posts_expiry_idx ON notice_board_posts (expires_at);");
    }

    private void registerCommand() {
        command = api.commands().create("noticeboard")
            .description("Post and manage survival notice boards")
            .usage("/noticeboard <post|mine|remove|board>")
            .tabCompletion("post")
            .tabCompletion("mine")
            .tabCompletion("remove")
            .tabCompletion("board", "create")
            .tabCompletion("board", "delete")
            .executor(this::onCommand)
            .register(STEMCraft.getPlugin());
    }

    private void onCommand(STEMCraftAPI unused, Command unusedCommand, CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.info("Use /noticeboard post, /noticeboard mine, or /noticeboard board create <id> [columns] [rows].");
            return;
        }
        switch (ctx.getArgLower(0)) {
            case "post" -> openPostDialog(ctx);
            case "mine" -> listOwnPosts(ctx);
            case "remove" -> removePost(ctx);
            case "board" -> manageBoard(ctx);
            default -> ctx.error("Unknown noticeboard command.");
        }
    }

    private void openPostDialog(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.error("Only players can post notices.");
            return;
        }
        boolean opened = api.dialogs().create("noticeboard:post")
            .title(net.kyori.adventure.text.Component.text("Post a notice"))
            .body(net.kyori.adventure.text.Component.text("Notices remain on the board for 14 days."))
            .textInput("header", net.kyori.adventure.text.Component.text("Header"), "", MAX_HEADER_LENGTH)
            .multilineTextInput("message", net.kyori.adventure.text.Component.text("Short message"), "", MAX_MESSAGE_LENGTH, 4)
            .submit(net.kyori.adventure.text.Component.text("Post"), response -> createPost(player, response))
            .cancel(net.kyori.adventure.text.Component.text("Cancel"), () -> { })
            .open(player);
        if (!opened) {
            ctx.error("Could not open the notice dialog.");
        }
    }

    private void createPost(Player player, DialogResponse response) {
        String header = response.text("header").trim();
        String message = response.text("message").trim();
        if (header.isBlank() || message.isBlank()) {
            api.messages().error(player, "A header and message are required.");
            return;
        }
        UUID id = UUID.randomUUID();
        long createdAt = System.currentTimeMillis();
        int changed = api.database().update("""
            INSERT INTO notice_board_posts (id, author_uuid, author_name, header, message, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, statement -> {
            statement.setString(1, id.toString());
            statement.setString(2, player.getUniqueId().toString());
            statement.setString(3, player.getName());
            statement.setString(4, header);
            statement.setString(5, message);
            statement.setLong(6, createdAt);
            statement.setLong(7, createdAt + retentionMillis);
        });
        if (changed == 1) {
            displayPage = 0;
            refreshBoards();
            api.messages().success(player, "Your notice has been posted for 14 days.");
        } else {
            api.messages().error(player, "Could not save your notice.");
        }
    }

    private void listOwnPosts(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.error("Only players can list their notices.");
            return;
        }
        List<NoticePost> posts = loadPosts("WHERE author_uuid = ? AND expires_at > ? ORDER BY created_at DESC",
            statement -> {
                statement.setString(1, player.getUniqueId().toString());
                statement.setLong(2, System.currentTimeMillis());
            });
        if (posts.isEmpty()) {
            ctx.info("You have no active notices.");
            return;
        }
        ctx.info("Your active notices:");
        for (NoticePost post : posts) {
            ctx.info(post.id().toString().substring(0, 8) + " - " + post.header());
        }
    }

    private void removePost(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null || ctx.args().size() < 2) {
            ctx.error("Usage: /noticeboard remove <post-id>");
            return;
        }
        NoticePost post = findPost(ctx.args().get(1));
        if (post == null) {
            ctx.error("Notice not found.");
            return;
        }
        if (!post.authorUuid().equals(player.getUniqueId()) && !player.hasPermission("stemcraft.noticeboard.admin")) {
            ctx.error("You can only remove your own notices.");
            return;
        }
        api.database().update("DELETE FROM notice_board_posts WHERE id = ?", statement -> statement.setString(1, post.id().toString()));
        refreshBoards();
        ctx.success("Notice removed.");
    }

    private void manageBoard(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null || !player.hasPermission("stemcraft.noticeboard.admin")) {
            ctx.error("You do not have permission to manage notice boards.");
            return;
        }
        if (ctx.args().size() < 3) {
            ctx.error("Usage: /noticeboard board <create|delete> <id> [columns] [rows]");
            return;
        }
        String action = ctx.getArgLower(1);
        String id = ctx.args().get(2).toLowerCase(Locale.ROOT);
        if (action.equals("delete")) {
            api.database().update("DELETE FROM notice_boards WHERE id = ?", statement -> statement.setString(1, id));
            api.imageMaps().delete(displayId(id));
            ctx.success("Notice board removed.");
            return;
        }
        if (!action.equals("create")) {
            ctx.error("Usage: /noticeboard board <create|delete> <id> [columns] [rows]");
            return;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null || target.getType().isAir()) {
            ctx.error("Look at the bottom-left backing block for the board.");
            return;
        }
        int columns = parseDimension(ctx, 3, DEFAULT_COLUMNS);
        int rows = parseDimension(ctx, 4, DEFAULT_ROWS);
        BlockFace facing = facingTowardPlayer(target.getLocation(), player.getLocation());
        api.database().update("""
            INSERT INTO notice_boards (id, world, x, y, z, facing, columns, rows) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
              facing=excluded.facing, columns=excluded.columns, rows=excluded.rows
            """, statement -> {
            statement.setString(1, id);
            statement.setString(2, target.getWorld().getName());
            statement.setInt(3, target.getX());
            statement.setInt(4, target.getY());
            statement.setInt(5, target.getZ());
            statement.setString(6, facing.name());
            statement.setInt(7, columns);
            statement.setInt(8, rows);
        });
        restoreBoard(new NoticeBoard(id, target.getLocation(), facing, columns, rows));
        ctx.success("Notice board created. The targeted block is its bottom-left backing block.");
    }

    private int parseDimension(CommandContext ctx, int index, int fallback) {
        if (ctx.args().size() <= index) return fallback;
        try {
            return Math.max(1, Math.min(8, Integer.parseInt(ctx.args().get(index))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void purgeExpired() {
        int removed = api.database().update("DELETE FROM notice_board_posts WHERE expires_at <= ?",
            statement -> statement.setLong(1, System.currentTimeMillis()));
        if (removed > 0) {
            refreshBoards();
        }
    }

    private void restoreBoards() {
        loadBoards().forEach(this::restoreBoard);
    }

    private void restoreBoard(NoticeBoard board) {
        api.imageMaps().create(displayId(board.id()), board.location(), board.facing(), board.columns(), board.rows());
        api.imageMaps().render(displayId(board.id()), renderBoard(board));
    }

    private void refreshBoards() {
        for (NoticeBoard board : loadBoards()) {
            if (!api.imageMaps().exists(displayId(board.id()))) {
                api.imageMaps().create(displayId(board.id()), board.location(), board.facing(), board.columns(), board.rows());
            }
            api.imageMaps().render(displayId(board.id()), renderBoard(board));
        }
    }

    private BufferedImage renderBoard(NoticeBoard board) {
        int width = board.columns() * 128;
        int height = board.rows() * 128;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(48, 31, 20));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(224, 190, 112));
        graphics.setStroke(new BasicStroke(6));
        graphics.drawRect(5, 5, width - 11, height - 11);

        String title = getConfigSection().getString("title", "SERVER NOTICE / REQUEST BOARD ★★★★★");
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(18, Math.min(34, width / 16))));
        graphics.setColor(new Color(255, 221, 133));
        drawCentered(graphics, title, width / 2, 42);

        List<NoticePost> posts = loadPosts("WHERE expires_at > ? ORDER BY created_at DESC",
            statement -> statement.setLong(1, System.currentTimeMillis()));
        int top = 62;
        int availableHeight = height - top - 15;
        int gridColumns = width >= 384 ? 2 : 1;
        int cardWidth = (width - 24 - (gridColumns - 1) * 10) / gridColumns;
        int cardHeight = 94;
        int gridRows = Math.max(1, availableHeight / cardHeight);
        int capacity = gridColumns * gridRows;
        int pages = Math.max(1, (posts.size() + capacity - 1) / capacity);
        int page = Math.floorMod(displayPage, pages);
        int start = page * capacity;
        for (int index = 0; index < Math.min(capacity, posts.size() - start); index++) {
            int column = index % gridColumns;
            int row = index / gridColumns;
            drawPost(graphics, posts.get(start + index), 12 + column * (cardWidth + 10), top + row * cardHeight,
                cardWidth, cardHeight - 8);
        }
        if (posts.isEmpty()) {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 22));
            graphics.setColor(new Color(230, 220, 195));
            drawCentered(graphics, "No notices have been posted yet.", width / 2, height / 2);
        }
        graphics.dispose();
        return image;
    }

    private void drawPost(Graphics2D graphics, NoticePost post, int x, int y, int width, int height) {
        graphics.setColor(new Color(245, 232, 190));
        graphics.fillRoundRect(x, y, width, height, 10, 10);
        graphics.setColor(new Color(72, 48, 31));
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRoundRect(x, y, width, height, 10, 10);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        graphics.drawString(post.header(), x + 12, y + 25);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        int lineY = y + 48;
        for (String line : wrap(graphics.getFontMetrics(), post.message(), width - 24, 2)) {
            graphics.drawString(line, x + 12, lineY);
            lineY += 19;
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        String by = "Posted by " + post.authorName();
        graphics.drawString(by, x + width - graphics.getFontMetrics().stringWidth(by) - 12, y + height - 10);
    }

    private static List<String> wrap(FontMetrics metrics, String text, int width, int maximumLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.replace('\n', ' ').split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (lines.size() == maximumLines) break;
            }
        }
        if (!line.isEmpty() && lines.size() < maximumLines) lines.add(line.toString());
        return lines;
    }

    private static void drawCentered(Graphics2D graphics, String text, int centerX, int baselineY) {
        graphics.drawString(text, centerX - graphics.getFontMetrics().stringWidth(text) / 2, baselineY);
    }

    private List<NoticeBoard> loadBoards() {
        List<NoticeBoard> boards = new ArrayList<>();
        api.database().queryEach("SELECT * FROM notice_boards ORDER BY id", null, result -> {
            org.bukkit.World world = Bukkit.getWorld(result.getString("world"));
            if (world != null) {
                boards.add(new NoticeBoard(result.getString("id"),
                    new Location(world, result.getInt("x"), result.getInt("y"), result.getInt("z")),
                    BlockFace.valueOf(result.getString("facing")), result.getInt("columns"), result.getInt("rows")));
            }
        });
        return boards;
    }

    private List<NoticePost> loadPosts(String clause, dev.stemcraft.api.database.DatabaseStatementBinder binder) {
        List<NoticePost> posts = new ArrayList<>();
        api.database().queryEach("SELECT * FROM notice_board_posts " + clause, binder, result -> posts.add(mapPost(result)));
        return posts;
    }

    private NoticePost findPost(String reference) {
        List<NoticePost> matches = loadPosts("WHERE id = ? OR id LIKE ?", statement -> {
            statement.setString(1, reference);
            statement.setString(2, reference + "%");
        });
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static NoticePost mapPost(ResultSet result) throws java.sql.SQLException {
        return new NoticePost(UUID.fromString(result.getString("id")), UUID.fromString(result.getString("author_uuid")),
            result.getString("author_name"), result.getString("header"), result.getString("message"),
            result.getLong("created_at"), result.getLong("expires_at"));
    }

    private static BlockFace facingTowardPlayer(Location block, Location player) {
        double dx = player.getX() - block.getX();
        double dz = player.getZ() - block.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static String displayId(String boardId) {
        return "notice-board:" + boardId;
    }

    private record NoticeBoard(String id, Location location, BlockFace facing, int columns, int rows) { }
    private record NoticePost(UUID id, UUID authorUuid, String authorName, String header, String message,
                              long createdAt, long expiresAt) { }
}
