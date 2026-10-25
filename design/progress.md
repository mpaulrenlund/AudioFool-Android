# AudioFool Android — Progress

Update this at the end of every session: tick off what's finished, note anything left open, and record every decision that changed or filled in the spec.

## Sessions

- [x] **1. Project, theme and library** (spec §1, §2, §8) — 2026-10-24
- [x] **2. Library screens, navigation and search** (§2, §3, §4) — 2026-10-24
- [x] **3. Playback** (§3, §5, §6.1, §6.3) — 2026-10-24
- [x] **4. Bit-Perfect, DSD over DoP, and an M21 test build** (§6) — 2026-10-24
- [ ] **5. Last.fm, Statistics, polish and release** (§4.5, §4.6, §7)

## Decisions made in the design

The user made these while the mockups were designed (October 2026). The spec already reflects them.

- **Faithful layout**, not the bolder variant: the desktop's panels, rows and molded round buttons, scaled for touch.
- **No on-screen back buttons** anywhere, including Now Playing's close arrow. System back and swipe-down do it.
- **The logo is the menu button** (Settings, Theme, Statistics). No ⋮ menu; the search field takes the space.
- **Artists show their newest album's art.**
- **No library sizes in GB** on Artists, Albums or Search. Statistics shows the total size.
- **Album table is # · Song · Time.** No Kind or Disc columns.
- **Search songs show no format column.**
- **Now Playing:** no Like button and no "Hi-Res Lossless" label. The track count reads "3 / 10", right of the title. Quality is two right-aligned lines: format · sample rate, then bit depth · bitrate. The output readout sits centred under the controls.
- **Controls for a right thumb:** Repeat, Shuffle and BP stacked on the left (34 dp, smaller than the transport); Previous, Play/Pause and Next on the right, centred on Shuffle and sized to the screen height.
- **BP** replaces the Bit-Perfect chip at the top: same size as Repeat and Shuffle, labelled "BP", green when on and red when off (Light's red is the mobile token `#B3261E`, since the desktop's Light "off" is yellow and unreadable on the button face). Greyed out and not tappable on Bluetooth and the speaker.
- **Mini player** has Previous, Play/Pause and Next.
- **Theme** defaults to Dark and switches by hand.

## Decisions made for the build

Session 4 (2026-10-24):

- **Research first** (shown to the user before building): Android 14+'s
  `setPreferredMixerAttributes` with `MIXER_BEHAVIOR_BIT_PERFECT` is optional for phone
  makers (AOSP: "optional", AIDL HAL only); recent Samsung reports (Z Fold 7 on Android 16,
  S23, A53) say Samsung lists no bit-perfect entries, so the S24 may not offer one. Only
  the plain mixer entries are required for USB. FiiO documents "no SRC for third-party
  apps" only for the old X5 III; reviews say the M21 bypasses Android's resampling for
  every app. An app can't read the hardware's rate or `dumpsys` on either device.
- **USB on Android 14+ (the S24):** with BP on, the bit-perfect mixer entry at the song's
  rate and channels, in the narrowest whole-number format that holds its samples (16-bit
  as 16, else 24, else 32; never narrower than the song); **else an ordinary entry at the
  song's rate, "rate matched"** (the user's call: no resampling, but still the mixer, so
  never called bit-perfect); else shared. `MixerChoice` in core, with tests. The
  `AudioTrack` uses the chosen entry's own `AudioFormat`, as Google's sample does, so an
  index channel mask (seen on a Fold 7) still matches.
- **The M21's way, and USB on Android 13** (the M21 with a DAC): with BP on, the song's own
  rate in whole-number samples (float decoders go out as 32-bit, then 24-bit) at full
  volume. The readout calls it bit-perfect only when `getNativeOutputSampleRate` shows the
  system output at the song's rate; "resampled" otherwise. On the emulator it says
  "Headphones · 48 kHz (resampled)", which is the honest answer there.
- **Greyed out:** Bluetooth, the speaker, anything else (HDMI, the earpiece), and a USB
  device that lists no mixer entries at all. The setting keeps its value; the BP label
  and the Settings dot show it, dimmed.
- **DoP** (desktop `DopRule`, ported with its tests): only on the bit-perfect mixer, at a
  rate it offers in 24- or 32-bit stereo. BASSDSD makes the DoP samples
  (`BASS_DSD_DOP`, float, which holds them exactly). A DoP song that doesn't get a
  bit-perfect output is reopened as PCM and the rate isn't tried again on that device, as
  the desktop does. DoP never joins PCM. Rate-matched is not bit-perfect, so DSD goes as PCM there.
- **Android caps `AudioFormat` at 192 kHz on the emulator** (a hidden limit set per device):
  DSD128 over DoP needs 352.8 kHz, so it may not be possible on the S24 either; it falls
  back to PCM if not. On the S24 list.
- **Route changes and the setting reopen the output** at the place heard (`ReopenPoint`),
  the decoder too (DSD may switch between DoP and PCM). A shared output isn't reopened
  for a route change: Android moves it by itself.
- **No ducking on a bit-perfect path:** a navigation prompt pauses the music and it
  carries on afterwards (bit-perfect means nothing changes the samples).
- **The readout** puts the route first: "USB · 96 kHz / 24-bit (bit-perfect)",
  "Speaker · Shared · 48 kHz (resampled)". The shared rate is now
  `AudioTrack.getNativeOutputSampleRate` (the output media actually goes to, Bluetooth's
  too), not the primary mixer's property. No Bluetooth codec: the codec API is system-only.
- **The Settings row's chip toggles Bit-Perfect** (the row too), greyed in the same cases as
  BP, with `status.bitPerfectOn` / `Off` for the dot. Saved in SharedPreferences
  (`bitPerfect`, off when unset).
- **`MODIFY_AUDIO_SETTINGS`** added to the manifest (needed for the mixer calls; a normal
  permission, no prompt).
- **A "Pretend route" switch on Statistics > Playback** (not saved; the emulator has no USB
  DAC or headphone jack): Headphones, or USB with a made-up list of bit-perfect entries that
  is never sent to Android. It's how the bit-perfect paths, DoP and the reopen were played
  through on the emulator. It ships in the test builds; it changes nothing until tapped.
- **Debug numbers** (Statistics > Playback) for the user to report: the route and the
  device's rates and formats, Bit-Perfect and its way here, how the output was opened, the
  system output's rate, whether Android says it would play the song's format direct, the
  USB device's mixer entries grouped by behaviour, and the mixer set (read back from
  Android) with the last attempt.

Session 3 (2026-10-24):

- **BASS 2.4 for Android, vendored unchanged** in `third_party/bass` (as the desktop keeps
  its DLLs in git): the core and the add-ons for every format the desktop plays, for
  `arm64-v8a` (the phones) and `x86_64` (the emulator), with the Java bindings. `bassmix`
  is left out: the desktop needs it for gapless, here the output stage joins tracks
  itself. **WMA can't play** on the phone: un4seen publishes `basswma` for Windows only.
  TAK and DTS can't play on either. Licence terms in README.
- **No media library.** The media session, the notification and audio focus use
  Android's own `MediaSession`, `Notification.MediaStyle` and `AudioFocusRequest`, so
  nothing new comes in (no Media3).
- **The output's format follows the file:** an `AudioTrack` at the track's own rate;
  16-bit sources as 16-bit, 24- and 32-bit sources as 24-bit packed (BASS decodes to
  float, which holds 24 bits exactly), lossy files as float. If a format won't open, it
  falls back to float, then 16-bit. Songs asking for the same format join in the same
  `AudioTrack` (gapless); one in another format reopens it after the last song has been
  heard out ([TrackEnd], ported, with the output treated like the desktop's exclusive mode).
- **DSD is converted to PCM at 88.2 kHz** (`BASS_CONFIG_DSD_FREQ`, the desktop's fallback
  rate), as float. DoP is session 4 (above).
- **What's shown is what's heard:** the desktop's `HearingTimeline` is ported; the
  `AudioTrack`'s playback head says which song and place are coming out, so the screens
  change song at the join, not half a second early.
- **The media session lives with the queue** (`SystemControls`), not with the service, so a
  headset or Bluetooth button resumes a restored queue before anything has played; the
  service holds the notification and the foreground.
- **Resume** (spec 6.1) is new for the phone (the desktop reopens on the last song at
  0:00): `no_backup/resume.json` holds the queue's paths, the song, the place heard,
  shuffle, repeat and the shuffled order, saved with `SafeFile` on every song change,
  pause, seek and mode change, every 10 s while playing, and when the app goes out of
  sight. Restored paused once the library is loaded; songs no longer in the library drop
  out. `PlayOrder.restore` is added for the shuffled order.
- **The end of the queue** goes back to its first song, paused, so Play plays it again.
  The desktop clears its now-playing bar; the mini player stays here.
- **Now Playing's "3 / 10"** counts in play order while shuffled (the 3rd song heard of 10),
  in album order otherwise.
- **The readout** (spec 6.3) is the shared output's: the system mixer's rate, and
  "(resampled)" when the song's rate differs, e.g. "Shared · 48 kHz (resampled)". The
  mockup's "/ 32-bit" is left out: Android doesn't tell an app the mixer's bit depth, and
  the desktop's readout leaves the depth out when the device doesn't say. Session 4 adds
  the route.
- **Seeking:** a tap seeks at once; a drag moves the bar and seeks on letting go.
  Previous restarts the song after 3 s, as on the desktop. Repeat cycles Off, All, One.
- **Swipe down** closes Now Playing after `nowPlaying.swipeCloseDistance` (96 dp, new);
  the screen follows the finger. The big art's placeholder is the desktop's
  `albumHeader.placeholderIconSize`.
- **Songs that won't open** (a removed SD card, WMA) are passed over to the next in the
  play order and listed in Statistics > Playback > Skipped.
- **Debug numbers** (Statistics > Playback): BASS's version and add-ons, the readout, gapless
  joins, reopens and **underruns** (the output running dry while fed: what a gap would be),
  and for each song the format its decoder asked for and the output opened. The mini
  player preview switch is gone.
- **Previews in ThemeLab** use the mockup's song (24/96 FLAC, 1,140 kbps, 3 / 10, 2:18 of
  5:32) and its made-up waveform, so the pictures line up with `design/screens/`.

Session 2 (2026-10-24):

- **Debug numbers live in Statistics** (the user's call): logo > Statistics shows the
  library line and a Debug panel. Session 5 builds the statistics above them; the Debug
  panel stays at the bottom.
- **Launcher icon: the desktop's icon** (the user's call): the PlayStation logo from
  `icon_preview.png`, unchanged, on white, scaled into the adaptive icon's safe zone.
- **No navigation library.** A small back stack of its own (`ui/Navigation.kt`) with
  `PredictiveBackHandler`, so no new dependency. Screens are named (artist, album title),
  not held, so a rescan shows the new library on an open screen. The gesture shrinks the
  top screen to `predictiveBack.scale` and moves it `shift` dp; committed, it slides away.
- **The row you opened stays selected** when you come back (`state.selectionBg`), which is
  what the mockups show on Artists and Albums.
- **Press feedback** is the desktop's hover colour, `state.hoverOverlay`, while a finger is down.
- **The logo menu** has no mockup: a panel under the logo (`menu.*` tokens) with Settings,
  Theme (with the theme's name; it switches at once and the menu stays open) and
  Statistics, with `shadow.headerArt` under it so it reads as lifted over the list.
- **Unavailable music** (spec 1 says "shown as unavailable"; the desktop only says so in
  its status bar): songs on an unreachable folder are drawn at `disabledOpacity` and can't
  be tapped; an album or artist is greyed when all of its songs are; the folder in Settings
  is greyed and its path line says "Not available". Everything stays listed and browsable.
- **The empty Artists list** says what the desktop's `EmptyStateText` says, pointing to
  Settings (from the logo menu) instead of the desktop's Libraries menu.
- **Search** groups hold the library's own artists and albums, so "3 albums" and "11
  tracks" count all of them, not just the matches. Results are worked out off the main
  thread on an index folded once per library. With an empty query the panel is empty.
- **Covers:** the desktop's `AlbumArtService` rules (first five tracks, then the folder;
  whole pictures first; "no cover" saved only when every file was read, so a missing SD
  card isn't remembered as coverless). Copies at the list size (`albums.thumb`, 64 dp) and
  the album header's (`album.art`), JPEG 90 (PNG with transparency), in `no_backup/art`,
  pruned after 90 days unused; decoded copies in memory up to an eighth of the heap. After
  each scan a background fill makes the list copies, newest albums first.
- **Mini player preview:** until playback exists, a debug switch makes a tapped song show
  as playing (silently), so the mini player and the now-playing rows can be checked.
- **The PlayStation logo** is copied into the assets by the build (`copyAppAssets`, which
  also copies the token files), so the repo's PNG stays the only copy.
- **CSS shadow order:** the first layer of a token is drawn on top, as CSS does
  (`tokenSurface` drew them the other way round before).
- **Settings rows:** the mockups' min-height is the content's, inside the padding.
- New tokens in `mobile-tokens.json`: `menu.*`, `predictiveBack.*`,
  `topBar.searchBarPaddingX`, `panel.headerActionPaddingEnd`, `settings.rowGap`,
  `settings.lineGap`, `settings.statusTextSize` and `album.triangle.strokeOnGrid`.

Session 1:

- New repo `C:\AudioFool-Android` (GitHub `mpaulrenlund/AudioFool-Android`); the desktop repo is untouched.
- BASS for decoding, AudioFool's own `AudioTrack` output stage, and a media session for the system controls.
- All-files access with plain folder paths. Music on internal storage and the SD card; about 26,000 tracks.
- Desktop colour token files copied unchanged; mobile sizes and mobile-only colours in `mobile-tokens.json`.
- Last.fm reuses the desktop's API key, entered in the app, never committed.
- Version 1 includes lock screen and notification controls, headset buttons, gapless, DSD, Last.fm, resume, and a library cache. Playlists are not in version 1.
- **No USB connection to either device** (the user's call, 2026-10-24). Both the S24 and the M21 are tested through GitHub pre-release APKs that the user installs and reports back on. The emulator covers what it can before each hand-over; what only a real device can show goes on the lists below.
- **One signing key from the start** (2026-10-24): every APK the user gets, test builds included, is signed with the same release keystore, kept outside the repo, so each build installs over the last without wiping the cache or settings.
- Ask before big choices only (new dependencies, spec changes, stored data, releases).
- **The repo is public** (the user's call, 2026-10-24). This PC pushes and releases as `marcusrenlund`, a collaborator with write access. Session 3 checks BASS's terms for shipping its libraries in a public repo.

## To test on the S24

### v0.0.5 (session 4: Bit-Perfect)

**Not released:** the user skipped v0.0.5 at the start of session 5 (2026-10-24). These
items carry over to the next build session 5 hands out, which installs over v0.0.4 (same
key). Report from logo > **Statistics** > **Playback**: Output, Route,
Bit-Perfect, Opened as, System output, Direct, USB mixers, Mixer set. A photo of the panel
is fine.

- [ ] **What the S24 offers your DAC.** Plug the DAC in, open Statistics > Playback: report
  **USB mixers** (two lines, "bit-perfect: …" and "default: …", or "none") and **Route**.
  This answers whether Samsung gives the S24 a bit-perfect mixer at all.
- [ ] **24/96 FLAC, Bit-Perfect on and off.** Play it with BP off, then tap BP (Now Playing):
  report Output and Mixer set for each. Expected with BP on: "USB · 96 kHz / 24-bit
  (bit-perfect)" or "(rate matched)"; with BP off: "USB · Shared · 48 kHz (resampled)" or
  similar. Does the DAC's own display (if it has one) show 96 kHz with BP on?
- [ ] **16/44 FLAC, Bit-Perfect on and off.** The same: "USB · 44.1 kHz / 16-bit (…)".
- [ ] **A rate change without cutting the end.** An album or queue going from a 44.1 kHz song
  to a 96 kHz one with BP on: the end of the first song is heard in full, then a short gap
  (the output reopens at the new rate), and the readout changes to 96 kHz.
- [ ] **DSD64 over DoP.** With BP on, play a DSF/DFF: does the DAC show DSD (or DoP), and does
  it sound right (not hiss)? Output should say "USB · 176.4 kHz · DSD over PCM
  (bit-perfect)". If it says "/ 32-bit float … (rate matched)" or 88.2 kHz, it went as PCM:
  report Mixer set. A DSD128 file too, if you have one: it needs 352.8 kHz, which Android may
  not allow (then PCM).
- [ ] **Volume.** With BP on through the DAC, the phone's volume keys: do they change the
  DAC's volume, or nothing? (Bit-perfect leaves the volume to the DAC.) Do notification
  sounds still play, and where?
- [ ] **BP greyed on Bluetooth and the speaker.** Unplug the DAC: BP and the Settings chip go
  dim and can't be tapped; Output says "Speaker · Shared · …". Same with Bluetooth
  connected: "Bluetooth · Shared · … ".
- [ ] **Switching routes mid-song.** While playing with BP on: plug the DAC in (it should move
  to the DAC and reopen bit-perfect at the place it was), pull it out (it pauses, as an
  unplug does), connect Bluetooth (BP goes grey, it plays on Bluetooth).
- [ ] **A navigation prompt with BP on** (through the DAC): the music pauses for it and
  carries on after, rather than getting quieter.

### v0.0.4 (session 3: playback)

Released 2026-10-24 as pre-release
[`v0.0.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.4)
("Playback (session 3 test build)"). Installs over v0.0.3 (same key). Report from logo > **Statistics** > **Playback**.

- [ ] **Formats.** Play a song of each kind you have: FLAC 16/44 and 24/96 (or 24/192),
  MP3, AAC, ALAC, Opus, DSD. Each plays; Playback > Opened shows, per song, what it asked
  for and what the output took, e.g. "96 kHz / 24-bit, 2 ch → output 96 kHz / 24-bit,
  2 ch". Report any where the two halves differ (the output fell back), and any song
  under Skipped.
- [ ] **Gapless.** A live album or a continuous one (Dark Side of the Moon, a DJ mix):
  no gap, click or dip between songs. Playback > Joins should count one gapless join per
  song change and **0 underruns**. A song in another format than the last (16-bit after
  24-bit) reopens the output, with a short gap: that one is expected.
- [ ] **Now Playing.** Tap the mini player: the cover, title, artist, album, "3 / 10", the
  quality lines, the waveform (flat for a moment the first time, then the song's shape),
  the times moving. Tap and drag the waveform to seek. Repeat cycles off / all / one (the
  "1" badge); Shuffle; Previous restarts after 3 s, else goes back. Swipe down or the
  back gesture closes it. Both themes.
- [ ] **Lock screen and notification.** While playing, lock the phone: the cover, title,
  artist, play/pause, previous, next and a seekbar, all working. Same in the notification
  shade. Pause, then swipe the notification away: it goes. Tapping it opens Now Playing.
- [ ] **Bluetooth headset buttons.** Play/pause, next and previous from the headset (and
  from a watch, if you use one). Also after a restart with nothing played yet: the button
  should resume where you left off.
- [ ] **Unplug pausing.** Playing through wired headphones (or a USB-C adapter), pull them
  out: it pauses. Turning a Bluetooth headset off does the same.
- [ ] **Interruptions.** A call, or a voice assistant, pauses it and it carries on after;
  a navigation prompt lowers it briefly; another music app starting stops it.
- [ ] **Resume.** Play an album, seek into a song, pause, swipe the app away from recents,
  open it again: the mini player shows the song, paused; Now Playing shows the same place;
  Play carries on from there, with the rest of the album after it.
- [ ] **The rates.** Playback > Output with the phone speaker, and with Bluetooth, while a
  44.1 kHz and a 96 kHz song play (e.g. "Shared · 48 kHz (resampled)").
- [ ] **Battery and heat** over an hour of playing with the screen off: anything unusual?

### v0.0.3 (the first scan shows the library as it is found)

Released 2026-10-24 as pre-release
[`v0.0.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.3).

- [ ] **First scan.** Statistics > Forget the cache, swipe the app away and open it: the ARTISTS header shows "Reading tags: N of 8,127" and artists appear within a few seconds, filling in as the scan goes. At the end the count goes and Statistics > Last scan has the time. Then open it again: the library is there at once, with no count.

### Session 2 build (`v0.0.2`)

Released 2026-10-24 as pre-release
[`v0.0.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.2).
Installs over v0.0.1 (same key): the library, cache and settings stay. The debug numbers
are now under the logo > **Statistics**.

- [ ] **Icon.** The launcher shows the PlayStation logo on white, not Android's default.
- [ ] **Artists.** Opens on Artists with your 192 artists, each with its newest album's cover. Covers appear as you scroll; Statistics > Covers counts up to 821 as the background fill works through them.
- [ ] **Scrolling.** Fling from the top of Artists to the bottom and back: smooth, no stutter, covers filling in. Same for a long album list. (The emulator can't judge this: its software renderer takes about 30 ms a frame for anything.)
- [ ] **Drill-down and back.** Artist → Albums → Album, then the back gesture: hold it halfway, and the screen underneath shows while the one you're leaving shrinks; let go and it slides away. The artist you opened is still highlighted, at the same scroll position.
- [ ] **Search.** Tap the search field: the keyboard comes up; results update as you type, in ARTISTS / ALBUMS / SONGS. Back closes the keyboard, back again returns. Accents are ignored: "bjork" finds Björk.
- [ ] **Theme on every screen.** Logo > Theme switches at once (the menu stays open to show it); Settings > Theme too. Check Artists, Albums, Album, Search, Settings and Statistics in both themes; the status bar icons are readable in both.
- [ ] **Mini player.** Statistics > Mini player preview on, then tap a song: the mini player shows it, the song's row turns blue with the ▸ triangle, Play/Pause toggles. No sound yet (session 3). Off hides it again.
- [ ] **Start times.** Statistics > Cold start and Warm start (back out, open again) with the new screens. Emulator: 1.3 s cold, 1.1 s warm with 826 tracks.
- [ ] **Session 1 items still open below** (folders, rescan, theme, covers, token sheet), now reached from Settings and Statistics.

### Session 1 build (`v0.0.1`)

Session 1 build (`v0.0.1`). Report from the **Debug** panel at the bottom of the screen.

The S24 has **no SD card slot** and holds a smaller library than the desktop's: **192 artists · 821 albums · 8,127 tracks** (the user, 2026-10-24). The full ~26,000-track library and SD card removal are tested on the M21.

- [x] **Install and access.** v0.0.1 installed, was given access and read the library (2026-10-24).
- [ ] **Folders.** The list starts with the phone's `Music` folder. "Add Folder…" picks another on internal storage, shown as "Internal storage · name" with its path. Unticking hides that folder's artists at once; ticking brings them back without a rescan.
- [x] **Counts.** 192 artists · 821 albums · 8,127 tracks (2026-10-24). Still to report: the Formats line.
- [x] **First scan.** Under 30 seconds for 8,127 tracks (2026-10-24). Exact figure, if wanted: Debug > Last scan.
- [x] **Cold start.** "Very fast" (2026-10-24). Exact figures, if wanted: Debug > Cold start and Cache.
- [ ] **Warm start.** Back out with the back gesture and open it again: Debug > Warm start.
- [ ] **Rescan.** The refresh button in MUSIC FOLDERS: Last scan should read about 0 and reuse every track.
- [ ] **Theme.** The moon chip switches to PS1 Light at once and back; the choice survives a restart; the status bar icons are readable in both.
- [ ] **Covers.** The "Newest album of …" row shows a cover and says whether it was Embedded or Folder.
- [ ] **Token sheet.** Debug > Token sheet: tap through the four pages in both themes; back returns.

## To test on the M21

Session 4 (`v0.0.5`, not released: these carry over to session 5's build), the M21's first playback build. Report from logo > **Statistics** >
**Playback** (a photo is fine).

- [ ] **Install over the last build** and play something from the SD card through the 3.5 mm or
  4.4 mm out: it plays, Now Playing shows the waveform, the notification works. Report
  Route (it should say "Headphones" and the device type).
- [ ] **Bit-Perfect on the headphone out.** BP should be tappable. Play a 44.1 kHz FLAC, then a
  96 kHz one, with BP on: report Output, System output and Direct for each. **The key
  question is System output:** if it follows the song (44.1 kHz, then 96 kHz), FiiO's
  bypass is in the path and Output says "(bit-perfect)"; if it stays at 48 kHz, it says
  "(resampled)". Then the same with BP off.
- [ ] **FiiO's own setting.** Is there a setting on the M21 for the SRC bypass (in Settings or
  the pull-down)? Does turning it on or off change System output?
- [ ] **DSD on the M21.** A DSF/DFF plays (as PCM: Output "… 88.2 kHz …"); no DoP to the
  internal DAC.
- [ ] **Gapless** across a live album, BP on and off: Playback > Joins, 0 underruns.
- [ ] **Bluetooth on the M21:** BP greyed, "Bluetooth · Shared · …".
- [ ] **Unplugging headphones** pauses.
- [ ] (If you have a USB DAC for the M21) **USB on Android 13:** BP works the M21's way;
  report Output and System output.

Session 2 (`v0.0.2`), with the full library on the SD card:

- **First scan (2026-10-24, in progress):** 27,115 files found; 12,000 read after a little
  over 3 minutes (about 60 a second, so about 8 minutes in all). Working, but the Artists
  screen shows nothing until it ends: it says "Music appears here as it's found", which
  this build doesn't do. Fixed in v0.0.3. Still to report: Statistics > Last scan when it
  finishes.
- [ ] **v0.0.3 first scan.** Install v0.0.3, Statistics > Forget the cache, swipe the app away and
  open it: within a few seconds artists appear and keep filling in, with "Reading tags: N of
  27,115" in the ARTISTS header. Does scrolling and opening an artist work while it reads?
  Report Statistics > Last scan when it ends, to compare with v0.0.2's.
  **Reported 2026-10-24: Last scan 528,563 ms (8 min 49 s)**, about 51 files a second.
  v0.0.2's run was only estimated (about 60 a second at the 3-minute mark), so whether the
  as-found rebuilds slowed it is not settled. The first scan as it now behaves: the user
  says "it's OK".

- [ ] **Scrolling the full library.** Fling through all ~499 artists and a long album list on the Snapdragon 680: smooth? Covers fill in as the background fill works (Statistics > Covers, up to ~2,376).
- [ ] **SD card out.** With the card out, Settings shows "SD card · Music" greyed with "Not available"; the card's artists, albums and songs stay listed, greyed, and its songs can't be tapped. Card back: everything as before, "Up to date".
- [ ] **Search** across the full library: results keep up with typing.

Started early (session 1, `v0.0.1`): the M21 has the SD card slot, and the full ~26,000-track library is meant to be on it. Session 4 adds the playback and Bit-Perfect items. Report from the **Debug** panel.

- [ ] **Install and access.** v0.0.1 installs on Android 13; "Allow…" opens the all-files access page; after allowing, the app comes back and starts reading.
- [ ] **Folders.** The list starts with the internal and the SD card's `Music` folders (where there are any), the card's shown as "SD card · Music". "Add Folder…" picks a folder on the card.
- [ ] **Counts.** The LIBRARY line against the desktop's status bar (499 artists · 2,376 albums · 26,795 tracks when last seen there), and the Formats line.
- [ ] **First scan.** How long the first full scan of the whole library took: Debug > Last scan, with its read, reused and removed counts.
- [ ] **Cold start with the full library.** Swipe the app away and open it again, three times: Debug > Cold start each time, and Cache ("read and built in"). The library should be on screen at once on the Snapdragon 680.
- [ ] **Rescan.** The refresh button in MUSIC FOLDERS: Last scan should read about 0 and reuse every track.
- [ ] **SD card out.** With the app open, eject or remove the card: the counts stay the same, and the LIBRARY line says "Unavailable: SD card · Music (N tracks kept)". Open the app with the card still out: the same. Put it back: "Up to date", nothing removed.

## Session notes

### Session 4 — Bit-Perfect, DSD over DoP, and an M21 test build (2026-10-24)

**Researched first** (shown to the user, who chose the rate-matched fallback): see
Decisions. Sources: Android's "Improve audio playback" page, AOSP's "Preferred mixer
attributes", the SDK's own `AudioManager` docs (on this PC), jastreamer PRs #53/#56 (Fold 7),
LastWave #118 (S23), aurora #7 (A53), Roon's community (S24 Ultra, FiiO M23), FiiO's X5 III
FAQ, and M21 reviews (SoundStage! Simplifi, Headfonia).

**Built:** `OutputRoutes` (the route from `getAudioDevicesForAttributes`, followed by an
`AudioDeviceCallback`; a USB device's mixer entries; setting and clearing the mixer; the
debug numbers), the output stage opening each output by `OutputPlan` (mixer entry,
source rate, shared) with `fixedVolume` and a reopen on a route or setting change, DSD
as DoP through BASSDSD, the player's Bit-Perfect setting (saved), focus pausing instead of
ducking on a bit-perfect path, the readout with the route, the BP button wired (on, off,
greyed), the Settings row's chip, and Statistics > Playback's new rows with the pretend
route switch. Ported with their tests: `DopRule` (and the DoP cases of
`PlaybackDecisionTests`). New, with tests: `BitPerfectRule`, `MixerChoice`, the routed
`OutputReadout.describe`.

**Verified:**
- `:core:test` (21 new: DoP 3, Bit-Perfect 18), `:app:testDebugUnitTest`, 289 in all, 0
  failures. `verifyRoborazziDebug`: every earlier picture unchanged; 16 new: Now Playing with
  BP on (USB bit-perfect readout) and greyed (speaker), and Settings with BP on (rate
  matched) and greyed (Bluetooth), both themes, both sizes.
- Emulator, release build (versionCode 5) installed over v0.0.4:
  - The speaker: BP and the Settings chip greyed, a tap does nothing; "Speaker · Shared · 48
    kHz (resampled)"; Statistics shows the route ("speaker \"sdk_gphone64_x86_64\""), "way
    here: None (greyed)".
  - `Gapless Live`: 2 gapless joins, 0 underruns, shared and on the (pretend) bit-perfect mixer.
  - Pretend Headphones, BP tapped on mid-song: reopened at the place as SourceRate, 96 kHz /
    24-bit; readout "Headphones · 48 kHz (resampled)" (the emulator's output stays at 48 kHz).
  - Pretend USB: 24/96 FLAC opened "(BitPerfect)" at 96 kHz / 24-bit; the DFF opened as **DoP,
    176.4 kHz / 24-bit**, "USB · 176.4 kHz · DSD over PCM (bit-perfect)"; BP off mid-song
    reopened it as 88.2 kHz float PCM, shared; on again, DoP again.
  - The setting survives a restart (on); the pretend switch doesn't (off).
  - The media session still shows the song.
- Found and fixed on the emulator: `AudioFormat` refuses rates above 192 kHz there (the
  pretend list crashed building 352.8 kHz).
- **Not checkable on the emulator** (on the S24 and M21 lists): any real USB DAC (what Samsung
  lists, whether the mixer is set, DoP into a DAC), the M21's bypass (System output), the
  volume keys on a bit-perfect output, Bluetooth, unplugging, and a ducking prompt.

**Differences from the spec** (the spec is updated, §6.2 and §6.3): the rate-matched
fallback (the user's call); the M21's way checks the system output's rate before calling
itself bit-perfect; a USB device with no mixer entries greys BP out; no Bluetooth codec in
the readout (system-only API); the shared readout has the route first ("Speaker · Shared ·
48 kHz"), so the mockup's "Shared · 48 kHz / 32-bit (resampled)" isn't what the app shows
(ThemeLab's mockup picture keeps the mockup's text).

### Session 3 — Playback (2026-10-24)

**Built:** BASS for Android vendored (core, FLAC, DSD, Opus, AAC, ALAC, APE, WavPack,
Musepack, AC-3), the output stage (BASS decode streams into an `AudioTrack` at the song's
own rate and depth, gapless joins in one output, a reopen for a new format, what's
heard from the playback head), the player (queue, play order, repeat, shuffle, Previous's
3-second rule, audio focus, unplug pausing, resume), the media session and the foreground
service with the notification, Now Playing (spec 5), the waveform worked out in the
background and cached per file, the readout, and the mini player and every song tap
wired to the queue. Ported with their tests: `PlayOrder`, `ReopenPoint`, `HearingTimeline`,
`OutputReadout`, `TrackEnd`, `WaveformLevels` (`Waveform`'s decoding half is in the app).
New, with tests: `QualityLines` (spec 5's two lines), `ResumePoint` (the saved queue),
`PlayOrder.restore`. The sample script gained `Gapless Live` (a sweep cut into three
songs at sample counts mid-frame).

**Verified:**
- `:core:test` 250 (72 new), `:app:testDebugUnitTest` 17, `verifyRoborazziDebug`: Now Playing
  as the mockup and with Repeat One / Shuffle / paused / no waveform yet, both themes, both
  sizes (8 new pictures), compared side by side with `design/screens/`; Statistics changed
  (the preview switch gone); every other picture unchanged.
- Emulator, release build installed over v0.0.3 (same key):
  - Every sample format plays, opened as asked: FLAC 16/44 (16-bit), FLAC 24/96 (24-bit),
    ALAC 24/48 (24-bit), WAV (16-bit), AIFF (24-bit), WavPack (16-bit); MP3, AAC, Opus and
    Vorbis as float; DSF and DFF as 88.2 kHz float PCM.
  - `Gapless Live`: two gapless joins, **0 underruns**, three times (once with the app out of
    sight); MP3 → MP3 joins too; WAV 16 → AIFF 24 reopens the output, as it should.
  - The end of the queue goes back to its first song, paused.
  - The media session: the shade's player with the embedded cover, title, artist and
    seekbar; `MEDIA_PAUSE`, `HEADSETHOOK`, `MEDIA_NEXT` and `MEDIA_PREVIOUS` work.
  - Seeking by tapping the waveform (75% → 9 s); swipe down closes Now Playing; the
    theme switches on Now Playing.
  - Resume: killed paused at 0:10 of Live Part 2, reopened with it in the mini player and
    Now Playing at 0:10; a play key from the home screen carried on from 10.26 s.
- Not checked on the emulator (on the S24 list): the lock screen (the AVD has none set),
  Bluetooth and wired headsets, unplug pausing, audio focus against another app, and the
  sound itself.

**Differences from the mockups (design/screens):**
- **The readout** reads "Shared · 48 kHz (resampled)", without the mockup's "/ 32-bit"
  (Android doesn't say the mixer's depth; see Decisions).
- **Art placeholder:** the desktop's music note, as everywhere else (session 2).
- **Font:** Selawik sits the album line 1–2 dp off the mockup's Segoe UI (known).
- BP shows its off state and does nothing yet (session 4), as the prompt asks.

**Differences from the spec, and what it didn't say** (all in Decisions above): WMA doesn't
play on Android; what the end of the queue does; "3 / 10" while shuffled; the swipe-down
distance; songs that won't open are passed over.

### Session 2 follow-up — the first scan shows the library as it is found (v0.0.3, 2026-10-24)

The M21's first scan of v0.0.2 (27,115 files from the SD card, about 8 minutes) showed
an empty Artists panel saying "Music appears here as it's found" until it ended.

**Built:** the scanner hands each track to the app as its tags are read
(`LibraryScanner.scan(onRead = ...)`); on a first scan (no cache) the repository rebuilds
the library from what has been read every 1.5 s, or 4 × the last rebuild's time when that
is longer, and the Artists list fills in. The partial rebuilds stop before the result is
shown. The ARTISTS header shows "Reading tags: N of M" whenever a scan reads tags (also a
rescan that finds new files); nothing while it only checks for changes.

**Verified:** `:core:test` 178 (two new: tracks handed over as read, none for files reused
from the cache), `:app:testDebugUnitTest` 17, ThemeLab: two new pictures per size and theme
(`artists-first-scan`, `artists-first-scan-starting`), the others unchanged. Emulator,
cache forgotten: "Scanning the library" at 2.4 s, "Reading tags: 88 of 826" at 3.0 s, the
first artists at 3.6 s with covers filling in, done in 4.8 s with the same 209 · 413 · 826.
A normal start shows the cached library at once with no count.

**Decisions:** the partial library is only shown, never saved: a first scan cut short
starts again next time. The count sits in the panel header, right-aligned, 13 sp
`text.muted` (`artists.countSize`), as no mockup shows a scan.

### Session 2 — Library screens, navigation and search (2026-10-24)

**Built:** the shared parts (screen frame with the title glow, panel, panel header, rows
in their three looks, chips, round buttons, the search field, album art with the
desktop's placeholder), navigation with predictive back, Artists with the logo menu,
Albums, Album, Search, Settings, Statistics (holding the debug numbers), the mini player
(hidden until something plays, with a debug preview), cover thumbnails cached on disk and
in memory with a background fill, and the launcher icon. Session 1's temporary screen is
gone. Ported with tests: the desktop's `ArtDiskCache`, `CutOffImage.WholeFirst` and
`Display` (times, counts, rates, sizes). New, with tests: `SearchResults` (Search's three
groups) and the back stack (`Navigator`).

**Verified:**
- `:core:test` 176 tests, `:app:testDebugUnitTest` 17, `verifyRoborazziDebug`: every
  screen in both themes at both sizes, plus nothing-played, SD-card-out and the logo menu
  (56 pictures in `app/screenshots/`, the token sheet's 16 included), compared side by side with `design/screens/`.
- Emulator, release build installed over v0.0.1 (the update path works): 209 artists ·
  413 albums · 826 tracks with folder covers. Drill-down and back; the back gesture held
  halfway (the screen underneath shows, the top one shrinks) and committed (scroll
  position and selection kept); Search with the keyboard (the panel shrinks above it; back
  closes the keyboard first); the logo menu; the theme from the menu and from Settings;
  the mini player preview (now-playing row, triangle, Play/Pause); the SD card unmounted
  (the card's songs greyed but kept, the folder "Not available") and mounted again ("Up to
  date", nothing removed). Cold start 1.3 s, warm 1.1 s, cache 78 ms, a rescan reads 0.
- Not judged on the emulator: scrolling smoothness (its software renderer takes ~30 ms a
  frame for everything; the UI thread was slow on 31 of 955 frames). It's on both device lists.

**Differences from the mockups (design/screens):**
- **Font:** Selawik is a little lighter than the mockups' Segoe UI, so text looks thinner
  and lines sit 1–2 dp differently. Known since session 1.
- **Art placeholders:** the desktop's music note on the panel colour (spec 2), not the
  mockups' stripes, which stand for album art.
- **Search finds more than the mockup shows:** the desktop's rule matches every song whose
  title, artist, album artist or album holds every term, so "polygon" also lists all of The
  Polygon Set's songs and its other albums. The mockup's three songs were sample data.
- **Album #** is the tag's track number (the desktop's table), so a second disc starts at
  1 again; the mockup numbers its ten songs straight through.
- **Bit-Perfect** reads "Shared" under the label: the readout (spec 6.3) needs the output
  stage (session 3). The BP chip and Last.fm's Connect do nothing yet.
- **Last.fm's line** stays on one line where it fits; the mockup wraps it.
- **Play/Pause** uses `shadow.playButton` (spec 2); the mini player mockup used
  `shadow.controlButton`.
- The search field's focus ring is `state.searchFocusRing` (spec 4.4); the mockup used
  `accent.playback` (the same colour in Dark, different in Light).

### Session 1 — Project, theme and library (2026-10-24)

**Built:** the Gradle project (`:core` plain Kotlin, `:app` Android), the release keystore,
the theme from the three token files, ThemeLab (Roborazzi) with a token sheet, settings
(theme, music folders), all-files access, the TagLib tag reader through the NDK, the
scan and its cache, the ported Core rules with their tests, a temporary screen with the
library counts, folders, theme and debug numbers, `tools/make-sample-music.sh`,
HANDOFF.md and README.md.

**Verified:**
- `:core:test`, 152 tests: the desktop's SortRules, ReleaseDate (sort and parse),
  LibrarySearch (accent-insensitive), LibraryCache, FileStamp, ScanSummary, path
  containment and rebasing, unavailable folders, drive lost mid-scan, removed counts,
  date added, MusicFolderList, DriveChange, MovedCopy, ThemeTokens (with the parity test:
  both colour files have the same keys and the same non-colour values), ImageInfo,
  CutOffImage, BestCover, DsfHeaderReader; plus tests of the mobile tokens and folder art.
- `:app:testDebugUnitTest`: Dark by default, a switch saved and applied at once; the folder
  list saved once per folder with its tick. `verifyRoborazziDebug`: the token sheet's four
  pages, both themes, S24 (384 × 832) and M21 (375 × 667): 16 pictures in `app/screenshots/`.
- Emulator (Android 17): every sample format read with the right tags, rates, depths and
  lengths: FLAC 16/44 and 24/96, MP3 ID3v2.3 and v2.4, AAC, ALAC 24/48 (told from AAC),
  Opus, Vorbis, WAV, AIFF 24-bit, WavPack, DSF and DFF (DSD64, 1 bit), an untagged MP3 by
  its file name; a hidden folder skipped; `Folder.JPG` found ignoring case. Release build,
  26 tracks on internal storage and the emulator's SD card: cold start 740–960 ms, warm
  680 ms, cache read and built in 5–30 ms, a rescan reads 0 and reuses 26. **SD card
  unmounted while running:** 26 tracks kept, 7 marked unavailable, 0 removed; mounted
  again: all back, 0 removed. Theme switch at once and after a restart.
- Not checked on the emulator: the system folder picker ("Add Folder…"); on the S24 list.

**Differences from the spec, and what it didn't say:**
- The screen is temporary; the spec §4 screens come in session 2.
- **No launcher icon yet**: Android's default shows. The spec doesn't say; ask in session 2.
- `mobile-tokens.json` gained `borderWidth: 1` (the spec's "1 dp" borders), so no screen
  hard-codes it.

**Decisions (session 1):**
- **Package** `com.mpaulrenlund.audiofool`; `minSdk 33`, target and compile SDK 37; portrait,
  edge to edge, predictive back on. Test builds are versioned `0.0.x`.
- **Font: Selawik, bundled** (the user's call). Regular, Semibold and Bold only: the spec uses
  400/600/700, and these three weights have tabular digits by default (checked in the font's
  metrics), so numbers line up without a font feature (`tnum` is asked for anyway). Glyphs
  Selawik lacks fall back to the system font.
- **Tag reader: TagLib 2.3.2 through the NDK** (the user's call), the C++ library TagLibSharp
  is ported from; it reads DSF and DFF. Vendored unchanged in `third_party/taglib`, linked
  statically under the MPL. Strings cross JNI as UTF-8 bytes. Where TagLib's APE tags give no
  year for a full date, the year comes from the date, as TagLibSharp gives it.
- **Screenshot tests: Roborazzi** (on Robolectric), not Paparazzi: Robolectric also runs the
  app's Android unit tests, its SDK is pinned apart from the app's (Paparazzi's layoutlib
  trails new compile SDKs), and its native graphics draw the real font, shadows and
  gradients. The pictures are kept in `app/screenshots/` and verified on every run.
- **Theme access by JSON path**, as the desktop's resources are: `color(...)`, `shadow(...)`
  and `desktopNumber(...)` read the Light or Dark desktop file; `dp(...)` and `sp(...)` read
  mobile-tokens.json; `mobileColor(...)` takes its `.light` or `.dark` value. The build copies
  the files from `design/` into the assets, so `design/` stays the only copy.
- **CSS shadows drawn as the tokens say**: outer layers behind, inset layers clipped inside,
  CSS blur converted to Android's. The Dark title glow is drawn from its tokens.
- **System bar icons follow the app's theme**, not the phone's.
- **The cache is binary**, not JSON: the desktop's `library.json` content with strings stored
  once (an album's folder, an artist's name), so a phone loads 26,000 tracks at once. Saved
  with a backup and read from it when damaged or missing (the desktop's SafeJsonFile).
- **Name order** uses a root-locale collator that ignores case but not accents (the desktop's
  InvariantCultureIgnoreCase); **search** folds case and accents (IgnoreNonSpace).
- **Paths compare ignoring case**, as on the desktop; Android's shared storage and SD cards
  are case-insensitive.
- **The folder list starts as each volume's `Music` folder** until the user changes it.
- **A left-out folder's tracks stay in the cache, hidden**; a folder taken off the list loses
  them (MusicFolderList).
- **Not ported:** the desktop's search for a folder on another drive letter (Android mounts a
  card under its own id, which doesn't change); tag writing and its leftovers.
- The scan reads 4 files at once (the desktop uses every core; an SD card prefers fewer).
