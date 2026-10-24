#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/plugins/Pl3xMap/web" >&2
  exit 2
fi
python3 - "$1" <<'PY'
import pathlib, re, sys
web = pathlib.Path(sys.argv[1])
index = web / 'index.html'
page = index.read_text(encoding='utf-8')
page = re.sub(r'[ \t]*<script\b(?=[^>]*\s+id\s*=\s*(?:"stemcraft-regions"|\'stemcraft-regions\'))[^>]*>\s*</script>[ \t]*(?:\r?\n)?', '', page, flags=re.I)
index.write_text(page, encoding='utf-8')
(web / 'stemcraft-regions.js').unlink(missing_ok=True)
print('Named-region browser extension removed; other theme changes are preserved.')
print('Set named-regions.map.biomes.auto-install: false to prevent installation at the next map startup/reload.')
PY
