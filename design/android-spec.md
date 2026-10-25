# AudioFool Android — design spec

The Android version of AudioFool, for a Galaxy S24 Ultra (Android 17) and a FiiO M21 (Android 13). It keeps the desktop's two themes, PS1 Light and PS2 Dark, and the desktop's colours, unchanged.

This file is the source of truth for layout and behaviour. Numbers are in `mobile-tokens.json`; colours, shadows and radii are the desktop tokens in `theme-tokens.json` (Light) and `theme-tokens.dark.json` (Dark). The PNGs in `screens/` show every screen in both themes at both device sizes. If this spec and a PNG disagree, the spec wins. If the spec says nothing about something, ask.

## 1. Platform

- Kotlin and Jetpack Compose. `minSdk 33` (the M21), target the newest SDK installed.
- Portrait only. Draw edge to edge: the status and navigation bars sit over the window background, and content keeps clear of them with system insets. In the mockups the 28 dp row at the top and the 20 dp handle at the bottom stand in for the system bars; don't draw them.
- Navigation is a drill-down with **no on-screen back buttons**. The system back gesture goes up one level. Predictive back is on.
- Sideloaded, so the app uses **all-files access** (`MANAGE_EXTERNAL_STORAGE`) and stores plain folder paths.
- Music can be on internal storage, an SD card, or both, about 26,000 tracks. An SD card can be removed at any time. As on desktop, **an unreachable folder is never treated as a deleted library**: its tracks are kept, shown as unavailable, and return when the card does.
- Font: Selawik, bundled (see `mobile-tokens.json` → `font`). Numbers use tabular figures.

## 2. Theme

- PS2 Dark and PS1 Light, switched by hand, saved, and **Dark when nothing is saved**. The switch is in Settings and the logo menu. It applies at once, with nothing rebuilt or interrupted.
- Every colour comes from a token. Don't put hex values in screens.
- Both colour files are the desktop's, copied unchanged. A test checks they have the same keys, as the desktop parity test does.
- Mobile-only colours live in `mobile-tokens.json` → `color`. There is one so far: the BP label (§6.4).
- In Dark, the desktop's title-bar glow sits at the top of every screen: a radial gradient from `color.window.titleGlow`, shape `titleBar.glow.*` (900 × 130 at 50% / −40), fading at 45% (40% of the alpha) and clear at 75%. It's transparent in Light.

### Mockup variable → token

The mockup files use short CSS variables. Each one is exactly the desktop token below, in both themes.

| Mockup | Token | Mockup | Token |
|---|---|---|---|
| `--wbg` | color.window.bg | `--cf` | color.control.face |
| `--pbg` | color.panel.bg | `--cfb` | color.control.faceBorder |
| `--pbd` | color.panel.border | `--cin` | color.control.iconNeutral |
| `--pdv` | color.panel.divider | `--cio` | color.control.iconOff |
| `--th` | color.text.heading | `--csh` | color.control.shuffleOn |
| `--tp` | color.text.primary | `--crp` | color.control.repeatOn |
| `--ts` | color.text.secondary | `--cpr` | color.control.playRing |
| `--tm` | color.text.muted | `--str` | color.slider.track |
| `--tph` | color.text.placeholder | `--sfill` | color.slider.seekFill |
| `--tst` | color.text.status | `--chbg` | color.status.chipBg |
| `--acp` | color.accent.playback | `--chbd` | color.status.chipBorder |
| `--acpt` | color.accent.playbackText | `--on` | color.status.bitPerfectOn |
| `--acb` | color.accent.blue | `--off` | color.status.bitPerfectOff |
| `--acl` | color.accent.like | `--dring` | color.status.dotRing |
| `--sel` | color.state.selectionBg | `--abd` | color.art.border |
| `--npbg` | color.state.nowPlayingBg | `--atb` | color.art.thumbBorder |
| `--sfbg` | color.state.searchFocusBg | `--atbs` | color.art.thumbBorderSelected |
| `--phl` | shadow.panelHighlight | `--shc` | shadow.controlButton |
| `--shs` | shadow.searchInset | `--shp` | shadow.playButton |
| `--sha` | shadow.headerArt | `--glow` | shadow.playGlow |
| `--shn` | shadow.nowPlayingArt | `--tgl` | the title glow (§2) |
| `--bpon` / `--bpoff` | mobile color.bpLabel.on / off | `--art1`, `--art2`, `--artl`, `--artlt` | mockup placeholder stripes only |

The striped squares in the mockups stand for album art. Where an album has no art, use the desktop's placeholder (the panel's colours with a placeholder icon, `albums.placeholderIconSize`).

### Shared parts

- **Panel**: `color.panel.bg`, 1 dp `color.panel.border`, `radius.surface`, `shadow.panelHighlight`; inset `panel.marginX` each side and `panel.marginBottom` below. Header row `panel.headerHeight` with a 1 dp `color.panel.divider` under it; label uppercase, 600, letter-spaced, `color.text.muted`.
- **Rows**: `radius.row` corners. Selected: `color.state.selectionBg`, the second line turns `color.text.secondary`, a thumbnail's border turns `art.thumbBorderSelected`. Now playing: `color.state.nowPlayingBg`, weight 600, text `accent.playbackText`.
- **Chip**: `settings.chipHeight`, `color.status.chipBg`, 1 dp `color.status.chipBorder`, `radius.chip`, text `color.text.status`.
- **Round buttons**: `color.control.face`, 1 dp `color.control.faceBorder`, `shadow.controlButton`. Play/Pause uses `shadow.playButton`, plus a 1 dp `color.control.playRing` ring and `shadow.playGlow` (both invisible in Light), with the icon in `accent.playback`.
- Album titles wrap to two lines, then "…". Everything else is one line with "…".
- No visible scrollbars. Lists scroll by touch. Artists' letter strip (§4.1) is the one thing drawn at a list's edge (the user's call, 0.2.0; always shown since 0.2.1).
- A tap on a song plays it, with the rest of its album queued after it, and opens Now Playing on it (the user's call, 0.1.1). A tap on an artist or album opens it.

## 3. Mini player

On every library screen, under the panel. Shows the cover, title (600, `text.primary`), artist (600, `accent.blue`), and Previous, Play/Pause and Next. Sizes are in `miniPlayer.*`: skip buttons are 36 dp faces in 44 × 48 dp touch areas, and Play/Pause is a 44 dp face in a 48 dp touch area. A tap anywhere else opens Now Playing. Hidden until something has been played.

## 4. Library screens

### 4.1 Artists (the start screen)

- Top bar: the PlayStation logo (`PlayStation-Logo-1994.png`, unchanged, 36 × 28 in a 48 dp touch area), then the search field filling the rest. A tap on the field opens Search.
- **The logo is the menu button.** Its menu holds Settings, Theme (switches at once) and Statistics.
- Panel "ARTISTS". Each row: a 48 dp thumbnail of the artist's **newest album** (latest release date, using the desktop's release-date rules), the name (16, `text.primary`) and the album count, e.g. "3 albums" (13, `text.muted`). The order follows the desktop (`ArtistOrder`).
- **Letter strip** (the user's call, 0.2.0; no mockup, sizes in `artists.index.*`): a narrow pill at the list's right edge holding only the letters the library has, in list order. An artist files under the first letter of its sort key ("The Beatles" under B), accents dropped ("Åsa" under A); digits and symbols under "#", other scripts under "•". Always shown (0.2.1; 0.2.0 showed it only while the list scrolled); the rows end `width` + 2 × `marginEnd` from the list's edge so they never run under it. A touch or a slide along it jumps the list to that letter's first artist, with a haptic tick at each new letter, and a bubble in the middle of the list shows the letter (the logo menu's surface, the letter 36/600 in `text.heading`). The letters are `text.muted`, the current one `text.primary` at 600. Not shown, and no room kept for it, when the library has fewer than two letters.

### 4.2 Albums

- Top bar: the artist's name (18/600, `accent.blue`) over the album count (13, `text.muted`), and a search button on the right.
- Panel "ALBUMS". Each row: a 64 dp thumbnail, the title (16, two lines) and "1997 · 10 tracks" (13). The order follows the desktop's release-date sort.

### 4.3 Album

- Top bar: the artist's name (15, `text.muted`) and a search button.
- Panel header block: 72 dp art with `shadow.headerArt`, the title (21/600, `text.heading`, two lines), the artist (14/600, `accent.blue`) and "1997 · 10 tracks · 42:58" (13, `text.secondary`).
- Table header "# · SONG · TIME", then rows of `album.rowHeight`. The # column has an 8 × 10 triangle slot left of the number: a solid triangle in `accent.playback` while that song plays. Discs follow each other in order; there's no disc column.

### 4.4 Search

- Top bar: the field in its focused state (`state.searchFocusBg`, a 2 dp `state.searchFocusRing` ring), the query, and a clear (×) button. Results update as you type; the matching follows the desktop's `LibrarySearch`.
- Three groups, each with a panel header, shown only when they have results: **ARTISTS** (name and album count), **ALBUMS** (thumbnail, title, "Artist · 2003 · 11 tracks"), **SONGS** (title, "Artist · Album", time). The song playing gets the now-playing row style.

### 4.5 Settings

The screen title "Settings" (18/600) and four panels, `settings.sectionGap` apart:

- **Appearance**: Theme, showing "PS2 Dark" or "PS1 Light", with a chip holding a moon (Dark) or sun (Light) icon. A tap switches.
- **Playback**: Bit-Perfect, with the output readout (§6.3) under the label and a chip with a status dot (`status.bitPerfectOn` / `status.bitPerfectOff`, ring `status.dotRing`) and the word "Bit-Perfect". It's the same setting as the BP button (§6.4), greyed out in the same cases.
- **Music folders**: a refresh button in the header (rescans). One row per folder, with a checkbox (filled `accent.playback`, check in `panel.bg`) to include or leave out, the folder's name ("SD card · Music") and its path. Then "Add Folder…", which opens a folder picker and saves the plain path.
- **Last.fm**: a dot and "Last.fm: not connected", with a Connect button. Once connected: "Last.fm: connected as <user>" with Disconnect. The dot is `status.lastFmDot` while scrobbling, `status.lastFmOff` otherwise; "Last.fm: connect again" when Last.fm rejects the session. Connect opens a form in the panel: the API key and shared secret fields, "Get a key…", Cancel and Connect; then, while last.fm's approval page is open in the browser, a line saying so with "Open again" and Cancel (session 5). A failed send adds the queue line under the status.

### 4.6 Statistics

There's no mockup. Build it from the same parts (panels, headers, rows, `type.statValue` for big numbers), and show the user a screenshot before finishing. It shows:

- **Library totals**: tracks, albums, artists, size on disk.
- **Formats**: tracks per format (FLAC, DSD, MP3, …), using the desktop's format names (`AudioFormats`).
- **Listening time**: total time listened, recorded by the app from now on.

As built (session 5, approved from screenshots): LIBRARY holds Tracks, Albums and Artists in one row and Play time and On disk in the next, as `type.statValue` figures with a label under each, then the scan line. FORMATS has one bar row per format (label; "count · share · size"; a `playbackBar.trackHeight` bar in `slider.volumeFill` on `slider.track`). LISTENING shows the time and "Listened since <date>". The Playback and Debug panels follow.

## 5. Now Playing

Opens from the mini player, and when a song is tapped (in a list or in Search). It has **no buttons at the top**: system back closes it, back to the screen underneath. There is no swipe down (the user's call, 0.2.0). **Swiping the art or the title block sideways skips**: left is Next, right is Previous (the button's rule: more than 3 s in, it restarts the song). They follow the finger and fade; let go past `nowPlaying.swipeSkipDistance`, or flick faster than `swipeSkipVelocity`, and the song changes with them back in place at once (no animation; 0.2.0's spring back after a skip left them stuck off the screen on the S24). Short of that, they spring back. The seekbar keeps its own sideways drag. From top to bottom:

1. **Art**, square, `nowPlaying.paddingX` from each side, `nowPlaying.artTop` below the status bar. Its side is the smaller of (screen width − 40) and (screen height − `artHeightReserve`), so on the M21 it shrinks to leave the controls room. Border 1 dp `art.border`, `shadow.headerArt`.
2. **Title** (22/600, `text.heading`), **artist** (16/600, `accent.blue`) and **album** (15, `text.secondary`), left-aligned. The **track count "3 / 10"** (15, `text.muted`) sits right of the title, aligned to its top. There's no Like button.
3. Flexible space, weight 3.
4. **Quality**, two right-aligned lines (13, `text.secondary`): format and sample rate ("FLAC · 96 kHz"), then bit depth and bitrate ("24 bit · 1,140 kbps"). Lossy files have no bit depth: show only the bitrate ("320 kbps"). DSD shows "DSD · 2.8 MHz" (DSD64), then "1 bit".
5. **Waveform seekbar**, full width, 40 dp: the track's waveform as 96 hard-edged bars, played part `slider.seekFill`, the rest `slider.track`. The split falls at the exact place, through the bar being played, so it glides while playing (the desktop's `WaveformView`; 0.2.0, the user's report). Drag or tap to seek. Compute it as the desktop's `Waveform` does, in the background, cached per file. Until it's ready, show a flat bar `waveformFloor` high.
6. **Elapsed** left, **total** right (13, `text.muted`).
7. Flexible space, weight 1.
8. **Controls row**: on the left, a column of three 34 dp round buttons, 8 dp apart: **Repeat**, **Shuffle**, **BP**. On the right, for a right thumb, **Previous**, **Play/Pause** and **Next**, centred on the Shuffle button's height. Their size follows the screen height (`nowPlaying.skipButton`, `playButton`, `transportGap`: a fraction of the screen height, between a min and max). That gives 70 / 96 / 22 dp on the S24 and about 57 / 78 / 17 dp on the M21.
9. **Output readout**, centred (13, `text.muted`), e.g. "Shared · 48 kHz / 32-bit (resampled)" (§6.3).

Repeat: off `control.iconOff`; on `control.repeatOn`; repeat-one adds the desktop's "1" badge (`playbackBar.repeatOneBadge`). Shuffle: off `control.iconOff`, on `control.shuffleOn`. Order and repeat follow the desktop's `PlayOrder`.

## 6. Playback

### 6.1 Engine

- **BASS for Android** decodes (with its add-ons for FLAC, DSD, ALAC, APE, WavPack, Opus, Musepack and the rest the desktop plays). AudioFool writes the decoded audio to **its own output stage** (an `AudioTrack` opened at the track's own sample rate and bit depth wherever the route allows), so it decides what reaches the DAC.
- **Gapless**: the next track is decoded and queued before the current one ends, with no gap between album tracks.
- **DSD**: DoP when the output is bit-perfect and the DAC takes it; otherwise converted to PCM (follow the desktop's `DopRule`).
- **Media session** (a foreground media service): lock screen and notification controls with art, headset and Bluetooth buttons, audio focus, and a pause when headphones are unplugged.
- **Resume**: reopening the app restores the queue, track and position, paused (desktop `ReopenPoint`).
- **No click at a jump** (0.1.4, the desktop's rule): a seek, a skip or a song tapped while another plays fades the old sound out over 5 ms before it is cut, and a jump into the middle of a song fades in over 5 ms. DoP is never faded.

### 6.2 Bit-Perfect

| Output | What BP does |
|---|---|
| S24 + USB DAC | On: the app asks Android for the bit-perfect mixer on the USB device (`AudioManager.setPreferredMixerAttributes` with `MIXER_BEHAVIOR_BIT_PERFECT`, Android 14+), at each track's rate. Where the phone offers no bit-perfect entry at that rate, it sets an ordinary mixer entry at the track's rate instead: no resampling, but still Android's mixer, so the readout says "rate matched", never "bit-perfect" (the user's call, session 4). No entry at the rate at all: shared. A USB device that lists no mixer entries greys BP out. |
| M21 headphone out (and a USB DAC on the M21, which has no mixer API on Android 13) | The M21 lists its 3.5 mm and 4.4 mm jacks to Android as the built-in speaker (it has no speaker), so on a FiiO player the speaker route is taken as Headphones (0.1.2). On: the app opens the output at the track's own rate, in whole-number samples, at full volume, and leaves the rest to FiiO's system SRC bypass. The readout says "bit-perfect" only when the output is seen running at the track's rate, and "resampled" otherwise. On a FiiO player the rate is the one FiiO reports its DAC running at (`fiio.intent.action.SAMPLE_RATE_CHANGED`, its `rate`), once it has reported one for the output open now (the user's call, 0.1.5); before that, and on other players, the system's media output (`AudioTrack.getNativeOutputSampleRate`), which on the M21 always says 48 kHz. |
| Bluetooth, phone speaker | Not possible. BP is greyed out (`disabledOpacity`) and can't be tapped. |

The shared output never takes 24-bit packed samples: a 24-bit track goes to Android's mixer as float, which holds it exactly (24-bit packed came out as static on the M21, 0.1.1), and the M21's way sends anything wider than 16-bit as 32-bit. The setting is saved. Off means the normal shared output. Turning it on or off, or a change of route (a DAC plugged in, Bluetooth connecting), reopens the output where the song was heard. On a bit-perfect path the app never turns the volume down: a prompt that would duck the music pauses it instead.

### 6.3 Output readout

One line describing what actually reaches the output, built like the desktop's `OutputReadout`: route, then rate and depth, then a note. Examples: "USB · 96 kHz / 24-bit (bit-perfect)", "USB · 96 kHz / 24-bit (rate matched)", "USB · 176.4 kHz · DSD over PCM (bit-perfect)", "Headphones · 96 kHz / 24-bit (bit-perfect)", "Speaker · Shared · 48 kHz (resampled)", "Bluetooth · Shared · 48 kHz (resampled)". The shared output's depth isn't known to an app, so it is left out; the Bluetooth codec isn't either (it's a system-only API), so there's no "LDAC". Shown under Now Playing's controls and on the Settings row.

### 6.4 BP button

A 34 dp round button labelled "BP" (12/700). The label is `bpLabel.on` when Bit-Perfect is on and `bpLabel.off` when it's off. A tap toggles it, unless it's greyed out (§6.2).

## 7. Last.fm

Scrobbling as on desktop (`LastFmApi`, `LastFmScrobbler`, `PlayTracker`, `ScrobbleQueue`): now-playing updates, scrobbles under Last.fm's rules, and an offline queue. Connect asks once for the API key and secret (the user reuses the desktop's), then opens the browser for approval and fetches the session. The key, secret and session are stored encrypted (Android Keystore), never in the repo. The play is counted from the heard position, so a scrobble's rules (half the song or four minutes, seeks don't count) are the desktop's exactly.

## 8. Library and cache

- The scan walks the chosen folders and reads tags and art. The tag reader must cover every format in the desktop's `AudioFormats`, DSF and DFF included.
- A cache makes a cold start with 26,000 tracks show the library at once. A rescan only reads files whose size or date changed (desktop `FileStamp`, `LibraryCache`).
- Artist, album and song order, release dates, and search follow the desktop's Core rules. Port them, and port their tests.
- A folder that can't be reached (the SD card out) keeps its tracks, shown as unavailable. When the card is ejected, a song on it that is playing stops at once, paused where it was heard (Android ends any app still holding a file open on it), and carries on with the card back (session 5).
- Cover thumbnails are cached on disk and decoded at display size. Scrolling 26,000 tracks must stay smooth on the M21 (Snapdragon 680, 4 GB).
