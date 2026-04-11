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

- Standard utilities: `/back`, `/warp`, `/spawn`, `/tpworld`, `/tpworldspawn`, `/tpworldlast`.
- `/tpworld` uses player last known location in that world, falls back to world spawn.
- `/tpworldlast <base>` selects the most recently visited world in `{base, base_nether, base_the_end}`.
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

## Webhook Bridge

- Signed outbound webhooks (player lifecycle, stats sync, status data, etc.).
- Inbound sync-managed state for accounts/penalties/blacklist.
- Whitelist authority can be driven by webhook account state.
- Bedrock username transformer support for webhook payloads.

## Player Stats

- Built-in stat descriptors always enabled.
- Persistent stat state in DB.
- Time-in-world bucketed stats via config:
  - `player_stats.time_in.<bucket>.worlds`
  - Recorded keys: `time_in_<bucket>`
- Startup sync sends `server.player-stats.sync` snapshots (configurable periods).

## Additional Gameplay Features

- Hub behavior and `/hub`
- Player tab list formatting
- Coordinates in action bar / boss bar (`/coord`, `/coordbar`)
- Gunpowder barrels
- Graves
- Deny spawn eggs
- Restrict creative, no anvil repair cost, no throwing potions, skip night, etc.
