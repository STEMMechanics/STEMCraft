# Chat HTTP bridge

The chat bridge lets a small Discord bot receive accepted Minecraft global chat
without making the Minecraft web server an inbound dependency for the bot. The
bot exposes one HTTPS POST receiver, checks the bearer token, de-duplicates by
`event_id`, and forwards the event through its Discord client or webhook. The
plugin remains responsible for moderation; blocked messages are never queued.

Enable it under `chat.discord-bridge`:

```yaml
chat:
  discord-bridge:
    enabled: true
    url: "https://bot.example.test/minecraft/events"
    bearer-token: "a-secret-known-by-both-sides"
    server-id: survival
    channel: global
    timeout: 3s
    queue-size: 256
    max-retries: 3
```

The token is separate from `web-server.mail-api.key`. Keep the receiver on
HTTPS and set a different token for each server or bot. The bridge is disabled
by default, uses a bounded queue, and performs HTTP work on its own worker so a
slow bot does not stall chat handling. It retries server errors and connection
failures with short backoff; a full queue drops the newest event and logs a
warning.

The receiver gets:

```http
POST /minecraft/events HTTP/1.1
Authorization: Bearer a-secret-known-by-both-sides
Content-Type: application/json; charset=utf-8
X-STEMCraft-Event-Id: 7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e
```

```json
{
  "event": "minecraft.chat",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "player": {
    "name": "Alex",
    "uuid": "01234567-89ab-cdef-0123-456789abcdef"
  },
  "message": "Hello from Minecraft"
}
```

The bot should return any 2xx response after it has accepted the event. It can
return 4xx for a permanent request problem or 5xx for a transient failure.
Keeping Discord formatting in the bot makes the plugin payload useful for
other consumers too and leaves room for a later Discord-to-Minecraft command
without coupling the two directions.
