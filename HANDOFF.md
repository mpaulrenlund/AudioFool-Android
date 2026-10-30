# AudioFool for Android — session handoff

Updated 2026-10-30, after 0.5.1 (confirmed on the S24). Read this with `README.md` (how the app
works) and `design/progress.md` (every decision, newest first, and the lists of what to test on
each device). The spec is `design/android-spec.md`; the USB driver's story is
`design/usb-bit-perfect.md`. Older history (what sessions 2–5 left, the 0.2.x changes, the
session notes, every decision up to 0.2.6) is in `design/progress-archive.md`: search it when
you touch a feature from then.

## Starting the next session

- **Read, in order:** `CLAUDE.md`, this file, then in `design/progress.md` the "Decisions made
  for the build" section from the top (0.5.1 down to 0.3.0: the S24's own USB driver) and the
  unticked items in *To test on the S24* / *To test on the M21*.
- **Latest: 0.5.1** (versionCode 33), a pre-release,
  <https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.1>,
  `dist/AudioFool-0.5.1.apk`. Every release is on GitHub (`gh release list`); all but v0.1.0
  are pre-releases, and v0.0.5 was skipped.
- **Next release:** **0.5.2** for fixes, **0.6.0** for new features (confirm the number with the
  user). Bump both in `app/build.gradle.kts` (versionCode 34 next).
- **The release routine the user expects:** bump, `:core:test :app:testDebugUnitTest
  :app:verifyRoborazziDebug :app:assembleRelease`, copy to `dist/AudioFool-<v>.apk`, check the
  signer (`apksigner verify --print-certs`: SHA-256 `1fdefe0a…be2c21`), install it on the
  emulator (and the M21 if it's plugged in), then the user says **"Show <v> notes"** (show them
  in chat, written for the user: what changed, then a numbered *To try on the S24 / M21*) and
  **"Publish"**; publish only then, and record it in `design/progress.md` and here.
- **Nothing is waiting on the user right now.** No request is open; start nothing new unasked.

## Where things stand (2026-10-30)

- **Nothing is broken that the user knows of.** The last reports: "s24u and m21 both look good"
  on 0.2.6; Rescan Library works (0.5.0); "No stutter. Looks like this has been fixed" (0.5.1,
  S24, BP on through the Shanling UA2 Plus).
- **True bit-perfect on the S24 works** (since 0.4.6): with BP on and a USB DAC in, the app's
  own USB Audio driver (libusb, `usbaudio_driver.cpp`) plays to the DAC; Android's audio stack
  is bypassed. Everything is in `design/usb-bit-perfect.md` and spec §6.2. What it took:
  - **Volume is a hearing-safety matter.** Samsung turns a USB DAC down in its own software and
    says it takes 0 dB off, so the driver's volume comes from the user only (quietest first,
    then where the keys left it). Two loud blasts happened on the way (0.4.2, 0.4.3). Treat any
    volume change here as a hearing-safety change.
  - **Giving the DAC back** needs a USB reset, or it stays silent; Android's media volume goes
    to 1 and the DAC to 0 dB at a give-back. The DAC is muted while BP takes it and at rate
    changes (no pop since 0.4.6).
  - **Android ending the app** (0.4.7, 0.4.8): the service stays foreground while the DAC is
    held; paused and out of sight (`MainActivity.onStop`) the DAC is given back at once (the
    user's call); the next start repairs a DAC left held (`UsbDriver.repairIfLeftHeld`).
  - **The stutter at app switches** (0.4.9–0.5.1): with BP on no `AudioTrack` played, so
    Samsung/Android didn't count the app as playing and **stopped the whole process for about a
    second** at a switch, foreground service or not. 0.5.0's heartbeat thread proved it (the app
    stopped 1005 ms of a 1006 ms gap). 0.5.1's `SilentTrack` (a static `AudioTrack` looping
    zeros, started and paused with `UsbOutput`) fixed it. 128 ms in flight and a 1 s ring
    (0.4.9) stay.
- **Debug is how phone results come back** (logo > Statistics > last row > Debug): logs can't
  be read off the phones. The Playback panel (route, Bit-Perfect, mixers, what each song opened
  as), the Debug panel (start times, cache and scan numbers, formats, covers filled, the token
  sheet), and the USB driver row (gaps since the stream started, "longest gap N ms:
  the app stopped N ms, a send to the DAC N ms, waiting for samples N ms", the event thread's
  priority, "silent track: playing / paused"). Keep every number there reachable: cheap, and
  they name the cause if a stall comes back.
- **Still open on the Bit-Perfect list** (unticked in `design/progress.md`; not asked for, don't
  start unasked): a **DSD file** through the driver; **notification sounds** while the driver
  holds the DAC; the **M21 check** that 0.4.0's new output layer changed nothing there (BP on,
  an album through the headphone out: gapless, readout, seeks without clicks). The user also
  might want BP remembered per output; only if they ask.
- **The test lists were sorted on 2026-10-30**: `design/progress.md` holds only what is still
  open (17 on the S24, 11 on the M21, grouped by subject), plus a short "probably fine from
  daily use" group per phone that the user can tick with one "fine". The per-version lists with
  every result up to 0.5.1 are in `design/progress-archive.md`. Don't block on any of them.

## How the user works

- Sends a few UI requests at a time, often several in one message, and says when to release:
  build them all into one version, then show the release notes and ask before publishing.
- Answers design questions quickly when they come as short choices, with a recommended option
  first (AskUserQuestion works well, also for "plug the M21 in now" or "plug into the 3.5 mm
  jack" during a test). For a bug that can't be reproduced here, ask what they saw (when, how
  often, which phone) before guessing: that is what found 0.2.0's stuck cover.
- Keeps the M21 exactly as it is when asking for an S24 change: S24-only layout goes through the
  spare height, which is 0 on the M21; check the M21's pictures come out pixel-identical.
- Reports back in a sentence ("Seems to be fixed"); tick the matching items in
  `design/progress.md` and push.
- Label any comparison picture (which phone, which theme) and leave a gap between its parts: an
  unlabelled stack of the S24's seekbar over the M21's read as "two waveforms".

## What the phones turned out to do

- **FiiO M21** (Android 13; `Build.MANUFACTURER` "QUALCOMM", `BRAND` "qti", `MODEL` "FiiO M21"):
  - Android lists no headphone device: the 3.5 mm and 4.4 mm jacks are the built-in
    "speaker" (48 kHz, 16-bit, per Android). `SpeakerlessPlayers` spots a FiiO by "FiiO" in
    any of those names (or the speaker's name) and takes the speaker route as Headphones.
  - Its "becoming noisy" at an unplug comes from FiiO's status bar (`com.android.systemui`,
    uid 10089, an app), so the receiver must be `RECEIVER_EXPORTED` or it never hears it. No
    `ACTION_HEADSET_PLUG` is ever sent, and the kernel's jack files can't be read.
  - FiiO's own broadcasts reach an exported receiver: `fiio.intent.action.SAMPLE_RATE_CHANGED`
    (`rate`: the DAC's rate, 0 when stopped; `format`) and `fiio.intent.action.HEADSET_TYPE_CHANGED`
    (`type` **1 for the 3.5 mm jack, 8 for the 4.4 mm, 0 unplugged**; read off the M21 over adb
    in 0.2.5). Neither is sticky, and nothing an app can read says which jack is in, so the
    last type is saved (`settings` → `fiioJack`). The readout's rate goes by `rate`
    (`OutputReadout.hardwareRate`).
  - **It has no speaker:** unplugged, nothing is heard (the user), hence "No Output".
  - The DAC runs at the song's rate even with BP off ("Headphones · 96 kHz"); BP adds
    the full-volume, whole-number-sample path. 24-bit songs go as 32-bit with BP on, as
    float with it off: **24-bit packed `AudioTrack`s come out as static there.**
  - It holds the full library: 27,115 files from the SD card. Its first full scan took
    8 min 49 s (about 51 files a second); later starts read the cache.
- **S24 Ultra:** **no SD card slot**, and a smaller library (192 artists · 821 albums · 8,127
  tracks on 2026-10-24; first scan under 30 s). With the user's USB dongle DAC, Android's mixer
  list is default entries only, at 48, 88.2, 96, 176.4, 192, 352.8 and 384 kHz (16, 24, 32-bit),
  **no bit-perfect entry and no 44.1 kHz**: that is why the app has its own USB driver. Before
  the driver, BP on was "Rate Matched" at 48 kHz and up; on Android's path a 44.1 kHz song is
  resampled to 48 kHz.

## Reaching the phones

- **The M21 can be plugged into this PC** (the user did, 2026-10-25 and 2026-10-26). If adb
  doesn't list it and Windows shows no FiiO device (VID 2972), ask for: USB debugging on, the
  USB notification set to "File transfer", "Allow" on the prompt, a data cable. Its screen:
  the logo (menu) is at (56, 104) in its 750 × 1334 px. adb shows it as
  `model:FiiO_M21` with the serial "?", so pick it with `adb -t <transport_id>` from
  `adb devices -l` (the emulator is the other device). adb isn't on the PATH: it is
  `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. `adb install -r` of a release build
  updates in place (same key, data kept). Useful: `dumpsys activity broadcasts history` (each
  broadcast, its sender and who received it; extras show as STRIPPED),
  `dumpsys activity broadcasts` (registered receivers), and `uiautomator dump` to read the
  app's screen without the user typing it out. A plain `adb logcat` capture dropped when
  USB reconnected and caught little.
- **The S24 can't use a cable while testing the DAC** (one USB-C port). Wireless debugging
  was tried and the user couldn't get the pairing step to work, so S24 results come as typed
  readings from Statistics > Debug (Playback). Never type a pairing code for the user. A
  Samsung phone was on this PC's USB once (2026-10-27, adb serial `R5CWC28ETAW`,
  "unauthorized"): probably the S24; adb on it needs "Allow USB debugging" on the phone.
- **The emulator** holds the sample music: `/sdcard/Music` (with `Long/`: 200 artists × 2
  albums of silent MP3s; `Gapless Live/`: three songs of exactly 12.000 s; `Waveform Test/`: a
  60 s pink-noise FLAC with swells and a near-silent dip at 24–27 s, for the seekbar's shape)
  and, on the SD card `/storage/0000-0000/Music`, part of the sample set and `SD Card Band/`:
  two 3-minute FLACs for pulling the card mid-song. **Those two are damaged again** (ffmpeg
  fails at the start of both, found 2026-10-26): rebuild them before relying on them. When last
  noted (0.2.6), the app there had Bit-Perfect saved on, Repeat All and Shuffle on, and Last.fm
  not connected. The AVD sometimes exits on its own (exit code 4): start it again. The app's
  uid there is 10236 (for `dumpsys audio`).
- `dist/` (ignored by git) holds the released APKs and the M21's captured reports (`m21-*.txt`).
- **Last.fm needs the user's key** to check for real: the emulator can't (no key here, and
  the key never goes in the repo).

---

## Where everything lives

| What | Path |
|---|---|
| Source | `C:\AudioFool-Android`, GitHub `mpaulrenlund/AudioFool-Android`, branch `main` |
| The desktop app (read-only) | `C:\MusicPlayer`; its `src/AudioFool.Core` is what gets ported |
| Ported Core (plain Kotlin, JVM tests) | `core/` — `library/`, `model/`, `theme/`, `art/`, `format/`, `playback/`, `analysis/` |
| The Android app | `app/` — `theme/`, `library/`, `settings/`, `storage/`, `art/` (thumbnails), `playback/`, `ui/` (screens, navigation) |
| The library | `library/LibraryRepository`: the cache first, then a background rescan; the scan reuses tags for files whose size and date haven't changed, and carries over every track of a folder it can't reach |
| Covers | `art/Thumbnails` (one per size: the lists' 64 dp, the album header's 72 dp) keeps small copies on disk and decoded ones in memory; `AudioFoolApp.prefillCovers` makes the list copies after each scan. Now Playing, the media session and Statistics' cover check read the full cover with `CoverArt.read` |
| Playback | `app/.../playback/`: `BassRuntime` (start-up, add-ons), `Decoder` (one BASS stream per song; DSD as PCM or DoP), `OutputStage` (the `AudioTrack` thread: gapless, what's heard, opening by `OutputPlan`, reopening on a route or setting change), `OutputRoutes` (the route, a USB device's mixer entries, setting the mixer, the pretend route, the debug numbers), `Player` (queue, order, focus, unplug, resume, the Bit-Perfect setting, the readout), `NowPlaying` (what the screens read), `SystemControls` (media session), `PlaybackService` (notification, foreground), `WaveformReader`. Ported and new logic in `core/.../playback/` (`DopRule`, `BitPerfect.kt`: `BitPerfectRule`, `MixerChoice`, `SpeakerlessPlayers`; `OutputReadout` with `hardwareRate`; `SeekFade`, the desktop's 5 ms fade, used by `OutputStage.cut` and `Decoder.fadeInNext`) and `core/.../analysis/` |
| USB DAC, read directly (0.3.0) and the driver (0.4.0) | `app/.../playback/UsbDriver.kt` (take, permission, rates, format, rate, volume, give back with a reset, unplug), `UsbStream.kt` (the native calls), `PcmOutput.kt` (`AudioTrackOutput`, `UsbOutput`), `app/src/main/cpp/usbaudio_driver.cpp` (libusb streaming, feedback, ring, fade; library `audiofoolusb`, linked to `usb-1.0` from `third_party/libusb`), `UsbDac.kt` (the Debug panel and report), `UsbAttachActivity.kt` (the "always open" filter), core `usbaudio/` (`UsbAudioDescriptors.kt`, `UsbStreamRules.kt`); write-up in `design/usb-bit-perfect.md` |
| Last.fm | `core/.../scrobbling/` (ported: `LastFmApi`, `PlayTracker`, `ScrobbleEntry`, `ScrobbleQueue`, `LastFmScrobbler`); the app's `lastfm/LastFm.kt` (the connect flow, what Settings shows, a `PlayWatcher`) and `LastFmStore.kt` (Keystore-encrypted key, secret and session) |
| Statistics | `core/.../library/LibraryStatistics.kt` (ported), `core/.../playback/ListeningTime.kt`; the app's `playback/Listening.kt` (a `PlayWatcher`) and `ui/StatisticsScreen.kt` (Statistics, and `DebugScreen` with the Playback and Debug panels) |
| What is heard, for both | `playback/PlayWatcher.kt`: `Player` tells its `watchers` the heard track and place every 250 ms while playing |
| BASS 2.4 for Android (vendored unchanged) | `third_party/bass/`: `libs/arm64-v8a`, `libs/x86_64`, `java/com/un4seen/bass` (added to the app's source sets in `app/build.gradle.kts`), licence texts. Where each zip comes from: README |
| Screens | `ui/AppRoot.kt` wires them; `Navigation.kt` (back stack, predictive back), `LibraryScreens.kt` (Artists, Albums, Album), `SearchScreen.kt`, `SettingsScreen.kt`, `ArtistIndexStrip.kt` (Artists' letter strip), `NowPlayingScreen.kt` (with `SwipeToSkip`), `StatisticsScreen.kt` (Statistics and Debug), `MiniPlayer.kt`, `LogoMenu.kt`, `Parts.kt` (the shared parts, `AlbumArt`) |
| Launcher icon | `res/mipmap-anydpi/ic_launcher.xml`; the foreground PNG is the desktop's `icon_preview.png` scaled with ffmpeg (228 px in a 432 px canvas) |
| TagLib 2.3.2 (C++, vendored unchanged) | `third_party/taglib/`; the JNI bridge is `app/src/main/cpp/tagreader_jni.cpp` |
| Selawik font | `app/src/main/res/font/` (Regular, Semibold, Bold), licence in `third_party/selawik/` |
| Token files | `design/theme-tokens.json`, `theme-tokens.dark.json` (the desktop's, unchanged), `design/mobile-tokens.json`. The build copies all three, and `PlayStation-Logo-1994.png`, into the app's assets (`copyAppAssets`); never copy them by hand |
| ThemeLab pictures | `app/screenshots/` (recorded by Roborazzi; compare with `design/screens/`) |
| **Release keystore** | `C:\Users\MarcusRenlund\AndroidKeys\audiofool-release.jks`, passwords in `audiofool-release.properties` beside it. **Outside the repo.** Back up both files together |
| Signing pointer | `~/.gradle/gradle.properties`: `audiofool.signing=<path to the .properties>` |
| On the phone: library cache | `/data/data/com.mpaulrenlund.audiofool/no_backup/library.bin` (+ `.bak`) |
| On the phone: settings | SharedPreferences `settings` (theme, music folders, `bitPerfect`, `fiioJack`: the last jack a FiiO named, since 0.2.5) |
| On the phone: Last.fm | SharedPreferences `lastfm` (API key, secret, user, session: each AES-GCM encrypted with the Keystore key `audiofool.lastfm`); `no_backup/scrobbles.json` (+ `.bak`): the queue |
| On the phone: listening time | `no_backup/listening.json` (+ `.bak`): total ms and since when |
| On the phone: cover copies | `/data/data/com.mpaulrenlund.audiofool/no_backup/art/*.art` (ArtDiskCache; empty file = no cover) |
| On the phone: resume point | `no_backup/resume.json` (+ `.bak`): the queue, song, place, shuffle, repeat |
| On the phone: waveforms | `no_backup/waveforms-600/*.wave` (600 levels per file, named from path, size, date and columns; the old 96-level `waveforms/` is deleted at start since 0.2.4) |

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
  underruns so far. Statistics > Debug > Playback shows the same.
- Gapless: play `Gapless Live` (three songs of exactly 12.000 s). Two joins and 0 underruns
  means one `AudioTrack` that never ran dry, **not** that the join is clean: also check
  the logcat `heard` lines come about 12.0 s apart. Up to 0.1.0 they came 12.7 s apart,
  which was put down to a slow emulator clock; it was 0.74 s of stale buffer written at
  every join (fixed in 0.1.1).
- Media keys: `adb shell input keyevent MEDIA_PLAY_PAUSE` (also `MEDIA_NEXT`,
  `MEDIA_PREVIOUS`, `HEADSETHOOK`). The session: `adb shell dumpsys media_session`; its
  first `state=PlaybackState` line can be an old one, `adb logcat -s MediaSessionService`
  shows each change.
- The shade's media player: `adb shell cmd statusbar expand-notifications`. The AVD has no
  lock screen set, so waking it shows the app; the lock screen is checked on the S24.
- **Bit-Perfect on the emulator:** its only output is the speaker, so BP is greyed. Statistics >
  Debug > Playback > **Pretend route (emulator test)** cycles off → Headphones (the M21's way) → USB
  (a made-up bit-perfect mixer, never sent to Android) → off; not saved. With USB and BP on,
  a 24/96 FLAC opens "(BitPerfect)" and the DFF opens as DoP at 176.4 kHz. The logcat
  `opened` lines end with the path and the route.
- The logo button is at (74, 137) in device pixels; the Debug screen is the last row of
  Statistics (swipe up three times), and its Pretend row ends the Playback panel. Too many BACKs leave the app, and the next tap
  lands on the home screen.

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

The routine is under *Starting the next session*. After the user says "Publish":
`gh release create v<version> dist/AudioFool-<version>.apk --prerelease --title "AudioFool <version>" --notes ...`,
then add what only the phones can show to *To test on the S24* / *To test on the M21* in
`design/progress.md`.

### Checking a screen against the mockups

```bash
ffmpeg -i design/screens/s24-dark-album.png -i app/screenshots/s24-dark-album.png -filter_complex "[1]scale=384:832:flags=area[b];[0][b]hstack" cmp.png
```

ThemeLab draws the screens with the mockups' sample library (`ThemeLabFixtures`) and the
mockups' 28 dp status row and 20 dp handle as insets, so the two line up.

### Driving the emulator

- Tapping by text: `adb shell uiautomator dump /sdcard/ui.xml`, find the node whose `text="…"`
  starts with what you want, skipping `EditText` (the search field holds the query too),
  and tap the centre of its `bounds`.
- With the logo menu open, Statistics is at about (320, 531) and Rescan Library at about
  (320, 657). Release builds log no `AudioFoolScan` lines, so Statistics' track and format
  counts are the quick check that a rescan ran.
- A long list: generate tagged MP3s with ffmpeg (`-metadata artist=...`, `-c copy` from one
  silent file) and a `cover.jpg` per folder, then `adb push` them under `/sdcard/Music/`.
  Session 2 used 200 artists × 2 albums.
- Taps are in device pixels (1080 × 2400): a 360-wide screenshot is a third of that.
- Artists' letter strip sits near x = 1012 px; the mini player at about (300, 2210). On Now
  Playing (the emulator's tall screen) Repeat is near (96, 2052), Shuffle (207, 2052), Play
  (657, 2130).
- **`uiautomator dump` drops a text that holds quote marks** (the Route row's `speaker "…"`):
  take a screenshot to read those.
- **Measuring something that moves** (the seekbar's split): crop one pixel row of rapid
  screenshots with ffmpeg (`crop=1080:1:0:<y>`, `-f rawvideo -pix_fmt rgb24`) and find the
  colour edge with a few lines of node.
- The back gesture held halfway: `adb shell "input motionevent DOWN 3 1200; input motionevent MOVE 400 1200"`,
  take the screenshot, then `input motionevent UP 400 1200`.
- Frame times: `dumpsys gfxinfo com.mpaulrenlund.audiofool` (reset first). The headless
  emulator renders in software (~30 ms a frame for anything), so only the phones can judge
  smoothness.

---

## Traps worth not rediscovering

- **Ejecting a volume kills any process with a file open on it.** vold sends SIGINT to every
  process holding a file on the card about 250 ms after `ACTION_MEDIA_EJECT`; the app has
  that long to close its streams (`Storage.watchVolumes`' `onEjecting` → `Player.letGoOf`).
  Anything new that keeps a file on the card open (a long read, a stream) must let go there
  too. A physical pull goes the same way.
- **With BP on, a `SilentTrack` must play whenever the USB driver plays.** Without an
  `AudioTrack` playing, Samsung/Android stops the whole process for about a second at an app
  switch, foreground service or not. Anything new that plays through `UsbOutput` (or another
  path around `AudioTrack`) starts and pauses the silent track with it.
- **`AudioFoolApp.playerLazy` must be declared before `player`** (which delegates to it), or
  the app crashes at start with a null delegate.
- **`adb shell mkdir` with a space** needs inner quotes (`"'/storage/…/SD Card Band'"`), or it
  makes one folder per word.
- **A play from outside the app goes through `Player.resumeFromSystem`**, never `resume()`
  directly, so a plug can't start the music (`PlugGuard`). The app's own buttons call `resume()`.
- **On the emulator, the Debug screen's rows move** as its Opened log fills: find a row's
  place again (uiautomator) right before tapping it, not from an earlier dump.
- **The listening time and scrobbles count the heard position**, not the clock: anything
  that changes how `positionMs()` behaves changes both.

- **A song that opens but stops at once at 0:00** (the queue "ends") on the emulator is likely a
  damaged sample file: check it with `ffmpeg -v error -i file -f null -`. The SD card's
  `SD Card Band` pair was damaged and rebuilt on 2026-10-25.
- **ThemeLab's pressed letter strip** passes `indexPreview` to `ArtistsScreen` (the letter under
  the finger, and the list scrolled to it).
- **A drag's moves set state directly** (`rememberDraggableState { offset += it }`), never through
  a coroutine per move: one landing after the release can stop the animation that follows.
  And an action that changes what's shown (a skip) shouldn't wait on an animation to put
  things back. Both were suspects in 0.2.0's stuck cover; 0.2.1 removed both.
- **A gesture bug the S24 shows and the emulator doesn't**: `adb shell input` swipes arrive
  50–100 ms per event; a phone's touch screen sends one every 8 ms or so. Robolectric's
  `performTouchInput` gets closer (`SwipeToSkipTest`), but neither caught 0.2.0's stuck art.
- **A background read that fails in ThemeLab** (TagLib) goes uncaught and fails the *next*
  compose test with `UncaughtExceptionsBeforeTest`; guard cover and tag reads on `LocalArt`.
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
- **A buffer from `Decoder.read` must be empty when it returns false** (`limit(0)`): `clear()` makes it look full of old samples, which `pump` then writes.
- **Anything that cuts a playing `AudioTrack` goes through `OutputStage.cut`**, which fades it out first; a bare `pause()`/`flush()` clicks on the M21.
- **On the M21, system broadcasts can come from FiiO's status bar** (an app uid): a `RECEIVER_NOT_EXPORTED` receiver misses them. Check the sender in `dumpsys activity broadcasts history`.
- **The M21 has no headphone device in Android's eyes**: its jacks are the "speaker" (48 kHz, 16-bit, per Android). Don't look for `TYPE_WIRED_*` there.
- **24-bit packed `AudioTrack`s are static on the M21** (Android 13) though the emulator plays them: shared sends 24-bit as float, the M21's way as 32-bit.
- **An `AudioTrack` must use the mixer entry's own `AudioFormat`** (`OutputPlan.Mixer.format`)
  to be attached to the mixer that was set; a rebuilt one with another channel mask may not
  match. `OutputRoutes.MEDIA` is the one set of attributes for both the mixer and every track.
- **`AudioFormat.Builder` throws above Android's rate limit** (192 kHz on the emulator; a
  hidden per-device value): wrap anything that builds one for a made-up rate.
- **`setPreferredMixerAttributes` and the other mixer calls are Android 14+**: guard each call
  with `Build.VERSION.SDK_INT` inline (lint doesn't follow `OutputRoutes.MIXER_API`). The M21
  is Android 13.
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
