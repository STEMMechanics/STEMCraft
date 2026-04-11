# Developer API

This plugin exposes a Java API through `STEMCraftAPI`.

## Getting the API

```java
import dev.stemcraft.api.STEMCraftAPI;

STEMCraftAPI api = STEMCraftAPI.api();
```

## Service Surface

From `STEMCraftAPI`, you can access:

- `commands()` - command registration
- `config()` - YAML config files/sections
- `database()` - SQL execution/query helpers
- `events()` - event registration helpers
- `holograms()` - hologram operations
- `items()` - custom item helpers
- `locales()` - locale/text resolution
- `messages()` - formatted messaging and token processing
- `minigames()` - minigame framework
- `motd()` - MOTD control
- `players()` - player utilities/logging service
- `punishments()` - punishment records/actions
- `playerStats()` - stat recording/query/export
- `recipes()` - custom recipes
- `regions()` - region registration/listeners
- `tabComplete()` - tab completion providers
- `tasks()` - sync/async scheduling + persistent timers
- `web()` - lightweight web endpoint service
- `worlds()` - world load/settings/generation services

## Command Registration Example

```java
api.commands().create("example")
    .permission("stemcraft.command.example")
    .usage("/example")
    .executor((plugin, cmd, ctx) -> {
        ctx.returnInfo("Hello from example.");
    })
    .register(yourPlugin);
```

## Event Registration Example

```java
api.events().register(org.bukkit.event.player.PlayerJoinEvent.class, event -> {
    api.messages().info(event.getPlayer(), "Welcome!");
});
```

## Database Example

```java
api.database().execute(
    "CREATE TABLE IF NOT EXISTS example_data (id TEXT PRIMARY KEY, value TEXT);"
);

api.database().update(
    "INSERT INTO example_data (id, value) VALUES (?, ?) " +
    "ON CONFLICT(id) DO UPDATE SET value = excluded.value",
    ps -> {
        ps.setString(1, "row-1");
        ps.setString(2, "hello");
    }
);
```

## Resource Pack Generator Extension Point

Resource pack generation supports generator registration via `ResourcePackGenerator`.
See implementations in:

- `dev.stemcraft.service.resourcepack.generators.PackMetaGenerator`
- `dev.stemcraft.service.resourcepack.generators.GlyphGenerator`
- `dev.stemcraft.service.resourcepack.generators.MinecraftPackGenerator`

## Notes for Third-Party Plugins

- Prefer service interfaces from `api/src/main/java/dev/stemcraft/api/service/...`.
- Keep world/player data in DB for persistent state; keep YAML for static configuration.
- Use locale keys + `messages()` when possible, rather than hardcoding text.
