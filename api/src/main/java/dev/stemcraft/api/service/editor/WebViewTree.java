/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** A searchable, nested tree of service data rendered by the shared WebView client. */
public record WebViewTree(@NotNull String label, @NotNull List<Node> nodes,
                          boolean searchable, @NotNull String empty) implements WebViewComponent {
    public WebViewTree(String label, List<Node> nodes) {
        this(label, nodes, false, "No entries.");
    }

    public WebViewTree {
        if (label == null) label = "";
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        if (empty == null) empty = "No entries.";
    }

    @Override public @NotNull String type() { return "tree"; }

    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("label", label, "nodes", nodes,
            "searchable", searchable, "empty", empty);
    }

    /** One tree entry with an optional action and nested children. */
    public record Node(@NotNull String id, @NotNull String label, @NotNull String description,
                       @NotNull List<WebViewBadge> badges, @NotNull List<Node> children,
                       @NotNull WebViewButton action, boolean expanded) {
        public Node(String id, String label, String description, WebViewButton action, List<Node> children) {
            this(id, label, description, List.of(), children, action, false);
        }

        public Node {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
            if (description == null) description = "";
            badges = badges == null ? List.of() : List.copyOf(badges);
            children = children == null ? List.of() : List.copyOf(children);
            if (action == null) throw new IllegalArgumentException("action must not be null");
        }
    }
}
