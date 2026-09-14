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

`move(destination, waitForPlayer)` replaces the current movement order and uses the scripted walk navigation/recovery. With `waitForPlayer=true`, the bot pauses and resumes using the configured distance thresholds. False means it proceeds independently. The return value acknowledges an accepted order, not arrival. If navigation cannot recover, it stops; callers may issue a new move or teleport. `follow(enabled)` tracks the moving player instead of a fixed destination. `location()` returns an optional defensive copy. `teleport(destination)` cancels navigation and returns whether teleporting succeeded. Both move and teleport require finite coordinates in the owner's current world. These operations run on the server thread.

`FirstJoinService` is a client of this API. It owns questions, attempts, deadlines, command restrictions and persistence. It passes localized presentation text to its guide handle, asks it to follow, and starts its configured action after a correct answer. The bot has no verification-specific state or dependency on FirstJoin. Without an active actor, FirstJoin uses ordinary messages and its movement restriction. World transitions reacquire a guide without resetting the question or deadline. Success explicitly requests `first-join.stembot-action` from `config.yml`; a blank value closes the guide, and a missing action closes it with an administrator log message. The bundled value is `first-hub`. The main `world` has no automatic `first-time` mapping, so FirstJoin owns that introduction. Other configured worlds retain automatic introductions.

`StemBotService.hasAction(name)` checks the loaded script. `startAction(player, name)` runs a named action without marking any automatic introduction as seen. `startAction(name)` also exists on a controlled handle, preserving the actor when possible. First-time world mappings are internal automation and have no public start/pause API. Invalid requests return false and retain control so the caller can retry or close. There is no implicit command-action fallback.

Automatic introductions use their per-world seen marker. Controlled sessions suspend automatic triggers. Avoid configuring both a FirstJoin completion action and an automatic first-time action for the same starting world. On an existing server, remove `first-time.world.world` from its deployed `stembot.yml` and set `first-join.stembot-action` in its deployed `config.yml`; bundled files do not overwrite an existing script.

## Script and timing

Actions are lists of instructions: `say`, `talk`, `sleep`, `walk`, `speed`, `look`, `point`, `wave`, `sneak`, `stand`, `action`, `listen`, `await`, `end` and `close`. World mappings select command and first-visit actions. Route targets and instruction syntax are validated before sessions start. `talk` includes speech and the configured post-delay; `say` does not wait. Avoid long sequences of `say` for player guidance.

`listen` routes recognise configured wildcard phrases. They are not an unrestricted language model. Sessions look ahead to the next listen menu so known feedback can interrupt talking and walking. Dismissal phrases work throughout the action sequence. Speech revisions prevent old queued speech from continuing after a route change.

## Privacy and movement

Only the session owner sees the actor. Nearby ordinary chat is private to the guide; slash commands retain their normal meaning. Configurable distance thresholds disengage/reengage private chat, and abandoned sessions time out. Use hysteresis: reengagement distance must be lower than disengagement distance.

Movement has bounded stall detection and an arrival tolerance. A stalled route can offer continuation; it must not loop or keep sending failure text. Spawn search is bounded and checks safe space. Departure waits briefly before particles and actor disposal. Summoning elsewhere replaces the old session without a duplicate farewell/welcome pair.

## Skins and lifecycle

Explicit signed texture values live directly in `stembot.yml`. URL conversions use the shared [skin request coordinator](skin-requests.md); failed sources have persistent backoff and successful textures are saved to the script configuration. Epoch checks prevent a completed old request from applying to a replacement session.

Reload/disable must close actors, cancel speech and movement work, and stop chat routing. Do not call quest implementation classes from STEMBot. Interactive steps use the public `GuideActionRequestEvent` and registered providers, with owner-scoped cleanup; `BotSession` never imports quest NPC/storage internals. See [interactive guide callbacks](guide-callbacks.md) for the `await` syntax, cancellation contract, coordinate-bar provider and private practice quest. These interactive steps are introduced in PR #162.

## Keeping the introduction optional

The bundled Survival introduction offers practice without starting it automatically. Detailed plot, quest and navigation guidance belongs behind topic routes. Practice requires explicit acceptance, supports skip and recognised-topic interruption, and has bounded waits. Do not turn every command into a tutorial requirement. Providers must observe successful state changes rather than treating typed chat as proof that a command ran.
