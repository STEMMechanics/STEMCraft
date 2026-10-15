#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/plugins/Pl3xMap/web" >&2
  exit 2
fi
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 - "$1" "$script_dir/../../plugin/src/main/resources/pl3xmap/stemcraft-regions.js" <<'PY'
import hashlib, pathlib, re, shutil, sys
web, source = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
index = web / 'index.html'
if not index.is_file() or not (web / 'pl3xmap.js').is_file():
    sys.exit(f'Not a generated Pl3xMap web directory: {web}')
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
tag = f'<script id="stemcraft-regions" src="stemcraft-regions.js?v={version}"></script>'
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
print('Region files load from the existing Pl3xMap tiles directory. Refresh the browser.')
print('STEMCraft installs this automatically by default; use auto-install: false for a manually managed copy.')
PY
