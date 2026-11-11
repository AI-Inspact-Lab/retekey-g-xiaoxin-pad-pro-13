#!/usr/bin/env bash
# Builds the modern (Android 9+) ReteKey G APK without Gradle, from the SDK's command-line tools:
# aapt2 → javac → d8 → zipalign → apksigner. Useful where Google's Maven repository (and so the
# Android Gradle Plugin) cannot be reached. The Gradle build remains the reference build.
#
# Tools (environment variables, with defaults for an installed Android SDK):
#   ANDROID_JAR    platforms/android-36/android.jar
#   AAPT2          build-tools/<v>/aapt2
#   D8_JAR         build-tools/<v>/lib/d8.jar         (anything containing com.android.tools.r8.D8)
#   ZIPALIGN       build-tools/<v>/zipalign
#   APKSIGNER_JAR  build-tools/<v>/lib/apksigner.jar
# Signing (required):
#   KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD   — or a keystore.properties at the root
# Output: release/ReteKey-G-<versionName>.apk
set -euo pipefail
# The helper scripts are Python; never leave bytecode caches in the repository.
export PYTHONDONTWRITEBYTECODE=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-36/android.jar}"
AAPT2="${AAPT2:-$BT/aapt2}"
D8_JAR="${D8_JAR:-$BT/lib/d8.jar}"
ZIPALIGN="${ZIPALIGN:-$BT/zipalign}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$BT/lib/apksigner.jar}"

if [ -f keystore.properties ] && [ -z "${KEYSTORE:-}" ]; then
  prop() { grep -m1 "^$1=" keystore.properties | cut -d= -f2-; }
  KEYSTORE="$ROOT/$(prop storeFile)"; KEYSTORE_PASSWORD="$(prop storePassword)"
  KEY_ALIAS="$(prop keyAlias)"; KEY_PASSWORD="$(prop keyPassword)"
fi
: "${KEYSTORE:?set KEYSTORE or provide keystore.properties}"

APP_ID="$(grep -m1 'applicationId = ' app/build.gradle | sed -E 's/.*"(.*)".*/\1/')"
VERSION_CODE="$(grep -m1 'versionCode = ' app/build.gradle | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
VERSION_NAME="$(grep -m1 'versionName = ' app/build.gradle | sed -E 's/.*"(.*)".*/\1/')"
NAMESPACE="$(grep -m1 'namespace = ' app/build.gradle | sed -E 's/.*"(.*)".*/\1/')"
MIN_SDK=28
TARGET_SDK=36

OUT="${OUT:-$ROOT/build-nogradle}"
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# The manifest names no package (Gradle injects the namespace); aapt2 needs one. And every class
# name written relative to it (".Foo") is spelled out in full against the namespace, as Gradle's
# manifest merger does: once the package is renamed to the application id, a relative name would
# point into com.retekey.g instead. aapt2 rewrites android:name itself but not
# android:targetActivity, and an activity-alias whose target is not in the manifest makes Android
# refuse the whole APK ("There was a problem parsing the package").
sed -E -e "0,/<manifest /s//<manifest package=\"$NAMESPACE\" /" \
  -e "s/(android:(name|targetActivity|parentActivityName|backupAgent|manageSpaceActivity)=\")\./\1$NAMESPACE./g" \
  app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
if grep -qE 'android:[A-Za-z]+="\.[A-Za-z]' "$OUT/AndroidManifest.xml"; then
  echo "relative class name left in the manifest:" >&2
  grep -nE 'android:[A-Za-z]+="\.[A-Za-z]' "$OUT/AndroidManifest.xml" >&2
  exit 1
fi

echo "• aapt2 compile"
# --legacy: what the Android Gradle Plugin passes; it lets "1% to 50%" in prose through.
"$AAPT2" compile --legacy --dir app/src/main/res -o "$OUT/res.zip"
echo "• aapt2 link ($APP_ID $VERSION_NAME/$VERSION_CODE)"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --rename-manifest-package "$APP_ID" --custom-package "$NAMESPACE" \
  --java "$OUT/gen" -A app/src/main/assets -0 idx --auto-add-overlay "$OUT/res.zip"

echo "• javac"
javac -nowarn -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT/classes" \
  $(find app/src/main/java "$OUT/gen" -name '*.java')

echo "• d8"
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "• package"
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" "$OUT/unsigned.apk" <<'PY'
import shutil, sys, zipfile
base, dex, out = sys.argv[1:]
shutil.copy(base, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY
if [ -x "$ZIPALIGN" ] && "$ZIPALIGN" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk" 2>/dev/null; then
  :
else
  # No working zipalign (missing, or its libc++ is): the same alignment in Python.
  python3 "$ROOT/scripts/zipalign.py" "$OUT/unsigned.apk" "$OUT/aligned.apk"
fi

echo "• sign"
mkdir -p release
FINAL="release/ReteKey-G-$VERSION_NAME.apk"
# The passwords go to apksigner through the environment, never on its command line, where any
# other process on the machine could read them from the process list.
export KEYSTORE_PASSWORD KEY_PASSWORD
# v1 + v2 + v3, not v3 alone: Android itself verifies v3 from 9.0 on, but device makers' installers
# and security scanners (and the Gradle default, which is v2) expect the older schemes too, and an
# APK carrying only v3 can be refused before Android ever sees it. --min-sdk-version 24 is what
# makes apksigner keep v2 alongside v3; the app's own minSdk (28) is unchanged.
java -jar "$APKSIGNER_JAR" sign --min-sdk-version 24 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --out "$FINAL" "$OUT/aligned.apk"
java -jar "$APKSIGNER_JAR" verify --print-certs "$FINAL" | head -3
# Refuse to hand over an APK that Android would refuse to parse (scripts/check-apk.py).
python3 "$ROOT/scripts/check-apk.py" "$FINAL" "$AAPT2"
echo "✔ $FINAL"
