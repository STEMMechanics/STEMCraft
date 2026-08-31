# STEMCraft Pl3xMap theme

This is a non-destructive visual overlay for the generated Pl3xMap 26.2 web client. It changes branding and controls only; map tiles and marker data are untouched.

## Test it

Stop the Minecraft server, then run:

```sh
chmod +x tools/pl3xmap-theme/install.sh tools/pl3xmap-theme/uninstall.sh
tools/pl3xmap-theme/install.sh /path/to/plugins/Pl3xMap/web
```

Set this in Pl3xMap's configuration before starting the server again:

```yaml
settings:
  web-directory:
    read-only: true
```

Hard-refresh the browser after startup. The installer keeps the original `index.html` at `web/.stemcraft-theme-backup/index.html` and can be safely run again after editing the theme files.

## Remove it

```sh
tools/pl3xmap-theme/uninstall.sh /path/to/plugins/Pl3xMap/web
```

Set `read-only` back to `false` if Pl3xMap should manage or upgrade its web files again.

## Upgrade Pl3xMap

1. Run `uninstall.sh`.
2. Set `read-only: false`.
3. Upgrade and start Pl3xMap once so it refreshes the web client.
4. Remove the old `.stemcraft-theme-backup` directory.
5. Run `install.sh` against the refreshed web directory.
6. Restore `read-only: true` and restart.
