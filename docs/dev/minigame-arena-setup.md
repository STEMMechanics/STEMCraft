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
| Volcano | `/volcano` | A flat destructible platform. Smoking vents erupt upward as scattered spurts, clustered bursts or fissures. Real lava flows over the floor and cools into volcanic rock; craters open in varied directions. Survive longest: lethal hazard damage or falling eliminates a player. Last survivor wins. |
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
   Add one distinct position per player. Punch the Bat can skip this step for random
   safe player spawns. For survival games, stand directly on
   the platform. For minecarts, stand on each starting rail facing the track.
   Floor Shuffle and Volcano can skip this step: with no configured spawns, players spawn
   randomly on the selected floor, one block above the floor blocks.
7. Set capacity: `/floorshuffle set test maxplayers 4` and
   `/floorshuffle set test minplayers 2`. Defaults are 8 maximum and 2 minimum.
8. Complete the game-specific geometry below.
9. Run `/floorshuffle validate test`. Fix every reported issue, then
   `/floorshuffle enable test`.
10. Join using `/floorshuffle join test`. Reaching the minimum starts a countdown.
    These minigames keep players in the lobby throughout that countdown and move
    them to their starting positions when it ends. Floor Shuffle and Volcano then
    give players 15 seconds on the floor to get ready before gameplay begins.
    With `minplayers 1`, joining starts
    the countdown immediately; the player still waits in the lobby for its duration.

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
Leave open space underneath. Volcano eliminates players below the floor; Floor
Shuffle eliminates them only after they leave the arena bounds. Set the bottom of
the Floor Shuffle arena to the height where falling players should be eliminated.

Floor Shuffle temporarily recolours all selected blocks, guarantees that every
announced colour exists, and restores the original blocks after the match.
Before removal, unsafe tiles flash white twice, returning to their assigned colour
between flashes. Safe tiles remain unchanged. Each flash lasts 0.3 seconds, with
0.3 seconds of the original colour after it; this uses the final 1.2 seconds of
the existing warning time, then the unsafe tiles disappear.

While the arena is waiting or counting down, its floor shows a random colour
pattern every three seconds. Change this using
`/floorshuffle set test lobbyshuffle 3` while the arena is stopped, or
`arenas.test.lobby-shuffle-seconds: 3` in `floorshuffle.yml`. Values are 0–3600
seconds; 0 disables the lobby animation. `info` shows the configured interval.
Lobby patterns never remove blocks or announce a target colour. Animation resumes
after a match resets; stopping/disabling/unloading restores the original floor.

Its `spawns` list is optional. If any spawns are configured, provide at least
`maxplayers` entries directly on the selected floor. With no spawns configured,
each player starts at a random floor block, centred horizontally and one block
above it when the countdown ends, including players who joined during the countdown.
The default 15-second preparation delay then gives players time on the solid floor
before the first colour challenge. Lobby animation stops during preparation.
Each new colour is announced in matching-colour chat and a centre-screen title
lasting two seconds (no fade). Configure the MiniMessage text in `floorshuffle.yml`:

```yaml
strings:
  colour:
    chat: '<{colour-tag}>Stand on {colour}!</{colour-tag}>'
    title: '<{colour-tag}><bold>{colour}</bold></{colour-tag}>'
    subtitle: '<{colour-tag}>Stand on this colour!</{colour-tag}>'
    objective: '<{colour-tag}>Stand on {colour}!</{colour-tag}>'
```

`{colour}` becomes the uppercase colour name and `{colour-tag}` becomes its hex
colour tag, including distinct orange, purple and lime colours. `objective` is the
text used by `{arena:objective}` in the HUD. Restart after editing the YAML.

Volcano changes the selected floor and temporarily places real lava in clear air
one block above it, within the arena bounds. Leave several blocks of open space
above the floor for rising lava jets. Spreading flows are controlled by the game;
vanilla fluid movement, lava hardening and lava ignition from those flows are
blocked. Lava covers at most a third of the selected floor, and crater holes at
most a quarter. Both the floor's original block data and air above it are restored
after a round, manual stop, unload or normal shutdown. Hot tiles cool into basalt
or blackstone during play.

Magma uses normal Minecraft heat damage, and real lava uses normal lava damage.
Flame markers deal half a heart per second while touched. Lethal damage eliminates
the player into spectator mode; PvP remains disabled. The default running HUD
shows personal survival time instead of an eruption count. Eliminated players'
timers freeze, and scores record whole seconds survived. Existing generated HUD
lines are upgraded; custom or disabled displays are preserved. Custom HUDs can
use `{player:survival-time}` for the player's `mm:ss` time.
Like Floor Shuffle, Volcano allows an empty `spawns` list and chooses random floor
blocks, centred horizontally and one block above the floor. If any spawns are
configured, there must be at least `maxplayers` entries directly on the floor.
Players joining during Volcano's lobby countdown stay in the lobby and receive
their floor spawn when it ends. The default 15-second preparation delay then gives
them time to get ready before eruptions and survival scoring begin. `info` reports
whether random or configured spawns are used.

### Mob Shooter and Punch the Bat

Stand at each target spawn point and run `/mobshooter addtarget test` or
`/punchthebat addtarget test`. Mob Shooter requires at least one point. Spread points out to
avoid crowding; `targetcount` controls total live targets, not targets per point.
Mob Shooter needs floor space for the animals to wander. Keep bats within punching
reach using a low ceiling. The arena bounds remove escaped targets and replace them.

Punch the Bat allows either list to be empty independently. With no `addspawn`
entries, players receive distinct random safe positions inside the arena when the
countdown ends. With no `addtarget` entries, bats spawn randomly half a block
above those ground-level positions, within punching reach. Configured lists take
precedence; a nonempty player spawn list still needs at least `maxplayers` entries.
Random positions require solid, non-magma ground and two clear air blocks inside
the actual arena region. Random mode supports arena bounding boxes up to 100,000
blocks and validation checks available space. `info` reports both spawn modes.
Remove all existing entries with `removespawn` / `removetarget` to use random mode.

### Quake

Use multiple separated player spawns. Respawns choose the configured spawn farthest
from its nearest opponent. Include cover to interrupt railgun shots. Keep all paths
inside the arena bounds; leaving returns a player to their assigned recovery spawn.

## Commands and settings

| Command after the game root | Purpose |
| --- | --- |
| `list`, `info <arena>` | List arenas or show full arena settings, locations and geometry |
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

`info` reports world, status, player capacity, spectators, timers, lobby and
spectator locations, numbered starting spawns, and game-specific settings such as
floor regions, checkpoints, laps, target spawns and reload timing. Floor Shuffle
and Volcano also report their spawn mode. Use `validate` separately to check setup while the
arena is stopped; `info` can be used during a match without validating altered tiles.

### Scoreboard and bossbar

These seven games use the same gold headings, coloured values, icons, spacing and
purple rotating bossbar style as BedWars, Nightfall and Boat Race. Waiting shows
joined/required players, starting shows the countdown, running shows game-specific
progress and time remaining, and ending shows the winner and reset countdown.
Survival games show survivors and the current hazard/colour; races show checkpoint
progress and laps; target games show score; Quake shows kills, kill target and deaths.

Configure `hud.<waiting|starting|running|ending>.scoreboard.lines` and
`hud.<state>.bossbar.lines`, `hold-updates` and `color` in each game's YAML.
The rotating top display is a bossbar, not an actionbar. On plugin/server restart,
unchanged HUD lines from the original generic defaults are upgraded automatically;
custom lines, including individually disabled displays, are preserved. A one-time
repair restores configs affected by the old migration bug where all four states'
bossbar and scoreboard lists were saved empty with the generated purple/five-update
settings. Once repaired, deliberately empty lists remain disabled on future loads.
The original generated bossbar
timing and colour upgrade to five updates per line and purple alongside its lines.

Start countdowns default to 30 seconds and results remain visible for 20 seconds
before reset, matching the reference games. The original 10-second start default
is upgraded once when loading an older arena config; other configured countdowns
are preserved. After migration, `countdown` remains configurable from 1–60 seconds.

Floor Shuffle and Volcano also have a separate preparation delay on the playing
floor. It defaults to 15 seconds for new and existing arenas without a configured
value. Set it with `/floorshuffle set test startdelay 15` or
`/volcano set test startdelay 15` while the arena is stopped. Valid values are 0–60;
0 starts gameplay immediately after the lobby countdown. The YAML key is
`arenas.test.start-delay-seconds`. `info` shows both timers. During preparation,
players can walk, look around and join the arena, while the HUD shows the remaining
delay. Safe positions and viewing directions are retained when play starts.
The full round timer and survival scoring begin after preparation, together with
colour challenges or eruptions.

Use `set <arena> <setting> <number>`. Each game exposes only its applicable settings:

| Setting | Default | Valid values | Applies to |
| --- | --- | --- | --- |
| `minplayers` | 2 | 1–64, at most maxplayers | All |
| `maxplayers` | 8 | 1–64, at most configured spawns (Floor Shuffle, Volcano and Punch the Bat also allow no configured spawns) | All |
| `roundseconds` | 180 | 1–3600 | All |
| `countdown` | 30 | 1–60 seconds | All |
| `startdelay` | 15 | 0–60 seconds; 0 disables floor preparation | Floor Shuffle, Volcano |
| `lobbyshuffle` | 3 | 0–3600 seconds; 0 disables lobby animation | Floor Shuffle |
| `laps` | 3 | 1–100 | Races |
| `killtarget` | 20 | 1–1000 | Quake |
| `reloadticks` | 30 | 5–200 ticks | Quake |
| `targetcount` | 12 | 1–64 | Mob Shooter, Punch the Bat |

HUDs are configurable in each game's YAML. Additional placeholders are
`{arena:objective}`, `{arena:winner}` and `{player:progress}`, alongside framework
score, player count, status and time placeholders. Races also expose `{arena:laps}`
and Quake exposes `{arena:kill-target}`.

## Test checklist

- Solo: enable, join, then `start` before the automatic minimum is reached.
  Survival practice continues until you fall or time expires. Solo matches never
  grant winner rewards. Completed rounds show results for twenty seconds, restore
  the arena, and return occupants to their original state/location.
- With two players: check countdown placement (players stay in the lobby), spectators, the win condition, and
  leaving/disconnecting during countdown and play.
- In Floor Shuffle and Volcano, check the additional 15-second preparation on the
  floor, joining during preparation, custom delays, disabling the delay, and
  leaving or stopping before gameplay starts.
- Race through every gate, skip one, dismount, and cross the finish both before
  and after the required laps. Verify horse equality and minecart corner behavior.
- In Floor Shuffle, verify that falling inside the arena keeps players active and
  leaving its bounds eliminates them. Check matching colour announcements in chat,
  the HUD and the two-second title, including custom text formats.
- In Volcano, verify upward jets, varied eruption shapes, spreading/cooling lava,
  survival timers, falling elimination and hazard damage. Stop during an active
  flow and compare the floor and air above it with the original. In both survival
  games, compare the restored floor after normal completion and shutdown too.
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
