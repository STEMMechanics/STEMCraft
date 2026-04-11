# Webhook Bridge Integration

This document describes STEMCraft's website bridge behavior for account state, penalties, status, and player stats.

## Outbound Security

Outbound webhook requests include:

- `X-Minecraft-Timestamp`
- `X-Minecraft-Signature`
- `X-Minecraft-Delivery-Id`

Signed with the configured shared secret.

## Core Outbound Events

- `player.login`
- `player.logout`
- `player.profile.updated`
- `server.player-stats.sync`
- punishment lifecycle events (via punishment observers)

## Startup / Manual Sync

- Server sends `server.sync.request`.
- Expected response shape includes:
  - `ok: true`
  - `sync.mode: "replace"`
  - `sync.accounts`
  - `sync.penalties`
  - `sync.legacy_blacklist`
- Server applies snapshot to local bridge state.

## `server.player-stats.sync`

After sync, STEMCraft pushes stat snapshots for configured periods.

Default:

- `day`
- `week`
- `month`
- `all`

Config:

- `webhook_bridge.player_stats_sync_periods`

Each payload includes:

- `event: "server.player-stats.sync"`
- `period`
- `period_days`
- `timestamp`
- `stats`
- `players`

## Bedrock Username Transformer

To avoid website account mismatches:

- Outbound Bedrock usernames are de-prefixed before send.
- Inbound Bedrock usernames are normalized for local storage/matching.
- Prefix is configurable:
  - `webhook_bridge.bedrock_username_prefix` (default `"."`)

## Whitelist Authority

Webhook bridge can enforce account whitelist state for login decisions.
Blacklist authority is handled by webhook bridge state; whitelist authority should be centralized in one place (webhook bridge in this setup).

## Operational Notes

- If website sends unknown platform labels, server-side normalization maps common Bedrock labels (`bedrock`, `geyser`, `floodgate`) to Bedrock.
- For replace sync snapshots, server preserves known UUIDs for matching platform+username when website provides null UUID.
