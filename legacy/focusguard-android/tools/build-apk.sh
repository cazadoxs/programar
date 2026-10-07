#!/usr/bin/env bash
# Manual build pipeline used to produce a signed, installable APK in
# environments that cannot reach Google's Maven repository (and therefore
# cannot run the Android Gradle Plugin). On a normal machine with Android
# Studio, just open the project and build/run as usual — this script exists
# for sandboxes/CI without that access.
#
# It reproduces, by hand, what Gradle + AGP would otherwise do:
#   aapt   (compile resources + manifest, generate R.java, package resources)
#   javac  (compile Java sources against a full Android API stub)
#   d8     (dex the compiled classes, via Google's R8 jar which bundles d8)
#   zipalign + apksigner (align and sign the final APK)
#
# Required tools on PATH: aapt, zipalign, apksigner, javac, java, keytool, zip.
#
# Required jars (not checked into the repo — see README "Building" section
# for where to fetch them):
#   ANDROID_STUB_JAR  — a full android.jar-equivalent stub with the real
#                        framework API surface, for javac. This project used
#                        org.robolectric:android-all (Maven Central).
#   R8_JAR            — Google's r8.jar (bundles d8), from the r8-releases
#                        GCS bucket or Maven Central's com.android.tools:r8.
#
# Usage:
#   ANDROID_STUB_JAR=/path/to/android-all-34.jar \
#   R8_JAR=/path/to/r8.jar \
#   tools/build-apk.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/app/src/main"
BUILD_DIR="$ROOT_DIR/build"
OUT_DIR="$ROOT_DIR/release"
KEYSTORE_DIR="$ROOT_DIR/keystore"

: "${ANDROID_STUB_JAR:?Set ANDROID_STUB_JAR to a full Android API stub jar (see header comment)}"
: "${R8_JAR:?Set R8_JAR to the Google r8.jar (see header comment)}"

echo "==> Cleaning build/"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/apk"
mkdir -p "$OUT_DIR"

echo "==> aapt: compiling resources + manifest, generating R.java"
aapt package -f -m \
  -J "$BUILD_DIR/gen" \
  -M "$APP_DIR/AndroidManifest.xml" \
  -S "$APP_DIR/res" \
  -I "$ANDROID_STUB_JAR"

echo "==> aapt: packaging resources.apk (manifest + resources.arsc + res/)"
aapt package -f \
  -M "$APP_DIR/AndroidManifest.xml" \
  -S "$APP_DIR/res" \
  -I "$ANDROID_STUB_JAR" \
  -F "$BUILD_DIR/apk/resources.apk"

echo "==> javac: compiling Java sources"
find "$APP_DIR/java" "$BUILD_DIR/gen" -name "*.java" > "$BUILD_DIR/sources.txt"
javac --release 8 -encoding UTF-8 \
  -cp "$ANDROID_STUB_JAR" \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/sources.txt"

echo "==> d8: dexing"
java -cp "$R8_JAR" com.android.tools.r8.D8 \
  --release --min-api 26 \
  --output "$BUILD_DIR/dex" \
  $(find "$BUILD_DIR/classes" -name "*.class")

echo "==> packaging APK"
cp "$BUILD_DIR/apk/resources.apk" "$BUILD_DIR/apk/app-unsigned.apk"
( cd "$BUILD_DIR/dex" && zip -q -X "$BUILD_DIR/apk/app-unsigned.apk" classes.dex )

echo "==> zipalign"
zipalign -f -p 4 "$BUILD_DIR/apk/app-unsigned.apk" "$BUILD_DIR/apk/app-aligned.apk"

echo "==> signing"
if [ ! -f "$KEYSTORE_DIR/release.keystore" ]; then
  echo "    no keystore found, generating a local debug-style one"
  mkdir -p "$KEYSTORE_DIR"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_DIR/release.keystore" \
    -alias focusguard \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass focusguard123 -keypass focusguard123 \
    -dname "CN=FocusGuard, OU=Dev, O=FocusGuard, L=Local, S=Local, C=ES"
fi

apksigner sign \
  --ks "$KEYSTORE_DIR/release.keystore" \
  --ks-pass pass:focusguard123 \
  --key-pass pass:focusguard123 \
  --ks-key-alias focusguard \
  --out "$OUT_DIR/FocusGuard.apk" \
  "$BUILD_DIR/apk/app-aligned.apk"

echo "==> done: $OUT_DIR/FocusGuard.apk"
apksigner verify --print-certs "$OUT_DIR/FocusGuard.apk"
