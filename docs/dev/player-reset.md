# Player reset service

The player reset service coordinates destructive removal of player-owned data. Features and extensions register `PlayerResetHandler` implementations through `api.playerResets()` so each subsystem can clear both persistent storage and runtime caches.

Moderation records, punishments, reports, audit history, and login history are deliberately retained by every scope.

## Administration

The command requires `stemcraft.player-reset.admin` and stores an expiring preview for each administrator:

```text
/playerreset <player> [progression|gameplay|complete]
/playerreset confirm
```

The area is optional and defaults to `complete`. The preview identifies every participating handler and how many records or files it expects to remove. `/playerreset confirm` confirms only the preview created by that administrator, and the preview expires after ten minutes.

When confirmed, an online target is kicked before any deletion begins. The coordinator waits for the disconnect save to finish and prevents the player from reconnecting while the reset is running.

- `progression` removes statistics, professions, quests, minigame records, entitlements, and badges.
- `gameplay` includes progression and also removes game-mode inventories, mailbox data, locations, notice posts, graves, and vanilla inventory/XP/statistic/advancement data.
- `complete` includes gameplay and also resets first-join, welcome, and random-spawn state.

## Extension API

Handlers declare the exact scopes in which they participate, provide a preview, and perform an idempotent reset:

```java
api.playerResets().register(new PlayerResetHandler() {
    public String id() { return "my-feature"; }
    public Set<PlayerResetScope> scopes() {
        return Set.of(PlayerResetScope.PROGRESSION, PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE);
    }
    public PlayerResetPreview preview(PlayerResetContext context) {
        return new PlayerResetPreview("My feature progress", count(context.playerUuid()));
    }
    public void reset(PlayerResetContext context) {
        deletePersistentData(context.playerUuid());
        clearRuntimeCache(context.playerUuid());
    }
});
```

Handlers run in ascending priority order and processing stops on the first failure. The coordinator records the outcome through the audit service and reports partial completion explicitly.
