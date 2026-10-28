# AudioFool for Android — session handoff

Updated 2026-10-27, after 0.2.6 (confirmed on both phones). Read this with `README.md` (how
the app works) and `design/progress.md` (every decision, and the lists of what to test on each
device). The spec is `design/android-spec.md`.

## Starting the next session

- **Read, in order:** `CLAUDE.md`, this file, then in `design/progress.md` the "Decisions made
  for the build" section from the top (0.4.0 down to 0.3.0 is the newest work) and the newest
  session notes.
- **Latest: 0.4.3** (versionCode 25), a pre-release, <https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.3>, `dist/AudioFool-0.4.3.apk`: the
  driver, never louder when the DAC changes hands (0.4.2's BP off came back far too loud).
- **Next release: 0.4.4 (versionCode 26) is built**, `dist/AudioFool-0.4.4.apk`, not yet released;
  then **0.4.5 / 27** for fixes, **0.5.0** for new features. Bump both in
  `app/build.gradle.kts`. Confirm the tag and the notes with the user before `gh release create`.
- **Releases so far:** v0.0.1 to v0.0.4 pre-releases, v0.0.5 skipped, v0.1.0 the first full
  release (2026-10-24); v0.1.1 to v0.1.5 and v0.2.0 to v0.2.2 (2026-10-25); v0.2.3 to v0.2.6
  (2026-10-26); v0.3.0, v0.3.1 and v0.4.0 to v0.4.3 (2026-10-27). All but v0.1.0 are pre-releases.
- **The release routine the user expects:** bump, `:core:test :app:testDebugUnitTest
  :app:verifyRoborazziDebug :app:assembleRelease`, copy to `dist/AudioFool-<v>.apk`, check the
  signer (`apksigner verify --print-certs`: SHA-256 `1fdefe0a…be2c21`), install it on the
  emulator (and the M21 if it's plugged in), show the notes, publish only on their yes, then
  record it in `design/progress.md` and here.

### Where things stand (2026-10-27)

- **Nothing is broken that the user knows of.** The user's last report: "s24u and m21 both look
  good" on 0.2.6.
- **Confirmed on the phones** (0.2.x): the letter strip and swipe to skip (S24); plugging a USB
  DAC or adapter in or out never starts the music (S24); the smooth seekbar (M21); the readout
  names the M21's jack, "3.5mm" / "4.4mm", and says "No Output" unplugged, also after a restart
  (M21); the S24's Now Playing buttons at 56 dp above the readout (S24). Earlier: gapless with no
  glitch, a song tap opens Now Playing, 24-bit clean, BP, unplug pausing, no click at a seek.
- **Not yet reported** (don't block on them; the unticked items in `design/progress.md`): the
  S24 DAC's **own name** in the readout (0.2.5: `AudioDeviceInfo.productName`; if it shows
  "USB" or an odd name, the Debug screen's Route row has what Android calls it), and the
  USB-C adapter's; the smooth seekbar on the S24; the 0.2.2 items (covers without a frame, the
  one-line quality, Debug); the seekbar gliding; Last.fm on a real profile; the lock screen and
  notification; Bluetooth buttons; a call; battery; DSD over DoP; a rate change mid-album with BP
  on; the SD card pulled while playing; the full library's cold start and Statistics on the M21;
  the S24 DAC's **Route** row (does the DAC itself list 44.1 kHz?).
- **In progress: true bit-perfect on the S24** (2026-10-27). The plan, the user's four choices
  and every finding are in `design/usb-bit-perfect.md`. 0.3.0 read the DAC (UA2 Plus: UAC2,
  44.1–768 kHz, −63.5 to 0 dB hardware volume; Samsung lets the app take it); 0.3.1 proved how to
  give it back (release, reattach, **reset**: Android then sets it up as if replugged; reattach
  alone leaves it silent). **0.4.0 is the driver** (released as a pre-release; "The driver
  (0.4.0)" in the write-up): libusb in `third_party/libusb`, `usbaudio_driver.cpp`,
  `UsbDriver`/`UsbStream`/`PcmOutput`, `OutputPlan.Driver`. **Nothing of it has run on a DAC**:
  the S24's report decides the next round (clicks or drift mean the feedback; the Driver row's
  clock line and underruns say). The spec (§6.2) changes only when it plays clean. The user
  also might want BP remembered per output; only if they ask.
- **A Samsung phone was on this PC's USB on 2026-10-27** (adb serial `R5CWC28ETAW`, shown as
  "unauthorized"): probably the S24. adb on it needs "Allow USB debugging" on the phone; it
  can't stay on the cable while the DAC is in.
- **Emulator housekeeping:** the SD card's `SD Card Band` FLACs are damaged again (ffmpeg
  fails at the start of both); rebuild them before relying on them. `/sdcard/Music/Waveform
  Test/` (a 60 s pink-noise FLAC with swells and a dip at 24–27 s) is the one to check the
  seekbar's shape with. The AVD sometimes exits on its own (exit code 4); start it again.

### What 0.2.x changed (all the user's requests)

- **0.2.0–0.2.2 (2026-10-25):** the Artists **letter strip**, always shown (`ArtistIndexStrip`,
  `ArtistIndex`); Now Playing **swipe to skip** (left Next, right Previous; no animation on a
  skip since 0.2.1's stuck-cover fix); covers **without a frame**; the quality on **one line**
  (`QualityLines`); **Repeat and Shuffle side by side over BP**; the M21's **cover 301 dp**
  (`artHeightReserve` 366); a **Debug screen** (Statistics' last row).
- **0.2.3:** **spare height** on Now Playing: where the width limits the cover (the S24, about
  120 dp; 0 on the M21), the waveform grows to 64 dp and the two flexible gaps even out.
  **`PlugGuard`** (core): a play from outside the app (keys, lock screen, notification:
  `SystemControls.onPlay` → `Player.resumeFromSystem`) within 3 s after a USB or wired device
  comes or goes is ignored, and one up to 3 s before it is paused again. Debug's **Plug guard** row.
- **0.2.4:** the **seekbar as one smooth shape**: the desktop's 600 RMS columns
  (`WaveformLevels.COLUMNS`) drawn as its `WaveformView` outline (`WaveShape`); RMS only, both
  channels combined, no playhead line (the user's choices). Waveforms live in
  `no_backup/waveforms-600/`; the old `waveforms/` is deleted at start.
- **0.2.5–0.2.6:** the **readout reworded**: the output's name, rate, depth and note with a dot
  between each: "Shanling UA2 Plus · 96 kHz · 24-Bit · Exclusive", "· Rate Matched",
  "3.5mm · 44.1 kHz · 16-Bit · Exclusive", "Speaker · 48 kHz · Resampled", "No Output"
  (`OutputNames`, `OutputReadout.describe(name, …)`; the desktop's describe by mode is
  unchanged). The M21's jack comes from FiiO (below), saved in `settings` → `fiioJack`.
  Debug's **FiiO jack** row. The S24's controls sit **56 dp above the readout**
  (`controlsLift`: 0.46 of the spare height, up to 56 dp; 0 on the M21).

### How the user works

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

### What the phones turned out to do (2026-10-25)

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
    last type is saved. The readout's rate goes by `rate` (`OutputReadout.hardwareRate`).
  - **It has no speaker:** unplugged, nothing is heard (the user), hence "No Output".
  - The DAC runs at the song's rate even with BP off ("Headphones · 96 kHz"); BP adds
    the full-volume, whole-number-sample path. 24-bit songs go as 32-bit with BP on, as
    float with it off: **24-bit packed `AudioTrack`s come out as static there.**
- **S24 Ultra + the user's USB dongle DAC:** the mixer list is default entries only, at 48,
  88.2, 96, 176.4, 192, 352.8 and 384 kHz (16, 24, 32-bit), **no bit-perfect entry and no
  44.1 kHz**. So BP on is "rate matched" at 48 kHz and up ("USB · 96 kHz / 24-Bit (Rate
  Matched)", in today's wording "<DAC name> · 96 kHz · 24-Bit · Rate Matched"), and a 44.1 kHz
  song is shared and resampled to 48 kHz either way.

### Reaching the phones

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
  readings from Statistics > Debug (Playback). Never type a pairing code for the user.
- **The emulator** holds the sample music (`/sdcard/Music`, with `Long/`: 200 artists × 2
  albums of silent MP3s, `Gapless Live/`: three songs of exactly 12.000 s; and on the SD card,
  `/storage/0000-0000/Music`, part of the sample set and `SD Card Band/`: two 3-minute FLACs
  for pulling the card mid-song, rebuilt 2026-10-25 as 3-minute tones after the old pair was
  found damaged, and **damaged again by 2026-10-26**: ffmpeg fails at the start of both; rebuild
  them before relying on them), and `/sdcard/Music/Waveform Test/`: one 60 s pink-noise FLAC with
  swells and a near-silent dip at 24–27 s, for checking the waveform's shape and the 0.2.6 release build, with Bit-Perfect saved **on**, Repeat All and
  Shuffle left **on** (from a tap test), and Last.fm not connected. `dist/` (ignored by git) holds the released APKs and the M21's
  captured reports (`m21-*.txt`).
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

1. Confirm the tag and the notes with the user (CLAUDE.md).
2. `./gradlew :app:assembleRelease`, copy the APK to `AudioFool-<version>.apk`.
3. `gh release create v<version> AudioFool-<version>.apk --prerelease --title ... --notes ...`
4. Add what only the phones can show to *To test on the S24* / *To test on the M21* in
   `design/progress.md`.

Logs can't be read off the phones, so anything the user reports back is on the screen:
logo > **Statistics** shows the library line; its last row opens **Debug** (since 0.2.2): the
Playback panel (route, Bit-Perfect, mixers, what each song opened as) and the Debug panel
(start times, cache and scan numbers, formats, covers filled, the token sheet).

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
- Text the screen shows: `adb shell uiautomator dump /sdcard/ui.xml` and grep `text=`.
- Frame times: `dumpsys gfxinfo com.mpaulrenlund.audiofool` (reset first). The headless
  emulator renders in software (~30 ms a frame for anything), so only the phones can judge
  smoothness.

---

## Where things stood after session 5

- **Last.fm** is ported and wired: now playing, scrobbles from the heard position, the
  offline queue, Connect / Disconnect in Settings. Not yet seen on a real profile.
- **Statistics** shows the totals, formats and listening time (the user approved the
  screenshots), above the Playback and Debug panels.
- **Edge cases** checked: long names, a 120-song album, an empty library and font size 1.3
  in ThemeLab; SD card out while playing and idle, a call, and cold start on the emulator.
  Fixed: the app was killed when the card was ejected with a song on it open
  (`Player.letGoOf`), and Now Playing's readout fell off the M21 at font size 1.3.
- **0.1.0 is released** (2026-10-24, a full release, versionCode 6):
  <https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.0>, with
  `dist/AudioFool-0.1.0.apk`. What to check on each phone is in `design/progress.md`
  (*To test on the S24* / *M21*).

## Where things stood after session 4

- **Bit-Perfect is wired up**: the BP button and the Settings chip (saved, greyed on the
  speaker and Bluetooth), USB mixer attributes on Android 14+ (bit-perfect, else rate
  matched), the M21's source-rate way checked against the system output's rate, DoP for DSD
  on the bit-perfect mixer, and a reopen at the place heard when the route or the setting
  changes. The readout names the route.
- **Checked only on the emulator**, with the pretend route: nothing about a real DAC or the
  M21's bypass is known yet; that's the v0.0.5 lists in `design/progress.md`.
- **What the user reports** is on Statistics > Debug > Playback: Route, Bit-Perfect, Opened as,
  System output, Direct, USB mixers, Mixer set.

## Where things stood after session 3

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
  add to them): `ui/StatisticsScreen.kt`'s `DebugScreen` (Playback and Debug panels).
- `LibraryRepository` is the library: cache first, then a background rescan; the scan
  reuses tags for files whose size and date haven't changed, and carries over every
  track of a folder it can't reach.
- **Covers:** `art/Thumbnails` (one per size: the lists' 64 dp, the album header's 72 dp)
  keeps small copies on disk and decoded ones in memory; `AudioFoolApp.prefillCovers` makes
  the list copies after each scan. Now Playing's big art (session 3) should decode the
  full cover with `CoverArt.read`, not these.

## Traps worth not rediscovering

- **Ejecting a volume kills any process with a file open on it.** vold sends SIGINT to every
  process holding a file on the card about 250 ms after `ACTION_MEDIA_EJECT`; the app has
  that long to close its streams (`Storage.watchVolumes`' `onEjecting` → `Player.letGoOf`).
  Anything new that keeps a file on the card open (a long read, a stream) must let go there
  too. A physical pull goes the same way.
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
