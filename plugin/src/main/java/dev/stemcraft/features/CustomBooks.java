package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
import dev.stemcraft.api.utils.SCString;
import dev.stemcraft.api.utils.chatmenu.SCChatMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomBooks implements STEMCraftFeature {
    private static STEMCraftAPI api;
    private List<String> bookNames = new ArrayList<>();

    private File booksDir;
    private final Map<String, File> bookFiles = new HashMap<>();
    private final Map<String, YamlConfiguration> books = new HashMap<>();

    /**
     * When feature is enabled
     */
    @Override
    public void onEnable(STEMCraftAPI api) {
        CustomBooks.api = api;

        booksDir = new File(STEMCraft.getInstance().getDataFolder(), "books");
        if (!booksDir.exists() && !booksDir.mkdirs()) {
            api.warn("Failed to create books directory: " + booksDir.getAbsolutePath());
        }

        loadAllBooks();
        buildCacheList();

        api.tabComplete().register("book", (player, args) -> {
            return this.bookNames;
        });

        api.registerCommand("book")
                .addTabCompletion("new")
                .addTabCompletion("save")
                .addTabCompletion("get", "{book}")
                .addTabCompletion("list")
                .addTabCompletion("show", "{book}", "{player}")
                .addTabCompletion("del", "{book}")
                .addTabCompletion("unlock")
                .setDescription("CUSTOM_BOOKS_DESCRIPTION")
                .setUsage("CUSTOM_BOOKS_USAGE")
                .setPermission("stemcraft.book")
                .setExecutor((unused, cmd, ctx) -> {
                    // Check there are args
                    if(ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), "BOOK_USAGE");
                        return;
                    }

                    String sub = ctx.args().getFirst().toLowerCase();

                    // Sub command - new
                    if ("new".equals(sub)) {
                        if(ctx.isConsole()) {
                            cmd.error("COMMAND_PLAYER_ONLY");
                            return;
                        }

                        if(!ctx.hasPermission("stemcraft.book.edit")) {
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
                    } else if ("save".equals(sub)) {
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

                        ItemStack item = ctx.getSenderAsPlayer().getInventory().getItemInMainHand();
                        if (!Objects.equals(item.getType().toString(), "BOOK_AND_QUILL")
                                && !Objects.equals(item.getType().toString(), "WRITABLE_BOOK")) {
                            cmd.error(ctx.getSender(), "BOOK_SAVE_NOT_WRITABLE");
                            return;
                        }

                        String author = ctx.args().get(1);
                        String title = String.join(" ", ctx.args().subList(2, ctx.args().size()));
                        String name = generateName(title);

                        BookMeta meta = (BookMeta) item.getItemMeta();
                        List<String> bookPages = meta.getPages();
                        List<String> newPages = new ArrayList<>(bookPages);

                        for (int i = 0; i < newPages.size(); i++) {
                            String originalPage = newPages.get(i);
                            newPages.set(i, SCString.colouriseToSection(originalPage));
                        }

                        meta.setAuthor(SCString.colouriseToSection(author));
                        meta.setTitle(SCString.colouriseToSection(title));
                        meta.setPages(newPages);

                        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                        book.setItemMeta(meta);

                        ctx.getSenderAsPlayer().getInventory().setItemInMainHand(book);

                        try {
                            boolean existed = books.containsKey(name);

                            File outFile = new File(booksDir, name + ".yml");
                            YamlConfiguration cfg = new YamlConfiguration();
                            cfg.set("author", author);
                            cfg.set("title", title);
                            cfg.set("pages", newPages);
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
                    } else if ("get".equals(sub)) {
                        if(ctx.isConsole()) {
                            cmd.error("COMMAND_PLAYER_ONLY");
                            return;
                        }

                        if(ctx.args().size() < 2) {
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
                    } else if ("list".equals(sub)) {
                        if (ctx.isConsole()) {
                            cmd.error("COMMAND_PLAYER_ONLY");
                            return;
                        }
                        buildCacheList();

                        int page = ctx.getArgAsInt(2, 1);
                        SCChatMenu.render(ctx.getSenderAsPlayer(), "BOOK_LIST_TITLE", "book list", page, bookNames.size(), (start, count, isPlayer) -> {
                            ctx.info("Start: {start}, Count: {count}",
                                    "start", String.valueOf(start),
                                    "count", String.valueOf(count)
                            );

                            List<Component> lines = new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                String bookName = bookNames.get(i + start);
                                String lineText = (i + 1) + ". " + bookName + " ";
                                Component line = Component.text((i + 1) + ". ", NamedTextColor.WHITE)
                                        .append(Component.text(bookName, NamedTextColor.YELLOW))
                                        .append(Component.text(" "));

                                if (isPlayer) {
                                    line = line.append(
                                            Component.text("[get]", NamedTextColor.GREEN)
                                            .clickEvent(ClickEvent.runCommand("/book get " + bookName))
                                            .hoverEvent(HoverEvent.showText(
                                                    Component.text(api.locale().get("BOOK_LIST_GET_HOVER", "name", bookName))
                                            ))
                                    );

                                    line = line.append(
                                            Component.text(" [show]", NamedTextColor.AQUA)
                                            .clickEvent(ClickEvent.runCommand("/book show " + bookName))
                                            .hoverEvent(HoverEvent.showText(
                                                    Component.text(api.locale().get("BOOK_LIST_SHOW_HOVER", "name", bookName))
                                            ))
                                    );
                                }

                                lines.add(line);
                            }

                            return lines;
                        }, "NO_BOOKS_FOUND");

                        // Sub command - show
                    } else if ("show".equals(sub)) {
                        if(ctx.args().size() < 2) {
                            cmd.error(ctx.getSender(), "BOOK_USAGE_SHOW");
                            return;
                        }

                        if(ctx.isConsole() && ctx.args().size() < 3) {
                            cmd.error(ctx.getSender(), "CONSOLE_PLAYER_REQUIRED");
                            return;
                        }

                        Player targetPlayer = ctx.getArgAsPlayerOrSender(3);
                        if(targetPlayer == null) {
                            cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(3));
                            return;
                        }

                        ItemStack book = this.getBook(ctx.args().get(1));
                        if (book != null) {
                            if (SCPlayer.isBedrock(targetPlayer)) {
                                api.items().addAttrib(book, "destroy-on-drop", 1);

                                SCPlayer.givePlayerItem(targetPlayer, book);

                                String title = getBookTitle(book);
                                cmd.info(targetPlayer, "BOOK_GIVEN_WITH_TITLE", "title", title);
                            } else {
                                targetPlayer.openBook(book);
                            }
                        } else {
                            cmd.error(ctx.getSender(), "BOOK_NOT_FOUND");
                        }

                        // Sub command - del
                    } else if ("del".equals(sub)) {
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
                    } else if ("unlock".equals(sub)) {
                        if(ctx.isConsole()) {
                            cmd.error("COMMAND_PLAYER_ONLY");
                            return;
                        }

                        if(!ctx.hasPermission("stemcraft.book.edit")) {
                            cmd.error("COMMAND_NO_PERMISSION");
                            return;
                        }

                        ItemStack item = ctx.getSenderAsPlayer().getInventory().getItemInMainHand();
                        if (item.getType() == Material.WRITTEN_BOOK) {
                            Material material = Material.getMaterial("BOOK_AND_QUILL");
                            if (material == null)
                                material = Material.getMaterial("WRITABLE_BOOK");
                            if (material == null)
                                throw new UnsupportedOperationException("Something went wrong with Bukkit Material!");

                            ItemStack book = new ItemStack(material);
                            BookMeta meta = (BookMeta) item.getItemMeta();
                            List<String> bookPages = meta.getPages();
                            List<String> newPages = new ArrayList<>(bookPages);

                            for (int i = 0; i < newPages.size(); i++) {
                                String originalPage = newPages.get(i);

                                String parsedPage = SCString.untranslateColorCodes(originalPage);
                                newPages.set(i, parsedPage);
                            }

                            meta.setPages(newPages);
                            book.setItemMeta(meta);
                            ctx.getSenderAsPlayer().getInventory().setItemInMainHand(book);
                            cmd.success(ctx.getSender(), "BOOK_UNLOCK_SUCCESSFUL");
                        } else {
                            cmd.error(ctx.getSender(), "BOOK_UNLOCK_NOT_BOOK");
                        }
                    } else {
                        cmd.error(ctx.getSender(), "BOOK_UNKNOWN_OPTION");
                    }

                    this.buildCacheList();
                })
                .register(STEMCraft.getInstance());
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
            if (key.isEmpty()) continue;

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            books.put(key, cfg);
            bookFiles.put(key, f);
        }
    }

    /**
     * Get Book item from database.
     *
     * @param name
     * @return
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

        meta.setAuthor(author);
        meta.setTitle(title);

        if (!pages.isEmpty()) meta.setPages(pages);

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
     *
     * @param player
     * @param name
     */
    public void showBook(Player player, String name) {
        ItemStack book = this.getBook(name);
        if (book != null) {
            if (SCPlayer.isBedrock(player)) {
                api.items().addAttrib(book, "destroy-on-drop", 1);

                if (SCPlayer.givePlayerItem(player, book)) {
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
     *
     * @param name
     * @return
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

        return api.locale().get("BOOK_NO_TITLE");
    }
}

