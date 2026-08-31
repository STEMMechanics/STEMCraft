#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/plugins/Pl3xMap/web" >&2
  exit 2
fi

web_dir=${1%/}
backup_dir="$web_dir/.stemcraft-theme-backup"

if [ ! -f "$backup_dir/index.html" ]; then
  echo "No STEMCraft theme backup found in $web_dir" >&2
  exit 1
fi

cp "$backup_dir/index.html" "$web_dir/index.html"
rm -f "$web_dir/stemcraft-theme.css" "$web_dir/stemcraft-theme.js" "$web_dir/images/stemcraft-logo.png"
echo "STEMCraft theme removed from $web_dir"
