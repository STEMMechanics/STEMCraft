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
- join/leave lifecycle actions
- HUD refresh loops
- countdown/start handling
- common safety and world protection hooks
- framework-managed team selection for compatible minigames

## Arena Lifecycle Actions

The minigame framework can run shared join/leave actions when a player or
spectator enters an arena from outside the minigame, and when they fully leave
it again.

Arena API:

- `MiniGameArena#getJoinCommands()`
- `MiniGameArena#setJoinCommands(...)`
- `MiniGameArena#getLeaveCommands()`
- `MiniGameArena#setLeaveCommands(...)`
- `MiniGameArena#getJoinPermissions()`
- `MiniGameArena#setJoinPermissions(...)`

Framework behavior:

- join commands run once when a player or spectator enters the minigame from outside
- leave commands run once when that occupant fully leaves the minigame
- join permissions are attached while the occupant remains in the arena
- attached permissions are removed automatically on leave
- swapping between player and spectator inside the same arena does not trigger a fake leave/join cycle

Command execution rules:

- `server: some command` runs as console
- `player: some command` runs as the player
- no prefix also runs as the player

Available tokens:

- `{player}`
- `{uuid}`
- `{arena}`
- `{arena-name}`
- `{minigame}`
- `{namespace}`
- `{role}` as `player` or `spectator`

Example:

```java
MiniGameArena arena = game.createArena("example", world)
    .setJoinCommands(List.of(
        "server: say {player} joined {namespace}:{arena} as {role}",
        "player: msg {player} Welcome to {arena-name}"
    ))
    .setLeaveCommands(List.of(
        "server: say {player} left {namespace}:{arena}"
    ))
    .setJoinPermissions(List.of(
        "example.arena.active",
        "example.arena.{arena}"
    ));
```

## Shared Supply Drops

The minigame framework also supports shared supply-drop spawning for games that
want a common drop presentation and cleanup model.

Current adopters:

- BedWars
- Bridge

Framework behavior:

- resolves valid drop landing locations from arena-configured `dropSurfaceMaterials`
- spawns a descending chest display with a parachute canopy
- converts the landed display into a real chest with the configured loot inside
- removes the chest after it has been opened, emptied, and closed

Arena API:

- `MiniGameArena#findRandomSupplyDropLocation(...)`
- `MiniGameArena#spawnSupplyDropCrate(...)`
- `MiniGameArena#clearAllSupplyDrops()`

Games still own:

- which items can drop
- which surface materials are valid
- when drops are announced and spawned
- any game-specific messaging around drop timing or availability

## Framework Team Selection

Team minigames can now opt into framework-managed team selection through the
public API instead of reimplementing selection logic per minigame.

API contract:

- `MiniGame#setTeamSelectionPolicy(...)`
- `MiniGameArena#setLobbyRegion(...)`
- `MiniGameArena#setTeamSelectionInput(...)`
- `MiniGameTeamSelectionInput`
- `MiniGameTeamSelectionPolicy`

Supported arena inputs:

- `FLOOR`
- `HOTBAR`

`null` means auto-assignment only.

The framework handles:

- floor and hotbar selection input handling
- selector validation during arena enable/validation
- provisional team assignment in `WAITING` / `STARTING`
- countdown reset when the lobby no longer satisfies minimum active-team rules
- shared placeholders for selection HUDs

### Example

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

        @Override
        public int requiredActiveTeams(MiniGameArena arena) {
            return 2;
        }
    });

MiniGameArena arena = game.createArena("example", world)
    .setLobbySpawn(world.getSpawnLocation())
    .setLobbyRegion(region)
    .setTeamSelectionInput(MiniGameTeamSelectionInput.FLOOR);
```

Global floor selector materials are configured in `config.yml`:

```yml
minigames:
  team-selection:
    floor:
      red:
        - red_concrete
        - red_wool
```

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
