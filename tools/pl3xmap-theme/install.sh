#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/plugins/Pl3xMap/web" >&2
  exit 2
fi

web_dir=${1%/}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
backup_dir="$web_dir/.stemcraft-theme-backup"
index_file="$web_dir/index.html"

if [ ! -f "$index_file" ] || [ ! -f "$web_dir/pl3xmap.js" ]; then
  echo "Not a generated Pl3xMap web directory: $web_dir" >&2
  exit 1
fi

mkdir -p "$backup_dir" "$web_dir/images"
if [ ! -f "$backup_dir/index.html" ]; then
  cp "$index_file" "$backup_dir/index.html"
fi

# Always rebuild index.html from the untouched backup so reapplying is idempotent.
cp "$backup_dir/index.html" "$index_file"
cp "$script_dir/stemcraft-theme.css" "$web_dir/stemcraft-theme.css"
cp "$script_dir/stemcraft-theme.js" "$web_dir/stemcraft-theme.js"
cp "$repo_dir/docs/images/stemcraft-logo.png" "$web_dir/images/stemcraft-logo.png"

perl -0pi -e 's#</head>#    <link rel="stylesheet" href="stemcraft-theme.css" type="text/css"/>\n  </head>#' "$index_file"
perl -0pi -e 's#</body>#    <script src="stemcraft-theme.js"></script>\n  </body>#' "$index_file"
perl -0pi -e 's#<meta name="description" content="[^"]*"/>#<meta name="description" content="Explore the STEMCraft Minecraft world live"/>#' "$index_file"
perl -0pi -e 's{<meta name="theme-color" content="[^"]*"/>}{<meta name="theme-color" content="#f59e0b"/>}' "$index_file"

echo "STEMCraft theme installed in $web_dir"
echo "Set settings.web-directory.read-only: true in Pl3xMap config before restarting."
