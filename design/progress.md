# AudioFool Android — Progress

Update this at the end of every session: tick off what's finished, note anything left open, and record every decision that changed or filled in the spec.

## Sessions

- [x] **1. Project, theme and library** (spec §1, §2, §8) — 2026-10-24
- [x] **2. Library screens, navigation and search** (§2, §3, §4) — 2026-10-24
- [ ] **3. Playback** (§3, §5, §6.1, §6.3)
- [ ] **4. Bit-Perfect, DSD over DoP, and an M21 test build** (§6)
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

Session 2 (`v0.0.2`), with the full library on the SD card:

- **First scan (2026-10-24, in progress):** 27,115 files found; 12,000 read after a little
  over 3 minutes (about 60 a second, so about 8 minutes in all). Working, but the Artists
  screen shows nothing until it ends: it says "Music appears here as it's found", which
  this build doesn't do. To fix in v0.0.3. Still to report: Statistics > Last scan when it
  finishes.

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
