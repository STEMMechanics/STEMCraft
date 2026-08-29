# Entitlements and badges

Entitlements turn persistent player facts into permissions and visible badges. Definitions live under `entitlements` in `config.yml`.

## Lifecycle

- A definition without `when` is manual and is only changed by an administrator.
- A definition with `when`, but no condition `duration`, is permanently retained after it is first earned.
- A definition containing a duration such as `28d` is recalculated as a rolling entitlement and removed when its conditions are no longer met.
- Every configured condition must be true. An administrator grant overrides calculation until it is explicitly revoked.
- An administrator revocation is persisted and suppresses future automatic grants until an administrator grants it again.

Supported conditions include a singular `when.stat`, a `when.stats` list, `when.quest-completed`, `when.quests`, `when.quests-completed.at-least`, and `when.permissions`.

```yaml
entitlements:
  definitions:
    bridge-hot-streak:
      when:
        stat:
          key: minigame_bridge_wins
          at-least: 10
          duration: 28d
      grants:
        permissions: [stemcraft.bridge.hot-streak]
        badges: [bridge-hot-streak]
```

## Badge display

`{badge}` displays every applied badge in descending priority order. `{badge-3}` displays at most the three highest-priority badges. Badge placeholders work in the STEMCraft TAB name format. PlaceholderAPI exposes `%stemcraft_badge%` and `%stemcraft_badge:3%`.

`/badges [player]` displays only badges currently applied to that player and their descriptions. It does not reveal locked or potentially earnable badges.
Bundled badge displays use resource-pack glyph tokens such as `:trophy:` rather than Unicode emoji. Custom badge
displays may use any registered glyph token; legacy bundled symbols are translated at runtime without replacing
administrator-customized displays.

## Administration

The administrative command requires `stemcraft.entitlements.admin`.

```text
/entitlements list
/entitlements reload
/entitlements recalculate <player>
/entitlements grant <player> <entitlement>
/entitlements revoke <player> <entitlement>
/entitlements create <id>
/entitlements delete <id>
/entitlements set <id> <field> <value>
/entitlements badge grant <player> <badge>
/entitlements badge revoke <player> <badge>
/entitlements badge create <id>
/entitlements badge delete <id>
/entitlements badge set <id> <field> <value>
```

Convenience entitlement fields are `stat`, `at-least`, `duration`, `quest`, `quests-at-least`, `badges`, `permissions`, and `manual`. Use `manual true` to remove the complete `when` block, `-` to remove an individual field, or `none` to clear a badges/permissions list. Badge fields correspond directly to configuration keys: `display`, `description`, `permission`, and `priority`.

Generated permissions are placed in LuckPerms groups named `stemcraft-entitlement-<id>`. This keeps STEMCraft reconciliation separate from rank and permission nodes managed directly by administrators.
