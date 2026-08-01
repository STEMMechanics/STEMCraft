# STEMCraft Wiki

STEMCraft is a Paper plugin with four main layers:

1. Core services exposed through `STEMCraftAPI`
2. Built-in gameplay features discovered from `dev.stemcraft.feature`
3. Command surfaces registered by commands, services, features, and minigames
4. Built-in minigames discovered from `dev.stemcraft.minigame`

This wiki mirrors the current source tree and is intended to give server operators and plugin developers a stable index over the runtime surface.

## Start Here

- [Architecture](./Architecture)
- [Services](./Services)
- [Features](./Features)
- [Commands](./Commands)
- [API](./API)
- [Minigames](./Minigames)
- [Configuration](./Configuration)

## Audience

- Server owners: start with [Features](./Features), [Commands](./Commands), and [Configuration](./Configuration)
- Plugin developers: start with [API](./API), [Services](./Services), and [Architecture](./Architecture)
- Contributors: use this wiki as the high-level index, then follow the package paths back into source

## Source of Truth

The wiki content is authored from this repository under `docs/wiki`.

- Bootstrap and registration flow: `plugin/src/main/java/dev/stemcraft/STEMCraft.java`
- Public API: `api/src/main/java/dev/stemcraft/api`
- Runtime implementations: `plugin/src/main/java/dev/stemcraft`
- Operator docs already in-repo: `docs/`

## Publishing Model

When `docs/wiki/**` changes on the repository default branch, GitHub Actions syncs this directory into the GitHub wiki repository.
