#!/bin/bash
# Builds launcher/out/SteroidHome.apk from source + config/. No Gradle needed.
. "$(dirname "$0")/env.sh"
L="$ROOT/launcher"; JAR="$PLATFORM/android.jar"
[ -f "$JAR" ] || { echo "Android platform missing — run scripts/check-env.sh"; exit 1; }
mkdir -p "$L/assets/config"; cp "$ROOT"/config/{apps,stations,windows,features}.json "$L/assets/config/"
"$ROOT/scripts/fetch-previews.sh" >/dev/null
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
O="$L/out"; rm -rf "$O"; mkdir -p "$O/classes" "$O/dex"
# --release 8 (not -source/-target + bootclasspath): lambdas need the JDK's LambdaMetafactory at compile time
# argument files keep paths with spaces intact
find "$L/src" -name '*.java' | sed 's/.*/"&"/' > "$O/sources.txt"
javac --release 8 -classpath "$JAR" -d "$O/classes" @"$O/sources.txt" 2>&1 | grep -v -E "^Note:|warning" || true
find "$O/classes" -name '*.class' > "$O/classes.txt"
[ -s "$O/classes.txt" ] || { echo "COMPILE FAILED"; exit 1; }
"$BT/d8" --min-api 26 --lib "$JAR" --output "$O/dex" @"$O/classes.txt"
"$BT/aapt2" link -I "$JAR" --manifest "$L/AndroidManifest.xml" -A "$L/assets" -o "$O/base.apk"
(cd "$O" && zip -qj base.apk dex/classes.dex)
"$BT/zipalign" -f 4 "$O/base.apk" "$O/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out "$O/SteroidHome.apk" "$O/aligned.apk"
echo "BUILT $O/SteroidHome.apk"
