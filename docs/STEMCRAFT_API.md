# STEMCraft API

High-level summary. Canonical webhook contract is in:
- `docs/STEMCRAFT_WEBHOOK_SPEC.md`

## Current Inbound Events (Plugin -> Laravel)

- `player.login`
- `player.logout`
- `player.profile.updated`
- `player.teleport`
- `player.gamemode.changed`
- `player.message`
- `player.penalty.created`
- `player.penalty.updated`
- `player.penalty.deleted`
- `server.health.ping`
- `server.sync.players`
- `server.sync.penalties`
- `server.sync.players.stats`

## Current Outbound Events (Laravel -> Plugin)

- `player.profile.created`
- `player.profile.deleted`
- `player.penalty.created`
- `player.penalty.updated`
- `player.penalty.deleted`

## Penalty Identity + Sync Rules

- Penalty identity is `uuid + started_at`.
- Helper identity: `penalty_key = <lowercase-uuid>|<started_at_utc_z>`.
- `external_id` is no longer used for webhook matching.
- `player.penalty.deleted` sets `deleted_at` (soft delete); it does not hard-delete rows.
- `updated_at` is used for stale-update protection on penalty create/update/delete events.

`server.sync.penalties` reconciliation:
- Minecraft newer `updated_at` -> Laravel updates to Minecraft values.
- Laravel newer `updated_at` -> incoming row ignored.
- Missing in Laravel but present in Minecraft -> Laravel inserts.
- Laravel responds with authoritative penalties snapshot.

## Player Sync Rules

`server.sync.players`:
- Laravel updates identity (`uuid`, `username`, `platform`) from inbound payload.
- Laravel keeps authority on `is_whitelisted`.
- Laravel responds with full authoritative players list (including players absent from inbound payload).
- inbound `players.*.uuid` may be null for unresolved players.
- response `sync.players` may include `uuid: null` rows.
- stale rows are ignored when inbound `updated_at` is older than Laravel `last_seen_at`.

## Connectivity Health

`server.health.ping`:
- plugin sends lightweight connectivity handshake payload.
- Laravel responds with `server.health.pong`, capabilities, and sync hints.

Event idempotency:
- all inbound events may include `event_id` UUID.
- duplicate `event_id` + event combinations are ignored (`ok=true`, `ignored=true`).
- Laravel outbound payloads now include root `event_id`.
- Laravel outbound state-change payloads include `updated_at`.

## Outage Recovery (Plugin)

- queue outbound events durably while comms are degraded.
- probe with `server.health.ping`.
- after recovery, run:
  - `server.sync.players`
  - `server.sync.penalties`
  - `server.sync.players.stats`
- after full sync, do not replay stale state-changing events older than sync watermark.

## Player Stats Sync Rules

`server.sync.players.stats`:
- payload includes root `stats` definitions (key/title/description collation)
- payload includes root `periods` snapshots
- payload is authoritative replace for website cache
- missing period rows are pruned
- periods absent from payload are pruned
- response is only `{"ok":true}`

## Deprecated (Now Rejected as Unknown Event)

As of **March 6, 2026**, deprecated inbound events are rejected with:
- `422 {"ok":false,"error":"unknown_event"}`

Deprecated -> replacement:
- `player.penalty.lifted` -> `player.penalty.updated`
- `server.sync.request` -> `server.sync.players` + `server.sync.penalties`
- `server.player-stats.sync` -> `server.sync.players.stats`
- `blacklist.sync` -> `player.penalty.created` / `player.penalty.updated` / `server.sync.penalties`
- `blacklist.remove` -> `player.penalty.updated` / `server.sync.penalties`
- `account.sync` -> `player.profile.created`
- `account.remove` -> `player.profile.deleted`
