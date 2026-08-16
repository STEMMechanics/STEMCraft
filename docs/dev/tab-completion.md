# Tab Completion Guide

This guide covers the STEMCraft command tab-completion system:

- writing completion providers with `api.tabComplete().register(...)`
- using `.tabCompletion(...)` tracks when defining commands

This `{...}` syntax is for command tab completion. It is separate from message or config token replacement used elsewhere in the project.

## Overview

The system has two layers:

- Providers: named callbacks that return a list of suggestions.
- Tracks: `.tabCompletion(...)` definitions attached to a command.

Each `.tabCompletion(...)` call defines one valid argument pattern. The command will suggest the next item from any track whose earlier items already match what the player has typed.

## Writing Providers

Register providers during your feature or service `onEnable()`:

```java
api.tabComplete().register("book", (player, args) -> this.bookNames);
```

Provider signature:

```java
api.tabComplete().register("name", (player, args) -> {
    return List.of();
});
```

- `player` is the player requesting completions.
- `args` are extra placeholder arguments supplied from the track, not the full command line by default.
- Return an empty list when nothing is available.

### Simple Provider

```java
api.tabComplete().register("speedtype", (player, args) -> List.of("fly", "walk"));
```

Usage:

```java
.tabCompletion("{speedtype}")
```

### Provider Using Earlier Command Args

If a provider depends on an earlier command argument, pass that argument through the track with `$n`:

```java
api.tabComplete().register("world-generator-options", (player, args) -> {
    if (args.length == 0 || args[0].isBlank()) {
        return List.of();
    }

    String generator = args[0];
    String existingOptions = args.length > 1 ? args[1] : "";
    return tabCompleteOptions(generator, existingOptions);
});
```

Usage:

```java
.tabCompletion("create", "", "{world-generators}", "{world-generator-options:$2}")
```

In `$2`, the index is zero-based over the already-entered raw command args before the current partial token. For `/world create demo flat ...`, `$2` resolves to `flat`.

### Permission-Aware Provider

Tracks can now gate items with `permission^value`, but provider-level checks are still useful when the returned list itself should vary by permission:

```java
api.tabComplete().register("hub-target", (player, args) -> {
    if (!player.hasPermission("stemcraft.command.hub.others")) {
        return List.of(player.getName());
    }

    return Bukkit.getOnlinePlayers().stream()
        .filter(player::canSee)
        .map(Player::getName)
        .toList();
});
```

## Using Tracks On Commands

Example:

```java
api.commands().create("example")
    .permission("stemcraft.command.example")
    .tabCompletion("list")
    .tabCompletion("info", "{example-items}")
    .tabCompletion("set", "{example-items}", "mode:{example-modes}", "-force")
    .executor((api, cmd, ctx) -> {
        String mode = ctx.getOption("mode", "default");
        boolean force = ctx.hasFlag("force", false);
    })
    .register(plugin);
```

Each item in a track maps to one argument position or one optional flag/value option group.

## Track Item Formats

### Literal

```java
"create"
"delete"
"reset"
```

The argument must match that literal value.

### Wildcard / Free Text

```java
""
```

Matches any argument at that position but does not suggest values. This is useful for names or other free text fields.

### Permission-Gated Item

```java
"stemcraft.cats^cats"
"stemcraft.command.hub.others^{player}"
"stemcraft.command.example.admin^-force"
"stemcraft.command.example.admin^mode:{example-modes}"
```

The text before `^` is the required permission. The text after `^` is the real tab item.

If the sender lacks that permission:

- positional items do not match and are not suggested
- flags and key/value options are simply omitted

This wrapper works with literals, placeholders, flags, key/value options, and `""`.

### Provider Placeholder

```java
"{player}"
"{world}"
"{int}"
```

Looks up a registered provider by name and uses its suggestions.

### Provider Placeholder With Extra Args

```java
"{world-generator-options:$2}"
"{my-provider:static-value}"
"{my-provider:$1:$3}"
```

The text before the first `:` is the provider name. Any remaining segments are passed to the provider as `args`.

### Argument References With `$n`

`$n` is only meaningful inside placeholder arguments.

```java
"{bedwars-arena-teams:$1}"
```

- `$0` = first raw arg
- `$1` = second raw arg
- `$2` = third raw arg

Use this when the provider needs context from a previously typed argument.

### Flag Options

```java
"-force"
"-silent"
```

Flags:

- are suggested when the preceding positional part of the track matches
- are removed from suggestions once already used
- can be read in the executor with `ctx.hasFlag("force", false)`

Typing only `-` shows available flags for the matching tracks.

### Key/Value Options

```java
"mode:{example-modes}"
"seed:{int}"
```

These produce suggestions after the player types the prefix:

```text
mode:
seed:
```

They can be read in the executor with `ctx.getOption("mode", "default")`.

Typing `mode:` shows only values for `mode`.

## Matching Rules

- Each `.tabCompletion(...)` call is one independent valid pattern.
- A track becomes active when its earlier positional items match the args already entered.
- Flags and key/value options do not have to appear in a fixed order relative to each other; they become available once the preceding positional portion matches.
- Suggestions are filtered against the current token using substring matching.

## Built-In Providers

Core providers are registered automatically:

- `{player}`
- `{duration}`
- `{world}`
- `{gamemode}`
- `{int}`

Many features and services also register their own providers, such as:

- `{world-generators}`
- `{world-generator-options:...}`
- `{book}`
- `{bedwars-arenas}`
- `{tntrun-arenas}`

## Reading Flags And Options In Executors

Flags and key/value options are parsed out of the raw command args before `ctx.args()` is returned.

```java
.executor((api, cmd, ctx) -> {
    List<String> positional = ctx.args();
    boolean force = ctx.hasFlag("force", false);
    String mode = ctx.getOption("mode", "default");
})
```

- `ctx.args()` returns positional arguments only.
- `ctx.hasFlag(...)` checks for `-flag`.
- `ctx.getOption(...)` reads `key:value`.
- `ctx.rawArgs()` returns the original raw args if you need them.

## Practical Examples

### Subcommand + Target

```java
.tabCompletion("show", "{player}")
.tabCompletion("hide", "{player}")
```

### Free-Text Name Then World

```java
.tabCompletion("create", "", "{world}")
```

The second arg is free text, and the third arg is suggested from the `world` provider.

### Context-Dependent Provider

```java
.tabCompletion("team", "{bedwars-arenas}", "{bedwars-arena-teams:$1}")
```

The second provider receives the selected arena id and can return teams for that arena.

### Optional Flags And Options

```java
.tabCompletion("sync", "scope:{sync-scopes}", "-force", "-dryrun")
```

### Permission-Gated Variants

```java
.tabCompletion("pet", "stemcraft.cats^cats")
.tabCompletion("tp", "stemcraft.command.tp.others^{player}")
.tabCompletion("sync", "stemcraft.command.sync.force^-force")
.tabCompletion("sync", "stemcraft.command.sync.admin^scope:{sync-scopes}")
```

## Best Practices

- Register providers before commands that use them.
- Keep providers fast and side-effect free.
- Return `List.of()` instead of `null`.
- Check `args.length` before reading provider args.
- Do permission filtering inside the provider.
- Use `$n` when a provider depends on earlier command arguments.
- Use `""` only for genuinely free-text slots.
