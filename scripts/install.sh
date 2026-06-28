#!/usr/bin/env bash
#
# install.sh — build heic-jpg and link its launchers into a bin directory.
#
# Usage:
#   ./scripts/install.sh                 # link into ~/.local/bin
#   PREFIX=/usr/local ./scripts/install.sh   # link into /usr/local/bin
#   BINDIR=~/bin ./scripts/install.sh        # link into a specific dir
#   ./scripts/install.sh --copy          # copy launchers instead of symlinking
#
# Notes:
#   - Symlinks (default) keep the installed command pointing at this repo,
#     so a future `git pull` + rebuild is picked up automatically.
#   - Requires JDK 17+ and macOS `sips` at runtime.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MODE="link"
for arg in "$@"; do
  case "$arg" in
    --copy) MODE="copy" ;;
    --link) MODE="link" ;;
    -h|--help)
      sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

# Resolve target bin directory: BINDIR wins, else PREFIX/bin, else ~/.local/bin.
if [[ -n "${BINDIR:-}" ]]; then
  TARGET_BIN="$BINDIR"
elif [[ -n "${PREFIX:-}" ]]; then
  TARGET_BIN="$PREFIX/bin"
else
  TARGET_BIN="$HOME/.local/bin"
fi

# Preflight: require Java.
if ! command -v java >/dev/null 2>&1; then
  echo "Error: 'java' not found. Install JDK 17+ first (e.g. brew install openjdk@17)." >&2
  exit 1
fi

echo "Building heic-jpg..."
"$ROOT_DIR/scripts/build.sh" >/dev/null
echo "Build complete."

mkdir -p "$TARGET_BIN"

install_one() {
  local name="$1"
  local src="$ROOT_DIR/$name"
  local dest="$TARGET_BIN/$name"

  if [[ ! -x "$src" ]]; then
    echo "Error: launcher not found or not executable: $src" >&2
    exit 1
  fi

  rm -f "$dest"
  if [[ "$MODE" == "copy" ]]; then
    {
      echo '#!/usr/bin/env bash'
      echo "# heic-jpg-source: $src"
      printf 'exec %q "$@"\n' "$src"
    } > "$dest"
  else
    ln -s "$src" "$dest"
  fi
  chmod +x "$dest"
  echo "Installed ($MODE): $dest -> $src"
}

install_one "heic-jpg"
install_one "heic-jpg-ui"

echo
echo "Done. Installed into: $TARGET_BIN"
case ":$PATH:" in
  *":$TARGET_BIN:"*)
    echo "This directory is already on your PATH. Try: heic-jpg --help"
    ;;
  *)
    echo "NOTE: $TARGET_BIN is not on your PATH."
    echo "Add this line to your shell profile (~/.zshrc or ~/.bashrc):"
    echo "    export PATH=\"$TARGET_BIN:\$PATH\""
    ;;
esac
