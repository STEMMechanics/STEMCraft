# STEMCraft Citizens editor example

This separate plugin demonstrates an external STEMCraft API module built entirely from shared WebView components. It lists NPCs from Citizens' active registry, which is loaded from Citizens storage (by default, `plugins/Citizens/saves.yml`), groups them by entity type, and opens each NPC in a typed detail view.

The detail view uses a name field, entity type dropdown, and checkboxes for protection, Minecraft AI, flying, nameplate visibility, name holograms, and collision. It also lists the traits registered on that NPC. Saving updates the live Citizens NPC through the Citizens API and calls Citizens' save method so the changes persist to its configured storage. It does not edit YAML text or reload Citizens: the API changes apply to the live NPC immediately.

Build it with `./gradlew :examples:citizens-editor:jar` and install the resulting `STEMCraft-Citizens-Editor` jar alongside STEMCraft and Citizens. The editor is available to users with `citizens.admin`.
