# Minigame Placeholder Reference

This page documents the placeholders available in minigame HUD config lines such as:

- `hud.<status>.bossbar.lines`
- `hud.<status>.scoreboard.lines`

Placeholder syntax:

- `{arena:key}` for arena-level values
- `{player:key}` for the current player's values
- `{team:<team-id>:key}` for a specific team's values

Conditional HUD lines:

- Prefix a HUD line with `?{placeholder}` to show it only when that placeholder is truthy.
- Prefix a HUD line with `?!{placeholder}` to show it only when that placeholder is falsey.
- Truthy values are anything except blank, `false`, `0`, `no`, `off`, or `null`.

Bossbar colour:

- `hud.<status>.bossbar.color` now accepts a static bossbar colour such as `PURPLE` or a placeholder-driven value such as `{arena:bossbar-color}`.

## Shared Placeholders

These placeholders are registered for every minigame.

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{arena:name}` | The arena display name. | Uses the arena's configured `name`. |
| `{arena:time-remaining}` | The arena countdown or remaining time. | Formatted as `mm:ss`. |
| `{arena:id}` | The arena ID. | Usually the config key / internal arena identifier. |
| `{arena:status}` | The current arena status. | Raw enum value such as `WAITING` or `RUNNING`. |
| `{arena:players}` | The current joined player count. | Alias of `{arena:joined-players}`. |
| `{arena:joined-players}` | The current joined player count. | Includes players currently in the arena. |
| `{arena:min-players}` | The configured minimum player count. | |
| `{arena:max-players}` | The configured maximum player count. | |
| `{player:score}` | The current player's arena score. | Meaning depends on the minigame. |
| `{player:kills}` | The current player's kill count. | |
| `{player:deaths}` | The current player's death count. | |
| `{team:<team-id>:name}` | The target team's internal name. | Example: `{team:red:name}`. |
| `{team:<team-id>:display-name}` | The target team's display name. | Example: `{team:red:display-name}`. |

## Bridge

Bridge uses the shared placeholders above and adds:

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{player:team-status}` | The current player's team display. | Returns a colored team name, `<dark_gray>Spectating`, or `<dark_gray>Unknown`. |
| `{team:red:score}` | Red team's score display. | Renders the team's display name plus a 7-heart score bar. |
| `{team:blue:score}` | Blue team's score display. | Renders the team's display name plus a 7-heart score bar. |

Bridge only supports the `red` and `blue` teams, so those are the expected team IDs for team placeholders.

## Parkour

Parkour uses the shared placeholders above and adds:

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{arena:record-time}` | The best recorded completion time for the arena. | Returns `-` if there is no record yet. |
| `{arena:record-holder}` | The player name holding the arena record. | Returns `-` if there is no record yet. |
| `{player:run-state}` | The player's current run state. | Returns `ready`, `running`, or `finished`. |
| `{player:run-time}` | The player's live or most recent run time. | While running it shows live elapsed time; after finishing it shows the last completed run time; otherwise `-`. |
| `{player:best-time}` | The player's best time in the current arena. | Returns `-` if the player has no personal best yet. |

## BedWars

BedWars uses the shared placeholders above and adds:

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{arena:team-count}` | The number of teams configured in the arena. | |
| `{arena:team-size}` | The configured size of each team. | |
| `{arena:winner}` | The winning team display name. | Returns `-` until a winner is set. |
| `{arena:team-line-1}` | Team summary line for the first team. | Teams are sorted by team ID. Empty if no team exists in that slot. |
| `{arena:team-line-2}` | Team summary line for the second team. | Shows the configured team label, bed status, and current player count. |
| `{arena:team-line-3}` | Team summary line for the third team. | |
| `{arena:team-line-4}` | Team summary line for the fourth team. | |
| `{arena:team-line-5}` | Team summary line for the fifth team. | |
| `{arena:team-line-6}` | Team summary line for the sixth team. | |
| `{arena:team-line-7}` | Team summary line for the seventh team. | |
| `{arena:team-line-8}` | Team summary line for the eighth team. | |
| `{player:final-kills}` | The current player's final kill count. | |

`{arena:team-line-N}` renders a compact team status line. During a running game it can show a team as eliminated when the bed is gone and no players remain.

BedWars `team-line` output is configurable from `bedwars.yml`. If the root keys are missing, STEMCraft now seeds these defaults automatically:

```yaml
placeholders:
  black: "&0black"
  blue: "&9blue"
  brown: "&6brown"
  cyan: "&3cyan"
  gray: "&8gray"
  green: "&2green"
  light_blue: "&blight blue"
  light_gray: "&7light gray"
  lime: "&alime"
  magenta: "&dmagenta"
  orange: "&6orange"
  pink: "&dpink"
  purple: "&5purple"
  red: "&cred"
  white: "&fwhite"
  yellow: "&eyellow"
  bed: "&abed"
  no-bed: "&cno bed"
  remaining-players: "&7({count})"
  no-remaining-players: "eliminated"
  team-line: "{colour}: {state}"
```

`team-line` supports `{colour}` and `{state}`. `remaining-players` supports `{count}`. This also means you can replace team names or states with glyph tokens, for example:

```yaml
placeholders:
  blue: ":mc_blue_bed:"
  red: ":mc_red_bed:"
  yellow: ":mc_yellow_bed:"
  bed: ":green_tick:"
  no-bed: ":red_cross:"
  remaining-players: ":steve: {count}"
  no-remaining-players: ":skull:"
  team-line: "{colour} {state}"
```

## Boat Race

Boat Race uses the shared placeholders above and adds:

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{arena:leader}` | The current race leader's player name. | Returns `-` if there is no leader yet. |
| `{arena:winner}` | The winning player's name. | Returns `-` until a winner is set. |
| `{arena:stage-count}` | The number of configured stage checkpoints. | |
| `{player:place}` | The current player's race position. | Formatted as an ordinal such as `1st`, `2nd`, or `3rd`. |
| `{player:progress}` | The current player's stage progress. | Formatted as `current/total`, for example `2/5`. |
| `{player:next-target}` | The player's next race target label. | Returns `Stage N` or `Finish`. |

## Nightfall

Nightfall uses the shared placeholders above and adds:

| Placeholder | Meaning | Notes |
| --- | --- | --- |
| `{arena:active-players}` | Survivors still active in the current cycle. | |
| `{arena:blood-moon}` | Whether the current sunset-to-sunrise cycle is a blood moon. | Returns `true` or `false`. |
| `{arena:bossbar-color}` | The active bossbar colour for Nightfall. | Returns `RED` during a blood moon, otherwise `PURPLE`. |
| `{arena:cycle-countdown-line}` | Countdown text for the current day/night phase. | |
| `{arena:generator-count}` | Number of configured generator locations. | |
| `{arena:night}` | Current completed night count. | |
| `{arena:phase}` | Current phase label. | |
| `{arena:phase-number}` | Current day/night number. | |
| `{arena:phase-line}` | Current phase detail line. | |
| `{arena:zombies-remaining}` | Total zombies still queued or alive this night. | |
| `{arena:zombies-alive}` | Zombies currently alive in the arena. | |
| `{arena:zombies-queued}` | Zombies still queued to spawn this night. | |
| `{player:lives-left}` | Remaining life state for the current cycle. | |

## Example

Example scoreboard lines:

```yaml
hud:
  waiting:
    scoreboard:
      lines:
        - "<gold>Bridge: <white>{arena:name}"
        - "Players: {arena:joined-players}/{arena:max-players}"
        - "Red: {team:red:score}"
        - "Blue: {team:blue:score}"
        - "Team: {player:team-status}"
```

## Notes

- Team placeholders only make sense for minigames that define teams.
- Placeholder availability comes from the current source code, so this page should be updated when new placeholders are registered.
