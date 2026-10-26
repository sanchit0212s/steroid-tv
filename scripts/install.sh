#!/bin/bash
# Installs the launcher, makes it the Home app, grants what its features need, then switches off
# the stock launchers (only after Steroid Home is confirmed as Home, so the TV is never left without one).
. "$(dirname "$0")/env.sh"
APK="$ROOT/launcher/out/SteroidHome.apk"; [ -f "$APK" ] || "$ROOT/scripts/build.sh"
tv install -r "$APK" | tail -1
tv shell cmd package set-home-activity tv.steroid.home/.Home --user 0
home=$(tv shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')
[ "$home" = "tv.steroid.home/.Home" ] || { echo "Home app not switched (got $home) — stock launchers left alone"; exit 1; }
# Radio visualizer reads the radio's own audio output; Android gates that behind RECORD_AUDIO.
tv shell pm grant tv.steroid.home android.permission.RECORD_AUDIO || true
# Little Office's 'needs you' notice is a small overlay over whatever is playing.
tv shell appops set tv.steroid.home SYSTEM_ALERT_WINDOW allow || true
R="$ROOT/backup/restore.sh"; mkdir -p "$ROOT/backup"
[ -f "$R" ] || printf '#!/bin/bash\n# Undo Steroid TV changes. Run from the repo root.\n. "$(dirname "$0")/../scripts/env.sh"\n' > "$R"
for p in $("$PY" -c "import json;print(' '.join(json.load(open('$ROOT/config/debloat.json'))['launchers']))"); do
  tv shell pm list packages | tr -d '\r' | grep -qx "package:$p" || continue
  tv shell pm disable-user --user 0 "$p" | tail -1; echo "tv shell pm enable $p" >> "$R"
done
grep -q set-home-activity "$R" || echo 'tv shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity --user 0' >> "$R"
chmod +x "$R"
tv shell input keyevent KEYCODE_HOME
echo "INSTALLED — Steroid Home is the Home app"
