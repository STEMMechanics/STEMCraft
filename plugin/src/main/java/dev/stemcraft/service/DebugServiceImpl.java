/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.debug.DebugContext;
import dev.stemcraft.api.service.debug.DebugHandler;
import dev.stemcraft.api.service.debug.DebugRegistration;
import dev.stemcraft.api.service.debug.DebugService;
import dev.stemcraft.api.service.debug.DebugTest;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Implementation of the shared {@code /debug} test registry and command. */
public final class DebugServiceImpl extends BaseService implements DebugService {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private final Map<String, RegisteredTest> registeredTests = new ConcurrentHashMap<>();
    private Command command;
    private DebugRegistration inspectRegistration;

    public DebugServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        api.tabComplete().register("debug-test", (player, args, currentArgument) -> tests().stream()
                .map(DebugTest::id)
                .toList());
        inspectRegistration = register("inspect",
                "Inspect the target block, entity, and held item.", this::inspect);

        command = api.commands().create("debug")
                .description("Run a registered STEMCraft diagnostic.")
                .usage("/debug [test] [player]")
                .permission("stemcraft.command.debug")
                .tabCompletion("{debug-test}", "{player}")
                .executor((unused, cmd, context) -> handleCommand(context))
                .register(plugin);
    }

    @Override
    public void onDisable() {
        if (command != null) {
            command.unregister();
            command = null;
        }
        if (inspectRegistration != null) {
            inspectRegistration.close();
            inspectRegistration = null;
        }
        registeredTests.clear();
    }

    @Override
    public @NotNull DebugRegistration register(@NotNull String id, @NotNull String description,
                                                @NotNull DebugHandler handler) {
        String normalizedId = normalizeId(id);
        String normalizedDescription = Objects.requireNonNull(description, "description").trim();
        if (normalizedDescription.isBlank()) {
            throw new IllegalArgumentException("Debug test descriptions cannot be blank");
        }
        Objects.requireNonNull(handler, "handler");

        RegisteredTest registration = new RegisteredTest(
                new DebugTest(normalizedId, normalizedDescription), handler);
        registeredTests.put(normalizedId, registration);
        return new RegistrationHandle(normalizedId, registration);
    }

    @Override
    public @NotNull List<DebugTest> tests() {
        return registeredTests.values().stream()
                .map(RegisteredTest::test)
                .sorted(Comparator.comparing(DebugTest::id))
                .toList();
    }

    @Override
    public @NotNull Optional<DebugTest> find(@NotNull String id) {
        String normalizedId;
        try {
            normalizedId = normalizeId(id);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        RegisteredTest registration = registeredTests.get(normalizedId);
        return registration == null ? Optional.empty() : Optional.of(registration.test());
    }

    private void handleCommand(CommandContext context) {
        String id = context.getArgLower(0);
        if (id == null) {
            showTests(context);
            return;
        }
        if (context.numArgs() > 2) {
            context.returnUsage();
            return;
        }

        RegisteredTest registration = registeredTests.get(id);
        if (registration == null) {
            context.returnError("Unknown debug test: " + id);
            return;
        }

        Player player = context.asPlayer();
        if (context.numArgs() > 1) {
            player = context.getPlayer(1);
            if (player == null) {
                context.returnError("That player is not online.");
                return;
            }
        }
        if (player == null) {
            context.returnError("A player is required when /debug is run from the console.");
            return;
        }

        DebugContext debugContext = new CommandDebugContext(context.getSender(), player);
        try {
            registration.handler().run(debugContext);
        } catch (RuntimeException exception) {
            context.error("Debug test " + registration.test().id() + " failed: "
                    + Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName()));
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Debug test " + registration.test().id() + " failed", exception);
        }
    }

    private void showTests(CommandContext context) {
        List<DebugTest> tests = tests();
        if (tests.isEmpty()) {
            context.info("No debug tests are registered.");
            return;
        }
        context.info("Registered debug tests:");
        for (DebugTest test : tests) {
            context.info("- " + test.id() + " - " + test.description());
        }
        if (context.isConsole()) {
            context.info("Run /debug <test> <player> from the console.");
        } else {
            context.info("Run /debug <test> to inspect yourself.");
        }
    }

    private void inspect(DebugContext context) {
        Player player = context.player();
        if (player == null) {
            context.error("A player is required for /debug inspect.");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            context.info("Held item: empty");
        } else {
            context.info("Held item: " + held.getType() + " x" + held.getAmount());
            context.info("Custom item id: " + Objects.requireNonNullElse(
                    api.items().getCustomItemId(held), "none"));
            ItemMeta meta = held.getItemMeta();
            if (meta != null) inspectPdc(context, "Item PDC", meta.getPersistentDataContainer());
        }

        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            context.info("Target block: none within 8 blocks.");
        } else {
            context.info("Target block: " + block.getType() + " at " + block.getWorld().getName()
                    + " " + block.getX() + "," + block.getY() + "," + block.getZ());
            context.info("Block data: " + block.getBlockData().getAsString());
            BlockState state = block.getState();
            if (state instanceof TileState tile) {
                inspectPdc(context, "Block tile PDC", tile.getPersistentDataContainer());
            } else {
                context.info("Block tile PDC: none (not a tile entity).");
            }
            inspectPdc(context, "Chunk PDC", block.getChunk().getPersistentDataContainer());
        }

        Entity entity = player.getTargetEntity(8);
        if (entity == null) {
            context.info("Target entity: none within 8 blocks.");
        } else {
            context.info("Target entity: " + entity.getType() + " " + entity.getUniqueId());
            context.info("Entity scoreboard tags: " + (entity.getScoreboardTags().isEmpty()
                    ? "none" : String.join(", ", entity.getScoreboardTags())));
            inspectPdc(context, "Entity PDC", entity.getPersistentDataContainer());
        }
    }

    private static void inspectPdc(DebugContext context, String label, PersistentDataContainer data) {
        List<NamespacedKey> keys = new ArrayList<>(data.getKeys());
        keys.sort(Comparator.comparing(NamespacedKey::toString));
        if (keys.isEmpty()) {
            context.info(label + ": none");
            return;
        }
        context.info(label + ":");
        for (NamespacedKey key : keys) {
            context.info("- " + key + " = " + pdcValue(data, key));
        }
    }

    private static String pdcValue(PersistentDataContainer data, NamespacedKey key) {
        String string = read(data, key, PersistentDataType.STRING);
        if (string != null) return "STRING " + string;
        Byte byteValue = read(data, key, PersistentDataType.BYTE);
        if (byteValue != null) return "BYTE " + byteValue;
        Integer integer = read(data, key, PersistentDataType.INTEGER);
        if (integer != null) return "INTEGER " + integer;
        Long longValue = read(data, key, PersistentDataType.LONG);
        if (longValue != null) return "LONG " + longValue;
        Float floatValue = read(data, key, PersistentDataType.FLOAT);
        if (floatValue != null) return "FLOAT " + floatValue;
        Double doubleValue = read(data, key, PersistentDataType.DOUBLE);
        if (doubleValue != null) return "DOUBLE " + doubleValue;
        byte[] bytes = read(data, key, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) return "BYTE_ARRAY " + Arrays.toString(bytes);
        int[] integers = read(data, key, PersistentDataType.INTEGER_ARRAY);
        if (integers != null) return "INTEGER_ARRAY " + Arrays.toString(integers);
        long[] longs = read(data, key, PersistentDataType.LONG_ARRAY);
        if (longs != null) return "LONG_ARRAY " + Arrays.toString(longs);
        PersistentDataContainer nested = read(data, key, PersistentDataType.TAG_CONTAINER);
        if (nested != null) return "TAG_CONTAINER (" + nested.getKeys().size() + " entries)";
        return "present (unrecognised type)";
    }

    private static <P, C> C read(PersistentDataContainer data, NamespacedKey key,
                                 PersistentDataType<P, C> type) {
        try {
            return data.get(key, type);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Debug test IDs must use lowercase letters, numbers, dots, underscores, or hyphens");
        }
        return normalized;
    }

    private record RegisteredTest(DebugTest test, DebugHandler handler) { }

    private final class RegistrationHandle implements DebugRegistration {
        private final String id;
        private final RegisteredTest registration;
        private boolean closed;

        private RegistrationHandle(String id, RegisteredTest registration) {
            this.id = id;
            this.registration = registration;
        }

        @Override
        public @NotNull String id() { return id; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            registeredTests.remove(id, registration);
        }
    }

    private final class CommandDebugContext implements DebugContext {
        private final CommandSender sender;
        private final Player player;

        private CommandDebugContext(CommandSender sender, Player player) {
            this.sender = sender;
            this.player = player;
        }

        @Override
        public @NotNull CommandSender sender() { return sender; }

        @Override
        public Player player() { return player; }

        @Override
        public void info(@NotNull String message) { api.messages().info(sender, message); }

        @Override
        public void success(@NotNull String message) { api.messages().success(sender, message); }

        @Override
        public void warn(@NotNull String message) { api.messages().warn(sender, message); }

        @Override
        public void error(@NotNull String message) { api.messages().error(sender, message); }
    }
}
