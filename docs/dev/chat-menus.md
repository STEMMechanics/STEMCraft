# Chat menus

All paginated clickable chat menus should use `ChatMenuUtil` and the semantic roles in
`ChatMenuStyle`. Do not embed raw `NamedTextColor` values in a menu when one of these roles
describes the text or action.

## Standard palette

| Role | Minecraft colour | Use |
| --- | --- | --- |
| `TITLE` | `&b` aqua | Menu titles and separators |
| `KEY` | `&6` gold | Labels and keys in `Key: value` lines |
| `VALUE` | `&f` white | Normal values |
| `ACCENT` | `&b` aqua | Important values, links, or command names |
| `MUTED` | `&7` gray | Secondary descriptions and separators |
| `ACTION_VIEW` | `&b` aqua | View/info/open actions |
| `ACTION_EDIT` | `&b` aqua | Edit actions |
| `ACTION_CREATE` | `&a` green | Create actions |
| `ACTION_ADD` | `&a` green | Add, run, join, or other positive actions |
| `ACTION_DELETE` | `&c` red | Delete/remove actions |
| `ACTION_WARNING` | `&e` yellow | Pause, reload, or review actions |
| `ACTION_NAVIGATION` | `&6` gold | Back and navigation actions |
| `DISABLED` | `&8` dark gray | Disabled or unavailable content only |

The key and view/edit colors are intentional: keys use `&6`, while both view and edit actions
use readable `&b` aqua. Dark gray is not a general-purpose secondary text color.

## Layout rules

- Use an aqua title and put the primary `[Create]` action beside it when the menu supports creation.
- Keep actions on the line they affect: for example, `Schedule: ... [Edit]` and
  `Reminders: ... [View] [Add]`.
- Use short, consistent labels such as `[View]`, `[Edit]`, `[Add]`, `[Delete]`, and `[Back]`.
- Put row actions inside the `ChatMenuUtil.render` renderer so they remain part of the paginated
  menu. A small navigation toolbar may be the first rendered line when a menu has several related
  destinations.
- Use bracketed text buttons without decorative glyphs. State text may be colored, but it should
  not be replaced by status symbols.
- Add click and hover events only for player-facing output. Console output should remain readable
  plain text.
- Empty menus still render their heading, so title actions such as `[Create]` remain available.

## Example

```java
Component schedule = ChatMenuUtil.text("Schedule: ", ChatMenuStyle.KEY)
    .append(ChatMenuUtil.text(formatSchedule(group), ChatMenuStyle.VALUE))
    .append(Component.space())
    .append(ChatMenuUtil.actionButton(
        "[Edit]",
        ChatMenuStyle.ACTION_EDIT,
        ClickEvent.suggestCommand("/vote admin schedule " + group.id() + " now "),
        "Edit the voting schedule"));
```

For a new menu, prefer `ChatMenuUtil.text(...)` and `ChatMenuUtil.actionButton(...)` directly.
Existing feature-local helpers should delegate to those methods rather than introducing another
color/button palette.
