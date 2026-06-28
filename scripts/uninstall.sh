#!/usr/bin/env bash
#
# uninstall.sh — remove heic-jpg launchers installed by install.sh.
#
# Usage:
#   ./scripts/uninstall.sh                    # remove from ~/.local/bin
#   PREFIX=/usr/local ./scripts/uninstall.sh  # remove from /usr/local/bin
#   BINDIR=~/bin ./scripts/uninstall.sh       # remove from a specific dir
#
# Only removes links/copies that point at this repository's launchers,
# so it won't touch an unrelated command that happens to share the name.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${BINDIR:-}" ]]; then
  TARGET_BIN="$BINDIR"
elif [[ -n "${PREFIX:-}" ]]; then
  TARGET_BIN="$PREFIX/bin"
else
  TARGET_BIN="$HOME/.local/bin"
fi

remove_one() {
  local name="$1"
  local dest="$TARGET_BIN/$name"
  local src="$ROOT_DIR/$name"

  if [[ ! -e "$dest" && ! -L "$dest" ]]; then
    echo "Skip (not found): $dest"
    return
  fi

  # Only remove a symlink or generated copy wrapper that points at this repo.
  if [[ -L "$dest" ]]; then
    local resolved
    resolved="$(readlink "$dest")"
    if [[ "$resolved" != "$src" ]]; then
      echo "Skip (links elsewhere): $dest -> $resolved"
      return
    fi
  elif ! grep -Fqx "# heic-jpg-source: $src" "$dest"; then
    echo "Skip (unrelated file): $dest"
    return
  fi

  rm -f "$dest"
  echo "Removed: $dest"
}

remove_one "heic-jpg"
remove_one "heic-jpg-ui"

echo "Done."
