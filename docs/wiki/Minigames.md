# Minigames

Built-in minigames are discovered from `dev.stemcraft.minigame` through `BaseMiniGame`.

## Built-In Minigames

| Minigame | Command Root | Main Runtime Class |
| --- | --- | --- |
| Bridge | `/bridge` | `BridgeMiniGame` |
| Boat Race | `/boatrace` | `BoatRaceMiniGame` |
| BedWars | `/bedwars` | `BedWarsMiniGame` |
| Nightfall | `/nightfall` | `NightfallMiniGame` |
| Parkour | `/parkour` | `ParkourMiniGame` |
| SkyBlock | `/skyblock` | `SkyBlockMiniGame` |
| TNT Run | `/tntrun` | `TntRunMiniGame` |

## Common Minigame Structure

Most built-in minigames follow the same file pattern:

- `*MiniGame.java` for the main runtime
- `*Command.java` for admin and operator command surfaces
- `*Config.java` for config and arena serialization
- `*ArenaHandler.java` for validation and gameplay rules
- `*ArenaRecord.java` for serialized arena definitions

## Shared Runtime Responsibilities

The shared minigame framework handles:

- arena creation and lookup
- lobby, play, and spectator state transitions
- spectator support
- player occupancy tracking
- HUD refresh loops
- countdown/start handling
- common safety and world protection hooks

## Command Surfaces

Each built-in minigame owns its own command namespace. These commands typically support:

- arena creation and deletion
- spawn, lobby, play, and spectator location management
- region and checkpoint selection
- arena state inspection
- player join/start/stop flows

The exact subcommands differ by minigame because each game has different arena data requirements.

## Integration Points

For plugin developers, the important API types are:

- `MiniGameService`
- `MiniGame`
- `MiniGameArena`
- `MiniGameArenaHandler`
- `MiniGamePlayer`
- `MiniGameTeam`

This lets new minigames follow the same runtime contract as the built-in ones.
