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
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
    private static final double TEXT_SCALE = 1.5D;
    private Command command;
    private long retentionMillis = DEFAULT_RETENTION_MILLIS;
    private int displayPage;
    private String cachedTitleImagePath = "";
    private long cachedTitleImageModified = Long.MIN_VALUE;
    private BufferedImage cachedTitleImage;
    private final Map<UUID, Long> lastBoardClicks = new HashMap<>();
    private static final Color[] PAPER_COLOURS = {
        new Color(247, 231, 164),
        new Color(226, 238, 190),
        new Color(235, 213, 174),
        new Color(221, 226, 238),
        new Color(241, 211, 203),
        new Color(231, 218, 242)
    };

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
        lastBoardClicks.clear();
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
            .permission("stemcraft.noticeboard.admin")
            .executor(this::onCommand)
            .register(STEMCraft.getPlugin());
    }

    private void onCommand(STEMCraftAPI unused, Command unusedCommand, CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.info("Use /noticeboard post, /noticeboard mine, or /noticeboard board create <id> [columns] [rows].");
            return;
        }
        switch (ctx.getArgLower(0)) {
            case "post" -> openAdminPostDialog(ctx);
            case "mine" -> listOwnPosts(ctx);
            case "remove" -> removePost(ctx);
            case "board" -> manageBoard(ctx);
            default -> ctx.error("Unknown noticeboard command.");
        }
    }

    private void openAdminPostDialog(CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.error("Only players can post notices.");
            return;
        }
        openNoticeEditor(player, null, false);
    }

    private void handleBoardClick(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastBoardClicks.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 500L) {
            return;
        }
        List<NoticePost> posts = activePosts(player.getUniqueId());
        if (posts.isEmpty()) {
            openNoticeEditor(player, null, true);
            return;
        }
        NoticePost post = posts.getFirst();
        boolean opened = api.dialogs().create("noticeboard:manage")
            .title(net.kyori.adventure.text.Component.text("Your notice"))
            .body(net.kyori.adventure.text.Component.text(post.header() + "\n\n" + post.message()))
            .action(net.kyori.adventure.text.Component.text("Edit"), () -> openNoticeEditor(player, post, true))
            .action(net.kyori.adventure.text.Component.text("Delete"), () -> deletePlayerPost(player, post))
            .cancel(net.kyori.adventure.text.Component.text("Cancel"), () -> { })
            .open(player);
        if (!opened) {
            api.messages().error(player, "Could not open the notice dialog.");
        }
    }

    private void openNoticeEditor(Player player, NoticePost existing, boolean enforceSingleNotice) {
        String title = existing == null ? "Post a notice" : "Edit your notice";
        String header = existing == null ? "" : existing.header();
        String message = existing == null ? "" : existing.message();
        boolean opened = api.dialogs().create(existing == null ? "noticeboard:post" : "noticeboard:edit")
            .title(net.kyori.adventure.text.Component.text(title))
            .body(net.kyori.adventure.text.Component.text("Notices remain on the board for 14 days."))
            .textInput("header", net.kyori.adventure.text.Component.text("Header"), header, MAX_HEADER_LENGTH)
            .multilineTextInput("message", net.kyori.adventure.text.Component.text("Short message"), message, MAX_MESSAGE_LENGTH, 4)
            .submit(net.kyori.adventure.text.Component.text(existing == null ? "Post" : "Save"), response -> {
                if (existing == null) {
                    createPost(player, response, enforceSingleNotice);
                } else {
                    updatePost(player, existing, response);
                }
            })
            .cancel(net.kyori.adventure.text.Component.text("Cancel"), () -> { })
            .open(player);
        if (!opened) {
            api.messages().error(player, "Could not open the notice dialog.");
        }
    }

    private void createPost(Player player, DialogResponse response, boolean enforceSingleNotice) {
        String header = response.text("header").trim();
        String message = response.text("message").trim();
        if (header.isBlank() || message.isBlank()) {
            api.messages().error(player, "A header and message are required.");
            return;
        }
        if (enforceSingleNotice && !activePosts(player.getUniqueId()).isEmpty()) {
            api.messages().error(player, "You already have an active notice. Click the board to edit or delete it.");
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

    private void updatePost(Player player, NoticePost post, DialogResponse response) {
        String header = response.text("header").trim();
        String message = response.text("message").trim();
        if (header.isBlank() || message.isBlank()) {
            api.messages().error(player, "A header and message are required.");
            return;
        }
        long now = System.currentTimeMillis();
        int changed = api.database().update("""
            UPDATE notice_board_posts
            SET author_name = ?, header = ?, message = ?, created_at = ?, expires_at = ?
            WHERE id = ? AND author_uuid = ?
            """, statement -> {
            statement.setString(1, player.getName());
            statement.setString(2, header);
            statement.setString(3, message);
            statement.setLong(4, now);
            statement.setLong(5, now + retentionMillis);
            statement.setString(6, post.id().toString());
            statement.setString(7, player.getUniqueId().toString());
        });
        if (changed == 1) {
            displayPage = 0;
            refreshBoards();
            api.messages().success(player, "Your notice has been updated for another 14 days.");
        } else {
            api.messages().error(player, "That notice is no longer available.");
        }
    }

    private void deletePlayerPost(Player player, NoticePost post) {
        int changed = api.database().update(
            "DELETE FROM notice_board_posts WHERE id = ? AND author_uuid = ?",
            statement -> {
                statement.setString(1, post.id().toString());
                statement.setString(2, player.getUniqueId().toString());
            });
        if (changed == 1) {
            refreshBoards();
            api.messages().success(player, "Your notice has been deleted.");
        } else {
            api.messages().error(player, "That notice is no longer available.");
        }
    }

    private List<NoticePost> activePosts(UUID playerUuid) {
        return loadPosts("WHERE author_uuid = ? AND expires_at > ? ORDER BY created_at DESC", statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, System.currentTimeMillis());
        });
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
        api.imageMaps().onClick(displayId(board.id()), click -> handleBoardClick(click.player()));
    }

    private void refreshBoards() {
        for (NoticeBoard board : loadBoards()) {
            if (!api.imageMaps().exists(displayId(board.id()))) {
                api.imageMaps().create(displayId(board.id()), board.location(), board.facing(), board.columns(), board.rows());
                api.imageMaps().onClick(displayId(board.id()), click -> handleBoardClick(click.player()));
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

        drawBoardTitle(graphics, width);

        List<NoticePost> posts = loadPosts("WHERE expires_at > ? ORDER BY created_at DESC",
            statement -> statement.setLong(1, System.currentTimeMillis()));
        int top = 62;
        int availableHeight = height - top - 15;
        int gridColumns = Math.max(1, board.columns() / 2);
        int cardWidth = (width - 24 - (gridColumns - 1) * 10) / gridColumns;
        int cardHeight = 94;
        int gridRows = Math.max(1, availableHeight / cardHeight);
        int capacity = gridColumns * gridRows;
        int pages = Math.max(1, (posts.size() + capacity - 1) / capacity);
        int page = Math.floorMod(displayPage, pages);
        int start = page * capacity;
        int visibleCount = Math.min(capacity, posts.size() - start);
        List<Integer> slots = randomizedSlots(board, page, capacity);
        for (int index = 0; index < visibleCount; index++) {
            int slot = slots.get(index);
            int column = slot % gridColumns;
            int row = slot / gridColumns;
            drawPost(graphics, posts.get(start + index), 12 + column * (cardWidth + 10), top + row * cardHeight,
                cardWidth, cardHeight - 8);
        }
        if (posts.isEmpty()) {
            graphics.setColor(new Color(230, 220, 195));
            drawMinecraftCentered(graphics, "No notices have been posted yet.", width / 2, height / 2,
                TEXT_SCALE, false);
        }
        graphics.dispose();
        return image;
    }

    private void drawPost(Graphics2D graphics, NoticePost post, int x, int y, int width, int height) {
        int padding = 6;
        BufferedImage paper = new BufferedImage(width + padding * 2, height + padding * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D card = paper.createGraphics();
        card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int colourIndex = Math.floorMod(post.id().hashCode(), PAPER_COLOURS.length);
        Color paperColour = PAPER_COLOURS[colourIndex];
        card.setColor(new Color(0, 0, 0, 55));
        card.fillRoundRect(padding + 3, padding + 4, width, height, 7, 7);
        card.setColor(paperColour);
        card.fillRoundRect(padding, padding, width, height, 7, 7);
        card.setColor(new Color(91, 61, 38));
        card.setStroke(new BasicStroke(1.5f));
        card.drawRoundRect(padding, padding, width - 1, height - 1, 7, 7);

        int centerX = padding + width / 2;
        card.setColor(new Color(126, 55, 42));
        card.fillOval(centerX - 3, padding + 4, 6, 6);
        card.setColor(new Color(72, 48, 31));
        drawMinecraftText(card, fitMinecraft(post.header(), width - 20, TEXT_SCALE), padding + 10, padding + 12,
            TEXT_SCALE, true);
        int lineY = padding + 28;
        for (String line : wrapMinecraft(post.message(), width - 20, 3, TEXT_SCALE)) {
            drawMinecraftText(card, line, padding + 10, lineY, TEXT_SCALE, false);
            lineY += 13;
        }
        String by = fitMinecraft("Posted by " + post.authorName(), width - 20, TEXT_SCALE);
        drawMinecraftText(card, by, padding + width - minecraftWidth(by, TEXT_SCALE) - 9,
            padding + height - 14, TEXT_SCALE, false);
        card.dispose();

        int hash = post.id().hashCode();
        int offsetX = Math.floorMod(hash / 31, 9) - 4;
        int offsetY = Math.floorMod(hash / 127, 7) - 3;
        double degrees = (Math.floorMod(hash / 7, 7) - 3) * 0.45;
        AffineTransform original = graphics.getTransform();
        graphics.rotate(Math.toRadians(degrees), x + offsetX + width / 2.0, y + offsetY + height / 2.0);
        graphics.drawImage(paper, x + offsetX - padding, y + offsetY - padding, null);
        graphics.setTransform(original);
    }

    private static List<Integer> randomizedSlots(NoticeBoard board, int page, int capacity) {
        List<Integer> slots = new ArrayList<>(capacity);
        for (int slot = 0; slot < capacity; slot++) {
            slots.add(slot);
        }
        long seed = 31L * board.id().hashCode() + page;
        Collections.shuffle(slots, new Random(seed));
        return slots;
    }

    private static List<String> wrapMinecraft(String text, int width, int maximumLines, double scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.replace('\n', ' ').split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (minecraftWidth(candidate, scale) <= width) {
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

    private static String fitMinecraft(String text, int width, double scale) {
        if (minecraftWidth(text, scale) <= width) {
            return text;
        }
        String suffix = "...";
        StringBuilder fitted = new StringBuilder(text);
        while (!fitted.isEmpty() && minecraftWidth(fitted + suffix, scale) > width) {
            fitted.deleteCharAt(fitted.length() - 1);
        }
        return fitted.toString().stripTrailing() + suffix;
    }

    private void drawBoardTitle(Graphics2D graphics, int width) {
        BufferedImage titleImage = loadTitleImage();
        if (titleImage != null) {
            double scale = Math.min((width - 30.0) / titleImage.getWidth(), 44.0 / titleImage.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(titleImage.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(titleImage.getHeight() * scale));
            graphics.drawImage(titleImage, (width - drawWidth) / 2, 8, drawWidth, drawHeight, null);
            return;
        }
        String title = getConfigSection().getString("title", "SERVER NOTICE / REQUEST BOARD ★★★★★");
        graphics.setColor(new Color(255, 221, 133));
        drawMinecraftCentered(graphics, fitMinecraft(title, width - 24, TEXT_SCALE), width / 2, 18,
            TEXT_SCALE, true);
    }

    private BufferedImage loadTitleImage() {
        String configured = getConfigSection().getString("title-image", "").trim();
        if (configured.isBlank()) {
            cachedTitleImagePath = "";
            cachedTitleImage = null;
            return null;
        }
        File file = new File(configured);
        if (!file.isAbsolute()) {
            file = new File(api.getDataFolder(), configured);
        }
        long modified = file.isFile() ? file.lastModified() : -1L;
        if (configured.equals(cachedTitleImagePath) && modified == cachedTitleImageModified) {
            return cachedTitleImage;
        }
        cachedTitleImagePath = configured;
        cachedTitleImageModified = modified;
        cachedTitleImage = null;
        if (!file.isFile()) {
            return null;
        }
        try {
            cachedTitleImage = ImageIO.read(file);
        } catch (IOException ignored) {
            cachedTitleImage = null;
        }
        return cachedTitleImage;
    }

    private static int minecraftWidth(String text, double scale) {
        return (int) Math.round(MinecraftFont.Font.getWidth(minecraftSafe(text)) * scale);
    }

    private static void drawMinecraftCentered(Graphics2D graphics, String text, int centerX, int y,
                                                double scale, boolean bold) {
        drawMinecraftText(graphics, text, centerX - minecraftWidth(text, scale) / 2, y, scale, bold);
    }

    private static void drawMinecraftText(Graphics2D graphics, String text, int x, int y,
                                           double scale, boolean bold) {
        String safe = minecraftSafe(text);
        double cursor = x;
        for (char character : safe.toCharArray()) {
            MapFont.CharacterSprite sprite = MinecraftFont.Font.getChar(character);
            if (sprite == null) continue;
            for (int column = 0; column < sprite.getWidth(); column++) {
                for (int row = 0; row < sprite.getHeight(); row++) {
                    if (sprite.get(row, column)) {
                        int left = (int) Math.round(cursor + column * scale);
                        int top = (int) Math.round(y + row * scale);
                        int right = (int) Math.round(cursor + (column + 1) * scale);
                        int bottom = (int) Math.round(y + (row + 1) * scale);
                        graphics.fillRect(left, top, Math.max(1, right - left) + (bold ? 1 : 0),
                            Math.max(1, bottom - top));
                    }
                }
            }
            cursor += (sprite.getWidth() + 1) * scale;
        }
    }

    private static String minecraftSafe(String text) {
        String safe = text.replace('★', '*');
        return MinecraftFont.Font.isValid(safe) ? safe : safe.replaceAll("[^\\x20-\\x7E]", "?");
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
