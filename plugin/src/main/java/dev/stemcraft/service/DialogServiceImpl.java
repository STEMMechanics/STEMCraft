/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.dialog.DialogBuilder;
import dev.stemcraft.api.service.dialog.DialogResponse;
import dev.stemcraft.api.service.dialog.DialogService;
import dev.stemcraft.api.util.PlayerUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Renders the dialog API using Paper dialogs or Geyser Cumulus forms. */
public final class DialogServiceImpl extends BaseService implements DialogService {
    public DialogServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public DialogBuilder create(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Dialog reference cannot be blank");
        }
        return new Builder(reference);
    }

    private final class Builder implements DialogBuilder {
        private final String reference;
        private final List<TextInput> inputs = new ArrayList<>();
        private final List<DialogButton> actions = new ArrayList<>();
        private Component title = Component.empty();
        private Component body = Component.empty();
        private Component submitLabel = Component.text("Submit");
        private Component cancelLabel = Component.text("Cancel");
        private Consumer<DialogResponse> submitCallback = response -> { };
        private Runnable cancelCallback = () -> { };
        private boolean submitConfigured;

        private Builder(String reference) {
            this.reference = reference;
        }

        @Override
        public DialogBuilder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        @Override
        public DialogBuilder body(Component body) {
            this.body = Objects.requireNonNull(body, "body");
            return this;
        }

        @Override
        public DialogBuilder textInput(String key, Component label, String initialValue, int maxLength) {
            return addTextInput(key, label, initialValue, maxLength, 1);
        }

        @Override
        public DialogBuilder multilineTextInput(String key, Component label, String initialValue, int maxLength, int lines) {
            return addTextInput(key, label, initialValue, maxLength, Math.max(2, lines));
        }

        private DialogBuilder addTextInput(String key, Component label, String initialValue, int maxLength, int lines) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Dialog input key cannot be blank");
            }
            if (inputs.stream().anyMatch(input -> input.key().equals(key))) {
                throw new IllegalArgumentException("Duplicate dialog input key: " + key);
            }
            inputs.add(new TextInput(key, Objects.requireNonNull(label, "label"),
                Objects.requireNonNullElse(initialValue, ""), Math.max(1, maxLength), lines));
            return this;
        }

        @Override
        public DialogBuilder submit(Component label, Consumer<DialogResponse> callback) {
            submitLabel = Objects.requireNonNull(label, "label");
            submitCallback = Objects.requireNonNull(callback, "callback");
            submitConfigured = true;
            return this;
        }

        @Override
        public DialogBuilder action(Component label, Runnable callback) {
            actions.add(new DialogButton(Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(callback, "callback")));
            return this;
        }

        @Override
        public DialogBuilder cancel(Component label, Runnable callback) {
            cancelLabel = Objects.requireNonNull(label, "label");
            cancelCallback = Objects.requireNonNull(callback, "callback");
            return this;
        }

        @Override
        public boolean open(Player player) {
            Objects.requireNonNull(player, "player");
            return PlayerUtil.isBedrock(player) ? openBedrock(player) : openJava(player);
        }

        @SuppressWarnings("UnstableApiUsage")
        private boolean openJava(Player player) {
            try {
                ClickCallback.Options options = ClickCallback.Options.builder()
                    .uses(1)
                    .lifetime(Duration.ofMinutes(10))
                    .build();
                DialogAction submitAction = DialogAction.customClick((response, audience) -> runSync(() -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    inputs.forEach(input -> values.put(input.key(), valueOrEmpty(response.getText(input.key()))));
                    submitCallback.accept(new DialogResponse(reference, player, values));
                }), options);
                DialogAction cancelAction = DialogAction.customClick(
                    (response, audience) -> runSync(cancelCallback), options);

                List<DialogInput> dialogInputs = inputs.stream().map(input -> {
                    TextDialogInput.Builder inputBuilder = DialogInput.text(input.key(), input.label())
                        .width(400)
                        .initial(input.initialValue())
                        .maxLength(input.maxLength());
                    if (input.lines() > 1) {
                        inputBuilder.multiline(TextDialogInput.MultilineOptions.create(input.lines(), 80));
                    }
                    return inputBuilder.build();
                }).map(DialogInput.class::cast).toList();

                List<ActionButton> buttons = new ArrayList<>();
                if (submitConfigured) {
                    buttons.add(ActionButton.builder(submitLabel).width(150).action(submitAction).build());
                }
                for (DialogButton action : actions) {
                    DialogAction dialogAction = DialogAction.customClick(
                        (response, audience) -> runSync(action.callback()), options);
                    buttons.add(ActionButton.builder(action.label()).width(150).action(dialogAction).build());
                }
                ActionButton cancel = ActionButton.builder(cancelLabel).width(150).action(cancelAction).build();
                DialogBase base = DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(body, 400)))
                    .inputs(dialogInputs)
                    .build();
                Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(buttons, cancel, Math.min(3, Math.max(1, buttons.size())))));
                player.showDialog(dialog);
                return true;
            } catch (LinkageError | RuntimeException exception) {
                return false;
            }
        }

        private boolean openBedrock(Player player) {
            if (!actions.isEmpty() && inputs.isEmpty() && !submitConfigured) {
                return openBedrockActions(player);
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());
                if (connection == null) {
                    plugin.getLogger().warning("[dialogs] No Geyser connection found for Bedrock player "
                        + player.getName() + " while opening '" + reference + "'.");
                    return false;
                }

                ClassLoader geyserLoader = connection.getClass().getClassLoader();
                Class<?> customFormClass = Class.forName("org.geysermc.cumulus.form.CustomForm", true, geyserLoader);
                Object form = customFormClass.getMethod("builder").invoke(null);
                invoke(form, "title", new Class<?>[]{String.class}, plain(title));
                invoke(form, "label", new Class<?>[]{String.class}, plain(body));
                inputs.forEach(input -> invoke(form, "input",
                    new Class<?>[]{String.class, String.class, String.class},
                    plain(input.label()), input.lines() > 1 ? "" : plain(input.label()), input.initialValue()));
                Consumer<Object> validHandler = response -> runSync(() -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < inputs.size(); i++) {
                        values.put(inputs.get(i).key(), valueOrEmpty(responseInput(response, i + 1)));
                    }
                    submitCallback.accept(new DialogResponse(reference, player, values));
                });
                invoke(form, "validResultHandler", new Class<?>[]{Consumer.class}, validHandler);
                invoke(form, "closedOrInvalidResultHandler", new Class<?>[]{Runnable.class},
                    (Runnable) () -> runSync(cancelCallback));
                Object builtForm = invoke(form, "build", new Class<?>[0]);
                Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form", true, geyserLoader);
                Method sendForm = connection.getClass().getMethod("sendForm", formClass);
                boolean sent = Boolean.TRUE.equals(sendForm.invoke(connection, builtForm));
                if (!sent) {
                    plugin.getLogger().warning("[dialogs] Geyser rejected dialog '" + reference
                        + "' for " + player.getName() + ".");
                }
                return sent;
            } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("[dialogs] Failed to open Bedrock dialog '" + reference
                    + "' for " + player.getName() + ": " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
                return false;
            }
        }

        private boolean openBedrockActions(Player player) {
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());
                if (connection == null) {
                    return false;
                }
                ClassLoader geyserLoader = connection.getClass().getClassLoader();
                Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm", true, geyserLoader);
                Object form = simpleFormClass.getMethod("builder").invoke(null);
                invoke(form, "title", new Class<?>[]{String.class}, plain(title));
                invoke(form, "content", new Class<?>[]{String.class}, plain(body));
                for (DialogButton action : actions) {
                    invoke(form, "button", new Class<?>[]{String.class}, plain(action.label()));
                }
                invoke(form, "button", new Class<?>[]{String.class}, plain(cancelLabel));
                Consumer<Object> validHandler = response -> runSync(() -> {
                    Object value = invoke(response, "clickedButtonId", new Class<?>[0]);
                    int selected = value instanceof Number number ? number.intValue() : -1;
                    if (selected >= 0 && selected < actions.size()) {
                        actions.get(selected).callback().run();
                    } else {
                        cancelCallback.run();
                    }
                });
                invoke(form, "validResultHandler", new Class<?>[]{Consumer.class}, validHandler);
                invoke(form, "closedOrInvalidResultHandler", new Class<?>[]{Runnable.class},
                    (Runnable) () -> runSync(cancelCallback));
                Object builtForm = invoke(form, "build", new Class<?>[0]);
                Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form", true, geyserLoader);
                Method sendForm = connection.getClass().getMethod("sendForm", formClass);
                return Boolean.TRUE.equals(sendForm.invoke(connection, builtForm));
            } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("[dialogs] Failed to open Bedrock action dialog '" + reference
                    + "' for " + player.getName() + ": " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
                return false;
            }
        }

        private Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... arguments) {
            try {
                return target.getClass().getMethod(method, parameterTypes).invoke(target, arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not invoke Cumulus form method " + method, exception);
            }
        }

        private String responseInput(Object response, int index) {
            Object value = invoke(response, "asInput", new Class<?>[]{int.class}, index);
            return value instanceof String string ? string : "";
        }
    }

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TextInput(@NotNull String key,
                             @NotNull Component label,
                             @NotNull String initialValue,
                             int maxLength,
                             int lines) {
    }

    private record DialogButton(@NotNull Component label, @NotNull Runnable callback) { }
}
