/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.save.SaveParticipant;
import dev.stemcraft.api.service.save.SaveReport;
import dev.stemcraft.api.service.save.SaveService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Default coordinator for core and extension save checkpoints. */
public final class SaveServiceImpl extends BaseService implements SaveService {
    private final Map<Key, Registration> participants = new LinkedHashMap<>();
    private final AtomicBoolean saving = new AtomicBoolean();

    public SaveServiceImpl(STEMCraft plugin, STEMCraftAPI api) { super(plugin, api); }

    @Override
    public synchronized void register(@NotNull Plugin owner, @NotNull String id,
                                      @NotNull SaveParticipant participant) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(participant, "participant");
        String normalized = normalizeId(id);
        participants.put(new Key(owner, normalized), new Registration(owner, normalized, participant));
    }

    @Override
    public synchronized void unregister(@NotNull Plugin owner, @NotNull String id) {
        participants.remove(new Key(Objects.requireNonNull(owner, "owner"), normalizeId(id)));
    }

    @Override
    public @NotNull SaveReport saveAll() {
        if (!saving.compareAndSet(false, true)) {
            return new SaveReport(0, 0, Map.of("save", "A STEMCraft save is already running"));
        }
        try {
            return plugin.saveStemCraftState();
        } finally {
            saving.set(false);
        }
    }

    @Override
    public boolean isSaving() { return saving.get(); }

    /** Called by the plugin's core coordinator after its own features and services. */
    public SaveReport saveExtensions() {
        List<Registration> snapshot;
        synchronized (this) {
            participants.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
            snapshot = new ArrayList<>(participants.values());
        }
        Map<String, String> failures = new LinkedHashMap<>();
        int succeeded = 0;
        for (Registration registration : snapshot) {
            String name = "extension:" + registration.owner().getName() + ":" + registration.id();
            try {
                registration.participant().save();
                succeeded++;
            } catch (Exception exception) {
                failures.put(name, describe(exception));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save " + name, exception);
            }
        }
        return new SaveReport(snapshot.size(), succeeded, failures);
    }

    private String normalizeId(String id) throws IllegalArgumentException {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("Save participant id must contain only letters, numbers, '.', '_' or '-'");
        }
        return normalized;
    }

    private String describe(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record Key(Plugin owner, String id) { }
    private record Registration(Plugin owner, String id, SaveParticipant participant) { }
}
