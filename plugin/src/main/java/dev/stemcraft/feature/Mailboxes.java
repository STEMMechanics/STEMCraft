package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.item.BedrockItemVisualDefinition;
import dev.stemcraft.api.service.item.CustomItemClientDefinition;
import dev.stemcraft.api.service.item.CustomItemDefinition;
import dev.stemcraft.api.service.item.CustomItemPlacementMode;
import dev.stemcraft.api.service.item.JavaItemVisualDefinition;
import dev.stemcraft.api.service.dialog.DialogResponse;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.mailbox.MailSendResult;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.api.service.placedobject.PlacedBlockRef;
import dev.stemcraft.api.service.placedobject.PlacedObject;
import dev.stemcraft.api.service.placedobject.PlacedObjectBlockLink;
import dev.stemcraft.api.service.placedobject.PlacedObjectCreateRequest;
import dev.stemcraft.api.service.placedobject.PlacedObjectEntityLink;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.api.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Mailboxes extends BaseFeature implements MailboxService {
    public static final String MAILBOX_ITEM_ID = "stemcraft-mailbox";
    public static final String MAILBOX_OBJECT_TYPE = "mailbox";
    private static final String MAILBOX_RECIPE_ID = "mailbox";
    private static final String DEFAULT_MAIL_HOLOGRAM_TEXT = ":mail_large:";
    private static final String SUPPORT_ROLE = "support";
    private static final String DISPLAY_ROLE = "display";
    private static final String INTERACTION_ROLE = "interaction";
    private static final String MAIL_HOLOGRAM_TYPE = "mailbox";
    private static final String LEGACY_MAIL_INDICATOR_ROLE = "mail-indicator";
    private static final String LEGACY_BEDROCK_MAIL_INDICATOR_ROLE = "mail-indicator-bedrock";
    private static final String LEGACY_CONTAINER_ROLE = "container";
    private static final String QUEUE_TASK_ID = "mailbox:delivery-queue";
    private static final Material MAILBOX_SUPPORT_MATERIAL = Material.COBBLESTONE_WALL;
    private static final int MAILBOX_MODEL_DATA = 46002;
    private static final int MAILBOX_INVENTORY_SIZE = 27;
    private static final float MAILBOX_DISPLAY_SCALE = 0.5f;
    private static final float MAILBOX_INTERACTION_WIDTH = 0.75f;
    private static final float MAILBOX_INTERACTION_HEIGHT = 0.5f;
    private static final long DEFAULT_DELIVERY_BASE_DELAY_TICKS = 20L * 60L * 60L;
    private static final long DEFAULT_QUEUE_PROCESS_PERIOD_TICKS = 20L;
    private static final long DEFAULT_FULL_NOTICE_COOLDOWN_TICKS = 20L * 60L * 60L;
    private static final String DEFAULT_PLACE_CLEARANCE_MESSAGE = "<red>This mailbox needs one clear block above the post.";
    private static final String DEFAULT_PLACE_FAILED_MESSAGE = "<red>Failed to place mailbox: {error}";
    private static final String DEFAULT_RECIPIENT_NOT_FOUND_MESSAGE = "<red>Could not find a player named '{recipient}'.";
    private static final String DEFAULT_RECIPIENT_REQUIRED_MESSAGE = "<red>Enter the name of the player you want to send this mail to.";
    private static final String DEFAULT_SELF_RECIPIENT_MESSAGE = "<red>You cannot send mail to yourself.";
    private static final String DEFAULT_TOO_MANY_ITEMS_MESSAGE = "<red>Leave enough room in the mailbox for the accompanying letter.";
    private static final String DEFAULT_QUEUED_MESSAGE = "<gold>Mail sent to {recipient}";
    private static final String DEFAULT_MAILBOX_FULL_MESSAGE = "<red>Your mailbox is full. Incoming mail is waiting for space.";
    private static final String DEFAULT_RECEIVED_MESSAGE = "<gold>You received mail from {sender}.";
    private static final String DEFAULT_DIALOG_TITLE = "Send mail";
    private static final String DEFAULT_DIALOG_NOTICE = "<yellow>This message and all items in the mailbox will be sent to the player below.</yellow>";
    private static final String DEFAULT_DIALOG_RECIPIENT_LABEL = "Send to";
    private static final String DEFAULT_DIALOG_MESSAGE_LABEL = "Message";
    private static final String DEFAULT_DIALOG_SEND_LABEL = "Send";
    private static final String DEFAULT_DIALOG_CANCEL_LABEL = "Cancel";
    private static final String DEFAULT_DIALOG_OPEN_FAILED = "<red>Could not open the mail dialog. Your items were returned to your mailbox.</red>";
    private static final String DEFAULT_DIALOG_CANCELLED = "<gray>Mail cancelled. Your items remain in your mailbox.</gray>";
    private final Map<UUID, Inventory> openMailboxInventories = new HashMap<>();
    private final Map<UUID, PendingMailDraft> pendingMailDrafts = new HashMap<>();
    private final Map<UUID, Long> mailboxFullNoticeCooldowns = new HashMap<>();
    private final Map<UUID, Boolean> mailIndicatorVisibility = new HashMap<>();
    private long deliveryBaseDelayTicks = DEFAULT_DELIVERY_BASE_DELAY_TICKS;
    private long queueProcessPeriodTicks = DEFAULT_QUEUE_PROCESS_PERIOD_TICKS;
    private long mailboxFullNoticeCooldownTicks = DEFAULT_FULL_NOTICE_COOLDOWN_TICKS;

    public Mailboxes(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadSettings();
        ensureStorage();
        startQueueProcessor();
        api.placedObjects().registerType(MAILBOX_OBJECT_TYPE);
        registerMailboxHolograms();
        registerCustomItems();
        registerMailboxRecipe();
        registerEvents();
        registerCommands();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
        registerMailboxRecipe();
        startQueueProcessor();
        registerMailboxHolograms();
    }

    @Override
    public void onDisable() {
        persistOpenMailboxInventories();
        persistPendingMailDrafts();
        mailIndicatorVisibility.clear();
        api.tasks().cancel(QUEUE_TASK_ID);
    }

    private void reloadSettings() {
        deliveryBaseDelayTicks = getConfigSection().getLong("delivery.base-delay", DEFAULT_DELIVERY_BASE_DELAY_TICKS);
        queueProcessPeriodTicks = Math.max(1L, getConfigSection().getLong("delivery.process-mail-queue", DEFAULT_QUEUE_PROCESS_PERIOD_TICKS));
        mailboxFullNoticeCooldownTicks = Math.max(20L, getConfigSection().getLong("delivery.mailbox-full-cooldown", DEFAULT_FULL_NOTICE_COOLDOWN_TICKS));
    }

    private void ensureStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS mailbox_mail_queue (
              id TEXT PRIMARY KEY,
              sender_uuid TEXT,
              sender_name TEXT,
              recipient_uuid TEXT NOT NULL,
              recipient_name TEXT NOT NULL,
              message TEXT NOT NULL DEFAULT '',
              source_world TEXT,
              source_x INTEGER,
              source_y INTEGER,
              source_z INTEGER,
              payload_base64 TEXT NOT NULL,
              queued_at INTEGER NOT NULL,
              deliver_after INTEGER NOT NULL,
              countdown_started_at INTEGER
            );
            """);
        api.database().execute("CREATE INDEX IF NOT EXISTS mailbox_mail_queue_recipient_idx ON mailbox_mail_queue (recipient_uuid);");
        api.database().execute("CREATE INDEX IF NOT EXISTS mailbox_mail_queue_due_idx ON mailbox_mail_queue (deliver_after, queued_at);");
        migrateQueueCountdownColumn();
        migrateQueueMessageColumn();
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS mailbox_notifications (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              message TEXT NOT NULL,
              created_at INTEGER NOT NULL
            );
            """);
        api.database().execute("CREATE INDEX IF NOT EXISTS mailbox_notifications_player_idx ON mailbox_notifications (player_uuid, id);");
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS mailbox_player_inventories (
              player_uuid TEXT PRIMARY KEY,
              payload_base64 TEXT NOT NULL,
              updated_at INTEGER NOT NULL
            );
            """);
    }

    private void migrateQueueCountdownColumn() {
        if (tableHasColumn("mailbox_mail_queue", "countdown_started_at")) {
            return;
        }

        if (api.database().execute("ALTER TABLE mailbox_mail_queue ADD COLUMN countdown_started_at INTEGER;")) {
            api.database().update(
                "UPDATE mailbox_mail_queue SET countdown_started_at = queued_at WHERE countdown_started_at IS NULL",
                null
            );
        }
    }

    private void migrateQueueMessageColumn() {
        if (!tableHasColumn("mailbox_mail_queue", "message")) {
            api.database().execute("ALTER TABLE mailbox_mail_queue ADD COLUMN message TEXT NOT NULL DEFAULT '';");
        }
    }

    private boolean tableHasColumn(@NotNull String tableName, @NotNull String columnName) {
        List<String> columns = new ArrayList<>();
        api.database().queryEach(
            "PRAGMA table_info(" + tableName + ")",
            null,
            rs -> columns.add(rs.getString("name"))
        );
        return columns.stream().anyMatch(columnName::equalsIgnoreCase);
    }

    private void startQueueProcessor() {
        api.tasks().cancel(QUEUE_TASK_ID);
        api.tasks().repeating(QUEUE_TASK_ID, queueProcessPeriodTicks, queueProcessPeriodTicks, this::processMailQueue);
    }

    private void registerCustomItems() {
        api.items().registerCustomItem(new CustomItemDefinition(
            MAILBOX_ITEM_ID,
            namedItem(Material.BARREL, "<gold>Mailbox"),
            CustomItemPlacementMode.MANAGED,
            MAILBOX_OBJECT_TYPE,
            new CustomItemClientDefinition(
                new JavaItemVisualDefinition(MAILBOX_MODEL_DATA, "stemcraft_mail:mailbox", "stemcraft_mail:item/mailbox", "stemcraft_mail:item/mailbox"),
                new BedrockItemVisualDefinition("stemcraft:mailbox", "mailbox", "stemcraft_mail:item/mailbox", "Mailbox")
            )
        ));
    }

    private void registerEvents() {
        api.events().register(BlockPlaceEvent.class, this::onPlaceMailbox, EventPriority.NORMAL, true);
        api.events().register(InventoryCloseEvent.class, this::onCloseMailboxInventory, EventPriority.NORMAL, false);
        api.events().register(BlockBreakEvent.class, this::onBreakMailbox, EventPriority.HIGHEST, false);
        api.events().register(PlayerInteractEvent.class, this::onInteractMailboxSupport, EventPriority.HIGHEST, false);
        api.events().register(PlayerInteractEntityEvent.class, this::onInteractMailboxEntity, EventPriority.HIGHEST, false);
        api.events().register(BlockExplodeEvent.class, event -> handleExplosion(event.blockList()), EventPriority.HIGHEST, false);
        api.events().register(EntityExplodeEvent.class, event -> handleExplosion(event.blockList()), EventPriority.HIGHEST, false);
        api.events().register(PlayerJoinEvent.class, event -> {
            UUID playerId = event.getPlayer().getUniqueId();
            api.tasks().runLater(20L, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }
                processMailQueue();
                deliverPendingNotifications(player);
                refreshMailIndicatorVisibility(player);
            });
        }, EventPriority.NORMAL, false);
        api.events().register(PlayerQuitEvent.class,
            event -> mailIndicatorVisibility.remove(event.getPlayer().getUniqueId()));
    }

    private void registerMailboxRecipe() {
        api.recipes().remove("stemcraft:" + MAILBOX_RECIPE_ID);

        ItemStack mailbox = api.items().createCustomItem(MAILBOX_ITEM_ID);
        if (mailbox == null) {
            return;
        }

        api.recipes().addShaped(
            MAILBOX_RECIPE_ID,
            mailbox,
            new String[] {
                " B ",
                " W "
            },
            Map.of(
                'B', Material.BARREL,
                'W', MAILBOX_SUPPORT_MATERIAL
            )
        );
    }

    private void registerCommands() {
        api.commands().create("mailbox")
            .tabCompletion("queue")
            .tabCompletion("queue", "{number}")
            .tabCompletion("send", "{player}")
            .tabCompletion("send", "{player}", "")
            .tabCompletion("view")
            .tabCompletion("release")
            .tabCompletion("hold")
            .tabCompletion("delete")
            .tabCompletion("item", "delete")
            .tabCompletion("item", "amount")
            .description("Mailbox queue administration")
            .usage("/mailbox send <player> [message] | /mailbox queue [page] | /mailbox view <queue-id> [page] | /mailbox release <queue-id> | /mailbox hold <queue-id> [ticks] | /mailbox delete <queue-id> | /mailbox item <delete|amount> ...")
            .permission("stemcraft.mailbox")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    renderQueueList(ctx, 1);
                    return;
                }

                String sub = ctx.args().getFirst().toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "send" -> handleSendCommand(ctx);
                    case "queue" -> renderQueueList(ctx, ctx.args().size() >= 2 ? parsePage(ctx.args().get(1)) : 1);
                    case "view" -> handleQueueViewCommand(ctx);
                    case "release" -> handleQueueReleaseCommand(ctx);
                    case "hold" -> handleQueueHoldCommand(ctx);
                    case "delete" -> handleQueueDeleteCommand(ctx);
                    case "item" -> handleQueueItemCommand(ctx);
                    default -> ctx.error("Usage: /mailbox send <player> [message] | /mailbox queue [page] | /mailbox view <queue-id> [page] | /mailbox release <queue-id> | /mailbox hold <queue-id> [ticks] | /mailbox delete <queue-id> | /mailbox item <delete|amount> ...");
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void handleSendCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.error("Usage: /mailbox send <player> [message]");
            return;
        }

        String requestedRecipient = ctx.args().get(1);
        Recipient recipient = resolveRecipient(requestedRecipient);
        if (recipient == null) {
            ctx.error("Could not find a player named '{recipient}'.", "recipient", requestedRecipient);
            return;
        }

        Player sender = ctx.asPlayer();
        MailSendRequest request = sender == null
            ? new MailSendRequest("STEMCraft", recipient.uuid(), ctx.getArgsAsString(3), List.of())
            : new MailSendRequest(sender.getUniqueId(), recipient.uuid(), ctx.getArgsAsString(3),
                List.of(), sender.getLocation());
        MailSendResult result = send(request);
        if (!result.queued()) {
            ctx.error("Could not send mail: {error}", "error", result.message());
            return;
        }

        ctx.success("Mail sent to {recipient}.", "recipient", recipient.name());
    }

    private void renderQueueList(@NotNull dev.stemcraft.api.command.CommandContext ctx, int page) {
        List<QueuedMail> queue = listQueuedMail();
        ChatMenuUtil.render(
            ctx.getSender(),
            Component.text("Mailbox Queue", NamedTextColor.AQUA),
            "mailbox queue",
            Math.max(1, page),
            queue.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, queue.size());
                for (int i = start; i < end; i++) {
                    QueuedMail queuedMail = queue.get(i);
                    Component line = Component.text(shortQueueId(queuedMail.id()), NamedTextColor.YELLOW)
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(Component.text(Objects.requireNonNullElse(queuedMail.senderName(), "someone"), NamedTextColor.AQUA))
                        .append(Component.text(" -> ", NamedTextColor.GRAY))
                        .append(Component.text(queuedMail.recipientName(), NamedTextColor.GOLD))
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(Component.text("(" + summarizeInventory(deserializeInventory(queuedMail.payloadBase64(), MAILBOX_INVENTORY_SIZE)) + ")", NamedTextColor.GRAY))
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(Component.text(formatQueueStatus(queuedMail), queueStatusColor(queuedMail)));

                    if (isPlayer) {
                        line = line.append(Component.text(" "))
                            .append(actionButton("[View]", NamedTextColor.BLUE,
                                ClickEvent.runCommand("/mailbox view " + queuedMail.id()),
                                "View queued items"))
                            .append(Component.text(" "))
                            .append(actionButton("[Release]", NamedTextColor.GREEN,
                                ClickEvent.runCommand("/mailbox release " + queuedMail.id()),
                                "Release immediately"))
                            .append(Component.text(" "))
                            .append(actionButton("[Hold]", NamedTextColor.GOLD,
                                ClickEvent.suggestCommand("/mailbox hold " + queuedMail.id() + " " + deliveryBaseDelayTicks),
                                "Hold this mail for more ticks"))
                            .append(Component.text(" "))
                            .append(actionButton("[Del]", NamedTextColor.RED,
                                ClickEvent.runCommand("/mailbox delete " + queuedMail.id()),
                                "Delete this queued mail"));
                    }
                    lines.add(line);
                }
                return lines;
            },
            "No queued mail found."
        );
    }

    private void handleQueueViewCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.error("Usage: /mailbox view <queue-id> [page]");
            return;
        }

        UUID queueId = parseQueueId(ctx.args().get(1));
        if (queueId == null) {
            ctx.error("Invalid queue id.");
            return;
        }

        QueuedMail queuedMail = findQueuedMail(queueId);
        if (queuedMail == null) {
            ctx.error("No queued mail found for that id.");
            return;
        }

        ItemStack[] payload = deserializeInventory(queuedMail.payloadBase64(), MAILBOX_INVENTORY_SIZE);
        List<QueuedMailSlot> slots = queuedMailSlots(payload);
        int page = ctx.args().size() >= 3 ? parsePage(ctx.args().get(2)) : 1;

        ChatMenuUtil.render(
            ctx.getSender(),
            Component.text("Queued Mail " + shortQueueId(queueId), NamedTextColor.AQUA),
            "mailbox view " + queueId,
            Math.max(1, page),
            slots.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, slots.size());
                for (int i = start; i < end; i++) {
                    QueuedMailSlot slot = slots.get(i);
                    Component line = Component.text("#" + (slot.slotIndex() + 1), NamedTextColor.YELLOW)
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(describeItem(slot.item()), NamedTextColor.AQUA));
                    if (isPlayer) {
                        line = line.append(Component.text(" "))
                            .append(actionButton("[Del]", NamedTextColor.RED,
                                ClickEvent.runCommand("/mailbox item delete " + queueId + " " + (slot.slotIndex() + 1)),
                                "Delete this queued item"))
                            .append(Component.text(" "))
                            .append(actionButton("[Amt]", NamedTextColor.GOLD,
                                ClickEvent.suggestCommand("/mailbox item amount " + queueId + " " + (slot.slotIndex() + 1) + " " + slot.item().getAmount()),
                                "Edit queued item amount"));
                    }
                    lines.add(line);
                }
                return lines;
            },
            "No queued items found."
        );
    }

    private void handleQueueReleaseCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.error("Usage: /mailbox release <queue-id>");
            return;
        }

        UUID queueId = parseQueueId(ctx.args().get(1));
        if (queueId == null) {
            ctx.error("Invalid queue id.");
            return;
        }
        if (findQueuedMail(queueId) == null) {
            ctx.error("No queued mail found for that id.");
            return;
        }

        long now = System.currentTimeMillis();
        api.database().update(
            "UPDATE mailbox_mail_queue SET countdown_started_at = ?, deliver_after = ? WHERE id = ?",
            ps -> {
                ps.setLong(1, now);
                ps.setLong(2, now);
                ps.setString(3, queueId.toString());
            }
        );
        api.tasks().nextTick(this::processMailQueue);
        ctx.success("Released queued mail " + shortQueueId(queueId) + ".");
    }

    private void handleQueueHoldCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.error("Usage: /mailbox hold <queue-id> [ticks]");
            return;
        }

        UUID queueId = parseQueueId(ctx.args().get(1));
        if (queueId == null) {
            ctx.error("Invalid queue id.");
            return;
        }
        if (findQueuedMail(queueId) == null) {
            ctx.error("No queued mail found for that id.");
            return;
        }

        long holdTicks = deliveryBaseDelayTicks;
        if (ctx.args().size() >= 3) {
            try {
                holdTicks = Math.max(0L, Long.parseLong(ctx.args().get(2)));
            } catch (NumberFormatException ex) {
                ctx.error("Hold ticks must be a number.");
                return;
            }
        }

        long now = System.currentTimeMillis();
        long appliedHoldTicks = holdTicks;
        api.database().update(
            "UPDATE mailbox_mail_queue SET countdown_started_at = ?, deliver_after = ? WHERE id = ?",
            ps -> {
                ps.setLong(1, now);
                ps.setLong(2, now + (appliedHoldTicks * 50L));
                ps.setString(3, queueId.toString());
            }
        );
        ctx.success("Held queued mail " + shortQueueId(queueId) + " for " + holdTicks + " ticks.");
    }

    private void handleQueueDeleteCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.error("Usage: /mailbox delete <queue-id>");
            return;
        }

        UUID queueId = parseQueueId(ctx.args().get(1));
        if (queueId == null) {
            ctx.error("Invalid queue id.");
            return;
        }
        if (findQueuedMail(queueId) == null) {
            ctx.error("No queued mail found for that id.");
            return;
        }

        api.database().update("DELETE FROM mailbox_mail_queue WHERE id = ?", ps -> ps.setString(1, queueId.toString()));
        ctx.success("Deleted queued mail " + shortQueueId(queueId) + ".");
    }

    private void handleQueueItemCommand(@NotNull dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 4) {
            ctx.error("Usage: /mailbox item <delete|amount> <queue-id> <slot> [amount]");
            return;
        }

        String action = ctx.args().get(1).toLowerCase(Locale.ROOT);
        UUID queueId = parseQueueId(ctx.args().get(2));
        if (queueId == null) {
            ctx.error("Invalid queue id.");
            return;
        }

        QueuedMail queuedMail = findQueuedMail(queueId);
        if (queuedMail == null) {
            ctx.error("No queued mail found for that id.");
            return;
        }

        int slotNumber;
        try {
            slotNumber = Integer.parseInt(ctx.args().get(3));
        } catch (NumberFormatException ex) {
            ctx.error("Slot must be a number from 1 to " + MAILBOX_INVENTORY_SIZE + ".");
            return;
        }
        if (slotNumber < 1 || slotNumber > MAILBOX_INVENTORY_SIZE) {
            ctx.error("Slot must be a number from 1 to " + MAILBOX_INVENTORY_SIZE + ".");
            return;
        }

        ItemStack[] payload = deserializeInventory(queuedMail.payloadBase64(), MAILBOX_INVENTORY_SIZE);
        int slotIndex = slotNumber - 1;
        ItemStack slotItem = payload[slotIndex];
        if (slotItem == null || slotItem.getType().isAir()) {
            ctx.error("That queue slot is empty.");
            return;
        }

        switch (action) {
            case "delete" -> payload[slotIndex] = null;
            case "amount" -> {
                if (ctx.args().size() < 5) {
                    ctx.error("Usage: /mailbox item amount <queue-id> <slot> <amount>");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(ctx.args().get(4));
                } catch (NumberFormatException ex) {
                    ctx.error("Amount must be a number.");
                    return;
                }
                if (amount <= 0) {
                    payload[slotIndex] = null;
                } else {
                    ItemStack updated = slotItem.clone();
                    updated.setAmount(Math.min(amount, updated.getMaxStackSize()));
                    payload[slotIndex] = updated;
                }
            }
            default -> {
                ctx.error("Usage: /mailbox item <delete|amount> <queue-id> <slot> [amount]");
                return;
            }
        }

        if (!containsAnyItems(payload)) {
            api.database().update("DELETE FROM mailbox_mail_queue WHERE id = ?", ps -> ps.setString(1, queueId.toString()));
            ctx.success("Removed the last queued item and deleted queue " + shortQueueId(queueId) + ".");
            return;
        }

        saveQueuedMailPayload(queueId, payload);
        ctx.success("Updated queued mail " + shortQueueId(queueId) + ".");
    }

    private int parsePage(@NotNull String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private @Nullable UUID parseQueueId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private @NotNull List<QueuedMail> listQueuedMail() {
        List<QueuedMail> queue = new ArrayList<>();
        api.database().queryEach(
            "SELECT id, sender_uuid, sender_name, recipient_uuid, recipient_name, message, source_world, source_x, source_y, source_z, payload_base64, queued_at, deliver_after, countdown_started_at FROM mailbox_mail_queue ORDER BY queued_at ASC",
            null,
            rs -> queue.add(new QueuedMail(
                UUID.fromString(rs.getString("id")),
                parseUuid(rs.getString("sender_uuid")),
                rs.getString("sender_name"),
                UUID.fromString(rs.getString("recipient_uuid")),
                rs.getString("recipient_name"),
                rs.getString("message"),
                rs.getString("source_world"),
                (Integer) rs.getObject("source_x"),
                (Integer) rs.getObject("source_y"),
                (Integer) rs.getObject("source_z"),
                rs.getString("payload_base64"),
                rs.getLong("queued_at"),
                rs.getLong("deliver_after"),
                parseNullableLong(rs.getObject("countdown_started_at"))
            ))
        );
        return queue;
    }

    private @Nullable QueuedMail findQueuedMail(@NotNull UUID queueId) {
        return api.database().querySingleMapped(
            "SELECT id, sender_uuid, sender_name, recipient_uuid, recipient_name, message, source_world, source_x, source_y, source_z, payload_base64, queued_at, deliver_after, countdown_started_at FROM mailbox_mail_queue WHERE id = ?",
            ps -> ps.setString(1, queueId.toString()),
            rs -> new QueuedMail(
                UUID.fromString(rs.getString("id")),
                parseUuid(rs.getString("sender_uuid")),
                rs.getString("sender_name"),
                UUID.fromString(rs.getString("recipient_uuid")),
                rs.getString("recipient_name"),
                rs.getString("message"),
                rs.getString("source_world"),
                (Integer) rs.getObject("source_x"),
                (Integer) rs.getObject("source_y"),
                (Integer) rs.getObject("source_z"),
                rs.getString("payload_base64"),
                rs.getLong("queued_at"),
                rs.getLong("deliver_after"),
                parseNullableLong(rs.getObject("countdown_started_at"))
            )
        );
    }

    private void saveQueuedMailPayload(@NotNull UUID queueId, ItemStack @NotNull [] payload) {
        api.database().update(
            "UPDATE mailbox_mail_queue SET payload_base64 = ? WHERE id = ?",
            ps -> {
                ps.setString(1, serializeInventory(payload));
                ps.setString(2, queueId.toString());
            }
        );
    }

    private @NotNull List<QueuedMailSlot> queuedMailSlots(ItemStack @NotNull [] payload) {
        List<QueuedMailSlot> slots = new ArrayList<>();
        for (int i = 0; i < payload.length; i++) {
            ItemStack item = payload[i];
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            slots.add(new QueuedMailSlot(i, item.clone()));
        }
        return slots;
    }

    private @NotNull String shortQueueId(@NotNull UUID queueId) {
        String raw = queueId.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private @NotNull String summarizeInventory(ItemStack @NotNull [] contents) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            parts.add(describeItem(item));
            if (parts.size() >= 3) {
                break;
            }
        }
        if (parts.isEmpty()) {
            return "empty";
        }
        return String.join(", ", parts);
    }

    private @NotNull String describeItem(@NotNull ItemStack item) {
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return item.getAmount() + "x " + name;
    }

    private @NotNull String formatQueueStatus(@NotNull QueuedMail queuedMail) {
        long now = System.currentTimeMillis();
        if (queuedMail.deliverAfter() <= now) {
            return "ready";
        }
        long seconds = Math.max(0L, (queuedMail.deliverAfter() - now) / 1000L);
        return TimeUtil.formatShortDuration(seconds);
    }

    private @NotNull NamedTextColor queueStatusColor(@NotNull QueuedMail queuedMail) {
        return queuedMail.deliverAfter() <= System.currentTimeMillis() ? NamedTextColor.GREEN : NamedTextColor.GRAY;
    }

    private @NotNull Component actionButton(@NotNull String label,
                                            @NotNull NamedTextColor color,
                                            @NotNull ClickEvent clickEvent,
                                            @NotNull String hoverText) {
        return Component.text(label, color)
            .clickEvent(clickEvent)
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)));
    }

    private void logSentMail(@NotNull Player sender, @NotNull OutgoingMailResult result) {
        Bukkit.getLogger().info("[mailbox] " + sender.getName()
            + " mailed to " + result.recipient().name()
            + ": " + summarizeInventory(result.items()));
    }

    private void logPickedUpMail(@NotNull Player player,
                                 ItemStack @NotNull [] initialContents,
                                 ItemStack @NotNull [] beforeCloseContents) {
        List<String> pickedUp = diffInventory(initialContents, beforeCloseContents);
        if (pickedUp.isEmpty()) {
            return;
        }
        Bukkit.getLogger().info("[mailbox] " + player.getName() + " picked up: " + String.join(", ", pickedUp));
    }

    private @NotNull List<String> diffInventory(ItemStack @NotNull [] before, ItemStack @NotNull [] after) {
        Map<Material, Integer> beforeTotals = materialTotals(before);
        Map<Material, Integer> afterTotals = materialTotals(after);
        List<String> pickedUp = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : beforeTotals.entrySet()) {
            int afterAmount = afterTotals.getOrDefault(entry.getKey(), 0);
            int diff = entry.getValue() - afterAmount;
            if (diff > 0) {
                pickedUp.add(diff + "x " + entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        }
        return pickedUp;
    }

    private @NotNull Map<Material, Integer> materialTotals(ItemStack @NotNull [] contents) {
        Map<Material, Integer> totals = new HashMap<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            totals.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return totals;
    }

    private void onPlaceMailbox(@NotNull BlockPlaceEvent event) {
        ItemStack placedItem = event.getItemInHand();
        if (!api.items().isCustomItemId(MAILBOX_ITEM_ID, placedItem)) {
            return;
        }

        Block supportBlock = event.getBlockPlaced();
        if (supportBlock.getType() != Material.BARREL) {
            return;
        }

        Player owner = event.getPlayer();
        Block displayBlock = supportBlock.getRelative(BlockFace.UP);
        if (!canPlaceMailboxDisplay(displayBlock)) {
            event.setCancelled(true);
            return;
        }

        try {
            createMailboxAssembly(owner.getUniqueId(), supportBlock);
        } catch (RuntimeException ex) {
            event.setCancelled(true);
            sendConfiguredMessage(owner, "place-failed", DEFAULT_PLACE_FAILED_MESSAGE, "error", ex.getMessage());
        }
    }

    private void createMailboxAssembly(@NotNull UUID ownerId, @NotNull Block supportBlock) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("custom_item_id", MAILBOX_ITEM_ID);

        BlockDisplay display = spawnMailboxDisplay(supportBlock);
        Interaction interaction = spawnMailboxInteraction(supportBlock);
        PlacedObject placedObject = null;

        try {
            placedObject = api.placedObjects().create(new PlacedObjectCreateRequest(
                MAILBOX_OBJECT_TYPE,
                null,
                PlacedBlockRef.of(supportBlock),
                List.of(new PlacedObjectBlockLink(PlacedBlockRef.of(supportBlock), SUPPORT_ROLE)),
                List.of(
                    new PlacedObjectEntityLink(display.getUniqueId(), DISPLAY_ROLE),
                    new PlacedObjectEntityLink(interaction.getUniqueId(), INTERACTION_ROLE)
                ),
                metadata
            ));
            supportBlock.setType(MAILBOX_SUPPORT_MATERIAL, false);
            registerMailboxHologram(placedObject);
        } catch (RuntimeException ex) {
            if (placedObject != null) {
                api.holograms().deleteDynamic(MAIL_HOLOGRAM_TYPE, placedObject.id().toString());
                api.placedObjects().delete(placedObject.id());
            }
            display.remove();
            interaction.remove();
            throw ex;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.playSound(display.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.1f);
        }
    }

    private @NotNull BlockDisplay spawnMailboxDisplay(@NotNull Block supportBlock) {
        Location location = supportBlock.getLocation().add(0.0d, 1.0d, 0.0d);
        return supportBlock.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(Material.BARREL.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTransformation(new Transformation(
                new Vector3f(0.25f, 0.0f, 0.25f),
                new Quaternionf(),
                new Vector3f(MAILBOX_DISPLAY_SCALE, MAILBOX_DISPLAY_SCALE, MAILBOX_DISPLAY_SCALE),
                new Quaternionf()
            ));
            entity.setViewRange(32.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
        });
    }

    private @NotNull Interaction spawnMailboxInteraction(@NotNull Block supportBlock) {
        Location location = supportBlock.getLocation().add(0.5d, 1.25d, 0.5d);
        return supportBlock.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(MAILBOX_INTERACTION_WIDTH);
            entity.setInteractionHeight(MAILBOX_INTERACTION_HEIGHT);
            entity.setResponsive(true);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
        });
    }

    private @NotNull Component mailIndicatorText() {
        String configured = getConfigSection().getString("hologram.text", DEFAULT_MAIL_HOLOGRAM_TEXT);
        return TextUtil.colourise(api.messages().tokens().apply(configured));
    }

    private void registerMailboxHolograms() {
        for (PlacedObject mailbox : api.placedObjects().findByType(MAILBOX_OBJECT_TYPE)) {
            boolean removedLegacyLinks = mailbox.entityLinks().removeIf(link -> {
                boolean legacy = LEGACY_MAIL_INDICATOR_ROLE.equalsIgnoreCase(link.role())
                    || LEGACY_BEDROCK_MAIL_INDICATOR_ROLE.equalsIgnoreCase(link.role());
                if (!legacy) {
                    return false;
                }
                Entity entity = Bukkit.getEntity(link.entityUuid());
                if (entity == null) {
                    return false;
                }
                entity.remove();
                return true;
            });
            if (removedLegacyLinks) {
                api.placedObjects().save(mailbox);
            }
            registerMailboxHologram(mailbox);
        }
    }

    private void registerMailboxHologram(@NotNull PlacedObject mailbox) {
        Location supportLocation = mailbox.primaryBlock().resolve();
        if (supportLocation == null || supportLocation.getWorld() == null) {
            return;
        }
        Location hologramLocation = supportLocation.clone().add(0.5D, 2.05D, 0.5D);
        api.holograms().createDynamic(
            MAIL_HOLOGRAM_TYPE,
            mailbox.id().toString(),
            hologramLocation,
            player -> mailIndicatorVisibility.computeIfAbsent(player.getUniqueId(), this::hasMail),
            player -> mailIndicatorText()
        );
    }

    private void refreshMailIndicatorVisibility(@NotNull Player player) {
        if (api.placedObjects() == null) {
            return;
        }
        mailIndicatorVisibility.put(player.getUniqueId(), hasMail(player.getUniqueId()));
        for (PlacedObject mailbox : api.placedObjects().findByType(MAILBOX_OBJECT_TYPE)) {
            api.holograms().refreshDynamic(MAIL_HOLOGRAM_TYPE, mailbox.id().toString(), player);
        }
    }

    private boolean canPlaceMailboxDisplay(@NotNull Block displayBlock) {
        return (displayBlock.getType().isAir() || displayBlock.isPassable()) && !displayBlock.isLiquid();
    }

    private @Nullable String normalizeRecipientName(@Nullable String rawRecipient) {
        if (rawRecipient == null) {
            return null;
        }

        String recipient = rawRecipient.trim();
        return recipient.isEmpty() ? null : recipient;
    }

    private @Nullable Recipient resolveRecipient(@NotNull String rawName) {
        dev.stemcraft.api.service.player.PlayerService.ResolvedPlayer resolved = api.players().resolveIdentity(rawName);
        return resolved == null ? null : new Recipient(resolved.uuid(), resolved.name());
    }

    @Override
    public @NotNull MailSendResult send(@NotNull MailSendRequest request) {
        dev.stemcraft.api.service.player.PlayerService.ResolvedPlayer recipient =
            api.players().resolveIdentityByUuid(request.recipientUuid());
        if (recipient == null) {
            return MailSendResult.failure("Could not resolve recipient UUID " + request.recipientUuid() + ".");
        }

        String senderName = request.senderName();
        if (request.senderUuid() != null) {
            dev.stemcraft.api.service.player.PlayerService.ResolvedPlayer sender =
                api.players().resolveIdentityByUuid(request.senderUuid());
            if (sender == null) {
                return MailSendResult.failure("Could not resolve sender UUID " + request.senderUuid() + ".");
            }
            senderName = sender.name();
        }
        senderName = TextUtil.stripColour(Objects.requireNonNullElse(senderName, "STEMCraft")).trim();
        if (senderName.isBlank()) {
            senderName = "STEMCraft";
        }
        String message = TextUtil.stripColour(request.message()).trim();
        List<ItemStack> suppliedItems = request.items().stream()
            .filter(item -> item != null && !item.getType().isAir() && item.getAmount() > 0)
            .map(ItemStack::clone)
            .toList();
        ItemStack[] outgoingItems = new ItemStack[suppliedItems.size() + 1];
        for (int i = 0; i < suppliedItems.size(); i++) {
            outgoingItems[i] = suppliedItems.get(i);
        }
        outgoingItems[suppliedItems.size()] = createMailLetter(senderName, message, suppliedItems);
        if (!canFitAll(new ItemStack[MAILBOX_INVENTORY_SIZE], outgoingItems)) {
            return MailSendResult.failure("Mail items and their accompanying letter do not fit in a mailbox.");
        }
        ItemStack[] packedItems = mergeContents(new ItemStack[MAILBOX_INVENTORY_SIZE], outgoingItems);

        queueMail(
            request.sourceLocation(),
            request.senderUuid(),
            senderName,
            new Recipient(recipient.uuid(), recipient.name()),
            message,
            packedItems
        );
        api.tasks().nextTick(this::processMailQueue);
        return MailSendResult.success();
    }

    @Override
    public boolean hasMail(@NotNull UUID playerUuid) {
        Inventory openInventory = findOpenMailboxInventoryForPlayer(playerUuid);
        if (openInventory != null) {
            return containsAnyItems(openInventory.getContents());
        }
        return containsAnyItems(loadPlayerInbox(playerUuid));
    }

    private @NotNull ItemStack createMailLetter(@NotNull String senderName,
                                                @NotNull String message,
                                                @NotNull List<ItemStack> suppliedItems) {
        ItemStack letter = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) letter.getItemMeta();
        String safeSender = senderName.isBlank() ? "STEMCraft" : senderName;
        meta.setTitle("Mail from " + safeSender.substring(0, Math.min(20, safeSender.length())));
        meta.setAuthor(safeSender.substring(0, Math.min(16, safeSender.length())));

        List<Component> pages = buildMailLetterPages(safeSender, message, suppliedItems);
        meta.addPages(pages.toArray(Component[]::new));
        letter.setItemMeta(meta);
        return letter;
    }

    private @NotNull List<Component> buildMailLetterPages(@NotNull String senderName,
                                                          @NotNull String message,
                                                          @NotNull List<ItemStack> suppliedItems) {
        List<Component> pages = new ArrayList<>();
        Component firstPage = Component.text("From: ", NamedTextColor.GRAY)
            .append(Component.text(senderName, NamedTextColor.GOLD));
        if (!message.isBlank()) {
            firstPage = firstPage.append(Component.newline()).append(Component.newline())
                .append(Component.text(message, NamedTextColor.DARK_GRAY));
        }
        pages.add(firstPage);

        Component itemPage = Component.text("Included items", NamedTextColor.GOLD)
            .append(Component.newline()).append(Component.newline());
        int lines = 2;
        if (suppliedItems.isEmpty()) {
            itemPage = itemPage.append(Component.text("No items", NamedTextColor.DARK_GRAY));
        }
        for (ItemStack item : suppliedItems) {
            if (lines >= 12) {
                pages.add(itemPage);
                itemPage = Component.empty();
                lines = 0;
            }
            itemPage = itemPage.append(Component.text(describeItem(item), NamedTextColor.DARK_GRAY))
                .append(Component.newline());
            lines++;
        }
        pages.add(itemPage);
        return pages;
    }

    private void queueMail(@Nullable Location sourceLocation,
                           @Nullable UUID senderUuid,
                           @NotNull String senderName,
                           @NotNull Recipient recipient,
                           @NotNull String message,
                           ItemStack @NotNull [] outgoingItems) {
        long queuedAt = System.currentTimeMillis();
        long deliverAfter = resolveDeliverAfter(queuedAt);
        String payload = serializeInventory(outgoingItems);

        api.database().update(
            "INSERT INTO mailbox_mail_queue (id, sender_uuid, sender_name, recipient_uuid, recipient_name, message, source_world, source_x, source_y, source_z, payload_base64, queued_at, deliver_after, countdown_started_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setString(1, UUID.randomUUID().toString());
                if (senderUuid == null) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, senderUuid.toString());
                }
                ps.setString(3, senderName);
                ps.setString(4, recipient.uuid().toString());
                ps.setString(5, recipient.name());
                ps.setString(6, message);
                if (sourceLocation == null || sourceLocation.getWorld() == null) {
                    ps.setNull(7, java.sql.Types.VARCHAR);
                    ps.setNull(8, java.sql.Types.INTEGER);
                    ps.setNull(9, java.sql.Types.INTEGER);
                    ps.setNull(10, java.sql.Types.INTEGER);
                } else {
                    ps.setString(7, sourceLocation.getWorld().getName());
                    ps.setInt(8, sourceLocation.getBlockX());
                    ps.setInt(9, sourceLocation.getBlockY());
                    ps.setInt(10, sourceLocation.getBlockZ());
                }
                ps.setString(11, payload);
                ps.setLong(12, queuedAt);
                ps.setLong(13, deliverAfter);
                ps.setLong(14, queuedAt);
            }
        );
    }

    private long resolveDeliverAfter(long queuedAt) {
        return queuedAt + (Math.max(0L, deliveryBaseDelayTicks) * 50L);
    }

    private void processMailQueue() {
        long now = System.currentTimeMillis();
        List<QueuedMail> readyMail = new ArrayList<>();
        api.database().queryEach(
            "SELECT id, sender_uuid, sender_name, recipient_uuid, recipient_name, message, source_world, source_x, source_y, source_z, payload_base64, queued_at, deliver_after, countdown_started_at "
                + "FROM mailbox_mail_queue WHERE countdown_started_at IS NULL OR deliver_after <= ? ORDER BY queued_at ASC",
            ps -> ps.setLong(1, now),
            rs -> readyMail.add(new QueuedMail(
                UUID.fromString(rs.getString("id")),
                parseUuid(rs.getString("sender_uuid")),
                rs.getString("sender_name"),
                UUID.fromString(rs.getString("recipient_uuid")),
                rs.getString("recipient_name"),
                rs.getString("message"),
                rs.getString("source_world"),
                (Integer) rs.getObject("source_x"),
                (Integer) rs.getObject("source_y"),
                (Integer) rs.getObject("source_z"),
                rs.getString("payload_base64"),
                rs.getLong("queued_at"),
                rs.getLong("deliver_after"),
                parseNullableLong(rs.getObject("countdown_started_at"))
            ))
        );

        for (QueuedMail queuedMail : readyMail) {
            attemptDelivery(queuedMail);
        }
    }

    private void attemptDelivery(@NotNull QueuedMail queuedMail) {
        if (queuedMail.countdownStartedAt() == null) {
            startDeferredCountdown(queuedMail);
            return;
        }

        ItemStack[] incomingItems = deserializeInventory(queuedMail.payloadBase64(), MAILBOX_INVENTORY_SIZE);
        PendingMailDraft recipientDraft = pendingMailDrafts.get(queuedMail.recipientUuid());
        ItemStack[] currentContents = loadPlayerInbox(queuedMail.recipientUuid());
        ItemStack[] capacityContents = recipientDraft == null
            ? currentContents
            : mergeContents(currentContents, recipientDraft.items());
        if (!canFitAll(capacityContents, incomingItems)) {
            notifyMailboxFull(queuedMail.recipientUuid());
            return;
        }

        ItemStack[] mergedContents = mergeContents(currentContents, incomingItems);
        Inventory openInventory = findOpenMailboxInventoryForPlayer(queuedMail.recipientUuid());
        if (openInventory != null) {
            openInventory.setContents(cloneInventoryContents(mergedContents, openInventory.getSize()));
        }
        savePlayerInbox(queuedMail.recipientUuid(), mergedContents);
        api.database().update("DELETE FROM mailbox_mail_queue WHERE id = ?", ps -> ps.setString(1, queuedMail.id().toString()));
        notifyMailReceived(queuedMail);
    }

    private void startDeferredCountdown(@NotNull QueuedMail queuedMail) {
        long countdownStartedAt = System.currentTimeMillis();
        long deliverAfter = resolveDeliverAfter(countdownStartedAt);
        api.database().update(
            "UPDATE mailbox_mail_queue SET countdown_started_at = ?, deliver_after = ? WHERE id = ?",
            ps -> {
                ps.setLong(1, countdownStartedAt);
                ps.setLong(2, deliverAfter);
                ps.setString(3, queuedMail.id().toString());
            }
        );
    }

    private void notifyMailboxFull(@NotNull UUID recipientUuid) {
        Player player = Bukkit.getPlayer(recipientUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextNoticeAt = mailboxFullNoticeCooldowns.getOrDefault(recipientUuid, 0L);
        if (now < nextNoticeAt) {
            return;
        }

        sendConfiguredMessage(player, "mailbox-full", DEFAULT_MAILBOX_FULL_MESSAGE);
        mailboxFullNoticeCooldowns.put(recipientUuid, now + (mailboxFullNoticeCooldownTicks * 50L));
    }

    private void notifyMailReceived(@NotNull QueuedMail queuedMail) {
        String senderName = queuedMail.senderName() == null || queuedMail.senderName().isBlank() ? "someone" : queuedMail.senderName();
        Player player = Bukkit.getPlayer(queuedMail.recipientUuid());
        if (player != null && player.isOnline()) {
            sendConfiguredMessage(player, "received", DEFAULT_RECEIVED_MESSAGE, "sender", senderName);
            return;
        }

        String notification = renderedConfiguredMessage(null, "received", DEFAULT_RECEIVED_MESSAGE, "sender", senderName);
        queueNotification(queuedMail.recipientUuid(), notification);
    }

    private void queueNotification(@NotNull UUID playerUuid, @NotNull String message) {
        api.database().update(
            "INSERT INTO mailbox_notifications (player_uuid, message, created_at) VALUES (?, ?, ?)",
            ps -> {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, message);
                ps.setLong(3, System.currentTimeMillis());
            }
        );
    }

    private void deliverPendingNotifications(@NotNull Player player) {
        List<PendingNotification> notifications = new ArrayList<>();
        api.database().queryEach(
            "SELECT id, message FROM mailbox_notifications WHERE player_uuid = ? ORDER BY id ASC",
            ps -> ps.setString(1, player.getUniqueId().toString()),
            rs -> notifications.add(new PendingNotification(rs.getLong("id"), rs.getString("message")))
        );

        for (PendingNotification notification : notifications) {
            api.messages().send(player, Objects.requireNonNullElse(notification.message(), ""));
            api.database().update("DELETE FROM mailbox_notifications WHERE id = ?", ps -> ps.setLong(1, notification.id()));
        }
    }

    private boolean containsAnyItems(ItemStack @Nullable [] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canFitAll(ItemStack @NotNull [] baseContents, ItemStack @NotNull [] additions) {
        ItemStack[] simulated = cloneInventoryContents(baseContents, MAILBOX_INVENTORY_SIZE);
        for (ItemStack item : additions) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (!placeItem(simulated, item.clone())) {
                return false;
            }
        }
        return true;
    }

    private ItemStack @NotNull [] mergeContents(ItemStack @NotNull [] baseContents, ItemStack @NotNull [] additions) {
        ItemStack[] merged = cloneInventoryContents(baseContents, MAILBOX_INVENTORY_SIZE);
        for (ItemStack item : additions) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            placeItem(merged, item.clone());
        }
        return merged;
    }

    private boolean placeItem(ItemStack @NotNull [] contents, @NotNull ItemStack item) {
        int remaining = item.getAmount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(item)) {
                continue;
            }

            int maxStack = existing.getMaxStackSize();
            int space = Math.max(0, maxStack - existing.getAmount());
            if (space <= 0) {
                continue;
            }

            int toMove = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + toMove);
            remaining -= toMove;
        }

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }

            int toMove = Math.min(item.getMaxStackSize(), remaining);
            ItemStack placed = item.clone();
            placed.setAmount(toMove);
            contents[i] = placed;
            remaining -= toMove;
        }

        item.setAmount(remaining);
        return remaining == 0;
    }

    private void onCloseMailboxInventory(@NotNull InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MailboxInventoryHolder mailboxHolder)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (hasOtherViewers(inventory, event)) {
            return;
        }

        openMailboxInventories.remove(mailboxHolder.sessionId());
        ItemStack[] beforeCloseContents = cloneInventoryContents(inventory.getContents(), inventory.getSize());
        if (event.getPlayer() instanceof Player player) {
            logPickedUpMail(player, mailboxHolder.initialContents(), beforeCloseContents);
            if (containsAnyItems(beforeCloseContents)) {
                inventory.clear();
                savePlayerInbox(mailboxHolder.playerUuid(), inventory.getContents());
                PendingMailDraft draft = new PendingMailDraft(mailboxHolder.mailboxLocation(), beforeCloseContents, "", "");
                pendingMailDrafts.put(player.getUniqueId(), draft);
                api.tasks().runLater(PlayerUtil.isBedrock(player) ? 10L : 1L, () -> showMailComposer(player, draft));
                return;
            }
        }
        savePlayerInbox(mailboxHolder.playerUuid(), inventory.getContents());
    }

    private void showMailComposer(@NotNull Player player, @NotNull PendingMailDraft draft) {
        if (!player.isOnline() || !pendingMailDrafts.containsKey(player.getUniqueId())) {
            return;
        }
        Component notice = TextUtil.colourise(configuredDialogText("notice", DEFAULT_DIALOG_NOTICE));
        boolean opened = api.dialogs().create("mailbox:compose")
            .title(TextUtil.colourise(configuredDialogText("title", DEFAULT_DIALOG_TITLE)))
            .body(notice)
            .textInput("recipient", TextUtil.colourise(configuredDialogText("recipient-label", DEFAULT_DIALOG_RECIPIENT_LABEL)), draft.recipient(), 64)
            .multilineTextInput("message", TextUtil.colourise(configuredDialogText("message-label", DEFAULT_DIALOG_MESSAGE_LABEL)), draft.message(), 256, 4)
            .submit(TextUtil.colourise(configuredDialogText("send-label", DEFAULT_DIALOG_SEND_LABEL)), response -> submitMailDraft(player, draft, response))
            .cancel(TextUtil.colourise(configuredDialogText("cancel-label", DEFAULT_DIALOG_CANCEL_LABEL)), () -> cancelMailDraft(player, draft))
            .open(player);
        if (!opened) {
            api.messages().send(player, configuredDialogText("open-failed", DEFAULT_DIALOG_OPEN_FAILED));
            cancelMailDraft(player, draft, false);
        }
    }

    private void submitMailDraft(@NotNull Player sender,
                                 @NotNull PendingMailDraft draft,
                                 @NotNull DialogResponse response) {
        PendingMailDraft activeDraft = pendingMailDrafts.get(sender.getUniqueId());
        if (activeDraft == null) {
            return;
        }
        String recipientName = Objects.requireNonNullElse(normalizeRecipientName(response.text("recipient")), "");
        String message = TextUtil.stripColour(response.text("message")).trim();
        if (recipientName.isBlank()) {
            sendConfiguredMessage(sender, "recipient-required", DEFAULT_RECIPIENT_REQUIRED_MESSAGE);
            PendingMailDraft updated = activeDraft.withInput(recipientName, message);
            pendingMailDrafts.put(sender.getUniqueId(), updated);
            showMailComposer(sender, updated);
            return;
        }
        Recipient recipient = resolveRecipient(recipientName);
        if (recipient == null) {
            sendConfiguredMessage(sender, "recipient-not-found", DEFAULT_RECIPIENT_NOT_FOUND_MESSAGE, "recipient", recipientName);
            cancelMailDraft(sender, activeDraft, false);
            return;
        }
        if (recipient.uuid().equals(sender.getUniqueId())) {
            sendConfiguredMessage(sender, "self-recipient", DEFAULT_SELF_RECIPIENT_MESSAGE);
            PendingMailDraft updated = activeDraft.withInput(recipientName, message);
            pendingMailDrafts.put(sender.getUniqueId(), updated);
            showMailComposer(sender, updated);
            return;
        }

        MailSendResult sendResult = send(new MailSendRequest(
            sender.getUniqueId(),
            recipient.uuid(),
            message,
            java.util.Arrays.stream(activeDraft.items())
                .filter(Objects::nonNull)
                .toList(),
            activeDraft.sourceLocation()
        ));
        if (!sendResult.queued()) {
            sendConfiguredMessage(sender, "too-many-items", DEFAULT_TOO_MANY_ITEMS_MESSAGE);
            PendingMailDraft updated = activeDraft.withInput(recipientName, message);
            pendingMailDrafts.put(sender.getUniqueId(), updated);
            showMailComposer(sender, updated);
            return;
        }
        pendingMailDrafts.remove(sender.getUniqueId());
        sendConfiguredMessage(sender, "queued", DEFAULT_QUEUED_MESSAGE, "recipient", recipient.name());
        logSentMail(sender, new OutgoingMailResult(recipient, activeDraft.items()));
    }

    private void cancelMailDraft(@NotNull Player player, @NotNull PendingMailDraft draft) {
        cancelMailDraft(player, draft, true);
    }

    private void cancelMailDraft(@NotNull Player player, @NotNull PendingMailDraft draft, boolean notifyPlayer) {
        PendingMailDraft activeDraft = pendingMailDrafts.remove(player.getUniqueId());
        if (activeDraft == null) {
            return;
        }
        ItemStack[] currentInbox = loadPlayerInbox(player.getUniqueId());
        savePlayerInbox(player.getUniqueId(), mergeContents(currentInbox, activeDraft.items()));
        if (notifyPlayer) {
            api.messages().send(player, configuredDialogText("cancelled", DEFAULT_DIALOG_CANCELLED));
        }
    }

    private void persistOpenMailboxInventories() {
        for (Inventory inventory : new ArrayList<>(openMailboxInventories.values())) {
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof MailboxInventoryHolder mailboxHolder) {
                savePlayerInbox(mailboxHolder.playerUuid(), inventory.getContents());
            }
        }
        openMailboxInventories.clear();
    }

    private void persistPendingMailDrafts() {
        pendingMailDrafts.forEach((playerUuid, draft) ->
            savePlayerInbox(playerUuid, mergeContents(loadPlayerInbox(playerUuid), draft.items())));
        pendingMailDrafts.clear();
    }

    private boolean hasOtherViewers(@NotNull Inventory inventory, @NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof org.bukkit.entity.HumanEntity closingViewer)) {
            return !inventory.getViewers().isEmpty();
        }

        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (!viewer.getUniqueId().equals(closingViewer.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void onBreakMailbox(@NotNull BlockBreakEvent event) {
        PlacedObject mailbox = api.placedObjects().findByBlock(event.getBlock().getLocation());
        if (mailbox == null || !MAILBOX_OBJECT_TYPE.equalsIgnoreCase(mailbox.typeId())) {
            return;
        }

        teardownMailbox(event.getBlock(), mailbox, true);
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    private void onInteractMailboxSupport(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        PlacedObject mailbox = api.placedObjects().findByBlock(clickedBlock.getLocation());
        if (mailbox == null || !MAILBOX_OBJECT_TYPE.equalsIgnoreCase(mailbox.typeId())) {
            return;
        }
        if (!isMailboxAccessBlock(mailbox, clickedBlock)) {
            return;
        }

        event.setCancelled(true);
        openMailbox(event.getPlayer(), mailbox);
    }

    private void onInteractMailboxEntity(@NotNull PlayerInteractEntityEvent event) {
        PlacedObject mailbox = api.placedObjects().findByEntity(event.getRightClicked().getUniqueId());
        if (mailbox == null || !MAILBOX_OBJECT_TYPE.equalsIgnoreCase(mailbox.typeId())) {
            return;
        }

        event.setCancelled(true);
        openMailbox(event.getPlayer(), mailbox);
    }

    private void handleExplosion(@NotNull List<Block> blocks) {
        for (Block block : new ArrayList<>(blocks)) {
            PlacedObject mailbox = api.placedObjects().findByBlock(block.getLocation());
            if (mailbox == null || !MAILBOX_OBJECT_TYPE.equalsIgnoreCase(mailbox.typeId())) {
                continue;
            }
            blocks.remove(block);
            teardownMailbox(block, mailbox, true);
        }
    }

    private void teardownMailbox(@NotNull Block block, @NotNull PlacedObject mailbox, boolean dropMailboxItem) {
        Block supportBlock = resolveLinkedBlock(mailbox, SUPPORT_ROLE);
        if (supportBlock == null) {
            supportBlock = block;
        }

        Block legacyContainerBlock = resolveLinkedBlock(mailbox, LEGACY_CONTAINER_ROLE);
        closeOpenMailboxSession(mailbox.id());
        api.holograms().deleteDynamic(MAIL_HOLOGRAM_TYPE, mailbox.id().toString());

        for (PlacedObjectEntityLink link : mailbox.entityLinks()) {
            Entity entity = Bukkit.getEntity(link.entityUuid());
            if (entity != null) {
                entity.remove();
            }
        }
        api.placedObjects().delete(mailbox.id());

        if (legacyContainerBlock != null && legacyContainerBlock.getType() != Material.AIR) {
            legacyContainerBlock.setType(Material.AIR, false);
        }
        if (supportBlock != null && supportBlock.getType() != Material.AIR) {
            supportBlock.setType(Material.AIR, false);
        }

        if (dropMailboxItem) {
            ItemStack item = api.items().createCustomItem(MAILBOX_ITEM_ID);
            if (item != null) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
            }
        }
    }

    private void openMailbox(@NotNull Player player, @NotNull PlacedObject mailbox) {
        PendingMailDraft pendingDraft = pendingMailDrafts.get(player.getUniqueId());
        if (pendingDraft != null) {
            showMailComposer(player, pendingDraft);
            return;
        }
        ItemStack[] initialContents = loadPlayerInbox(player.getUniqueId());
        MailboxInventoryHolder holder = newMailboxInventoryHolder(UUID.randomUUID(), mailbox.id(), player.getUniqueId(), mailbox.primaryBlock().resolve(), initialContents);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.BARREL, "Mailbox");
        holder.inventory(inventory);
        inventory.setContents(cloneInventoryContents(initialContents, inventory.getSize()));
        openMailboxInventories.put(holder.sessionId(), inventory);
        player.openInventory(inventory);
        Entity displayEntity = resolveLinkedEntity(mailbox, DISPLAY_ROLE);
        Location soundLocation = displayEntity == null ? player.getLocation() : displayEntity.getLocation();
        player.playSound(soundLocation, Sound.BLOCK_BARREL_OPEN, 0.6f, 1.0f);
    }

    private @NotNull String renderedConfiguredMessage(@Nullable Player player,
                                                      @NotNull String key,
                                                      @NotNull String fallback,
                                                      @NotNull Object... placeholders) {
        String template = fallback;
        try {
            template = getConfigSection().getString("messages." + key, fallback);
        } catch (RuntimeException ignored) {
            template = fallback;
        }
        String rendered = PlaceholderUtil.apply(template, placeholders);
        String applied = rendered;
        try {
            if (api.placeholders() != null) {
                applied = api.placeholders().apply(player, rendered);
            }
        } catch (RuntimeException ignored) {
            applied = rendered;
        }
        return applied == null ? "" : applied;
    }

    private void sendConfiguredMessage(@NotNull Player player,
                                       @NotNull String key,
                                       @NotNull String fallback,
                                       @NotNull Object... placeholders) {
        api.messages().send(player, renderedConfiguredMessage(player, key, fallback, placeholders));
    }

    private @NotNull String configuredDialogText(@NotNull String key, @NotNull String fallback) {
        try {
            return getConfigSection().getString("dialog." + key, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private @NotNull MailboxInventoryHolder newMailboxInventoryHolder(@NotNull UUID sessionId,
                                                                      @NotNull UUID mailboxId,
                                                                      @NotNull UUID playerUuid,
                                                                      @Nullable Location mailboxLocation) {
        return newMailboxInventoryHolder(sessionId, mailboxId, playerUuid, mailboxLocation, new ItemStack[MAILBOX_INVENTORY_SIZE]);
    }

    private @NotNull MailboxInventoryHolder newMailboxInventoryHolder(@NotNull UUID sessionId,
                                                                      @NotNull UUID mailboxId,
                                                                      @NotNull UUID playerUuid,
                                                                      @Nullable Location mailboxLocation,
                                                                      ItemStack @NotNull [] initialContents) {
        return new MailboxInventoryHolder(sessionId, mailboxId, playerUuid, mailboxLocation, initialContents);
    }

    private void closeOpenMailboxSession(@NotNull UUID mailboxId) {
        List<Map.Entry<UUID, Inventory>> matchingSessions = new ArrayList<>();
        for (Map.Entry<UUID, Inventory> entry : openMailboxInventories.entrySet()) {
            InventoryHolder holder = entry.getValue().getHolder();
            if (holder instanceof MailboxInventoryHolder mailboxHolder
                && mailboxHolder.mailboxId().equals(mailboxId)) {
                matchingSessions.add(entry);
            }
        }

        for (Map.Entry<UUID, Inventory> entry : matchingSessions) {
            Inventory inventory = openMailboxInventories.remove(entry.getKey());
            if (inventory == null) {
                continue;
            }

            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof MailboxInventoryHolder mailboxHolder) {
                savePlayerInbox(mailboxHolder.playerUuid(), inventory.getContents());
            }
            for (org.bukkit.entity.HumanEntity viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    private @Nullable Inventory findOpenMailboxInventoryForPlayer(@NotNull UUID playerUuid) {
        for (Inventory inventory : openMailboxInventories.values()) {
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof MailboxInventoryHolder mailboxHolder
                && mailboxHolder.playerUuid().equals(playerUuid)) {
                return inventory;
            }
        }
        return null;
    }

    private ItemStack @NotNull [] loadPlayerInbox(@NotNull UUID playerUuid) {
        String encoded = api.database().querySingleMapped(
            "SELECT payload_base64 FROM mailbox_player_inventories WHERE player_uuid = ?",
            ps -> ps.setString(1, playerUuid.toString()),
            rs -> rs.getString("payload_base64")
        );
        return deserializeInventory(encoded, MAILBOX_INVENTORY_SIZE);
    }

    private void savePlayerInbox(@NotNull UUID playerUuid, ItemStack @NotNull [] contents) {
        ItemStack[] stored = cloneInventoryContents(contents, MAILBOX_INVENTORY_SIZE);
        if (!containsAnyItems(stored)) {
            api.database().update(
                "DELETE FROM mailbox_player_inventories WHERE player_uuid = ?",
                ps -> ps.setString(1, playerUuid.toString())
            );
            refreshOnlineMailIndicator(playerUuid);
            return;
        }

        String payload = serializeInventory(stored);
        api.database().update(
            """
            INSERT INTO mailbox_player_inventories (player_uuid, payload_base64, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
              payload_base64 = excluded.payload_base64,
              updated_at = excluded.updated_at
            """,
            ps -> {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, payload);
                ps.setLong(3, System.currentTimeMillis());
            }
        );
        refreshOnlineMailIndicator(playerUuid);
    }

    private void refreshOnlineMailIndicator(@NotNull UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            refreshMailIndicatorVisibility(player);
        }
    }

    private boolean isMailboxAccessBlock(@NotNull PlacedObject mailbox, @NotNull Block block) {
        return hasBlockRole(mailbox, block, SUPPORT_ROLE) || hasBlockRole(mailbox, block, LEGACY_CONTAINER_ROLE);
    }

    private boolean hasBlockRole(@NotNull PlacedObject mailbox, @NotNull Block block, @NotNull String role) {
        for (PlacedObjectBlockLink link : mailbox.blockLinks()) {
            if (role.equalsIgnoreCase(link.role()) && link.block().matches(block)) {
                return true;
            }
        }
        return mailbox.primaryBlock().matches(block) && SUPPORT_ROLE.equalsIgnoreCase(role);
    }

    private @Nullable Block resolveLinkedBlock(@NotNull PlacedObject mailbox, @NotNull String role) {
        for (PlacedObjectBlockLink link : mailbox.blockLinks()) {
            if (!role.equalsIgnoreCase(link.role())) {
                continue;
            }

            Location location = link.block().resolve();
            if (location != null) {
                return location.getBlock();
            }
        }

        if (SUPPORT_ROLE.equalsIgnoreCase(role)) {
            Location primaryLocation = mailbox.primaryBlock().resolve();
            return primaryLocation == null ? null : primaryLocation.getBlock();
        }
        return null;
    }

    private @Nullable Entity resolveLinkedEntity(@NotNull PlacedObject mailbox, @NotNull String role) {
        for (PlacedObjectEntityLink link : mailbox.entityLinks()) {
            if (role.equalsIgnoreCase(link.role())) {
                return Bukkit.getEntity(link.entityUuid());
            }
        }
        return null;
    }

    private ItemStack @NotNull [] cloneInventoryContents(ItemStack @Nullable [] source, int size) {
        ItemStack[] cloned = new ItemStack[size];
        if (source == null) {
            return cloned;
        }

        int copyLength = Math.min(cloned.length, source.length);
        for (int i = 0; i < copyLength; i++) {
            cloned[i] = source[i] == null ? null : source[i].clone();
        }
        return cloned;
    }

    private @NotNull String serializeInventory(ItemStack @Nullable [] inventory) {
        ItemStack[] safeInventory = cloneInventoryContents(inventory, MAILBOX_INVENTORY_SIZE);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteArrayOutputStream)) {
            out.writeInt(safeInventory.length);
            for (ItemStack item : safeInventory) {
                if (item == null) {
                    out.writeInt(0);
                    continue;
                }
                byte[] data = item.serializeAsBytes();
                out.writeInt(data.length);
                out.write(data);
            }
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize mailbox inventory", e);
        }
    }

    private ItemStack @NotNull [] deserializeInventory(@Nullable String encoded, int expectedSize) {
        ItemStack[] inventory = new ItemStack[expectedSize];
        if (encoded == null || encoded.isBlank()) {
            return inventory;
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ignored) {
            return inventory;
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(raw);
             DataInputStream in = new DataInputStream(byteArrayInputStream)) {
            int size = Math.min(Math.max(in.readInt(), 0), expectedSize);
            for (int i = 0; i < size; i++) {
                int len = in.readInt();
                if (len <= 0) {
                    continue;
                }
                byte[] itemData = in.readNBytes(len);
                inventory[i] = ItemStack.deserializeBytes(itemData);
            }
            return inventory;
        } catch (IOException | RuntimeException ignored) {
            return new ItemStack[expectedSize];
        }
    }

    private @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @Nullable Long parseNullableLong(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private @NotNull ItemStack namedItem(@NotNull Material material, @NotNull String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name.replace("<yellow>", "").replace("<gold>", ""), NamedTextColor.GOLD));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private record Recipient(@NotNull UUID uuid, @NotNull String name) {
    }

    private record QueuedMailSlot(int slotIndex, @NotNull ItemStack item) {
    }

    private record OutgoingMailResult(@NotNull Recipient recipient, ItemStack @NotNull [] items) {
    }

    private record QueuedMail(@NotNull UUID id,
                              @Nullable UUID senderUuid,
                              @Nullable String senderName,
                              @NotNull UUID recipientUuid,
                              @NotNull String recipientName,
                              @NotNull String message,
                              @Nullable String sourceWorldName,
                              @Nullable Integer sourceX,
                              @Nullable Integer sourceY,
                              @Nullable Integer sourceZ,
                              @NotNull String payloadBase64,
                              long queuedAt,
                              long deliverAfter,
                              @Nullable Long countdownStartedAt) {
        private @Nullable Location sourceLocation() {
            if (sourceWorldName == null || sourceX == null || sourceY == null || sourceZ == null) {
                return null;
            }
            org.bukkit.World world = Bukkit.getWorld(sourceWorldName);
            if (world == null) {
                return null;
            }
            return new Location(world, sourceX, sourceY, sourceZ);
        }
    }

    private record PendingNotification(long id, @NotNull String message) {
    }

    private record PendingMailDraft(@Nullable Location sourceLocation,
                                    ItemStack @NotNull [] items,
                                    @NotNull String recipient,
                                    @NotNull String message) {
        private PendingMailDraft {
            sourceLocation = sourceLocation == null ? null : sourceLocation.clone();
            items = MailboxInventoryHolder.copyContents(items, MAILBOX_INVENTORY_SIZE);
        }

        private PendingMailDraft withInput(String recipient, String message) {
            return new PendingMailDraft(sourceLocation, items, recipient, message);
        }

        @Override
        public Location sourceLocation() {
            return sourceLocation == null ? null : sourceLocation.clone();
        }

        @Override
        public ItemStack[] items() {
            return MailboxInventoryHolder.copyContents(items, items.length);
        }
    }

    private static final class MailboxInventoryHolder implements InventoryHolder {
        private final @NotNull UUID sessionId;
        private final @NotNull UUID mailboxId;
        private final @NotNull UUID playerUuid;
        private final @Nullable Location mailboxLocation;
        private final ItemStack @NotNull [] initialContents;
        private @Nullable Inventory inventory;

        private MailboxInventoryHolder(@NotNull UUID sessionId,
                                       @NotNull UUID mailboxId,
                                       @NotNull UUID playerUuid,
                                       @Nullable Location mailboxLocation,
                                       ItemStack @NotNull [] initialContents) {
            this.sessionId = sessionId;
            this.mailboxId = mailboxId;
            this.playerUuid = playerUuid;
            this.mailboxLocation = mailboxLocation == null ? null : mailboxLocation.clone();
            this.initialContents = copyContents(initialContents, MAILBOX_INVENTORY_SIZE);
        }

        private @NotNull UUID sessionId() {
            return sessionId;
        }

        private @NotNull UUID mailboxId() {
            return mailboxId;
        }

        private @NotNull UUID playerUuid() {
            return playerUuid;
        }

        private @Nullable Location mailboxLocation() {
            return mailboxLocation == null ? null : mailboxLocation.clone();
        }

        private ItemStack @NotNull [] initialContents() {
            return copyContents(initialContents, initialContents.length);
        }

        private void inventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("Mailbox inventory has not been assigned yet.");
            }
            return inventory;
        }

        private static ItemStack @NotNull [] copyContents(ItemStack @Nullable [] source, int size) {
            ItemStack[] cloned = new ItemStack[size];
            if (source == null) {
                return cloned;
            }

            int copyLength = Math.min(cloned.length, source.length);
            for (int i = 0; i < copyLength; i++) {
                cloned[i] = source[i] == null ? null : source[i].clone();
            }
            return cloned;
        }
    }
}
