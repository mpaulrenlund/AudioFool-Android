# AudioFool Android — Progress

Update this at the end of every session: tick off what's finished, note anything left open, and record every decision that changed or filled in the spec.

## Sessions

- [x] **1. Project, theme and library** (spec §1, §2, §8) — 2026-10-24
- [x] **2. Library screens, navigation and search** (§2, §3, §4) — 2026-10-24
- [x] **3. Playback** (§3, §5, §6.1, §6.3) — 2026-10-24
- [x] **4. Bit-Perfect, DSD over DoP, and an M21 test build** (§6) — 2026-10-24
- [x] **5. Last.fm, Statistics, polish and release** (§4.5, §4.6, §7) — 2026-10-24, released as v0.1.0
- [x] **0.1.1 fixes** from the first device reports — 2026-10-25
- [x] **0.1.2**: the M21's headphone out — 2026-10-25 (didn't take: 0.1.3)
- [x] **0.1.3**: the M21 recognised by its speaker's name; unplug from the jack — 2026-10-25
- [x] **0.1.4**: no click at a restart or a tap (the desktop's seek fade) — 2026-10-25
- [x] **0.1.5**: unplug pauses on the M21; its readout goes by FiiO's reported rate (both checked on the M21 over adb) — 2026-10-25
- [x] **0.15.1**: the cover menu keeps only Theme and Version of the logo menu's rows (Listening History, Rescan Library, Settings and Statistics are the logo menu's only) — 2026-11-10, released as pre-release [`v0.15.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.15.1)
- [x] **0.15.0**: Version opens the releases page (the logo menu's and the cover menu's Version row, in the default browser); the mini player's buttons follow Now Playing's (the cover's colour on the three icons and Play/Pause's ring and glow; the glow only while playing); no glow at the top of the screen in Dark (every screen, Now Playing's cover-coloured one too) — 2026-11-10, released as pre-release [`v0.15.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.15.0). It replaces 0.14.1 (versionCode 49), built the same day without the Version change and never published
- [x] **0.14.0**: five layout requests (Artists' logo at the right of the search field; a shuffle button on Albums for the artist; the cover menu opened by a tap, with Bit-Perfect in it in place of the BP button; Repeat over Shuffle at the left and the transport centred; Previous and Next in the cover's colour) — 2026-11-10, released as pre-release [`v0.14.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.14.0)
- [x] **0.13.0**: Now Playing's ideas (the cover's colour on Repeat, Shuffle, the artist's name and the top glow; the glow only while playing; a time bubble while scrubbing; a ring in Light; the cover kept between songs; a press shown on the round buttons; the artist's and album's names open them; screen reader names) — 2026-11-09, released as pre-release [`v0.13.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.13.0)
- [x] **0.12.0**: Now Playing's cover colour (the seekbar, Play/Pause's icon and the ring and glow round it take the cover's main colour) — 2026-11-09, released as pre-release [`v0.12.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.12.0)
- [x] **0.11.0**: Albums' year strip (Artists' letter strip on Albums, holding release years, when the albums don't fit) — 2026-11-09, released as pre-release [`v0.11.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.11.0)
- [x] **0.10.1**: the Album page's track numbers (left-aligned at the row's start, ▶ in the playing song's number place) — 2026-11-06, released as pre-release [`v0.10.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.10.1)
- [x] **0.10.0**: Genius (the desktop's Genius window as a screen, from the cover menu) — 2026-11-05, released as pre-release [`v0.10.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.10.0)
- [x] **0.9.0**: a shuffle button on Artists plays the whole library shuffled — 2026-11-05, released as pre-release [`v0.9.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.9.0), the first from the private repo (the APK carries AudioFool's Last.fm key)
- [x] **0.8.1**: Last.fm sign-in with the username and password (AudioFool's own key built in) — 2026-11-05, installed on the S24 and the M21 over USB; not on GitHub (the APK carries the key)
- [x] **0.8.0**: Listening History (the desktop's, from Last.fm) in the logo menu and the cover menu — 2026-11-04, released as a pre-release
- [x] **0.7.4**: Now Playing's cover menu ends with the logo menu's rows (Rescan Library, Settings, Statistics, Theme, the version) — 2026-11-03, released as a pre-release
- [x] **0.7.3**: a longer mute at the first Play through a cold USB DAC (S24, BP); FiiO's jack type read strictly, and Debug shows what FiiO last sent (M21) — 2026-11-03, released as a pre-release
- [x] **0.7.2**: no molded lip at the bottom of the round buttons (Now Playing, the mini player) — 2026-11-02, released as a pre-release
- [x] **0.7.1**: the logo menu in alphabetical order with the version at the foot; the S24's Now Playing readout higher — 2026-11-02, released as a pre-release
- [x] **0.7.0**: Rescan progress: a rescan you start shows "Checking for changes…", then how it ended ("Up to date · 3 new, 1 removed") in the headers — 2026-11-02, released as a pre-release
- [x] **0.6.0**: Now Playing's cover menu (the cover's resolution, Analyze); Analyze, the desktop's spectrogram and quality opinion; the readout's bit depth before the rate — 2026-11-02, released as a pre-release
- [x] **0.5.1**: a silent `AudioTrack` plays while the BP driver plays, so Android doesn't stop the app at an app switch — 2026-10-30, released as a pre-release
- [x] **0.5.0**: Rescan Library in the logo menu; Debug says where the 1.3 s stalls at an app switch go (the app stopped, a send to the DAC, or waiting for samples) — 2026-10-28, released as a pre-release
- [x] **0.4.9**: less stutter at an app switch with BP on (128 ms sent ahead to the DAC, a 1 s ring); Debug counts the gaps by cause — 2026-10-28, released as a pre-release
- [x] **0.4.8**: paused and out of sight, the DAC goes back to Android at once (Samsung ends the app at a swipe from recents) — 2026-10-28, released as a pre-release
- [x] **0.4.7**: no silent DAC after Android ends the app while BP holds it (foreground while the DAC is held; a DAC left held repaired at the next start) — 2026-10-28, released as a pre-release
- [x] **0.4.6**: the DAC muted from the moment BP takes it until its new stream has settled: no pop — 2026-10-28, released as a pre-release
- [x] **0.4.5**: given back, the DAC returns to 0 dB with Android's media volume lowered to 1; the report counts failed packets and reads the volume back — 2026-10-28, released as a pre-release
- [x] **0.4.4**: the DAC's volume from the user only (quietest first, then where the keys left it; every volume control set); BP off and plugging in pause — 2026-10-28, released as a pre-release
- [x] **0.4.3**: never louder when the DAC changes hands (BP off came back far too loud in 0.4.2) — 2026-10-27, released as a pre-release
- [x] **0.4.2**: the DAC's volume kept across hand-backs (and Android's media volume kept with it); 64 ms in flight to the DAC so the background keeps up; refill timing in the report — 2026-10-27, released as a pre-release
- [x] **0.4.1**: the seekbar no longer flashes back after a seek; the driver's Debug row keeps its last eight notes — 2026-10-27, released as a pre-release
- [x] **0.4.0**: true Bit-Perfect on the S24: the app's own USB Audio driver (libusb), the DAC's hardware volume on the volume keys, given back with a reset — 2026-10-27, released as a pre-release
- [x] **0.3.1**: true Bit-Perfect on the S24, a test build: two ways of giving the DAC back (a USB reset; waiting for Android to close its output first) — 2026-10-27, released as a pre-release
- [x] **0.3.0**: true Bit-Perfect on the S24, step 1: the USB DAC read directly (look only, Debug) — 2026-10-27, released as a pre-release
- [x] **0.2.6**: the M21 unplugged says "No Output"; the S24's buttons higher again — 2026-10-26, released as a pre-release
- [x] **0.2.5**: the readout names the output (the DAC's name, the M21's 3.5mm / 4.4mm jack), "Exclusive" and dots; the S24's buttons higher — 2026-10-26, released as a pre-release
- [x] **0.2.4**: a smooth waveform seekbar (the desktop's 600 columns); the S24's Now Playing buttons higher — 2026-10-26, released as a pre-release
- [x] **0.2.3**: Now Playing's spacing on the S24; plugging in a USB DAC or adapter never starts the music — 2026-10-26, released as a pre-release
- [x] **0.2.2**: Statistics' Playback and Debug panels moved to a Debug screen; no border round covers; one quality line; the readout without "Shared", capitalised; Now Playing's new controls layout and a bigger cover on the M21 — 2026-10-25, released as a pre-release
- [x] **0.2.1**: the letter strip always shown; swipe-to-skip no longer leaves the art off the screen — 2026-10-25, released as a pre-release (the stuck art still to confirm on the S24)
- [x] **0.2.0**: the Artists letter strip; Now Playing swipes sideways to skip (no swipe down); the seekbar glides — 2026-10-25, released as a pre-release

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

2026-11-10, 0.15.1 (versionCode 51; released the same day as pre-release [`v0.15.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.15.1),
APK attached, the repo checked `PRIVATE` first, the downloaded APK's SHA-256 `e9838121…020ee0`
matching `dist/`): **Listening History, Rescan Library, Settings and Statistics leave the cover menu** (the user: "for the menu in the Now Playing screen, let's remove
Listening History, Rescan Library, and Statistics. These should just be in the Main Logo menu.",
then "Let's remove settings as well."). Spec §5 (*Cover menu*), §4.1, §4.7; README.
- The cover menu is now Resolution, Analyze, Bit-Perfect, Genius, Theme, Version: still
  alphabetical after the resolution. The logo menu keeps all six of its rows. `AppMenuRows` gained
  `allRows` (false for the cover menu: Theme and the version only) rather than a second copy of
  the rows.
- **Checked:** 101 app tests (`NowPlayingTouchTest`: the cover menu has its six rows and not
  the other four; `VersionRowTest`: the logo menu still has all six). ThemeLab: the 12 cover menu
  pictures changed (four rows shorter), the logo menu's and every other one unchanged. Emulator:
  a tap on the cover shows the six rows.

2026-11-10, 0.15.0 (versionCode 50; released the same day as pre-release
[`v0.15.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.15.0), APK attached, the repo checked `PRIVATE` first, the downloaded APK's SHA-256
`a5168eba…12bf42` matching `dist/`): **Version opens the releases page**
(the user: "In the App menu, if i tap the Version row, I'd like the app to open the default
browser and this website: https://github.com/mpaulrenlund/AudioFool-Android/releases"). Spec §4.1.
- A tap on Version closes the menu and opens the page (`RELEASES_URL`, `openInBrowser` in
  `ui/OpenInBrowser.kt`; `AppActions.openReleases`). **My call: in the default browser, by
  name**: a plain link to github.com goes to GitHub's app where it is installed, which claims
  those links; "the default browser" is the one a plain web link goes to, so the link is sent
  to that one (`setPackage`). With no default chosen, Android asks; with no browser, nothing
  happens. The manifest gained a `<queries>` entry for web links, so the app can see the
  browsers (no new permission).
- **My call: the cover menu's Version too**, since it ends with the logo menu's rows.
- The row now reads as the others (`text.primary`, value `text.muted`), not as information.
- The repository is private: the page shows only where that browser is signed in to GitHub.
- **Checked:** 3 new tests (`VersionRowTest`: the tap opens it and closes the menu; the page
  goes to the default browser and not to an app claiming github.com; with no browser known,
  Android chooses). ThemeLab: the 16 menu pictures changed (Version's colours), every other one
  unchanged. Emulator: the tap sent the page to Chrome by name (the log's `pkg=com.android.chrome`);
  Chrome showed its first-run screen, never having been opened there.

2026-11-10, 0.15.0 (versionCode 50; first built as 0.14.1, never published): **the mini player's buttons follow Now Playing's**
(the user: "Can we update the mini-player's icons to follow the same logic as the Now Playing
screen? Including showing the glow around the play button when audio is playing."). Spec §2, §3,
§5 (*Cover colour*, Controls row).
- Previous's, Play/Pause's and Next's icons and Play/Pause's ring and glow take the cover's
  colour, as on Now Playing, and **the glow shows only while playing** (it showed paused too, in
  the theme's blue). Without a colour of its own the theme's stay, and paused there is still no
  glow. Light has no glow, as on Now Playing; the cover's colour still makes its 2 dp ring there.
- **My call: the title and the artist's name keep their colours.** The request was the icons;
  Now Playing's artist name in the cover's colour could follow if wanted.
- **My call: one colour per song, shared** (`art/CoverColors`, provided as `LocalCoverColors`).
  Now Playing worked the colour out from the cover decoded at its own size, so the S24 and the
  M21 could differ a little, and the mini player, which has only the small album thumbnail,
  couldn't match it. Now both screens take it from the song's cover decoded no smaller than
  512 px a side (the lock screen's size), worked out once and kept for the last 64 songs
  (keyed by path, modified time and size, so a changed file is worked out afresh). The mini
  player keeps the last song's colour until the next one's is in, as Now Playing does. Now
  Playing's colour can shift very slightly on a busy cover; on plain ones it is the same.
- **So the buttons follow the song's own cover, the thumbnail the album's.** Where an album's
  songs carry different covers (the emulator's sample music does), the mini player's buttons can
  be in another colour than its thumbnail, as Now Playing shows the song's cover. Seen on the
  emulator with 0.15.0; an album with one cover has the two agree.
- **Checked:** 624 tests (4 new, `CoverColorsTest`: worked out once, the file and ThemeLab's
  pictures agree, no colour kept too, a changed file afresh). ThemeLab: 12 new pictures
  (`mini-player-cover-red`, playing; `-cover-yellow-paused`; `mini-player-paused`, no colour),
  every other one unchanged; the thumbnail stays the placeholder there. Emulator (Dark, this
  build over 0.14.0, still labelled 0.14.0): paused on an orange cover, the three icons and the
  ring are orange with no glow; playing, the glow is orange; Now Playing and the mini player show
  the same orange for the same song.

2026-11-10, 0.15.0 (versionCode 50; first built as 0.14.1, never published): **no glow at the top of the screen** (the user:
"Can we remove the glow at the top of the window in the Dark theme?"). Spec §2; §5's *Cover
colour* list lost its last item.
- **My call: every screen**, since it was one drawing (`WindowBackground`) that every screen
  shares, and "the window" reads as all of them; Now Playing's glow in the cover's colour
  (0.13.0, idea 1) went with it. The window is plain `color.window.bg`, behind the status bar
  too. Light drew nothing there already, so it is unchanged.
- `Modifier.titleGlow` and `WindowBackground`'s `glowTint` are gone. The desktop's
  `color.window.titleGlow` and `titleBar.glow.*` stay in the colour files (they must match
  the desktop's) and are simply not read; the token sheet still shows the colour.
- **Checked:** 620 tests. ThemeLab: all 150 Dark pictures changed and no Light one; each Dark
  picture compared with the old one below its top 80 dp came out identical, so the glow is
  the only change. Emulator (this build over 0.14.0, still labelled 0.14.0): Artists and Now
  Playing in Dark have a plain background behind the status bar.

2026-11-10, 0.14.0 (versionCode 48; released the same day as pre-release
[`v0.14.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.14.0), "AudioFool
0.14.0", after "Build 0.14.0" (the number the wrap-up proposed) and "Publish it"; the repo
checked `PRIVATE` right before the upload; the downloaded APK's SHA-256 matched `dist/`):
**five layout requests** in one message, built as asked. Spec §4.1, §4.2, §5 (item 8, *Cover colour*, *Cover menu*), §6.2, §6.4 and §2
(*Round buttons*, the mobile colours line).
- **Artists' bar**: the search field from the left, the logo at the right (the user: "move
  the app logo to the right hand side of the Search box"). `topBar.logoBarPaddingStart` and
  `End` swapped values (12 / 4), so the field starts 12 dp in, as Search's does. **My call:**
  the logo menu now hangs from the logo's right edge (`Popup` aligned `TopEnd`), its right
  edge level with the panel's, the mirror of where it was.
- **Albums' shuffle button**: Artists' button at the far right of the ALBUMS header, for that
  artist ("start playing/shuffling music from that artist and open the Now Playing screen").
  Core `LibraryShuffle.queue(artist)`: the artist's albums as Albums lists them, each album's
  songs, unreachable ones left out; then `Player.shuffleAll` as for the library, Shuffle on,
  a random song first. Back from Now Playing returns to Albums.
- **The cover menu opens on a tap** ("tapping on the album art opens the menu"). **My call:**
  the long press still opens it too (with its haptic tick), so 0.6.0's habit keeps working.
  A sideways swipe still skips and doesn't open it (the emulator: 3 / 4 to 4 / 4).
- **Bit-Perfect moved into the cover menu**, as a row with a check at its end while on
  ("call it 'Bit-Perfect' with a checkmark at the end of the row if it's enabled. Tapping the
  row should remove the checkmark"). **My calls:** it sits between Analyze and Genius, which
  keeps the menu alphabetical; a tap keeps the menu open (as Theme does) so the check is seen
  to go; the check is `status.bitPerfectOn`, Settings' "on" dot (the same green / teal as
  the BP label's "on"); where BP can't work the row is greyed and can't be tapped, the check
  still showing the setting, as the button did. New `MenuCheckRow`, `menu.checkIcon` (18 dp).
  The BP button and its `nowPlaying.bpLabel*` sizes are gone; **the `color.bpLabel` pair is
  kept, unused** (the token sheet still shows it, and `ThemeSettingTest` reads it to test
  mobile colours), should the button come back.
- **Repeat over Shuffle at the left, the transport centred** ("place the repeat button above
  the shuffle button … move these two buttons to the left … make the shuffle and repeat
  buttons any bigger … center the previous, play, and next buttons on the screen"). **My
  calls on the sizes**, because centring and bigger buttons pull against each other on the
  S24: the two are as tall as Play/Pause together (Repeat's top at its top, Shuffle's bottom
  at its bottom), so each is (Play/Pause − 8) / 2, within `modeButton.min`–`max` (34–44):
  **44 dp on the S24, 35 on the M21** (34 before). The M21's grow only 1 dp: its Play/Pause
  is 78 dp and its screen has no height to spare; bigger there would take height from the
  cover. With Play/Pause in the screen's middle, the S24 is too narrow for 44 dp buttons and
  its full-size transport (70 / 96 / 22): its gaps go to `transportGap.fitMin` (8 dp) and
  **Previous and Next shrink to 60 dp** to keep 12 dp before Previous. The M21's transport
  is unchanged. Repeat and Shuffle start at `paddingX`, level with the cover and the
  seekbar. No height changed on either phone, so nothing above the row moved.
- **Previous and Next in the cover's colour** ("follow the same color scheme as the album art
  like the Play button does"). **My calls:** only their icons (no ring or glow, so Play/Pause
  still stands out), and without a cover colour they keep `control.iconNeutral`, so a black
  and white cover gives the same grey buttons as before (not `accent.playback`, which would
  have widened the playback colour's role).
- **Checked:** 620 tests, 22 more than 0.13.0's: 7 in `NowPlayingTouchTest` (the row's layout
  on both phones, the M21's touch areas, the cover's tap and long press, the Bit-Perfect row
  off and switched, on, and greyed; the BP button's test gone, the touch-area and name tests
  rewritten), 4 in the new `AlbumsShuffleTest` (the button, its place, none for an unknown
  artist, the logo's place), 3 in core `LibraryShuffleTest`, 8 menu pictures. ThemeLab: the Artists, Albums, logo menu, Now
  Playing and cover menu pictures re-recorded (128 changed), 8 new `art-menu-bp-on` and
  `art-menu-bp-greyed`, the 8 `now-playing-bp-*` removed; looked at in both themes at both
  sizes; every other picture identical. Emulator (this build over 0.13.0, still labelled
  0.13.0): the logo at the right and its menu under it; Artist 001's shuffle opened Now
  Playing on "3 / 4" with Shuffle lit; Play/Pause centred (x 414–666 of 1080) and Repeat
  44 dp; Previous and Next in the cover's orange; a tap on the cover opened the menu, with
  Bit-Perfect greyed on the speaker; on Debug's pretend Headphones route a tap cleared the
  check and another brought it back, the menu staying open.
- **The build** (bump commit `4dd2ebb`): 620 tests and ThemeLab passed again with the bump; the
  APK (`dist/AudioFool-0.14.0.apk`, SHA-256 `832e8d78…da06d826`) is signed with the release key
  (`1fdefe0a…be2c21`) and carries AudioFool's Last.fm key and secret (compared with
  `gradle.properties` in the shell, not printed). On the emulator it installed as an update
  (versionCode 48, library and settings kept); the logo menu says "Version 0.14.0", Artist
  001's shuffle and the cover menu worked as before. Notes: `dist/notes-0.14.0.md`.

2026-11-09, 0.13.0 (versionCode 47; released the same day as pre-release
[`v0.13.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.13.0), "AudioFool
0.13.0", after "Build 0.13.0" (the number the wrap-up proposed) and "Publish it"; the repo
checked `PRIVATE` right before the upload; the downloaded APK's SHA-256 matched `dist/`):
**Now Playing's ideas, all but the "Next" line**
(the user asked for "recommendations or mockups on how to improve this Now Playing screen", got
a critique and eight numbered mock-ups, and answered "Let's do everything except for idea 6").
Spec §5 (*Cover colour*, items 2, 5 and 8) and §2 (*Round buttons*); `CLAUDE.md`'s colour rule
gained its Now Playing exception.
- **1. The glow at the top** takes the cover's colour, at `window.titleGlow`'s own opacity
  (Dark only: Light has no glow).
- **2. Repeat and Shuffle when on**, and Repeat One's badge, take the cover's colour, so the
  screen has one colour for "on". BP keeps its red and green: they are a status. On a red
  cover, a lit Repeat sits next to BP's red "off"; the user saw that and chose it.
- **3. The artist's name** takes the cover's colour (the user's call, against my note that it
  breaks "artist names are blue"). **My call:** it is fitted to `accent.blue`, the text colour
  it replaces, not to `accent.playback`, so it comes out lighter on Dark and darker on Light
  than the seekbar; fitted to the playback colour it would have been 2.7:1 against Light's
  background. `AppTheme.coverAccent(main, standsInFor)` does both.
- **4. The glow round Play/Pause means playing**: paused, the ring alone. **My call:** on Now
  Playing only; the mini player's glow stays on while paused, since the request was this
  screen. Open to carrying over.
- **5. A time bubble while scrubbing**: the index strips' bubble surface over the finger,
  inside the bar's ends, 36 dp above the bar so it clears the quality line (the first render
  sat on it; the mock-up had it clear). New tokens `nowPlaying.scrubBubble.*`.
- **6. The "Next" line: not built**, the user's call (it only fitted the S24).
- **7. Light gets a ring** round Play/Pause in the cover's colour, 2 dp at 0.67
  (`nowPlaying.coverAccent.ring`), where the theme's ring is clear. No cover colour, no ring,
  as before.
- **8. Touch areas: nothing to build, and my critique was wrong.** It said Repeat, Shuffle and
  BP had 34 dp touch areas. A test showed a tap 5 dp outside one already presses it: Compose
  widens a small touch area to 48 dp by itself. `NowPlayingTouchTest` pins it; the user was
  told.
- **Not pictured, built as "everything":**
  - *The cover stays until the next is read.* The art used to blink to the placeholder at
    every song change; now picture and colour both wait for the new cover.
  - *A pressed round button* shows `control.faceHover`. **My call:** all nine round buttons,
    the mini player's three included (one shared part, and a press shows nowhere at rest).
    BP greyed can't be pressed and doesn't show one.
  - *The artist's and the album's names open them* (`artistRouteFor`, `albumRouteFor`): the
    artist's albums; the album with the song chosen. **My call:** an artist the library
    doesn't list (a guest) leads to the artist the album is under.
  - *Screen reader names* on every round button, and the seekbar as a slider ("Seek").
- **Left alone:** Light's small grey text is about 4.3:1 on the window (just under 4.5:1). It
  is the desktop's `text.muted`; changing it needs a phone-only colour on every screen, which
  nobody asked for.
- **The screen's frame was turned inside out** to get the cover's colour to the top glow: the
  measuring box is now the whole screen, with the window background and the inset column
  inside it (`screenHeight` is its height; `barHeights()` is gone). Every picture without a
  cover came out pixel-identical, so the sizes didn't move.
- **Checked:** 598 tests (11 new in `NowPlayingTouchTest`: touch areas, names, BP greyed,
  the two taps, the bubble and the seek, where the lines lead). ThemeLab: the 16
  `now-playing-cover-*` pictures re-recorded (Repeat and Shuffle now on in red and grey,
  yellow paused with Repeat One), `now-playing-modes` changed in Dark only (paused: no
  glow), 8 new `now-playing-scrub*`; looked at in both themes at both sizes; every other
  picture identical. Emulator (this build over 0.12.0, still labelled 0.12.0): paused shows
  the ring only and playing the glow; the bubble follows a drag; a held Next shows the
  press; the artist line opened Air's albums and the album line *Moon Safari* with the song
  shown, back returning to Now Playing each time; across three song changes in an album
  every one of 273 recorded frames had the cover, none the placeholder.
- **The build:** `dist/AudioFool-0.13.0.apk` (SHA-256 `a4dbbf18…8841344c`), signer
  `1fdefe0a…be2c21`, AudioFool's own Last.fm key in it (compared with `gradle.properties`,
  neither printed), 598 tests and ThemeLab passing. Installed over the emulator's build in
  place (the song and the library kept): the cover menu says "Version 0.13.0"; paused, the
  ring only, and the glow once playing; the mini player as it was. Notes in
  `dist/notes-0.13.0.md`.
- **A ThemeLab scare, once:** one record run changed 32 untouched pictures (Search's cursor
  missing, the index strips' lists not scrolled): effects that hadn't run when the picture
  was taken. Five more runs, alone and with everything else, gave them back identical, and
  the cause wasn't found. If `verify` ever fails on only those, run it again before
  believing it.

2026-11-09, 0.12.0 (versionCode 46; released the same day as pre-release
[`v0.12.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.12.0), "AudioFool
0.12.0", after "Build 0.12.0" (the number the wrap-up proposed) and "Publish it"; the repo
checked `PRIVATE` right before the upload; the downloaded APK's SHA-256 matched `dist/`):
**Now Playing's playback colour comes from the
cover** (the user: "I'd like try taking the main color theme of the album art that is currently
playing and use that color as the accent color for the seekbar, the color around the play
button, and the Play button itself"). Spec §5, *Cover colour*; a change to the rule that the
playback colour is `accent.playback`, asked for by the user as something to try.
- **What takes it** (as asked): the seekbar's played part, Play/Pause's icon, and the ring and
  glow round Play/Pause. Nothing else: the mini player, the now-playing rows and the Album
  page's triangle keep `accent.playback`.
- **No new dependency:** the colour is worked out by the phone's own `core/.../art/CoverColor`
  (plain Kotlin, 20 tests), not androidx.palette. The desktop has no such rule to port.
- **My calls, open to change:**
  - *Which colour is the cover's.* The hue with the most colour in it: pixels counted by hue
    (OKLab, 10° slices with their neighbours), each by its chroma, greys left out. So area
    counts and so does vividness: a red logo on a black cover is red, a blue sky with a small
    sun is blue, a large dull blue beats a small vivid red. Not chosen: the most vivid colour
    whatever its size (Android Palette's "vibrant"), or the plain commonest colour (often
    black or white).
  - *A cover with no colour keeps the theme's.* Black and white, or under 2% coloured, or no
    cover: `accent.playback`, as before. Not chosen: a grey accent.
  - *The colour is fitted to the theme, not used as it is.* Same hue, at a lightness within
    `nowPlaying.coverAccent.lightnessRange` (0.15, the one new token) of the theme's own
    `accent.playback`: that or lighter on Dark, that or darker on Light. A navy cover would
    otherwise vanish on Dark's buttons, a yellow one on Light's. The cost: on Light a yellow
    cover gives a dark gold, and on Dark a burgundy one a bright red. Made lighter it keeps
    its saturation, made darker its chroma.
  - *Light has no ring or glow* (`control.playRing` and `shadow.playGlow` are clear there), and
    still has none: the cover's colour takes each token's own opacity. In Light the seekbar
    and the icon change.
  - *No animation, no setting.* The colour changes when the new cover appears; until then the
    last cover's stays, so an album's songs don't flash the theme's blue between them. There
    is no switch to turn it off.
- **Code:** `NowPlayingScreen.kt` reads the cover once for the art and the colour
  (`shownCover`, `BigCover.color`); `AppTheme.coverAccent` fits it to the theme at each
  composition (a theme switch re-fits it); `RoundButton(playTint)` and an `Icon` taking a
  colour in `Parts.kt`. ThemeLab gets made-up covers through `coverForPictures`
  (`ThemeLabFixtures.coverDeepRed` and three more).
- **Checked:** core and app tests (587, none failing); ThemeLab: 16 new pictures
  (`now-playing-cover-red`, `-yellow`, `-landscape`, `-grey`), looked at in both themes at both
  sizes; every existing picture pixel-identical (no cover in ThemeLab is still the theme's
  colour). Emulator (this build over 0.11.0, still labelled 0.11.0): a pale green cover and a
  vivid green one each gave their green; the fractal on pink of *Moon Safari*'s sample gave
  pink; Next within an album kept the colour through the frame where the art was the
  placeholder (screen recording, the icon's pixel in each frame); Theme from the cover menu
  re-fitted it at once (darker green on Light, no ring or glow there). Only made-up and sample
  covers so far: real covers are for the phones.
- **The build:** `dist/AudioFool-0.12.0.apk` (SHA-256 `1cea098a…36eb71e7`), signer
  `1fdefe0a…be2c21`, AudioFool's own Last.fm key in it (compared with `gradle.properties`,
  neither printed), 587 tests and ThemeLab passing. Installed over the emulator's build in
  place: the song, the theme and the library kept, the cover menu says "Version 0.12.0", the
  cover's colour as before. Notes in `dist/notes-0.12.0.md`.

2026-11-09, 0.11.0 (versionCode 45; released the same day as pre-release
[`v0.11.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.11.0), "AudioFool
0.11.0", after "Build 0.11.0" and "Publish it"; the downloaded APK's SHA-256 matched `dist/`):
**a year strip on Albums** (the user: "Can we add
the same Alpha scroll bar from the Artists screen when there are enough albums to warrant
scrolling on the Albums screen?"). Spec §4.2.
- **Years, not letters (the user's call**, one of three, asked as a choice): Albums is in the
  desktop's release-date order, so A–Z letters would come out jumbled. The strip holds the years
  the albums came out in, "’97" on the strip (two digits fit the 24 dp pill at 12 sp), "1997" in
  the bubble. Not chosen: letters with the list re-sorted A–Z (a spec change), or letters in
  first-album order with the dates kept.
- **Only when the albums don't fit** (as asked): rows are a fixed `albums.rowHeight`, so
  `rows × height + 2 × listPadding > the list's height` decides it before drawing, with no
  flash. Otherwise the rows keep the full width, so every existing Albums picture is unchanged.
  Like Artists, no strip for a single year.
- **My calls, open to change:** albums with no year (at the foot of the list, as the desktop
  sorts them) are under "?"; 1928 and 2028 are two sections though both read "’28".
- **Thinning** (new, Albums only in practice): an artist with many years would squeeze the labels
  together on the M21 (sixty years leave about 7 dp each). Below `artists.index.minSlot` (14 dp)
  only every few labels is written, each centred over its run, the current one in its run's
  place; the touch still picks each year and the bubble names it. Artists' 28 letters get 17.5 dp
  on the M21 (more with nothing playing), so Artists never thins.
- **Shared code:** core `ScrollIndex` (sections, `sectionAt`; a label and a bubble per section)
  now backs both `ArtistIndex` (now an object; same rules, same tests) and the new
  `AlbumYearIndex` (5 tests). `ArtistIndexStrip.kt` became `IndexStrip.kt` (`IndexStrip`,
  `labelGroups` with 3 tests). The bubble is `height = bubble`, at least `bubble` wide, with
  `bubblePaddingX` (12 dp) either side, so a letter's bubble is the same 72 dp square.
- **Tokens:** `artists.index.minSlot` and `artists.index.bubblePaddingX` added; the strip's
  `$note` says Albums uses the same sizes. The group keeps its `artists.` name.
- **Checked:** core (504) and app (62) tests; ThemeLab re-recorded: 8 new pictures
  (`edge-albums-years`, 26 albums with ’99 pressed and a song playing; `edge-albums-years-full`,
  sixty years, thinned to every other year on both phones), looked at in both themes on both
  phones; every other picture pixel-identical, Artists' strip and bubble included. Emulator (the
  release build over 0.10.1; a test artist of 25 albums, 1971–2024 and one untagged, pushed to
  `/sdcard/Music/Year Strip Band`): 23 years and "?" shown, a tap on ’99 put 1999's album at the
  top, a slide to the foot reached the untagged album, the bubble showed "1985" while held, and
  a two-album artist had no strip. The 0.11.0 release build, installed over that one: the strip
  from ’71 to ’24 and "?", and a tap on ’01 put 2001's album at the top.

2026-11-06, 0.10.1 (versionCode 44; released the same day as pre-release
[`v0.10.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.10.1), "AudioFool
0.10.1", after "Build 0.10.1" and "Publish it"; the downloaded APK's SHA-256 matched `dist/`):
**the Album page's track numbers move left** (the user, with a photo of the S24: "On this page the track numbers are too far to the right. Can we
correct the layout of this page."). Spec §4.3.
- **Why they sat there:** the mockup and the desktop keep a triangle slot (8 dp, a 4 dp gap) left
  of the number, which was right-aligned in a 32 dp column, so "1" began about 26 dp in from the
  cover's left edge and every title was pushed over with it.
- **The user's call** (one of three, asked as a choice): **the playing song's ▶ takes its
  number's place**, and the numbers are **left-aligned at the row's start**, about where the
  cover begins; the "#" heading too. Not chosen: ▶ in the number's place with the numbers
  right-aligned (1–9 under 10's last digit), or the triangle kept beside the number with only
  spare space taken out (about 4 dp).
- **The column** is `album.columns.number.width` (now 18 dp, two digits; was 32) or the album's
  longest number if that is wider, measured with the cell's style (`numberColumnWidth`; the
  numbers use tabular figures). So a 120-track album and the 130 % font size fit, and the header
  and every row share one width. Titles start about 14 dp further left on most albums.
- **Differs from the desktop on purpose:** its # cell keeps the triangle slot (`theme.songNumberCell`);
  the phone gives that width to the titles. The mockups in `design/screens/` still show the old
  column.
- **Tokens:** `album.numberMinWidth` and `album.triangle.gap` are gone (unused); the number
  column's `align` is "left"; a `$note` on `album` says how the column works.
- **Checked:** the core and app tests (`ThemeTokensTest`'s spot value is now 18); ThemeLab
  re-recorded: the 20 Album pictures changed (album, chosen song, long titles, 120 tracks,
  1.3 font; both phones, both themes), looked at in both themes on both phones; every other
  picture pixel-identical. Emulator (the release build over 0.10.0, library and resume kept):
  numbers at the cover's left edge, ▶ in the current song's place, and it moved to the next
  song played.

2026-11-05, 0.10.0 (versionCode 43; released the same day as pre-release
[`v0.10.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.10.0), "AudioFool
0.10.0", from the private repo, after "Build 0.10.0" and "Publish it"; the downloaded APK's
SHA-256 matched `dist/`): **Genius** (the user: "In the desktop version …, we
add a feature for a genius.com integration. … could we add that same feature for the android
version. This should appear when long pressing the album art on the Now Playing screen.").
The desktop's session 75, ported. Spec §5 (the cover menu) and the new §5.2.
- **Core** (`core/.../genius/`): `GeniusModels`, `GeniusJson` (kotlinx's JSON tree for the
  desktop's `System.Text.Json`), `GeniusMatch`, `GeniusClient` (a `GeniusTransport` like Last.fm's,
  `HttpURLConnection`, every request and its parsing on IO) and `GeniusLookup` (coroutines for
  its tasks), with the desktop's 64 test cases in 32 tests (`GeniusTest`; each `[Theory]` a loop).
  One difference: kotlinx reads a bare word like `<html>blocked</html>` as a JSON literal where
  .NET fails, so anything that isn't a JSON object counts as "without a page AudioFool can read".
- **The app** (`genius/Genius.kt`, `genius/LinkedText.kt`, `ui/GeniusScreen.kt`): `Genius` (the
  desktop's `GeniusViewModel`, its wording unchanged, in `GeniusRows`) is held by the app
  (`AudioFoolApp.genius`), so its lookup keeps every song found for the run, as the desktop's
  does. `Route.Genius(path)` names the song, so the screen stays on it when the next one plays.
  The cover menu's **Genius** row (between Analyze and Listening History: alphabetical) calls
  `show` each time, as the desktop's Ctrl+G does. "Open on Genius" and links in the text open
  the browser (`ACTION_VIEW`; with nothing to open it, the tap does nothing, as on the desktop).
- **The user's call:** in the cover menu (the long press on Now Playing's art).
- **My calls, open to change:** the screen is titled "Genius" (the desktop's window title is
  "Song · Genius"; the song is right under it here). The song's header is the Album screen's
  header in a panel (its album's cover, title, blue artist, "Album · Year", then "Open on
  Genius"). The desktop's message line (looking up, no match, failed) is a GENIUS panel's row,
  as Listening History's states are, and a failure, or a part Genius didn't send, is tappable
  with "Tap to try again." (the desktop says to open the song again). "Show 50 more" says
  "Loading…" while the next page comes (the desktop shows nothing). Body text is 15 (the
  desktop's 13 is small on a phone); the credit label column is 116 dp (`genius.creditLabelWidth`;
  the desktop's 150 leaves too little on the M21). Links are the desktop's `theme.link`:
  `text.primary` at `dialog.emphasisWeight`, underlined.
- **Tokens:** `genius.*` in `mobile-tokens.json`; the desktop's `genius.*` (in both of its files,
  copied unchanged earlier) are its window's and aren't used.
- **Checked:** 499 core tests (32 new) and 59 app tests (12 new: the wording, `LinkedText`, the
  model against a made-up genius.com: a match, show more, no match and a chosen result, a
  failure and its retry, the same song again asking nothing). ThemeLab: 28 new pictures (the
  page's top and end, looking up, no match, failed, a missing part, 1.3 font; both phones,
  both themes, made-up text), the 4 cover-menu pictures changed (the new row), every other
  picture pixel-identical. Emulator (release build over 0.9.0): the row in the cover menu, the
  screen with the song's header and cover, the failure row and its retry, back to Now Playing.
- **Not checked against the real genius.com, and why:** this PC's Zscaler re-signs genius.com's
  certificate, which Android doesn't trust, so the emulator gets "Trust anchor for
  certification path not found" (correctly shown as a failure). A JVM probe through the ported
  client (with the Zscaler trust store) matched Radiohead's and the Beatles' songs and read
  their pages, but Cloudflare answered about 1 request in 4 with a challenge (HTTP 403,
  `cf-mitigated: challenge`), parallel or not; curl from this PC got 0 of 20. Cloudflare scores
  the desktop JDK's own requests; the phones send theirs through Android's stack, which none
  of this measures. **On the phones, watch for "Couldn't load the Genius page. Genius answered
  HTTP 403 …" or a "didn't send" row**: if they come often, it's Genius's bot check refusing the
  phone. Don't try to get round it (imitating a browser's requests was ruled out); the honest
  options are the documented `api.genius.com` with a token (no comments, no Q&A) or living
  with a retry tap, which is the user's call.

2026-11-05, 0.9.0: **the repo is private, and builds go out as GitHub pre-releases again**
(the user's call). After 0.9.0 was built, the user asked for a release ("installing via ADB
isn't my preferred process"). Told that the APK carries AudioFool's Last.fm key and secret, so
it can't go on a public release (0.8.1's upload was stopped as a credential leak), the user
asked to keep the key in the APK and made the repo private instead ("I can make the Github
project private, if that's the concern"). Offered too and not taken: a public release built
without the key (signed-in phones keep their own saved key; a new sign-in would fail). The
owner `mpaulrenlund` switched it; GitHub then said `PRIVATE`, and the repo and the release's
APK gave 404 to an anonymous visitor. **Before every upload, check the repo is still
`PRIVATE`**; `CLAUDE.md`, HANDOFF's routine and its traps say so. The user installs from the
release page on each phone, signed in to GitHub. USB stays for when they ask.

2026-11-05, 0.9.0 (versionCode 42; released the same day as pre-release
[`v0.9.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.9.0), "AudioFool
0.9.0", from the private repo; the user's number, proposed as a new feature and taken with
"Let's build it"): **a shuffle button on Artists** (the user: "At the far right side of the "Artists"
row, can we put a small shuffle button? Clicking this should shuffle the music library and
start playing a random track."). Spec §4.1 and §6.1 (Resume) updated. The desktop has no such
button, so nothing to port; the shuffling is the desktop's `PlayOrder`, as for an album.
- **What a tap does:** the queue is every song that can be reached (a removed SD card's left
  out), in library order: artists, their albums, the albums' songs (`LibraryShuffle.queue` in
  core, 7 tests). Shuffle is turned on and play starts from a random song
  (`Player.shuffleAll`): `PlayOrder` pins it to the front and shuffles the rest. Repeat is left
  as it was. Each tap reshuffles.
- **Now Playing opens on the song** (the user's call, "Open Now Playing", asked after a first
  version that left Artists on screen), as a tap on a song does; back returns to Artists. It
  opens only when the random song did start (`shuffleAll` says so; audio focus can refuse).
- **My calls, open to change:** Shuffle stays on, so Now Playing shows it lit, and turning it off
  plays the library on in its own order from that song. The button is Settings' refresh button
  (`IconButtonSmall`: 32 dp, a 16 dp icon in `text.muted`, `AppIcons.SHUFFLE`, the same
  `settings.headerAction*` tokens; no new token), with TalkBack's name "Shuffle library"; a
  rescan's note sits left of it and ellipsizes before it, as in Music folders. No button when
  the library is empty (the empty pictures are unchanged). Now Playing's "3 / 10" takes
  thousands separators (`Display.count`), since the count can be 27,115 now.
- **A 27,000-song queue** (the M21's library) was new: queues were one album until now. The
  resume file holds the queue's paths, so it grows from a few hundred bytes to about 3 MB
  there (same format, nothing migrates). `Player.save` used to encode it on the main thread at
  every song change, pause and seek; now it gathers and encodes on a one-at-a-time IO
  dispatcher and skips a save a newer one has overtaken. The restore's path lookup moved off
  the main thread too (`AudioFoolApp.restoreQueue` resolves; `Player.restore(Restored)`).
  `PlayOrder.candidates` still lists the whole remaining order at each start (a list of up to
  27,000, a millisecond or so).
- **Checked:** 467 core tests (7 new) and 47 app tests (4 new, `ArtistsShuffleTest`: the tap,
  the button at the far right level with the label, a long note keeping it, none when empty).
  ThemeLab: the 44 Artists pictures that have artists changed (the button; both phones, both
  themes), compared zoomed before and after; 4 new `now-playing-library-shuffle` ("12,345 /
  27,115" by the title, Shuffle on); every other picture pixel-identical. Emulator (release
  build over 0.8.0): the button plays a random song and opens Now Playing on it, "2 / 832" (the
  whole sample library) with Shuffle lit, and back returns to Artists, still playing; Next
  jumps across artists; Shuffle off
  goes on in library order; a force-stop and restart reopens the same song, paused, "10 / 832",
  and Next carries on shuffled; with the SD card unmounted the queue was 823 (its 9 songs left
  out). **Not checked:** the M21's 27,000 songs (how long the tap and the restart take, and
  that song changes stay smooth): on its list.

0.8.0 and 0.8.1: the user reported "looks it's working on both devices" (2026-11-05): the
username-and-password sign-in and Listening History against the real Last.fm, on the S24 and
the M21. Ticked the sign-in and Listening History items; the finer ones (a wrong password, the
password manager, the period switch, row taps, offline, disconnecting) stay open.

2026-11-05, after 0.8.1: **builds go to the phones over USB, not GitHub** (the user's call,
"Install over USB only"). From 0.8.1 every APK carries AudioFool's Last.fm key and secret, and
the repo's releases are public; uploading 0.8.1 was stopped by Claude's safety check as a
credential leak, and the user chose USB installs (`adb install -r`, data kept) over publishing
it themselves. 0.8.0 stays the last GitHub release. The release routine is otherwise the same
(bump, the suite, `dist/AudioFool-<v>.apk`, the signer, "Show <v> notes"), with the install on
each phone in place of "Publish".

2026-11-05, 0.8.1 (versionCode 41; installed on the S24 and the M21 over USB the same day, **not published on GitHub**, see below): **Last.fm signs in with the username and password** (the user,
on the S24: Connect asked for the API key and secret, "I believe I already set this up … can
we make the login process … my last.fm username and password instead?"). They are kept per
phone, and the S24 had never been connected. Asked first, since it changes the spec and the
stored sign-in; the user chose it ("Yes, build it") after being told the trade-off:
- **Last.fm's sign-in for phone apps** (`auth.getMobileSession`: the name or email and the
  password in one signed POST over HTTPS) still needs an app's API key and secret, so
  **AudioFool's own are built in** from `~/.gradle/gradle.properties` on the build PC
  (`audiofool.lastfm.apiKey`, `audiofool.lastfm.secret`; outside the repo, put there by the
  user; a release build stops without them). The repo and its releases are public, so anyone
  with the APK can read them: that gives no way into an account (the password is needed), but
  misuse could get the key suspended (then a new build with a new key). Suggested to the user:
  build in the separate AudioFool-Android key, not the desktop's.
- **The password** is sent that once and kept nowhere: not stored, not in saved instance
  state (`remember`, not `rememberSaveable`), drawn as dots, offered to the phone's password
  manager (`ContentType.Password` / `Username`). The session is stored as before, with the
  key it was made with, so a phone connected earlier with a typed-in key stays connected.
- **Gone from the app:** the key and secret fields, "Get a key…", the browser approval and its
  polling. Core keeps `getToken` / `getSession` / `authorizeUrl` (ported from the desktop,
  tested). The desktop is unchanged.
- **My calls, open to change:** the form's line ("Sign in with your Last.fm username and
  password. AudioFool keeps the session Last.fm gives back, never the password."); the name
  filled in when connecting again; "Signing in…" left of the buttons while it's sent; the
  refusal wording (4: "Last.fm didn't accept that username and password."; 10 or 26: the app's
  key turned down, a new build needed); the username keyboard is the email one (Last.fm takes
  either).
- **Checked:** 460 core tests (2 new: the signed POST with name and password over HTTPS, error
  4) and 43 app tests (3 new: the refusal wording); ThemeLab: the four `settings-lastfm-connect`
  pictures changed (the new form), four new `settings-lastfm-signin-failed` (scrolled to the
  form's end), every other picture pixel-identical. `assembleRelease` without the key stops
  with the message. Emulator (a throwaway release with a dummy key from the command line, not
  kept): Connect shows the form, a typed name, the password as dots, Cancel closes it. **Not
  signed in** (that needs the user's own password, on the phones).

2026-11-04, 0.8.0 (released as pre-release [`v0.8.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.8.0), "AudioFool 0.8.0", versionCode 40, the same day): **Listening History**
(the user: "add this feature [the desktop's Last.fm Listening History page] to this Android
version", in the logo menu and the cover menu). The desktop's session 74, ported:
- **Core** (`core/.../scrobbling/`): `ListeningHistory.kt` (`ListeningPeriod`, `ListenerProfile`,
  `TopEntry`, `RecentPlay`, `ListeningJson`, and `HistoryTime`, the desktop's `ListeningTime`
  renamed because `core.playback.ListeningTime` is the phone's listening total),
  `ListeningMatch.kt`, and `LastFmApi`'s five unsigned GET reads (sharing the response reader
  with the signed POSTs; `LastFmTransport.get`). The desktop's 17 tests ported
  (`ListeningHistoryTest`), plus one for the phone's period words. `Display.count(Long)`.
- **The user's calls** (asked as short options): the period switch as **short words in one row**
  (7D · 1M · 3M · 6M · 12M · All, in equal slots; the desktop's words need about 440 dp, the M21
  has 343, and a scrolling row hid 12M), and a **song row opens its album with the song chosen**
  (as the desktop, over playing it). Artists open Albums, albums open Album, as asked.
- **My calls, open to change:** one column in the desktop's reading order (TOTALS, the switch,
  TOP ARTISTS, TOP ALBUMS, TOP TRACKS, RECENTLY PLAYED); the totals as Statistics' figures, two
  then three, in a panel "Totals"; the title "Listening History · <user>" as the desktop's window
  title; the chosen period `text.heading` at `dialog.emphasisWeight` (the desktop's), the slots
  48 dp high for touch; the rows are Statistics' bar rows with the artist under the bar
  (`listeningHistory.detailGap`); not connected, a LAST.FM panel row that opens Settings (the
  desktop links to its Last.fm window); a failed load says why, and a tap tries again (the
  desktop has no retry: you reopen the window). The song chosen on the Album screen uses the
  selected row look (`state.selectionBg`; the playing song keeps its own) and the list opens
  scrolled to it (`Route.Album.song`).
- **How it fetches:** `ListeningHistory` (app) is held by the app, not the screen, so coming back
  from a row finds it as it was. Each opening from a menu asks Last.fm afresh (the desktop's
  window does at each opening); within one, each period once. It reads with the saved API key
  and user name only (`LastFm.historyReader`), so it needs no session and no secret.
- **Tokens:** `listeningHistory.*` in `mobile-tokens.json`. The desktop's two token files copied
  again, unchanged: the desktop had added `listeningHistory.periodGap` and `genius.*` since the
  last copy (the parity test passes; the phone doesn't use either).
- **Checked:** 458 core and 40 app tests (8 new: rows, where a row leads, each period fetched
  once, not connected, a failure); ThemeLab: 32 new pictures (top, scrolled to the end, a period
  loading, loading, not connected, failed, the 1.3 font size, the album with a song chosen; both
  phones, both themes), the 8 menu pictures changed (the new row), every other picture
  pixel-identical. Emulator (release build over 0.7.4): the row in the logo menu and the cover
  menu, the not-connected screen, its tap to Settings, back to Listening History, back to Artists
  or Now Playing. **Not checked against the real Last.fm** (no key here): that's on the phones'
  lists.

2026-11-03, 0.7.4 (released as pre-release [`v0.7.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.4), "AudioFool 0.7.4", versionCode 39, the same day): **the cover menu ends with the logo menu's rows**
(the user: "add the Logo menu options to the resolution and Analyze pop-up", alphabetized with
the resolution at the top and the version at the bottom). Resolution, Analyze, Rescan Library,
Settings, Statistics, Theme, Version: Analyze already sorts before the logo menu's rows, so the
two menus share one piece (`AppMenuRows`) and the logo menu itself is unchanged. They behave as
under the logo: Theme keeps the menu open, the others close it; Settings and Statistics open
over Now Playing, and back returns to it. A rescan started here shows its progress in the
ARTISTS header, as before. The menu keeps `artMenu.width` (224 dp). Checked with ThemeLab (both
themes, both sizes) and on the emulator.

0.7.3: the user reported both fixes working on the phones (2026-11-03).

2026-11-03, 0.7.3 (released as pre-release [`v0.7.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.3), "AudioFool 0.7.3", versionCode 38, the same day): **two bugs reported from the phones**, neither
reproducible here (no DAC or FiiO on the emulator, the M21 not on adb), and neither caused by a
code change since 0.5.1 (the driver's mute and the FiiO receiver are as they were). The user
had no Debug readings to hand, so each fix is the best guess the code allows, plus the readings
that will settle it:
- **S24, a loud pop at the first Play with BP on** (the UA2 Plus, as before; not at a pause or
  between songs). The 0.4.6 checks that passed all took a DAC that had just been playing; this
  is a DAC that has sat idle. Now the first stream after a take stays muted **500 ms**
  (`UsbDriver.COLD_SETTLE_MS`) before the level, not 150 ms; rate changes keep 150 ms. Costs a
  third of a second at the first Play. The driver's note says how long it was muted ("playing
  44.1 kHz, … (muted 500 ms first)").
- **M21, "No Output" with headphones in, always, also after a replug with the app open.** A
  replug should have fixed a stale saved jack, so either FiiO's `type` no longer reads as a
  number (anything else read as 0, "No Output") or its broadcast no longer reaches the app (a
  FiiO update or mode?). Only a whole number now counts (`OutputNames.fiioJackOf`, tested);
  anything else is ignored. Debug's **FiiO jack** row adds what FiiO last sent, with the time and
  every extra, or "nothing from FiiO since the app started", which tells the two apart.
- **On the emulator** (release build, installed over 0.7.2): plays; a faked FiiO broadcast with
  `type=4.4mm` shows in the row and is ignored, `type=8` gives "type 8 → 4.4mm". The row is read
  when Debug opens, so reopen it after a plug or unplug.

2026-11-02, 0.7.2 (released as pre-release [`v0.7.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.2), "AudioFool 0.7.2", versionCode 37, the same day): **no molded lip at the bottom of the round buttons**
(the user, with an S24 screenshot: "a small fill at the bottom of these buttons"). It was the
bottom inset layer of the desktop's `shadow.controlButton` (`inset 0 -3px 0`) and
`shadow.playButton` (`-4px`). The desktop's token files stay unchanged; `RoundButton` leaves out
every inset layer with a negative y offset, keeping the top highlight and the drop shadow. My call,
open to change: the mini player's three buttons go with it, since they are the same
`RoundButton` and would otherwise no longer match Now Playing. ThemeLab: 100 pictures changed,
every one that shows the mini player or Now Playing (both phones, both themes); compared zoomed
before and after. The token sheet still draws the desktop's tokens whole.

2026-11-02, 0.7.1 (released as pre-release [`v0.7.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.1), "AudioFool 0.7.1", versionCode 36, the same day): **Now Playing's output readout higher on the S24**
(the user: "awkwardly close to the bottom of the screen"). `readoutLift` (0.17 of the spare
height, up to 20 dp, never more than `controlsLift`) is taken out of the gap between the controls
and the readout and put under the readout, so the readout rises 20 dp on the S24 and the six
buttons don't move (the user tuned them in 0.2.4–0.2.6). No spare height on the M21, so nothing
changes there: every M21 picture is pixel-identical; only the S24's Now Playing pictures changed
(14, both themes). My call, open to change: 20 dp ("a little", as before); the gap over the
readout is now 36 dp + the controls' 8 dp padding. Emulator: the readout sits well clear of the
gesture bar.

2026-11-02, 0.7.1 (released as pre-release [`v0.7.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.1), "AudioFool 0.7.1", versionCode 36, the same day): **the logo menu in alphabetical order, with the
version at the foot** (the user's request). Rescan Library, Settings, Statistics, Theme; then
"Version 0.7.0", from `BuildConfig.VERSION_NAME`. My calls, open to change: the version row is
information only, styled as the cover menu's resolution (label `text.muted`, value
`text.secondary`, a tap does nothing), with no divider above it; ThemeLab passes a fixed "0.7.0"
(`ArtistsScreen(version = …)`) so a version bump doesn't change the pictures. Only the four menu
pictures changed. Emulator (release build): the rows read in that order with "0.7.0"; Version
does nothing, and Rescan Library, Settings and Statistics each do what they did.

2026-11-02, 0.7.0 (released as pre-release [`v0.7.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.7.0), "AudioFool 0.7.0", versionCode 35, the same day): **a rescan you start shows its progress and its end**
(the user's request: "some sort of progress tracker so I know when it's done"). Before, a rescan
that found nothing showed nothing at all. The user chose header text (asked as short options, over
a bar under the header or a toast): the ARTISTS header and Settings' MUSIC FOLDERS header (left of
the refresh button) say "Checking for changes…", then "Reading tags: N of M" if files changed,
then for 4 s "Up to date · 3 new, 1 updated, 2 removed" ("no changes" when none, "· 1 folder
unavailable" when a folder can't be reached). Only rescans the user starts (Rescan Library,
Settings' refresh) show the checking and the end; the scan at start and a card mounting show
only "Reading tags" while reading, as before, so the start screen doesn't flash a note. My calls,
open to change: "new" is a file not shown before, "updated" a re-read one that was, "removed" a
shown one now gone (tracks of unticked folders count as neither, so unticking isn't "removed");
a rescan the user asked for that is replaced by another scan (a card mounted meanwhile) passes
its ask to the next. `RescanOutcome` in core (5 tests); `LibraryRepository.rescan(asked = true)`;
`scanHeaderText` / `ScanNote` in `LibraryScreens.kt`. `PanelHeader` now measures its label first
(then a spacer), so a long note ellipsizes instead of pushing the label out; every existing
ThemeLab picture is pixel-identical. New pictures: `artists-rescan-checking`,
`artists-rescan-done` (a long worst case), `settings-rescan-done`. Emulator (release build):
Rescan Library showed "Checking for changes…", then "Up to date · no changes" for about 4 s, then
nothing; with a FLAC copied in, "Up to date · 1 new"; with two removed, "Up to date · 2 removed".
Settings' refresh wasn't tapped on the emulator (same code path; ThemeLab shows its header).

2026-11-02, 0.6.0 (released as a pre-release the same day): **the readout gives the bit depth before the sample
rate** (the user's call): "Shanling UA2 Plus · 24-Bit · 96 kHz · Exclusive", as the quality line
over the seekbar has them. It is one line, so Settings' Bit-Perfect row changes with Now
Playing's. DoP keeps its words after the rate ("· 176.4 kHz · DSD over PCM · Exclusive"): it isn't
a depth. Only the eight ThemeLab pictures with a depth in the readout changed (BP on, both
screens).

2026-11-02, 0.6.0 (released as a pre-release the same day): **Now Playing's cover menu and Analyze** (the user's
request). A long press on the cover opens a menu at the finger: the cover's resolution (not
tappable), then Analyze. Choices (the user's, asked as short options): Analyze is **its own
screen** (not a sheet over Now Playing), and shows **the spectrogram and the opinion** as the
desktop does. Ported from `AudioFool.Core/Analysis` with their tests: `Fft`, `SpectrumScale`,
`SpectrumAccumulator` (+ `SpectrumAnalysis`), `QualityOpinion` (+ `QualityClaim`, `Verdict`,
`Finding`, `QualityFlag`, `Opinion`), 44 tests in `core/.../analysis/`; the desktop's
`AnalysisSampling` and its test are left out (the library-wide Quality Check), and so are the
window's clear buttons. `TrackAnalyzer` (BASS, in the app) follows `WaveformReader` and lets go
of a file on an ejecting card (`AudioFoolApp`'s `onEjecting`). The resolution reads "1200 × 1200"
like the desktop's art viewer. My calls, open to change: the menu's look is the logo menu's
(shared `MenuSurface` / `MenuRow`; the logo menu's pictures are unchanged); the info row's label
is `text.muted`; the menu closes when the song changes; the time axis's ticks are at least a
second apart (the desktop's NiceStep gave "0:00 0:00 0:01" on a 1 s file); the picture is 0.36
of the screen height (300 dp on the S24, 240 on the M21). New tokens: `artMenu.*`, `analysis.*`
in `mobile-tokens.json`; the desktop files are unchanged. On the emulator: the menu at the finger
("No cover" on the coverless test FLAC, "600 × 600" on the sample MP3), Analyze on the 60 s
pink-noise FLAC (done in under half a second, its quiet dip showing, "Consistent with FLAC ·
24-bit · 44.1 kHz"), the MP3 ("Can't tell": a test tone) and a DFF (axes to 88 kHz, "Read as
176.4 kHz PCM").

2026-10-30, after 0.5.1: **the docs slimmed** (the user's call). The decisions for 0.2.6 and
older and the session notes moved unchanged to [`progress-archive.md`](progress-archive.md), with
HANDOFF's history (the 0.2.x changes, "where things stood after session 2–5"). HANDOFF keeps the
current state, the build loop and the traps (526 → 421 lines); this file keeps the decisions
from 0.3.0 on, session 1's, and both test lists whole (1,859 → about 950 lines).

2026-10-30: **the test lists sorted** (the user's call). Of the 91 unticked items, the ones that
recorded a failure later fixed and confirmed (0.3.0 to 0.4.7's driver results), the ones a later
build answered (the S24's mixers and 44.1 kHz, the DAC's name in the readout, the M21's FiiO
bypass, first scans, Rescan), and the ones whose feature changed (the mini player preview, the
pre-driver "Rate Matched" readouts, plugging in moving the music to the DAC: it pauses since
0.4.4) are dropped. What is left is grouped by subject: 17 open on the S24 and 11 on the M21,
plus a "probably fine from daily use" group per phone (8 and 3) for the user to tick with one
word. The old per-version lists, with every result, are in `progress-archive.md`.

0.5.1 (2026-10-30), released as pre-release [`v0.5.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.1) ("AudioFool 0.5.1", versionCode 33): **a silent track keeps Android from stopping the app with
BP on.** 0.5.0's Debug on the S24 (the user): still a stutter at app switches; the longest gap
1006 ms, of which **the app stopped 1005 ms** (the heartbeat thread, which only sleeps, didn't
run either), a send to the DAC 7 ms, samples 1 ms. So the whole process is stopped for about a
second at the switch, though the media service is in the foreground. With BP off it never
stuttered, though an `AudioTrack` holds only about half a second: the difference is that with
BP on no `AudioTrack` plays, so to Android (and Samsung's power management, which spares apps
playing audio) the app isn't playing anything. Now `SilentTrack` plays alongside the driver: a
static `AudioTrack` looping 0.1 s of zeros (48 kHz, 16-bit stereo, media attributes), started
and paused with `UsbOutput` and paused when it's released. Only zeros, so nothing can be heard
wherever Android routes it, and static, so it costs the app no work. Debug's driver row says
"silent track: playing / paused" (or why it couldn't open). Emulator: a check build that started
it at launch showed in `dumpsys audio` as an `AudioTrack`, `state:started`, `mutedState:none`;
the shared path still runs one `AudioTrack` only, and none when idle. The 0.5.0 diagnostics stay
until the S24 confirms. If it still stutters with the silent track playing, the next look is
Samsung's battery setting for the app (Unrestricted).

0.5.0 (2026-10-28), released as pre-release [`v0.5.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.0) ("AudioFool 0.5.0", versionCode 32): **Rescan Library in the logo menu** (the user's request), a
fourth row under Statistics. It runs the same rescan as the refresh button in Settings' Music
folders and closes the menu; the scan runs in the background as before. Spec 4.1 updated; the
menu's four screenshots re-recorded (S24 and M21, Light and Dark: the row fits the menu's
width). Emulator: a FLAC copied in and then removed, each followed by Rescan Library from the
menu: Statistics went 832 → 833 → 832 tracks. A new feature, so 0.5.0 rather than 0.4.10;
0.4.10 was built but never published, and its diagnostics ride along:

**Finding where the stalls at an app switch come from.**
0.4.9 on the S24 (the user): 5 gaps from USB refills late, 0 from samples late; the longest
gap **1288 ms**, priority -19. No pop at pause, seek or skip; gapless works. So the samples
were always there and the thread was never demoted: the libusb event thread simply didn't run
for over a second, and no amount sent ahead covers that without slowing every pause and seek.
Three things can stop it that long: **the whole app stopped** (a freezer, Samsung's power
management), **a resubmit held up in the kernel** (usbfs takes the device's lock for a submit,
and another request to the DAC holds it; Android's control requests here time out at 1000
ms), or **waiting for the ring's lock**. Diagnostics only, no behaviour change: a heartbeat
thread at the same priority that only sleeps 5 ms, and the longest resubmit and lock wait.
Debug's driver row gets a second line, "longest gap N ms: the app stopped N ms, a send to the
DAC N ms, waiting for samples N ms", and the stop note carries the same. The fix follows from
which of the three matches the gap. Emulator: starts and plays as before (no DAC there, so the
new line can't show).

0.4.9 (2026-10-28), released as pre-release [`v0.4.9`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.9) ("AudioFool 0.4.9", versionCode 31): **less stutter at an app switch with BP on.** The user,
on 0.4.8: the music still stutters a little when going home or to any other app, only with BP
on ("not as bad as before", i.e. than 0.4.1). The Debug numbers weren't read, so both ways the
driver can run short got more room: **128 ms sent ahead to the DAC** (16 transfers of 8 ms; 64
since 0.4.2), the cause 0.4.2 found; and **a second of samples in the ring** (half a second
before), in case it's the output thread. A pause or seek waits for what was sent ahead before
its fade is heard, so it now takes about 150 ms instead of 85. Debug's driver row starts with
a plain line, "gaps since this stream started: N from USB refills late, N from samples late",
to say which one if it still stutters. Emulator: the shared output unchanged (the driver can't
run there).

0.4.8 (2026-10-28), released as pre-release [`v0.4.8`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.8) ("AudioFool 0.4.8", versionCode 30): **paused and out of sight, the DAC goes back at once.**
0.4.7 on the S24: a swipe from recents while paused still left the DAC silent (YouTube silent
after waiting), and opening AudioFool brought it back: so the app was ended at the swipe, still
holding the DAC, and the start-up repair fixed it. Samsung ends the app the moment it is swiped,
before `onTaskRemoved`'s give-back can run. The user's call (of: this; leave it, with the repair
at the next start; a second watcher process): **when AudioFool leaves the screen (home,
recents, screen off) while paused with the DAC held, it is given back at once**
(`MainActivity.onStop` → `Player.letGoOfDacNow`), so a swipe finds it Android's already. Play
after coming back takes it again (muted, no pop), and Android's media volume goes to 1 as at
every give-back. Paused from the notification or a headset while in another app keeps the
minute's wait. Emulator: play, pause, home, back and play again unchanged with no DAC.
**On the S24 (the user, 2026-10-28): all four checks pass**: after a swipe from recents YouTube
plays through the DAC; Play after home or screen off takes the DAC again in under a second; a
pause from the notification keeps the minute. With 0.4.7's results, the silent DAC after
Android ends the app is fixed.

0.4.7 (2026-10-28), released as pre-release [`v0.4.7`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.7) ("AudioFool 0.4.7", versionCode 29): **the DAC no longer goes silent when Android ends the app
while the driver holds it.** The user saw it after leaving AudioFool paused in the background
with BP on. The cause: at a pause the service dropped out of the foreground straight away, so
Android stopped or froze the app before the minute's give-back ran, and later ended it while it
still held the DAC. Ending the app releases the interfaces but leaves them with no driver (Linux
doesn't bind Android's again), and Android goes on writing to its old sound card: silence until
a replug. Two changes (the user approved the first):
- **The service stays in the foreground while the driver holds the DAC**, paused up to the
  minute before it is given back; the notification can't be swiped away for that minute, and
  can again once the DAC is Android's. **Swiped from recents while paused**, the DAC is given
  back at once, then the service ends (`Player.holdingDac`, `letGoOfDacNow`;
  `PlaybackService.stopWhenDacFree`).
- **At the app's start, a DAC left held is given back** (`UsbDriver.repairIfLeftHeld`, Android
  14+ only, so never on the M21): a plugged DAC the app has permission for whose audio interfaces
  have no kernel driver (`libusb_kernel_driver_active`, new `UsbStream.kernelDriverActive`) is
  claimed, put back to 0 dB and unmuted with Android's media volume lowered to 1, and reset, as
  every give-back is. Covers whatever else ends the app holding it (a crash, Android's memory
  killer, a force stop, installing a build over it). Nothing is stored for it. The Debug
  driver row says "found left held …" and then "given back (left held when Android ended the
  app)".
- Checked on the emulator only (no USB there): all tests and screenshots pass; with no DAC the
  start check does nothing and pause/notification behave as before (foreground while playing,
  not when paused).

2026-10-28, after 0.4.6 on the S24: **the driver plays clean** (no pop, sound after a pause,
volume from the user only, BP off and plugging in pausing). Spec §6.2's "S24 + USB DAC" row now
describes the driver (the plan said the spec would change once it did). Still open: a DSD file
through it, notifications while it holds the DAC, the M21 check.

0.4.6 (2026-10-28), released as pre-release [`v0.4.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.6) ("AudioFool 0.4.6", versionCode 28): **no pop when BP takes the DAC.**
0.4.5 gives the DAC back at 0 dB, so each take found it there and set the user's level only a few
tens of ms later, after reading the clock and the volume ranges; the DAC popped loudly as it
changed from Android's stream to the driver's. Now the DAC is muted straight after it is claimed
(before anything is read), stays muted through the rate and setting change, and is unmuted
150 ms after its stream starts (which starts paused, so the DAC locks to silence first). The
same at every rate change between tracks. A DAC without a mute control is turned to the bottom
of its range instead. The give-back is unchanged (no pop reported there; a mute could stay on
across the reset and leave Android silent).

0.4.5 (2026-10-28), released as pre-release [`v0.4.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.5) ("AudioFool 0.4.5", versionCode 27): **from 0.4.4's report.** Samsung
never touches the DAC's own volume controls (master −51 dB, channels 0 dB, with Android at 11 of
15): it turns a USB DAC down in its own software, which Android's `getStreamVolumeDb` doesn't
show. So:
- **Given back, the DAC goes back to 0 dB** (`UsbVolume.atZeroDb`), as Android expects it, and
  **Android's media volume is first lowered to 1 if it is higher** (and again once Android has
  the DAC back, in case it restores its own). Through Android the volume can then go all the
  way up (0.4.4 left the DAC at the BP level: too quiet at full Android volume), and what plays
  next starts quiet (0.4.2's blast was the DAC at 0 dB with Android at 9 of 15). BP off still
  pauses. The level in BP is unchanged: the user's, from the keys.
- **The silence after a minute's pause** isn't explained (0 underruns, 0 transfer errors). The
  report now counts failed packets inside transfers (the host's per-packet status, which 0.4.x
  didn't look at), gives the DAC's clock and feedback count in each "stopped" line, and reads
  the volume and mute back from the DAC right after setting them.

0.4.4 (2026-10-28), released as pre-release [`v0.4.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.4) ("AudioFool 0.4.4", versionCode 26): **the volume from the user only,
and pauses where the volume could jump.** After 0.4.3 came in at full volume twice on the S24.
The report showed why: Samsung's media volume (9 of 15, 3 of 15) "takes 0 dB off" a USB DAC; it
turns the DAC down inside the DAC, in controls the driver didn't set. The user's calls
(2026-10-28), all three the recommended ones:
- **Where the volume starts**: the first time the driver takes the DAC after the app starts, the
  quietest step that is heard (step 1, 59 dB below the DAC's loudest); after that, where the keys
  last left it (kept while the app runs, not saved). Nothing read from Android or the DAC
  decides it (`UsbVolume.startStep`, tested). The driver sets **every** volume control: the
  level on the master, the channels' own controls opened to 0 dB, so the master alone says how
  loud. The "taken" note lists each control as found.
- **BP off while playing pauses** (with the driver holding the DAC); the user plays on when
  ready. A give-back now waits up to 8 s for Android to set the DAC up again and says whether
  it did.
- **Plugging a USB DAC or headphones in while music plays pauses it** (spec 6.1 updated). The
  driver's own give-back isn't a plug (its window is now 12 s).
- 0.4.3's `startingValue`/`stepAtOrBelow` and the Android-curve reading are gone.

0.4.3 (2026-10-27), released as pre-release [`v0.4.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.3) ("AudioFool 0.4.3", versionCode 25): **never louder when the DAC
changes hands.** In 0.4.2, Bit-Perfect off while playing came back far too loud on the S24: the
give-back put the DAC's own volume back to 0 dB and relied on Android's media volume (which
0.4.2 moved with the keys) to bring it down, and it didn't. Why exactly isn't known; the new rule
doesn't depend on it:
- **Giving back**: the DAC's volume stays where the keys left it (only unmuted). Android's media
  volume can only take more off, so Bit-Perfect off is never louder than on; it may be quieter,
  and then Android's volume goes up.
- **Taking**: the DAC goes down by what Android's media volume was taking off, from Android's
  own curve (`AudioManager.getStreamVolumeDb`), to the nearest step not louder
  (`UsbVolume.startingValue`, `stepAtOrBelow`, tested). 0.4.0 to 0.4.2 used a 1 dB-per-step guess
  of the curve, which was louder than Android's: the user turned −28 dB down to −55.
- **Android's media volume is no longer moved by the keys** (0.4.2's mirroring is gone).
- **The notes give both volumes in dB** at each take and give-back, and once Android has the DAC
  again, to show whether Android changes its own volume then.
- The DAC keeps its lowered volume after a give-back, also if it is later plugged in elsewhere.

0.4.2 (2026-10-27), released as pre-release [`v0.4.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.2) ("AudioFool 0.4.2", versionCode 24): **two problems from 0.4.1 on the
S24.**
- **Louder after a hand-back**: each take started from Android's media volume, which the volume
  keys hadn't moved (they move the DAC). Now the keys' step is kept and used again, unless
  Android's volume was changed since; and Android's media volume follows the keys while the
  driver holds the DAC (nothing hears it then), so once the DAC is Android's again it plays
  about as loud. "About": Android's curve isn't the driver's 1 dB steps.
- **A few seconds' silence on leaving the app**: with an `AudioTrack`, Android's own audio
  service did the timely part; with the driver the app refills the DAC's transfers itself, and
  only 16 ms were in flight. Now 8 transfers of 8 ms (64 ms). A cut (seek, skip) waits for what
  is in flight before its fade is heard, so it now waits about 85 ms instead of 40.
- **The report says why if it happens again**: the longest gap between refills, how many came
  after the DAC had played all in flight, and the event thread's actual priority; each stream's
  summary as it stops. The Driver row keeps 12 notes.

0.4.1 (2026-10-27), released as pre-release [`v0.4.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.1) ("AudioFool 0.4.1", versionCode 23): **two fixes from the S24's 0.4.0
results.** The driver played well (44.1 kHz, hi-res, gapless, volume keys, unplug, BP off).
- **The seek flash**: until the output stage has carried out a seek, the player gives the place
  being sought (`OutputStage.seeksDone` against `Player.seeksAsked`), not the old heard place.
  It was the gap between asking and the 5 ms fade and flush, on any output.
- **The Driver row keeps its last eight notes with times**: the report showed only the last
  give-back, so whether a minute's pause had handed the DAC back couldn't be told.

0.4.0 (2026-10-27), released as pre-release [`v0.4.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.0) ("AudioFool 0.4.0", versionCode 22): **true Bit-Perfect on the S24, the
driver.** The user said to start it after 0.3.1 proved the give-back. All in
`design/usb-bit-perfect.md` > "The driver (0.4.0)". In short:

- **Where**: Bit-Perfect on, a USB DAC, Android 14+, no bit-perfect mixer entry (the S24). The
  M21 and the emulator's pretend DAC keep their old ways. A track the driver can't play goes
  the mixer's way ("Rate Matched"), the DAC handed back first.
- **libusb 1.0.30** vendored unchanged in `third_party/libusb` (signature checked), built as its
  own `libusb-1.0.so` (LGPL, dynamic); credited in README. The CMake project now builds C too.
- **The output stage writes to a `PcmOutput`**: `AudioTrackOutput` (as before) or `UsbOutput`
  (the driver). Gapless joins, the 5 ms seek fade, the heard position and underruns work the
  same through both. A DAC lost mid-song pauses (`onOutputLost`); a seek while handed back is
  kept for Resume.
- **Given back** at BP off, the player stopping, a minute paused (`Player.LET_GO_OF_DAC_MS`),
  or a track it can't play: volume restored, released, reattached, reset, then it waits for
  Android to set the DAC up again; the drop and return don't pause or count as a plug.
- **Volume**: the DAC's hardware volume in 60 steps of 1 dB (0 mutes), started from Android's
  media volume; the volume keys move it through the media session (remote volume).
- **"Always open AudioFool"**: a `USB_DEVICE_ATTACHED` filter on `UsbAttachActivity`, which shows
  nothing; it lets the driver use the DAC at every plug without asking.
- **Debug**: the USB DAC panel's 0.3.x give-back tests are gone (they'd take the DAC from the
  driver); a **Driver** row shows what it does (each second: the clock's feedback, underruns,
  errors, volume), and the report carries it.
- **Not checked anywhere yet**: the emulator has no USB. Checked there: playback, skips, seeks
  (faded), pause/resume and gapless joins through `AudioTrackOutput`, unchanged; all tests
  pass (19 new core tests on the driver's rules), screenshots unchanged.

0.3.1 (2026-10-27), released as pre-release [`v0.3.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.1) ("AudioFool 0.3.1", versionCode 21): **a test of giving the DAC back.**
0.3.0's "Take it and give it back" left the DAC silent until a replug (the user, on the S24).
The user chose a test build before the driver. Debug's USB DAC panel now has two rows, each
one test: **Give it back with a USB reset** (reattach Android's driver, then `USBDEVFS_RESET`)
and **Give it back after a wait** (pause, wait 5 s for Android to close its output, then take
and give back as 0.3.0 did). Each shows the sound card Android plays to (the USB output's
address, "card=N;device=0") and the kernel's list of sound cards, if the app may read it,
before, while held and after. The reset may well not be seen as a replug (Linux usually just
rebinds the driver on a reset); the card numbers will say why either works or not. Also fixed:
a second permission answer no longer overwrites what was read with "not allowed".

0.3.0 (2026-10-27), released as pre-release [`v0.3.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.0) ("AudioFool 0.3.0", versionCode 20): **true Bit-Perfect on the S24, step 1 (look only)**.

- **What it takes** is written up in `design/usb-bit-perfect.md`: the app drives the DAC itself
  over USB (as USB Audio Player Pro does), since the S24 offers no bit-perfect mixer entry and
  no 44.1 kHz. The user chose (2026-10-27): **libusb**, vendored unchanged (LGPL-2.1, its own
  `.so`); **volume** by the DAC's own hardware volume, else the app's software volume with
  "Exclusive" only at 100%; **hand the DAC back** to Android about a minute after a pause and
  at once on BP off or stop; and **a look-only build first**, so the driver is built against
  the user's real DAC. libusb is not in yet (it comes with the driver).
- **Statistics > Debug > USB DAC** (new panel): the DAC's name and numbers; **Read the DAC**
  (asks Android once from a tap, not at every plug: the manifest's "always" filter comes with
  the driver) shows its descriptors parsed; **Take it and give it back** claims the audio
  interfaces with force (Android's own driver lets go), asks the UAC2 clock for its rates and
  the feature unit for its volume range (class requests to an interface need it claimed), then
  releases them and binds Android's driver again with `USBDEVFS_CONNECT` (`usbdac_jni.cpp`, new
  small library `audiofoolusb`), timing both changes as Android sees them. It pauses the music
  first. **Send the report** saves `Download/AudioFool-USB-DAC.txt` and opens the share sheet
  (the descriptors in words, the test's results, the raw bytes in hex).
- **Core:** `core/.../usbaudio/UsbAudioDescriptors.kt` (new): UAC1 and UAC2 descriptors
  (clocks, feature units, terminals, each playback setting's format, rates and endpoints),
  UAC2 RANGE replies (`UacRanges`), the report's lines (`UsbAudioReport`). New, with tests
  built from the specs (`UsbAudioDescriptorsTest`, 11); the user's own DAC's bytes join them.
- **Manifest:** `android.hardware.usb.host`, not required (the M21 doesn't need it).
- **Checked:** `:core:test`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug` (no picture
  changed: ThemeLab draws Debug without the panel), `:app:assembleRelease`. On the emulator
  (no USB): the library loads, the panel says "none plugged in", a tap says "no USB DAC
  plugged in". Everything with a DAC is on the S24 list.

Decisions for 0.2.6 and older (0.2.x, 0.1.x and sessions 2–5) are in
[`progress-archive.md`](progress-archive.md), newest first. Session 1's still stand:

- New repo `C:\AudioFool-Android` (GitHub `mpaulrenlund/AudioFool-Android`); the desktop repo is untouched.
- BASS for decoding, AudioFool's own `AudioTrack` output stage, and a media session for the system controls.
- All-files access with plain folder paths. Music on internal storage and the SD card; about 26,000 tracks.
- Desktop colour token files copied unchanged; mobile sizes and mobile-only colours in `mobile-tokens.json`.
- Last.fm reuses the desktop's API key, entered in the app, never committed.
- Version 1 includes lock screen and notification controls, headset buttons, gapless, DSD, Last.fm, resume, and a library cache. Playlists are not in version 1.
- **No USB connection to either device** (the user's call, 2026-10-24). Both the S24 and the M21 are tested through GitHub pre-release APKs that the user installs and reports back on. The emulator covers what it can before each hand-over; what only a real device can show goes on the lists below.
- **One signing key from the start** (2026-10-24): every APK the user gets, test builds included, is signed with the same release keystore, kept outside the repo, so each build installs over the last without wiping the cache or settings.
- Ask before big choices only (new dependencies, spec changes, stored data, releases).
- **The repo is public** (the user's call, 2026-10-24; **private since 2026-11-05**, see 0.9.0 above). This PC pushes and releases as `marcusrenlund`, a collaborator with write access. Session 3 checks BASS's terms for shipping its libraries in a public repo.

## To test on the S24

Sorted on 2026-10-30, after 0.5.1. Only what is still open is here; the per-version lists with
every result up to 0.5.1 are in [`progress-archive.md`](progress-archive.md). A new build adds
its own section at the top, as before. (From …) says which build first asked.

### The cover menu's rows (from 0.15.1)

- [ ] Tap the cover: Resolution, Analyze, Bit-Perfect, Genius, Theme and Version, and no Listening
  History, Rescan Library, Settings or Statistics (they're in the logo menu, which still has all six).

### Version opens the releases page (from 0.15.0)

- [ ] Logo menu > Version: the releases page opens in your default browser (not GitHub's app,
  if you have it), and you see the builds (that browser signed in to GitHub). Back returns to
  AudioFool. The cover menu's Version does the same.

### The mini player's buttons (from 0.15.0)

- [ ] Play something with a colourful cover and go back to a list: are the mini player's three
  icons and the ring round Play/Pause in the same colour Now Playing shows? Does the glow show
  while playing and go when paused (Dark)? A black-and-white cover keeps the theme's blue.
- [ ] Skip through an album: does the colour stay put between its songs rather than blinking?

### No glow at the top (from 0.15.0)

- [ ] **Dark theme**: the top of every screen, behind the status bar, is the plain window
  colour, with no blue (or, on Now Playing, cover-coloured) glow. Does the top look right
  without it, and does the status bar's text still read well?

### The five layout requests (from 0.14.0)

- [ ] **Artists**: the search field starts at the left and the logo is at the right; its menu
  opens under it.
- [ ] **Albums**: the shuffle button at the right of ALBUMS plays that artist shuffled and opens
  Now Playing; back returns to Albums.
- [ ] **Now Playing's controls**: Repeat over Shuffle at the left (44 dp, from 34), Previous,
  Play/Pause and Next centred. Previous and Next are smaller than before (60 dp, from 70) to
  make the room: do they still feel easy to hit? Are Repeat and Shuffle easy to reach with a
  thumb on the left?
- [ ] **Colour**: with a colourful cover, Previous and Next take its colour, as Play/Pause does.
- [ ] **The cover menu**: a tap on the cover opens it. Any opened by accident, while swiping
  to skip or just holding the phone?
- [ ] **Bit-Perfect in the menu**, with the DAC plugged in: the row has a check while on; a
  tap takes the check away and Bit-Perfect goes off (the music pauses, as BP off always
  did); another tap brings it back. Unplugged (the speaker, Bluetooth), the row is greyed.

### Now Playing's ideas (from 0.13.0)

- [ ] **The colour goes further**: with a colourful cover, Repeat and Shuffle when on, the
  artist's name and the faint glow at the very top are in the cover's colour too. Does the
  artist's name read well in it, on bright and dark covers?
- [ ] **Paused and playing**: paused, Play/Pause has its ring only; playing, the glow comes on.
- [ ] **Scrubbing**: drag the seekbar; the time is in a bubble over your thumb, and it stays
  inside the screen at both ends. Is it clear of your thumb?
- [ ] **Skipping**: within an album the cover no longer blinks; to another album, the old
  cover stays for a moment, then the new one. Does that moment feel right?
- [ ] **Taps**: the artist's name opens their albums, the album's name the album; back returns
  to Now Playing. Any opened by accident, while swiping to skip or just holding the phone?
- [ ] **Presses**: the round buttons lighten under your thumb, here and on the mini player.
- [ ] **Light theme**: Play/Pause has a ring in the cover's colour.

### Now Playing's cover colour (from 0.12.0)

- [ ] **A few albums with very different covers**: the seekbar's played part, the Play/Pause
  icon and the glow round it are in the cover's colour. Does the colour picked look like the
  cover's, and is it the one you'd have picked?
- [ ] **Hard covers**: a black-and-white one (should stay the usual blue), a very dark one, a
  very pale one, one with many colours. Anything hard to see against the button or the bar?
- [ ] **Skipping within an album and to another album**: no flash of blue between songs; the
  colour changes with the cover.
- [ ] **Light theme** (logo > Theme): the same covers; the colours come out darker, and
  Play/Pause has no ring or glow there, as before.

### Albums' year strip (from 0.11.0)

- [ ] An artist with more albums than fit: the year strip at the right, "’97" style, "?" at the
  foot if some albums have no year; a tap or a slide jumps to that year's first album, with a
  tick at each year and the full year in the bubble. An artist whose albums all fit: no strip,
  the rows full width.

### The Album page's track numbers (from 0.10.1)

- [ ] An album: the numbers start at the cover's left edge, left-aligned, and the titles sit
  closer to them; the playing song shows ▶ instead of its number. An album of 100+ tracks: the
  numbers fit, none cut off.

### Genius (from 0.10.0)

The emulator can't reach genius.com from this PC (Zscaler), so this is its first live run.

- [ ] Long press the cover in Now Playing > **Genius**: the song's header, "Looking up on
  Genius…", then the page (ABOUT, QUESTIONS & ANSWERS, ANNOTATIONS, CREDITS, CONNECTIONS,
  COMMENTS: whichever the song has). Try a well-known song and a "(Remastered)" one.
- [ ] How often a GENIUS row says "Couldn't load the Genius page. Genius answered HTTP 403…" or
  "Genius didn't send the … this time" (Genius's bot check refusing the phone). Tap it to try
  again.
- [ ] A song Genius doesn't have (an instrumental, a game soundtrack): "Genius has no page that
  matches this song." and CLOSEST ON GENIUS; a tap on one loads it under the same header.
- [ ] **Show 50 more** under the comments (a popular song); **Open on Genius** and a link in the
  text open the browser (or Genius's app).
- [ ] Next song while Genius is open: the screen stays on its song; back returns to Now Playing.
- [ ] Wi-Fi and mobile data off: the failure row with the reason; back on, a tap loads it.

### The shuffle button on Artists (from 0.9.0)

- [ ] The shuffle icon at the right of the ARTISTS header: a tap starts a random song and opens
  Now Playing on it; back returns to Artists.
- [ ] Now Playing: Shuffle lit, the count "N / 8,127" (the whole library); Next jumps around the
  library.
- [ ] Shuffle off in Now Playing: the next songs follow the library's order from there.
- [ ] Close AudioFool and reopen it: the same song, paused; Next carries on shuffled.

### Listening History (from 0.8.0)

Only the phones have the Last.fm key, so this is its first run against the real Last.fm.

- [x] **Logo > Listening History**: "Listening History · <you>", then TOTALS (Scrobbles,
  Scrobbling since, Artists, Albums, Tracks) and the top lists on 12M. Do the figures and lists
  match your last.fm profile and the desktop's window? If a list comes back empty or wrong, say
  which.
- [ ] **The period switch**: 7D, 1M, 3M, 6M, 12M, All. The first time on each, "Loading…" for a
  moment; going back to one is instant.
- [ ] **Taps**: an artist opens its albums, an album its album, a song its album with the song
  highlighted and in view. Back returns to Listening History where you left it. Rows you don't
  have in the library do nothing.
- [ ] **RECENTLY PLAYED**: the song playing now says "Now playing" at the top; the times
  ("12 min ago", "Yesterday") look right.
- [ ] **From Now Playing**: hold the cover; Listening History is after Analyze, and back returns
  to Now Playing.
- [ ] **Offline** (airplane mode): "Couldn't load your listening history" with the reason; a tap
  tries again once you're back online.

### The cover menu with the logo menu's rows (from 0.7.4)

- [ ] **Long press on Now Playing's cover**: Resolution, Analyze, Rescan Library, Settings,
  Statistics, Theme, then the version. Settings and Statistics open, and back returns to Now
  Playing; Theme switches with the menu still open.

### The pop at the first Play (from 0.7.3)

- [x] **BP on, the DAC idle a while** (freshly plugged in, or the app just opened): press Play.
  No pop? The music starts about a third of a second later than before. If it still pops,
  Statistics > Debug, the USB driver row: its notes with their times.

### The round buttons (from 0.7.2)

- [ ] **No fill at the bottom** of Now Playing's six buttons or the mini player's three.

### The logo menu and Now Playing's readout (from 0.7.1)

- [ ] **The logo menu**: Rescan Library, Settings, Statistics, Theme, then "Version 0.7.1".
- [ ] **The output readout** under the controls sits higher, no longer close to the bottom edge;
  the six buttons are where they were.

### Rescan progress (from 0.7.0)

- [ ] **Logo > Rescan Library**: the ARTISTS header says "Checking for changes…", then "Up to
  date · no changes" for a few seconds. Add or delete an album first and it should name them
  ("Up to date · 12 new"). Also Settings' refresh button: the same note left of it.

### The cover menu and Analyze (from 0.6.0)

- [ ] **The long press**: on Now Playing, hold the cover. A tick, and the menu at your finger
  with the cover's resolution and Analyze. A quick sideways swipe still skips, without the menu.
- [ ] **Analyze a long hi-res FLAC and a lossy-sourced one, if you know one**: how long the
  reading takes, that playback carries on without a stutter meanwhile (with BP on through the
  DAC too), and whether the verdict matches the desktop's for the same file.

### Bit-Perfect through the driver

- [ ] **A DSD file with BP on** (from 0.4.0). A DSF or DFF: does the DAC show DSD (or DoP), and
  does it sound right, not hiss? A DSD64 file goes to the DAC as DoP at 176.4 kHz. A
  DSD128 file too, if you have one (352.8 kHz). Pausing it may click as the DAC drops out of
  DSD (a known gap).
- [ ] **A notification or a navigation prompt while the driver plays** (from 0.3.0 and 0.0.5).
  Is it heard, and where: through the DAC, the speaker, or not at all? Does the music pause,
  get quieter, or play on?
- [ ] **The USB-C 3.5 mm adapter's name** in the readout (from 0.2.5). The Shanling's own name
  already shows ("Shanling UA2 Plus · 48 kHz" on 0.4.0).
- [ ] **BP greyed on the speaker and Bluetooth** (from 0.0.5). Unplug the DAC: BP and the
  Settings chip go dim and can't be tapped. With Bluetooth connected: the same, and the readout
  names Bluetooth. Connecting Bluetooth while playing with BP on: it plays on there.

### Everyday playback

- [ ] **The lock screen and the notification** (from 0.0.4). While playing, lock the phone: the
  cover, title, artist, play/pause, previous, next and a seekbar, all working. The same in the
  shade. Paused, the notification can be swiped away; tapping it opens Now Playing.
- [ ] **Bluetooth headset buttons** (from 0.0.4). Play/pause, next and previous from the headset
  (and a watch, if you use one), also after a restart with nothing played yet: it resumes where
  you left off. Turning the headset off pauses.
- [ ] **Interruptions** (from 0.0.4 and 0.1.0). A call or the voice assistant pauses it and it
  carries on after; a navigation prompt lowers it briefly; another music app starting stops it.
- [ ] **Resume** (from 0.0.4). Seek into a song, pause, swipe the app away from recents, open it
  again: the mini player shows the song, paused, at the same place; Play carries on with the
  rest of the album.
- [ ] **Battery and heat** over an hour of playing with the screen off (from 0.0.4), with BP on
  and with it off.
- [ ] **Font size** one or two steps up in Android's Display settings (from 0.1.0): Artists, an
  album, Now Playing and Settings still fit.

### Last.fm sign-in with your username and password (from 0.8.1)

- [x] **Connect.** Settings > Last.fm > Connect: Username and Password (dots), then Connect.
  "Signing in…", then "Last.fm: connected as <you>" with a green dot. Does Samsung Pass or
  Google offer to fill or save the password?
- [ ] **A wrong password**: "Last.fm didn't accept that username and password." over the
  fields; fix it and Connect works.
- [ ] **Disconnect and connect again**: the form asks for the password again (it was never
  saved).
- [x] **Listening History** then shows your history (the 0.8.0 items above).

### Last.fm (from 0.1.0)

- [ ] **Scrobbles reach your profile.** "Scrobbling now" when a song starts; the scrobble at
  its start time once past half (or four minutes). Seeking ahead doesn't count.
- [ ] **The offline queue.** Airplane mode, two songs past the threshold, airplane mode off,
  play anything: both scrobbles appear with their own times.

### The library

- [ ] **Search ignores accents** (from 0.0.2): "bjork" finds Björk.
- [ ] **Folders** (from 0.0.1). Add Folder… picks another folder, shown as "Internal storage ·
  name". Unticking hides its artists at once; ticking brings them back without a rescan.
- [ ] **Statistics against the desktop's** (from 0.1.0): the figures and the Formats line (the
  desktop counts every library, the phone its own folders).

### Seen in daily use: all fine

**All fine on both phones** (the user, 2026-10-30). Never reported one by one before that.

- [x] The launcher icon (the PlayStation logo on white).
- [x] Artists with their newest album's cover; covers filling in as you scroll; smooth scrolling.
- [x] Artist → Albums → Album and the back gesture held halfway (the screen underneath shows);
  the artist still highlighted at the same place.
- [x] Theme switching on every screen, the status bar readable in both.
- [x] Covers without a frame; the one-line quality ("FLAC · 1,901 kbps · 24-Bit · 48 kHz").
- [x] Now Playing's spacing (no big gap over the quality line) and a smooth, gliding seekbar.
- [x] Every format you have plays (FLAC, MP3, AAC, ALAC, Opus).
- [x] Gapless through the speaker and Bluetooth, not only the DAC.

## To test on the M21

Sorted on 2026-10-30, as the S24's list above.

### The cover menu's rows (from 0.15.1)

- [ ] As on the S24. The menu is four rows shorter: does it still fit by the cover wherever you tap?

### Version opens the releases page (from 0.15.0)

- [ ] As on the S24.

### The mini player's buttons (from 0.15.0)

- [ ] As on the S24, in Light as well: a ring in the cover's colour, and no glow.

### No glow at the top (from 0.15.0)

- [ ] As on the S24.

### The five layout requests (from 0.14.0)

- [ ] As on the S24. Repeat and Shuffle grew only to 35 dp here (the M21's Play/Pause is
  shorter, and they are as tall as it together): big enough, or worth a slightly smaller
  cover to make them bigger?
- [ ] **Bit-Perfect in the menu** on the 3.5 mm or 4.4 mm jack: the check comes and goes
  with a tap, and the readout follows (Bit-Perfect / Resampled).
- [ ] **Albums' shuffle** on an artist with many albums: how quickly Now Playing opens.

### Now Playing's ideas (from 0.13.0)

- [ ] As on the S24, on the shorter screen: the time bubble sits between the album's name and
  the quality line; does it cover anything you want to see while scrubbing?
- [ ] Skipping through an album with large covers: the cover stays put, with no blink.

### Now Playing's cover colour (from 0.12.0)

- [ ] As on the S24, across the full library: a spread of covers, and whether opening Now
  Playing or skipping feels any slower (the colour is worked out when the cover is read).

### Albums' year strip (from 0.11.0)

- [ ] As on the S24, on the shorter screen: the artist with the most albums in the full
  library; if its years are too many to write them all, every few is written and a slide still
  stops on each year (the bubble names it).

### The Album page's track numbers (from 0.10.1)

- [ ] As on the S24, on the narrower screen; the full library's longest albums (100+ tracks, if
  any) fit their numbers.

### Genius (from 0.10.0)

- [ ] Long press the cover > **Genius**: the page, as on the S24; the header and the entries'
  notes ("Mar 7, 2017 · 297 votes") fit the narrower screen; the credit labels wrap, not clip.
- [ ] A song from the SD card, then from internal storage; and with the card out, the
  unavailable song still opens Genius (it needs only the tags).
- [ ] How often a 403 or a "didn't send" row comes, as on the S24.

### The shuffle button on Artists (from 0.9.0)

The M21's 27,000 songs are the first queue that size.

- [ ] A tap on the shuffle icon (right of the ARTISTS header) starts a random song within a
  moment, and Now Playing opens on it with "N / 27,115" and Shuffle lit.
- [ ] Song changes, pauses and seeks stay smooth while the whole library is queued (each saves
  the queue, about 3 MB, in the background).
- [ ] Close AudioFool (swipe it from recents) and reopen it: the same song comes back paused,
  and Next carries on shuffled. How long until the mini player shows it?
- [ ] With the SD card out, the shuffle plays only what is on the internal storage.

### Listening History (from 0.8.0)

- [x] **Logo > Listening History** on the M21's smaller screen: the five figures, the six
  period words in one row, and the lists, against your last.fm profile. A tap on a song opens
  its album with the song highlighted.
- [ ] **The cover menu**, now nine rows with Listening History: it still fits the screen
  wherever the cover is pressed.

### The cover menu with the logo menu's rows (from 0.7.4)

- [ ] **Long press on Now Playing's cover**: the same seven rows, and the menu fits the M21's
  screen wherever the cover is pressed (it moves up to stay on it).

### The output row (from 0.7.3)

- [x] **Headphones in, Now Playing's output row**: "3.5mm" or "4.4mm", not "No Output"? Pull
  them out and plug them back in with the app open, then Statistics > Debug, the **FiiO jack**
  row: what does "last from FiiO" say (or "nothing from FiiO since the app started")?

### The logo menu (from 0.7.1)

- [ ] **The logo menu**: Rescan Library, Settings, Statistics, Theme, then "Version 0.7.1", and
  the menu fits the screen. Now Playing looks exactly as before.

### Rescan progress (from 0.7.0)

- [ ] **Rescan the full library**: Logo > Rescan Library. How long "Checking for changes…"
  stays up with ~27,000 files before "Up to date" shows. With the SD card out, the end should
  say "· 1 folder unavailable".

### The cover menu and Analyze (from 0.6.0)

- [ ] **Analyze on the M21's slower chip**: a 24/96 or 24/192 FLAC from the SD card. How long
  it takes, and that playback doesn't stutter meanwhile.
- [ ] **Pull the SD card while Analyze reads a song on it**: the app keeps running, and the
  screen says the card was removed.

### Playback

- [ ] **Nothing changed since 0.4.0** (from 0.4.0). The output stage writes through a new layer
  for the S24's driver; the M21 should play exactly as 0.2.6 did. BP on, an album through the
  headphone out: gapless, the readout as before ("3.5mm · 24-Bit · 96 kHz · Exclusive"), seeks
  without clicks.
- [ ] **DSD** (from 0.0.5). A DSF or DFF plays, as PCM at 88.2 kHz (no DoP to the M21's DAC).
- [ ] **Bluetooth** (from 0.0.5). BP greyed; the readout names Bluetooth.
- [ ] **A USB DAC on the M21**, if you have one to try (from 0.0.5): BP works the M21's way;
  report the readout.

### The SD card and the full library

- [ ] **Pull the SD card while a song on it plays** (from 0.1.0). It stops and shows paused; the
  card's artists stay, greyed. Card back, Play: it carries on from where it stopped. The same
  with the card ejected from Android's Settings, and with the music paused.
- [ ] **The card out at start** (from 0.0.1, 0.0.2). Settings shows "SD card · Music" greyed,
  "Not available"; the card's songs listed, greyed, and can't be tapped; the LIBRARY line says
  "Unavailable: SD card · Music (N tracks kept)". Card back: "Up to date", nothing removed.
- [ ] **Cold start with the full library** (from 0.0.1), three times: Statistics > Debug > Cold
  start and Cache. The library should be on screen at once.
- [ ] **Rescan with the full library** (from 0.0.1): Logo > Rescan Library; Last scan should read
  about 0 and reuse every track.
- [ ] **Statistics** with the full library (from 0.1.0): the figures against the desktop's (499
  artists · 2,376 albums · 26,795 tracks when last seen), and how long the screen takes to open.
- [ ] **Scrolling, the letter strip and Search** with ~26,000 tracks (from 0.0.2, 0.2.0): flings
  smooth; a slide down the strip keeps up and its letters fit the M21's shorter screen; Search
  keeps up with typing.
- [x] **Last.fm** (from 0.1.0): connected on the M21 too (a second session for the same user),
  scrobbles arriving. From 0.8.1: if the M21 was connected before (with the key typed
  in), it stays connected after the update; if not, Connect asks for the username and password.

### Seen in daily use: all fine

**All fine** (the user, 2026-10-30).

- [x] Now Playing's bigger cover, with the readout still fitting under the controls (also at a
  larger font size).
- [x] Now Playing's controls: Repeat and Shuffle side by side over BP.
- [x] The folders list: the internal and the SD card's `Music` folders, the card's shown as "SD
  card · Music".

## Session notes

The notes for 0.2.3 and older are in [`progress-archive.md`](progress-archive.md). Later
versions record their notes in the decisions above.
