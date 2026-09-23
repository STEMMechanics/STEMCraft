# Minigame arena setup and testing

The minigames below each extend `BaseMiniGame` directly and implement their own
`MiniGameArenaHandler`, command, config serializer and arena settings in a separate
package under `dev.stemcraft.minigame`. They use STEMCraft's Minigame Framework and API for registration, occupancy,
player inventory/game-mode restoration, spectators, countdowns, HUDs, commands,
configuration, events, tasks, selections and winner rewards. They are discovered
automatically when the plugin loads; no separate plugin or registration is needed.
WorldEdit is needed for arena and checkpoint selections.

| Game | Command | Arena layout and rules |
| --- | --- | --- |
| Minecart Racing | `/minecartrace` | A rail circuit with individual starting rails, ordered checkpoint gates, and a final lap/finish gate. Right-click the supplied feather for a boost every five seconds. Rails guide the cart; reachable levers can control track junctions. First to complete all laps wins. |
| Volcano | `/volcano` | A flat destructible platform. Flames mark incoming debris before magma impacts and neighbouring holes form. Contact with impact magma or falling below the platform eliminates a player. Eruptions become faster and wider. Last survivor wins. |
| Horse Racing | `/horserace` | A fenced course with jumps and ordered gates. Each rider receives a horse with identical speed and jump strength. First to complete all laps wins. |
| Mob Shooter | `/mobshooter` | An enclosed shooting gallery with player positions and target spawn points. Shoot moving chickens (3), rabbits (2), and pigs (1). Arrows replenish. Highest score at timeout wins. |
| Punch the Bat | `/punchthebat` | An enclosed room with reachable bat spawn points. Empty-handed punches score 1; glowing bats score 5. Targets respawn. Highest score at timeout wins. |
| Floor Shuffle | `/floorshuffle` | A flat platform above a drop. Move to the announced concrete colour before other tiles disappear. Tiles return and shuffle after each round, with progressively shorter warnings. Last survivor wins. |
| Minecraft Quake | `/quake` | A combat arena with cover, sight lines, and spread-out spawns. Right-click the supplied hoe to fire a railgun. One hit scores a kill. Hits stop at solid cover. Respawns have 1.5 seconds of protection and cannot fire during protection. First to the kill target wins. |

Administrative commands require operator status or
`stemcraft.command.<command>`; players can use list, info, join, spectate and leave.
Running a game command without arguments lists its arenas.

The existing `MiniGameConfigSupport` supplies common spawn validation and world
loading; `MiniGameHudConfigSupport` supplies configurable HUD defaults. Gameplay
and round state remain in each game's own handler. The command roots, YAML filenames
and applicable arena keys are unchanged from the initial test build.

## Common setup

Replace `floorshuffle` with the desired command and `test` with your arena ID.
IDs use lowercase letters, digits, hyphens and underscores.

1. Build the map. Use separate, non-overlapping arena regions for each arena.
2. Run `/floorshuffle create test` in the map's world. The new arena is disabled.
3. Select the whole play area using WorldEdit, then `/floorshuffle set test arena`.
   Include headroom, all player and target spawns, and the falling space beneath
   survival floors. Checkpoints must fit entirely inside this selection.
4. Stand in the waiting lobby and run `/floorshuffle set test lobby`.
5. Stand at a spectator viewing point and run `/floorshuffle set test spectator`.
6. Stand at each player starting position and run `/floorshuffle addspawn test`.
   Add one distinct position per player. For survival games, stand directly on
   the platform. For minecarts, stand on each starting rail facing the track.
7. Set capacity: `/floorshuffle set test maxplayers 4` and
   `/floorshuffle set test minplayers 2`. Defaults are 8 maximum and 2 minimum.
8. Complete the game-specific geometry below.
9. Run `/floorshuffle validate test`. Fix every reported issue, then
   `/floorshuffle enable test`.
10. Join using `/floorshuffle join test`. Reaching the minimum starts a countdown.

Setup edits save automatically to `plugins/STEMCraft/<command>.yml` under `arenas`.
Use `/<command> stop <arena>` before editing an enabled arena. Stop removes its
occupants, restores its changed blocks, removes game entities, cancels its loop,
and disables it. Re-enable after editing. `delete` removes the definition only.
Configuration changes made directly to YAML require a plugin/server restart.
Do not edit YAML while simultaneously using setup commands.

## Game-specific geometry

### Racing

For each checkpoint, select a thin gate covering the full width and height of the
track and run `/horserace addcheckpoint test` (or `/minecartrace ...`). Add gates in
travel order. **The last gate is the lap/finish line.** Configure at least two gates;
consecutive gates must not overlap. Place the starting grid before the first gate,
outside its region. Checkpoint selections are limited to 100,000 blocks each.

Use a circuit for multiple laps. For a point-to-point track set `laps 1`.
Checkpoint progress only counts while riding the assigned mount. Leaving the arena,
losing the mount or dismounting returns the racer and a replacement mount to their
last checkpoint (or starting grid). Build fences/barriers to prevent shortcuts
between gates. Minecart tracks need connected, powered rails; build straight boost
sections and test corners at speed. Do not put a gate on a gap or obstacle: its
crossing position is also the recovery point.

### Volcano and Floor Shuffle

Select **one layer of solid floor blocks**, then `/volcano set test floor` or
`/floorshuffle set test floor`. Select 6–4,096 blocks. Containers and block entities
cannot be used as floor tiles. Include the whole floor inside the arena region.
Leave open space underneath: dropping beneath the floor eliminates a player.

Floor Shuffle temporarily recolours all selected blocks, guarantees that every
announced colour exists, and restores the original blocks after the match.
Volcano changes only the selected floor; its lava appearance uses magma blocks
and particles, so fluid cannot spread into the surrounding map.

### Mob Shooter and Punch the Bat

Stand at each target spawn point and run `/mobshooter addtarget test` or
`/punchthebat addtarget test`. At least one point is required. Spread points out to
avoid crowding; `targetcount` controls total live targets, not targets per point.
Mob Shooter needs floor space for the animals to wander. Keep bats within punching
reach using a low ceiling. The arena bounds remove escaped targets and replace them.

### Quake

Use multiple separated player spawns. Respawns choose the configured spawn farthest
from its nearest opponent. Include cover to interrupt railgun shots. Keep all paths
inside the arena bounds; leaving returns a player to their assigned recovery spawn.

## Commands and settings

| Command after the game root | Purpose |
| --- | --- |
| `list`, `info <arena>` | Arenas and validation summary |
| `join <arena>`, `spectate <arena>`, `leave` | Participate, watch, or leave |
| `create <arena>`, `delete <arena>` | Create/remove a definition |
| `set <arena> arena\|floor` | Store the current WorldEdit selection |
| `set <arena> lobby\|spectator` | Store your current location |
| `addspawn <arena>`, `addtarget <arena>` | Append your current location |
| `addcheckpoint <arena>` | Append the current WorldEdit selection |
| `removespawn\|removetarget\|removecheckpoint <arena> <number>` | Remove a 1-based entry |
| `validate <arena>`, `enable <arena>` | Check setup, then open the arena |
| `start <arena>` | Start a waiting match with at least one joined player |
| `stop <arena>`, `disable <arena>` | Abort, clean up, and disable for editing |
| `save <arena>` | Explicitly save current setup |

Use `set <arena> <setting> <number>`. Each game exposes only its applicable settings:

| Setting | Default | Valid values | Applies to |
| --- | --- | --- | --- |
| `minplayers` | 2 | 1–64, at most maxplayers | All |
| `maxplayers` | 8 | 1–64, at most configured spawns | All |
| `roundseconds` | 180 | 1–3600 | All |
| `countdown` | 10 | 1–60 seconds | All |
| `laps` | 3 | 1–100 | Races |
| `killtarget` | 20 | 1–1000 | Quake |
| `reloadticks` | 30 | 5–200 ticks | Quake |
| `targetcount` | 12 | 1–64 | Mob Shooter, Punch the Bat |

HUDs are configurable in each game's YAML. Additional placeholders are
`{arena:objective}`, `{arena:winner}` and `{player:progress}`, alongside framework
score, player count, status and time placeholders.

## Test checklist

- Solo: enable, join, then `start` before the automatic minimum is reached.
  Survival practice continues until you fall or time expires. Solo matches never
  grant winner rewards. Completed rounds show results for eight seconds, restore
  the arena, and return occupants to their original state/location.
- With two players: check countdown spawns, spectators, the win condition, and
  leaving/disconnecting during countdown and play.
- Race through every gate, skip one, dismount, and cross the finish both before
  and after the required laps. Verify horse equality and minecart corner behavior.
- In both survival games, verify falling and impact elimination, then stop during
  an active hazard and compare the restored floor with the original.
- Shoot targets or punch bats: check point values, replenishment, and that another
  arena or outsider cannot score against this arena's targets.
- Quake: check cover, reload timing, respawn protection, score limit, and timeout.
- Run two arenas simultaneously and stop one; the other must continue normally.
- Check inventories, game modes, mounts, arrows and blocks after normal completion,
  manual stop, disconnect, and a normal server restart during play.

At timeout, score games/races award the highest score/progress (ties share the win;
zero scores give no winner). Survival games share a timeout win among survivors.
Map building, rail physics, mob movement and combat feel need a live Paper server
playtest; automated tests cannot establish those aspects.
