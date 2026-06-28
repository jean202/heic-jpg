#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/build/HEIC JPG.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"
JAR_NAME="heic-jpg-cli.jar"

"$ROOT_DIR/scripts/build.sh" >/dev/null

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"
cp "$ROOT_DIR/build/libs/$JAR_NAME" "$RESOURCES_DIR/$JAR_NAME"

cat > "$CONTENTS_DIR/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key>
  <string>heic-jpg-ui</string>
  <key>CFBundleIdentifier</key>
  <string>io.github.jean202.heicjpg</string>
  <key>CFBundleName</key>
  <string>HEIC JPG</string>
  <key>CFBundleDisplayName</key>
  <string>HEIC JPG</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>0.1.0</string>
  <key>LSMinimumSystemVersion</key>
  <string>12.0</string>
</dict>
</plist>
PLIST

cat > "$MACOS_DIR/heic-jpg-ui" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

CONTENTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$CONTENTS_DIR/Resources/heic-jpg-cli.jar"

exec java -Xdock:name="HEIC JPG" -cp "$JAR_PATH" io.github.jean202.heicjpg.HeicJpgUi "$@"
SH

chmod +x "$MACOS_DIR/heic-jpg-ui"

echo "Built $APP_DIR"
