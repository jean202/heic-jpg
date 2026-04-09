#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_SRC_DIR="$ROOT_DIR/src/main/java"
TEST_SRC_DIR="$ROOT_DIR/src/test/java"
BUILD_DIR="$ROOT_DIR/build"
MAIN_CLASSES_DIR="$BUILD_DIR/classes"
TEST_CLASSES_DIR="$BUILD_DIR/test-classes"

"$ROOT_DIR/scripts/build.sh" >/dev/null

rm -rf "$TEST_CLASSES_DIR"
mkdir -p "$TEST_CLASSES_DIR"

test_sources=()
while IFS= read -r file; do
  test_sources+=("$file")
done < <(find "$TEST_SRC_DIR" -name '*.java' | sort)

if [[ ${#test_sources[@]} -eq 0 ]]; then
  echo "No test source files found under $TEST_SRC_DIR" >&2
  exit 1
fi

javac --release 17 -cp "$MAIN_CLASSES_DIR" -d "$TEST_CLASSES_DIR" "${test_sources[@]}"
java -cp "$MAIN_CLASSES_DIR:$TEST_CLASSES_DIR" io.github.jean202.heicjpg.HeicJpgCliTest
