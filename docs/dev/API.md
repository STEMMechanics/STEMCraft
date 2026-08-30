# API

The public integration surface is `dev.stemcraft.api.STEMCraftAPI`.

## Getting the API

<!-- javadoc:method api/src/main/java/dev/stemcraft/api/STEMCraftAPI.java#api -->
<!-- /javadoc -->

## Service Accessors

`STEMCraftAPI` exposes the following runtime service families:

- `commands()`
- `coordinateBar()`
- `comets()`
- `config()`
- `audit()`
- `database()`
- `dialogs()`
- `events()`
- `holograms()`
- `items()`
- `locales()`
- `mailboxes()`
- `messages()`
- `minigames()`
- `motd()`
- `players()`
- `placedObjects()`
- `placeholders()`
- `protections()`
- `profanityFilter()`
- `punishments()`
- `playerStats()`
- `recipes()`
- `regions()`
- `selections()`
- `saves()`
- `tabComplete()`
- `tasks()`
- `web()`
- `worlds()`

## Save checkpoints

Extensions with pending in-memory state can join `/stemcraft save` and normal STEMCraft shutdown checkpoints:

Registering the same plugin and ID replaces the previous callback. Disabled plugins are discarded automatically. A callback must return only after its state is durable and may throw an exception; one failing participant is reported without preventing the remaining participants from saving. Call `api.saves().unregister(myPlugin, "player-data")` when an integration no longer owns that state.

`api.saves().saveAll()` invokes the same complete STEMCraft checkpoint as `/stemcraft save`. It covers active STEMCraft features and services, dirty STEMCraft configuration files, and enabled registered extensions. It deliberately does not save Bukkit worlds or vanilla player data.

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/save/SaveService.java -->
<!-- /javadoc -->

## Main Extension Areas

### Coordinate bar

`CoordinateBarService` lets extensions register ordered, player-specific `Component` providers for `/coordbar`. Providers return `null` when inactive. Registrations are keyed by owning plugin and ID, replace cleanly during reloads, and are discarded when their owning plugin is disabled. Extensions can also register amendments appended directly to the built-in world, time, or direction section. Amendments must supply their own spacing and are rendered without an automatic separator.

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/coordinatebar/CoordinateBarService.java -->
<!-- /javadoc -->

### Comets

`CometService` launches destructive comet events at an impact location. Calls may use a random direction or supply a horizontal direction, with optional `CometLoot` block ranges scattered around the terminal geode. See [Comets](https://github.com/STEMMechanics/stemcraft/wiki/comets).

### Mailboxes

`MailboxService` queues letters and item stacks to a recipient UUID. `MailSendRequest` accepts either a player UUID or system string as its sender; player names are resolved internally. See [Mailboxes](https://github.com/STEMMechanics/stemcraft/wiki/mailboxes) for examples.

### Dynamic Holograms

`HologramService#createDynamic(...)` registers location- or entity-anchored, player-specific holograms under a stable type/context key. Use `refreshDynamic(...)` after state changes and `deleteDynamic(...)` when the owning object is removed. The service manages Java/Bedrock rendering and world/chunk lifecycle.

`ImageMapService` creates wall-mounted filled-map mosaics and renders a `BufferedImage` across them. Callers use stable string IDs, can register per-display tile click callbacks, and do not manage the underlying maps or item frames.

### Dialogs

`DialogService` exposes one fluent input-dialog builder backed by Paper dialogs on Java and Geyser Cumulus forms on Bedrock. Builders support body text, single- and multiline inputs, submit callbacks, and cancellation callbacks.

### Messages

`MessageService` supports explicit `MessageType` and optional configured contexts. Trusted strings may begin with routing directives such as `/survival//info/`; player-authored chat is never directive-processed.

### Custom Items and Placed Objects

Rich custom item definitions include Java/Bedrock visual metadata and managed placement behavior. `PlacedObjectService` persists stable block/entity assemblies with role-labelled links.

### Commands

The command API centers on:

- `CommandService`
- `CommandBuilder`
- `Command`
- `CommandContext`
- `CommandExecutor`

This is the preferred way to register new command surfaces inside STEMCraft and companion plugins.

### Events

The event layer wraps Bukkit listener registration through:

- `EventService`
- `EventHandler`

This keeps event hookups lightweight and consistent with the rest of the plugin.

### Worlds

The world API includes:

- `WorldService`
- `WorldGeneration`
- `WorldBaseSetting`
- `WorldChangeSession`
- `WorldSettingCommand`
- `WorldSettingCommandExecutor`

This is the main extension point for:

- custom world settings
- generator registration
- world lifecycle tooling
- structured world mutation recording

### Minigames

The minigame API includes:

- `MiniGameService`
- `MiniGame`
- `MiniGameArena`
- `MiniGameArenaHandler`
- `MiniGamePlayer`
- `MiniGameTeam`
- `MiniGameTeamSelectionInput`
- `MiniGameTeamSelectionPolicy`
- `MiniGamePlaceholderProvider`
- `MiniGameHudProvider`

Use these when extending or integrating with the arena framework.

Framework-managed team selection is now part of the public minigame API:

- `MiniGame#setTeamSelectionPolicy(...)` declares the team-selection rules for a minigame.
- `MiniGameArena#setLobbyRegion(...)` stores the lobby area used by framework-owned lobby features.
- `MiniGameArena#setTeamSelectionInput(...)` enables `floor` or `hotbar` selection for one arena.

At runtime the framework owns:

- floor and hotbar selection input handling
- provisional assignment for lobby/start phases
- countdown reset when the current lobby state no longer satisfies minimum-team rules
- shared placeholders such as `{player:selected-team}` and `{arena:lobby-team-line-1}`

The minigame arena API also includes shared supply-drop helpers:

- `MiniGameArena#findRandomSupplyDropLocation(...)`
- `MiniGameArena#spawnSupplyDropCrate(...)`
- `MiniGameArena#clearAllSupplyDrops()`

These are used by BedWars and Bridge to share the same crate/parachute drop
presentation while leaving item selection and drop timing in the minigame
handlers.

The minigame arena API also includes shared player-pull helpers:

- `MiniGameArena#pullPlayer(...)`
- `MiniGameArena#pullPlayers(...)`
- `MiniGameArena#cancelPlayerPulls()`
- `MiniGameArena#isPlayerBeingPulled(...)`

These let minigames apply a straight-line pull toward one or more target
locations at a specified blocks-per-second speed while the framework owns the
per-tick movement and active-pull tracking.

The arena API also exposes lifecycle actions for entering and leaving a
minigame:

- `MiniGameArena#getJoinCommands()`
- `MiniGameArena#setJoinCommands(...)`
- `MiniGameArena#getLeaveCommands()`
- `MiniGameArena#setLeaveCommands(...)`
- `MiniGameArena#getJoinPermissions()`
- `MiniGameArena#setJoinPermissions(...)`

These actions fire when a player or spectator enters an arena from outside the
minigame, and again when they fully leave it. Join permissions are attached
for the duration of occupancy and are removed automatically on exit.

Lifecycle command tokens:

- `{player}`
- `{uuid}`
- `{arena}`
- `{arena-name}`
- `{minigame}`
- `{namespace}`
- `{role}`

Lifecycle command execution rules:

- `server:` runs the command as console
- `player:` runs the command as the player
- no prefix also runs as the player

Example:

```java
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
    });

MiniGameArena arena = game.createArena("example", world)
    .setLobbySpawn(world.getSpawnLocation())
    .setLobbyRegion(selectionRegion)
    .setTeamSelectionInput(MiniGameTeamSelectionInput.FLOOR);

arena.pullPlayer(player, arena.getLobbySpawn(), 50.0d);
```

### Protections

The protection API includes:

- `ProtectionService`
- `ProtectionType`
- `ProtectionRule`
- `ProtectionRequest`

Use this when a feature wants to request a temporary gameplay protection while
leaving the final allow/deny decision to shared policy rules.

### Resource Packs

The resource pack API includes:

- `ResourcePackService`
- `ResourcePackGenerator`
- `AbstractResourcePackGenerator`
- `ResourcePackBuildContext`
- `ResourcePackBuildTarget`
- `ResourcePackWriter`
- `ResourcePackHost`

This is the supported extension path for custom resource-pack generators.

### Tasks

The task API includes:

- `TaskService`
- `TaskCallback`
- `TaskRetryable`
- `TaskRetryCallback`

This is the preferred scheduling surface when work needs naming, delayed execution, retries, or persistence.

## Supporting Models

Useful supporting API types include:

- `SCRegion`
- `TokenProcessor`
- player stats records and definitions
- punishment records
- profanity filter result/severity

## Design Expectations for Integrators

- prefer the interfaces in `api/` over implementation classes in `plugin/`
- use locale keys and `messages()` rather than hardcoded strings when integrating into the user-facing experience
- keep persistent runtime data in SQLite through `database()`
- keep operator-authored configuration in YAML through `config()`

## More API documentation

- [Developer API examples](https://github.com/STEMMechanics/stemcraft/wiki/developer-api)
- [Tab completion](https://github.com/STEMMechanics/stemcraft/wiki/tab-completion)
