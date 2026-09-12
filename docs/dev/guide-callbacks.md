# Interactive guide callbacks

STEMBot can wait for a verified action using `GuideActionRequestEvent` from the public API. Coordinates and Quests register providers through `api.events()`. Neither module imports STEMBot classes, and STEMBot does not import quest implementation classes.

## Script instruction

```yaml
- 'talk:Run /coordbar to enable your coordinate bar. Say skip to stop.'
- 'await:stemcraft:coordbar 60 -> coordinates-done, practice-skipped'
```

The namespaced key selects a provider. The timeout is 1–300 seconds. Both success and fallback actions must exist. Unavailable providers, failures and timeouts use the fallback. `skip`, `later` and `not now` also use it. Normal dismissal and other recognised topics interrupt the step; they never count as success. Timers pause while the player is outside conversation range, with the session's away cleanup still enforced.

## Provider contract

Register an ordinary listener for `GuideActionRequestEvent`, match `action()`, and call `claim(cleanup)` before allocating resources. Only the first provider can claim a request. Call `complete(true)` after observing actual success, or `complete(false)` when the step cannot continue. Cleanup runs once, before the completion callback. Caller cancellation runs cleanup without completing the abandoned action; repeated completion/cancellation and late callbacks are ignored. All methods and callbacks must run on the server thread.

```java
api.events().register(GuideActionRequestEvent.class, request -> {
    if (!request.action().equals("example:practice")) return;
    if (!request.claim(() -> removeTemporaryResources(request.player()))) return;
    // Store request; complete it when an event verifies the objective.
});
```

The caller dispatches the event and checks `claimed()`. An unclaimed event means no provider is available; do not wait indefinitely. Retain `request::cancel` until completion or cancellation. Providers must also clean up their requests on feature disable and must not complete from asynchronous workers.

## Built-in providers

| Key | Provider | Verified success |
| --- | --- | --- |
| `stemcraft:coordbar` | Coordinates | Boss bar is enabled already, or successfully enabled by the toggle command. Requires `stemcraft.command.coordbar`. |
| `stemcraft:quest-practice` | Quests | Owner accepts a private quest, walks four horizontal blocks from the acceptance position, returns and hands in the practice book. |

Practice is only offered in Survival worlds. Its villager is hidden by default before spawning and explicitly shown only to its owner. The safe-spot search uses loaded chunks, air space, a solid floor and the world border. Another player cannot accept or complete it. The practice book has a separate persistent-data key and never enters campaign storage, reward granting, completion statistics or XP handling.

The book cannot be dropped or moved into storage. Completion, cancellation, death, world changes, quitting, feature reload/disable and a five-minute provider limit remove the NPC and book. Join/startup cleanup removes stale practice books after an unclean shutdown. Script timeouts can be shorter than the provider limit.

## Verification

Automated coverage includes parser bounds, synchronous callbacks, late completion, topic changes, skip/dismissal/timeouts, actual coordinate-bar state, private NPC ownership, acceptance/movement/hand-in, temporary item cleanup and no reward/stat updates. On a Paper server, also verify owner-only visibility with two clients, interaction protection plugins, inventory shift/hotbar operations, reconnect/reload cleanup and a fully populated Survival world.

Existing servers keep their editable `stembot.yml`. Merge the new actions and menu routes from the bundled file into the deployed script, then reload STEMBot; do not overwrite custom dialogue without reviewing it.
