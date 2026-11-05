# AudioFool for Android — session handoff

Updated 2026-11-05, after 0.8.1. Read this with `README.md` (how the app works) and
`design/progress.md` (every decision, newest first, and the lists of what to test on each
device). The spec is `design/android-spec.md`; the USB driver's story is
`design/usb-bit-perfect.md`. Older history (what sessions 2–5 left, the 0.2.x changes, the
session notes, every decision up to 0.2.6) is in `design/progress-archive.md`: search it when
you touch a feature from then.

## Starting the next session

- **Read, in order:** `CLAUDE.md`, this file, then in `design/progress.md` the "Decisions made
  for the build" section from the top (0.8.1 down to 0.3.0, the S24's own USB driver) and the
  unticked items in *To test on the S24* / *To test on the M21*.
- **Where it stands:** nothing is broken that the user knows of. The latest build is **0.8.1**
  (versionCode 41), on both phones since 2026-11-05. The user's last report: "looks its
  working on both devices" (the Last.fm sign-in and Listening History). **On `main` since,
  not in a build yet:** the shuffle button in the ARTISTS header (spec §4.1; see *What the
  recent builds did*). It waits for the user to say to build (0.9.0 proposed).
- **Builds go to the phones over USB, never to GitHub** (since 0.8.1, the user's call;
  `CLAUDE.md` says so too). Every APK carries AudioFool's Last.fm key and secret, and the repo's
  releases are public: Claude's safety check stopped the 0.8.1 upload as a credential leak, and
  any later attempt would be the same leak. The last GitHub release is 0.8.0 (all but v0.1.0
  are pre-releases; v0.0.5 was skipped). `dist/` (ignored by git) keeps each build's APK and
  its notes: `dist/AudioFool-0.8.1.apk`, `dist/notes-0.8.1.md`.
- **Next build:** **0.8.2** for fixes, **0.9.0** for new features. Confirm the number with the
  user (they called 0.7.1's version row, 0.7.4's cover menu rows and 0.8.1's sign-in fixes).
  versionCode 42 next. The logo menu shows the version (`BuildConfig`); ThemeLab's menu
  pictures use a fixed "0.7.0", so a bump changes no picture.
- **The routine the user expects**, when they say to build:
  1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
  2. `./gradlew :core:test :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleRelease`
     (with the environment under *This PC's quirks*). `assembleRelease` stops unless AudioFool's
     Last.fm key is in `gradle.properties`.
  3. Copy `app/build/outputs/apk/release/app-release.apk` to `dist/AudioFool-<v>.apk`.
  4. Check the signer (`apksigner verify --print-certs`: SHA-256 `1fdefe0a…be2c21`), and that
     the APK has the real Last.fm key, not the emulator's dummy: compare the release
     `BuildConfig.java` with `gradle.properties` in the shell, never printing either.
  5. Install it on the emulator and look at what changed.
  6. The user says **"Show <v> notes"**: show them in chat, written for the user (what changed,
     then a numbered *To try on the S24* / *To try on the M21*), and save them as
     `dist/notes-<v>.md`.
  7. The user plugs each phone in (see *Reaching the phones*): `adb -s <serial> install -r
     dist/AudioFool-<v>.apk`, which keeps the library and settings. Check `versionName` after.
  8. Record it in `design/progress.md` (Sessions, Decisions, the test lists) and here; commit
     and push.
- **Before the first Gradle run:** read *This PC's quirks* (Android Studio's JDK, the Zscaler
  trust store, the loopback directory, Gradle with the sandbox off).

## What the recent builds did

In short; the user's calls, the reasoning and what was checked are in
`design/progress.md`. Most of 0.6.0–0.7.4's phone checks are still unticked in both lists; they
don't block anything.

- **On `main`, after 0.8.1** (2026-11-05, not built yet): **a shuffle button on Artists**
  (spec §4.1). `LibraryShuffle` (core: the library in its own order, unreachable songs left
  out), `Player.shuffleAll` (Shuffle on, a random start), `ArtistsHeaderAction` with Settings'
  `IconButtonSmall`. Artists stays; the mini player shows the song. The queue can now be the
  whole library (27,115 on the M21), so `Player.save` encodes the resume file (about 3 MB
  there) off the main thread, newest save only, and the restore resolves paths off it too.
  Checked with the tests, ThemeLab and the emulator; the M21's full library is on its list.
- **0.8.1** (2026-11-05, over USB): **Last.fm signs in with the username and password**
  (spec §4.5, §7). `LastFmApi.getMobileSession` (a signed POST over HTTPS), `LastFm.connect`,
  with AudioFool's own key built in (`BuildConfig.LASTFM_*`, from `gradle.properties`;
  `preReleaseBuild` stops without it). The password is never stored, nor in saved state
  (`remember`); the form offers it to the password manager. A session is saved with the key it
  was made with, so a phone connected earlier with a typed-in key stays connected. The browser
  approval and the key fields are gone from the app; core keeps `getToken` / `getSession`
  (ported). The emulator shows only the form: only the user signs in, on the phones.
- **0.8.0** (2026-11-04, the last on GitHub): **Listening History** (spec §4.7), the desktop's
  ported with its 17 tests (`ListeningJson`, `ListeningMatch`, `HistoryTime`, `LastFmApi`'s
  unsigned GET reads). From the logo menu and the cover menu (`AppMenuRows`' first row).
  `AudioFoolApp.history` holds it so back from a row finds it as it was (`open()` from a menu
  fetches afresh, `showing()` only if nothing was). The period switch is 7D · 1M · 3M · 6M · 12M
  · All; a song row opens `Route.Album(…, song)` with the song selected. The desktop's token
  files were copied again (they had gained `listeningHistory.periodGap` and `genius.*`). If a
  list ever comes back empty or wrong, read `ListeningJson` against Last.fm's real answer.
- **0.7.4** (2026-11-03): the cover menu ends with the logo menu's rows (`AppMenuRows`).
- **0.7.3** (2026-11-03): the S24's pop at the first Play with BP on (the first stream after a
  take muted 500 ms, `COLD_SETTLE_MS`); the M21's "No Output" (only a whole-number FiiO `type`
  counts, `OutputNames.fiioJackOf`; Debug's FiiO jack row shows what FiiO last sent). The user
  reported both fixed.
- **0.7.2** (2026-11-02): the round buttons without the desktop's molded lip (`RoundButton`).
- **0.7.1** (2026-11-02): the logo menu in alphabetical order with the version at its foot;
  Now Playing's readout 20 dp higher on the S24 (`nowPlaying.readoutLift`).
- **0.7.0** (2026-11-02): rescan progress in the ARTISTS and MUSIC FOLDERS headers
  (`RescanOutcome`, `scanHeaderText`, `rescan(asked = true)`).
- **0.6.0** (2026-11-02): Now Playing's cover menu and Analyze (the desktop's spectrogram and
  opinion, ported); the readout gives the bit depth before the rate.

## Background worth knowing (2026-10-30, still true)

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
- **The test lists** in `design/progress.md` hold only what is still open, newest build first,
  then by subject (sorted on 2026-10-30; the "seen in daily use" items under them the user
  confirmed with "All fine" that day). Each build adds its own section at the top. The
  per-version lists with every result up to 0.5.1 are in `design/progress-archive.md`. Don't
  block on any of them.

## How the user works

- Sends a few UI requests at a time, often several in one message, and says when to release:
  build them all into one version, then show the release notes and ask before publishing.
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
- Plugs a phone in when asked, with debugging on. Its first connection needs "Allow USB
  debugging" on the phone (both done by 2026-11-05).
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

- **Both phones reach adb over USB when the user plugs them in** (one at a time, usually);
  builds are installed this way since 0.8.1. Check `adb devices -l` first: when the emulator is
  up too, name the device (`-s` or `-t`).
- **The M21** (plugged in 2026-10-25, 2026-10-26 and 2026-11-05). If adb doesn't list it and
  Windows shows no FiiO device (VID 2972), ask for: USB debugging on, the USB notification set
  to "File transfer", "Allow" on the prompt, a data cable (on 2026-11-05 it showed up once
  "Allow" was tapped). Its screen:
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
  readings from Statistics > Debug (Playback). Never type a pairing code for the user. **Over
  USB the S24 works** (2026-11-05, the user turned debugging on and allowed this PC): adb serial
  `R5CWC28ETAW`, `model:SM_S928U`; 0.8.1 went on with `adb -s R5CWC28ETAW install -r`. Pick it
  with `-s` when the emulator is up too. Builds reach it this way now (no GitHub releases).
- **The emulator** holds the sample music: `/sdcard/Music` (with `Long/`: 200 artists × 2
  albums of silent MP3s; `Gapless Live/`: three songs of exactly 12.000 s; `Waveform Test/`: a
  60 s pink-noise FLAC with swells and a near-silent dip at 24–27 s, for the seekbar's shape)
  and, on the SD card `/storage/0000-0000/Music`, part of the sample set and `SD Card Band/`:
  two 3-minute FLACs for pulling the card mid-song. **Those two are damaged again** (ffmpeg
  fails at the start of both, found 2026-10-26): rebuild them before relying on them. When last
  noted (0.2.6), the app there had Bit-Perfect saved on, Repeat All and Shuffle on, and Last.fm
  not connected. The AVD sometimes exits on its own (exit code 4): start it again. The app's
  uid there is 10236 (for `dumpsys audio`).
- `dist/` (ignored by git) holds every build's APK, its notes (`notes-<v>.md`, since 0.8.1) and
  the M21's captured reports (`m21-*.txt`). An APK there carries AudioFool's Last.fm key: it
  stays on this PC and the phones.
- **Last.fm signs in with the user's own username and password** (after 0.8.0), which only
  the user types, on the phones: never sign in for them, on the emulator either. The emulator
  shows the form, the not-connected Listening History screen, and nothing past that. Without
  AudioFool's key in `gradle.properties` (below), a throwaway release for the emulator can take
  a dummy one on the command line:
  `-Paudiofool.lastfm.apiKey=<32 zeros> -Paudiofool.lastfm.secret=<32 zeros>`; never copy
  that build to `dist/` or give it to the user.

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
| Last.fm | `core/.../scrobbling/` (ported: `LastFmApi`, `PlayTracker`, `ScrobbleEntry`, `ScrobbleQueue`, `LastFmScrobbler`; the phone's own `getMobileSession`); the app's `lastfm/LastFm.kt` (the username-and-password sign-in, what Settings shows, a `PlayWatcher`) and `LastFmStore.kt` (Keystore-encrypted key, secret and session) |
| **AudioFool's Last.fm key** | `~/.gradle/gradle.properties`: `audiofool.lastfm.apiKey=` and `audiofool.lastfm.secret=` (32 hex characters each, the user's own API account at last.fm/api/accounts). **Outside the repo**, put there by the user. `app/build.gradle.kts` makes them `BuildConfig.LASTFM_API_KEY` / `LASTFM_SECRET`; `preReleaseBuild` stops a release without them. Check they're set with `grep -c '^audiofool.lastfm.apiKey=..*'`, never by printing them |
| Listening History | `core/.../scrobbling/ListeningHistory.kt` (periods, records, `ListeningJson`, `HistoryTime`) and `ListeningMatch.kt` (ported); the app's `lastfm/ListeningHistory.kt` (model, `HistoryRows`, `historyTarget`) and `ui/ListeningHistoryScreen.kt`; ThemeLab's made-up answers in `ThemeLabFixtures.history()` |
| Analyze (0.6.0) | `core/.../analysis/` (ported: `Fft`, `SpectrumScale`, `SpectrumAccumulator`, `QualityOpinion`); the app's `playback/TrackAnalyzer.kt` (BASS, lets go at an eject) and `ui/AnalysisScreen.kt`; the cover menu is `ArtMenu` in `NowPlayingScreen.kt`, sharing `MenuSurface` / `MenuRow` with the logo menu |
| Statistics | `core/.../library/LibraryStatistics.kt` (ported), `core/.../playback/ListeningTime.kt`; the app's `playback/Listening.kt` (a `PlayWatcher`) and `ui/StatisticsScreen.kt` (Statistics, and `DebugScreen` with the Playback and Debug panels) |
| What is heard, for both | `playback/PlayWatcher.kt`: `Player` tells its `watchers` the heard track and place every 250 ms while playing |
| BASS 2.4 for Android (vendored unchanged) | `third_party/bass/`: `libs/arm64-v8a`, `libs/x86_64`, `java/com/un4seen/bass` (added to the app's source sets in `app/build.gradle.kts`), licence texts. Where each zip comes from: README |
| Screens | `ui/AppRoot.kt` wires them; `Navigation.kt` (back stack, predictive back), `LibraryScreens.kt` (Artists, Albums, Album), `SearchScreen.kt`, `SettingsScreen.kt`, `ArtistIndexStrip.kt` (Artists' letter strip), `NowPlayingScreen.kt` (with `SwipeToSkip`), `StatisticsScreen.kt` (Statistics and Debug; `Figures` and `StatBar` shared with Listening History), `ListeningHistoryScreen.kt`, `AnalysisScreen.kt`, `MiniPlayer.kt`, `LogoMenu.kt` (`AppMenuRows`, shared with the cover menu), `Parts.kt` (the shared parts, `AlbumArt`) |
| Launcher icon | `res/mipmap-anydpi/ic_launcher.xml`; the foreground PNG is the desktop's `icon_preview.png` scaled with ffmpeg (228 px in a 432 px canvas) |
| TagLib 2.3.2 (C++, vendored unchanged) | `third_party/taglib/`; the JNI bridge is `app/src/main/cpp/tagreader_jni.cpp` |
| Selawik font | `app/src/main/res/font/` (Regular, Semibold, Bold), licence in `third_party/selawik/` |
| Token files | `design/theme-tokens.json`, `theme-tokens.dark.json` (the desktop's, unchanged: `C:\MusicPlayer\design\theme-tokens.json` and `design\ps2-dark\theme-tokens.dark.json`, last copied 2026-11-04; copy both again when the desktop's change), `design/mobile-tokens.json`. The build copies all three, and `PlayStation-Logo-1994.png`, into the app's assets (`copyAppAssets`); never copy them by hand |
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

The routine is under *Starting the next session*. Since 0.8.1 a build goes to each phone over
USB (`adb -s <serial> install -r dist/AudioFool-<version>.apk`), **never to a GitHub release**:
the APK carries AudioFool's Last.fm key. Keep the notes beside it (`dist/notes-<version>.md`),
then add what only the phones can show to *To test on the S24* / *To test on the M21* in
`design/progress.md`. Up to 0.8.0 it was `gh release create … --prerelease`.

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
- With the logo menu open (alphabetical since 0.7.0's follow-up): Listening History at about
  (250, 280), then each row 126 px lower: Rescan Library (320, 405), Settings (320, 531),
  Statistics (320, 657), Theme (320, 783), Version (320, 909), which does nothing. **BACK on Artists leaves the app**: later taps land on the home screen. Release builds log no `AudioFoolScan` lines, so Statistics' track and format
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

- **An APK built with AudioFool's Last.fm key must not go to GitHub** (or anywhere public).
  Claude's safety check blocks the upload as credential leakage (it did for 0.8.1); don't look
  for a way round it. Builds go over USB.
- **`MSYS_NO_PATHCONV=1` stops Git Bash converting `/c/...` paths too**: a Windows program
  (ffmpeg) then can't open them. With it set, give files as `C:/...`.
- **A ThemeLab picture of a screen's end**: `TitledScreen` (Settings, Listening History) takes a
  `ScrollState`; pass `rememberScrollState(Int.MAX_VALUE)`, which settles at the bottom.
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
- **A queue can be the whole library** (Artists' shuffle button, 0.9.0): 27,115 songs on the
  M21. Anything per queue entry on the main thread (the resume save, the restore's lookups,
  a list built at each song change) costs that many; keep such work off the main thread or
  proportional to what's near the song playing.
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
- **Popups need their own position near an edge**: Compose's offset `Popup` doesn't keep itself on screen; the cover menu uses a `PopupPositionProvider` (`AtFinger`) that clamps it, allowing for the shadow room round `MenuSurface`.
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
