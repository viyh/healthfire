#!/bin/sh
# Render design SVGs to PNG via headless Chrome.
# Usage: sh render.sh healthfire-a healthfire-a-icon-dark ...
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DIR="$(cd "$(dirname "$0")" && pwd)"
for name in "$@"; do
  "$CHROME" --headless --disable-gpu --hide-scrollbars \
    --default-background-color=00000000 \
    --screenshot="$DIR/$name.png" \
    --window-size=1024,1024 \
    "file://$DIR/$name.svg" >/dev/null 2>&1
  echo "rendered $name.png"
done
