# Architecture

## Runtime Shape

STEMCraft starts in `STEMCraft.java` and builds the runtime in four passes:

1. Verifies Paper and the minimum supported Minecraft version (26.2)
2. Bootstraps config and task infrastructure
3. Instantiates core services and exposes them through `STEMCraftAPI`
4. Scans and loads built-in features from `dev.stemcraft.feature`
5. Scans and loads built-in commands and minigames

The plugin relies heavily on runtime registration rather than a static registry file.

## Package Layout

- `api/src/main/java/dev/stemcraft/api`
  Public interfaces, command abstractions, service contracts, minigame contracts, and shared models
- `plugin/src/main/java/dev/stemcraft/service`
  Runtime implementations of API services
- `plugin/src/main/java/dev/stemcraft/feature`
  Optional gameplay features that can register listeners, commands, and config-backed behavior
- `plugin/src/main/java/dev/stemcraft/command`
  Standalone commands loaded through `BaseCommand`
- `plugin/src/main/java/dev/stemcraft/minigame`
  Built-in minigames and their command/config/runtime logic
- `plugin/src/main/resources`
  `plugin.yml`, locales, bundled defaults, and other plugin resources
- `docs`
  Operator and developer documentation

## Loading Model

### Services

Services are instantiated directly in `STEMCraft.java` and attached to `STEMCraftAPIImpl`.

Important consequence:

- service availability is explicit
- the API surface is stable even when individual features are disabled
- services own durable resources such as database access, web endpoints, tab completion registries, and world management

### Features

Features are discovered by scanning `dev/stemcraft/feature` for subclasses of `BaseFeature`.

Important consequence:

- adding a new feature usually means adding one class in that package
- enablement is config-driven through `BaseFeature`
- features can register commands, event listeners, scheduled tasks, and DB-backed state

### Commands

Commands come from more than one place:

- `dev.stemcraft.command` via `BaseCommand`
- service-owned command classes such as `WorldCommand`, `ResourcePackCommand`, and `HubCommand`
- feature-owned direct registrations such as `/book`, `/coord`, `/speed`, and `/imenu`
- minigame command classes such as `/bridge` and `/nightfall`

### Minigames

Minigames are discovered by scanning `dev/stemcraft/minigame` for subclasses of `BaseMiniGame`.

Each minigame typically owns:

- one command class
- one config helper
- one arena handler
- one arena record type
- the gameplay runtime implementation

## Persistent State

STEMCraft uses two main persistence models:

- YAML config files for static configuration and operator-managed content
- SQLite for durable runtime state

Typical DB-backed systems include:

- punishments
- reports and moderation incidents
- first-join verification state
- player stats
- persistent scheduled tasks
- random first spawn state
- custom book state
- world change recording

## Messaging and Locales

User-facing text is expected to go through the locale and message services rather than being hardcoded inline.

Main pieces:

- `LocaleService` resolves keys and locale files
- `MessageService` formats and dispatches messages
- `TokenProcessor` applies placeholder-like token expansion

## Extension Guidance

When adding new behavior:

- put shared, reusable mechanics behind a service or API interface
- put optional gameplay behavior behind a feature
- keep command handling near the domain it controls
- prefer DB for runtime state and YAML for operator-authored configuration
