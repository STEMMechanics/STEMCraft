# Formatted signs

Use formatting codes on normal or hanging signs, on either side:

- `&0`–`&f`: Minecraft's sixteen text colors (for example, `&b` is aqua).
- `&l`: bold; `&n`: underline; `&o`: italic; `&r`: reset.
- `:stembot:` and other registered glyph names: resource-pack icons.
- `&&`: a literal `&`, without applying a formatting code after it.

Codes are case-insensitive. A color code clears earlier bold, underline and italic
formatting. Each sign line starts with its own default formatting.

| Input | Display |
| --- | --- |
| `Hello d&m` | Literal `Hello d&m`; `&m` is unsupported |
| `Hello &bMan` | `Hello ` followed by aqua `Man` |
| `Hello d&b Man` | `Hello d` followed by aqua ` Man` |
| `Hello d&&b Man` | Literal `Hello d&b Man` |
| `&b:stembot: Welcome` | The STEMBot glyph and welcome text in aqua, when that glyph is registered |

Unknown glyph names stay as text. Glyphs require the server resource pack to display
correctly. MiniMessage tags such as `<red>` are literal text on signs.
Formatting is applied when a player submits a sign edit; existing signs are not
rewritten automatically. To retain a literal `&b` when editing a sign again, enter
`&&b` again. Server administrators can disable this feature with
`formatted-signs.enabled: false`.
