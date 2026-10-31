# Chat HTTP bridge

The chat bridge connects Minecraft global chat and a configured Discord channel
in both directions. The bot exposes one HTTPS POST receiver for Minecraft
events, while STEMCraft accepts Discord chat on its existing web server. Both
directions use the same bearer token. The plugin remains responsible for
moderation; blocked Minecraft messages are never queued.

Enable it under `chat.discord-bridge`:

```yaml
chat:
  discord-bridge:
    enabled: true
    url: "https://bot.example.test/minecraft/event"
    bearer-token: "a-secret-known-by-both-sides"
    inbound-enabled: true
    inbound-format: "&1[DISCORD] {displayname}: {message} "
    server-id: survival
    channel: global
    timeout: 3s
    queue-size: 256
    max-retries: 3
    events:
      player-join: true
      player-quit: true
      minigame-start: true
      minigame-end: true
      player-death: true
      player-command: true
      server-start: true
      server-stop: true
      server-message: true
    redacted-commands:
      - login
      - register
      - changepassword
      - password
```

Use the same receiver path in the plugin URL and the bot environment. New
deployments should use `/minecraft/event`; an existing `/minecraft/chat` path
continues to work when it remains explicitly configured on both sides.

The event switches are enabled by default when the bridge is enabled. Set an
individual switch to `false` when the bot should ignore that event category.
Server startup and shutdown events have no player and an empty `data` object.
Startup is sent for the initial server load, and shutdown is queued while
STEMCraft disables for a server stop; shutdown delivery is best-effort with a
five-second drain limit.

The bridge sends these event names:

- `minecraft.player_join`
- `minecraft.player_quit`
- `minecraft.minigame_start`
- `minecraft.minigame_end`
- `minecraft.player_death`
- `minecraft.player_command`
- `minecraft.server_start`
- `minecraft.server_stop`
- `minecraft.server_message`

`minecraft.server_message` is emitted for server-originated `say` and
`minecraft:say` commands, including commands sent by Craftarr, RCON, scripts,
and other server services. Its `data.message` contains the text after the
command; the Discord bot renders that text as a compact amber embed.

The token is separate from `web-server.mail-api.key`. Keep the receiver on
HTTPS and set a different token for each server or bot. The bridge is disabled
by default, uses a bounded queue, and performs HTTP work on its own worker so a
slow bot does not stall chat handling. It retries server errors and connection
failures with short backoff; a full queue drops the newest event and logs a
warning.

The receiver gets:

```http
POST /minecraft/event HTTP/1.1
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
When `chat.discord-bridge.enabled` and `inbound-enabled` are true, STEMCraft
accepts Discord chat at `POST /api/v1/chat/discord` on the configured web
server. The bot sends only plain message text; Discord embeds, images,
attachments, and messages sent by bots are ignored before a request is made.

The inbound payload is:

```http
POST /api/v1/chat/discord HTTP/1.1
Authorization: Bearer a-secret-known-by-both-sides
Content-Type: application/json; charset=utf-8
X-Discord-Message-Id: 123456789012345678
```

```json
{
  "event": "discord.chat",
  "event_id": "123456789012345678",
  "username": "alex",
  "displayname": "Alex",
  "message": "Hello from Discord"
}
```

The `inbound-format` supports `{username}`, `{displayname}`, and `{message}`.
Its default is `&1[DISCORD] {displayname}: {message} `.

Player join and quit events include the player object at the top level and an
`online_players` count in `data`. Player death events include the player
object and the rendered death `message`, `killer`, `cause`, and `world` in
`data`; `killer` is null
when no player caused the death:

```json
{
  "event": "minecraft.player_join",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "player": {
    "name": "Alex",
    "uuid": "01234567-89ab-cdef-0123-456789abcdef"
  },
  "data": {
    "online_players": 12
  }
}
```

A player death event uses the same top-level player shape:

```json
{
  "event": "minecraft.player_death",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "player": {
    "name": "Alex",
    "uuid": "01234567-89ab-cdef-0123-456789abcdef"
  },
  "data": {
    "message": "Alex was slain by Steve",
    "killer": "Steve",
    "cause": "player_attack",
    "world": "survival"
  }
}
```

Player command events include the player object and the command in `data`.
Arguments for command roots listed under `redacted-commands` are replaced with
`<redacted>` before the event leaves the server:

```json
{
  "event": "minecraft.player_command",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "player": {
    "name": "Alex",
    "uuid": "01234567-89ab-cdef-0123-456789abcdef"
  },
  "data": {
    "command": "/spawn home"
  }
}
```

Minigame events include the minigame namespace, arena, participants,
spectators, and teams in `data`. End events also include `winner`,
`winner_team`, and `winner_players`; fields are `null` or empty when a game
does not produce that kind of result. For example:

```json
{
  "event": "minecraft.minigame_end",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "data": {
    "minigame": "bedwars",
    "arena": {
      "id": "forest",
      "name": "Forest",
      "world": "bedwars_forest"
    },
    "status": "ending",
    "players": [
      {
        "name": "Alex",
        "uuid": "01234567-89ab-cdef-0123-456789abcdef"
      }
    ],
    "spectators": [],
    "teams": [
      {
        "name": "red",
        "display_name": "Red",
        "score": 3,
        "players": [
          {
            "name": "Alex",
            "uuid": "01234567-89ab-cdef-0123-456789abcdef"
          }
        ]
      }
    ],
    "winner": null,
    "winner_team": "red",
    "winner_players": [
      {
        "name": "Alex",
        "uuid": "01234567-89ab-cdef-0123-456789abcdef"
      }
    ]
  }
}
```
