# Player and staff permissions

STEMCraft checks command access before executing handlers and filters tab completions
using the same policy. Player, other-player and administrative actions have distinct
permissions. Most command permissions default to operators; the recommended player permissions
below must be granted explicitly. Formatted signs default to false, including for operators.

## Recommended standard-player permissions

Grant the following individual permissions to your normal player group, plus the eight
minigame permissions in the next section. There are no built-in player or minigame bundles;
manage your preferred grouping in LuckPerms.

| Permission | Access |
| --- | --- |
| `stemcraft.command.afk` | Toggle your own AFK status (granted by default) |
| `stemcraft.command.hub` | Return yourself to the hub |
| `stemcraft.command.survival` | Use the configured fixed Survival shortcut |
| `stemcraft.command.creative` | Use the configured fixed Creative shortcut |
| `stemcraft.command.coord` | Toggle your coordinate action bar |
| `stemcraft.command.coordbar` | Toggle your coordinate boss bar |
| `stemcraft.minigame.skyblock.reset` | Reset your own island, with confirmation |
| `stemcraft.mailbox.send` | Send mail without queue administration |
| `stemcraft.namedregion.read` | Info, list, find and nearby named regions |
| `stemcraft.noticeboard.read` | List notices and your own posts |
| `stemcraft.noticeboard.post` | Post a notice; edit/remove your own posts |
| `stemcraft.sign.format` | Format sign text and use registered glyphs; requires an explicit grant; defaults false |

If you previously granted `stemcraft.player` or `stemcraft.minigame.play` while testing
this branch, replace those grants with the individual nodes listed here; those bundles
are no longer provided.

The normal quest journal, STEMBot/help, reports, selection-preview toggles and reading
physical noticeboards do not need new permissions. Voting defaults to open access but
still respects a configured `voting.permission`. Configured menus/actions and external
plugins may have additional permission checks.

## All minigames

Grant each of these to let standard players play all eight minigames:

| Game | Standard-player permission |
| --- | --- |
| BedWars | `stemcraft.minigame.bedwars.play` |
| Bridge | `stemcraft.minigame.bridge.play` |
| BoatRace | `stemcraft.minigame.boatrace.play` |
| Minefield | `stemcraft.minigame.minefield.play` |
| Nightfall | `stemcraft.minigame.nightfall.play` |
| Parkour | `stemcraft.minigame.parkour.play` |
| TNT Run | `stemcraft.minigame.tntrun.play` |
| SkyBlock | `stemcraft.minigame.skyblock.play` |

Each `.play` allows list/info, joining yourself, leaving yourself and spectating yourself
where the game supports it. Parkour also allows restarting your own run. SkyBlock join
creates/resumes your own island. Arena status, capacity and game rules still apply.

For each game, `stemcraft.minigame.<game>.others` permits targeting another player with
player actions and also requires `.play`. It does not authorize arena editing, joinall,
or start/stop/reload. `stemcraft.minigame.<game>.admin` permits the administrative actions
and player actions, including acting on other players.

`stemcraft.minigame.skyblock.reset` additionally requires `.play` and ownership of the
island. `/skyblock reset <owner-or-arena-id> confirm` is required to actually delete an
island. Resetting someone else's island requires admin access (or the explicitly retained
legacy `stemcraft.command.skyblock.others` grant). New minigame `.others` alone does not
authorize deleting another island. An administrator must confirm resets too.

## Other player and administrative splits

| Player access | Additional privileged access |
| --- | --- |
| `stemcraft.mailbox.send` | `stemcraft.mailbox.admin` for all queue inspection/release/hold/delete/item edits |
| `stemcraft.namedregion.read` | `stemcraft.namedregion.teleport` for travel; `stemcraft.namedregion.admin` for all management |
| `stemcraft.noticeboard.read` and `.post` | `stemcraft.noticeboard.admin` for other users' posts, arbitrary expiry and board management |
| `stemcraft.book` for get/list/show self | `stemcraft.book.others` for showing another player a book; `stemcraft.book.edit` for authoring/editing |
| `stemcraft.command.back` | `stemcraft.command.back.others` |
| `stemcraft.command.spawn` for current-world self travel | `.spawn.others` to target another player; `.spawn.worlds` for any world or `.spawn.world.<world-name>` for a specific world |
| `stemcraft.command.ptime` | `stemcraft.command.ptime.others` |
| `stemcraft.command.pweather` | `stemcraft.command.pweather.others` |
| `stemcraft.command.speed` | `stemcraft.command.speed.others` |
| `stemcraft.command.clearsel` | `stemcraft.command.clearsel.others` |
| `stemcraft.command.enderchest` for your own chest | `stemcraft.command.enderchest.others` to open another player's live chest for yourself |
| `stemcraft.command.tpsbar` | `stemcraft.command.tpsbar.others` (legacy `.other` still accepted) |

Personal commands retain their base permission requirement when `.others` is granted.
Explicitly naming yourself is permitted by the newly introduced access policies. Console
can still execute fixed server-side actions. `/enderchest` now defaults to the caller;
console must supply a target and opens that target's chest for them.

Notice-board ownership checks also run when a dialog is submitted. Non-admin players
remain limited to one active notice; they cannot assign unlimited expiry or edit another
author's post. Reading a physical display remains unrestricted.

## Survival and Creative shortcuts

The bundled aliases run:

```yaml
custom-commands:
  survival:
    permission: stemcraft.command.survival
    command: survival
    run:
      - "server:tpworld survival {player}"
  creative:
    permission: stemcraft.command.creative
    command: creative
    run:
      - "server:tpworld creative {player}"
```

Players need the shortcut permission, not `stemcraft.command.tpworld`. The destination
is fixed and the target is the invoking player's name. The original unchanged shortcut
(`survival` with the sole action `tpworld survival`) is migrated and saved on enable or
reload. Customized aliases/actions are preserved; review them separately if they still
run a teleport as the player. Other teleport/world administration remains staff-only.

## Optional grants

These are optional additions to the recommended player permissions:

- `stemcraft.command.warp`: every configured warp; there is no per-warp gate.
- `stemcraft.command.back`, `.spawn`, `.ptime`, `.pweather`, `.tpsbar`: decide travel and
  personal-display policy before granting globally.
- `stemcraft.book`: access to all configured books.
- `stemcraft.command.clearsel`, `.speed`, `.nightvision`, `.fly`, `.workbench`, `.repair`,
  `.enderchest`, `.clearinv`: offer in the intended worlds/groups, particularly creative.
- `stemcraft.allow_throwing_potions`, `stemcraft.allow_spawn_eggs` and individual
  `stemcraft.creative.override.*` nodes: explicit gameplay overrides; not blanket player
  privileges.
- `stemcraft.qol.*` and badge/entitlement nodes: preserve progression-based unlocks unless
  intentionally replacing that progression with group grants.

## Migration and staff groups

Remove the old broad minigame nodes from standard players. Existing
`stemcraft.command.<game>` permissions remain **administrative compatibility grants**.
Likewise, `stemcraft.mailbox` and `stemcraft.command.namedregion` retain their former
administrative meaning. Do not grant them merely to allow playing, sending mail or
reading region information. Avoid `stemcraft.*` and `stemcraft.command.*` in normal groups.

Non-operator staff who previously relied on broad personal-command access need the
new matching `.others` permissions and, for `/spawn`, appropriate world access. Operators
receive undeclared permission nodes through Bukkit's operator default. Existing quest admin
(`stemcraft.quest.admin`), voting admin (`stemcraft.command.vote.admin`) and menu editor
(`stemcraft.imenu.edit`) checks remain separate from normal player access.

To enable formatted signs for a group/player, grant `stemcraft.sign.format` explicitly.
Without a grant (or with an explicit denial), formatting codes and glyph names stay literal.
The global `formatted-signs.enabled` flag still controls the feature as a whole.

Permissions are checked directly by the owning code. There are no permission YAML files
or declarations in `plugin.yml`. FormattedSigns registers its own single permission in
code with a false default, and removes that registration when disabled.
The plugin does not rewrite your live LuckPerms groups.

### AFK kick exemption

`stemcraft.afk.kick-exempt` exempts a player from STEMCraft's AFK kick timer. It defaults to false, including for operators; grant it explicitly only where needed. It does not prevent AFK status or announcements and does not bypass Paper's separate idle timeout.
