# The Underhalls

The Underhalls is an experimental persistent maze dimension. Yellow end-stone
walls and occasional end-stone-brick details surround sandstone floors and
ceilings. Warm ochre froglight strips alternate with unlit halls. Dark oak doors
lead into enclosed, unlit exit chambers. No resource pack is required.

## Quick test

Build with Java 25 using `./gradlew check :plugin:jar`. Install the generated
STEMCraft jar on a Paper 26.2 test server and restart. The administrator command
requires `stemcraft.command.underhalls`.

1. In `survival`, aim at a level patch of solid ground with a clear 3x3 area
   and three blocks of headroom. Run `/underhalls entrance`.
2. Open the dark oak door in its mossy stone-brick frame and walk through it.
   The destination world, `survival_underhalls`, is created on demand.
3. You arrive just outside a dark oak exit door. Open it and step into the
   dark chamber to return to its fixed overworld location, or turn around to
   explore the maze. Exit destinations remain fixed; they need not be the
   overworld entrance you used. If you enter during the five-second teleport
   cooldown, wait inside and the exit retries automatically.
4. Break an overworld entrance's door, frame or supporting ground. That entrance is permanently
   retired, even if somebody rebuilds it. The maze exit rooms continue working.
5. Place torches, signs or blocks in the halls. Another player can remove them;
   neither player can break the generated walls, lights, floors or doors.
6. Restart and verify that routes, retired entrances and placed-block ownership
   remain intact.

The manual entrance command ignores grass and flowers when aiming, clears soft
vegetation in the doorway footprint, and accepts constructed solid floors. It
reports the specific obstruction, distance, protection or world-loading failure.
Automatic generation retains its larger 5x5 natural-ground check.

`/underhalls enter` takes an administrator to the maze beside an exit doorway for direct
inspection. `/underhalls status` reports active and retired entrance counts.
The generator is also available as `/world create test_underhalls underhalls seed:12345`.
Worlds using this generator receive maze protection and exit-room routing to the
configured source world, regardless of their name.

This branch is based directly on `version/26.2.x`. Inventory sharing follows the
server's GMI implementation. [Inventory PR #176](https://github.com/STEMMechanics/STEMCraft/pull/176)
adds automatic sharing between `survival` and `survival_underhalls`; without that
change, the base branch's GMI uses separate profiles for those worlds.

## Configuration

```yaml
world-generation:
  underhalls:
    enabled: true

underhalls:
  source-world: survival
  world: survival_underhalls
  entrances:
    enabled: true
    interval-seconds: 900
    chance: 0.2
    max-active: 16
    minimum-distance: 256
```

Generator availability takes effect after restart. Feature settings support
`/stemcraft reload`. Set `entrances.enabled: false` to stop automatic discovery
while retaining existing portals and maze protection. Administrator entrance
creation remains available. Keep the feature enabled to protect existing mazes.

Each interval has one chance to attempt an entrance. Up to 16 loaded chunks are
sampled in the source overworld. Sites must be at least 96 horizontal blocks
from every player, on clear natural ground, and surrounded by loaded chunks
without block entities or recorded player building/breaking. Build markers
persist in chunk data. These checks avoid replacing existing blocks, but do not
constitute a claim system or discover all historic player construction.
Protection integrations can veto `SurvivalPortalActivateEvent` with type
`stemcraft:underhalls`; the player is null for automatic creation. The frame
occupies X offsets -1..1, Y offsets 0..2 at the event origin's Z.

Entrances stay until destroyed. Both active and retired sites reserve their
minimum-distance radius, so a destroyed entrance cannot randomly reappear.
Entrances are never rebuilt automatically. Closed records do not count toward
the active cap. Integrity checks only inspect loaded chunks.

## Maze and routes

The version-1 generator produces deterministic 128x128-block tiles. Each tile
contains a seeded connected maze of sixteen by sixteen eight-block cells.
Fixed openings connect adjacent tiles, including negative coordinates. The
floor is normally Y=64, adjusted to the world's available height. Bedrock below
the floor and above the ceiling seals the maze. Vanilla terrain decoration,
structures and generation-time mob spawning are disabled.

Every tile contains an exit chamber at local X=66..70, Z=66..70. Its north door
is at X=68, Z=66. Walking to local Z=69 inside the chamber triggers departure.
Arrival squares are at local X=4, Z=4, away from the exit trigger.

An entrance at overworld X/Z maps to maze tile `floor(coordinate / 512)`. Each
exit room initially searches near overworld X/Z `tile * 512 + 8`, within three
blocks of that column, for a supported landing with two air blocks. Its first
successful destination is saved permanently. Later obstruction prevents travel;
it never silently redirects the room to spawn or another location. A room with
no safe initial landing remains unusable until there is one. Players can explore
a different room. World borders are checked before chunk preparation.

Entrances and exit rooms are independent one-way routes. Destroying an entrance
does not strand players by disabling an exit. World UUIDs prevent a portal from
silently targeting a replacement world after regeneration. Unloaded saved exit
worlds must be loaded by an administrator before using their routes.

## Protection and persistence

Successful player placements are recorded by world UUID and block coordinates
in SQLite's `underhalls_state` table. Material and player identity do not control
ownership: a player-placed end-stone block is removable by anyone. Generated
blocks cannot be broken, including in Creative. Placement is prevented in exit
chambers, arrival squares, and outside the maze's usable vertical space.

Pistons, flowing liquids, fire, growth and entity block changes cannot alter the
maze; explosions do not destroy maze blocks. Bucket emptying is disabled there.
Players can still place and remove ordinary building blocks, signs and lights.
These are gameplay event protections, not a restriction on administrator tools
or plugins that directly edit blocks.

SQLite writes persist placement ownership, entrance retirement and fixed exit
destinations. Grouping, records and terrain are separate from GMI's inventory
storage. Existing worlds must not be regenerated in place as a way to reset the
maze while retaining old placement records.

Automated tests cover seeded connectivity, negative-coordinate generation,
palette and exit geometry, storage round trips, permanently retired entrances,
fixed exit travel and obstruction, protection cancellation, player placement
provenance, explosions, and pistons. Visual lighting, door collision, natural
entrance frequency and multiplayer play need a live Paper client test.

## Recovery from a normal world created by the initial test build

The initial implementation called `loadWorld` for a missing world, which created
normal terrain before the custom generator could be selected. The corrected
implementation creates missing worlds explicitly with `underhalls`, and rejects
existing worlds with the wrong generator without modifying them.

After installing the corrected jar and restarting, select a fresh unused name:

```yaml
underhalls:
  source-world: survival
  world: survival_underhalls_v2
```

Run `/stemcraft reload`, then `/underhalls enter`. The previous world is retained;
changing its generator would not replace already-generated terrain.
