# AFK

The `Afk` feature automatically marks players away after five minutes without activity. `/afk` toggles a player's own status manually. Movement (including looking around), chat, commands other than `/afk`, inventory clicks/drags, hand/item changes, arm swings, dropping items, and interactions count as activity. This is basic inactivity detection, not an anti-AFK-farm system.

Players see yellow text with no icon: `James is now AFK` and `James is no longer AFK`. Only the player's name becomes italic in STEMCraft's tab list; prefix and badge formatting are preserved. This requires the PlayerTabList feature and `{player}` in its name format.

```yaml
afk:
  enabled: true
  idle-minutes: 5
  kick-after-afk-minutes: 30
```

The kick timer starts when AFK begins: defaults mean 35 minutes total inactivity, or 30 minutes after manually using `/afk`. Set `kick-after-afk-minutes: 0` to keep status tracking without kicks. Timers use elapsed real time and are checked once per second. Returning resets the timers. Reconnecting starts a new session; feature reload keeps session state unless disabled. Disable clears status silently and restores tab names.

`stemcraft.command.afk` defaults to true. `stemcraft.afk.kick-exempt` defaults to false, including for operators. An exempt player is still marked AFK. A cancelled kick is retried at most once a minute.

Paper's `player-idle-timeout` is independent. Set it to `0` in `server.properties` if STEMCraft should control idle kicks and exemptions; STEMCraft does not change that server setting itself.
