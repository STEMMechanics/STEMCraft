# Feature Guide

## World Management

- Dynamic world create/load/delete/duplicate.
- World inspection via `/world info [world]`.
- Built-in chunk generator registration/listing plus external Bukkit generator passthrough (for example `PlotSquared` or `Plugin:id`).
- World settings via `/world`:
  - Time/weather locks
  - Tick speed
  - Per-world game mode
  - Nether/end links
  - Spawn flags (deny spawn/no damage/no hunger/force spawn/random first spawn)

## Random First Spawn

- Per-world random first spawn rules.
- Triggered when a player first enters a configured world.
- Configurable:
  - Radius range
  - Attempt count
  - Min distance from other players
  - Border buffer
  - Avoid biomes/blocks/liquids/leaves
- Persisted state in DB (`random_first_spawn_seen`).

## Teleport Utilities

- Custom nether portal routing via `/portal set <id> <world|here> ...`.
- Custom portals bind to the connected lit `NETHER_PORTAL` block cluster, so non-rectangular portal shapes are supported.
- Destinations can use world-default routing, force a world's spawn with `spawn`, or target a specific location with yaw/pitch.
- Standard utilities: `/back`, `/warp`, `/spawn`, `/tpworld`, `/tpworldspawn`, `/tpworldlast`.
- `/tpworld` uses player last known location in that world, falls back to world spawn.
- `/tpworldlast <base>` selects the most recently visited world in `{base, base_nether, base_the_end}`.
- Successful player teleports are written to the server log with source and destination.
- Persistence:
  - `player_last_locations` for `/back`
  - `player_world_last_locations` for world-specific recall

## Resource Pack System

- Java resource pack generation from `data-packs`.
- Glyph token generation and token application in messages/UI.
- Output:
  - Java: `resource-pack.zip`
  - Bedrock: generated pack folder + generated `.zip` in Geyser packs directory
- Bedrock integration:
  - Writes Bedrock manifest/texture metadata/glyph maps
  - Optional automatic Geyser reload after `/resourcepack zip`
- Font-image tokens may use path-like names such as `:contexts/survival:`.
- Glyphs without an explicit character are assigned the next collision-free codepoint and retain stable assignments across builds.

## Contextual Messages

- Trusted config messages can declare a type and context through leading directives such as `/survival//info/`.
- Contexts add configured prefixes only when their world/permission `show-when` and `hide-when` rules apply.
- Player-authored messages cannot inject message type or context directives.

## Mailboxes

- Placeable custom mailbox with queued, delayed delivery.
- Java Paper dialog and Bedrock Geyser form for player-to-player composition.
- Every delivery includes a written letter describing its sender, message, and items.
- Offline delivery notifications are replayed when the recipient joins.
- Player-specific Java/Bedrock holograms appear while delivered mail is waiting.
- Public API supports server/plugin deliveries using a recipient UUID and either a sender UUID or sender string.
- See [Mailboxes](./mailboxes.md) for configuration and API examples.

## First Join Check

- Unverified players are held in a restricted first-join session.
- Players answer a generated addition or subtraction prompt in private chat.
- Correct answers persist a first-join flag against the authenticated UUID.
- Ops and `stemcraft.firstjoin.bypass` bypass the check.
- Admin commands:
  - `/stemcraft firstjoin status <player>`
  - `/stemcraft firstjoin reset <player>`

## Player Stats

- Built-in stat descriptors always enabled.
- Persistent stat state in DB.
- Time-in-world bucketed stats via config:
  - `player_stats.time_in.<bucket>.worlds`
- Recorded keys: `time_in_<bucket>`
- Startup sync sends `server.player-stats.sync` snapshots (configurable periods).

## Player Welcome

- Configurable multi-line welcome messages for first-time and returning players.
- Messages are sent directly to the player without message prefixes.
- Supports MiniMessage formatting plus placeholder expansion such as `{player}`, `{world}`, `{years}`, `{ordinal_year}`, and `{first_join_date}`.
- Anniversary "cake day" broadcasts are driven by persisted first-join state in DB (`player_welcome_state`).
- Blank or missing message lists are ignored.

## Web Status Endpoint

- Built-in `/status` endpoint from the embedded web server.
- Returns online state, player counts, Minecraft version, maintenance state, and timestamp JSON.

## Moderation, Audit, and Reports

- Local profanity filtering replaces the old website-backed moderation path.
- Chat, sign, and book moderation can create incidents and punishments locally.
- Structured audit logging records chat, sign, book, command, and related player events.
- Staff review commands:
  - `/profanity ...`
  - `/moderation ...`
  - `/audit ...`
- Player reporting flow:
  - `/report <message>`
  - `/reports ...`

## Website Bridge Status

- The old webhook bridge is no longer active runtime behavior.
- Website polling support remains through the embedded web server.
- Legacy webhook docs are retained only as historical reference.

## Additional Gameplay Features

- Hub behavior and `/hub`
- Player tab list formatting
- Coordinates in action bar / boss bar (`/coord`, `/coordbar`)
- Gunpowder barrels
- Graves
- Deny spawn eggs
- Restrict creative, no anvil repair cost, no throwing potions, skip night, etc.
- Survival QOL: 3×3 hoe harvesting, crop protection, stack refill, stronger leads, faster powered minecarts and leaf decay, named-mob information, anvil/durability warnings, animal crates, and automatic End Dragon respawning. See [survival-qol.md](survival-qol.md).
