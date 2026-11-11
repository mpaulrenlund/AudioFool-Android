Traps worth not rediscovering: playback, the output stage, scanning, ThemeLab, JNI, the emulator and shipping. Moved out of HANDOFF.md on 2026-11-11, unchanged except for cross-references.

## Traps worth not rediscovering

- **An APK built with AudioFool's Last.fm key must never go anywhere public.** GitHub releases
  are fine only because the repo is private (since 2026-11-05); check `PRIVATE` before each
  upload. While the repo was public, Claude's safety check blocked 0.8.1's upload as a
  credential leak; if the repo is ever public again, don't upload and don't look for a way
  round it: tell the user.
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
- **The emulator can't reach genius.com from this PC**: Zscaler re-signs its certificate, and
  Android doesn't trust Zscaler's root, so the Genius screen says "Couldn't reach Genius:
  …Trust anchor for certification path not found." That's Zscaler, not the app. A JVM test can
  reach it (the Gradle trust store has Zscaler's root), but Cloudflare challenges desktop
  Java's requests about 1 in 4, so a JVM probe's 403s say nothing about the phones.
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
- **ThemeLab's pictures that wait on an effect can, rarely, be taken too early**: Search's
  text cursor (the focus) and the index strips' pressed pictures (the list scrolled to the
  letter). Once (2026-11-09) a record run changed all 32 of them, with nothing of theirs
  touched; five runs after it gave them back identical and the cause wasn't found. If
  `verify` fails on only those, or a record changes only those, run it again before believing
  it, and never commit them changed.
- **A small button's touch area is already 48 dp.** Compose widens any touch area smaller than
  that by itself, to the nearest one where two overlap, so a 34 dp button needs no bigger box
  (`NowPlayingTouchTest`). Read a button's layout size as its look, not its reach.
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
  check) is shown without a library there, and `LocalArt` is null (placeholders). Now Playing
  takes a made-up cover instead (`coverForPictures`, a `Bitmap` drawn in `ThemeLabFixtures`),
  which also gives it the cover's colour; without one its pictures show the theme's colours.
- **CSS min-height** in the mockups is the content's, inside the padding: put
  `heightIn(min = ...)` after `padding(...)`.
- **Token arrays are keyed by `id`**: `album.columns.number.width`, not `columns.0`.
- A pushed file on a FAT card can be re-read once after a remount (FAT keeps write times to
  2 seconds), which the scan counts as "read", not "removed". Harmless.
