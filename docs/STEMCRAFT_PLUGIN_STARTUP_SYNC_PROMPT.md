# STEMCraft Plugin Startup Sync - Codex Prompt

Use this prompt when implementing plugin-side startup reconciliation against Laravel.

## Copy/Paste Prompt

```text
Implement startup sync in the Minecraft STEMCraft plugin so it pushes authoritative startup state to Laravel after plugin enable (plus a manual admin command to trigger sync).

Laravel inbound webhook endpoint:
- POST http://127.0.0.1:8080/webhooks/minecraft/server
- Alias: /webhooks/stemcraft/server

Signing headers (required):
- X-Minecraft-Timestamp: unix seconds string
- X-Minecraft-Signature: hex(hmac_sha256(secret, timestamp + "\n" + raw_json_body))
- X-Minecraft-Delivery-Id: UUID

Shared secret:
- same value as Laravel `minecraft.webhook-secret`

Startup flow:
1) Send `server.health.ping` until success (`server.health.pong`)
2) Send `server.sync.players`
3) Send `server.sync.penalties`
4) Send `server.sync.players.stats`

`server.sync.players.stats` payload shape:
{
  "event": "server.sync.players.stats",
  "timestamp": "<iso8601|null>",
  "stats": [
    {
      "key": "play_time",
      "title": "Play Time",
      "description": "Total play time recorded by the server in hours."
    }
  ],
  "periods": [
    {
      "period": "all|week|month|year",
      "period_days": <int|null>,
      "timestamp": "<iso8601|null>",
      "players": [
        {
          "uuid": "...",
          "username": "...",
          "updated_at": "<iso8601|null>",
          "stats": [
            {
              "key": "play_time",
              "value": <number|string|bool|null>,
              "updated_at": "<iso8601|null>"
            }
          ]
        }
      ]
    }
  ]
}

Behavior requirements:
- Run startup sync asynchronously (non-blocking main thread).
- On communication failure, enter degraded mode and queue future outbound events durably (disk/db).
- If players or penalties sync fails (non-2xx or ok != true), log warning and keep existing plugin state unchanged.
- For stats sync, Laravel response is only `{"ok":true}`; do not expect echoed data.
- Treat players/penalties responses as authoritative replace snapshots.
- After successful full sync, do not replay stale state-changing events older than sync watermark.
- Penalty identity must be `uuid + started_at` (or use `penalty_key`).
- Preserve replay/idempotency protection via delivery IDs.
- Include optional `event_id` (UUID) on outbound events and reuse on retries.
- Include `updated_at` on state-changing events so stale updates can be ignored safely.
- Add manual command `/stemcraft sync now`.

Compatibility note:
- Do not send deprecated inbound events (`player.penalty.lifted`, `server.sync.request`, `server.player-stats.sync`, `blacklist.sync`, `blacklist.remove`, `account.sync`, `account.remove`).
- Laravel rejects deprecated/unknown events with `422 {"ok":false,"error":"unknown_event"}`.
```

## Quick Checks

1. Restart plugin and confirm ping + all three sync calls succeed.
2. Verify whitelist behavior matches the server's native whitelist settings.
3. Verify penalties reconcile by `updated_at` and include deleted (`deleted_at`) records.
4. Verify stats payload uses root `stats` definitions and root `periods` snapshots.
5. Verify deprecated events return `unknown_event` and are not used.
