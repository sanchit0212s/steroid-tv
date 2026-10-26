# Steroid TV — playbook for Claude

You are setting up **Steroid TV** on the user's Android TV: a clean, ad-free custom launcher with
Retro TV, Radio, The World's Windows and (optionally) Little Office. This file is your script.
Follow the phases in order. Each phase links to a detailed guide in `docs/`; read it before you
start that phase. Everything here was built and debugged on a real TV — the fixes are already in
the code and scripts, and `docs/troubleshooting.md` explains every one, so you don't have to
rediscover them.

## How to work

- **The user only makes three decisions:** which apps to keep, whether to import their CLI AMP
  radio (if they have it), and whether to enable Little Office. Everything else is automated.
  Don't ask about things this playbook already decides.
- **Keep explanations short.** Show progress, not essays. One sentence before each phase.
- **Everything is reversible.** Packages are disabled or removed *for the user only*, never deleted
  from the system. `backup/restore.sh` is written before anything changes.
- **Never touch anything in `config/never-disable.txt`.** Playback, sign-in, Wi-Fi, Bluetooth,
  HDMI, sound, voice and casting depend on those packages.
- **Look before you press.** Before sending `input keyevent DPAD_CENTER` (OK), take a screenshot
  and confirm what is focused. Blind key presses can open apps and start playback on the user's
  accounts (it happened while building this). Press `KEYCODE_HOME` first to get a known starting
  point — Steroid Home always returns to the top with YouTube (first app) focused.
- **Screenshots:** `scripts/env.sh` gives you `tv` (adb aimed at the TV). Use
  `tv shell screencap -p /sdcard/s.png && tv pull /sdcard/s.png <local>` and read the image. If a
  screenshot comes out black (some video surfaces), use `tv shell screenrecord --time-limit 3` and
  extract a frame with ffmpeg.
- **Key names:** `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, `KEYCODE_HOME`, `KEYCODE_BACK` (not
  `KEYCODE_DOWN` — that's not a key and silently does nothing).
- **Ask before** anything outward-facing (accounts, purchases, publishing) — none of the phases
  need it.

## Phase 0 — Computer ready  → `docs/00-requirements.md`

Run `scripts/check-env.sh`. If anything is missing, install it as the guide says (Android
platform-tools, build-tools, an Android platform, JDK 17+, python3, ffmpeg). Re-run until `READY`.

## Phase 1 — Connect to the TV  → `docs/01-connect.md`

Walk the user through enabling Developer options + network/wireless debugging, get the TV's IP,
run `scripts/connect.sh <ip>`. They must accept the "Allow debugging?" prompt on the TV (tick
*Always allow*). Report the model, Android version and CPU ABI it prints — the launcher needs
Android 8+ (API 26); everything else adapts.

## Phase 2 — Choose the apps  → `docs/02-apps.md`

Run `scripts/inventory.sh`. It lists only apps a person can open — system plumbing is already
filtered out. Turn package names into friendly names with `config/known-apps.json` (infer the rest).
Ask the user **which apps to keep on the home screen**, showing only real apps (streaming, music,
games, tools they'd recognise), most likely first. Don't list launchers, settings, TV inputs or
updaters. Then write:
- `config/apps.json` — the kept apps in the user's preferred order, with accents from
  `known-apps.json` (or the app's brand colour);
- `backup/keep.txt` — the same package names, one per line.

## Phase 3 — Clean up  → `docs/03-debloat.md`

Run `scripts/debloat.sh backup/keep.txt`. It disables ad/tracking/telemetry packages for the TV's
vendor (from `config/debloat.json`) and every user-facing app that wasn't kept, writing
`backup/restore.sh` as it goes. If the TV isn't a Xiaomi, follow the guide to review that vendor's
system packages for ad/analytics services and add them to `config/debloat.json` first (with the
same care: never anything that plays video, signs in, or drives inputs).

## Phase 4 — Radio  → `docs/04-radio.md`

If the computer has CLI AMP (`command -v cliamp` or `~/.config/cliamp/playlists/*.toml`), ask
whether to bring its stations to the TV; if yes, run `scripts/import-cliamp.py`. Otherwise keep
the shipped `config/stations.json` (17 tested internet stations). Nothing on the computer changes.

## Phase 5 — The World's Windows  → `docs/05-worlds-windows.md`

Fully automated. Run `scripts/check-youtube.py`; if any video is `GONE`/`NO-EMBED`, replace it
following the curation rules in the guide (real footage, natural sound, visible motion, an hour or
more, embeddable). Previews are fetched during the build.

## Phase 6 — Retro TV  → `docs/06-retro-tv.md`

Fully automated, nothing to configure. Read the guide so you understand the architecture (hidden
MyRetroTVs page picks programmes; our own player shows them) in case anything needs fixing.

## Phase 7 — Little Office (optional)  → `docs/07-little-office.md`

Ask: *"Do you use Claude Code or Codex on this computer, and would you like a small status card on
the TV showing whether they're working or need you?"* Explain it reads local session files and
shares only project names, states and one sentence, on the home network. If yes (macOS only):
ask which projects to pin, run `little-office/install.sh "Project A" "Project B"`. It installs a
login item — that's the user's yes, but say so. If no: set `"littleOffice": false` in
`config/features.json`.

## Phase 8 — Build and install  → `docs/08-build-install.md`

`scripts/build.sh` then `scripts/install.sh`. The install sets Steroid Home as the Home app,
grants the radio visualizer and Little Office permissions, and only then disables the stock
launchers.

## Phase 9 — Verify  → `docs/09-verify.md`

Work through the checklist on the TV: home renders and scrolls, every kept app opens, a radio
station plays with a live spectrum, a window plays with sound, Retro TV tunes and pauses, Little
Office shows status (if enabled), and everything survives a reboot. Fix what fails using
`docs/troubleshooting.md`.

## Phase 10 — Hand over

Tell the user, briefly: what's on the home screen and how to use each part with the remote
(see `docs/remote-controls.md`), that `backup/restore.sh` undoes everything, and the honest
limits: ads inside streaming apps (YouTube, Prime etc.) can't be removed by a launcher, and
YouTube may show its own ads inside Retro TV and Windows videos.

## Reference

- `docs/remote-controls.md` — every screen's controls.
- `docs/troubleshooting.md` — every problem hit while building this, and the fix (already applied).
- `docs/architecture.md` — how the launcher is put together, for changes.
- `config/` — everything a user would customise.
