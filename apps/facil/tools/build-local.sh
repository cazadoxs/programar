#!/usr/bin/env bash
# Compila un APK sin Gradle ni Google Maven (entornos con red restringida).
# Requisitos: aapt, zipalign, apksigner, java 17+, y en ~/tc: kc (kotlinc), lib/*.jar, r8lib.jar
# Uso: tools/build-local.sh   -> out/facil-debug.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="${TC:-$HOME/tc}"
APP="$ROOT/app/src/main"
B="$ROOT/out/build"
ANDROID_JAR="${ANDROID_JAR:-$(ls $TC/lib/android-all-*.jar | head -1)}"
rm -rf "$B"; mkdir -p "$B"/{gen,classes,dex,apk,res}
cp -r "$APP/res/." "$B/res/"
cat > "$B/res/values/zz_build.xml" <<X
<resources><string name="firebase_api_key">${FACIL_FIREBASE_API_KEY:-}</string><string name="firebase_db_url">${FACIL_FIREBASE_DB_URL:-}</string></resources>
X
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.facil.launcher">#' "$APP/AndroidManifest.xml" > "$B/AndroidManifest.xml"
aapt2 compile --dir "$B/res" -o "$B/res.zip"
aapt2 link -o "$B/apk/res.apk" -I "${RES_JAR:-$TC/android-all-13-robolectric-9030017.jar}" --manifest "$B/AndroidManifest.xml" --java "$B/gen" \
  --min-sdk-version 26 --target-sdk-version 33 --version-code 1 --version-name 1.0.0 --auto-add-overlay "$B/res.zip"
find "$APP/java" "$ROOT/tools/stubs" -name '*.kt' -o -name '*.java' | grep -v '^$' > "$B/src.txt"
find "$B/gen" -name '*.java' >> "$B/src.txt"
KT=$(grep '\.kt$' "$B/src.txt" | tr '\n' ' ')
JV=$(grep '\.java$' "$B/src.txt" | tr '\n' ' ')
CP="$ANDROID_JAR:$TC/lib/core-3.5.3.jar"
"$TC/kc" -no-stdlib -no-reflect -jvm-target 17 -cp "$CP:$TC/lib/kotlin-stdlib-2.1.21.jar" -d "$B/classes" $KT $JV 2>&1 | grep -v '^warning\|JAVA_TOOL' | tee "$B/kotlinc.log" || true
if grep -q 'error:' "$B/kotlinc.log"; then echo "FALLO de compilación Kotlin"; exit 1; fi
javac -nowarn --release 17 -cp "$CP:$B/classes:$TC/lib/kotlin-stdlib-2.1.21.jar" -d "$B/classes" $JV 2>&1 | grep -v JAVA_TOOL || true
java -cp "$TC/r8lib.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$B/dex" \
  $(find "$B/classes" -name '*.class') "$TC/lib/core-3.5.3.jar" "$TC/lib/kotlin-stdlib-2.1.21.jar" 2>&1 | grep -v JAVA_TOOL || true
cp "$B/apk/res.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -j "$B/unsigned.apk" classes*.dex)
zipalign -f 4 "$B/unsigned.apk" "$B/aligned.apk"
KS="${KEYSTORE:-$ROOT/out/debug.keystore}"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Facil Debug" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$ROOT/out/facil-debug.apk" "$B/aligned.apk"
echo "OK: $ROOT/out/facil-debug.apk"
