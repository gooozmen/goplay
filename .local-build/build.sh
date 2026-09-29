#!/usr/bin/env bash
# Local compile + unit tests without Maven. Usage: .local-build/build.sh [test-module ...]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.local-build/out"
export JAVA_HOME="${JAVA_HOME:-$HOME/java/jdk-17.0.13+11}"
export PATH="$JAVA_HOME/bin:$PATH"

JARS="$ROOT/.local-build/jars"
CP="$JARS/commons-compress-1.26.0.jar:$JARS/commons-io-2.16.1.jar:$JARS/commons-lang3-3.17.0.jar"
JUNIT="$JARS/junit-platform-console-standalone-1.9.2.jar"

rm -rf "$OUT"
mkdir -p "$OUT/main" "$OUT/test"

MAIN_SRC=(
	common/TuxGuitar-lib/src/main/java
	common/TuxGuitar-gm-utils/src
	common/TuxGuitar-gtp/src
	common/TuxGuitar-gpx/src
	common/TuxGuitar-musicxml-reader/src
)
TEST_SRC=(
	common/TuxGuitar-lib/src/test/java
	common/TuxGuitar-musicxml-reader/test/java
)

list_sources() {
	for d in "$@"; do
		if [ -d "$ROOT/$d" ]; then find "$ROOT/$d" -name '*.java'; fi
	done
}

list_sources "${MAIN_SRC[@]}" > "$OUT/main.txt"
javac -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT/main" @"$OUT/main.txt"

for d in common/TuxGuitar-musicxml-reader/share; do
	if [ -d "$ROOT/$d" ]; then cp -r "$ROOT/$d/." "$OUT/main/"; fi
done

list_sources "${TEST_SRC[@]}" > "$OUT/test.txt"
if [ -s "$OUT/test.txt" ]; then
	javac -nowarn -encoding UTF-8 -cp "$CP:$OUT/main:$JUNIT" -d "$OUT/test" @"$OUT/test.txt"
	for d in common/TuxGuitar-lib/src/test/resources common/TuxGuitar-musicxml-reader/test/resources; do
		if [ -d "$ROOT/$d" ]; then cp -r "$ROOT/$d/." "$OUT/test/"; fi
	done
	SELECT=()
	if [ "$#" -gt 0 ]; then
		for p in "$@"; do SELECT+=(--select-package "$p"); done
	else
		SELECT=(--scan-classpath "$OUT/test")
	fi
	(cd "$ROOT/common/TuxGuitar-lib" && java -jar "$JUNIT" -cp "$CP:$OUT/main:$OUT/test" "${SELECT[@]}" --disable-banner --details=summary)
fi
