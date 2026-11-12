# AudioFool for Android — session handoff

Updated 2026-11-12. This file holds only what a new session needs to start: the latest build,
what is open, what waits on the user, the next steps and where things live. How the app works
is in `README.md`; every decision, newest first, and the lists of what to test on each device
are in `design/progress.md`; the spec is `design/android-spec.md`. The rest is in the files
listed under *Other docs* at the end: read each only when the work touches it.

## Starting the next session

- **Read:** `CLAUDE.md`, then this file. Before UI work, the "Decisions made for the build"
  section of `design/progress.md` from the top.
- **Latest build: 0.18.1** (versionCode 57), published 2026-11-12 as the pre-release
  [`v0.18.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.18.1) (APK in
  `dist/AudioFool-0.18.1.apk`, notes in `dist/notes-0.18.1.md`): **no Last.fm key in the APK**
  (the user's call, so the repo can be public again). Connect asks for the API key and shared
  secret while the phone has none saved; a phone that signed in before keeps its key.
  `tools/build.sh` fails an APK that holds the key. The emulator has it. Details:
  `design/progress.md`, its newest decision. Before it, 0.18.0 (56: Edit Tags and Lyrics,
  below), 0.17.2 (55), 0.17.1 (54), 0.17.0 (53), 0.16.0 (52), 0.15.1 (51), 0.15.0 (50) and
  0.14.0 (48); 49 went to the unpublished 0.14.1. Build by build: `design/progress.md` (Sessions).
- **Edit Tags** (0.18.0, spec 5.4): a cover-menu row opening the
  desktop's Edit Track Tags window for the song playing; the app's first writes to music files,
  made the desktop's safe way (in place when the tags fit, else on a copy checked and swapped
  in). Checked on the emulator in every format, the files read back with the desktop's own
  TagLibSharp; what only the phones can show is on both test lists. Details and my calls:
  `design/progress.md`, its third decision.
- **Lyrics** (0.18.0, spec 5.5): a cover-menu row opening the song
  playing's lyrics, the line being sung lit in the cover's colour and kept in view; from the
  song's `.lrc` file or tags, else LRCLIB (the user's calls). The screen follows the song. The
  emulator can't reach LRCLIB (Zscaler): the live lookup and Bluetooth timing are on the test
  lists. My calls (tap a line to seek, the screen kept on, plain file lyrics not upgraded from
  LRCLIB): `design/progress.md`, its second decision.
- **The repo is public again** (2026-11-12, the user's call): 0.18.1 went out without the key,
  the APK files of v0.9.0 to v0.18.0 (which carried it) were removed from GitHub on the user's
  yes (notes and tags stay, copies in `dist/`), and the user switched it. No secrets are in git
  history (checked that day). GitHub's releases list now reads without signing in.
- **Next: the update pop-up** (the user's idea, 2026-11-12, not started): on opening, if a newer
  release exists, "AudioFool 0.x.y is available. You have 0.x.y." with Update (download the APK
  in the app, hand it to Android's installer; first time, Android asks to allow "Install unknown
  apps") and Cancel. Proposed, not yet confirmed: check at most every few hours, and Cancel
  means not again for that version. With the repo public, no GitHub token is needed. It can
  only first show at the build after the one that adds it.
- **Next build:** 0.18.2 for fixes, 0.19.0 for new features (confirm the number with the user);
  **versionCode 58**. ThemeLab's menu pictures use a fixed "0.7.0", so a bump changes no picture.
- **Waiting on the user's phone tests:** the unticked items in *To test on the S24* and *To test
  on the M21* in `design/progress.md`, newest build first. Nothing from 0.9.0 to 0.18.1 has been
  reported yet (the last report, on 0.8.1: "looks its working on both devices"). When the user
  says how a build feels, tick the matching items in both lists and push, leaving open what the
  sentence doesn't cover. What to listen for and ask for each build is in the archived handoff:
  `design/progress-archive.md`, *HANDOFF.md as of 2026-11-11*, the bullet *First thing in the
  next chat*.
- **Open bugs:** none the user knows of. To watch for when reports come:
  - **Genius** has never reached the real genius.com on Android (the emulator can't get past
    this PC's Zscaler). Frequent "HTTP 403" or "didn't send" rows on the phones mean
    Cloudflare's check; what to do is the user's call, and never imitate a browser.
  - **The M21's whole-library queue** (Artists' shuffle, 27,115 songs) is untested there. If it's
    slow, ask what was slow (the tap, song changes, the restart) and measure with Debug's
    numbers before changing anything.
  - **A cover colour the user wouldn't pick:** ask which album and what they expected before
    changing the rule (core `CoverColor`; how to measure a change on the desktop's art cache is
    in `design/progress.md`).
  - Bit-Perfect items still open but not asked for (don't start them unasked): `docs/background.md`.
- **Publishing:** builds go out as GitHub pre-releases with the APK attached. No APK may carry
  AudioFool's Last.fm key (since 2026-11-12): upload only one `tools/build.sh` passed. The APKs
  of 0.8.1 to 0.18.0 carry it: never anywhere public.

## Build and release

- `tools/build.sh` sets the Java environment, runs all tests and the signed release build, and
  checks the signer and that no Last.fm key is in the APK. It never uploads.
- Publishing a build: the `/release` skill, `.claude/skills/release/SKILL.md`.
- Details (this PC's quirks, each Gradle task, the emulator and driving it, comparing with the
  mockups): `docs/build-and-emulator.md`.

## How the user works

- Sends a few UI requests at a time, often several in one message, and says when to release:
  build them all into one version, then show the release notes and ask before publishing.
  For 0.9.0: one request, built and pushed, then "Let's build it" (taking the proposed
  number), then "can we create a release for this".
- **Installs from GitHub releases, not over USB** ("installing via ADB isn't my preferred
  process", 0.9.0). To make that safe with the key in the APK, they made the repo private
  themselves (only the owner account can). Don't ask them to plug a phone in to install a build;
  USB is for debugging or when they ask.
- **Likes a new way to start music to behave like the existing one**: 0.9.0's shuffle button
  first left Artists on screen (my call); asked as a yes/no after it was built, they chose
  "Open Now Playing", as a tap on a song does. Build with the obvious call, list it as "my
  call, open to change", and offer the one real alternative in the wrap-up question.
- Answers design questions quickly when they come as short choices, with a recommended option
  first (AskUserQuestion works well, also for "plug the M21 in now" or "plug into the 3.5 mm
  jack" during a test). For a bug that can't be reproduced here, ask what they saw (when, how
  often, which phone) before guessing: that is what found 0.2.0's stuck cover.
- Keeps the M21 exactly as it is when asking for an S24 change: S24-only layout goes through the
  spare height, which is 0 on the M21; check the M21's pictures come out pixel-identical.
- Reports back in a sentence ("Seems to be fixed", "looks its working on both devices"); tick
  the matching items in `design/progress.md` and push, leaving open what the sentence doesn't
  cover.
- **Asks before a change to the spec or to stored data is fine; they expect it** (CLAUDE.md).
  The username-and-password sign-in was asked as a trade-off first (the key inside every APK),
  then built on "Yes, build it".
- **Copies example lines literally:** given `audiofool.lastfm.apiKey=<your API key>`, they kept
  the `<` `>` around the value. Write placeholders that can't be pasted wrongly (say "after the
  `=`, nothing else"), and check a pasted config line by its shape (masked, or a regex count),
  never by printing it. Asked first, then stripped the brackets with `sed` in place.
- Plugs a phone in when asked (for debugging; installs come from releases now), with debugging
  on. Its first connection needs "Allow USB debugging" on the phone (both done by 2026-11-05).
- Label any comparison picture (which phone, which theme) and leave a gap between its parts: an
  unlabelled stack of the S24's seekbar over the M21's read as "two waveforms".

## Where everything lives

| What | Path |
|---|---|
| Source | `C:\AudioFool-Android`, GitHub `mpaulrenlund/AudioFool-Android`, branch `main` |
| The desktop app (read-only) | `C:\MusicPlayer`; its `src/AudioFool.Core` is what gets ported |
| Ported Core (plain Kotlin, JVM tests) | `core/` — `library/`, `model/`, `theme/`, `art/`, `format/`, `playback/`, `analysis/` |
| The Android app | `app/` — `theme/`, `library/`, `settings/`, `storage/`, `art/` (thumbnails), `playback/`, `ui/` (screens, navigation) |
| The library | `library/LibraryRepository`: the cache first, then a background rescan; the scan reuses tags for files whose size and date haven't changed, and carries over every track of a folder it can't reach |
| Covers | `art/Thumbnails` (one per size: the lists' 64 dp, the album header's 72 dp) keeps small copies on disk and decoded ones in memory; `AudioFoolApp.prefillCovers` makes the list copies after each scan. Now Playing, the media session and Statistics' cover check read the full cover with `CoverArt.read` |
| Each part's code (playback, the screens, the cover colour, Now Playing's ideas, the USB DAC and driver, Last.fm, Listening History, Genius, Analyze, Edit Tags, Lyrics, Statistics, what is heard) | `docs/code-map.md` |
| **AudioFool's Last.fm key** | `~/.gradle/gradle.properties`: `audiofool.lastfm.apiKey=` and `audiofool.lastfm.secret=` (32 hex characters each, the user's own API account at last.fm/api/accounts). **Outside the repo**, put there by the user. Since 2026-11-12 no build reads them: they're kept only so `tools/build.sh` can check no APK carries them. Check they're set with `grep -c '^audiofool.lastfm.apiKey=..*'`, never by printing them |
| BASS 2.4 for Android (vendored unchanged) | `third_party/bass/`: `libs/arm64-v8a`, `libs/x86_64`, `java/com/un4seen/bass` (added to the app's source sets in `app/build.gradle.kts`), licence texts. Where each zip comes from: README |
| Launcher icon | `res/mipmap-anydpi/ic_launcher.xml`; the foreground PNG is the desktop's `icon_preview.png` scaled with ffmpeg (228 px in a 432 px canvas) |
| TagLib 2.3.2 (C++, vendored unchanged) | `third_party/taglib/`; the JNI bridge is `app/src/main/cpp/tagreader_jni.cpp` |
| Selawik font | `app/src/main/res/font/` (Regular, Semibold, Bold), licence in `third_party/selawik/` |
| Token files | `design/theme-tokens.json`, `theme-tokens.dark.json` (the desktop's, unchanged: `C:\MusicPlayer\design\theme-tokens.json` and `design\ps2-dark\theme-tokens.dark.json`, last copied 2026-11-04; copy both again when the desktop's change), `design/mobile-tokens.json`. The build copies all three, and `PlayStation-Logo-1994.png`, into the app's assets (`copyAppAssets`); never copy them by hand |
| ThemeLab pictures | `app/screenshots/` (recorded by Roborazzi; compare with `design/screens/`) |
| **Release keystore** | `C:\Users\MarcusRenlund\AndroidKeys\audiofool-release.jks`, passwords in `audiofool-release.properties` beside it. **Outside the repo.** Back up both files together |
| Signing pointer | `~/.gradle/gradle.properties`: `audiofool.signing=<path to the .properties>` |
| On the phone: library cache | `/data/data/com.mpaulrenlund.audiofool/no_backup/library.bin` (+ `.bak`) |
| On the phone: settings | SharedPreferences `settings` (theme, music folders, `bitPerfect`, `fiioJack`: the last jack a FiiO named, since 0.2.5) |
| On the phone: Last.fm | SharedPreferences `lastfm` (API key, secret, user, session: each AES-GCM encrypted with the Keystore key `audiofool.lastfm`; the key and secret as typed on the phone, or built in by 0.9.0 to 0.18.0); `no_backup/scrobbles.json` (+ `.bak`): the queue |
| On the phone: listening time | `no_backup/listening.json` (+ `.bak`): total ms and since when |
| On the phone: cover copies | `/data/data/com.mpaulrenlund.audiofool/no_backup/art/*.art` (ArtDiskCache; empty file = no cover) |
| On the phone: resume point | `no_backup/resume.json` (+ `.bak`): the queue, song, place, shuffle, repeat |
| On the phone: waveforms | `no_backup/waveforms-600/*.wave` (600 levels per file, named from path, size, date and columns; the old 96-level `waveforms/` is deleted at start since 0.2.4) |

Release key SHA-256: `1F:DE:FE:0A:0E:C5:F4:B6:63:61:39:B3:5B:F3:9D:B8:9E:EA:2C:68:9A:3A:A1:7A:BC:12:D8:CE:C5:BE:2C:21`.
Every APK the user gets is signed with it. A build signed with anything else won't
install over the last one, and uninstalling wipes the cache and settings.

---

## Other docs: read when...

- `docs/build-and-emulator.md`: before the first Gradle or emulator command (this PC's quirks,
  the build → test → verify → ship loop, the emulator, checking against the mockups, driving it).
- `docs/devices.md`: before anything about the S24, the M21, adb or a USB DAC (what the phones
  turned out to do, reaching them, the emulator's sample music).
- `docs/traps.md`: before changing playback, scanning, the output stage, ThemeLab tests, JNI or
  anything that ships an APK.
- `docs/background.md`: when a decision's history matters (the S24's own USB driver and its
  hearing-safety rules, Debug as the way results come back, the open Bit-Perfect items).
- `docs/code-map.md`: before working on a part's code (playback, the screens, the cover colour,
  Now Playing's ideas, the USB driver, Last.fm, Listening History, Genius, Analyze, Statistics).
- `design/progress.md`: before UI work (Decisions), and when recording a build (Sessions, the
  test lists).
- `design/android-spec.md`: before any UI work; the source of truth for every screen.
- `design/usb-bit-perfect.md`: before touching the USB driver.
- `design/progress-archive.md`: not read up front; search it before changing a feature built
  earlier. It holds this file's build-by-build narrative up to 0.17.0 and the numbered build
  routine (*HANDOFF.md as of 2026-11-11*).
