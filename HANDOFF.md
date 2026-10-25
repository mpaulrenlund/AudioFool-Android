# AudioFool for Android — session handoff

Updated 2026-10-24, end of session 3 (playback). Read this with `README.md` (how the app
works) and `design/progress.md` (every decision, and the lists of what to test on each
device). The spec is `design/android-spec.md`.

## Starting the next session

- **Next is session 4, Bit-Perfect, DSD over DoP, and an M21 test build**
  (`design/session-prompts.md`). It starts with research the user wants to see first (the
  Android 14+ bit-perfect mixer on a USB DAC, FiiO's SRC bypass on the M21).
- Read, in order: `CLAUDE.md`, this file, `design/android-spec.md` (§6), and in
  `design/progress.md` the Decisions sections and the session 3 notes.
- **Where session 4 plugs in:**
  - `playback/OutputStage.kt` opens the `AudioTrack` (`openOutput`, `build`): that is
    where the route, `setPreferredMixerAttributes` and DoP go. It already reopens the
    output when a song asks for another format, after the last one is heard out (that's
    the place to re-apply the mixer attributes at a new rate), and after a lost output
    (`reopenAfterLoss`, carrying on from `ReopenPoint`).
  - `playback/Decoder.kt` picks the encoding from the bit depth (`encodingFor`) and opens
    DSD as PCM; DoP needs `BASS_DSD_RAW` and the desktop's DoP packing.
  - `playback/Player.kt` `readoutFor` builds the readout with `OutputMode.Shared`; the
    route goes in front of it (spec 6.3).
  - `ui/NowPlayingScreen.kt` `BitPerfectButton(on, onClick, greyed)` draws all three
    states; `NowPlayingActions.toggleBitPerfect` is null today. Settings' row reads
    `nowPlaying.readout` and still shows the off chip.
  - Statistics > Playback is where the route, readout and mixer attributes go for the user
    to report.
- **Device reports still open** (don't block on them): the v0.0.2 and v0.0.4 items on both
  lists in `design/progress.md`. Done so far: the S24's install, counts, first scan and
  cold start (v0.0.1), and the M21's first scan (v0.0.3).
- **Releases:** `v0.0.1` to `v0.0.3` are out, and session 3's `0.0.4` (versionCode 4) if
  the user approved it (see *Where things stand*). The next is `0.0.5`, versionCode 5.
  Confirm the tag and notes with the user before `gh release create`.
- **The emulator** holds the sample music (`/sdcard/Music`, with `Long/`: 200 artists × 2
  albums of silent MP3s, `Gapless Live/`: a sweep cut into three songs; and a copy of the
  sample set on the SD card, `/storage/0000-0000/Music`) and the session 3 release build.
  `dist/` (ignored by git) holds the released APKs.

---

## Where everything lives

| What | Path |
|---|---|
| Source | `C:\AudioFool-Android`, GitHub `mpaulrenlund/AudioFool-Android`, branch `main` |
| The desktop app (read-only) | `C:\MusicPlayer`; its `src/AudioFool.Core` is what gets ported |
| Ported Core (plain Kotlin, JVM tests) | `core/` — `library/`, `model/`, `theme/`, `art/`, `format/` |
| The Android app | `app/` — `theme/`, `library/`, `settings/`, `storage/`, `art/` (thumbnails), `playback/`, `ui/` (screens, navigation) |
| Playback | `app/.../playback/`: `BassRuntime` (start-up, add-ons), `Decoder` (one BASS stream per song), `OutputStage` (the `AudioTrack` thread: gapless, what's heard), `Player` (queue, order, focus, unplug, resume), `NowPlaying` (what the screens read), `SystemControls` (media session), `PlaybackService` (notification, foreground), `WaveformReader`. Ported logic in `core/.../playback/` and `core/.../analysis/` |
| BASS 2.4 for Android (vendored unchanged) | `third_party/bass/`: `libs/arm64-v8a`, `libs/x86_64`, `java/com/un4seen/bass` (added to the app's source sets in `app/build.gradle.kts`), licence texts. Where each zip comes from: README |
| Screens | `ui/AppRoot.kt` wires them; `Navigation.kt` (back stack, predictive back), `LibraryScreens.kt` (Artists, Albums, Album), `SearchScreen.kt`, `SettingsScreen.kt`, `StatisticsScreen.kt` (debug numbers), `MiniPlayer.kt`, `LogoMenu.kt`, `Parts.kt` (the shared parts) |
| Launcher icon | `res/mipmap-anydpi/ic_launcher.xml`; the foreground PNG is the desktop's `icon_preview.png` scaled with ffmpeg (228 px in a 432 px canvas) |
| TagLib 2.3.2 (C++, vendored unchanged) | `third_party/taglib/`; the JNI bridge is `app/src/main/cpp/tagreader_jni.cpp` |
| Selawik font | `app/src/main/res/font/` (Regular, Semibold, Bold), licence in `third_party/selawik/` |
| Token files | `design/theme-tokens.json`, `theme-tokens.dark.json` (the desktop's, unchanged), `design/mobile-tokens.json`. The build copies all three, and `PlayStation-Logo-1994.png`, into the app's assets (`copyAppAssets`); never copy them by hand |
| ThemeLab pictures | `app/screenshots/` (recorded by Roborazzi; compare with `design/screens/`) |
| **Release keystore** | `C:\Users\MarcusRenlund\AndroidKeys\audiofool-release.jks`, passwords in `audiofool-release.properties` beside it. **Outside the repo.** Back up both files together |
| Signing pointer | `~/.gradle/gradle.properties`: `audiofool.signing=<path to the .properties>` |
| On the phone: library cache | `/data/data/com.mpaulrenlund.audiofool/no_backup/library.bin` (+ `.bak`) |
| On the phone: settings | SharedPreferences `settings` (theme, music folders) |
| On the phone: cover copies | `/data/data/com.mpaulrenlund.audiofool/no_backup/art/*.art` (ArtDiskCache; empty file = no cover) |
| On the phone: resume point | `no_backup/resume.json` (+ `.bak`): the queue, song, place, shuffle, repeat |
| On the phone: waveforms | `no_backup/waveforms/*.wave` (96 levels per file, named from path, size and date) |

Release key SHA-256: `1F:DE:FE:0A:0E:C5:F4:B6:63:61:39:B3:5B:F3:9D:B8:9E:EA:2C:68:9A:3A:A1:7A:BC:12:D8:CE:C5:BE:2C:21`.
Every APK the user gets is signed with it. A build signed with anything else won't
install over the last one, and uninstalling wipes the cache and settings.

---

## This PC's quirks (read before the first Gradle run)

1. **Java**: use Android Studio's JDK, `C:\Program Files\Android\Android Studio\jbr` (JDK 25).
   There is no other `java` on the PATH.
2. **Zscaler intercepts TLS**, so Java doesn't trust Maven or Google's repositories out of
   the box. `~/.gradle/truststore-with-zscaler.jks` is the JDK's own trust store plus the
   Zscaler root (exported from Windows' store). `~/.gradle/gradle.properties` points the
   Gradle daemon at it; the wrapper download needs it too, through `JAVA_TOOL_OPTIONS`.
3. **Loopback**: Gradle fails with "Unable to establish loopback connection" unless
   `-Djdk.net.unixdomain.tmpdir` points somewhere short (the default temp path is the
   8.3 form `MARCUS~1`).
4. **The sandbox** blocks Gradle's daemon sockets: run Gradle with the sandbox off.
5. **Git Bash rewrites `/storage/...`** into `C:/Program Files/Git/storage/...` in `adb`
   arguments. `export MSYS_NO_PATHCONV=1` before any `adb` command with a device path,
   and give local files as `C:/...` paths.

The environment every Gradle command needs:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/MarcusRenlund/.audiofool-tmp -Djavax.net.ssl.trustStore=C:/Users/MarcusRenlund/.gradle/truststore-with-zscaler.jks -Djavax.net.ssl.trustStorePassword=changeit"
```

---

## The build → test → verify → ship loop

All from `C:\AudioFool-Android`, with the environment above.

```bash
./gradlew :core:test
```

The ported Core and its tests (plain JVM, seconds).

```bash
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Robolectric tests and ThemeLab: every screenshot test is compared with `app/screenshots/`.
After a deliberate visual change, re-record with `:app:recordRoborazziDebug`, look at the
new pictures in both themes at both sizes, and compare them with `design/screens/`.

```bash
./gradlew :app:assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Bump `versionCode`
(every build) and `versionName` in `app/build.gradle.kts` first.

### The emulator

AVD `Medium_Phone_API_37.0` (Android 17, 1080 × 2400, with an SD card volume `0000-0000`).
Leave out `-no-audio` now that it plays: the output stage needs an audio device.

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_37.0 -no-window -no-boot-anim -no-snapshot-save
```

Playback, as the emulator shows it (release builds too):

- `adb logcat -s AudioFoolPlay:I` logs each output opened ("44.1 kHz / 16-bit, 2 ch →
  output 44.1 kHz / 16-bit, 2 ch") and each song heard, with the joins, reopens and
  underruns so far. Statistics > Playback shows the same.
- Gapless: play `Gapless Live`; two joins and 0 underruns is gapless (one `AudioTrack`,
  never dry). The emulator's clock runs a little slow (12 s songs take about 12.7 s), so
  don't judge gaps by timestamps.
- Media keys: `adb shell input keyevent MEDIA_PLAY_PAUSE` (also `MEDIA_NEXT`,
  `MEDIA_PREVIOUS`, `HEADSETHOOK`). The session: `adb shell dumpsys media_session`; its
  first `state=PlaybackState` line can be an old one, `adb logcat -s MediaSessionService`
  shows each change.
- The shade's media player: `adb shell cmd statusbar expand-notifications`. The AVD has no
  lock screen set, so waking it shows the app; the lock screen is checked on the S24.

Then, with `MSYS_NO_PATHCONV=1`:

- Sample music: `tools/make-sample-music.sh <folder>` (ffmpeg and node) makes a FLAC 16/44 and 24/96, MP3 (ID3v2.3 and
  v2.4, embedded art), AAC, ALAC, Opus, Vorbis, WAV, AIFF, WavPack, DSF and DFF set, with
  folder covers, a hidden folder and an untagged file. Push it: `adb push <folder> /sdcard/` and to
  `/storage/0000-0000/Music/` for the SD card.
- All-files access without the settings page:
  `adb shell appops set --uid com.mpaulrenlund.audiofool MANAGE_EXTERNAL_STORAGE allow`
- Cold start: `adb shell am force-stop com.mpaulrenlund.audiofool` then
  `adb shell am start -W -n com.mpaulrenlund.audiofool/.MainActivity`. Warm start: back
  out (the activity goes, the process stays) and start again.
- **SD card removal**: `adb shell sm list-volumes all`, then `adb shell sm unmount public:253,80`
  and `sm mount ...`. The app gets the broadcast and rescans; the tracks stay.
- Tags as read: a **debug** build logs one line per track (≤ 200 tracks) under the tag
  `AudioFoolScan`: `adb logcat -s AudioFoolScan:D`. The debug build is signed with the
  debug key, so uninstall before switching between debug and release.
- Screenshots: `adb exec-out screencap -p > shot.png`.

### Shipping a test build

1. Confirm the tag and the notes with the user (CLAUDE.md).
2. `./gradlew :app:assembleRelease`, copy the APK to `AudioFool-<version>.apk`.
3. `gh release create v<version> AudioFool-<version>.apk --prerelease --title ... --notes ...`
4. Add what only the phones can show to *To test on the S24* / *To test on the M21* in
   `design/progress.md`.

Logs can't be read off the phones, so anything the user reports back is on the screen:
logo > **Statistics** shows the library line and the Debug panel (counts, start times,
cache and scan numbers, formats, covers filled, the mini player preview, the token sheet).

### Checking a screen against the mockups

```bash
ffmpeg -i design/screens/s24-dark-album.png -i app/screenshots/s24-dark-album.png -filter_complex "[1]scale=384:832:flags=area[b];[0][b]hstack" cmp.png
```

ThemeLab draws the screens with the mockups' sample library (`ThemeLabFixtures`) and the
mockups' 28 dp status row and 20 dp handle as insets, so the two line up.

### Driving the emulator

- Tapping by text: dump the screen (`uiautomator dump`), find the node whose `text="…"`
  starts with what you want, skipping `EditText` (the search field holds the query too),
  and tap the centre of its `bounds`. Session 3 wrapped that in a small script.

- A long list: generate tagged MP3s with ffmpeg (`-metadata artist=...`, `-c copy` from one
  silent file) and a `cover.jpg` per folder, then `adb push` them under `/sdcard/Music/`.
  Session 2 used 200 artists × 2 albums.
- Taps are in device pixels (1080 × 2400): a 360-wide screenshot is a third of that.
- The back gesture held halfway: `adb shell "input motionevent DOWN 3 1200; input motionevent MOVE 400 1200"`,
  take the screenshot, then `input motionevent UP 400 1200`.
- Text the screen shows: `adb shell uiautomator dump /sdcard/ui.xml` and grep `text=`.
- Frame times: `dumpsys gfxinfo com.mpaulrenlund.audiofool` (reset first). The headless
  emulator renders in software (~30 ms a frame for anything), so only the phones can judge
  smoothness.

---

## Where things stand (after session 3)

- **Playback works on the emulator:** every sample format plays at its own rate and depth
  (FLAC 16/44 and 24/96, MP3, AAC, ALAC 24/48, Opus, Vorbis, WAV, AIFF 24, WavPack, DSF and
  DFF as 88.2 kHz PCM); `Gapless Live` joins twice with 0 underruns; a format change
  reopens the output; the media session, the shade's player with the cover, media keys,
  seeking, swipe-down, and resume after the app is killed (restored paused at the place,
  and a play key resumes it from the home screen).
- **Not checked on the emulator** (on the S24 list): the lock screen, a real Bluetooth
  headset's buttons, unplugging headphones, audio focus against another app, and how it
  sounds.
- `NowPlaying` is what every screen reads; `Player` keeps it up to date. Statistics' preview
  switch is gone.
- Bit-Perfect is drawn off and does nothing (session 4); the readout is the shared output's.

## Where things stood after session 2

- **Released** (all pre-releases, signed with the release key): `v0.0.1` (session 1,
  versionCode 1), `v0.0.2` ("Session 2 test build", versionCode 2) and `v0.0.3` ("First-scan
  progress", versionCode 3), all 2026-10-24. The next is `0.0.4`, versionCode 4.
- **First scan:** with no cache, `LibraryRepository.showAsFound` publishes a partial
  library every 1.5 s or so while tags are read, and Artists shows "Reading tags: N of M".
  The M21's first full scan took 528,563 ms (8 min 49 s, 27,115 files from the SD card,
  about 51 a second); later starts read the cache.
- **Screens (session 2):** Artists, Albums, Album, Search, Settings, Statistics, the logo
  menu and the mini player, all from tokens, checked against `design/screens/` (the
  differences are listed in `design/progress.md`). Navigation is its own small back stack
  with predictive back. Statistics holds the debug numbers until session 5 builds the
  statistics above them (the user's call).
- **Bit-Perfect and Last.fm rows** in Settings are drawn in their off state and do nothing.
- **The user's results so far** (S24, 2026-10-24): 192 artists · 821 albums · 8,127 tracks,
  first scan under 30 seconds, cold start "very fast". The S24 has **no SD card slot** and a
  smaller library than the desktop's; the full ~26,000-track library and SD card removal
  are tested on the **M21** (27,115 files found there).
- **GitHub:** the repo is public (the user's call). This PC pushes and releases as
  `marcusrenlund`, a collaborator with write access; `gh` is signed in as that account.
- **Keep the debug numbers reachable** (the user reports them back, and later sessions
  add to them): `ui/StatisticsScreen.kt`'s `DebugPanel`.
- `LibraryRepository` is the library: cache first, then a background rescan; the scan
  reuses tags for files whose size and date haven't changed, and carries over every
  track of a folder it can't reach.
- **Covers:** `art/Thumbnails` (one per size: the lists' 64 dp, the album header's 72 dp)
  keeps small copies on disk and decoded ones in memory; `AudioFoolApp.prefillCovers` makes
  the list copies after each scan. Now Playing's big art (session 3) should decode the
  full cover with `CoverArt.read`, not these.

## Traps worth not rediscovering

- **An endless frame loop hangs ThemeLab.** Roborazzi waits for the screen to settle; a
  `LaunchedEffect` that asks for every frame (`withFrameMillis` in a loop) never lets it,
  and the test run hangs with no output. Now Playing's seekbar ticks only while playing and
  `NowPlaying.live` (false in `showForPictures`).
- **Tracks are matched by identity** (`===`) in the player and the hearing timeline, as
  the desktop's `ReferenceEquals`: `Track` is a data class, so `==` would mix up two copies
  of the same song (the emulator's SD card holds a copy of the sample set, so some albums
  have every song twice; that's the sample data, not a bug).
- **The output stage owns BASS's streams and the `AudioTrack`** on its own thread; anything
  else sends it a command. Its news comes back on that thread; `Player` posts it to main.
- **BASS add-ons load by file name** (`BASS_PluginLoad("libbassflac.so")`): no extracted
  libraries needed. The Java bindings must keep the package `com.un4seen.bass` (JNI names).
- **`run-as` doesn't work on release builds**, so the app's files can't be read on the
  emulator without a debug build (which needs an uninstall first: other key).

- **The Application loads TagLib** (an Android `.so`), so Robolectric tests run with a plain
  `android.app.Application` (`app/src/test/resources/robolectric.properties`). Test the
  pure logic in `core/`; TagLib itself is checked on the emulator.
- **Robolectric on JDK 25** needs `--add-exports java.base/jdk.internal.access` (set in
  `app/build.gradle.kts`), or every test fails with "raw FileDescriptor internals".
- **Robolectric runs SDK 36** (`robolectric.properties`); the app targets 37.
- **Strings cross JNI as UTF-8 byte arrays**, not `jstring`: JNI's modified UTF-8 breaks on
  emoji and other characters outside the BMP.
- **TagLib's ID3v2.3 dates** are not trusted beyond the year (the desktop's rule: TYER and
  TDAT merge with day and month swapped). **TagLib's APE tags** give no year for a full date;
  the reader takes it from the date, as TagLibSharp does.
- **Paths compare ignoring case** (the desktop's OrdinalIgnoreCase): Android's shared storage
  and SD cards are case-insensitive. `Paths.key()` for maps.
- **Name sorting** uses a root-locale collator (case ignored, accents not), cached per string.
- **Navigation keeps one keyed composition per screen** (`NavHost`). During the back
  gesture the screen underneath is composed too; when it becomes the top it must stay the
  same `key(...)` group, or it is recreated and loses its scroll position and selection
  (found on the emulator in session 2). Write `gesture = false` and the pop together.
- **Popups are windows of their own**: Roborazzi's `captureRoboImage { }` misses them. The
  logo menu's pictures use a compose rule and `captureScreenRoboImage` (`ThemeLabMenuTest`),
  one picture per test because a test can set its content only once.
- **ThemeLab can't reach TagLib**, so anything that reads covers or tags (Statistics' cover
  check) is shown without a library there, and `LocalArt` is null (placeholders).
- **CSS min-height** in the mockups is the content's, inside the padding: put
  `heightIn(min = ...)` after `padding(...)`.
- **Token arrays are keyed by `id`**: `album.columns.number.width`, not `columns.0`.
- A pushed file on a FAT card can be re-read once after a remount (FAT keeps write times to
  2 seconds), which the scan counts as "read", not "removed". Harmless.
