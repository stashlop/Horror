#!/usr/bin/env bash
# Builds a debug-signed Hollow Halls APK with the raw Android SDK tools.
# No Gradle, no Unity, no license server -- just aapt2 / javac / d8 / apksigner.
#
#   ANDROID_SDK_ROOT=/path/to/sdk ./build.sh
#
# Produces build/HollowHalls.apk
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" ]]; then
  echo "Set ANDROID_SDK_ROOT to your Android SDK location." >&2
  exit 1
fi

BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"
PLATFORM_VERSION="${PLATFORM_VERSION:-android-34}"
BT="$SDK/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK/platforms/$PLATFORM_VERSION/android.jar"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  [[ -e "$f" ]] || { echo "Missing $f -- check the SDK install." >&2; exit 1; }
done

OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> Compiling resources"
"$BT/aapt2" compile --dir res -o "$OUT/compiled/res.zip"

echo "==> Linking resources"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --java "$OUT/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  "$OUT/compiled/res.zip"

echo "==> Compiling Java"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
if ! javac \
  -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
  grep -v 'bootstrap class path' "$OUT/javac.log" >&2 || true
  echo "Java compilation failed." >&2
  exit 1
fi

echo "==> Dexing"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$ANDROID_JAR" --min-api 21 --output "$OUT/dex" @"$OUT/classes.txt"

echo "==> Packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -qu "../unsigned.apk" classes.dex)

echo "==> Aligning"
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$OUT/debug.keystore"
if [[ ! -f "$KS" ]]; then
  echo "==> Generating debug keystore"
  keytool -genkeypair -v \
    -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "==> Signing"
"$BT/apksigner" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/HollowHalls.apk" \
  "$OUT/aligned.apk"

"$BT/apksigner" verify --print-certs "$OUT/HollowHalls.apk" >/dev/null
echo
echo "APK ready: $HERE/$OUT/HollowHalls.apk"
ls -lh "$OUT/HollowHalls.apk"
