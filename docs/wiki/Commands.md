# Commands

Commands are registered from four places:

- standalone command classes in `dev.stemcraft.command`
- feature-owned registrations
- service-owned registrations
- minigame command classes

The list below reflects the active built-in command roots in source.

## Core and Admin

| Command | Owner | Notes |
| --- | --- | --- |
| `/stemcraft` | `STEMCraftCommand` | Status, version, reload, and first-join administration |
| `/maintenance` | `STEMCraft.java` | Toggles server maintenance mode |
| `/webserver` | `WebServiceImpl` | Embedded web server control |
| `/world` | `WorldCommand` | World lifecycle, inspection, settings, and join/leave command editing |
| `/resourcepack` | `ResourcePackCommand` | Pack delivery and pack build operations |
| `/hologram` | `HologramServiceImpl` | Hologram creation and editing |
| `/mailbox` | `Mailboxes` | Send mail and administer the delivery queue |
| `/audit` | `AuditServiceImpl` | Audit review |
| `/moderation` | `ChatServiceImpl` | Moderation incident review |
| `/reports` | `ChatServiceImpl` | Player report review |
| `/profanity` | `ProfanityFilterServiceImpl` | Filter inspection and management |
| `/muteall` | `ChatServiceImpl` | Global chat mute control |
| `/ban`, `/unban`, `/kick`, `/warn` | `PunishmentServiceImpl` | Punishment management |
| `/report` | `ChatServiceImpl` | Player report submission |

## Travel and Utility

| Command | Owner | Notes |
| --- | --- | --- |
| `/hub` | `HubCommand` | Sends players to the configured hub |
| `/back` | `TeleportUtils` | Returns to last stored location |
| `/warp`, `/setwarp`, `/delwarp` | `TeleportUtils` | Warp management |
| `/spawn`, `/tpspawn` | `TeleportUtils` | Spawn travel |
| `/tpall`, `/tphere` | `TeleportUtils` | Player teleport administration |
| `/tpworld`, `/tpworldspawn`, `/tpworldlast` | `TeleportUtils` | World-aware teleport tools |
| `/top`, `/jump`, `/thru` | `TeleportUtils` | Positional utility commands |
| `/coord`, `/coordbar` | `Coordinates` | Coordinate displays |
| `/speed` | `PlayerSpeed` | Walk/fly speed management |
| `/fly` | `FlyCommand` | Flight toggle/control |
| `/ptime`, `/pweather` | Base commands | Per-player time/weather |
| `/workbench` | `WorkbenchCommand` | Virtual workstation surface plus aliases such as `anvil` and `stonecutter` |
| `/invsee`, `/enderchest`, `/clearinv`, `/break`, `/repair` | Base commands | Inventory/admin utilities |
| `/clearsel` | `SelectionServiceImpl` | Clears active selection state |

## Content and Interaction

| Command | Owner | Notes |
| --- | --- | --- |
| `/book` | `CustomBooks` | Create, list, show, and distribute configured books; saved book keys are title-derived and preserve existing hyphens |
| `/imenu` | `InteractiveMenus` | Interactive menu runtime and editor |
| dynamic configured aliases | `CustomCommands` | Commands loaded from config at startup; default player sender with optional `player:` / `server:` prefixes |
| `gms`, `gmc`, `gma`, `gmsp` | `GameModeAliases` | Short aliases that dispatch to `/gamemode` |

## Minigames

| Command | Owner |
| --- | --- |
| `/bridge` | `BridgeCommand` |
| `/boatrace` | `BoatRaceCommand` |
| `/bedwars` | `BedWarsCommand` |
| `/nightfall` | `NightfallCommand` |
| `/parkour` | `ParkourCommand` |
| `/skyblock` | `SkyBlockCommand` |
| `/tntrun` | `TntRunCommand` |

## World Command Highlights

The `/world` command is one of the most important operator surfaces. It covers:

- create, load, unload, duplicate, and delete
- world inspection via `/world info`
- stored generator selection
- world settings such as time, weather, tick speed, gamemode, nether, end, and random spawn
- join/leave command editors:
  - `/world joincommands`
  - `/world leavecommands`
  - `/world addjoincommand`
  - `/world addleavecommand`
  - `/world setjoincommand`
  - `/world setleavecommand`
  - `/world removejoincommand`
  - `/world removeleavecommand`

Join and leave commands run as the player by default. Use `player:` or `server:` prefixes on individual entries to control sender mode per command.

Custom command aliases also run as the player by default. Use `player:` or `server:` on a configured run entry to force the sender, and `{player}` / `{uuid}` placeholders inside the configured command text.

## Permission Conventions

Most built-in commands follow:

- `stemcraft.command.<label>`

Common exceptions:

- feature editor permissions such as `stemcraft.imenu.edit`
- maintenance bypass or specialized gameplay override permissions

For exact argument validation and user-facing usage strings, see locale entries in `plugin/src/main/resources/locales/en.yml`.
