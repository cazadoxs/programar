#!/usr/bin/env bash
# Ejecuta los tests unitarios de la lógica (paquete core) sin Gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; TC="${TC:-$HOME/tc}"; O="$ROOT/out/test"
rm -rf "$O"; mkdir -p "$O"
CP="$TC/lib/kotlin-stdlib-2.1.21.jar:$TC/tlib/junit-4.13.2.jar:$TC/tlib/hamcrest-core-1.3.jar:$TC/tlib/json-20240303.jar"
"$TC/kc" -no-reflect -jvm-target 17 -cp "$CP" -d "$O" "$ROOT"/app/src/main/java/app/facil/launcher/core/*.kt "$ROOT"/app/src/test/java/app/facil/launcher/*.kt 2>&1 | grep -v 'JAVA_TOOL\|^warning' || true
java -cp "$O:$CP" org.junit.runner.JUnitCore app.facil.launcher.CoreTest 2>&1 | grep -v JAVA_TOOL
