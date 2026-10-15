#!/bin/sh
set -eu
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 /path/to/plugins/Pl3xMap/web [public-endpoint]" >&2
  exit 2
fi
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 - "$1" "${2:-/stemcraft/named-regions}" "$script_dir/../../plugin/src/main/resources/pl3xmap/stemcraft-regions.js" <<'PY'
import hashlib, html, pathlib, re, shutil, sys
from urllib.parse import urlparse
web, endpoint, source = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
index = web / 'index.html'
if not index.is_file() or not (web / 'pl3xmap.js').is_file():
    sys.exit(f'Not a generated Pl3xMap web directory: {web}')
parsed = urlparse(endpoint)
if parsed.scheme not in ('', 'http', 'https') or (not parsed.scheme and not endpoint.startswith('/')):
    sys.exit('Use an absolute HTTP(S) endpoint or an origin-relative path beginning with /')
page = index.read_text(encoding='utf-8')
if not re.search(r'</body\s*>', page, flags=re.I):
    sys.exit('Could not find </body> in index.html')
backup = web / '.stemcraft-regions-backup'
backup.mkdir(exist_ok=True)
if not (backup / 'index.html').exists():
    shutil.copy2(index, backup / 'index.html')
shutil.copyfile(source, web / 'stemcraft-regions.js')
version = hashlib.sha256(source.read_bytes()).hexdigest()[:12]
page = re.sub(r'[ \t]*<script\b(?=[^>]*\s+id\s*=\s*(?:"stemcraft-regions"|\'stemcraft-regions\'))[^>]*>\s*</script>[ \t]*(?:\r?\n)?', '', page, flags=re.I)
tag = f'<script id="stemcraft-regions" src="stemcraft-regions.js?v={version}" data-endpoint="{html.escape(endpoint, quote=True)}"></script>'
body = next((match for match in re.finditer(r'<!--.*?-->|<script\b[^>]*>.*?</script\s*>|(?P<body></body\s*>)', page, flags=re.I | re.S) if match.group('body')), None)
if body is None:
    sys.exit('Could not find closing body tag outside comments/scripts')
position = body.start()
line_start = page.rfind('\n', 0, position) + 1
if not page[line_start:position].strip():
    position = line_start
newline = '\r\n' if '\r\n' in page else '\n'
page = page[:position] + f'  {tag}{newline}' + page[position:]
index.write_text(page, encoding='utf-8')
print(f'Named-region browser extension installed in {web}')
print(f'Route {endpoint} to STEMCraft /map/named-regions, then refresh the browser.')
print('STEMCraft installs this automatically by default; use auto-install: false for a manually managed copy.')
PY
