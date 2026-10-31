# Web event subscriptions and Discord chat

STEMCraft publishes transport-neutral Minecraft events through the WebService.
The consumer may be a Discord bot or any other HTTP integration; it owns the
external SDK and exposes one HTTPS POST receiver. The plugin remains
responsible for moderation; blocked Minecraft messages are never queued.

Configure the outbound subscription under `web-server.event-subscriptions`:

```yaml
web-server:
  event-subscriptions:
    enabled: true
    url: "https://bot.example.test/minecraft/event"
    bearer-token: "a-secret-known-by-both-sides"
    server-id: survival
    channel: global
    timeout: 3s
    queue-size: 256
    max-retries: 3
    events:
      minecraft.chat: true
      minecraft.player_join: true
      minecraft.player_quit: true
      minecraft.minigame_start: true
      minecraft.minigame_end: true
      minecraft.player_death: true
      minecraft.player_command: true
      minecraft.server_start: true
      minecraft.server_stop: true
      minecraft.server_message: true
      minecraft.player_skill_level_up: true
    redacted-commands:
      - login
      - register
      - changepassword
      - password
```

Existing `chat.discord-bridge` outbound settings are still accepted as a
fallback during migration. Move `url`, `bearer-token`, delivery settings, and
event switches to `web-server.event-subscriptions`. Keep the old section's
`inbound-enabled` and `inbound-format` values for the Discord chat endpoint;
that inbound path remains a chat-specific feature.

Use the same receiver path in the plugin URL and the bot environment. New
deployments should use `/minecraft/event`; an existing `/minecraft/chat` path
continues to work when it remains explicitly configured on both sides.

The event switches are enabled by default when a subscription is enabled. Set
an individual switch to `false` when the consumer should ignore that event
category.
Server startup and shutdown events have no player and an empty `data` object.
Startup is sent for the initial server load, and shutdown is queued while
STEMCraft disables for a server stop; shutdown delivery is best-effort with a
five-second drain limit.

The subscription sends these event names:

- `minecraft.player_join`
- `minecraft.player_quit`
- `minecraft.minigame_start`
- `minecraft.minigame_end`
- `minecraft.player_death`
- `minecraft.player_command`
- `minecraft.server_start`
- `minecraft.server_stop`
- `minecraft.server_message`
- `minecraft.player_skill_level_up`

`minecraft.server_message` is emitted for server-originated `say` and
`minecraft:say` commands, including commands sent by Craftarr, RCON, scripts,
and other server services. Its `data.message` contains the text after the
command; the Discord bot renders that text as a compact amber embed.

`minecraft.player_skill_level_up` includes the player object and the skill
transition in `data`:

```json
{
  "event": "minecraft.player_skill_level_up",
  "event_id": "7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e",
  "occurred_at": "2026-10-24T01:02:03Z",
  "server": "survival",
  "channel": "global",
  "player": {
    "name": "Alex",
    "uuid": "01234567-89ab-cdef-0123-456789abcdef"
  },
  "data": {
    "skill": "mining",
    "skill_title": "Mining",
    "previous_level": 11,
    "new_level": 12,
    "previous_xp": 12000,
    "new_xp": 12100
  }
}
```

The token is separate from `web-server.mail-api.key`. Keep the receiver on
HTTPS and set a different token for each server or bot. Subscriptions are
disabled by default, use a bounded queue, and perform HTTP work on their own worker so a
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
When the legacy `chat.discord-bridge.enabled` and `inbound-enabled` values are
true, STEMCraft
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
