# STEMBot

`StemBotFeature` owns the private guide, `BotScript` validates the YAML program, `BotSession` interprets it on the server thread, and `BotActor` isolates Citizens movement/rendering. Dialogue and routes live in `stembot.yml`, while `config.yml` controls feature availability. Citizens is optional for the plugin, but required for the guide actor.

## Controlling STEMBot through the API

`api.stemBot()` exposes `StemBotService` even when Citizens or STEMBot is unavailable. Call `open(player)` to acquire an exclusive `StemBotSession`; an empty result means unavailable or already owned. Retain the handle for the duration of your interaction and close it on cancellation. All mutations run on the server thread; `active()` may also be queried by chat listeners.

```java
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.stembot.StemBotSession;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

final class ExampleGuide {
    private StemBotSession guide;

    void begin(STEMCraftAPI api, Player player, Consumer<String> replies) {
        cancel();
        guide = api.stemBot().open(player).orElse(null);
        if (guide == null) return;
        guide.follow(true);
        guide.listen(replies);
        if (!guide.speak("Welcome! Answer the question in chat.")) {
            player.sendMessage("Welcome! Answer the question in chat.");
        }
    }

    // Call after your feature validates a reply; do not hand off immediately after begin().
    void complete() {
        if (guide == null) return;
        if (!guide.startAction("my-welcome-action")) guide.close();
        guide = null;
    }

    // Call on cancellation or when the owning feature is disabled.
    void cancel() {
        if (guide != null) guide.close();
        guide = null;
    }
}
```

The handle provides `active()`, `speak(text)`, `follow(enabled)`, `startAction(name)` and `close()`. It reserves the guide even if terrain prevents spawning; an inactive handle still needs closing. It suspends automatic introductions and manual summons. `speak(text)` uses bot text and sounds without enabling chat capture. `listen(callback)` consumes ordinary chat privately until `stopListening()`, replacement, close or action handoff. Callbacks run on the server thread; stale queued replies are discarded. Without a listener, ordinary chat stays public. Callers own gameplay restrictions. Stale handles cannot speak through, dismiss or redirect a replacement session. World changes, disconnects, reload and disable invalidate handles; callers may acquire another as needed.

Features can register an event trigger with `api.stemBot().registerTrigger("mailbox-received")`, retain the returned `StemBotTrigger`, call `fire(player)` on the server thread when the event occurs, and close the registration when disabled. `fire` returns whether STEMBot accepted the request. Routes are configured in `stembot.yml`; an absent route leaves the owning feature's normal behavior intact. The handle can be used with try-with-resources when its lifetime is lexical.

`move(destination, waitForPlayer)` replaces the current movement order and uses the scripted walk navigation/recovery. With `waitForPlayer=true`, the bot pauses and resumes using the configured distance thresholds. False means it proceeds independently. The return value acknowledges an accepted order, not arrival. If navigation cannot recover, it stops; callers may issue a new move or teleport. `follow(enabled)` tracks the moving player instead of a fixed destination. `location()` returns an optional defensive copy. `teleport(destination)` cancels navigation and returns whether teleporting succeeded. Both move and teleport require finite coordinates in the owner's current world. These operations run on the server thread.

`FirstJoinService` is a client of this API. It owns questions, attempts, deadlines, command restrictions and persistence. It passes localized presentation text to its guide handle, asks it to follow, and starts its configured action after a correct answer. The bot has no verification-specific state or dependency on FirstJoin. Without an active actor, FirstJoin uses ordinary messages and its movement restriction. World transitions reacquire a guide without resetting the question or deadline. Success explicitly requests `first-join.stembot-action` from `config.yml`; a blank value closes the guide, and a missing action closes it with an administrator log message. The bundled value is `first-hub`. The main `world` has no automatic `first-time` mapping, so FirstJoin owns that introduction. Other configured worlds retain automatic introductions.

`StemBotService.hasAction(name)` checks the loaded script. `startAction(player, name)` runs a named action without marking any automatic introduction as seen. `startAction(name)` also exists on a controlled handle, preserving the actor when possible. First-time world mappings are internal automation and have no public start/pause API. Invalid requests return false and retain control so the caller can retry or close. There is no implicit command-action fallback.

Automatic introductions use their per-world seen marker. Controlled sessions suspend automatic triggers. Avoid configuring both a FirstJoin completion action and an automatic first-time action for the same starting world. On an existing server, remove `worlds.world.first-time-action` from its deployed `stembot.yml` and set `first-join.stembot-action` in its deployed `config.yml`; bundled files do not overwrite an existing script.

## Script and timing

Actions are lists of instructions: `say`, `talk`, `sleep`, `walk`, `speed`, `look`, `point`, `require-world`, `exit-region`, `wave`, `sneak`, `stand`, `action`, `listen`, `await`, `end` and `close`. World mappings select command and first-visit actions. Route targets, region references and instruction syntax are validated before sessions start. `talk` includes speech and the configured post-delay; `say` does not wait. Avoid long sequences of `say` for player guidance.

`listen` routes recognise configured wildcard phrases. They are not an unrestricted language model. Sessions look ahead to the next listen menu so known feedback can interrupt talking and walking. Dismissal phrases work throughout the action sequence. Speech revisions prevent old queued speech from continuing after a route change.

## Privacy and movement

Only the session owner sees the actor. Nearby ordinary chat is private to the guide; slash commands retain their normal meaning. Configurable distance thresholds disengage/reengage private chat, and abandoned sessions time out. Use hysteresis: reengagement distance must be lower than disengagement distance.

Movement has bounded stall detection and an arrival tolerance. A stalled route can offer continuation; it must not loop or keep sending failure text. Spawn search is bounded and checks safe space. Departure waits briefly before particles and actor disposal. Summoning elsewhere replaces the old session without a duplicate farewell/welcome pair.

## Skins and lifecycle

Explicit signed texture values live directly in `stembot.yml`. URL conversions use the shared [skin request coordinator](skin-requests.md); failed sources have persistent backoff and successful textures are saved to the script configuration. Epoch checks prevent a completed old request from applying to a replacement session.

Reload/disable must close actors, cancel speech and movement work, and stop chat routing. Do not call quest implementation classes from STEMBot. Interactive steps use the public `GuideActionRequestEvent` and registered providers, with owner-scoped cleanup; `BotSession` never imports quest NPC/storage internals. See [interactive guide callbacks](guide-callbacks.md) for the `await` syntax, cancellation contract, coordinate-bar provider and private practice quest. These interactive steps are introduced in PR #162.

## Keeping the introduction optional

The bundled Survival introduction offers practice without starting it automatically. Detailed plot, quest and navigation guidance belongs behind topic routes. Practice requires explicit acceptance, supports skip and recognised-topic interruption, and has bounded waits. Do not turn every command into a tutorial requirement. Providers must observe successful state changes rather than treating typed chat as proof that a command ran.


## World action configuration

All world routes live together in `stembot.yml`:

```yaml
worlds:
  hub:
    command-action: hub
    first-time-action: first-hub
  floorshuffle:
    always-action: floorshuffle-greeting
    always-action-spawn: [20.5, 65, 20.5, 90, 0]
```

Action names must exist under `actions`. `command-action` selects the action for
`/stembot` and `/help`; when absent or blank, the command tells the player
“STEMBot is not available right now.” It does not summon the automatic action.

`first-time-action` runs once per player/world. `always-action` runs on login
and on each world change. When both exist, the first-time action takes priority
on the first visit; later visits use the always action. Teleports within the same
world do not trigger arrival actions. Controlled sessions retain priority.

`always-action-spawn` optionally supplies `[x, y, z]` or
`[x, y, z, yaw, pitch]` in that world, with zero rotation by default.
Only the always action uses this position; command and first-time actions retain
the near-player spawn search. The fixed spot must have safe ground, clear space,
be inside the world border and in a loaded chunk. An unavailable fixed spot does
not fall back to spawning beside the player.

Existing `command.world` and `first-time.world` entries migrate after successful
script validation. Explicit new values, including blank opt-outs, take precedence.
Reload or restart to apply edited scripts. These routes do not add click actions,
region-based triggers or selective chat capture.

## Event triggers and help topics

Trigger IDs are registered by the feature that owns the event. A regular route
runs on later events; an optional `<trigger-id>-once` route runs the first time
for each player. The once marker is saved in player data after STEMBot starts
the action. Pending requests survive reconnects, wait behind world-arrival and
active guide actions, and are coalesced by trigger ID. With combat deferral
enabled, arrival, trigger and help-topic actions wait until the configured quiet
period after the player's last dealt or received entity damage.

```yaml
trigger-policy:
  defer-during-combat: true
  combat-cooldown-seconds: 10

triggers:
  mailbox-received-once: mailbox-first-received

help-topics:
  mail: mailbox-help
  mailbox: mailbox-help
```

With no `mailbox-received` route, the mailbox explanation runs only on the
player's first mail notification. Adding `mailbox-received: mailbox-reminder`
also runs `mailbox-reminder` on later notifications. `/help mail` and
`/help mailbox` use the configured help topic actions. `/help <topic>` and
`/stembot <topic>` complete the configured topics and `close`, with prefix
filtering and refreshed suggestions after reload. Player `/help` completions
are replaced through `TabCompleteEvent` when Bukkit owns the command alias.
An idle topic that spawns a guide starts on the next scheduler tick without a waiting
message. Requests blocked by combat or another guide task explain the actual
reason for waiting.
Automatic arrival, trigger and help-topic actions that spawn a guide pause during combat until
the configured quiet period has elapsed. The bundled mailbox explanation says
to craft the mailbox in the Survival world. `/help` and `/help <topic>` forward
to STEMBot, while `/stembot` and `/stembot <topic>` remain available directly.

Prefix a help topic's target with `no-spawn:` to run it as private text:

```yaml
help-topics:
  mail: mailbox-help
  wilderness: no-spawn:survival-wilderness
```

The prefix is a route option; the action name remains `survival-wilderness`.
No-spawn topics answer immediately, including during combat or another guide
conversation. They do not need Citizens, safe NPC ground or a skin. They do not
start private reply capture, robot sounds, gestures, particles or farewells,
and leave an existing guide session untouched. Each command request runs the
action again from the player's current position. Feature disabling still
disables these topics.

These actions support `say`, `talk` (text only), `sleep`, `require-world`,
`exit-region`, `action`, `end` and `close`. Reaching the end of the list closes
silently too. All reachable action jumps and world-check fallbacks are validated;
NPC movement/gestures, `listen` and `await` require a normal topic. Delayed text
is cancelled on world changes, disconnect, `/stembot close`, reload or disable.
World routes and event triggers retain their existing guide spawning behaviour.

## Reusable region directions and world requirements

`exit-region:<region-id>` gives directions from the player's current position to
the closest exit from a supplied rectangular X/Z region. `require-world` guards
an action by Bukkit world-name patterns and, optionally, its dimension:

```text
require-world:pattern[|pattern] [environment] -> fallback-action
```

Patterns support `*`, alternatives use `|`, and matching ignores case. Environment
is `normal`, `nether`, `the_end` or `custom`; omit it to allow any dimension.
A match continues with the next instruction. A mismatch jumps to the configured
fallback action (`end` or `close` also works). The reply-menu lookahead follows
the same world check. Actions own their world restrictions: a region is an X/Z
rectangle interpreted in the player's current world.

The bundled `/help wilderness` topic uses these reusable instructions:

```yaml
help-topics:
  wilderness: no-spawn:survival-wilderness

actions:
  survival-wilderness:
    - 'require-world:survival* normal -> survival-wilderness-world-required'
    - 'exit-region:survival-old-terrain'
    - 'end'
  survival-wilderness-world-required:
    - 'talk:Ask me for these directions when you are in a Survival overworld.'
    - 'end'

regions:
  survival-old-terrain:
    bounds: [] # Set the actual old terrain corners before enabling directions.
    align-to-chunks: true
    messages:
      inside: 'Head {direction} about {distance} blocks to X {x}, Z {z}. Newly generated chunks beyond the old 26.2 area use 26.3.'
      outside: 'You are already beyond the old 26.2 generation boundary.'
      unconfigured: 'I do not have the old terrain boundary mapped yet.'
      blocked: 'The world border blocks the way out of the old terrain.'
```

Merge these sections with the existing deployed script; keep its other actions
and routes. Bundled scripts do not overwrite existing files. Reload STEMCraft
after editing. Adjust `survival*` to the server's actual Survival overworld names;
multiple explicit names can use `survival|survival_north`. `normal` excludes their
Nether and End dimensions.

Bounds accept `"x1,z1;x2,z2"` or `[x1, z1, x2, z2]`. Corners can be in either order
and may be negative. They are **inclusive block coordinates**. For example,
`"0,0;8000,8000"` is a format example, not a confirmed boundary for this server.
Missing/empty bounds leave that region unmapped; the bundled bounds are empty.

Exact block bounds are the default. `align-to-chunks: true` expands to complete
Minecraft chunks for a generation footprint. A maximum of 8000 then includes its
chunk through block 8015; the first block outside starts at 8016. If the last old
block is 7999, use 7999 as the maximum.

Each region may override `messages.inside`, `outside`, `unconfigured` and `blocked`.
The inside reply supports `{direction}`, `{distance}`, `{x}` and `{z}`. Defaults
describe a generic region; generation versions and wilderness dialogue are
ordinary YAML text. Define another region and reference it from another action
to reuse the same instruction for a training area, spawn footprint or other map.

Defining regions does not add topics, actions or chat preferences. `/help` and
`/stembot` topics come solely from `help-topics`; ordinary chat routes come solely
from each action's `listen` instructions. The bundled Survival menu includes
explicit wilderness/new-terrain phrases. Custom scripts must add the routes they
want; deleting the topic or route removes it. Controlled API sessions and
disengaged/public chat retain their existing behaviour.

STEMBot compares the four cardinal exits from the player's current X/Z and picks
the shortest accessible one within the world border. Equal distances prefer
north, east, south, then west. The reply gives the direction, rounded-up horizontal
distance and target block coordinates. A spawned guide also points that way;
the bundled no-spawn command sends only text and ends without a menu.
Ask again after moving to recalculate. The guidance reports when the player is
already outside, bounds are missing or the border blocks all exits.

This is a straight-line horizontal direction, not a terrain-aware walking route
or teleport. Leaving the old footprint does not guarantee a particular biome or
that nobody has previously generated those chunks.

Server checks: [printable checklist](stembot-wilderness-test-checklist.txt).
