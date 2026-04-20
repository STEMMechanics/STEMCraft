# STEMCraft Webhook Spec (Current Behavior)

This document reflects the currently implemented webhook behavior in Laravel.

## 1. Endpoints

Inbound to website (plugin/server -> Laravel):
- `POST /webhooks/stemcraft/server`
- `POST /webhooks/minecraft/server` (alias)

Outbound from website (Laravel -> plugin/server):
- URL from site option: `minecraft.server-webhook-url`

Shared secret:
- `minecraft.webhook-secret`

## 2. Signing + Replay

Headers:
- `X-Minecraft-Timestamp`
- `X-Minecraft-Signature`
- `X-Minecraft-Delivery-Id`

Signature input:
- `timestamp + "\n" + raw_json_body`

PHP implementation:
- `hash_hmac('sha256', $timestamp."\n".$body, $secret)`

Inbound checks:
- signature required/valid
- timestamp numeric and within +/- 5 minutes
- delivery id UUID-like and not replayed within 10 minutes
- optional `event_id` UUID de-duplication (duplicates return `ok=true` with `ignored=true`)

Failures:
- invalid signature: `403 {"ok":false,"error":"invalid_signature"}`
- replay/missing delivery id: `409 {"ok":false,"error":"replay_detected"}`
- unknown/deprecated event: `422 {"ok":false,"error":"unknown_event"}`
- schema validation failure: `422 {"message":"The given data was invalid.","errors":{...}}`

## 3. Inbound Events (Plugin -> Website)

Supported:
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

Common optional field for inbound events:
- `event_id` (UUID). If repeated, Laravel responds:
- `{"ok":true,"ignored":true,"reason":"duplicate_event_id"}`

### 3.1 `player.penalty.created`

Required:
- `uuid`, `username`, `type`

Optional:
- `started_at` (defaults from `occurred_at`)
- `occurred_at`, `reason`, `duration_seconds`, `is_permanent`
- `by_uuid`, `by_username`
- `lifted_at`, `lifted_by_uuid`, `lifted_by_username`, `lift_reason`
- `updated_at` (recommended for stale-update protection)

Behavior:
- upsert by `uuid + started_at`
- lifted details are accepted on create
- if local record has newer `updated_at`, inbound row is ignored

### 3.2 `player.penalty.updated`

Required:
- `uuid`, `username`, `type`, `started_at`

Optional:
- same optional fields as `player.penalty.created`

Behavior:
- full record update by `uuid + started_at`
- if local record has newer `updated_at`, inbound row is ignored

### 3.3 `player.penalty.deleted`

Required:
- `uuid`, `started_at`

Optional:
- `occurred_at`, `updated_at`

Behavior:
- does **not** hard-delete database rows
- marks record as deleted by setting `deleted_at`
- if local record has newer `updated_at`, inbound row is ignored

### 3.4 `server.sync.players`

Purpose:
- plugin sends current player identity snapshot
- Laravel updates identity fields (`uuid`, `username`, `platform`) from this payload
- `is_whitelisted` remains Laravel source-of-truth and is not overridden by inbound values

Request:
- optional: `server_name`, `reason`, `plugin_version`
- required: `players` array
- player fields:
  - required: `username`, `platform`
  - optional: `uuid` (nullable for unresolved/site-only players), `updated_at`, `is_whitelisted` (ignored for authority)

Response:
- `{"ok":true,"sync":{...}}`
- includes `sync.players` as authoritative full list from Laravel, including players not present in inbound payload
- response players may include `uuid: null` for records Laravel knows but Minecraft has not resolved yet
- stale inbound identity rows are ignored when `updated_at` is older than Laravel `last_seen_at`

### 3.5 `server.sync.penalties`

Purpose:
- plugin sends full penalties snapshot (optionally scoped by `starting_from`)
- Laravel reconciles by `uuid + started_at` and `updated_at`

Request:
- optional: `starting_from`
- required: `penalties` array
- each penalty requires: `uuid`, `username`, `type`, `started_at`, `updated_at`
- optional fields include standard penalty fields plus `deleted_at`

Reconciliation rules:
- if Minecraft `updated_at` is newer than Laravel: Laravel updates to Minecraft values
- if Laravel `updated_at` is newer: incoming row is ignored
- if Minecraft has row Laravel does not: Laravel inserts row

Response:
- `{"ok":true,"sync":{...}}`
- includes authoritative `sync.penalties` snapshot from Laravel (scoped by `starting_from` when provided)

### 3.6 `server.sync.players.stats`

Purpose:
- plugin sends all player-stats periods in one authoritative payload
- website treats payload as source of truth and replaces cached rows

Request:
- optional root `timestamp`
- required root `stats` array (global key/title/description definitions)
- required root `periods` array
- each period item:
  - required: `period`, `players`
  - optional: `period_days`, `timestamp`
- each player stat row requires at least `key` and may include `value`, `updated_at`

Replace semantics:
- per-period rows are replaced (missing players for that period are pruned)
- periods not present in payload are pruned from website cache
- if period has `players: []`, that period is cleared

Response:
- `{"ok":true}`

### 3.7 `server.health.ping`

Purpose:
- lightweight connectivity handshake and recovery hinting

Request:
- optional: `server_name`, `plugin_version`, `queue_depth`, `last_error_at`, `last_sync_at`

Response:
- `{"ok":true,"event":"server.health.pong",...}`
- includes:
  - `server_time`
  - `capabilities.supports_event_id=true`
  - `capabilities.recommended_reconnect_sequence` (`server.sync.players`, `server.sync.penalties`, `server.sync.players.stats`)
  - `sync.last_inbound_sync_at` timestamps per sync channel
  - `sync.required` channels Laravel has never seen synced

## 4. Outbound Events (Website -> Plugin)

Queued outbound events:
- `player.profile.created`
- `player.profile.deleted`
- `player.penalty.created`
- `player.penalty.updated`
- `player.penalty.deleted`

Outbound delivery requirements:
- Laravel includes `occurred_at` at the root of every outbound payload
- Laravel includes `event_id` (UUID) at the root of every outbound payload
- Laravel includes root `updated_at` for state-changing outbound events
- webhook is marked delivered on HTTP `2xx` (plugin response body is not required/parsed)
- recommended plugin response body: `{"ok":true}`

### 4.1 `player.profile.created`

Purpose:
- Laravel sends player profile upsert state to plugin

Payload:
- root `occurred_at`
- root `updated_at`
- root `player` object:
  - `uuid` nullable
  - `username` required
  - `platform` required
  - `user_id` nullable
  - `is_whitelisted` required
  - `updated_at` present
  - nested `occurred_at` included

### 4.2 `player.profile.deleted`

Purpose:
- Laravel indicates a player profile should be removed from plugin-managed lists

Payload:
- root `occurred_at`
- root `updated_at`
- root `player` object:
  - `uuid` nullable
  - `username` required
  - `platform` required
  - `updated_at` present
  - nested `occurred_at` included

### 4.3 `player.penalty.created`

Purpose:
- Laravel sends a new/authoritative penalty record

Payload fields:
- required: `type`, `started_at`, `occurred_at`
- required: `updated_at`
- identity: `uuid + started_at` (and `penalty_key` when UUID is available)
- optional/common: `uuid`, `username`, `reason`, `duration_seconds`, `is_permanent`
- optional actor fields: `by_uuid`, `by_username`
- optional lifted fields: `lifted_at`, `lifted_by_uuid`, `lifted_by_username`, `lift_reason`
- optional tombstone field: `deleted_at`

### 4.4 `player.penalty.updated`

Purpose:
- Laravel sends an update to an existing penalty record

Payload:
- same shape as `player.penalty.created`
- plugin should upsert by identity (`uuid + started_at`) and apply newer values

### 4.5 `player.penalty.deleted`

Purpose:
- Laravel sends a tombstone/deletion marker for a penalty

Payload:
- required: `started_at`, `occurred_at`
- required: `updated_at`
- includes: `penalty_key`, `uuid`, `type`
- plugin should treat as soft-delete marker (not historical hard-delete)

Delivery behavior:
- queued delivery with retry/backoff
- retries use a new `X-Minecraft-Delivery-Id`

## 5. Deprecated Event Mapping (Now Rejected)

Deprecated inbound events are no longer accepted and return `unknown_event`.

- `player.penalty.lifted` -> replacement: `player.penalty.updated`
- `server.sync.request` -> replacement: `server.sync.players` and `server.sync.penalties`
- `server.player-stats.sync` -> replacement: `server.sync.players.stats`
- `blacklist.sync` -> replacements: `player.penalty.created`, `player.penalty.updated`, `server.sync.penalties`
- `blacklist.remove` -> replacements: `player.penalty.updated`, `server.sync.penalties`
- `account.sync` -> replacement: `player.profile.created`
- `account.remove` -> replacement: `player.profile.deleted`

Deprecated identity:
- `external_id` no longer used for webhook matching
- replacement identity: `uuid + started_at` (with helper `penalty_key`)

## 6. Reliability + Recovery Contract

To avoid state rollback during outages, plugin should implement:

1. Communication error mode:
   - on repeated outbound failures/timeouts, mark link degraded
   - persist future outbound events in a durable queue (disk/db), do not drop

2. Reconnect probing:
   - send `server.health.ping` until successful `server.health.pong`

3. Reconnect reconciliation sequence:
   - run in this order:
     - `server.sync.players`
     - `server.sync.penalties`
     - `server.sync.players.stats`
   - treat sync responses as source-of-truth

4. Queue replay discipline:
   - after successful sync sequence, do not replay stale state-changing events older than sync watermark
   - replay of observational/audit-only events is optional

5. Authority rule (players):
   - Laravel remains authority for `is_whitelisted`
   - plugin-side disconnected whitelist toggles are temporary and may be overwritten on sync
