This PC's quirks, the Gradle tasks, the emulator, comparing with the mockups and driving the emulator. Moved out of HANDOFF.md on 2026-11-11, unchanged except for cross-references.

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

The routine is the `/release` skill (`.claude/skills/release/SKILL.md`); the numbered routine it came from is in `design/progress-archive.md` (*HANDOFF.md as of 2026-11-11*). A build goes out as a GitHub pre-release
with the APK (`gh release create … --prerelease`), **only after checking the repo is
`PRIVATE`**: the APK carries AudioFool's Last.fm key. 0.8.1 alone went over USB (`adb -s
<serial> install -r dist/AudioFool-<version>.apk`), while the repo was public. Keep the notes
beside it (`dist/notes-<version>.md`), then add what only the phones can show to *To test on the
S24* / *To test on the M21* in `design/progress.md`.

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
- With the logo menu open (alphabetical since 0.7.0's follow-up; at the right since the logo
  moved there, 2026-11-10): Library Statistics at about (650, 282), then each row 126 px lower:
  Listening History (650, 408), Rescan Library (650, 534), Settings (650, 660), Theme (650, 786),
  Version (650, 912), which opens the releases page in Chrome (its first-run screen, the first
  time: leave it). A tap at x = 320 misses the menu and closes it. **BACK on Artists leaves the app**: later taps land on the home screen. Release builds log no `AudioFoolScan` lines, so Statistics' track and format
  counts are the quick check that a rescan ran.
- A long list: generate tagged MP3s with ffmpeg (`-metadata artist=...`, `-c copy` from one
  silent file) and a `cover.jpg` per folder, then `adb push` them under `/sdcard/Music/`.
  Session 2 used 200 artists × 2 albums.
- Taps are in device pixels (1080 × 2400): a 360-wide screenshot is a third of that.
- Artists' letter strip sits near x = 1012 px; the mini player at about (300, 2210); the logo
  (at the right since 2026-11-10) at (1006, 137), and the ARTISTS / ALBUMS shuffle buttons at
  (996, 256). On Now Playing (the emulator's tall screen, from 2026-11-10) Repeat is near
  (111, 1917), Shuffle (111, 2054), Play (540, 1986), and a tap on the cover (540, 560) opens
  its menu, Bit-Perfect at about (715, 891). The sample songs are 2 s long and the menu closes
  at a song change: pause first.
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
