/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service.web;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared server-owned page components.
 *
 * <p>Pages are assembled from components here so the web service does not
 * depend on checked-in HTML documents.  Feature services provide data and
 * component definitions, while this class owns the common document shell.</p>
 */
public final class WebPageComponents {
    private WebPageComponents() { }

    public static @NotNull String landingPage(@NotNull String javaAddress,
                                               @NotNull String bedrockAddress,
                                               @NotNull String aboutUrl,
                                               @NotNull String discordUrl) {
        Node navigation = node("nav").attr("class", "landing-nav").attr("aria-label", "STEMCraft navigation");
        if (!aboutUrl.isBlank()) navigation.child(link(aboutUrl, "About STEMCraft"));
        if (!discordUrl.isBlank()) navigation.child(link(discordUrl, "Discord"));

        Node connectionGrid = node("div").attr("class", "connection-grid");
        if (!javaAddress.isBlank()) connectionGrid.child(connectionCard(
            "Java Edition", javaAddress, "Use this address in Minecraft Java Edition.",
            "M4 7h16v10H4zM8 4v3M16 4v3M8 12h.01M12 12h.01M16 12h.01M8 15h.01M12 15h.01M16 15h.01"));
        if (!bedrockAddress.isBlank()) connectionGrid.child(connectionCard(
            "Bedrock Edition", bedrockAddress, "Use this address on Minecraft Bedrock.",
            "m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3ZM4 7.5 12 12l8-4.5M12 12v9"));
        if (javaAddress.isBlank() && bedrockAddress.isBlank()) {
            connectionGrid.child(node("p").attr("class", "landing-unconfigured")
                .text("Connection details are not configured for this web service."));
        }

        Node landingActions = node("div").attr("class", "landing-actions")
            .child(node("a").attr("class", "landing-button primary").attr("href", "#connect").text("Join the server"));
        if (!aboutUrl.isBlank()) landingActions.child(link(aboutUrl, "Read the join guide", "landing-button"));
        Node hero = node("section").attr("class", "landing-hero").attr("aria-labelledby", "welcome-title")
            .child(node("p").attr("class", "eyebrow").text("STEMMechanics · Minecraft"))
            .child(node("h1").attr("id", "welcome-title").text("Build. Experiment. Create."))
            .child(node("p").attr("class", "landing-intro").text(
                "Keep building between workshops in STEMCraft — a shared Minecraft world for curious makers, creative projects and new adventures."))
            .child(landingActions);
        Node connect = node("section").attr("class", "landing-connect").attr("id", "connect").attr("aria-labelledby", "connect-title")
            .child(node("div").attr("class", "section-heading")
                .child(node("div")
                    .child(node("p").attr("class", "eyebrow").text("Connection details"))
                    .child(node("h2").attr("id", "connect-title").text("Join STEMCraft"))))
            .child(connectionGrid);
        Node landingLinks = node("nav").attr("class", "landing-links").attr("aria-label", "STEMCraft links")
            .child(node("a").attr("href", "/status").text("Server status"));
        if (!aboutUrl.isBlank()) landingLinks.child(link(aboutUrl, "Server information"));
        if (!discordUrl.isBlank()) landingLinks.child(link(discordUrl, "Discord"));
        Node footer = node("footer").attr("class", "landing-footer")
            .child(node("p").text("Build something brilliant."))
            .child(landingLinks);
        Node main = node("main").attr("class", "landing-shell")
            .child(node("header").attr("class", "landing-header")
                .child(node("a").attr("class", "landing-brand").attr("href", "/")
                    .attr("aria-label", "STEMCraft home")
                    .child(node("img").attr("src", "/assets/logo.png").attr("alt", "STEMCraft")))
                .child(navigation))
            .child(node("div").attr("class", "landing-grid").child(hero).child(connect))
            .child(footer);
        Node document = node("html").attr("lang", "en")
            .child(head("STEMCraft", true))
            .child(node("body").attr("class", "landing-page")
                .child(node("picture").attr("class", "landing-background").attr("aria-hidden", "true")
                    .child(node("source").attr("srcset", "/assets/background.webp").attr("type", "image/webp"))
                    .child(node("img").attr("src", "/assets/background.png").attr("alt", "")))
                .child(main));
        return "<!doctype html>\n" + document.render();
    }

    public static @NotNull String editorPage() {
        Node sidebar = node("aside").attr("class", "sidebar").attr("aria-label", "Editor navigation")
            .child(node("div").attr("class", "sidebar-brand")
                .child(node("a").attr("class", "sidebar-logo-link").attr("href", "/")
                    .attr("aria-label", "STEMCraft home")
                    .child(node("img").attr("class", "sidebar-logo").attr("src", "/assets/logo.png").attr("alt", "STEMCraft")))
                .child(iconButton("sidebarClose", "Close navigation", "M6 6l12 12M18 6 6 18", "sidebar-close")))
            .child(node("nav").attr("id", "moduleNav").attr("class", "module-nav"));
        Node workspace = node("section").attr("id", "workspace").attr("class", "workspace")
            .child(node("div").attr("class", "loading").attr("role", "status")
                .child(node("span").attr("class", "loading-spinner").attr("aria-hidden", "true"))
                .child(node("span").text("Loading editor…")));
        Node main = node("main").attr("class", "main")
            .child(node("header").attr("class", "topbar")
                .child(iconButton("sidebarToggle", "Open navigation", "M4 6h16M4 12h16M4 18h16", ""))
                .child(node("div").attr("class", "topbar-content")
                    .child(node("h1").attr("id", "pageTitle").text("STEMCraft Editor"))))
            .child(node("div").attr("id", "notice").attr("class", "notice").attr("role", "status"))
            .child(workspace);
        Node app = node("div").attr("class", "app")
            .child(sidebar)
            .child(node("div").attr("id", "sidebarBackdrop").attr("class", "backdrop"))
            .child(main);
        Node document = node("html").attr("lang", "en")
            .child(head("STEMCraft Editor", false, false)
                .child(node("link").attr("rel", "stylesheet").attr("href", "/assets/editor.css"))
                .child(node("link").attr("rel", "stylesheet").attr("href", "/assets/site.css")))
            .child(node("body").attr("class", "editor-page sidebar-open")
                .child(app)
                .child(node("dialog").attr("id", "editor-dialog").attr("class", "editor-dialog")
                    .attr("aria-labelledby", "editor-dialog-title"))
                .child(node("script").attr("src", "/assets/editor.js").attr("defer", "defer")));
        return "<!doctype html>\n" + document.render();
    }

    private static @NotNull Node head(@NotNull String title, boolean description) {
        return head(title, description, true);
    }

    private static @NotNull Node head(@NotNull String title, boolean description, boolean includeSiteStyles) {
        Node head = node("head")
            .child(node("meta").attr("charset", "utf-8"))
            .child(node("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1"));
        if (description) head.child(node("meta").attr("name", "description")
            .attr("content", "STEMCraft Minecraft server by STEMMechanics"));
        head.child(node("title").text(title));
        if (includeSiteStyles) head.child(node("link").attr("rel", "stylesheet").attr("href", "/assets/site.css"));
        return head;
    }

    private static @NotNull Node connectionCard(@NotNull String edition, @NotNull String address,
                                                 @NotNull String description, @NotNull String iconPath) {
        return node("article").attr("class", "connection-card")
            .child(node("span").attr("class", "connection-card-icon").attr("aria-hidden", "true")
                .child(node("svg").attr("viewBox", "0 0 24 24").attr("focusable", "false")
                    .child(node("path").attr("d", iconPath))))
            .child(node("div")
                .child(node("span").attr("class", "edition-label").text(edition))
                .child(node("code").text(address))
                .child(node("p").text(description)));
    }

    private static @NotNull Node iconButton(@NotNull String id, @NotNull String label, @NotNull String path,
                                             @NotNull String extraClass) {
        String classes = "icon-button" + (extraClass.isBlank() ? "" : " " + extraClass);
        return node("button").attr("id", id).attr("class", classes).attr("type", "button")
            .attr("aria-label", label).attr("aria-expanded", id.equals("sidebarToggle") ? "true" : null)
            .child(node("svg").attr("class", "icon-button-svg").attr("viewBox", "0 0 24 24")
                .attr("focusable", "false").attr("aria-hidden", "true")
                .child(node("path").attr("d", path)));
    }

    private static @NotNull Node link(@NotNull String href, @NotNull String text) {
        return link(href, text, "");
    }

    private static @NotNull Node link(@NotNull String href, @NotNull String text, @NotNull String extraClass) {
        return node("a").attr("href", href).attr("class", extraClass).text(text);
    }

    private static @NotNull Node node(@NotNull String tag) {
        return new Node(tag);
    }

    private static final class Node {
        private static final List<String> VOID_TAGS = List.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
        private final String tag;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final List<Node> children = new java.util.ArrayList<>();
        private String text;

        private Node(@NotNull String tag) { this.tag = tag; }

        private Node attr(@NotNull String name, String value) {
            if (value != null && !value.isBlank()) attributes.put(name, value);
            return this;
        }

        private Node child(@NotNull Node child) {
            children.add(child);
            return this;
        }

        private Node text(@NotNull String value) {
            text = value;
            return this;
        }

        private String render() {
            StringBuilder output = new StringBuilder("<").append(tag);
            attributes.forEach((name, value) -> output.append(' ').append(name).append("=\"")
                .append(escape(value)).append('"'));
            output.append('>');
            if (text != null) output.append(escape(text));
            children.forEach(child -> output.append(child.render()));
            if (!VOID_TAGS.contains(tag)) output.append("</").append(tag).append('>');
            return output.toString();
        }

        private static String escape(@NotNull String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
        }
    }
}
