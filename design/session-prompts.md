# AudioFool Android — build session prompts

Five Claude Code sessions in `C:\AudioFool-Android`, in order. Paste one prompt per session. Each one assumes the previous one is done and recorded in `design/progress.md`.

Before session 1: copy this handoff folder's contents into `C:\AudioFool-Android` (an empty clone of `github.com/mpaulrenlund/AudioFool-Android`) and install Android Studio (for the SDK, NDK and emulator images).

No phone is connected to the PC. Each session verifies on an emulator with sample music, then puts a signed APK on a GitHub pre-release for the user to install, and adds what only a real device can show to the *To test on the S24* and *To test on the M21* lists in `design/progress.md`. Logs can't be read off the phones, so anything the user has to report back (counts, timings, rates, mixer attributes) is shown on a debug screen in the app.

---

## Session 1 — Project, theme and library

```text
We're building AudioFool for Android, the mobile version of the desktop app in
C:\MusicPlayer. Read CLAUDE.md first, then design/android-spec.md (§1, §2, §8) and
design/mobile-tokens.json. Skim C:\MusicPlayer\CLAUDE.md and its README for how the
desktop works; its src/AudioFool.Core holds the logic we port.

This session sets up the project, the theme and the library. Screens come next session.

1. The project: Gradle, Kotlin, Compose, minSdk 33, target the newest installed SDK.
   Package name com.mpaulrenlund.audiofool unless you see a reason not to. Portrait
   only, edge to edge. A git repo on main with origin
   github.com/mpaulrenlund/AudioFool-Android; .gitignore for build output; the design/
   folder and the logo tracked. A release keystore outside the repo: tell me where it
   is and how to back it up. Every APK I get, test builds included, is signed with it,
   so each one installs over the last.
2. Font: propose bundling Selawik (spec §1) and ask me.
3. Theme: load both desktop token files and mobile-tokens.json into a Compose theme,
   with every token reachable by its JSON path. Dark/Light saved; Dark when nothing is
   saved; switching applies at once. Unit test: both colour files have the same keys.
4. Screenshot tests (Roborazzi or Paparazzi; pick one and say why) that render a
   composable in both themes at 384 × 832 and 375 × 667 dp. This is our ThemeLab.
   Start with a token swatch sheet.
5. Storage: the all-files-access permission flow, and the music folder list (plain
   paths, include/exclude per folder), saved in the app's settings.
6. The library: scan the folders, read tags and embedded/folder art, and keep a cache
   so a cold start shows the library at once and a rescan reads only changed files
   (desktop FileStamp, LibraryCache). Choose the tag reader (TagLib through the NDK, or
   a JVM library); it must read every format in the desktop's AudioFormats, DSF and
   DFF included. Ask me before adding it.
7. Port from AudioFool.Core, with their tests: AudioFormats, ArtistOrder, ReleaseDate,
   SortRules, LibrarySearch, and the unreachable-folder handling (a missing SD card is
   never a deleted library).
8. Create HANDOFF.md (the build/install/verify loop, as the desktop's does) and
   README.md.

Verify: unit tests pass; screenshot tests render both themes at both sizes; on an
emulator with sample music, the app scans its music folders, and a debug screen shows
the artist, album and track counts and how long the cold and warm starts took. Then
a signed build on a GitHub pre-release (confirm the tag and notes with me first), and
start the "To test on the S24" list in design/progress.md: the same counts and start
times against my full library.

When done, list what changed and anything that differs from the spec, add the
decisions to design/progress.md, commit and push.
```

---

## Session 2 — Library screens, navigation and search

```text
Continue AudioFool for Android. Read CLAUDE.md, HANDOFF.md, design/android-spec.md
(§2, §3, §4) and session 1's notes in design/progress.md. Compare everything with
design/screens/.

1. Shared parts from spec §2: panel, panel header, rows (normal, selected, now
   playing), chip, round button, the Dark title glow behind the top of each screen.
2. Navigation: Artists → Albums → Album, plus Search and Settings, with system back
   and predictive back. No back buttons.
3. Artists (§4.1): the logo menu (Settings, Theme, Statistics), the search field that
   opens Search, rows with the newest album's thumbnail.
4. Albums (§4.2) and Album (§4.3), including the # column's triangle slot.
5. Search (§4.4): three groups, updated as you type, using the ported LibrarySearch.
6. Settings (§4.5): Theme, music folders (refresh, include/exclude, Add Folder…).
   Show the Bit-Perfect and Last.fm rows in their off state; they're wired up later.
7. The mini player (§3), built but hidden until something plays. Give it a debug
   preview state so it can be checked now.
8. Art: thumbnails cached on disk and decoded at display size. Scrolling all artists
   and a long album list has to stay smooth.

Verify: screenshot tests of every screen in both themes at both sizes, compared side
by side with design/screens/ (list every difference and fix it or ask). On an
emulator with sample music: scrolling a long artist list, a removed or unmounted
folder showing as unavailable rather than gone, and a theme switch on every screen.
Then a signed build on a pre-release, and add to the S24 list: the real library,
scrolling the full artist list, removing the SD card, and a theme switch on every
screen.

When done, list what changed and anything that differs from the spec, record the
decisions in design/progress.md, update HANDOFF.md, commit and push.
```

---

## Session 3 — Playback

```text
Continue AudioFool for Android. Read CLAUDE.md, HANDOFF.md, design/android-spec.md
(§3, §5, §6.1, §6.3) and the earlier notes in design/progress.md. The desktop's
playback is in C:\MusicPlayer\src\AudioFool.Core\Playback.

1. BASS for Android: the core library and the add-ons for every format the desktop
   plays (see the desktop's BassRuntime). List the files and where they come from; if
   you can't download them, tell me exactly which to fetch. Vendor them in the repo as
   the desktop does, and note BASS's licence terms in README.
2. The output stage: BASS decode channels feeding an AudioTrack opened at the track's
   own sample rate and bit depth where the route allows. Gapless between tracks.
   DSD converted to PCM for now (DoP comes in session 4).
3. A foreground media service with a media session: lock screen and notification
   controls with art, headset and Bluetooth buttons, audio focus, pause when
   headphones are unplugged.
4. Port PlayOrder (shuffle, repeat, repeat one), ReopenPoint (resume paused where you
   left off), Waveform and OutputReadout, with their tests.
5. Now Playing (§5): the layout exactly as specified, including the art size rule,
   the right-aligned quality lines, the waveform seekbar (computed in the background,
   cached), the controls row (Repeat / Shuffle / BP on the left, the transport on the
   right), and the centred output readout. BP shows its off state.
6. Wire the mini player and every "tap a song" in the library screens to the queue.

Verify: unit tests; screenshot tests of Now Playing and the mini player in both themes
at both sizes against design/screens/. On an emulator: play FLAC 16/44 and 24/96,
MP3, AAC, ALAC, Opus and DSD; gapless across a live album; the lock screen and
notification; reopening the app restoring the queue. Show on the debug screen what
rate and format each track was opened at. Then a signed build on a pre-release, and
add to the S24 list: the same formats from my library, gapless, the lock screen and
notification, a Bluetooth headset's buttons, unplug pausing, and the rates the debug
screen shows.

When done, list what changed and anything that differs from the spec, record it in
design/progress.md, update HANDOFF.md, commit and push.
```

---

## Session 4 — Bit-Perfect, DSD over DoP, and an M21 test build

```text
Continue AudioFool for Android. Read CLAUDE.md, HANDOFF.md, design/android-spec.md
(§6) and the earlier notes in design/progress.md. The desktop's Bit-Perfect is in
C:\MusicPlayer\src\AudioFool.Core\Playback (OutputChain, DopRule, OutputReadout) and
README's output modes section.

Before building, research and tell me what each device can actually do, with sources:
the Android 14+ bit-perfect mixer API on a USB DAC from the S24, and FiiO's system SRC
bypass on the M21 (what a third-party app has to do, and whether it can confirm the
output). Then:

1. Output routes: detect USB DAC, Bluetooth, the phone speaker and the M21's headphone
   out, and follow changes (plugging in, Bluetooth connecting) during playback.
2. S24 + USB DAC: Bit-Perfect on uses setPreferredMixerAttributes with
   MIXER_BEHAVIOR_BIT_PERFECT at each track's rate, re-applied when the rate changes,
   without cutting the end of the previous track.
3. M21: whatever the research showed.
4. Bluetooth and speaker: BP greyed out and not tappable (§6.2), in Now Playing and
   Settings.
5. The BP button (§6.4) and the Settings row, saved, plus the output readout for
   every route.
6. DSD: DoP when the output is bit-perfect and the DAC takes it (port DopRule);
   otherwise PCM.
7. A signed build (the session 1 keystore) on a GitHub pre-release for me to install
   on the S24 and the M21. Confirm the tag and notes with me first. Start the "To test
   on the M21" list in design/progress.md.

Verify: unit tests; screenshot tests of BP's three states in both themes; on an
emulator, BP greyed on the speaker and on any other route it can simulate. Show the
route, the readout and the mixer attributes on the debug screen so I can report
them. Add to the S24 list: through a USB DAC, a 24/96 FLAC and a 16/44 FLAC with
Bit-Perfect on and off, with the readout and mixer attributes for each; DSD64 over
DoP; BP greyed on Bluetooth and the speaker; switching routes mid-track.

When done, list what changed and anything that differs from the spec, record it in
design/progress.md, update HANDOFF.md, commit and push.
```

---

## Session 5 — Last.fm, Statistics, polish and release

```text
Finish version 1 of AudioFool for Android. Read CLAUDE.md, HANDOFF.md,
design/android-spec.md (§4.5, §4.6, §7) and the earlier notes in design/progress.md,
including my S24 and M21 test results.

1. Fix anything from the S24 and M21 test lists.
2. Last.fm (§7): port LastFmApi, LastFmScrobbler, PlayTracker and ScrobbleQueue with
   their tests. Connect asks for the API key and secret, then opens the browser for
   approval; all three are stored encrypted. Now-playing updates and scrobbles,
   including offline queueing.
3. Statistics (§4.6): library totals, formats and listening time, built from the
   existing parts. Show me screenshots of both themes before you finish it.
4. Edge cases on both sizes and in both themes: very long names, a 100+ track album,
   an empty library, a removed SD card (while idle and while playing from it), a
   phone call interrupting playback, a font scale of 1.3, and a cold start with the
   full library.
5. Docs: README (features, BASS licence, how to build), HANDOFF.md, and
   design/progress.md up to date.
6. Release 0.1.0: a signed APK on a GitHub release. Confirm the version and notes
   with me before gh release create.

Verify: all unit and screenshot tests pass; every screen in both themes at both sizes
matches design/screens/ or the differences are listed; the edge cases above on an
emulator where it can show them, and the rest on the S24 list; Last.fm scrobbles
appear on my profile.
```
