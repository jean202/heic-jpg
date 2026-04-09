#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_SRC_DIR="$ROOT_DIR/src/main/java"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
LIB_DIR="$BUILD_DIR/libs"
JAR_PATH="$LIB_DIR/heic-jpg-cli.jar"

if [[ ! -d "$MAIN_SRC_DIR" ]]; then
  echo "Main source directory not found: $MAIN_SRC_DIR" >&2
  exit 1
fi

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR" "$LIB_DIR"

sources=()
while IFS= read -r file; do
  sources+=("$file")
done < <(find "$MAIN_SRC_DIR" -name '*.java' | sort)

if [[ ${#sources[@]} -eq 0 ]]; then
  echo "No Java source files found under $MAIN_SRC_DIR" >&2
  exit 1
fi

javac --release 17 -d "$CLASSES_DIR" "${sources[@]}"
jar --create --file "$JAR_PATH" --main-class io.github.jean202.heicjpg.Main -C "$CLASSES_DIR" .

echo "Built $JAR_PATH"
