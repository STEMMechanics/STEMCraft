# Professions

The professions feature adds persistent, world-scoped progression for Mining, Herbalism, Farming, Fishing,
Cooking, Engineering, Melee Combat and Ranged Combat. Players begin at level 1. Level `n` requires
`100 × (n - 1)²` lifetime XP, up to level 100.

By default, activity is accepted in worlds matching the `survival*` glob. Creative and Spectator activity is
always excluded. Level-up messages are sent only to the player.

```yaml
professions:
  enabled: true
  worlds: ["survival*"]
  level-up-message: "&eYou have reached {skill} Level {level}"
```

Mining and Herbalism ignore player-placed sources. Farming requires mature crops. Engineering covers redstone
components, automation blocks, rails and minecarts and awards XP when they are crafted. Combat XP is awarded for
the killing blow against hostile mobs and is separated by direct player attacks and player-fired projectiles.

Profession XP is registered with player stats under `skill_<profession>_xp` keys.

Large lifetime-stat announcements are configured separately under `player_stats.milestones`. A broadcast happens
only when a player's lifetime total crosses the configured value.

## Player stats

Players can use `/stats` or `/stats <player>` to view recorded non-zero statistics. Profession XP is displayed as
both its derived level and lifetime XP.

Block placement and break totals are also recorded against every matching `player_stats.groups` entry. A group may
filter by game mode, world-name globs, or both. Omitting `worlds` matches every world. Groups are independent: when
an activity matches multiple groups, every matching group receives the increment. Milestones for `blocks_placed`
and `blocks_broken` are expanded across these group totals and `{group}` resolves to the configured group title.
