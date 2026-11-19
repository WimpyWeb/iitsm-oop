#!/bin/sh
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
for f in architecture use-case class-domain class-state class-strategy class-observer-notifier er-diagram; do
  npx -y @mermaid-js/mermaid-cli --size 1600 -b white -i "$DIR/img/$f.mmd" -o "$DIR/img/$f.png"
done
echo "All 7 Mermaid diagrams rendered to docs/img/*.png"
