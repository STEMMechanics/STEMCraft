# Debug tests

STEMCraft exposes one permission-gated `/debug` command for diagnostics that
belong to services or features. A feature registers a named test through the
API and owns the returned handle:

```java
DebugRegistration registration = api.debug().register(
    "example-state",
    "Inspect the example state for a player.",
    context -> {
        Player player = context.player();
        context.info("Player: " + player.getName());
        context.success("The example state is ready.");
    }
);
```

Close the registration when the feature is disabled or when the test is no
longer valid. IDs are case-insensitive and use lowercase letters, numbers,
dots, underscores, and hyphens. The command lists tests when called without a
test ID. A player can run `/debug <test>` to inspect themselves; the console
must supply an online player with `/debug <test> <player>`.

The command requires `stemcraft.command.debug`. Tests should report their
configuration gates and use the same rendering or delivery path as the live
feature wherever possible. This makes a debug run a useful preview of the
player-facing result without waiting for an event or timer.

## Workshop login test

When `AnnouncementsFeature` is enabled, `/debug workshop-login-active` renders
the active-workshop notice and access instructions, and sends those same lines
to the selected player immediately. If there is no active workshop, it
previews the next eligible workshop or generates an example workshop so
formatting can still be tested. It does not create a delayed join task. From
the console, use for example:

```text
/debug workshop-login-active Alex
```

## Workshop diagnostics

The Workshops feature registers additional read-only previews:

- `/debug workshop-status Alex` reports the feature gates, cached workshop
  counts, upcoming entries, access-instruction target, and feed synchronisation
  state.
- `/debug workshop-upcoming Alex` sends the configured `/workshops` display to
  Alex immediately, including display placeholders, booking handling, and
  access instructions. If the catalogue is empty, it uses an example workshop
  for the preview.
- `/debug workshop-access-prompt Alex` selects the access-instructions target
  and sends the configured prompt immediately. It previews the text even when
  the normal live time-window or stored-instructions check would skip it.

The announcements feature also provides:

- `/debug workshop-login-upcoming Alex` renders and sends the configured
  upcoming-workshop login notice immediately; the configured login delay is
  bypassed for debugging. It uses the next eligible workshop or example data
  when no configured match exists.

`workshop-status` remains the explicit state diagnostic. The other workshop
preview tests send only the player-facing message to the target player; when a
preview is run from the console or for another player, the invoking sender
receives only the same plain text. The normal `/workshops refresh` command
remains the way to start an actual feed refresh.
