/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A service-owned WebService view described entirely with typed components. */
public record WebView(
        @NotNull String kind,
        @NotNull String title,
        @NotNull String description,
        boolean writable,
        @NotNull List<WebViewComponent> components,
        @Nullable Object state,
        @NotNull List<WebViewButton> topbarActions,
        @NotNull List<WebViewSidebarItem> sidebarItems
) {
    public WebView(@NotNull String kind, @NotNull String title, @NotNull String description,
                   boolean writable, @NotNull List<WebViewComponent> components,
                   @Nullable Object state) {
        this(kind, title, description, writable, components, state, List.of(), List.of());
    }

    public WebView(@NotNull String kind, @NotNull String title, @NotNull String description,
                   boolean writable, @NotNull List<WebViewComponent> components) {
        this(kind, title, description, writable, components, null, List.of(), List.of());
    }

    public WebView {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (description == null) description = "";
        components = components == null ? List.of() : List.copyOf(components);
        topbarActions = topbarActions == null ? List.of() : List.copyOf(topbarActions);
        sidebarItems = sidebarItems == null ? List.of() : List.copyOf(sidebarItems);
    }

    /** Convert the typed tree to the stable JSON boundary consumed by the shared renderer. */
    public @NotNull Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", kind);
        value.put("title", title);
        value.put("description", description);
        value.put("writable", writable);
        value.put("components", WebViewComponent.definitions(components));
        value.put("topbarActions", topbarActions);
        value.put("sidebarItems", sidebarItems);
        if (state != null) value.put("state", state);
        return value;
    }

    /** Return a copy with action buttons shown beside the page title. */
    public @NotNull WebView withTopbarActions(@NotNull List<WebViewButton> actions) {
        return new WebView(kind, title, description, writable, components, state, actions, sidebarItems);
    }

    /** Return a copy with quick links shown below this module in the sidebar. */
    public @NotNull WebView withSidebarItems(@NotNull List<WebViewSidebarItem> items) {
        return new WebView(kind, title, description, writable, components, state, topbarActions, items);
    }
}
