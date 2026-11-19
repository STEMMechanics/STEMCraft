/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed input description shared by forms, dialogs and repeaters. */
public record WebViewField(@NotNull String type, @NotNull String name, @NotNull String label,
                           @NotNull String value, @NotNull String placeholder, boolean required,
                           @Nullable String pattern, @NotNull List<WebViewOption> options,
                           @NotNull Map<String, String> attributes, boolean wide,
                           @NotNull String help, @Nullable WebViewLink link) {
    public WebViewField(String type, String name, String label, String value, String placeholder,
                        boolean required, String pattern, List<WebViewOption> options,
                        Map<String, String> attributes, boolean wide) {
        this(type, name, label, value, placeholder, required, pattern, options, attributes, wide, "", null);
    }

    public WebViewField(String type, String name, String label, String value, String placeholder,
                        boolean required, String pattern, List<WebViewOption> options) {
        this(type, name, label, value, placeholder, required, pattern, options, Map.of(), false, "", null);
    }

    public WebViewField {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (value == null) value = "";
        if (placeholder == null) placeholder = "";
        options = options == null ? List.of() : List.copyOf(options);
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        if (help == null) help = "";
    }

    /** Add a short explanation shown directly below the field. */
    public WebViewField withHelp(String value) {
        return new WebViewField(type, name, label, this.value, placeholder, required, pattern,
            options, attributes, wide, value, link);
    }

    /** Add an open-in-new-tab link for the record currently selected in this field. */
    public WebViewField withLink(WebViewLink value) {
        return new WebViewField(type, name, label, this.value, placeholder, required, pattern,
            options, attributes, wide, help, value);
    }

    public static WebViewField text(String name, String label, String placeholder, boolean required, String pattern) {
        return new WebViewField("text", name, label, "", placeholder, required, pattern, List.of());
    }
    public static WebViewField select(String name, String label, List<WebViewOption> options) {
        return new WebViewField("select", name, label, "", "", false, null, options);
    }
    public static WebViewField suggestion(String name, String label, String placeholder,
                                          List<WebViewOption> options, boolean required) {
        return new WebViewField("suggestion", name, label, "", placeholder, required, null, options);
    }
    public static WebViewField number(String name, String label, String value, String placeholder,
                                      boolean required, String min, String max, String step) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (min != null) attributes.put("min", min);
        if (max != null) attributes.put("max", max);
        if (step != null) attributes.put("step", step);
        return new WebViewField("number", name, label, value, placeholder, required, null, List.of(), attributes, false);
    }
    public static WebViewField textarea(String name, String label, String value, boolean wide) {
        return new WebViewField("textarea", name, label, value, "", false, null, List.of(), Map.of(), wide);
    }
    public static WebViewField checkbox(String name, String label, boolean checked) {
        return new WebViewField("checkbox", name, label, String.valueOf(checked), "", false, null, List.of());
    }
    public static WebViewField json(String name, String label, Object value, boolean wide) {
        return new WebViewField("json", name, label, value == null ? "{}" : new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(value),
            "", false, null, List.of(), Map.of(), wide);
    }

    /** A searchable checklist whose selected options can each carry a number. */
    public static WebViewField checkboxGroup(String name, String label, Map<String, ?> selected,
                                             List<WebViewOption> options, String amountLabel,
                                             String minimum, String maximum, String step, boolean wide) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("$amount-label", amountLabel == null ? "Value" : amountLabel);
        if (minimum != null) settings.put("$min", minimum);
        if (maximum != null) settings.put("$max", maximum);
        if (step != null) settings.put("$step", step);
        String json = new com.google.gson.Gson().toJson(selected == null ? Map.of() : selected);
        return new WebViewField("checkbox-group", name, label, json, "", false,
            null, options, settings, wide);
    }
}
