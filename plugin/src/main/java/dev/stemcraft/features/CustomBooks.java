/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.service.chatmenu.SCChatMenuService;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Feature to manage custom books.
 */
public class CustomBooks extends BaseFeature {
    private List<String> bookNames = new ArrayList<>();

    private File booksDir;
    private final Map<String, File> bookFiles = new HashMap<>();
    private final Map<String, YamlConfiguration> books = new HashMap<>();

    /**
     * Constructor for CustomBooks.
     */
    public CustomBooks(STEMCraftAPI api) {
        super(api);
    }

    /**
     * When feature is enabled
     */
    @Override
    public void onEnable() {
        booksDir = new File(STEMCraft.getPlugin().getDataFolder(), "books");
        if (!booksDir.exists() && !booksDir.mkdirs()) {
            api.warn("Failed to create books directory: " + booksDir.getAbsolutePath());
        }

        loadAllBooks();
        buildCacheList();

        api.tabComplete().register("book", (player, args) -> this.bookNames);

        api.commands().create("book")
                .tabCompletion("new")
                .tabCompletion("save")
                .tabCompletion("get", "{book}")
                .tabCompletion("list")
                .tabCompletion("show", "{book}", "{player}")
                .tabCompletion("del", "{book}")
                .tabCompletion("unlock")
                .description("CUSTOM_BOOKS_DESCRIPTION")
                .usage("CUSTOM_BOOKS_USAGE")
                .permission("stemcraft.book")
                .executor((unused, cmd, ctx) -> {
                    // Check there are args
                    if(ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), "BOOK_USAGE");
                        return;
                    }

                    String sub = ctx.args().getFirst().toLowerCase();

                    // Sub command - new
                    switch (sub) {
                        case "new" -> {
                            if (ctx.isConsole()) {
                                cmd.error("COMMAND_PLAYER_ONLY");
                                return;
                            }

                            if (!ctx.hasPermission("stemcraft.book.edit")) {
                                cmd.error("COMMAND_NO_PERMISSION");
                                return;
                            }

                            Material material = Material.getMaterial("BOOK_AND_QUILL");
                            if (material == null)
                                material = Material.getMaterial("WRITABLE_BOOK");
                            if (material == null)
                                throw new UnsupportedOperationException("Something went wrong with Bukkit Material!");

                            ItemStack item = new ItemStack(material);
                            PlayerInventory inventory = ctx.getSenderAsPlayer().getInventory();
                            Map<Integer, ItemStack> result = inventory.addItem(item);
                            if (!result.isEmpty()) {
                                cmd.error(ctx.getSender(), "BOOK_INVENTORY_FULL");
                            } else {
                                cmd.error(ctx.getSender(), "BOOK_GIVEN_NEW");
                            }

                            // Sub command - save
                        }
                        case "save" -> {
                            if (ctx.isConsole()) {
                                cmd.error("COMMAND_PLAYER_ONLY");
                                return;
                            }
                            if (!ctx.hasPermission("stemcraft.book.edit")) {
                                cmd.error("COMMAND_NO_PERMISSION");
                                return;
                            }
                            if (ctx.args().size() < 3) {
                                cmd.error(ctx.getSender(), "BOOK_USAGE_SAVE");
                                return;
                            }

                            Player player = ctx.getSenderAsPlayer();
                            ItemStack inHand = player.getInventory().getItemInMainHand();
                            Material type = inHand.getType();

                            boolean writable =
                                    type == Material.WRITABLE_BOOK
                                            || type.name().equals("BOOK_AND_QUILL"); // legacy name compatibility


                            if (!writable) {
                                cmd.error(ctx.getSender(), "BOOK_SAVE_NOT_WRITABLE");
                                return;
                            }

                            String authorRaw = ctx.args().get(1);
                            String titleRaw = String.join(" ", ctx.args().subList(2, ctx.args().size()));
                            String name = generateName(titleRaw);

                            if (!(inHand.getItemMeta() instanceof BookMeta srcMeta)) {
                                cmd.error(ctx.getSender(), "BOOK_SAVE_NOT_WRITABLE");
                                return;
                            }

                            // Convert pages: we receive Components, but players typed ampersand codes as plain text.
                            // 1) Keep a raw string copy for YAML storage.
                            // 2) Build coloured Components for the in-game WRITTEN_BOOK.
                            List<String> rawPages = new ArrayList<>(srcMeta.pages().size());
                            List<Component> displayPages = new ArrayList<>(srcMeta.pages().size());

                            for (Component page : srcMeta.pages()) {
                                // Raw text as typed by player, eg "Cats &eand &0Dogs"
                                rawPages.add(TextUtil.plain(page));

                                // Reinterpret ampersands into coloured component for display
                                displayPages.add(TextUtil.colourise(page));
                            }

                            // Build a WRITTEN_BOOK with component pages (Paper) and legacy strings for title/author
                            ItemStack written = new ItemStack(Material.WRITTEN_BOOK);
                            BookMeta meta = (BookMeta) written.getItemMeta();

                            // pages(...) returns a Book (adventure), so use the builder
                            meta = meta.toBuilder()
                                    .pages(displayPages)
                                    .build();

                            meta.setAuthor(TextUtil.colouriseToSection(authorRaw));
                            meta.setTitle(TextUtil.colouriseToSection(titleRaw));

                            written.setItemMeta(meta);
                            player.getInventory().setItemInMainHand(written);

                            try {
                                boolean existed = books.containsKey(name);

                                File outFile = new File(booksDir, name + ".yml");
                                YamlConfiguration cfg = new YamlConfiguration();
                                cfg.set("author", authorRaw);
                                cfg.set("title", titleRaw);
                                cfg.set("pages", rawPages);
                                cfg.save(outFile);

                                books.put(name, cfg);
                                bookFiles.put(name, outFile);

                                if (existed) {
                                    cmd.success(ctx.getSender(), "BOOK_SAVE_UPDATED", "name", name);
                                } else {
                                    cmd.success(ctx.getSender(), "BOOK_SAVE_NEW", "name", name);
                                }
                            } catch (IOException e) {
                                cmd.error(ctx.getSender(), "BOOK_SAVE_FAILED", e, "name", name);
                            }

                            // Sub command - get
                        }
                        case "get" -> {
                            if (ctx.isConsole()) {
                                cmd.error("COMMAND_PLAYER_ONLY");
                                return;
                            }

                            if (ctx.args().size() < 2) {
                                cmd.error(ctx.getSender(), "BOOK_USAGE_GET");
                                return;
                            }

                            ItemStack book = this.getBook(ctx.args().get(1));
                            if (book != null) {
                                Map<Integer, ItemStack> result = ctx.getSenderAsPlayer().getInventory().addItem(book);
                                if (!result.isEmpty()) {
                                    cmd.error(ctx.getSender(), "BOOK_INVENTORY_FULL");
                                } else {
                                    cmd.success(ctx.getSender(), "BOOK_GIVEN");
                                }
                            } else {
                                cmd.error(ctx.getSender(), "BOOK_NOT_FOUND");
                            }

                            // Sub command - list
                        }
                        case "list" -> {
                            if (ctx.isConsole()) {
                                cmd.error("COMMAND_PLAYER_ONLY");
                                return;
                            }
                            buildCacheList();

                            int page = ctx.getArgAsInt(2, 1);
                            SCChatMenuService.render(ctx.getSenderAsPlayer(), "BOOK_LIST_TITLE", "book list", page, bookNames.size(), (start, count, isPlayer) -> {
                                ctx.info("Start: {start}, Count: {count}",
                                        "start", String.valueOf(start),
                                        "count", String.valueOf(count)
                                );

                                List<Component> lines = new ArrayList<>();
                                for (int i = 0; i < count; i++) {
                                    String bookName = bookNames.get(i + start);
                                    Component line = Component.text((i + 1) + ". ", NamedTextColor.WHITE)
                                            .append(Component.text(bookName, NamedTextColor.YELLOW))
                                            .append(Component.text(" "));

                                    if (isPlayer) {
                                        line = line.append(
                                                Component.text("[get]", NamedTextColor.GREEN)
                                                        .clickEvent(ClickEvent.runCommand("/book get " + bookName))
                                                        .hoverEvent(HoverEvent.showText(
                                                                Component.text(
                                                                        PlaceholderUtil.apply(
                                                                                api.locales().resolve("BOOK_LIST_GET_HOVER"),
                                                                                "name", bookName
                                                                        )
                                                                )
                                                        ))
                                        );

                                        line = line.append(
                                                Component.text(" [show]", NamedTextColor.AQUA)
                                                        .clickEvent(ClickEvent.runCommand("/book show " + bookName))
                                                        .hoverEvent(HoverEvent.showText(
                                                                Component.text(
                                                                        PlaceholderUtil.apply(
                                                                                api.locales().resolve("BOOK_LIST_SHOW_HOVER"),
                                                                                "name", bookName
                                                                        )
                                                                )
                                                        ))
                                        );
                                    }

                                    lines.add(line);
                                }

                                return lines;
                            }, "NO_BOOKS_FOUND");

                            // Sub command - show
                        }
                        case "show" -> {
                            if (ctx.args().size() < 2) {
                                cmd.error(ctx.getSender(), "BOOK_USAGE_SHOW");
                                return;
                            }

                            if (ctx.isConsole() && ctx.args().size() < 3) {
                                cmd.error(ctx.getSender(), "CONSOLE_PLAYER_REQUIRED");
                                return;
                            }

                            Player targetPlayer = ctx.getArgAsPlayerOrSender(3);
                            if (targetPlayer == null) {
                                cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(3));
                                return;
                            }

                            ItemStack book = this.getBook(ctx.args().get(1));
                            if (book != null) {
                                if (PlayerUtil.isBedrock(targetPlayer)) {
                                    api.items().addAttrib(book, "destroy-on-drop", 1);

                                    PlayerUtil.give(targetPlayer, book);

                                    String title = getBookTitle(book);
                                    cmd.info(targetPlayer, "BOOK_GIVEN_WITH_TITLE", "title", title);
                                } else {
                                    targetPlayer.openBook(book);
                                }
                            } else {
                                cmd.error(ctx.getSender(), "BOOK_NOT_FOUND");
                            }

                            // Sub command - del
                        }
                        case "del" -> {
                            if (!ctx.hasPermission("stemcraft.book.edit")) {
                                cmd.error("COMMAND_NO_PERMISSION");
                                return;
                            }
                            if (ctx.args().size() < 2) {
                                cmd.error(ctx.getSender(), "BOOK_USAGE_DEL");
                                return;
                            }

                            String name = ctx.args().get(1);

                            if (!books.containsKey(name)) {
                                cmd.error(ctx.getSender(), "BOOK_NOT_FOUND");
                                return;
                            }

                            File f = bookFiles.get(name);
                            books.remove(name);
                            bookFiles.remove(name);

                            if (f != null && f.exists() && !f.delete()) {
                                cmd.error(ctx.getSender(), "BOOK_SAVE_FAILED",
                                        new IOException("Failed to delete: " + f.getAbsolutePath()),
                                        "name", name);
                                return;
                            }

                            cmd.success(ctx.getSender(), "BOOK_DELETE_SUCCESSFUL");

                            // Sub command - unlock
                        }
                        case "unlock" -> {
                            if (ctx.isConsole()) {
                                cmd.error("COMMAND_PLAYER_ONLY");
                                return;
                            }

                            if (!ctx.hasPermission("stemcraft.book.edit")) {
                                cmd.error("COMMAND_NO_PERMISSION");
                                return;
                            }

                            ItemStack item = ctx.getSenderAsPlayer().getInventory().getItemInMainHand();
                            if (item.getType() != Material.WRITTEN_BOOK) {
                                cmd.error(ctx.getSender(), "BOOK_UNLOCK_NOT_BOOK");
                                return;
                            }

                            Material material = Material.getMaterial("BOOK_AND_QUILL");
                            if (material == null) material = Material.WRITABLE_BOOK;

                            ItemStack book = new ItemStack(material);

                            BookMeta srcMeta = (BookMeta) item.getItemMeta();
                            List<Component> srcPages = srcMeta.pages();
                            List<Component> editablePages = new ArrayList<>(srcPages.size());

                            for (Component p : srcPages) {
                                editablePages.add(TextUtil.untranslateCodes(p));
                            }

                            BookMeta outMeta = (BookMeta) book.getItemMeta();
                            outMeta = outMeta.toBuilder()
                                    .pages(editablePages)
                                    .build();

                            book.setItemMeta(outMeta);
                            ctx.getSenderAsPlayer().getInventory().setItemInMainHand(book);
                            cmd.success(ctx.getSender(), "BOOK_UNLOCK_SUCCESSFUL");
                        }
                        default -> cmd.error(ctx.getSender(), "BOOK_UNKNOWN_OPTION");
                    }

                    this.buildCacheList();
                })
                .register(STEMCraft.getPlugin());
    }


    private void loadAllBooks() {
        books.clear();
        bookFiles.clear();

        if (booksDir == null || !booksDir.exists()) return;

        File[] files = booksDir.listFiles((dir, filename) -> filename.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            String filename = f.getName();
            if (filename.length() <= 4) continue;

            String key = filename.substring(0, filename.length() - 4);

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            books.put(key, cfg);
            bookFiles.put(key, f);
        }
    }

    /**
     * Get Book item from disk.
     */
    private ItemStack getBook(String name) {
        if (name == null || name.isEmpty()) return null;

        YamlConfiguration cfg = books.get(name);
        if (cfg == null) return null;

        String author = cfg.getString("author", "");
        String title = cfg.getString("title", "");
        List<String> pages = cfg.getStringList("pages");

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setAuthor(TextUtil.colouriseToSection(author));
        meta.setTitle(TextUtil.colouriseToSection(title));

        if (!pages.isEmpty()) {
            List<Component> displayPages = new ArrayList<>(pages.size());
            for (String p : pages) {
                // Stored as raw text with ampersands, convert to coloured component for display
                displayPages.add(TextUtil.colourise(Component.text(p)));
            }

            meta = meta.toBuilder()
                    .pages(displayPages)
                    .build();
        }

        book.setItemMeta(meta);
        return book;
    }

    private String generateName(String title) {
        // Remove non-alpha characters
        title = title.replaceAll("[^a-zA-Z0-9\\s]", "");

        // Replace spaces with dashes
        title = title.replace(" ", "-");

        // Convert to lowercase
        title = title.toLowerCase();

        return title;
    }

    /**
     * Build a book name cache list.
     */
    private void buildCacheList() {
        ArrayList<String> names = new ArrayList<>(books.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        bookNames = names;
    }

    /**
     * Present a book to a player.
     */
    public void showBook(Player player, String name) {
        ItemStack book = this.getBook(name);
        if (book != null) {
            if (PlayerUtil.isBedrock(player)) {
                api.items().addAttrib(book, "destroy-on-drop", 1);

                if (PlayerUtil.give(player, book)) {
                    String title = getBookTitle(book);
                    api.info(player, "BOOK_GIVEN_WITH_TITLE", "title", title);
                }
            } else {
                player.openBook(book);
            }
        } else {
            api.error(player, "BOOK_NOT_FOUND");
        }
    }

    /**
     * Check a book exists.
     */
    public Boolean bookExists(String name) {
        return name != null && books.containsKey(name);
    }

    /**
     * Get a Books title or the no title locale.
     *
     * @param item The book item.
     * @return The book title.
     */
    public static String getBookTitle(ItemStack item) {
        if (item.getType() == Material.WRITTEN_BOOK || item.getType() == Material.WRITABLE_BOOK) {
            BookMeta bookMeta = (BookMeta) item.getItemMeta();

            if (bookMeta != null && bookMeta.hasTitle()) {
                return bookMeta.getTitle();
            }
        }

        return STEMCraftAPI.api().locales().resolve("BOOK_NO_TITLE");
    }
}
