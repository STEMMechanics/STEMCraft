# Release and documentation checks

Run `./gradlew check :api:jar :plugin:jar` before publishing code. This matches the core CI checks, including static analysis. For API documentation run `./gradlew :api:javadoc`; the wiki workflow also validates JavaDoc include directives. Link checks should cover the player handbook's Markdown navigation. Screenshot placeholders should describe the intended capture and must not masquerade as live screenshots.

## Document a feature at its boundaries

Public API JavaDoc should state thread affinity, ownership/lifetime, null/empty results, permission boundaries and failure behaviour. Callback APIs need to explain who invokes callbacks, on which thread, and how subscriptions are released. Immutable snapshots must not accidentally expose live mutable state. Examples must compile against the exposed API rather than plugin implementation classes.

Keep player workflows in `docs/pub`, operational/extension contracts in `docs/dev`, and detailed local implementation notes alongside the code. Update the public SUMMARY and developer sidebar for new pages. Distinguish bundled defaults from migrated live settings; adding a default does not always replace an existing value.

## Deployment checks beyond unit tests

- Minecraft clients: resource-pack glyphs, formatted signs, tab-name styling and private NPC visibility.
- Pl3xMap: startup ordering, reloads, late world registration, overlays and representative rendered tiles.
- Citizens: optional dependency absence, skin timeout/backoff, actor cleanup and player-only visibility.
- Permissions: non-op player access, explicit administrator grants and negative permissions.
- Storage: migration backups, player-reset preview/delete consistency, and SQLite compaction outside transactions.
- Tutorials: opt-in/skip paths, reconnects, world changes, command execution and quest hand-in exactly once.

A passing local build is not evidence of a live-server deployment. PRs should state which of these still require manual verification.
