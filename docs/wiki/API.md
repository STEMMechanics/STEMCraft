# API

The public integration surface is `dev.stemcraft.api.STEMCraftAPI`.

## Getting the API

```java
import dev.stemcraft.api.STEMCraftAPI;

STEMCraftAPI api = STEMCraftAPI.api();
```

## Service Accessors

`STEMCraftAPI` exposes the following runtime service families:

- `commands()`
- `config()`
- `audit()`
- `database()`
- `events()`
- `holograms()`
- `items()`
- `locales()`
- `messages()`
- `minigames()`
- `motd()`
- `players()`
- `placeholders()`
- `profanityFilter()`
- `punishments()`
- `playerStats()`
- `recipes()`
- `regions()`
- `selections()`
- `tabComplete()`
- `tasks()`
- `web()`
- `worlds()`

## Main Extension Areas

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
```

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
- `JsonFile`
- `TokenProcessor`
- player stats records and definitions
- punishment records
- profanity filter result/severity

## Design Expectations for Integrators

- prefer the interfaces in `api/` over implementation classes in `plugin/`
- use locale keys and `messages()` rather than hardcoded strings when integrating into the user-facing experience
- keep persistent runtime data in SQLite through `database()`
- keep operator-authored configuration in YAML through `config()`

## Existing In-Repo API Docs

The repository already contains longer-form API notes in:

- `docs/developer-api.md`
- `docs/STEMCRAFT_API.md`
- `docs/tab-completion.md`
