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
| `/stemcraft` | `STEMCraftCommand` | Status, version, explicit STEMCraft/extension saves, reload, and first-join administration |
| `/maintenance` | `STEMCraft.java` | Toggles server maintenance mode |
| `/debug` | `DebugServiceImpl` | List and run registered feature/service diagnostics; requires `stemcraft.command.debug` |
| `/webserver` | `WebServiceImpl` | Embedded web server control, status and a clickable HTTP diagnostic URL |
| `/world` | `WorldCommand` | World lifecycle, inspection, settings, and join/leave command editing |
| `/resourcepack` | `ResourcePackCommand` | Pack delivery, pack builds and a clickable download URL |
| `/glyph` | `GlyphCommand` | Browse and search registered resource-pack glyphs |
| `/namedregion` | `NamedRegions` | Inspect, find, teleport to, rename, retire, release, and regenerate named areas; see [Named Regions](https://github.com/STEMMechanics/stemcraft/wiki/named-regions) |
| `/hologram` | `HologramServiceImpl` | Hologram creation and editing |
| `/mail` | `Mailboxes` | Compose item and message deliveries (mailbox admin) |
| `/mailbox` | `Mailboxes` | Send letter-only mail and administer the delivery queue |
| `/quest` | `QuestFeature` | Player quest journal and administration |
| `/audit` | `AuditServiceImpl` | Audit review |
| `/moderation` | `ChatServiceImpl` | Moderation incident review |
| `/reports` | `ChatServiceImpl` | Player report review |
| `/profanity` | `ProfanityFilterServiceImpl` | Filter inspection and management |
| `/muteall` | `ChatServiceImpl` | Global chat mute control |
| `/ban`, `/unban`, `/kick`, `/warn` | `PunishmentServiceImpl` | Punishment management |
| `/report` | `ChatServiceImpl` | Player report submission |
| `/announcements` | `AnnouncementsFeature` | List, manage, and broadcast configured announcements |
| `/workshops` | `WorkshopsFeature` | View upcoming workshops and manage manual entries |

### STEMBot wilderness directions

The configured `/help wilderness` and `/stembot wilderness` topic checks that the
player is in a Survival overworld, then uses `exit-region:survival-old-terrain` to
give the closest direction out of the supplied area, distance and coordinates.
Topics, world restrictions and dialogue live in `stembot.yml`. The same
`require-world` and `exit-region` instructions can serve other topics and regions.
See [STEMBot](stembot.md) for bounds and custom-script setup.

### Web server diagnostics

Use `/webserver` or `/webserver status` to see whether the embedded server is
running, its listening address, and a clickable `/status` URL. Successful
`/webserver start` and `/webserver stop` commands confirm their outcome to the
player. A failed start reports failure to the player and logs the cause.

Direct connections to the embedded server use **HTTP**. An HTTPS public URL
requires a reverse proxy that terminates TLS and forwards HTTP to this server.
`web-server.ip: "127.0.0.1"` accepts local connections, such as a local reverse
proxy. For direct connections from other computers, bind to `"0.0.0.0"` and
forward/allow the configured port, then set `web-server.public-url` to the
address clients can reach.

The read-only `GET /status` endpoint does not require Discord role
authorisation. Existing status fields remain available to callers. To include
current online Minecraft usernames, set
`web-server.status.players.enabled: true`; the response then adds a sorted
`players` array containing usernames only. IP addresses, UUIDs, emails and
other personal data are not included. Keep the embedded server on loopback
when the endpoint is consumed through a local HTTPS reverse proxy, and use
the HTTPS `public-url` between STEMBot and the proxy. Administrative or write
endpoints keep their own authentication requirements.

Open the `/status` link from the affected computer first, then use
`/resourcepack url` to test the pack download. If no request appears in the log,
the request did not reach the HTTP handler: check the protocol, address, port,
proxy, and firewall. Handler failures log their exception and return HTTP 500
when a response has not already started. Quest editor access codes stay redacted
from request log paths.

### Resource pack download diagnostics

Use `/resourcepack url` (or `/respack url`) to print the current download address
as a clickable link. Open it in a browser on the affected player's computer to
check whether that computer can download the pack. The address includes the same
version parameter used when sending the pack to Minecraft. This command displays
the URL without requesting an in-game download or rebuilding the pack.
It requires `stemcraft.command.resourcepack`, like the other resource pack commands.
Tab completion offers `info`, `send`, `sendall`, `url` and `zip`; player names are
offered after `send`.

### Hologram cycleboard

Create one rotating arena-stat board with `/hologram create minigame_leaderboard`.

Boards render only while their chunks are loaded; automatic rotation does not
load unattended chunks. Returning renders the current page once. Reloading
STEMCraft rebuilds tracked boards. Unlinked nameplates remain for manual cleanup.

Default leaderboard rows use columns: ranks and names align left, and scores or
times align right. This also applies to `stat_leaderboard` and Parkour/Boat Race
record boards. Rows fit the longest entry with a minimum two-space gap between
the name and score/time, measured in the default Minecraft font. Spaces leave at most
three pixels of rounding. Titles remain centered. Custom `line=` and `first=`
templates retain their formatting; add `{gap}` between the name and score/time
to align the right column, and `{rank-gap}` after the rank to align names.

It discovers arena-scoped minigame stats and shows one arena at a time. Wins are
used when available; otherwise it prefers goals, scores, then a stat by key.
The default rotation delay is 20 seconds. Configure or disable timed rotation
with `/hologram cycle delay <seconds>` (`0` disables timed rotation), then set a
single manual advance button with `/hologram cycle button set` while looking at
it, or remove it with `/hologram cycle button clear`. Setting these options
requires `stemcraft.command.hologram`; any player can press the configured
button. The board accepts the stat leaderboard options `limit`, `period`, `line`,
and `first` as pipe-separated data after the type.

### Hologram deletion

Use `/hologram delete -range 4` to delete all registered STEMCraft holograms
within four blocks of your position in the current world. Add `-limit 1` to
delete only the nearest one. The limit is optional; candidates are ordered from
closest to furthest, with the lower hologram ID first when distances tie.
Distance is measured to the saved board position, as with `/hologram closest`.
Positive decimal ranges are accepted, and the two flags can appear in either order.
Deleting by ID still works, including from the console. Range deletion requires
a player and the existing `stemcraft.command.hologram` permission.

Deletion removes registered boards and their tracked rows. It does not remove
unlinked nameplates, Citizens displays, or other plugins' entities. Reload and
shutdown likewise leave untracked entities alone.

For explicit cleanup of old STEMCraft-tagged static rows, stand nearby and run:

```text
/minecraft:kill @e[type=minecraft:armor_stand,distance=..4,nbt={Tags:["stemcraft:hologram"]},nbt=!{Tags:["stemcraft:dynamic-hologram"]}]
```

This also removes any active STEMCraft rows in that radius. It requires the
`minecraft.command.kill` and `minecraft.command.selector` permissions; see
[Paper's permissions reference](https://docs.papermc.io/paper/reference/permissions/).
Use NBT filters for these colon-containing tag names. Minecraft's `tag=` selector
reads an unquoted word and does not accept these names, even with quotes. The
selector above was verified with Mojang's Minecraft 26.2 parser.
From the server console, use the player's location instead:

```text
minecraft:execute at <player> run minecraft:kill @e[type=minecraft:armor_stand,distance=..4,nbt={Tags:["stemcraft:hologram"]},nbt=!{Tags:["stemcraft:dynamic-hologram"]}]
```

## Travel and Utility

| Command | Owner | Notes |
| --- | --- | --- |
| `/hub` | `HubCommand` | Sends players to the configured hub |
| `/back` | `TeleportUtils` | Returns to last stored location |
| `/warp`, `/setwarp`, `/delwarp` | `TeleportUtils` | Warp management |
| `/spawn`, `/tpspawn` | `TeleportUtils` | Spawn travel |
| `/tpall`, `/tphere` | `TeleportUtils` | Player teleport administration |
| `/tpworld`, `/tpworldat`, `/tpworldspawn`, `/tpworldlast` | `TeleportUtils` | World-aware teleport tools |
| `/top`, `/jump`, `/thru` | `TeleportUtils` | Positional utility commands |
| `/coord`, `/coordbar` | `Coordinates` | Coordinate displays |
| `/speed` | `PlayerSpeed` | Walk/fly speed management |
| `/fly` | `FlyCommand` | Flight toggle/control |
| `/ptime`, `/pweather` | Base commands | Per-player time/weather |
| `/workbench` | `WorkbenchCommand` | Virtual workstation surface plus aliases such as `anvil` and `stonecutter` |
| `/invsee`, `/enderchest`, `/clearinv`, `/break`, `/repair` | Base commands | Inventory/admin utilities |
| `/clearsel` | `SelectionServiceImpl` | Clears active selection state |
| `/selpreview [on\|off\|grid [on\|off]]` | `SelectionServiceImpl` | Controls saved player preferences for WorldEdit outlines and grids; alias `/selprev`; available to all players |

## Content and Interaction

| Command | Owner | Notes |
| --- | --- | --- |
| `/book` | `CustomBooks` | Create, list, show, and distribute configured books; saved book keys are title-derived and preserve existing hyphens |
| `/quest` | `QuestFeature` | View active quests and carried books, track objectives, or abandon quests |
| `/imenu` | `InteractiveMenus` | Interactive menu runtime and editor |
| dynamic configured aliases | `CustomCommands` | Commands loaded from config at startup; default player sender with optional `player:` / `server:` prefixes |
| `gms`, `gmc`, `gma`, `gmsp` | `GameModeAliases` | Short aliases that dispatch to `/gamemode` |

## Minigames

| Command | Owner |
| --- | --- |
| `/bridge` | `BridgeCommand` |
| `/boatrace` | `BoatRaceCommand` |
| `/bedwars` | `BedWarsCommand` |
| `/minefield` | `MinefieldCommand` |
| `/nightfall` | `NightfallCommand` |
| `/parkour` | `ParkourCommand` |
| `/skyblock` | `SkyBlockCommand` |
| `/tntrun` | `TntRunCommand` |

## World Command Highlights

`/world duplicate <source> <destination>` supports both standalone world folders
and Paper's integrated `world/dimensions/minecraft/<world>` storage. Integrated
worlds are copied to a sibling dimension folder, preserving terrain, entities,
generation settings and per-world data. UUID metadata and session locks are omitted
so Paper assigns a fresh identity when the copy is loaded. The source is saved and
unloaded before copying; the destination remains unloaded until `/world load`.
Destinations must be new names using lowercase letters, numbers, dots, underscores
or hyphens. Existing worlds are never merged or overwritten. Symbolic links are
rejected, and an incomplete destination is removed if copying fails.

The `/world` command is one of the most important operator surfaces. It covers:

- create, load, unload, duplicate, and delete
- world inspection via `/world info`
- player-facing world names via `/world displayname <world> <name|clear>`
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

Custom command aliases use the entry key as their command label when `command` is
omitted. The `run` list is an ordered action list. An action beginning with
`message:` is sent to the player through the message service; all other actions
are dispatched as commands. Commands run as the player by default. Use `player:`
or `server:` on an action to choose the sender, and use `{player}` / `{uuid}`
placeholders inside command or message text.

```yaml
custom-commands:
  discord:
    permission: ""
    run:
      - "message:<aqua>Join our Discord community:</aqua>"
      - "message:<click:open_url:'https://discord.example'><underlined><aqua>discord.example</aqua></underlined></click>"
      - "server:discord announce {player}"
```

The command editor appends actions to the same ordered list:

```text
/customcommands create discord
/customcommands addmessage discord "<aqua>Join our Discord community:</aqua>"
/customcommands addcommand discord "server:discord announce {player}"
```

Use `/customcommands info discord` to see the complete action order. The
`setcommand` and `removecommand` editor commands use the overall action index;
`setmessage` and `removemessage` use the ordinal among message actions. Add
actions in the desired sequence, or edit the YAML directly when you need to
reorder an existing list. Message actions support MiniMessage formatting,
routing directives, locale keys, and the same placeholders as command actions.

## Permission Conventions

See [Player and staff permissions](permissions.md) for the standard-player grant list,
all eight minigames, other-player checks and migration from the old broad permissions.

Most built-in commands follow:

- `stemcraft.command.<label>`

Common exceptions:

- feature editor permissions such as `stemcraft.imenu.edit`
- maintenance bypass or specialized gameplay override permissions

For exact argument validation and user-facing usage strings, see locale entries in `plugin/src/main/resources/locales/en.yml`.

Notice boards use `/noticeboard post`, `/noticeboard list [page]`, `/noticeboard edit <post-id>`, `/noticeboard expiry <post-id> <duration|-1>`, and `/noticeboard remove <post-id>`. Administrators create displays with `/noticeboard board create <id> [columns] [rows]` while targeting the bottom-left backing block.

## Dynamic command lifecycle

Registered commands own all aliases and namespaced labels pointing to their exact Bukkit command object. Cleanup snapshots matching keys and removes them through Paper's command map, which updates Brigadier. Do not use `entrySet().removeIf`: the current Paper forwarding iterator does not support iterator removal. Re-registration first cleans up the previous registration; failure retains the handle and logs its cause.

### Returning to a dimension group

`/tpworldlast survival [player]` returns to the most recently visited location in
`survival` or any `survival_*` world, including custom dimensions. If the player
is already in that group, it leaves them at their current location. A player with
no saved visit goes to the base world's spawn. Locations persist through logout
and restart. Cancelled teleports do not update them.

Travel groups use the requested base name and an underscore boundary; they are
independent of inventory sharing settings. `/tpworld survival` still explicitly
selects the overworld, and `/tpworldspawn survival` selects its spawn.

Commands that declare a `{players}` argument accept comma-separated online
player names, `*` for all online players, and `-name` exclusions. For example,
`/hub *,-kira` sends everyone except Kira to the hub. The older `{player}`
placeholder remains a single-player argument.

For existing installations, change the custom `/survival` command's `run` entry
in `config.yml` to `server:tpworldlast survival {player}` and reload STEMCraft.
Also change any survival menu command to `player:tpworldlast survival`; players
using that direct command need `stemcraft.command.tpworldlast`. Bundled defaults
use these commands, but existing custom commands are preserved.
