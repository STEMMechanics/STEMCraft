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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

    public DebugServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        api.tabComplete().register("debug-test", (player, args) -> tests().stream()
                .map(DebugTest::id)
                .toList());

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
