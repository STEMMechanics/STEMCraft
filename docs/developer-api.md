# Developer API

This plugin exposes a Java API through `STEMCraftAPI`.

## Getting the API

```java
import dev.stemcraft.api.STEMCraftAPI;

final class ExamplePluginBootstrap {
    private final STEMCraftAPI api = STEMCraftAPI.api();
}
```

## Service Surface

From `STEMCraftAPI`, you can access:

- `commands()` - command registration
- `config()` - YAML config files/sections
- `database()` - SQL execution/query helpers
- `dialogs()` - cross-platform Java/Bedrock input dialogs
- `events()` - event registration helpers
- `holograms()` - hologram operations
- `items()` - custom item helpers
- `locales()` - locale/text resolution
- `mailboxes()` - queued mail and item delivery
- `messages()` - formatted messaging and token processing
- `minigames()` - minigame framework
- `motd()` - MOTD control
- `players()` - player utilities/logging service
- `placedObjects()` - persistent placed block/entity assemblies
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
final class ExampleCommands {
    void register(STEMCraftAPI api, org.bukkit.plugin.Plugin yourPlugin) {
        api.commands().create("example")
            .permission("stemcraft.command.example")
            .usage("/example")
            .executor((plugin, cmd, ctx) -> {
                ctx.returnInfo("Hello from example.");
            })
            .register(yourPlugin);
    }
}
```

## Event Registration Example

```java
final class ExampleEvents {
    void register(STEMCraftAPI api) {
        api.events().register(org.bukkit.event.player.PlayerJoinEvent.class, event -> {
            api.messages().info(event.getPlayer(), "Welcome!");
        });
    }
}
```

## Mailbox API

`MailboxService#send(MailSendRequest)` queues a written letter and optional item stacks. Recipients use only their UUID; STEMCraft resolves their current known name internally. Senders use either a player UUID or a system/plugin display string.

```java
MailSendResult result = api.mailboxes().send(new MailSendRequest(
    "Daily Rewards",
    recipientUuid,
    "Here is today's reward.",
    List.of(new ItemStack(Material.EMERALD, 2))
));
```

Use `result.queued()` and `result.message()` to inspect the outcome. See [Mailboxes](./mailboxes.md) for the complete feature guide.

## Dynamic Hologram API

Dynamic holograms are runtime registrations identified by a stable type and context. They can be anchored to a location or an entity UUID and can provide player-specific visibility and content.

```java
api.holograms().createDynamic(
    "quest",
    questId,
    npcUuid,
    2.2D,
    player -> quest.isAvailableTo(player),
    player -> Component.text("!")
);
```

State changes should invalidate the affected rendering:

```java
api.holograms().refreshDynamic("quest", questId, player);
api.holograms().deleteDynamic("quest", questId);
```

The service manages Java/Bedrock rendering, player movement, world and chunk availability, moving entity anchors, range/line-of-sight rules, and resource-pack token refreshes. Dynamic registrations are intentionally not persisted; the owning feature re-registers them from its own stable records during startup.

## Cross-platform Dialog API

`DialogService` builds one logical form for both Paper's Java dialog UI and Geyser's Bedrock Cumulus forms.

```java
boolean opened = api.dialogs().create("example:feedback")
    .title(Component.text("Feedback"))
    .body(Component.text("Tell us what happened."))
    .textInput("subject", Component.text("Subject"), "", 64)
    .multilineTextInput("message", Component.text("Message"), "", 256, 4)
    .submit(Component.text("Send"), response -> {
        String subject = response.text("subject");
        String message = response.text("message");
    })
    .cancel(Component.text("Cancel"), () -> { })
    .open(player);
```

`open` returns `false` if the appropriate client UI cannot be opened. Callbacks run on the server thread.

## Message Types and Contexts

Messages can provide an explicit `MessageType` and optional context:

```java
api.messages().send(player, MessageType.INFO, "survival", "You have mail");
```

Configured strings can route themselves with leading directives:

- `/info/<gold>You have mail`
- `/survival//info/<gold>You have mail`
- `/info//survival/<gold>You have mail`
- `/survival/info/<gold>You have mail`

Directives are processed only at the beginning of trusted plugin/config messages. Player-authored chat is not directive-processed. Later directives override earlier values.

Context prefixes are configured under `logging.contexts`. Optional `show-when` and `hide-when` rules support world globs and permissions, including `!` negation. With only `show-when`, the implicit default is hidden; with `hide-when`, or with both keys, the implicit default is shown. An unknown context simply adds no prefix.

## Custom Items and Placed Objects

`CustomItemDefinition` combines an item template, placement mode, placed-object type, and Java/Bedrock client presentation metadata. `PlacedObjectService` persists stable assemblies containing a primary block plus role-labelled block/entity links. Features should resolve and delete assemblies through this service instead of storing transient Bukkit entity references.

## Database Example

```java
final class ExampleDatabase {
    void init(STEMCraftAPI api) {
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
    }
}
```

## Minigame Framework Example

The minigame framework is available through `api.minigames()`. A minigame can
register a framework-managed team-selection policy and then leave lobby team
selection, provisional assignment, countdown gating, and HUD placeholders to
the shared runtime.

Common API types:

- `MiniGameService`
- `MiniGame`
- `MiniGameArena`
- `MiniGameArenaHandler`
- `MiniGameTeam`
- `MiniGameTeamSelectionInput`
- `MiniGameTeamSelectionPolicy`

Example:

```java
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionInput;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionPolicy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExampleMiniGameBootstrap {
    void register(STEMCraftAPI api, MiniGameArenaHandler handler) {
        MiniGame game = api.minigames()
            .create("examplegame", handler)
            .setTeamSelectionPolicy(new MiniGameTeamSelectionPolicy() {
                @Override
                public List<MiniGameTeam> assignableTeams(MiniGameArena arena, Map<Player, String> preferences) {
                    return new ArrayList<>(arena.getTeams());
                }

                @Override
                public int teamCapacity(MiniGameArena arena, MiniGameTeam team) {
                    return 2;
                }

                @Override
                public int requiredActiveTeams(MiniGameArena arena) {
                    return 2;
                }

                @Override
                public Set<MiniGameTeamSelectionInput> supportedInputs(MiniGameArena arena) {
                    return Set.of(MiniGameTeamSelectionInput.FLOOR, MiniGameTeamSelectionInput.HOTBAR);
                }
            });

        game.registerHud(
            MiniGameArena.ArenaStatus.WAITING,
            List.of("Example: {arena:name}"),
            List.of(
                "<gold>Example: <white>{arena:name}",
                "Selected: {player:selected-team}",
                "Auto: {arena:auto-selected-count}",
                "{arena:lobby-team-line-1}",
                "{arena:lobby-team-line-2}"
            )
        );
    }
}
```

Arena-side setup used by the framework:

- `arena.setLobbySpawn(...)`
- `arena.setLobbyRegion(...)`
- `arena.setTeamSelectionInput(MiniGameTeamSelectionInput.FLOOR)` or `HOTBAR`

Framework-owned behavior:

- floor and hotbar team selection
- strict floor validation against `lobbyRegion`
- provisional lobby assignment and team balancing
- countdown stop/reset when the minimum active-team requirement is no longer met
- shared lobby placeholders such as `{player:selected-team}` and `{arena:lobby-team-line-1}`

Shared supply-drop helpers on `MiniGameArena`:

- `findRandomSupplyDropLocation(List<Material> allowedSurfaceMaterials, int attempts)`
  searches the arena region for a valid landing column
- `spawnSupplyDropCrate(ItemStack item, Location landingLocation)`
  spawns the shared descending crate/parachute presentation and lands it as a loot chest
- `clearAllSupplyDrops()`
  removes active drop visuals and landed framework-managed drop chests

Shared player-pull helpers on `MiniGameArena`:

- `pullPlayer(Player player, Location target, double blocksPerSecond)`
  pulls one player toward a target location
- `pullPlayers(Map<Player, Location> targets, double blocksPerSecond)`
  pulls multiple players in one framework-managed sequence
- `cancelPlayerPulls()`
  cancels any active arena-managed player pull
- `isPlayerBeingPulled(Player player)`
  checks whether the framework currently has that player in a pull sequence

The framework owns the crate animation and cleanup lifecycle. Individual
minigames still choose the drop items, valid surface materials, when to
trigger drops, and which players should be pulled to which destinations.

Protection service types available through `STEMCraftAPI`:

- `ProtectionService`
- `ProtectionType`
- `ProtectionRule`
- `ProtectionRequest`

This is the shared path for requesting timed protections such as teleport
damage immunity while allowing gameplay systems like minigames to deny those
protections centrally through registered rules.

## Resource Pack Generator Extension Point

`ResourcePackGenerator` is now an interface-based extension point.

This is a breaking API change from the previous abstract-class model.
Generators written against the older `extends ResourcePackGenerator` API must
be migrated to the new interface contract.

Plugin authors should usually implement `ResourcePackGenerator` directly.
`AbstractResourcePackGenerator` is available as an optional convenience helper
when you want STEMCraft to store the generator id and generator config for you.

Core generator contract:

- `id()`
  Returns the unique generator id used for registration, dependency
  resolution, config lookup, and logging.
- `onLoad(ConfigSectionView)`
  Called after dependency checks and before the generator is activated.
  Use this to read and cache generator-specific config from disk.
- `onUnload()`
  Called when the generator is unregistered or the service unloads.
- `generate(ResourcePackBuildContext)`
  Performs one build pass for one target. `generate(context)` is invoked once
  per supported `ResourcePackBuildTarget`.
- `requiredGenerators()`
  Returns generator ids that must already be active before this generator can
  be activated.
- `supportedFormats()`
  Declares the pack-format range the generator can build.
- `supports(ResourcePackBuildTarget)`
  Optional finer-grained target check. By default this delegates to
  `supportedFormats().contains(target.packFormat())`.

`ResourcePackBuildContext` provides:

- `target()`
  The explicit `ResourcePackBuildTarget` currently being built.
- `writer()`
  The `ResourcePackWriter` output abstraction for the current target.
- `config()`
  The generator-specific `ConfigSectionView`.

See bundled implementations in:

- `dev.stemcraft.service.resourcepack.generators.PackMetaGenerator`
- `dev.stemcraft.service.resourcepack.generators.GlyphGenerator`
- `dev.stemcraft.service.resourcepack.generators.MinecraftPackGenerator`

## Notes for Third-Party Plugins

### Image-map displays

`ImageMapService` renders a `BufferedImage` across a wall-mounted mosaic of filled maps. Displays use stable string IDs and can be created, updated, and deleted without callers managing map IDs or item-frame entities.

```java
api.imageMaps().create("quests:lobby", backingBlock, BlockFace.NORTH, 4, 3);
api.imageMaps().render("quests:lobby", image);
api.imageMaps().onClick("quests:lobby", click -> handleClick(click.player(), click.tileColumn(), click.tileRow()));
api.imageMaps().delete("quests:lobby");
```

The location is the bottom-left backing block when viewing the display from the front. Click callbacks receive the player and selected map tile on both Java and Geyser-translated Bedrock interactions. Map canvases are contextual per viewer and active maps are resent after player join, ensuring reconnecting players receive dynamic pixels as well as the item frames. Runtime registrations and callbacks are recreated by the owning feature after restart.

- Prefer service interfaces from `api/src/main/java/dev/stemcraft/api/service/...`.
- Keep world/player data in DB for persistent state; keep YAML for static configuration.
- Use locale keys + `messages()` when possible, rather than hardcoding text.
