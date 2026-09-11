# STEMCraft Wiki

STEMCraft is a Paper plugin with four main layers:

1. Core services exposed through `STEMCraftAPI`
2. Built-in gameplay features discovered from `dev.stemcraft.feature`
3. Command surfaces registered by commands, services, features, and minigames
4. Built-in minigames discovered from `dev.stemcraft.minigame`

## Requirements

- Java 25
- Paper for Minecraft 26.2 or newer

The plugin declares Paper API version `26.2` and also checks the Minecraft version before initializing services. On an older server it logs the detected and required versions, disables itself, and performs no data or service initialization.

This wiki mirrors the current source tree and is intended to give server operators and plugin developers a stable index over the runtime surface.

## Start Here

- [Architecture](https://github.com/STEMMechanics/stemcraft/wiki/Architecture)
- [Services](https://github.com/STEMMechanics/stemcraft/wiki/Services)
- [Features](https://github.com/STEMMechanics/stemcraft/wiki/Features)
- [Commands](https://github.com/STEMMechanics/stemcraft/wiki/commands)
- [API](https://github.com/STEMMechanics/stemcraft/wiki/API)
- [Minigames](https://github.com/STEMMechanics/stemcraft/wiki/Minigames)
- [Configuration](https://github.com/STEMMechanics/stemcraft/wiki/Configuration)

## Audience

- Server owners: start with [Features](https://github.com/STEMMechanics/stemcraft/wiki/Features), [Commands](https://github.com/STEMMechanics/stemcraft/wiki/commands), and [Configuration](https://github.com/STEMMechanics/stemcraft/wiki/Configuration)
- Plugin developers: start with [API](https://github.com/STEMMechanics/stemcraft/wiki/API), [Services](https://github.com/STEMMechanics/stemcraft/wiki/Services), and [Architecture](https://github.com/STEMMechanics/stemcraft/wiki/Architecture)
- Contributors: use this wiki as the high-level index, then follow the package paths back into source

## Source of Truth

The wiki content is authored from this repository under `docs/dev`.

- Bootstrap and registration flow: `plugin/src/main/java/dev/stemcraft/STEMCraft.java`
- Public API: `api/src/main/java/dev/stemcraft/api`
- Runtime implementations: `plugin/src/main/java/dev/stemcraft`
- Developer and operator docs: `docs/dev/`
- Player handbook: `docs/pub/`

## Publishing Model

When `docs/dev/**` changes on the repository default branch, GitHub Actions syncs this directory into the GitHub wiki repository.
