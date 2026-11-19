/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** A styled button and its declared navigation, request, or dialog behaviour. */
public record WebViewButton(@NotNull String type, @NotNull String id, @NotNull String label,
                            @NotNull String variant, @Nullable String endpoint, @NotNull String method,
                            @NotNull Map<String, Object> payload, @NotNull Map<String, String> query,
                            @Nullable WebViewDialog dialog, @NotNull Map<String, Object> result,
                            @Nullable String confirmation) {
    public WebViewButton(String type, String id, String label, String variant, String endpoint, String method,
                         Map<String, Object> payload, Map<String, String> query, WebViewDialog dialog,
                         Map<String, Object> result) {
        this(type, id, label, variant, endpoint, method, payload, query, dialog, result, null);
    }
    public WebViewButton {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (variant == null || variant.isBlank()) variant = "default";
        if (method == null || method.isBlank()) method = "POST";
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        query = query == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(query));
        result = result == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(result));
    }
    public static WebViewButton navigate(String id, String label, Map<String, String> query) {
        return new WebViewButton("navigate", id, label, "default", null, "GET", Map.of(), query, null, Map.of());
    }
    /** Navigate to a different registered editor module in the current editor window. */
    public static WebViewButton moduleNavigate(String id, String label, String module, Map<String, String> query) {
        return new WebViewButton("module", id, label, "default", null, "GET", Map.of(), query, null,
            Map.of("module", module));
    }
    public static WebViewButton request(String id, String label, String variant, String endpoint, Map<String, Object> payload) {
        return new WebViewButton("request", id, label, variant, endpoint, "POST", payload, Map.of(), null, Map.of());
    }
    public static WebViewButton dialog(String id, String label, String variant, String endpoint,
                                       Map<String, Object> payload, WebViewDialog dialog) {
        return new WebViewButton("dialog", id, label, variant, endpoint, "POST", payload, Map.of(), dialog, Map.of());
    }
    public static WebViewButton previewDialog(String id, String label, String variant, WebViewDialog dialog) {
        return new WebViewButton("dialog", id, label, variant, null, "POST", Map.of(), Map.of(), dialog, Map.of());
    }
    public static WebViewButton display(String id, String label, String variant) {
        return new WebViewButton("display", id, label, variant, null, "GET", Map.of(), Map.of(), null, Map.of());
    }
    public WebViewButton confirmation(String text) {
        return new WebViewButton(type, id, label, variant, endpoint, method, payload, query, dialog, result, text);
    }
}
