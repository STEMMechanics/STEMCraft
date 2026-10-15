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
page = re.sub(r'\s*<script\b[^>]*\bid=["\']stemcraft-regions["\'][^>]*>\s*</script>', '', page, flags=re.I)
tag = f'<script id="stemcraft-regions" src="stemcraft-regions.js?v={version}" data-endpoint="{html.escape(endpoint, quote=True)}"></script>'
page = re.sub(r'</body\s*>', lambda match: f'  {tag}\n{match.group(0)}', page, count=1, flags=re.I)
index.write_text(page, encoding='utf-8')
print(f'Named-region browser extension installed in {web}')
print(f'Route {endpoint} to STEMCraft /map/named-regions, then refresh the browser.')
print('Set Pl3xMap settings.web-directory.read-only: true before restarting.')
PY
