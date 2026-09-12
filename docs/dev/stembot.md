# STEMBot

`StemBotFeature` owns the private guide, `BotScript` validates the YAML program, `BotSession` interprets it on the server thread, and `BotActor` isolates Citizens movement/rendering. Dialogue and routes live in `stembot.yml`, while `config.yml` controls feature availability. Citizens is optional for the plugin, but required for the guide actor.

## Script and timing

Actions are lists of instructions: `say`, `talk`, `sleep`, `walk`, `speed`, `look`, `point`, `wave`, `sneak`, `stand`, `action`, `listen`, `end` and `close`. World mappings select command and first-visit actions. Route targets and instruction syntax are validated before sessions start. `talk` includes speech and the configured post-delay; `say` does not wait. Avoid long sequences of `say` for player guidance.

`listen` routes recognise configured wildcard phrases. They are not an unrestricted language model. Sessions look ahead to the next listen menu so known feedback can interrupt talking and walking. Dismissal phrases work throughout the action sequence. Speech revisions prevent old queued speech from continuing after a route change.

## Privacy and movement

Only the session owner sees the actor. Nearby ordinary chat is private to the guide; slash commands retain their normal meaning. Configurable distance thresholds disengage/reengage private chat, and abandoned sessions time out. Use hysteresis: reengagement distance must be lower than disengagement distance.

Movement has bounded stall detection and an arrival tolerance. A stalled route can offer continuation; it must not loop or keep sending failure text. Spawn search is bounded and checks safe space. Departure waits briefly before particles and actor disposal. Summoning elsewhere replaces the old session without a duplicate farewell/welcome pair.

## Skins and lifecycle

Explicit signed texture values live directly in `stembot.yml`. URL conversions use the shared [skin request coordinator](skin-requests.md); failed sources have persistent backoff and successful textures are saved to the script configuration. Epoch checks prevent a completed old request from applying to a replacement session.

Reload/disable must close actors, cancel speech and movement work, and stop chat routing. Do not call quest implementation classes from STEMBot. Cross-feature tutorials should use a public quest contract with owner-scoped cleanup and registered callbacks; no coupling between `BotSession` and NPC/storage internals is needed.
